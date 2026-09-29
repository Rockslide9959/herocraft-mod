package com.projecthero.mod.symbiote;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.SonicVulnerability;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.symbiote.entity.SymbioteSpikeEntity;
import com.projecthero.mod.symbiote.entity.SymbioteTendrilEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Normal Symbiote Host's abilities. Every ability is available whenever the player is bonded -- the
 * black suit does not have to be worn ({@link SymbioteVitalsManager#usable}) -- but a spent Biomass bar
 * locks them all until it recovers.
 *
 * <pre>
 *   R  Tendril Strike (30 blocks, can miss)        Shift+R  Tendril Sweep (cone, Slow 7 s)
 *   G  Symbiote Spike (one spike, 4 s)             Shift+G  Spike Fan (five spikes, 10 s)
 *   X  Symbiote Lunge (20 blocks, ram = 15 dmg)    Shift+X  Symbiote Grapple (30 blocks)
 *   Z  Tendril Barrage / Blade Slash               Shift+hold Z  Symbiote Onslaught (ultimate)
 *   V  Symbiote Blade (toggle)                     Shift+V  Symbiote Shield (toggle)
 *   C  Symbiote Spikes (Thorns toggle)             Shift+C  Tendril Grab (C again to throw)
 * </pre>
 *
 * <p>v0.13.19: every move comes out of the host's hands ({@link SymbioteHands}) and is drawn as a real living
 * tendril ({@link SymbioteTendrilEntity}) or spike ({@link SymbioteSpikeEntity}) rather than a particle line;
 * Tendril Strike and Tendril Barrage now fire whether or not something is in the sights, so they can miss;
 * each move plays a pose ({@link SymbioteAnim}). Tendril Grab moved from Shift+G to Shift+C to make room for
 * the Spike Fan.
 */
public final class SymbioteAbilityManager {
	private static final int CD_TENDRIL_STRIKE = 30;   // 1.5s
	private static final int CD_TENDRIL_SWEEP = 200;   // 10s
	private static final int CD_LEAP = 40;             // 2s
	private static final int CD_BARRAGE = 300;         // 15s
	private static final int CD_TENDRIL_GRAB = 100;    // 5s
	private static final int CD_SPIKE_SHOT = 80;       // 4s
	private static final int CD_SPIKE_FAN = 200;       // 10s

	/** The Spike Fan's five spikes, degrees either side of the aim. */
	private static final float[] SPIKE_FAN_YAW = { -20.0f, -10.0f, 0.0f, 10.0f, 20.0f };

	private static final int LEAP_NO_FALL_TICKS = 600;
	private static final float LEAP_RAM_DAMAGE = 15.0f;

	private static final double TENDRIL_STRIKE_RANGE = 30.0;
	private static final float TENDRIL_STRIKE_DAMAGE = 15.0f;
	private static final double SWEEP_RANGE = 9.0;
	private static final double SWEEP_CONE_DOT = 0.35;
	private static final float SWEEP_DAMAGE = 8.0f;

	private static final double GRAB_RANGE = 15.0;
	private static final double GRAB_HOLD_DISTANCE = 2.6;
	private static final int GRAB_MAX_HOLD_TICKS = 70;
	private static final float GRAB_THROW_DAMAGE = 7.0f;

	private static final int BARRAGE_DURATION = 26;
	private static final int BARRAGE_HIT_INTERVAL = 3;
	private static final float BARRAGE_HIT_DAMAGE = 4.0f;
	/** v0.13.19: the barrage reaches as far as Tendril Strike. */
	private static final double BARRAGE_RANGE = TENDRIL_STRIKE_RANGE;
	/** Each barrage tendril wanders up to this many degrees off the aim. */
	private static final float BARRAGE_SPREAD_DEGREES = 3.5f;

	private static final double BLADE_SLASH_RANGE = 5.0;
	private static final double BLADE_SLASH_CONE_DOT = 0.2;
	private static final float BLADE_SLASH_DAMAGE = 8.0f;

	/** v0.13.19: 60 s -> 90 s, a much bigger hit. */
	private static final int CD_ONSLAUGHT = 1800;
	private static final int ONSLAUGHT_CHARGE_TICKS = 60;
	private static final double ONSLAUGHT_RADIUS = 9.0;
	private static final float ONSLAUGHT_DAMAGE = 20.0f;
	private static final int ONSLAUGHT_DOT_TICKS = 160;

	/** v0.13.21: 25 -> 30 blocks. */
	public static final double GRAPPLE_RANGE = 30.0;
	private static final int CD_GRAPPLE = 60;
	private static final int GRAPPLE_PULL_TICKS = 20;

	/** The Symbiote's living black, and a deep purple sheen -- the Onslaught's shockwave colours. */
	private static final DustParticleOptions ICHOR = new DustParticleOptions(new org.joml.Vector3f(0.04f, 0.02f, 0.06f), 2.2f);
	private static final DustParticleOptions SHEEN = new DustParticleOptions(new org.joml.Vector3f(0.35f, 0.12f, 0.55f), 1.4f);

	private static final Map<Integer, Long> LEAP_NO_FALL_UNTIL = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> GRAPPLE_READY_AT = new ConcurrentHashMap<>();
	private static final Map<Integer, double[]> GRAPPLE_PULL = new ConcurrentHashMap<>();
	/** Active Tendril Barrage: {endTick, lastHitTick, shotsFired}. */
	private static final Map<Integer, long[]> BARRAGE = new ConcurrentHashMap<>();
	/** Onslaught victims to keep spraying with symbiote particles: casterId -> {endTick, victimId...}. */
	private static final Map<Integer, long[]> ONSLAUGHT_VICTIMS = new ConcurrentHashMap<>();
	/** The tendril currently holding something (grab / grapple): playerId -> tendril entity id. */
	private static final Map<Integer, Integer> HELD_TENDRIL = new ConcurrentHashMap<>();

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

	/** Is Symbiote Shield (Shift+V) available -- not up already, and the Biomass is not spent? */
	public static boolean shieldReady(ServerPlayer player) {
		return !Symbiote.state(player).shieldHeld && SymbioteVitalsManager.usable(player);
	}

	public static void clearSessionState() {
		LEAP_NO_FALL_UNTIL.clear();
		LUNGE.clear();
		GRAPPLE_READY_AT.clear();
		GRAPPLE_PULL.clear();
		BARRAGE.clear();
		ONSLAUGHT_VICTIMS.clear();
		HELD_TENDRIL.clear();
	}

	public static void clearFor(ServerPlayer player) {
		LEAP_NO_FALL_UNTIL.remove(player.getId());
		LUNGE.remove(player.getId());
		GRAPPLE_READY_AT.remove(player.getId());
		GRAPPLE_PULL.remove(player.getId());
		BARRAGE.remove(player.getId());
		ONSLAUGHT_VICTIMS.remove(player.getId());
		dropHeldTendril(player);
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
		if (!pressed) {
			return;
		}
		// Symbiote Shield (Shift + V): a toggle.
		if (slot == AbilitySlot.SLOT_5 && sneak) {
			toggleShield(player);
			return;
		}
		// C while holding something throws it -- whatever the Biomass says, you can always let go.
		if (slot == AbilitySlot.SLOT_6 && Symbiote.state(player).tendrilGrabHeld) {
			throwGrabbed(player, Symbiote.state(player), now);
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
					tryWithCooldown(player, 0, now, CD_TENDRIL_STRIKE, () -> tendrilStrike(player));
				}
			}
			case SLOT_2 -> {
				if (sneak) {
					spikeFan(player, now);
				} else {
					tryWithCooldown(player, AbilitySlot.SLOT_2.index(), now, CD_SPIKE_SHOT, () -> spikeShot(player));
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
			case SLOT_6 -> {
				if (sneak) {
					beginTendrilGrab(player, now);
				} else {
					SymbioteVitalsManager.toggleThorns(player);
				}
			}
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
			cooldownMessage(player, readyAt - now);
			return;
		}
		if (attempt.run()) {
			setCooldown(player, index, now, cooldown);
		}
	}

	private static void cooldownMessage(ServerPlayer player, long ticksLeft) {
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.ability_cooldown",
				String.format(java.util.Locale.ROOT, "%.1f", ticksLeft / 20.0f)), true);
	}

	private static void setCooldown(ServerPlayer player, int index, long now, int ticks) {
		SymbioteState c = Symbiote.state(player).copy();
		c.abilityCooldowns.set(index, now + ticks);
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
	}

	// ---------------- aiming helpers ----------------

	/**
	 * v0.13.21: {@link AbilityHelpers#enemiesAround} minus the caster's squadmates. Every Symbiote move -- the
	 * Normal host's, the Black Suit's and Agent Venom's -- picks its area targets through this, so a squadmate is
	 * never hit, slowed, withered, blinded or thrown, not merely spared the damage by the friendly-fire veto.
	 */
	public static List<LivingEntity> enemiesAround(ServerPlayer player, Vec3 center, double radius) {
		return AbilityHelpers.enemiesAround(player, center, radius).stream()
				.filter(e -> !Squads.areAllies(player, e)).toList();
	}

	/**
	 * What a tendril flying from the eyes along {@code dir} runs into first, within {@code range}: the first
	 * living thing, else the block face, else the end of its reach. Blocks stop it -- nothing through walls.
	 */
	private static Vec3 traceTendril(ServerPlayer player, Vec3 dir, double range, LivingEntity[] hitOut) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 eye = player.getEyePosition();
		Vec3 far = eye.add(dir.scale(range));
		HitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
		AABB sweep = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level, player, eye, end, sweep,
				e -> e != player && e.isPickable() && e.isAlive() && e instanceof LivingEntity && !(e instanceof ArmorStand)
						&& !Squads.areAllies(player, e)); // v0.13.21: tendrils pass a squadmate by
		if (ehr != null && ehr.getEntity() instanceof LivingEntity le) {
			hitOut[0] = le;
			return le.position().add(0, le.getBbHeight() * 0.5, 0);
		}
		hitOut[0] = null;
		return end;
	}

	private static Vec3 spread(ServerPlayer player, Vec3 dir, float degrees) {
		float yaw = (player.getRandom().nextFloat() * 2.0f - 1.0f) * degrees * ((float) Math.PI / 180.0f);
		float pitch = (player.getRandom().nextFloat() * 2.0f - 1.0f) * degrees * ((float) Math.PI / 180.0f);
		return dir.yRot(yaw).xRot(pitch).normalize();
	}

	// ---------------- Tendril Strike / Sweep ----------------

	/**
	 * R -- a tendril lashes out of the host's hand along the aim, up to 30 blocks. v0.13.19: it fires whether
	 * or not anything is in the sights, so it can miss; whatever it meets first takes 15.
	 */
	private static boolean tendrilStrike(ServerPlayer player) {
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 end = traceTendril(player, player.getLookAngle(), TENDRIL_STRIKE_RANGE, hit);
		LivingEntity target = hit[0];
		ServerLevel level = AbilityHelpers.level(player);
		SymbioteAnim.play(player, SymbioteAnim.TENDRIL_STRIKE);
		SymbioteTendrilEntity.fromHand(player, true, end, target, 11, 3, 0.14f);
		SymbioteSounds.lash(player, 0.9f, 0.8f);
		if (target != null && AbilityHelpers.hurtLands(player, target, TENDRIL_STRIKE_DAMAGE)) {
			AbilityHelpers.knockbackFrom(target, player.position(), 0.9);
			AbilityHelpers.burst(level, end, ParticleTypes.SQUID_INK, 14, 0.3);
			AbilityHelpers.burst(level, end, ParticleTypes.CRIT, 6, 0.3);
			SymbioteSounds.organic(player, 0.8f, 0.5f);
		} else {
			level.sendParticles(ParticleTypes.SQUID_INK, end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0.02);
		}
		return true;
	}

	/** Shift + R -- a fan of tendrils that sweeps everything in front and slows it for 7 seconds. */
	private static boolean tendrilSweep(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 look = player.getLookAngle();
		SymbioteAnim.play(player, SymbioteAnim.TENDRIL_SWEEP);
		List<LivingEntity> hit = coneTargets(player, SWEEP_RANGE, SWEEP_CONE_DOT);
		for (LivingEntity target : hit) {
			AbilityHelpers.hurt(player, target, SWEEP_DAMAGE + player.getRandom().nextFloat() * 2.0f);
			AbilityHelpers.slow7s(target);
			AbilityHelpers.knockbackFrom(target, player.position(), 0.5);
			AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
					ParticleTypes.SQUID_INK, 16, 0.35);
		}
		// seven tendrils fanning out left to right, each reaching a beat after the last: the sweep itself
		Vec3 eye = player.getEyePosition();
		for (int i = 0; i < 7; i++) {
			double a = 0.9 - i * 0.3;
			Vec3 dir = look.yRot((float) a).normalize();
			HitResult block = level.clip(new ClipContext(eye, eye.add(dir.scale(SWEEP_RANGE)),
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(dir.scale(SWEEP_RANGE)) : block.getLocation();
			SymbioteTendrilEntity.fromHand(player, true, end, null, 10 + i, 2 + i, 0.12f);
		}
		SymbioteSounds.organic(player, 1.0f, 0.4f);
		SymbioteSounds.lash(player, 1.0f, 0.6f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.5f, 1.4f);
		return true;
	}

	private static List<LivingEntity> coneTargets(ServerPlayer player, double range, double dotThreshold) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		return enemiesAround(player, player.position(), range).stream().filter(e -> {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			return to.lengthSqr() > 0.01 && to.normalize().dot(look) >= dotThreshold;
		}).toList();
	}

	// ---------------- Symbiote Spike (G) / Spike Fan (Shift + G) ----------------

	/** G -- one living spike, shot from the hand straight down the aim. */
	private static boolean spikeShot(ServerPlayer player) {
		SymbioteAnim.play(player, SymbioteAnim.SPIKE_SHOT);
		SymbioteSpikeEntity.shoot(player, SymbioteHands.right(player), player.getLookAngle());
		SymbioteSounds.organic(player, 0.9f, 1.3f);
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_THROW, 0.6f, 1.5f);
		return true;
	}

	/** Shift + G -- five spikes in a 40-degree fan, both hands. Its own 10 s cooldown. */
	private static void spikeFan(ServerPlayer player, long now) {
		SymbioteVitals v = SymbioteVitalsManager.vitals(player);
		if (now < v.spikeConeReadyAt) {
			cooldownMessage(player, v.spikeConeReadyAt - now);
			return;
		}
		SymbioteAnim.play(player, SymbioteAnim.SPIKE_FAN);
		SymbioteVitals c = SymbioteVitalsManager.vitals(player).copy();
		c.spikeConeReadyAt = now + CD_SPIKE_FAN;
		SymbioteVitalsManager.save(player, c);
		Vec3 look = player.getLookAngle();
		for (int i = 0; i < SPIKE_FAN_YAW.length; i++) {
			Vec3 dir = look.yRot(SPIKE_FAN_YAW[i] * ((float) Math.PI / 180.0f)).normalize();
			SymbioteSpikeEntity.shoot(player, SymbioteHands.hand(player, i % 2 == 0), dir);
		}
		SymbioteSounds.organic(player, 1.0f, 1.1f);
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_THROW, 0.9f, 1.2f);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.4f);
	}

	// ---------------- Tendril Grab (Shift + C, C again to throw) ----------------

	private static void beginTendrilGrab(ServerPlayer player, long now) {
		SymbioteState s = Symbiote.state(player);
		long readyAt = s.abilityCooldowns.get(AbilitySlot.SLOT_6.index());
		if (now < readyAt) {
			cooldownMessage(player, readyAt - now);
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, GRAB_RANGE);
		if (target == null || !AbilityHelpers.isValidGrabTarget(target, player) || Squads.areAllies(player, target)) {
			// the tendril still lashes out and comes back empty
			SymbioteAnim.play(player, SymbioteAnim.TENDRIL_STRIKE);
			SymbioteTendrilEntity.fromHand(player, true, AbilityHelpers.aimPoint(player, GRAB_RANGE), null, 9, 3, 0.12f);
			SymbioteSounds.lash(player, 0.7f, 0.9f);
			return;
		}
		SymbioteState c = s.copy();
		c.tendrilGrabTargetId = target.getId();
		c.tendrilGrabHeld = true;
		c.tendrilGrabStartTick = now;
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);

		target.setNoGravity(true);
		target.setDeltaMovement(Vec3.ZERO);
		SymbioteAnim.play(player, SymbioteAnim.GRAB);
		holdTendril(player, SymbioteTendrilEntity.fromHand(player, true,
				target.position().add(0, target.getBbHeight() * 0.5, 0), target, GRAB_MAX_HOLD_TICKS + 10, 3, 0.15f));
		AbilityHelpers.burst(AbilityHelpers.level(player), target.position().add(0, target.getBbHeight() * 0.5, 0),
				ParticleTypes.SQUID_INK, 10, 0.3);
		SymbioteSounds.organic(player, 0.9f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.grab_seized"), true);
	}

	private static void holdTendril(ServerPlayer player, SymbioteTendrilEntity tendril) {
		dropHeldTendril(player);
		if (tendril != null) {
			HELD_TENDRIL.put(player.getId(), tendril.getId());
		}
	}

	private static void dropHeldTendril(ServerPlayer player) {
		Integer id = HELD_TENDRIL.remove(player.getId());
		if (id != null && player.level().getEntity(id) instanceof SymbioteTendrilEntity t) {
			t.retract();
		}
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
		Vec3 hold = player.getEyePosition().add(player.getLookAngle().scale(GRAB_HOLD_DISTANCE * Math.max(1.0, player.getScale())));
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
			SymbioteAnim.play(player, SymbioteAnim.THROW);
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
		SymbioteState c = Symbiote.state(player).copy();
		c.tendrilGrabTargetId = -1;
		c.tendrilGrabHeld = false;
		if (startCooldown) {
			c.abilityCooldowns.set(AbilitySlot.SLOT_6.index(), now + CD_TENDRIL_GRAB);
		}
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		dropHeldTendril(player);
		SymbioteAnim.stop(player, SymbioteAnim.GRAB);
	}

	// ---------------- Symbiote Lunge (X) ----------------

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
		SymbioteAnim.play(player, SymbioteAnim.LUNGE);
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
		for (LivingEntity target : enemiesAround(player, player.position().add(0, 0.9 * player.getScale(), 0), 2.0 * player.getScale())) {
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
			cooldownMessage(player, readyAt - now);
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
			SymbioteAnim.play(player, SymbioteAnim.GRAPPLE);
			SymbioteTendrilEntity.fromHand(player, true, item.position(), item, 12, 3, 0.1f);
			SymbioteSounds.organic(player, 0.9f, 0.7f);
			return;
		}

		// v0.11.15: the grapple needs something to hold onto -- a block or a creature within range. Aimed at
		// open air the tendril still lashes out the full distance, finds nothing, and snaps back.
		boolean hasAnchor = AbilityHelpers.raycastEntity(player, GRAPPLE_RANGE) != null
				|| AbilityHelpers.raycastBlock(player, GRAPPLE_RANGE).getType() != HitResult.Type.MISS;
		if (!hasAnchor) {
			SymbioteAnim.play(player, SymbioteAnim.TENDRIL_STRIKE);
			SymbioteTendrilEntity.fromHand(player, true, eye.add(look.scale(GRAPPLE_RANGE)), null, 10, 4, 0.12f);
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
		SymbioteAnim.play(player, SymbioteAnim.GRAPPLE);
		holdTendril(player, SymbioteTendrilEntity.fromHand(player, true, anchor, null, GRAPPLE_PULL_TICKS + 4, 3, 0.13f));
		SymbioteSounds.organic(player, 0.9f, 0.4f);
		SymbioteSounds.lash(player, 0.9f, 0.6f);
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
			dropHeldTendril(player);
			return;
		}
		Vec3 dir = toAnchor.normalize();
		double speed = Math.min(1.7, 0.7 + toAnchor.length() * 0.08);
		Vec3 vel = dir.scale(speed);
		// Never let a downward grapple slam the host straight into the ground -- keep a slight lift so
		// it carries them ACROSS to a lower ledge instead of drilling them into it.
		double vy = Math.max(vel.y, -0.4) + 0.12;
		AbilityHelpers.launchSelf(player, new Vec3(vel.x, vy, vel.z));
	}

	// ---------------- Tendril Barrage / Blade Slash (Z) ----------------

	/**
	 * Z -- a flurry of tendrils from alternating hands for 1.3 s. v0.13.19: it fires whether or not anything is
	 * in the sights (so it can miss), every tendril flies down the aim with a little wander, and it reaches as
	 * far as Tendril Strike.
	 */
	private static boolean startBarrage(ServerPlayer player, long now) {
		BARRAGE.put(player.getId(), new long[]{now + BARRAGE_DURATION, 0L, 0L});
		SymbioteAnim.play(player, SymbioteAnim.BARRAGE);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.7f, 1.3f);
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
		boolean right = (b[2]++ % 2) == 0;
		ServerLevel level = AbilityHelpers.level(player);
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 end = traceTendril(player, spread(player, player.getLookAngle(), BARRAGE_SPREAD_DEGREES), BARRAGE_RANGE, hit);
		SymbioteTendrilEntity.fromHand(player, right, end, hit[0], 7, 2, 0.1f);
		if (hit[0] != null) {
			LivingEntity target = hit[0];
			target.invulnerableTime = 0;
			if (AbilityHelpers.hurtLands(player, target, BARRAGE_HIT_DAMAGE)) {
				AbilityHelpers.burst(level, end, ParticleTypes.SQUID_INK, 8, 0.3);
			}
		}
		SymbioteSounds.lash(player, 0.5f, 0.9f + player.getRandom().nextFloat() * 0.3f);
	}

	private static boolean bladeSlash(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		SymbioteAnim.play(player, SymbioteAnim.BLADE_SLASH);
		List<LivingEntity> targets = coneTargets(player, BLADE_SLASH_RANGE * Math.max(1.0, player.getScale()), BLADE_SLASH_CONE_DOT);
		for (LivingEntity target : targets) {
			if (AbilityHelpers.hurtLands(player, target, BLADE_SLASH_DAMAGE)) {
				target.hurt(player.damageSources().magic(), 2.0f);
				AbilityHelpers.knockbackFrom(target, player.position(), 0.6);
				AbilityHelpers.burst(level, target.position().add(0, target.getBbHeight() * 0.5, 0),
						ParticleTypes.SQUID_INK, 20, 0.4);
			}
		}
		Vec3 hand = SymbioteHands.right(player);
		Vec3 look = player.getLookAngle();
		for (double a = -0.7; a <= 0.7; a += 0.35) {
			Vec3 p = hand.add(look.yRot((float) a).scale(BLADE_SLASH_RANGE * 0.6));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
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
				cooldownMessage(player, readyAt - now);
				return;
			}
			SymbioteState c = s.copy();
			c.onslaughtChargeStart = now;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			SymbioteAnim.play(player, SymbioteAnim.ONSLAUGHT_CHARGE);
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

	/**
	 * The wind-up: the host's feet spread a black pool, tendrils rise out of the ground in a closing ring and
	 * writhe there, and anything inside is already slowed.
	 */
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
		double r = ONSLAUGHT_RADIUS * (1.0 - frac * 0.6);
		// the pool: a ring of ichor on the ground that tightens as it charges
		int ring = 16 + (int) (frac * 16);
		for (int i = 0; i < ring; i++) {
			double ang = (Math.PI * 2 * i) / ring + now * 0.12;
			level.sendParticles(ICHOR, player.getX() + Math.cos(ang) * r, player.getY() + 0.1,
					player.getZ() + Math.sin(ang) * r, 1, 0.05, 0.0, 0.05, 0.0);
		}
		// every few ticks a pair of tendrils claws up out of the pool
		if (held % 6 == 0) {
			for (int k = 0; k < 2; k++) {
				double ang = player.getRandom().nextDouble() * Math.PI * 2;
				Vec3 root = groundPoint(level, player.position().add(Math.cos(ang) * r, 0, Math.sin(ang) * r));
				Vec3 tip = root.add(Math.cos(ang) * -0.8, 1.8 + player.getRandom().nextDouble(), Math.sin(ang) * -0.8);
				SymbioteTendrilEntity.fromPoint(level, root, tip, null, 14, 5, 0.16f);
			}
		}
		// enemies caught in the radius are already slowed while it charges
		for (LivingEntity target : enemiesAround(player, player.position(), ONSLAUGHT_RADIUS)) {
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 20, 1);
		}
		if (player.tickCount % 6 == 0) {
			AbilityHelpers.sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f + (float) frac);
		}
	}

	private static void cancelOnslaught(ServerPlayer player, SymbioteState s, String messageKey) {
		SymbioteState c = s.copy();
		c.onslaughtChargeStart = -1L;
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		SymbioteAnim.stop(player, SymbioteAnim.ONSLAUGHT_CHARGE);
		if (messageKey != null) {
			player.displayClientMessage(Component.translatable(messageKey), true);
		}
	}

	/**
	 * The release: the pool erupts. A crown of huge tendrils bursts outward from the host to the edge of the
	 * radius, and a tendril spears up out of the ground into every enemy inside -- 20 damage, Wither III,
	 * Blindness, Slowness IV and Weakness II, and they are thrown into the air -- under a shockwave of living
	 * black. 90 s cooldown.
	 */
	private static void fireOnslaught(ServerPlayer player, SymbioteState s, long now) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 center = player.position();
		SymbioteAnim.play(player, SymbioteAnim.ONSLAUGHT_RELEASE);
		List<LivingEntity> victims = enemiesAround(player, center, ONSLAUGHT_RADIUS);
		long[] track = new long[victims.size() + 1];
		track[0] = now + ONSLAUGHT_DOT_TICKS;
		int idx = 1;
		for (LivingEntity target : victims) {
			Vec3 mid = target.position().add(0, target.getBbHeight() * 0.5, 0);
			Vec3 root = groundPoint(level, target.position().add(
					(player.getRandom().nextDouble() - 0.5) * 1.4, 0, (player.getRandom().nextDouble() - 0.5) * 1.4));
			SymbioteTendrilEntity.fromPoint(level, root, mid, target, 34, 3, 0.2f);
			AbilityHelpers.hurtBurst(player, target, ONSLAUGHT_DAMAGE);
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, ONSLAUGHT_DOT_TICKS, 2, false, true, true), player);
			AbilityHelpers.applyControl(target, MobEffects.BLINDNESS, 100, 0);
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 120, 3);
			AbilityHelpers.applyControl(target, MobEffects.WEAKNESS, ONSLAUGHT_DOT_TICKS, 1);
			AbilityHelpers.push(target, new Vec3(0, 0.85, 0));
			AbilityHelpers.burst(level, mid, ParticleTypes.SQUID_INK, 40, target.getBbWidth() * 0.6 + 0.4);
			level.sendParticles(SHEEN, mid.x, mid.y, mid.z, 16, 0.4, 0.5, 0.4, 0.0);
			track[idx++] = target.getId();
		}
		if (victims.isEmpty()) {
			ONSLAUGHT_VICTIMS.remove(player.getId());
		} else {
			ONSLAUGHT_VICTIMS.put(player.getId(), track);
		}
		// the crown: eighteen great tendrils thrown outward from the host, arcing down to the edge of the radius
		int crown = 18;
		Vec3 base = center.add(0, 0.9 * player.getScale(), 0);
		for (int i = 0; i < crown; i++) {
			double ang = (Math.PI * 2 * i) / crown;
			Vec3 tip = groundPoint(level, center.add(Math.cos(ang) * ONSLAUGHT_RADIUS, 0, Math.sin(ang) * ONSLAUGHT_RADIUS))
					.add(0, 0.6 + (i % 3) * 0.5, 0);
			SymbioteTendrilEntity.fromPoint(level, base, tip, null, 22, 4 + (i % 3), 0.24f);
		}
		// the shockwave: three expanding rings of ichor and sheen
		for (int ringIdx = 1; ringIdx <= 3; ringIdx++) {
			double r = ONSLAUGHT_RADIUS * ringIdx / 3.0;
			int n = 24 * ringIdx;
			for (int i = 0; i < n; i++) {
				double ang = (Math.PI * 2 * i) / n;
				double x = center.x + Math.cos(ang) * r;
				double z = center.z + Math.sin(ang) * r;
				level.sendParticles(ringIdx == 2 ? SHEEN : ICHOR, x, center.y + 0.15, z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 1, center.z, 30, 2.5, 0.8, 2.5, 0.05);
		AbilityHelpers.burst(level, center.add(0, 1, 0), ParticleTypes.SQUID_INK, 80, ONSLAUGHT_RADIUS * 0.5);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 0.6f);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_ROAR, 1.2f, 0.8f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.9f, 0.5f);
		SymbioteSounds.lash(player, 1.5f, 0.5f);

		SymbioteState c = s.copy();
		c.onslaughtChargeStart = -1L;
		c.abilityCooldowns.set(AbilitySlot.SLOT_4.index(), now + CD_ONSLAUGHT);
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
	}

	/** The ground under {@code p}: the first solid block surface within a few blocks up or down. */
	private static Vec3 groundPoint(ServerLevel level, Vec3 p) {
		BlockPos pos = BlockPos.containing(p.x, p.y + 1.0, p.z);
		for (int i = 0; i < 5 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) {
			pos = pos.below();
		}
		for (int i = 0; i < 3 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) {
			pos = pos.above();
		}
		return new Vec3(p.x, pos.getY() + 0.05, p.z);
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

	/**
	 * v0.13.19: no time limit any more -- the shield stays up until the host drops it or the Biomass runs out;
	 * it costs Biomass while it is up instead ({@link SymbioteVitalsManager#tick}).
	 */
	private static void toggleShield(ServerPlayer player) {
		SymbioteState s = Symbiote.state(player);
		if (s.shieldHeld) {
			SymbioteState c = s.copy();
			c.shieldHeld = false;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			return;
		}
		if (!SymbioteVitalsManager.usable(player) || SymbioteVitalsManager.biomass(player) <= 0.0f) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
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
		if (!s.shieldHeld) {
			return;
		}
		if (!SymbioteVitalsManager.usable(player)) {
			SymbioteState c = s.copy();
			c.shieldHeld = false;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.shield_spent"), true);
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 1, true, true, true));
		if (player.tickCount % 2 != 0) {
			return;
		}
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 look = player.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		Vec3 up = new Vec3(0, 1, 0);
		double scale = Math.max(1.0, player.getScale());
		Vec3 center = player.getEyePosition().add(look.scale(0.9 * scale)).add(0, -0.35 * scale, 0);
		for (int i = 0; i < 14; i++) {
			double ang = (Math.PI * 2 * i) / 14.0;
			Vec3 edge = center.add(right.scale(Math.cos(ang) * 0.55 * scale)).add(up.scale(Math.sin(ang) * 0.7 * scale));
			level.sendParticles(ParticleTypes.SQUID_INK, edge.x, edge.y, edge.z, 1, 0.0, 0.0, 0.0, 0.0);
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
		dropHeldTendril(player);
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
		SymbioteAnim.play(player, SymbioteAnim.RESURRECT);

		// The tendrils: a wide crown of real tendrils reaching the full radius.
		int rays = 20;
		for (int i = 0; i < rays; i++) {
			double ang = (Math.PI * 2 * i) / rays;
			double rise = 0.05 + (i % 3) * 0.12;
			Vec3 dir = new Vec3(Math.cos(ang), rise, Math.sin(ang)).normalize();
			SymbioteTendrilEntity.fromPoint(level, origin, origin.add(dir.scale(RESURRECT_RADIUS * 0.6)), null, 24, 5, 0.22f);
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
				x -> x != player && x.isAlive() && !x.isSpectator() && !Squads.areAllies(player, x))) {
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
