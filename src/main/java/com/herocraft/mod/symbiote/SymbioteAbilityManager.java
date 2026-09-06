package com.herocraft.mod.symbiote;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.combat.SonicVulnerability;
import com.herocraft.mod.hero.AbilitySlot;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.PowerToggles;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The Normal Symbiote Host's abilities: Tendril Grab (R), Tendril Strike (G), Symbiote Leap
 * (X) / Symbiote Grapple (Sneak + X), Symbiote Onslaught (Z, the hold-charge ultimate),
 * Symbiote Shield (V), Frenzy (C). Bridges the six universal ability slots
 * exactly like every other {@code <Hero>AbilityManager} -- see
 * {@link com.herocraft.mod.hero.AbilityRouter}'s priority chain.
 *
 * <p>Black Suit Spider-Man does NOT use this class -- his Symbiote-flavoured extras layer onto the
 * existing {@code SpiderManAbilityManager}/{@code SpiderAbilities} instead, per the spec's explicit
 * "contextual, not new keybinds" instruction for that variant.
 *
 * <p>Three slots are HOLD abilities, so unlike the rest they act on both the press AND the release:
 * {@link AbilitySlot#SLOT_1} (Tendril Grab -- grab on press, throw on release),
 * {@link AbilitySlot#SLOT_5} (Symbiote Shield -- up while held, draining a 9-second guard bar) and
 * {@link AbilitySlot#SLOT_4} (Symbiote Onslaught -- a 3-second hold-charge, then a black-symbiote AoE
 * on release, 60-second cooldown). All three are dispatched before the generic press-only switch
 * below and manage their own cooldown/guard bookkeeping. Sneak + {@link AbilitySlot#SLOT_3} is also
 * special-cased on the press edge, routing the Movement key to Symbiote Grapple instead of Leap.
 */
public final class SymbioteAbilityManager {
	private static final ResourceLocation FRENZY_ATTACK = HeroCraftMod.id("symbiote_frenzy_attack");
	private static final ResourceLocation FRENZY_SPEED = HeroCraftMod.id("symbiote_frenzy_speed");
	private static final ResourceLocation FRENZY_KNOCKBACK = HeroCraftMod.id("symbiote_frenzy_knockback");

	private static final int CD_TENDRIL_STRIKE = 80;  // 4s
	private static final int CD_LEAP = 40;             // 2s
	private static final int CD_FRENZY = 500;          // 25s
	/** Cooldown after a Tendril Grab resolves (thrown, or the target slips away) before it can grab again. */
	private static final int CD_TENDRIL_GRAB = 100;    // 5s

	private static final int FRENZY_DURATION = 280;     // 14s
	private static final int FRENZY_DEBUFF_DURATION = 100; // 5s post-Frenzy defence dip
	/** Fall-damage grace after a Symbiote Leap or Grapple -- deliberately long: it is cleared the instant
	 *  the player next touches the ground (see {@link #serverTick}), so in practice it always lasts
	 *  exactly "until you land", however far the launch carried you. */
	private static final int LEAP_NO_FALL_TICKS = 600;

	private static final float SHIELD_GUARD_DRAIN = 1.0f; // 1 tick of hold per tick -- 180 ticks = 9s
	private static final float SHIELD_GUARD_REGEN = 2.0f; // refills twice as fast as it drains while down

	private static final double GRAB_RANGE = 15.0;
	private static final double GRAB_HOLD_DISTANCE = 2.6;
	private static final int GRAB_MAX_HOLD_TICKS = 70;  // ~3.5s safety cap before an auto-throw
	private static final float GRAB_THROW_DAMAGE = 7.0f;

	// ---- Symbiote Onslaught (slot 4 ultimate): a 3-second hold-charge, then a black-symbiote AoE ----
	private static final int CD_ONSLAUGHT = 1200;          // 60s
	private static final int ONSLAUGHT_CHARGE_TICKS = 60;  // 3s hold to release it
	private static final double ONSLAUGHT_RADIUS = 6.0;
	private static final int ONSLAUGHT_DOT_TICKS = 100;    // 5s of black-symbiote decay on the victims

	// ---- Symbiote Grapple (Sneak + X): a 25-block yank toward whatever you are aiming at ----
	private static final double GRAPPLE_RANGE = 25.0;
	private static final int CD_GRAPPLE = 60;              // 3s
	private static final int GRAPPLE_PULL_TICKS = 20;      // how long the pull steers the player

	/** Per-player leap fall-damage grace window -- not persisted, cleared on server stop like the rest. */
	private static final Map<Integer, Long> LEAP_NO_FALL_UNTIL = new ConcurrentHashMap<>();
	/** Per-player Grapple cooldown -- transient, cleared on server stop like the rest. */
	private static final Map<Integer, Long> GRAPPLE_READY_AT = new ConcurrentHashMap<>();
	/** Per-player active Grapple pull: {targetX, targetY, targetZ, endTick}. Transient. */
	private static final Map<Integer, double[]> GRAPPLE_PULL = new ConcurrentHashMap<>();

	private SymbioteAbilityManager() {
	}

	public static boolean hasContext(ServerPlayer player) {
		return Symbiote.isActive(player) && SymbioteHostType.of(player) == SymbioteHostType.NORMAL;
	}

	public static boolean shieldActive(ServerPlayer player) {
		return Symbiote.state(player).shieldHeld;
	}

	public static boolean frenzyActive(ServerPlayer player, long now) {
		return now < Symbiote.state(player).frenzyEndTick;
	}

	public static boolean frenzyDebuffActive(ServerPlayer player, long now) {
		SymbioteState s = Symbiote.state(player);
		return now >= s.frenzyEndTick && now < s.frenzyDebuffEndTick;
	}

	public static boolean leapFallProtected(ServerPlayer player, long now) {
		Long until = LEAP_NO_FALL_UNTIL.get(player.getId());
		return until != null && now < until;
	}

	public static boolean onslaughtCharging(ServerPlayer player) {
		return Symbiote.state(player).onslaughtChargeStart >= 0;
	}

	public static void clearSessionState() {
		LEAP_NO_FALL_UNTIL.clear();
		GRAPPLE_READY_AT.clear();
		GRAPPLE_PULL.clear();
	}

	// ---------------- dispatch ----------------

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		long now = player.level().getGameTime();
		if (pressed && SonicVulnerability.isDisrupted(player, now)) {
			player.displayClientMessage(
					Component.translatable("message.herocraft.symbiote.sonic_disrupted"), true);
			return;
		}

		// Hold abilities: act on press AND release, and gate/cooldown themselves.
		if (slot == AbilitySlot.SLOT_5) {
			handleShield(player, pressed);
			return;
		}
		if (slot == AbilitySlot.SLOT_1) {
			handleTendrilGrab(player, pressed, now);
			return;
		}
		if (slot == AbilitySlot.SLOT_4) {
			handleOnslaught(player, pressed, now);
			return;
		}
		// Sneak + X (Movement key) is Symbiote Grapple -- a 25-block yank -- rather than the plain Leap.
		if (slot == AbilitySlot.SLOT_3 && pressed && player.isShiftKeyDown()) {
			handleGrapple(player, now);
			return;
		}

		if (!pressed) {
			return;
		}
		int index = slot.index();
		SymbioteState state = Symbiote.state(player);
		long readyAt = state.abilityCooldowns.get(index);
		if (now < readyAt) {
			player.displayClientMessage(Component.translatable("message.herocraft.symbiote.ability_cooldown",
					String.format(java.util.Locale.ROOT, "%.1f", (readyAt - now) / 20.0f)), true);
			return;
		}

		boolean used = switch (slot) {
			case SLOT_2 -> tendrilStrike(player);
			case SLOT_3 -> symbioteLeap(player);
			case SLOT_6 -> frenzy(player);
			default -> false;
		};
		if (used) {
			setCooldown(player, index, now, cooldownFor(slot));
		}
	}

	private static int cooldownFor(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> CD_TENDRIL_STRIKE;
			case SLOT_3 -> CD_LEAP;
			case SLOT_6 -> CD_FRENZY;
			default -> 0;
		};
	}

	private static void setCooldown(ServerPlayer player, int index, long now, int ticks) {
		SymbioteState c = Symbiote.state(player).copy();
		c.abilityCooldowns.set(index, now + ticks);
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
	}

	// ---------------- abilities ----------------

	/**
	 * Ability 1 -- Tendril Grab: press to lash a tendril at what you are aiming at and suspend it
	 * helpless in mid-air in front of you; release to hurl it away in whatever direction you are now
	 * looking. {@link #tickTendrilGrab} keeps the target pinned in place and enforces a safety cap so
	 * it can never be held forever.
	 */
	private static void handleTendrilGrab(ServerPlayer player, boolean pressed, long now) {
		SymbioteState s = Symbiote.state(player);
		if (pressed) {
			if (s.tendrilGrabHeld) {
				return; // already holding something -- releasing the key throws it, not a second press
			}
			long readyAt = s.abilityCooldowns.get(AbilitySlot.SLOT_1.index());
			if (now < readyAt) {
				player.displayClientMessage(Component.translatable("message.herocraft.symbiote.ability_cooldown",
						String.format(java.util.Locale.ROOT, "%.1f", (readyAt - now) / 20.0f)), true);
				return;
			}
			LivingEntity target = AbilityHelpers.raycastEntity(player, GRAB_RANGE);
			if (target == null || !AbilityHelpers.isValidGrabTarget(target, player)) {
				return;
			}
			SymbioteState c = s.copy();
			c.tendrilGrabTargetId = target.getId();
			c.tendrilGrabHeld = true;
			c.tendrilGrabStartTick = now;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);

			target.setNoGravity(true);
			target.setDeltaMovement(Vec3.ZERO);
			ServerLevel level = AbilityHelpers.level(player);
			AbilityHelpers.line(level, player.getEyePosition().add(player.getLookAngle().scale(0.6)),
					target.position().add(0, target.getBbHeight() * 0.5, 0), ParticleTypes.SQUID_INK, 4.0);
			AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
					ParticleTypes.SQUID_INK, 10, 0.3);
			AbilityHelpers.sound(player, SoundEvents.SLIME_SQUISH, 0.9f, 0.5f);
		} else if (s.tendrilGrabHeld) {
			throwGrabbed(player, s, now);
		}
	}

	/** Per-tick upkeep for a held Tendril Grab: pin the target in place, or auto-throw past the cap. */
	private static void tickTendrilGrab(ServerPlayer player, long now) {
		SymbioteState s = Symbiote.state(player);
		if (!s.tendrilGrabHeld) {
			return;
		}
		ServerLevel level = AbilityHelpers.level(player);
		Entity e = level.getEntity(s.tendrilGrabTargetId);
		if (!(e instanceof LivingEntity target) || !target.isAlive()
				|| player.distanceToSqr(target) > (GRAB_RANGE * 1.5) * (GRAB_RANGE * 1.5)) {
			releaseGrabQuietly(player, s, now);
			return;
		}
		if (now - s.tendrilGrabStartTick >= GRAB_MAX_HOLD_TICKS) {
			throwGrabbed(player, s, now);
			return;
		}

		Vec3 hold = player.getEyePosition().add(player.getLookAngle().scale(GRAB_HOLD_DISTANCE));
		target.setNoGravity(true);
		target.teleportTo(hold.x, hold.y - target.getBbHeight() * 0.5, hold.z);
		target.setDeltaMovement(Vec3.ZERO);
		target.fallDistance = 0;
		if (player.tickCount % 3 == 0) {
			AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
					ParticleTypes.SQUID_INK, 5, 0.22);
		}
	}

	private static void throwGrabbed(ServerPlayer player, SymbioteState s, long now) {
		ServerLevel level = AbilityHelpers.level(player);
		Entity e = level.getEntity(s.tendrilGrabTargetId);
		if (e instanceof LivingEntity target && target.isAlive()) {
			target.setNoGravity(false);
			Vec3 look = player.getLookAngle();
			AbilityHelpers.push(target, look.scale(1.9).add(0, 0.4, 0));
			AbilityHelpers.hurt(player, target, GRAB_THROW_DAMAGE);
			AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
					ParticleTypes.SQUID_INK, 18, 0.4);
			AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
					ParticleTypes.CRIT, 8, 0.35);
			AbilityHelpers.sound(player, SoundEvents.HOSTILE_SWIM, 1.0f, 0.4f);
		}
		clearGrab(player, s, now, true);
	}

	/** The target died or got out of range while held -- let it go, no cooldown penalty for the whiff. */
	private static void releaseGrabQuietly(ServerPlayer player, SymbioteState s, long now) {
		ServerLevel level = AbilityHelpers.level(player);
		Entity e = level.getEntity(s.tendrilGrabTargetId);
		if (e instanceof LivingEntity target) {
			target.setNoGravity(false);
		}
		clearGrab(player, s, now, false);
	}

	private static void clearGrab(ServerPlayer player, SymbioteState s, long now, boolean startCooldown) {
		SymbioteState c = s.copy();
		c.tendrilGrabTargetId = -1;
		c.tendrilGrabHeld = false;
		if (startCooldown) {
			c.abilityCooldowns.set(AbilitySlot.SLOT_1.index(), now + CD_TENDRIL_GRAB);
		}
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
	}

	/** Ability 2 -- Tendril Strike: short-range tendril melee. */
	private static boolean tendrilStrike(ServerPlayer player) {
		LivingEntity target = AbilityHelpers.raycastEntity(player, 8.0);
		if (target == null) {
			return false;
		}
		float damage = 9.0f + player.getRandom().nextFloat() * 2.0f;
		if (!AbilityHelpers.hurt(player, target, damage)) {
			return false;
		}
		AbilityHelpers.knockbackFrom(target, player.position(), 0.9);
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.6));
		Vec3 hit = target.position().add(0, target.getBbHeight() * 0.5, 0);
		AbilityHelpers.line(level, hand, hit, ParticleTypes.SQUID_INK, 4.0);
		AbilityHelpers.burst(level, hit, ParticleTypes.SQUID_INK, 14, 0.3);
		AbilityHelpers.burst(level, hit, ParticleTypes.CRIT, 6, 0.3);
		AbilityHelpers.sound(player, SoundEvents.HOSTILE_SWIM, 0.8f, 0.5f);
		return true;
	}

	/** Ability 3 -- Symbiote Leap: a strong forward-and-up mobility launch, landing softened. */
	private static boolean symbioteLeap(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
		AbilityHelpers.launchSelf(player, dir.scale(1.35).add(0, 1.05, 0));
		long now = player.level().getGameTime();
		LEAP_NO_FALL_UNTIL.put(player.getId(), now + LEAP_NO_FALL_TICKS);
		ServerLevel level = AbilityHelpers.level(player);
		AbilityHelpers.burst(level, player.position(), ParticleTypes.SQUID_INK, 20, 0.3);
		AbilityHelpers.burst(level, player.position(), ParticleTypes.POOF, 10, 0.4);
		AbilityHelpers.sound(player, SoundEvents.SLIME_JUMP, 1.0f, 0.6f);
		return true;
	}

	/**
	 * Ability 4 -- Symbiote Onslaught (the ultimate): hold for 3 seconds to charge, then release to
	 * engulf every nearby enemy in living black symbiote -- blinding and slowing them and decaying them
	 * for 10 damage over the next 5 seconds. 60-second cooldown. Releasing early cancels it with no
	 * cooldown; {@link #tickOnslaught} auto-fires it once the charge is full even if the key is held.
	 */
	private static void handleOnslaught(ServerPlayer player, boolean pressed, long now) {
		SymbioteState s = Symbiote.state(player);
		if (pressed) {
			if (s.onslaughtChargeStart >= 0) {
				return; // already charging
			}
			long readyAt = s.abilityCooldowns.get(AbilitySlot.SLOT_4.index());
			if (now < readyAt) {
				player.displayClientMessage(Component.translatable("message.herocraft.symbiote.ability_cooldown",
						String.format(java.util.Locale.ROOT, "%.1f", (readyAt - now) / 20.0f)), true);
				return;
			}
			SymbioteState c = s.copy();
			c.onslaughtChargeStart = now;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			player.displayClientMessage(Component.translatable("message.herocraft.symbiote.onslaught_charging"), true);
			AbilityHelpers.sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f);
		} else if (s.onslaughtChargeStart >= 0) {
			if (now - s.onslaughtChargeStart >= ONSLAUGHT_CHARGE_TICKS) {
				fireOnslaught(player, s, now);
			} else {
				cancelOnslaught(player, s, "message.herocraft.symbiote.onslaught_interrupted");
			}
		}
	}

	/** Per-tick upkeep while Symbiote Onslaught is charging: rising aura, and an auto-fire at full charge. */
	private static void tickOnslaught(ServerPlayer player, long now) {
		SymbioteState s = Symbiote.state(player);
		if (s.onslaughtChargeStart < 0) {
			return;
		}
		long held = now - s.onslaughtChargeStart;
		if (held >= ONSLAUGHT_CHARGE_TICKS) {
			fireOnslaught(player, s, now);
			return;
		}
		ServerLevel level = AbilityHelpers.level(player);
		double frac = held / (double) ONSLAUGHT_CHARGE_TICKS;
		Vec3 c = player.position().add(0, player.getBbHeight() * 0.5, 0);
		int ring = 12 + (int) (frac * 16);
		for (int i = 0; i < ring; i++) {
			double ang = (Math.PI * 2 * i) / ring + now * 0.15;
			double r = ONSLAUGHT_RADIUS * (1.0 - frac * 0.7);
			level.sendParticles(ParticleTypes.SQUID_INK, c.x + Math.cos(ang) * r, player.getY() + 0.1,
					c.z + Math.sin(ang) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		AbilityHelpers.burst(level, c, ParticleTypes.LARGE_SMOKE, 2, 0.4);
		if (player.tickCount % 6 == 0) {
			AbilityHelpers.sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f + (float) frac);
		}
	}

	private static void cancelOnslaught(ServerPlayer player, SymbioteState s, String messageKey) {
		SymbioteState c = s.copy();
		c.onslaughtChargeStart = -1L;
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		if (messageKey != null) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
	}

	private static void fireOnslaught(ServerPlayer player, SymbioteState s, long now) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 center = player.position();
		for (LivingEntity target : AbilityHelpers.enemiesAround(player, center, ONSLAUGHT_RADIUS)) {
			// Wither III for 5 seconds decays the target ~10 health over that window -- "10 damage for 5s".
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, ONSLAUGHT_DOT_TICKS, 2, false, true, true));
			AbilityHelpers.applyControl(target, MobEffects.BLINDNESS, ONSLAUGHT_DOT_TICKS, 0);
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, ONSLAUGHT_DOT_TICKS, 1);
			Vec3 hit = target.position().add(0, target.getBbHeight() * 0.5, 0);
			AbilityHelpers.burst(level, hit, ParticleTypes.SQUID_INK, 40, target.getBbWidth() * 0.6 + 0.4);
			AbilityHelpers.burst(level, hit, ParticleTypes.LARGE_SMOKE, 12, 0.4);
		}
		AbilityHelpers.burst(level, center.add(0, 1, 0), ParticleTypes.SQUID_INK, 60, ONSLAUGHT_RADIUS * 0.6);
		level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.9f, 0.6f);

		SymbioteState c = s.copy();
		c.onslaughtChargeStart = -1L;
		c.abilityCooldowns.set(AbilitySlot.SLOT_4.index(), now + CD_ONSLAUGHT);
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
	}

	/**
	 * Sneak + X -- Symbiote Grapple: fire a tendril at whatever you are aiming at within 25 blocks and
	 * reel yourself toward it over the next second ({@link #tickGrapplePull}). Fall damage is waived on
	 * the landing, exactly like a Symbiote Leap.
	 */
	private static void handleGrapple(ServerPlayer player, long now) {
		Long readyAt = GRAPPLE_READY_AT.get(player.getId());
		if (readyAt != null && now < readyAt) {
			player.displayClientMessage(Component.translatable("message.herocraft.symbiote.ability_cooldown",
					String.format(java.util.Locale.ROOT, "%.1f", (readyAt - now) / 20.0f)), true);
			return;
		}
		Vec3 anchor = AbilityHelpers.aimPoint(player, GRAPPLE_RANGE);
		Vec3 eye = player.getEyePosition();
		if (anchor.distanceTo(eye) < 3.0) {
			player.displayClientMessage(Component.translatable("message.herocraft.symbiote.grapple_too_close"), true);
			return;
		}
		GRAPPLE_READY_AT.put(player.getId(), now + CD_GRAPPLE);
		GRAPPLE_PULL.put(player.getId(), new double[]{anchor.x, anchor.y, anchor.z, now + GRAPPLE_PULL_TICKS});
		LEAP_NO_FALL_UNTIL.put(player.getId(), now + LEAP_NO_FALL_TICKS);
		ServerLevel level = AbilityHelpers.level(player);
		AbilityHelpers.line(level, eye.add(player.getLookAngle().scale(0.4)), anchor, ParticleTypes.SQUID_INK, 3.0);
		AbilityHelpers.sound(player, SoundEvents.SLIME_SQUISH, 0.9f, 0.4f);
		AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.9f, 0.6f);
	}

	private static void tickGrapplePull(ServerPlayer player, long now) {
		double[] pull = GRAPPLE_PULL.get(player.getId());
		if (pull == null) {
			return;
		}
		Vec3 anchor = new Vec3(pull[0], pull[1], pull[2]);
		Vec3 toAnchor = anchor.subtract(player.getEyePosition());
		if (now >= (long) pull[3] || toAnchor.length() < 2.0) {
			GRAPPLE_PULL.remove(player.getId());
			return;
		}
		Vec3 dir = toAnchor.normalize();
		double speed = Math.min(1.6, 0.7 + toAnchor.length() * 0.08);
		AbilityHelpers.launchSelf(player, dir.scale(speed).add(0, 0.12, 0));
		AbilityHelpers.level(player).sendParticles(ParticleTypes.SQUID_INK,
				player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
	}

	/** Ability 5 -- Symbiote Shield: a holdable stance, -60% damage while up, slowed while up. */
	private static void handleShield(ServerPlayer player, boolean pressed) {
		SymbioteState s = Symbiote.state(player);
		if (pressed) {
			if (s.shieldHeld) {
				return;
			}
			if (s.shieldGuard <= 0.0f) {
				player.displayClientMessage(Component.translatable("message.herocraft.symbiote.shield_spent"), true);
				return;
			}
			SymbioteState c = s.copy();
			c.shieldHeld = true;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0f, 0.5f);
			AbilityHelpers.burst(AbilityHelpers.level(player), player.position().add(0, 1, 0),
					ParticleTypes.SQUID_INK, 20, 0.4);
		} else if (s.shieldHeld) {
			SymbioteState c = s.copy();
			c.shieldHeld = false;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		}
	}

	/** Per-tick upkeep for a held Symbiote Shield: drain/regen the guard bar and draw the black wall. */
	private static void tickShield(ServerPlayer player) {
		SymbioteState s = Symbiote.state(player);
		if (s.shieldHeld) {
			float remaining = s.shieldGuard - SHIELD_GUARD_DRAIN;
			boolean depleted = remaining <= 0.0f;
			SymbioteState c = s.copy();
			c.shieldGuard = Math.max(0.0f, remaining);
			c.shieldHeld = !depleted;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);

			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 1, true, true, true));
			ServerLevel level = AbilityHelpers.level(player);
			// Draw a round black slab hovering just off the player's leading arm -- a real shield sits a
			// little in front of you and faces where you look, so this one does too.
			Vec3 look = player.getLookAngle();
			Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
			Vec3 up = new Vec3(0, 1, 0);
			Vec3 center = player.getEyePosition().add(look.scale(0.9)).add(0, -0.35, 0);
			for (int i = 0; i < 14; i++) {
				double ang = (Math.PI * 2 * i) / 14.0;
				double rw = 0.55, rh = 0.7;
				Vec3 edge = center.add(right.scale(Math.cos(ang) * rw)).add(up.scale(Math.sin(ang) * rh));
				level.sendParticles(ParticleTypes.SQUID_INK, edge.x, edge.y, edge.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 4, 0.28, 0.36, 0.05, 0.0);
			if (player.tickCount % 4 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 3, 0.3, 0.4, 0.05, 0.0);
			}
			if (depleted) {
				player.displayClientMessage(Component.translatable("message.herocraft.symbiote.shield_spent"), true);
			}
		} else if (s.shieldGuard < SymbioteState.SHIELD_GUARD_MAX) {
			SymbioteState c = s.copy();
			c.shieldGuard = Math.min(SymbioteState.SHIELD_GUARD_MAX, s.shieldGuard + SHIELD_GUARD_REGEN);
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		}
	}

	/** Ability 6 -- Frenzy: aggressive burst with a defensive drawback afterward. */
	private static boolean frenzy(ServerPlayer player) {
		long now = player.level().getGameTime();
		SymbioteState c = Symbiote.state(player).copy();
		c.frenzyEndTick = now + FRENZY_DURATION;
		c.frenzyDebuffEndTick = 0L; // set when Frenzy actually expires, see #serverTick
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.8f, 1.4f);
		ServerLevel level = AbilityHelpers.level(player);
		AbilityHelpers.burst(level, player.position().add(0, 1, 0), ParticleTypes.LARGE_SMOKE, 24, 0.5);
		AbilityHelpers.burst(level, player.position().add(0, 1, 0), ParticleTypes.SQUID_INK, 16, 0.6);
		return true;
	}

	// ---------------- per-tick upkeep ----------------

	public static void serverTick(ServerPlayer player) {
		if (!Symbiote.hasSymbiote(player)) {
			return;
		}
		long now = player.level().getGameTime();
		boolean frenzying = frenzyActive(player, now);
		if (frenzying) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, FRENZY_ATTACK, 0.25,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, FRENZY_SPEED, 0.15,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, FRENZY_KNOCKBACK, 0.25,
					AttributeModifier.Operation.ADD_VALUE);
			// A dark, roiling aura while it lasts -- Frenzy should visibly read as "on" from a distance.
			if (player.tickCount % 3 == 0) {
				ServerLevel level = AbilityHelpers.level(player);
				AbilityHelpers.burst(level, player.position().add(0, player.getBbHeight() * 0.5, 0),
						ParticleTypes.LARGE_SMOKE, 3, 0.45);
				AbilityHelpers.burst(level, player.position().add(0, player.getBbHeight() * 0.5, 0),
						ParticleTypes.SQUID_INK, 2, 0.5);
			}
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, FRENZY_ATTACK);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, FRENZY_SPEED);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, FRENZY_KNOCKBACK);
		}

		SymbioteState s = Symbiote.state(player);
		// Frenzy just ended (frenzyEndTick is in the past but the debuff window hasn't been armed yet):
		// arm the brief post-Frenzy defence dip exactly once.
		if (!frenzying && s.frenzyEndTick != 0 && now >= s.frenzyEndTick && s.frenzyDebuffEndTick < s.frenzyEndTick) {
			SymbioteState c = s.copy();
			c.frenzyDebuffEndTick = s.frenzyEndTick + FRENZY_DEBUFF_DURATION;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		}

		if (leapFallProtected(player, now) && player.onGround()) {
			LEAP_NO_FALL_UNTIL.remove(player.getId());
		}

		if (hasContext(player)) {
			wallAssist(player);
			tickShield(player);
			tickTendrilGrab(player, now);
			tickOnslaught(player, now);
			tickGrapplePull(player, now);
		} else {
			// The suit retracted (or the host type changed) mid-grab: let go rather than leave the
			// target permanently floating with no-gravity set.
			SymbioteState held = Symbiote.state(player);
			if (held.tendrilGrabHeld) {
				releaseGrabQuietly(player, held, now);
			}
			if (held.onslaughtChargeStart >= 0) {
				cancelOnslaught(player, held, null);
			}
			GRAPPLE_PULL.remove(player.getId());
		}
	}

	/**
	 * Wall Assistance (passive): a Normal host clinging against a wall while airborne falls much more
	 * slowly, and can push off it by sneaking. Deliberately much lighter than Spider-Man's full
	 * {@code SpiderClimb} engine -- no ceiling case, no sustained climbing, per the spec's explicit
	 * "does not need to be as advanced as Spider-Man" instruction.
	 */
	private static void wallAssist(ServerPlayer player) {
		if (player.onGround() || !player.horizontalCollision) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		if (v.y >= -0.1) {
			return;
		}
		if (player.isShiftKeyDown()) {
			// Push away from the wall: reverse whatever horizontal velocity drove into it.
			AbilityHelpers.launchSelf(player, new Vec3(-v.x * 1.5, Math.max(v.y, -0.05), -v.z * 1.5));
			return;
		}
		// Slow the fall against the wall (nowhere near Spider-Man's full cling -- just a soft brake).
		AbilityHelpers.launchSelf(player, new Vec3(v.x * 0.7, Math.max(v.y, -0.10), v.z * 0.7));
		player.fallDistance *= 0.5f;
	}
}
