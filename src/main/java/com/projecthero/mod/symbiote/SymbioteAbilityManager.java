package com.projecthero.mod.symbiote;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.SonicVulnerability;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Normal Symbiote Host's abilities (v0.9.23 layout). Every ability is available whenever the
 * player is bonded -- the black suit no longer has to be worn ({@link SymbioteVitalsManager#usable})
 * -- but a spent Symbiote health bar locks them all until it recovers.
 *
 * <pre>
 *   R  Tendril Strike (30 blocks)      Shift+R  Tendril Sweep (cone, Slow 7 s)
 *   Shift+G  Tendril Grab (press, press again to throw)
 *   Ability 3 (Z)  Symbiote Lunge (20 blocks, ram = 15 dmg)   Shift+Ability 3  Symbiote Grapple (25 blocks; needs an anchor; pulls items)
 *   Z  Tendril Barrage / Blade Slash   Shift+hold Z  Symbiote Onslaught (ultimate)
 *   V  Symbiote Blade (toggle)         Shift+V  Symbiote Shield (toggle)
 *   C  Symbiote Spikes (Thorns IV toggle)
 * </pre>
 */
public final class SymbioteAbilityManager {
	private static final int CD_TENDRIL_STRIKE = 30;   // 1.5s
	private static final int CD_TENDRIL_SWEEP = 200;   // 10s
	private static final int CD_LEAP = 40;             // 2s
	private static final int CD_BARRAGE = 300;         // 15s
	private static final int CD_TENDRIL_GRAB = 100;    // 5s
	private static final int CD_SPIKE_VOLLEY = 18;     // ~0.9s

	private static final double SPIKE_RANGE = 26.0;
	private static final double SPIKE_CONE_DOT = 0.965; // ~15 degree forward cone
	private static final float SPIKE_DAMAGE = 5.0f;
	private static final int SPIKE_MAX_TARGETS = 3;

	private static final int LEAP_NO_FALL_TICKS = 600;
	private static final float LEAP_RAM_DAMAGE = 15.0f;

	private static final float SHIELD_GUARD_DRAIN = 1.0f;
	private static final float SHIELD_GUARD_REGEN = 2.0f;

	private static final double TENDRIL_STRIKE_RANGE = 30.0;
	private static final double SWEEP_RANGE = 9.0;
	private static final double SWEEP_CONE_DOT = 0.35;

	private static final double GRAB_RANGE = 15.0;
	private static final double GRAB_HOLD_DISTANCE = 2.6;
	private static final int GRAB_MAX_HOLD_TICKS = 70;
	private static final float GRAB_THROW_DAMAGE = 7.0f;

	private static final int BARRAGE_DURATION = 26;
	private static final int BARRAGE_HIT_INTERVAL = 4;
	private static final float BARRAGE_HIT_DAMAGE = 4.0f;
	private static final double BARRAGE_RANGE = 7.0;

	private static final double BLADE_SLASH_RANGE = 5.0;
	private static final double BLADE_SLASH_CONE_DOT = 0.2;
	private static final float BLADE_SLASH_DAMAGE = 8.0f;

	private static final int CD_ONSLAUGHT = 1200;
	private static final int ONSLAUGHT_CHARGE_TICKS = 60;
	private static final double ONSLAUGHT_RADIUS = 6.0;
	private static final int ONSLAUGHT_DOT_TICKS = 100;

	private static final double GRAPPLE_RANGE = 25.0;
	private static final int CD_GRAPPLE = 60;
	private static final int GRAPPLE_PULL_TICKS = 20;

	private static final Map<Integer, Long> LEAP_NO_FALL_UNTIL = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> GRAPPLE_READY_AT = new ConcurrentHashMap<>();
	private static final Map<Integer, double[]> GRAPPLE_PULL = new ConcurrentHashMap<>();
	/** Active Tendril Barrage: {endTick, lastHitTick}. */
	private static final Map<Integer, long[]> BARRAGE = new ConcurrentHashMap<>();
	/** Onslaught victims to keep spraying with symbiote particles: casterId -> {endTick, victimId...}. */
	private static final Map<Integer, long[]> ONSLAUGHT_VICTIMS = new ConcurrentHashMap<>();

	private SymbioteAbilityManager() {
	}

	/** v0.9.23: a bonded Normal host owns the six slots whether or not the suit is currently worn. */
	public static boolean hasContext(ServerPlayer player) {
		return Symbiote.hasSymbiote(player) && SymbioteHostType.of(player) == SymbioteHostType.NORMAL;
	}

	public static boolean shieldActive(ServerPlayer player) {
		return Symbiote.state(player).shieldHeld;
	}

	public static boolean leapFallProtected(ServerPlayer player, long now) {
		Long until = LEAP_NO_FALL_UNTIL.get(player.getId());
		return until != null && now < until;
	}

	public static boolean onslaughtCharging(ServerPlayer player) {
		return Symbiote.state(player).onslaughtChargeStart >= 0;
	}

	/** Is the Symbiote Grapple (Shift+X) off cooldown? Feeds the Symbiote's voice. */
	public static boolean grappleReady(ServerPlayer player, long now) {
		Long readyAt = GRAPPLE_READY_AT.get(player.getId());
		return readyAt == null || now >= readyAt;
	}

	/** Is Symbiote Leap (X) off cooldown? */
	public static boolean leapReady(ServerPlayer player, long now) {
		return now >= Symbiote.state(player).abilityCooldowns.get(AbilitySlot.SLOT_3.index());
	}

	/** Is Symbiote Onslaught (Shift+hold Z) off cooldown and not already charging? */
	public static boolean onslaughtReady(ServerPlayer player, long now) {
		SymbioteState s = Symbiote.state(player);
		return s.onslaughtChargeStart < 0
				&& now >= s.abilityCooldowns.get(AbilitySlot.SLOT_4.index());
	}

	/** Is Symbiote Shield (Shift+V) available -- not up already, and the guard bar has charge? */
	public static boolean shieldReady(ServerPlayer player) {
		SymbioteState s = Symbiote.state(player);
		return !s.shieldHeld && s.shieldGuard >= SymbioteState.SHIELD_GUARD_MAX * 0.5f;
	}

	public static void clearSessionState() {
		LEAP_NO_FALL_UNTIL.clear();
		LUNGE.clear();
		GRAPPLE_READY_AT.clear();
		GRAPPLE_PULL.clear();
		BARRAGE.clear();
		ONSLAUGHT_VICTIMS.clear();
	}

	public static void clearFor(ServerPlayer player) {
		LEAP_NO_FALL_UNTIL.remove(player.getId());
		LUNGE.remove(player.getId());
		GRAPPLE_READY_AT.remove(player.getId());
		GRAPPLE_PULL.remove(player.getId());
		BARRAGE.remove(player.getId());
		ONSLAUGHT_VICTIMS.remove(player.getId());
	}

	// ---------------- dispatch ----------------

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		long now = player.level().getGameTime();
		if (pressed && SonicVulnerability.isDisrupted(player, now)) {
			player.displayClientMessage(
					Component.translatable("message.projecthero.symbiote.sonic_disrupted"), true);
			return;
		}
		boolean sneak = player.isShiftKeyDown();

		// Onslaught (Shift + hold Z): a press starts the charge, the release fires it -- both edges must
		// reach handleOnslaught. A normal (no-sneak) Z press falls through to Barrage / Blade Slash.
		if (slot == AbilitySlot.SLOT_4) {
			if (Symbiote.state(player).onslaughtChargeStart >= 0) {
				handleOnslaught(player, pressed, now);
				return;
			}
			if (sneak) {
				if (pressed) {
					handleOnslaught(player, true, now);
				}
				return;
			}
		}
		// Symbiote Shield (Shift + V): a toggle now.
		if (slot == AbilitySlot.SLOT_5 && pressed && sneak) {
			toggleShield(player);
			return;
		}
		// G: fire a Symbiote Spike volley. Shift + G grabs; a bare G press while already holding a target
		// throws it; otherwise a bare G press shoots spikes.
		if (slot == AbilitySlot.SLOT_2) {
			if (pressed) {
				if (sneak || Symbiote.state(player).tendrilGrabHeld) {
					handleTendrilGrab(player, now, sneak);
				} else if (SymbioteVitalsManager.usable(player)) {
					tryWithCooldown(player, AbilitySlot.SLOT_2.index(), now, CD_SPIKE_VOLLEY,
							() -> fireSpikeVolley(player));
				} else {
					player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
				}
			}
			return;
		}
		if (!pressed) {
			return;
		}
		if (!SymbioteVitalsManager.usable(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
			return;
		}

		switch (slot) {
			case SLOT_1 -> {
				if (sneak) {
					tryWithCooldown(player, 0, now, CD_TENDRIL_SWEEP, () -> tendrilSweep(player));
				} else {
					tryWithCooldown(player, 0, now, CD_TENDRIL_STRIKE, () -> tendrilStrike(player, TENDRIL_STRIKE_RANGE));
				}
			}
			case SLOT_3 -> {
				if (sneak) {
					handleGrapple(player, now);
				} else {
					tryWithCooldown(player, 2, now, CD_LEAP, () -> symbioteLunge(player));
				}
			}
			case SLOT_4 -> {
				if (SymbioteVitalsManager.bladeActive(player)) {
					tryWithCooldown(player, 3, now, CD_BARRAGE, () -> bladeSlash(player));
				} else {
					tryWithCooldown(player, 3, now, CD_BARRAGE, () -> startBarrage(player, now));
				}
			}
			case SLOT_5 -> SymbioteVitalsManager.toggleBlade(player);
			case SLOT_6 -> SymbioteVitalsManager.toggleThorns(player);
			default -> {
			}
		}
	}

	private interface AbilityAttempt {
		boolean run();
	}

	private static void tryWithCooldown(ServerPlayer player, int index, long now, int cooldown, AbilityAttempt attempt) {
		long readyAt = Symbiote.state(player).abilityCooldowns.get(index);
		if (now < readyAt) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.ability_cooldown",
					String.format(java.util.Locale.ROOT, "%.1f", (readyAt - now) / 20.0f)), true);
			return;
		}
		if (attempt.run()) {
			setCooldown(player, index, now, cooldown);
		}
	}

	private static void setCooldown(ServerPlayer player, int index, long now, int ticks) {
		SymbioteState c = Symbiote.state(player).copy();
		c.abilityCooldowns.set(index, now + ticks);
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
	}

	// ---------------- Tendril Strike / Sweep ----------------

	private static boolean tendrilStrike(ServerPlayer player, double range) {
		LivingEntity target = AbilityHelpers.raycastEntity(player, range);
		if (target == null) {
			return false;
		}
		float damage = 9.0f + player.getRandom().nextFloat() * 2.0f;
		if (!AbilityHelpers.hurtLands(player, target, damage)) {
			return false;
		}
		AbilityHelpers.knockbackFrom(target, player.position(), 0.9);
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.6));
		Vec3 hit = target.position().add(0, target.getBbHeight() * 0.5, 0);
		AbilityHelpers.line(level, hand, hit, ParticleTypes.SQUID_INK, 4.0);
		AbilityHelpers.burst(level, hit, ParticleTypes.SQUID_INK, 14, 0.3);
		AbilityHelpers.burst(level, hit, ParticleTypes.CRIT, 6, 0.3);
		SymbioteSounds.organic(player, 0.8f, 0.5f);
		return true;
	}

	/** Shift + R -- a fan of tendrils that sweeps everything in front and slows it for 7 seconds. */
	private static boolean tendrilSweep(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 look = player.getLookAngle();
		List<LivingEntity> hit = coneTargets(player, SWEEP_RANGE, SWEEP_CONE_DOT);
		for (LivingEntity target : hit) {
			AbilityHelpers.hurt(player, target, 4.0f + player.getRandom().nextFloat() * 2.0f);
			AbilityHelpers.slow7s(target);
			AbilityHelpers.knockbackFrom(target, player.position(), 0.4);
			AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
					ParticleTypes.SQUID_INK, 16, 0.35);
		}
		Vec3 eye = player.getEyePosition();
		for (double a = -0.9; a <= 0.9; a += 0.18) {
			Vec3 dir = look.yRot((float) a).normalize();
			AbilityHelpers.line(level, eye.add(dir.scale(0.5)), eye.add(dir.scale(SWEEP_RANGE)),
					ParticleTypes.SQUID_INK, 2.0);
		}
		SymbioteSounds.organic(player, 1.0f, 0.4f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.5f, 1.4f);
		return true;
	}

	private static List<LivingEntity> coneTargets(ServerPlayer player, double range, double dotThreshold) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		return AbilityHelpers.enemiesAround(player, player.position(), range).stream().filter(e -> {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			return to.lengthSqr() > 0.01 && to.normalize().dot(look) >= dotThreshold;
		}).toList();
	}

	// ---------------- Symbiote Spikes (G) ----------------

	/** Fire a short spread of living spikes at whatever is in the narrow cone the player is aiming down. */
	private static boolean fireSpikeVolley(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		int struck = 0;
		for (LivingEntity target : coneTargets(player, SPIKE_RANGE, SPIKE_CONE_DOT)) {
			if (AbilityHelpers.hurtLands(player, target, SPIKE_DAMAGE)) {
				AbilityHelpers.knockbackFrom(target, player.position(), 0.35);
				Vec3 hit = target.position().add(0, target.getBbHeight() * 0.5, 0);
				AbilityHelpers.line(level, eye.add(look.scale(0.5)), hit, ParticleTypes.SQUID_INK, 4.0);
				AbilityHelpers.burst(level, hit, ParticleTypes.CRIT, 6, 0.25);
				AbilityHelpers.burst(level, hit, ParticleTypes.SQUID_INK, 8, 0.25);
				if (++struck >= SPIKE_MAX_TARGETS) {
					break;
				}
			}
		}

		// Always show the spikes leaving the arm, hit or miss.
		for (int i = -1; i <= 1; i++) {
			Vec3 dir = look.yRot(i * 0.09f).normalize();
			AbilityHelpers.line(level, eye.add(dir.scale(0.5)), eye.add(dir.scale(SPIKE_RANGE * 0.7)),
					ParticleTypes.SQUID_INK, 3.0);
		}
		SymbioteSounds.organic(player, 0.9f, 1.2f);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.4f);
		return true;
	}

	// ---------------- Tendril Grab (Shift+G, tap-tap) ----------------

	private static void handleTendrilGrab(ServerPlayer player, long now, boolean sneak) {
		SymbioteState s = Symbiote.state(player);
		if (s.tendrilGrabHeld) {
			throwGrabbed(player, s, now);
			return;
		}
		if (!sneak) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.grab_needs_sneak"), true);
			return;
		}
		if (!SymbioteVitalsManager.usable(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
			return;
		}
		long readyAt = s.abilityCooldowns.get(AbilitySlot.SLOT_2.index());
		if (now < readyAt) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.ability_cooldown",
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
		SymbioteSounds.organic(player, 0.9f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.grab_seized"), true);
	}

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
			SymbioteSounds.organic(player, 1.0f, 0.4f);
		}
		clearGrab(player, s, now, true);
	}

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
			c.abilityCooldowns.set(AbilitySlot.SLOT_2.index(), now + CD_TENDRIL_GRAB);
		}
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
	}

	// ---------------- Symbiote Lunge (Ability 3) ----------------

	/** How far one lunge impulse carries the host along the aim line. */
	private static final double LUNGE_BLOCKS = 20.0;
	/** Ticks after which a lunge stops checking for a ram, even if the host is somehow still airborne. */
	private static final int LUNGE_MAX_TICKS = 40;

	/** Active lunges: {endTick, startTick}. The flight itself is ballistic -- one launch, no held push. */
	private static final Map<Integer, double[]> LUNGE = new ConcurrentHashMap<>();

	private static boolean symbioteLunge(ServerPlayer player) {
		long now = player.level().getGameTime();
		Vec3 launch = AbilityHelpers.ballisticLaunch(player.getLookAngle(), LUNGE_BLOCKS, player.onGround());
		LUNGE.put(player.getId(), new double[]{now + LUNGE_MAX_TICKS, now});
		LEAP_NO_FALL_UNTIL.put(player.getId(), now + LEAP_NO_FALL_TICKS);
		AbilityHelpers.launchSelf(player, launch);
		ServerLevel level = AbilityHelpers.level(player);
		AbilityHelpers.burst(level, player.position(), ParticleTypes.SQUID_INK, 24, 0.3);
		AbilityHelpers.burst(level, player.position(), ParticleTypes.POOF, 10, 0.4);
		SymbioteSounds.lash(player, 1.0f, 0.7f);
		return true;
	}

	private static void tickLunge(ServerPlayer player, long now) {
		if (leapFallProtected(player, now)) {
			// trailing wisp so it reads as the Symbiote launching the host
			AbilityHelpers.level(player).sendParticles(ParticleTypes.SQUID_INK,
					player.getX(), player.getY() + player.getBbHeight() * 0.4, player.getZ(), 3, 0.15, 0.2, 0.15, 0.01);
		}
		double[] l = LUNGE.get(player.getId());
		if (l == null) {
			return;
		}
		boolean travelled = now - (long) l[1] >= 3;
		if (now >= (long) l[0] || (travelled && player.onGround())) {
			LUNGE.remove(player.getId());
			return;
		}
		// v0.12.1: the launch is a real impulse now (vanilla gravity and drag carry it), so the only
		// per-tick work is the ram check -- the host punches through the first creature it meets.
		for (LivingEntity target : AbilityHelpers.enemiesAround(player, player.position().add(0, 0.9 * player.getScale(), 0), 2.0 * player.getScale())) {
			if (AbilityHelpers.hurtLands(player, target, LEAP_RAM_DAMAGE)) {
				AbilityHelpers.knockbackFrom(target, player.position(), 1.6);
				AbilityHelpers.burst(AbilityHelpers.level(player),
						target.position().add(0, target.getBbHeight() * 0.5, 0), ParticleTypes.SQUID_INK, 26, 0.5);
				SymbioteSounds.lash(player, 1.0f, 0.6f);
				LUNGE.remove(player.getId());
				return;
			}
		}
	}

	// ---------------- Symbiote Grapple (Shift + X) ----------------

	private static void handleGrapple(ServerPlayer player, long now) {
		Long readyAt = GRAPPLE_READY_AT.get(player.getId());
		if (readyAt != null && now < readyAt) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.ability_cooldown",
					String.format(java.util.Locale.ROOT, "%.1f", (readyAt - now) / 20.0f)), true);
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		// An item on the ground / in the air along the aim line is reeled in, Spider-Man style.
		ItemEntity item = nearestItemAlongAim(player, eye, look);
		if (item != null) {
			GRAPPLE_READY_AT.put(player.getId(), now + CD_GRAPPLE);
			Vec3 toPlayer = player.position().add(0, 0.4, 0).subtract(item.position());
			item.setDeltaMovement(toPlayer.normalize().scale(Math.min(2.0, 0.5 + toPlayer.length() * 0.15)));
			item.setNoPickUpDelay();
			ServerLevel level = AbilityHelpers.level(player);
			AbilityHelpers.line(level, eye.add(look.scale(0.4)), item.position(), ParticleTypes.SQUID_INK, 3.0);
			SymbioteSounds.organic(player, 0.9f, 0.7f);
			return;
		}

		// v0.11.15: the grapple needs something to hold onto -- a block or a creature within range. Aimed at
		// open air the tendril still lashes out the full distance, finds nothing, and snaps back.
		boolean hasAnchor = AbilityHelpers.raycastEntity(player, GRAPPLE_RANGE) != null
				|| AbilityHelpers.raycastBlock(player, GRAPPLE_RANGE).getType() != net.minecraft.world.phys.HitResult.Type.MISS;
		if (!hasAnchor) {
			ServerLevel missLevel = AbilityHelpers.level(player);
			AbilityHelpers.line(missLevel, eye.add(look.scale(0.4)), eye.add(look.scale(GRAPPLE_RANGE)),
					ParticleTypes.SQUID_INK, 3.0);
			SymbioteSounds.organic(player, 0.6f, 0.5f);
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.grapple_no_anchor"), true);
			return;
		}
		Vec3 anchor = AbilityHelpers.aimPoint(player, GRAPPLE_RANGE);
		if (anchor.distanceTo(eye) < 3.0) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.grapple_too_close"), true);
			return;
		}
		// Aim the pull at a point a little SHORT of the surface, along the line of sight, so the player
		// fetches up next to the block rather than being driven into its face -- this is what used to
		// leave you stuck against a wall when the grapple point was below you.
		Vec3 pullTarget = anchor;
		Vec3 back = eye.subtract(anchor);
		if (back.length() > 2.5) {
			pullTarget = anchor.add(back.normalize().scale(1.6));
		}
		GRAPPLE_READY_AT.put(player.getId(), now + CD_GRAPPLE);
		GRAPPLE_PULL.put(player.getId(),
				new double[]{pullTarget.x, pullTarget.y, pullTarget.z, now + GRAPPLE_PULL_TICKS, now});
		LEAP_NO_FALL_UNTIL.put(player.getId(), now + LEAP_NO_FALL_TICKS);
		ServerLevel level = AbilityHelpers.level(player);
		AbilityHelpers.line(level, eye.add(look.scale(0.4)), anchor, ParticleTypes.SQUID_INK, 3.0);
		SymbioteSounds.organic(player, 0.9f, 0.4f);
		SymbioteSounds.organic(player, 0.9f, 0.6f);
	}

	private static ItemEntity nearestItemAlongAim(ServerPlayer player, Vec3 eye, Vec3 look) {
		AABB box = player.getBoundingBox().inflate(GRAPPLE_RANGE);
		ItemEntity best = null;
		double bestDot = 0.94;
		for (ItemEntity item : AbilityHelpers.level(player).getEntitiesOfClass(ItemEntity.class, box)) {
			Vec3 to = item.position().subtract(eye);
			double dist = to.length();
			if (dist < 2.0 || dist > GRAPPLE_RANGE) {
				continue;
			}
			double dot = to.scale(1.0 / dist).dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = item;
			}
		}
		return best;
	}

	private static void tickGrapplePull(ServerPlayer player, long now) {
		double[] pull = GRAPPLE_PULL.get(player.getId());
		if (pull == null) {
			return;
		}
		Vec3 anchor = new Vec3(pull[0], pull[1], pull[2]);
		long startTick = pull.length > 4 ? (long) pull[4] : now;
		Vec3 toAnchor = anchor.subtract(player.getEyePosition());
		// End the pull when we arrive, when time runs out, or -- after a couple of ticks of travel --
		// the moment the player collides with terrain. That last case is the fix for "stuck on blocks":
		// re-setting the velocity into a wall/floor every tick was what pinned the player in place.
		boolean travelled = now - startTick >= 2;
		boolean fetchedUp = travelled && (player.horizontalCollision
				|| (player.onGround() && toAnchor.y < 1.0));
		if (now >= (long) pull[3] || toAnchor.length() < 2.5 || fetchedUp) {
			GRAPPLE_PULL.remove(player.getId());
			return;
		}
		Vec3 dir = toAnchor.normalize();
		double speed = Math.min(1.7, 0.7 + toAnchor.length() * 0.08);
		Vec3 vel = dir.scale(speed);
		// Never let a downward grapple slam the host straight into the ground -- keep a slight lift so
		// it carries them ACROSS to a lower ledge instead of drilling them into it.
		double vy = Math.max(vel.y, -0.4) + 0.12;
		AbilityHelpers.launchSelf(player, new Vec3(vel.x, vy, vel.z));
		AbilityHelpers.level(player).sendParticles(ParticleTypes.SQUID_INK,
				player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
	}

	// ---------------- Tendril Barrage / Blade Slash (Z) ----------------

	private static boolean startBarrage(ServerPlayer player, long now) {
		BARRAGE.put(player.getId(), new long[]{now + BARRAGE_DURATION, 0L});
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.7f, 1.3f);
		AbilityHelpers.burst(AbilityHelpers.level(player), player.position().add(0, 1, 0),
				ParticleTypes.SQUID_INK, 20, 0.5);
		return true;
	}

	private static void tickBarrage(ServerPlayer player, long now) {
		long[] b = BARRAGE.get(player.getId());
		if (b == null) {
			return;
		}
		if (now >= b[0] || !SymbioteVitalsManager.usable(player)) {
			BARRAGE.remove(player.getId());
			return;
		}
		if (now - b[1] < BARRAGE_HIT_INTERVAL) {
			return;
		}
		b[1] = now;
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 eye = player.getEyePosition();
		List<LivingEntity> targets = coneTargets(player, BARRAGE_RANGE, 0.1);
		int struck = 0;
		for (LivingEntity target : targets) {
			target.invulnerableTime = 0;
			if (AbilityHelpers.hurtLands(player, target, BARRAGE_HIT_DAMAGE)) {
				Vec3 hit = target.position().add(0, target.getBbHeight() * 0.5, 0);
				AbilityHelpers.line(level, eye.add(player.getLookAngle().scale(0.5)), hit, ParticleTypes.SQUID_INK, 3.0);
				AbilityHelpers.burst(level, hit, ParticleTypes.SQUID_INK, 8, 0.3);
				if (++struck >= 3) {
					break;
				}
			}
		}
		SymbioteSounds.organic(player, 0.5f, 0.6f);
	}

	private static boolean bladeSlash(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		List<LivingEntity> targets = coneTargets(player, BLADE_SLASH_RANGE, BLADE_SLASH_CONE_DOT);
		for (LivingEntity target : targets) {
			if (AbilityHelpers.hurtLands(player, target, BLADE_SLASH_DAMAGE)) {
				target.hurt(player.damageSources().magic(), 2.0f);
				AbilityHelpers.knockbackFrom(target, player.position(), 0.6);
				AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
						ParticleTypes.SQUID_INK, 20, 0.4);
			}
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		for (double a = -0.7; a <= 0.7; a += 0.14) {
			Vec3 dir = look.yRot((float) a).normalize();
			AbilityHelpers.line(level, eye.add(dir.scale(0.4)), eye.add(dir.scale(BLADE_SLASH_RANGE)),
					ParticleTypes.SQUID_INK, 3.0);
		}
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
		SymbioteSounds.organic(player, 0.8f, 0.4f);
		return true;
	}

	// ---------------- Symbiote Onslaught (Shift + hold Z) ----------------

	private static void handleOnslaught(ServerPlayer player, boolean pressed, long now) {
		SymbioteState s = Symbiote.state(player);
		if (pressed) {
			if (s.onslaughtChargeStart >= 0) {
				return;
			}
			if (!SymbioteVitalsManager.usable(player)) {
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
				return;
			}
			long readyAt = s.abilityCooldowns.get(AbilitySlot.SLOT_4.index());
			if (now < readyAt) {
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.ability_cooldown",
						String.format(java.util.Locale.ROOT, "%.1f", (readyAt - now) / 20.0f)), true);
				return;
			}
			SymbioteState c = s.copy();
			c.onslaughtChargeStart = now;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.onslaught_charging"), true);
			AbilityHelpers.sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f);
		} else if (s.onslaughtChargeStart >= 0) {
			if (now - s.onslaughtChargeStart >= ONSLAUGHT_CHARGE_TICKS) {
				fireOnslaught(player, s, now);
			} else {
				cancelOnslaught(player, s, "message.projecthero.symbiote.onslaught_interrupted");
			}
		}
	}

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
		// enemies caught in the radius are already slowed while it charges
		for (LivingEntity target : AbilityHelpers.enemiesAround(player, player.position(), ONSLAUGHT_RADIUS)) {
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 20, 1);
			if (player.tickCount % 4 == 0) {
				level.sendParticles(ParticleTypes.SQUID_INK, target.getX(),
						target.getY() + target.getBbHeight() * 0.5, target.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
			}
		}
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
		List<LivingEntity> victims = AbilityHelpers.enemiesAround(player, center, ONSLAUGHT_RADIUS);
		long[] track = new long[victims.size() + 1];
		track[0] = now + ONSLAUGHT_DOT_TICKS;
		int idx = 1;
		for (LivingEntity target : victims) {
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, ONSLAUGHT_DOT_TICKS, 2, false, true, true));
			AbilityHelpers.applyControl(target, MobEffects.BLINDNESS, ONSLAUGHT_DOT_TICKS, 0);
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, ONSLAUGHT_DOT_TICKS, 1);
			Vec3 hit = target.position().add(0, target.getBbHeight() * 0.5, 0);
			AbilityHelpers.burst(level, hit, ParticleTypes.SQUID_INK, 40, target.getBbWidth() * 0.6 + 0.4);
			AbilityHelpers.burst(level, hit, ParticleTypes.LARGE_SMOKE, 12, 0.4);
			track[idx++] = target.getId();
		}
		if (victims.isEmpty()) {
			ONSLAUGHT_VICTIMS.remove(player.getId());
		} else {
			ONSLAUGHT_VICTIMS.put(player.getId(), track);
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

	private static void tickOnslaughtVictims(ServerPlayer player, long now) {
		long[] track = ONSLAUGHT_VICTIMS.get(player.getId());
		if (track == null) {
			return;
		}
		if (now >= track[0]) {
			ONSLAUGHT_VICTIMS.remove(player.getId());
			return;
		}
		if (player.tickCount % 3 != 0) {
			return;
		}
		ServerLevel level = AbilityHelpers.level(player);
		for (int i = 1; i < track.length; i++) {
			if (level.getEntity((int) track[i]) instanceof LivingEntity victim && victim.isAlive()) {
				level.sendParticles(ParticleTypes.SQUID_INK, victim.getX(),
						victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
						6, victim.getBbWidth() * 0.5, victim.getBbHeight() * 0.5, victim.getBbWidth() * 0.5, 0.01);
			}
		}
	}

	// ---------------- Symbiote Shield (Shift + V toggle) ----------------

	private static void toggleShield(ServerPlayer player) {
		SymbioteState s = Symbiote.state(player);
		if (s.shieldHeld) {
			SymbioteState c = s.copy();
			c.shieldHeld = false;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			return;
		}
		if (!SymbioteVitalsManager.usable(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
			return;
		}
		if (s.shieldGuard <= 0.0f) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.shield_spent"), true);
			return;
		}
		SymbioteState c = s.copy();
		c.shieldHeld = true;
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.5f);
		AbilityHelpers.burst(AbilityHelpers.level(player), player.position().add(0, 1, 0),
				ParticleTypes.SQUID_INK, 20, 0.4);
	}

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
			Vec3 look = player.getLookAngle();
			Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
			Vec3 up = new Vec3(0, 1, 0);
			Vec3 center = player.getEyePosition().add(look.scale(0.9)).add(0, -0.35, 0);
			for (int i = 0; i < 14; i++) {
				double ang = (Math.PI * 2 * i) / 14.0;
				Vec3 edge = center.add(right.scale(Math.cos(ang) * 0.55)).add(up.scale(Math.sin(ang) * 0.7));
				level.sendParticles(ParticleTypes.SQUID_INK, edge.x, edge.y, edge.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 4, 0.28, 0.36, 0.05, 0.0);
			if (depleted) {
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.shield_spent"), true);
			}
		} else if (s.shieldGuard < SymbioteState.SHIELD_GUARD_MAX) {
			SymbioteState c = s.copy();
			c.shieldGuard = Math.min(SymbioteState.SHIELD_GUARD_MAX, s.shieldGuard + SHIELD_GUARD_REGEN);
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		}
	}

	// ---------------- sound-attack shock + resurrection ----------------

	/** A sound attack rips every active Symbiote ability out of the host's control at once. */
	public static void disrupt(ServerPlayer player) {
		long now = player.level().getGameTime();
		SymbioteState s = Symbiote.state(player);
		if (s.tendrilGrabHeld) {
			releaseGrabQuietly(player, s, now);
		}
		s = Symbiote.state(player);
		if (s.onslaughtChargeStart >= 0) {
			cancelOnslaught(player, s, null);
		}
		s = Symbiote.state(player);
		if (s.shieldHeld) {
			SymbioteState c = s.copy();
			c.shieldHeld = false;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		}
		GRAPPLE_PULL.remove(player.getId());
		BARRAGE.remove(player.getId());
		LUNGE.remove(player.getId());
		ONSLAUGHT_VICTIMS.remove(player.getId());
	}

	private static final double RESURRECT_RADIUS = 20.0;
	private static final double RESURRECT_PUSH = 2.8;

	/**
	 * The resurrection burst: massive tendrils erupt from the host in every direction and hurl every living
	 * thing within 20 blocks away from them. Nothing takes damage -- it is pure separation, so the freshly
	 * revived host has room to breathe.
	 */
	public static void resurrectionBlast(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 origin = player.position().add(0, player.getBbHeight() * 0.6, 0);

		// The tendrils: a wide fan of long, arcing lines of black ichor reaching the full radius.
		int rays = 28;
		for (int i = 0; i < rays; i++) {
			double ang = (Math.PI * 2 * i) / rays;
			double rise = 0.05 + (i % 3) * 0.12;
			Vec3 dir = new Vec3(Math.cos(ang), rise, Math.sin(ang)).normalize();
			Vec3 prev = origin;
			for (int seg = 1; seg <= 10; seg++) {
				double dist = seg * (RESURRECT_RADIUS / 10.0);
				double sway = Math.sin(seg * 0.9 + i) * 0.9;
				Vec3 p = origin.add(dir.scale(dist)).add(-dir.z * sway * 0.3, Math.sin(seg * 0.6) * 0.6, dir.x * sway * 0.3);
				AbilityHelpers.line(level, prev, p, ParticleTypes.SQUID_INK, 2.5);
				prev = p;
			}
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y, origin.z, 60, 1.2, 0.8, 1.2, 0.05);
		level.sendParticles(ParticleTypes.SONIC_BOOM, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
		SymbioteSounds.lash(player, 1.6f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WARDEN_ROAR, net.minecraft.sounds.SoundSource.PLAYERS, 1.6f, 0.9f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.RAVAGER_ROAR, net.minecraft.sounds.SoundSource.PLAYERS, 1.4f, 0.5f);

		AABB box = player.getBoundingBox().inflate(RESURRECT_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
				x -> x != player && x.isAlive() && !x.isSpectator())) {
			Vec3 away = e.position().subtract(player.position());
			Vec3 flat = new Vec3(away.x, 0, away.z);
			if (flat.lengthSqr() < 0.01) {
				flat = new Vec3(player.getRandom().nextDouble() - 0.5, 0, player.getRandom().nextDouble() - 0.5);
			}
			if (flat.length() > RESURRECT_RADIUS) {
				continue;
			}
			Vec3 vel = flat.normalize().scale(RESURRECT_PUSH).add(0, 0.9, 0);
			e.setDeltaMovement(vel);
			e.hurtMarked = true;
			e.hasImpulse = true;
			if (e instanceof ServerPlayer sp) {
				sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
			}
			AbilityHelpers.burst(level, e.position().add(0, e.getBbHeight() * 0.5, 0), ParticleTypes.SQUID_INK, 10, 0.3);
		}
	}

	// ---------------- per-tick upkeep ----------------

	public static void serverTick(ServerPlayer player) {
		if (!Symbiote.hasSymbiote(player)) {
			return;
		}
		long now = player.level().getGameTime();
		if (leapFallProtected(player, now) && player.onGround()) {
			LEAP_NO_FALL_UNTIL.remove(player.getId());
		}

		if (hasContext(player)) {
			wallAssist(player);
			tickShield(player);
			tickTendrilGrab(player, now);
			tickOnslaught(player, now);
			tickOnslaughtVictims(player, now);
			tickGrapplePull(player, now);
			tickBarrage(player, now);
			tickLunge(player, now);
		} else {
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
	 * Wall Assistance + Wall Climb: a Normal host against a wall while airborne falls slowly, and --
	 * while crouched against it -- climbs it like a ladder (look up to rise, level to cling, down to
	 * descend). Deliberately far lighter than Spider-Man's full {@code SpiderClimb} engine.
	 */
	private static void wallAssist(ServerPlayer player) {
		if (!player.horizontalCollision || player.onGround()) {
			return;
		}
		if (player.isShiftKeyDown()) {
			float pitch = player.getXRot();
			double vy = pitch < -25.0f ? 0.16 : (pitch > 25.0f ? -0.14 : 0.0);
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x * 0.2, vy, v.z * 0.2);
			player.resetFallDistance();
			player.hurtMarked = true;
			if (player.tickCount % 4 == 0) {
				AbilityHelpers.level(player).sendParticles(ParticleTypes.SQUID_INK,
						player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 2, 0.2, 0.3, 0.2, 0.0);
			}
			return;
		}
		Vec3 v = player.getDeltaMovement();
		if (v.y >= -0.1) {
			return;
		}
		player.setDeltaMovement(v.x * 0.7, Math.max(v.y, -0.10), v.z * 0.7);
		player.fallDistance *= 0.5f;
		player.hurtMarked = true;
	}
}
