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
 * The Normal Symbiote Host's six abilities: Tendril Grab (R), Tendril Strike (G), Symbiote Leap
 * (X), Symbiote Slam (Z), Symbiote Shield (V), Frenzy (C). Bridges the six universal ability slots
 * exactly like every other {@code <Hero>AbilityManager} -- see
 * {@link com.herocraft.mod.hero.AbilityRouter}'s priority chain.
 *
 * <p>Black Suit Spider-Man does NOT use this class -- his Symbiote-flavoured extras layer onto the
 * existing {@code SpiderManAbilityManager}/{@code SpiderAbilities} instead, per the spec's explicit
 * "contextual, not new keybinds" instruction for that variant.
 *
 * <p>Two of the six slots are HOLD abilities (v0.9.19), so unlike every other slot here they act on
 * both the press AND the release: {@link AbilitySlot#SLOT_1} (Tendril Grab -- grab on press, hold the
 * target suspended, throw it on release) and {@link AbilitySlot#SLOT_5} (Symbiote Shield -- up while
 * held, draining a 9-second guard bar, regenerating twice as fast while down). Both are dispatched
 * before the generic press-only switch below and manage their own cooldown/guard bookkeeping.
 */
public final class SymbioteAbilityManager {
	private static final ResourceLocation FRENZY_ATTACK = HeroCraftMod.id("symbiote_frenzy_attack");
	private static final ResourceLocation FRENZY_SPEED = HeroCraftMod.id("symbiote_frenzy_speed");
	private static final ResourceLocation FRENZY_KNOCKBACK = HeroCraftMod.id("symbiote_frenzy_knockback");

	private static final int CD_TENDRIL_STRIKE = 80;  // 4s
	private static final int CD_LEAP = 40;             // 2s
	private static final int CD_SLAM = 200;            // 10s
	private static final int CD_FRENZY = 500;          // 25s
	/** Cooldown after a Tendril Grab resolves (thrown, or the target slips away) before it can grab again. */
	private static final int CD_TENDRIL_GRAB = 100;    // 5s

	private static final int FRENZY_DURATION = 280;     // 14s
	private static final int FRENZY_DEBUFF_DURATION = 100; // 5s post-Frenzy defence dip
	private static final int LEAP_NO_FALL_TICKS = 60;   // ~3s grace after a leap

	private static final float SHIELD_GUARD_DRAIN = 1.0f; // 1 tick of hold per tick -- 180 ticks = 9s
	private static final float SHIELD_GUARD_REGEN = 2.0f; // refills twice as fast as it drains while down

	private static final double GRAB_RANGE = 15.0;
	private static final double GRAB_HOLD_DISTANCE = 2.6;
	private static final int GRAB_MAX_HOLD_TICKS = 70;  // ~3.5s safety cap before an auto-throw
	private static final float GRAB_THROW_DAMAGE = 7.0f;

	/** Per-player leap fall-damage grace window -- not persisted, cleared on server stop like the rest. */
	private static final Map<Integer, Long> LEAP_NO_FALL_UNTIL = new ConcurrentHashMap<>();

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

	public static void clearSessionState() {
		LEAP_NO_FALL_UNTIL.clear();
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
			case SLOT_4 -> symbioteSlam(player);
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
			case SLOT_4 -> CD_SLAM;
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

	/** Ability 4 -- Symbiote Slam: short-range ground slam, no terrain damage by default. */
	private static boolean symbioteSlam(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 center = player.position();
		boolean hitAny = false;
		for (LivingEntity target : AbilityHelpers.enemiesAround(player, center, 4.0)) {
			float damage = 8.0f + player.getRandom().nextFloat() * 2.0f;
			if (AbilityHelpers.hurt(player, target, damage)) {
				AbilityHelpers.knockbackFrom(target, center, 1.1);
				hitAny = true;
			}
		}
		AbilityHelpers.burst(level, center, ParticleTypes.SQUID_INK, 30, 0.6);
		AbilityHelpers.burst(level, center, ParticleTypes.CRIT, 16, 0.8);
		level.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z, 20, 0.9, 0.1, 0.9, 0.02);
		// A quick expanding ring of impact particles, tracing the slam's true hit radius.
		for (int i = 0; i < 24; i++) {
			double ang = (Math.PI * 2 * i) / 24.0;
			Vec3 edge = center.add(Math.cos(ang) * 3.6, 0.1, Math.sin(ang) * 3.6);
			level.sendParticles(ParticleTypes.SQUID_INK, edge.x, edge.y, edge.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		AbilityHelpers.sound(player, SoundEvents.GENERIC_BIG_FALL, 1.0f, 0.6f);
		return hitAny || true; // functions even against nothing (creates the impact), matching the spec's slam
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
			Vec3 front = player.getEyePosition().add(player.getLookAngle().scale(1.1)).add(0, -0.2, 0);
			AbilityHelpers.burst(level, front, ParticleTypes.SQUID_INK, 5, 0.4);
			if (player.tickCount % 4 == 0) {
				AbilityHelpers.burst(level, front, ParticleTypes.SMOKE, 3, 0.25);
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
		} else {
			// The suit retracted (or the host type changed) mid-grab: let go rather than leave the
			// target permanently floating with no-gravity set.
			SymbioteState held = Symbiote.state(player);
			if (held.tendrilGrabHeld) {
				releaseGrabQuietly(player, held, now);
			}
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
