package com.projecthero.mod.ironman.ability.era1;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.EraOneMk34Access;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Era I (user): the Mk III Classic's twelve moves ({@code docs/IRONMAN_ERA1_PLAN.md}) -- the iconic red and gold:
 * real flight, the chest Unibeam, good all-round. Numbers come from the kit table ({@code move.damage()}); energy is paid
 * by the router (and, for the held Tractor Beam / the Targeting HUD toggle, per tick -- see {@link #drain}).
 *
 * <ul>
 *   <li>R: Repulsor 14 / Twin Volley 2 x 12 (one bolt from each hand).</li>
 *   <li>G: Unibeam -- hold to charge (full at 1.5 s), release: a third of 36 at once up to 36 at full, along 40 blocks;
 *       Shift: Unibeam Sweep -- 24 to everything a beam sweeping a 90° arc crosses.</li>
 *   <li>X: Flight Boost -- 3 s at x1.8 flight speed (takes off if grounded) / Barrel Roll -- a sideways dodge with 0.6 s
 *       of invulnerability.</li>
 *   <li>Z: Mini-Missiles -- 3 targets locked, 18 each / Missile Barrage -- 6 x 14 onto the crosshair area.</li>
 *   <li>V: Tractor Beam -- hold: pull a mob / item in 20 blocks to you, release lets go / Tractor Throw -- fling what the
 *       beam holds (or the nearest thing in front) for 16.</li>
 *   <li>C: Targeting HUD toggle -- mobs within 32 outlined for the wearer only (private marks), +20% crit chance.</li>
 * </ul>
 */
public final class EraOneMk3Moves {
	public static final String SUIT = "mark_iii";

	public static final double REPULSOR_RANGE = 40.0;
	public static final double UNIBEAM_RANGE = 40.0;
	/** A Unibeam held this long (1.5 s) is fully charged. */
	public static final int UNIBEAM_FULL_TICKS = 30;
	/** An uncharged (tapped) Unibeam still lands this share of its damage. */
	public static final float UNIBEAM_MIN_SHARE = 1f / 3f;
	public static final double SWEEP_RANGE = 30.0;
	public static final double SWEEP_ARC_DEGREES = 90.0;
	public static final int SWEEP_STEPS = 10;
	public static final int FLIGHT_BOOST_TICKS = 3 * 20;
	public static final double FLIGHT_BOOST_MULTIPLIER = 1.8;
	/** The boost timer rides in the synced cooldown map under this key, so the client's flight can read it. */
	public static final String FLIGHT_BOOST_KEY = "e1mk3_flight_boost_on";
	public static final int ROLL_IFRAME_TICKS = 12; // 0.6 s
	public static final double ROLL_SPEED = 1.5;
	public static final int MISSILE_LOCKS = 3;
	public static final double MISSILE_RANGE = 48.0;
	public static final int BARRAGE_COUNT = 6;
	public static final double BARRAGE_RANGE = 64.0;
	public static final double BARRAGE_SPREAD = 3.0;
	public static final double TRACTOR_RANGE = 20.0;
	public static final double TRACTOR_HOLD_DISTANCE = 3.5;
	public static final double THROW_SPEED = 2.2;
	public static final double HUD_RADIUS = 32.0;
	public static final float HUD_CRIT_CHANCE = 0.20f;
	public static final float HUD_CRIT_MULTIPLIER = 1.5f;

	private EraOneMk3Moves() {
	}

	static void init() {
		EraOneMk34State.initialize();
		tap("e1mk3_repulsor", (p, suit, m) -> {
			EraOneMk34Access.repulsor(p, m.damage(), REPULSOR_RANGE, EraOneMk34Access.BEAM_REPULSOR);
			return true;
		});
		tap("e1mk3_twin_volley", (p, suit, m) -> twinVolley(p, m.damage()));
		IronManEraOne.register("e1mk3_unibeam", (p, suit, m, pressed) -> unibeam(p, m.damage(), pressed));
		tap("e1mk3_unibeam_sweep", (p, suit, m) -> sweep(p, m.damage()));
		tap("e1mk3_flight_boost", (p, suit, m) -> flightBoost(p));
		tap("e1mk3_barrel_roll", (p, suit, m) -> barrelRoll(p));
		tap("e1mk3_mini_missiles", (p, suit, m) -> miniMissiles(p, m.damage()));
		tap("e1mk3_missile_barrage", (p, suit, m) -> barrage(p, m.damage()));
		IronManEraOne.register("e1mk3_tractor_beam", (p, suit, m, pressed) -> tractor(p, pressed));
		tap("e1mk3_tractor_throw", (p, suit, m) -> tractorThrow(p, m.damage()));
		tap("e1mk3_targeting_hud", (p, suit, m) -> toggleHud(p));
	}

	@FunctionalInterface
	interface TapMove {
		boolean fire(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m);
	}

	/** A TAP move: only the press does anything. */
	static void tap(String id, TapMove move) {
		IronManEraOne.register(id, (p, suit, m, pressed) -> pressed && move.fire(p, suit, m));
	}

	/**
	 * One tick of a held / toggled move's per-second energy ({@link IronManComicKit#energyPerSecond}). False = out of
	 * energy, the move stops.
	 */
	private static boolean drain(ServerPlayer p, String moveId) {
		return IronManEraOne.drainTick(p, com.projecthero.mod.ironman.suit.IronManSuits.byId(SUIT), moveId);
	}

	// ================================================================== R

	private static boolean twinVolley(ServerPlayer p, float damage) {
		Vec3 look = p.getLookAngle();
		for (Vec3 from : new Vec3[] { EraOneMk34Access.palm(p), EraOneMk34Access.leftPalm(p) }) {
			Vec3 dir = IronManTargeting.aim(p, from, look, REPULSOR_RANGE);
			EraOneMk34Access.beam(p, from, dir, REPULSOR_RANGE, 0.6, false, EraOneMk34Access.BEAM_REPULSOR, damage, 0.9);
			EraOneMk34Access.level(p).sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 4, 0.05, 0.05, 0.05, 0.05);
		}
		EraOneMk34Access.pose(p, IronManAbilityFx.CHARGED, 12);
		EraOneMk34Access.sound(p, IronManSounds.REPULSOR_BLAST, 1.0f, 0.95f);
		EraOneMk34Access.sound(p, IronManSounds.REPULSOR_BLAST, 0.8f, 1.15f);
		return true;
	}

	// ================================================================== G

	/** The Unibeam's damage after {@code heldTicks} of charge: a third of {@code full} at once, all of it at 1.5 s. */
	public static float unibeamDamage(float full, long heldTicks) {
		float t = Math.max(0f, Math.min(1f, heldTicks / (float) UNIBEAM_FULL_TICKS));
		return full * (UNIBEAM_MIN_SHARE + (1f - UNIBEAM_MIN_SHARE) * t);
	}

	private static boolean unibeam(ServerPlayer p, float damage, boolean pressed) {
		EraOneMk34State.P s = EraOneMk34State.of(p);
		long now = p.level().getGameTime();
		if (pressed) {
			s.unibeamChargeStart = now;
			EraOneMk34Access.sound(p, IronManSounds.UNIBEAM_CHARGE, 1.0f, 1.0f);
			EraOneMk34Access.pose(p, IronManAbilityFx.UNIBEAM, 6);
			return false; // goes off on the release
		}
		if (s.unibeamChargeStart < 0L) {
			return false;
		}
		long held = now - s.unibeamChargeStart;
		s.unibeamChargeStart = -1L;
		boolean full = held >= UNIBEAM_FULL_TICKS;
		Vec3 from = EraOneMk34Access.chest(p);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), UNIBEAM_RANGE);
		EraOneMk34Access.beam(p, from, dir, UNIBEAM_RANGE, full ? 1.5 : 1.1, true,
				full ? EraOneMk34Access.BEAM_CHARGED : EraOneMk34Access.BEAM_UNIBEAM, unibeamDamage(damage, held), full ? 1.6 : 1.0);
		ServerLevel level = EraOneMk34Access.level(p);
		level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, full ? 30 : 12, 0.3, 0.3, 0.3, 0.15);
		EraOneMk34Access.pose(p, IronManAbilityFx.UNIBEAM, 16);
		EraOneMk34Access.sound(p, IronManSounds.REPULSOR_CHARGED_BLAST, 1.1f, full ? 0.6f : 0.8f);
		EraOneMk34Access.sound(p, IronManSounds.UNIBEAM_LOOP, 0.8f, 1.1f);
		return true;
	}

	private static void tickUnibeamCharge(ServerPlayer p, EraOneMk34State.P s) {
		long held = p.level().getGameTime() - s.unibeamChargeStart;
		IronManAbilityFx.hold(p, IronManAbilityFx.UNIBEAM);
		Vec3 c = EraOneMk34Access.chest(p);
		ServerLevel level = EraOneMk34Access.level(p);
		if (held % 2 == 0) {
			int n = 1 + (int) Math.min(UNIBEAM_FULL_TICKS, held) / 6;
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, n, 0.35, 0.35, 0.35, -0.04);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 1, 0.1, 0.1, 0.1, 0.02);
		}
		if (held == UNIBEAM_FULL_TICKS) {
			EraOneMk34Access.sound(p, IronManSounds.REPULSOR_READY, 1.0f, 0.8f);
		}
	}

	/** A beam swept left to right across a 90° arc over {@link #SWEEP_STEPS} ticks; each thing it crosses is hit once. */
	private static boolean sweep(ServerPlayer p, float damage) {
		Vec3 base = p.getLookAngle();
		Set<Integer> hit = new HashSet<>();
		EraOneMk34Access.pose(p, IronManAbilityFx.UNIBEAM, SWEEP_STEPS + 8);
		EraOneMk34Access.sound(p, IronManSounds.UNIBEAM_CHARGE, 0.9f, 1.3f);
		EraOneMk34Access.sound(p, IronManSounds.UNIBEAM_LOOP, 1.0f, 1.1f);
		for (int i = 0; i < SWEEP_STEPS; i++) {
			double deg = -SWEEP_ARC_DEGREES / 2.0 + SWEEP_ARC_DEGREES * i / (SWEEP_STEPS - 1);
			Vec3 dir = base.yRot((float) Math.toRadians(deg)).normalize();
			EraOneMk34Access.later(p, 1 + i, w -> {
				if (!SUIT.equals(IronManArmor.wornSuitId(w))) {
					return;
				}
				Vec3 from = EraOneMk34Access.chest(w);
				Vec3 end = from.add(dir.scale(EraOneMk34Access.wallDistance(w, from, dir, SWEEP_RANGE)));
				for (LivingEntity e : EraOneMk34Access.along(w, from, end, 1.0)) {
					if (hit.add(e.getId())) {
						EraOneMk34Access.hit(w, e, damage);
						EraOneMk34Access.shove(e, from, 0.8, 0.2);
					}
				}
				EraOneMk34Access.broadcastBeam(w, from, end, EraOneMk34Access.BEAM_UNIBEAM);
				EraOneMk34Access.level(w).sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 6, 0.2, 0.2, 0.2, 0.1);
				IronManSounds.play(w, IronManSounds.BEAM_CRACKLE, 0.5f, 1.2f);
			});
		}
		return true;
	}

	// ================================================================== X

	/** The client flight's speed multiplier from a running Flight Boost (1 = none). Either side. */
	public static double flightBoostMultiplier(TonyStarkState state, String suitId, long gameTime) {
		if (state == null || !SUIT.equals(suitId)) {
			return 1.0;
		}
		return state.abilityReadyAt.getOrDefault(suitId + "/" + FLIGHT_BOOST_KEY, 0L) > gameTime ? FLIGHT_BOOST_MULTIPLIER : 1.0;
	}

	private static boolean flightBoost(ServerPlayer p) {
		if (!IronManArmor.isPieceWorn(p, EquipmentSlot.FEET, SUIT)) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.need_boots"), true);
			return false;
		}
		TonyStark.triggerCooldown(p, SUIT, FLIGHT_BOOST_KEY, FLIGHT_BOOST_TICKS);
		EraOneMk34State.of(p).boostUntil = p.level().getGameTime() + FLIGHT_BOOST_TICKS;
		boolean wasFlying = IronManFlight.isFlying(p);
		if (!wasFlying) {
			IronManFlight.setFlying(p, true);
		}
		Vec3 look = p.getLookAngle();
		EraOneMk34Access.launch(p, look.scale(1.6).add(0, wasFlying ? 0.0 : 0.5, 0));
		ServerLevel level = EraOneMk34Access.level(p);
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 24, 0.25, 0.05, 0.25, 0.08);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 16, 0.4, 0.1, 0.4, 0.1);
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_LAUNCH, 12);
		EraOneMk34Access.sound(p, IronManSounds.SUPERSONIC, 1.0f, 1.2f);
		return true;
	}

	private static boolean barrelRoll(ServerPlayer p) {
		EraOneMk34State.P s = EraOneMk34State.of(p);
		Vec3 look = p.getLookAngle();
		Vec3 right = EraOneMk34Access.rightOf(p, look);
		// strafing picks the side (xxa > 0 = A / left); with no strafe it alternates
		boolean toRight = p.xxa < 0f || p.xxa == 0f && !s.rollRight;
		s.rollRight = toRight;
		Vec3 side = right.scale(toRight ? ROLL_SPEED : -ROLL_SPEED);
		boolean flying = IronManFlight.isFlying(p);
		EraOneMk34Access.launch(p, side.add(p.getDeltaMovement().scale(0.3)).add(0, flying ? 0.05 : 0.3, 0));
		s.rollIframesUntil = p.level().getGameTime() + ROLL_IFRAME_TICKS;
		ServerLevel level = EraOneMk34Access.level(p);
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		EraOneMk34Access.ring(level, c, 0.9, ParticleTypes.CLOUD, 14);
		EraOneMk34Access.line(level, c, c.add(side.scale(2.5)), ParticleTypes.FLAME, 2.0);
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_SPIN, ROLL_IFRAME_TICKS);
		EraOneMk34Access.sound(p, IronManSounds.DASH, 1.0f, 1.25f);
		return true;
	}

	/** The Barrel Roll's i-frames: nothing lands on the wearer for 0.6 s after the roll. */
	public static boolean invulnerable(ServerPlayer p) {
		EraOneMk34State.P s = EraOneMk34State.peek(p);
		return s != null && s.rollIframesUntil > p.level().getGameTime() && SUIT.equals(IronManArmor.wornSuitId(p));
	}

	// ================================================================== Z

	private static boolean miniMissiles(ServerPlayer p, float damage) {
		List<LivingEntity> targets = EraOneMk34Access.frontTargets(p, MISSILE_RANGE, MISSILE_LOCKS);
		EraOneMk34Access.privateMark(p, targets, 40);
		EraOneMk34Access.pose(p, IronManAbilityFx.MISSILES, 16);
		EraOneMk34Access.sound(p, IronManSounds.TARGET_LOCK, 0.9f, 1.3f);
		EraOneMk34Access.sound(p, IronManSounds.MISSILE_POD, 1.0f, 1.1f);
		for (int i = 0; i < MISSILE_LOCKS; i++) {
			final int n = i;
			EraOneMk34Access.later(p, 1 + i * 2, w -> {
				if (!SUIT.equals(IronManArmor.wornSuitId(w))) {
					return;
				}
				Vec3 from = EraOneMk34Access.shoulder(w, n);
				LivingEntity t = targets.isEmpty() ? null : targets.get(n % targets.size());
				Vec3 dir = t != null && t.isAlive() ? EraOneMk34Access.centre(t).subtract(from).normalize().add(0, 0.2, 0).normalize()
						: w.getLookAngle().add(0, 0.12, 0).normalize();
				IronManMissileEntity m = EraOneMk34Access.missile(w, from, dir, 0.9, damage, damage * 0.4f, 1.2f);
				if (t != null && t.isAlive()) {
					m.withTarget(t);
				} else {
					m.withHoming();
				}
				EraOneMk34Access.spawn(w, m);
				IronManSounds.play(w, IronManSounds.MISSILE_LAUNCH, 0.7f, 1.15f + n * 0.08f);
			});
		}
		if (!targets.isEmpty()) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.e1mk3_locked", targets.size())
					.withStyle(ChatFormatting.RED), true);
		}
		return true;
	}

	private static boolean barrage(ServerPlayer p, float damage) {
		LivingEntity aimed = EraOneMk34Access.target(p, BARRAGE_RANGE);
		Vec3 centre = aimed != null ? EraOneMk34Access.centre(aimed) : EraOneMk34Access.groundAt(p, EraOneMk34Access.aimPoint(p, BARRAGE_RANGE));
		EraOneMk34Access.pose(p, IronManAbilityFx.MISSILES, BARRAGE_COUNT * 2 + 10);
		EraOneMk34Access.sound(p, IronManSounds.MISSILE_POD, 1.1f, 0.9f);
		EraOneMk34Access.ring(EraOneMk34Access.level(p), centre.add(0, 0.2, 0), BARRAGE_SPREAD, ParticleTypes.CRIT, 20);
		for (int i = 0; i < BARRAGE_COUNT; i++) {
			final int n = i;
			EraOneMk34Access.later(p, 1 + i * 2, w -> {
				if (!SUIT.equals(IronManArmor.wornSuitId(w))) {
					return;
				}
				// the first one straight onto the aim point, the rest scattered round it
				Vec3 at = centre;
				if (n > 0) {
					double a = w.getRandom().nextDouble() * Math.PI * 2;
					double r = Math.sqrt(w.getRandom().nextDouble()) * BARRAGE_SPREAD;
					at = centre.add(Math.cos(a) * r, 0, Math.sin(a) * r);
				}
				Vec3 from = EraOneMk34Access.shoulder(w, n);
				IronManMissileEntity m = EraOneMk34Access.missile(w, from, at.subtract(from).normalize(), 1.3, damage, damage * 0.5f, 1.6f);
				EraOneMk34Access.spawn(w, m);
				IronManSounds.play(w, IronManSounds.MISSILE_LAUNCH, 0.7f, 0.95f + (n % 3) * 0.08f);
			});
		}
		return true;
	}

	// ================================================================== V

	/** HOLD: the press grabs a mob / item in 20 blocks on the beam; it is pulled in front of you; the release lets go. */
	private static boolean tractor(ServerPlayer p, boolean pressed) {
		EraOneMk34State.P s = EraOneMk34State.of(p);
		if (pressed) {
			Entity t = EraOneMk34Access.target(p, TRACTOR_RANGE);
			if (t == null) {
				t = itemOnBeam(p, TRACTOR_RANGE);
			}
			if (t == null) {
				p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.no_target"), true);
				return false;
			}
			s.tractorTarget = t.getId();
			s.tractorStart = p.level().getGameTime();
			EraOneMk34Access.sound(p, IronManSounds.REPULSOR_CHARGE, 0.8f, 0.6f);
			EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_MAGNET, 8);
			return false; // the cooldown starts when the beam is let go
		}
		if (s.tractorStart <= 0L) {
			return false;
		}
		s.tractorStart = 0L;
		s.tractorTarget = -1;
		EraOneMk34Access.sound(p, IronManSounds.RELEASE, 0.8f, 1.2f);
		return true;
	}

	/** The nearest item entity within 1.5 blocks of the look ray (the beam picks up loot too). */
	private static ItemEntity itemOnBeam(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double wall = EraOneMk34Access.wallDistance(p, eye, look, range);
		ItemEntity best = null;
		double bestT = Double.MAX_VALUE;
		for (ItemEntity item : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(range))) {
			Vec3 c = item.position();
			double t = c.subtract(eye).dot(look);
			if (t < 0 || t > wall + 0.5) {
				continue;
			}
			if (c.distanceTo(eye.add(look.scale(t))) <= 1.5 && t < bestT) {
				bestT = t;
				best = item;
			}
		}
		return best;
	}

	/** The entity the Tractor Beam holds right now (or null). */
	public static Entity tractorHeld(ServerPlayer p) {
		EraOneMk34State.P s = EraOneMk34State.peek(p);
		return s == null || s.tractorTarget < 0 ? null : p.level().getEntity(s.tractorTarget);
	}

	private static void tickTractor(ServerPlayer p, EraOneMk34State.P s) {
		Entity t = p.level().getEntity(s.tractorTarget);
		if (t == null || !t.isAlive() || t.distanceToSqr(p) > (TRACTOR_RANGE + 6) * (TRACTOR_RANGE + 6)
				|| !drain(p, "e1mk3_tractor_beam")) {
			s.tractorTarget = -1; // the beam broke (the release still starts the cooldown)
			return;
		}
		Vec3 point = p.getEyePosition().add(p.getLookAngle().scale(TRACTOR_HOLD_DISTANCE * p.getScale()));
		EraOneMk34Access.pull(t, point, 0.7);
		t.resetFallDistance();
		if (t instanceof Mob m) {
			m.getNavigation().stop();
		}
		IronManAbilityFx.hold(p, IronManAbilityFx.COMIC_MAGNET);
		long age = p.level().getGameTime() - s.tractorStart;
		if (age % 2 == 0) {
			Vec3 c = t instanceof LivingEntity le ? EraOneMk34Access.centre(le) : t.position();
			EraOneMk34Access.broadcastBeam(p, EraOneMk34Access.palm(p), c, EraOneMk34Access.BEAM_TRACTOR);
			EraOneMk34Access.level(p).sendParticles(ParticleTypes.ENCHANT, c.x, c.y, c.z, 3, 0.3, 0.3, 0.3, 0.2);
		}
		if (age % 20 == 0) {
			IronManSounds.play(p, IronManSounds.SHIELD_HUM, 0.4f, 1.6f);
		}
	}

	private static boolean tractorThrow(ServerPlayer p, float damage) {
		EraOneMk34State.P s = EraOneMk34State.of(p);
		Entity held = tractorHeld(p);
		LivingEntity t = held instanceof LivingEntity le && le.isAlive() ? le : EraOneMk34Access.target(p, 12.0);
		if (t == null) {
			List<LivingEntity> front = EraOneMk34Access.cone(p, p.getEyePosition(), p.getLookAngle(), 8.0, 45.0);
			t = front.isEmpty() ? null : front.get(0);
		}
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.no_target"), true);
			return false;
		}
		s.tractorTarget = -1; // a thrown target leaves the beam
		Vec3 look = p.getLookAngle();
		EraOneMk34Access.hit(p, t, damage);
		t.setDeltaMovement(look.scale(THROW_SPEED).add(0, 0.4, 0));
		t.hurtMarked = true;
		t.hasImpulse = true;
		ServerLevel level = EraOneMk34Access.level(p);
		EraOneMk34Access.broadcastBeam(p, EraOneMk34Access.palm(p), EraOneMk34Access.centre(t), EraOneMk34Access.BEAM_TRACTOR);
		Vec3 c = EraOneMk34Access.centre(t);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.1);
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_THROW, 10);
		EraOneMk34Access.sound(p, IronManSounds.REPULSOR_CHARGED_BLAST, 0.9f, 0.9f);
		return true;
	}

	// ================================================================== C

	private static boolean toggleHud(ServerPlayer p) {
		EraOneMk34State.P s = EraOneMk34State.of(p);
		s.hudOn = !s.hudOn;
		if (s.hudOn) {
			s.hudNextMark = p.level().getGameTime();
			EraOneMk34Access.sound(p, IronManSounds.HUD_ON, 0.9f, 1.0f);
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.e1mk3_hud_on").withStyle(ChatFormatting.AQUA),
					true);
		} else {
			hudOff(p, s);
		}
		EraOneMk34Access.pose(p, IronManAbilityFx.COMIC_GUARD, 8);
		return true;
	}

	private static void hudOff(ServerPlayer p, EraOneMk34State.P s) {
		s.hudOn = false;
		s.hudMarked = 0;
		IronManSounds.play(p, IronManSounds.HUD_OFF, 0.9f, 1.0f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.e1mk3_hud_off"), true);
	}

	public static boolean hudOn(ServerPlayer p) {
		EraOneMk34State.P s = EraOneMk34State.peek(p);
		return s != null && s.hudOn;
	}

	/** Gametests: how many mobs the Targeting HUD outlined on its last sweep. */
	public static int hudMarked(ServerPlayer p) {
		EraOneMk34State.P s = EraOneMk34State.peek(p);
		return s == null ? 0 : s.hudMarked;
	}

	private static void tickHud(ServerPlayer p, EraOneMk34State.P s) {
		if (!drain(p, "e1mk3_targeting_hud")) {
			hudOff(p, s);
			return;
		}
		long now = p.level().getGameTime();
		if (now < s.hudNextMark) {
			return;
		}
		s.hudNextMark = now + 20;
		List<Mob> mobs = new ArrayList<>();
		for (LivingEntity e : EraOneMk34Access.around(p, p.position(), HUD_RADIUS)) {
			if (e instanceof Mob m) {
				mobs.add(m);
			}
		}
		EraOneMk34Access.privateMark(p, mobs, 30); // refreshed every second, only on the wearer's own screen
		s.hudMarked = mobs.size();
	}

	/** The Targeting HUD's crit roll on one hit: x1.5 one time in five while it is on (1 otherwise). */
	public static float critMultiplier(ServerPlayer attacker, RandomSource random) {
		if (!hudOn(attacker) || !SUIT.equals(IronManArmor.wornSuitId(attacker))) {
			return 1f;
		}
		return random.nextFloat() < HUD_CRIT_CHANCE ? HUD_CRIT_MULTIPLIER : 1f;
	}

	/** {@code IronManComicState.outgoingMultiplier}: a Targeting HUD crit, with the crit sparks. */
	public static float outgoingMultiplier(ServerPlayer attacker, LivingEntity target) {
		float m = critMultiplier(attacker, attacker.getRandom());
		if (m > 1f && attacker.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.3, 0.3,
					0.3, 0.2);
			IronManSounds.play(attacker, SoundEvents.PLAYER_ATTACK_CRIT, 0.6f, 1.2f);
		}
		return m;
	}

	/** {@code IronManComicState.incomingFactor}: 0 during the Barrel Roll's i-frames (the hit is dodged). */
	public static float incomingFactor(ServerPlayer p) {
		return invulnerable(p) ? 0f : 1f;
	}

	// ================================================================== tick

	/** Per server tick for a player with state ({@code active} = the Mk III is worn and working). */
	static void tick(ServerPlayer p, EraOneMk34State.P s, boolean active) {
		if (!active) {
			s.unibeamChargeStart = -1L;
			s.tractorTarget = -1;
			s.boostUntil = 0L;
			if (s.hudOn) {
				hudOff(p, s);
			}
			return;
		}
		long now = p.level().getGameTime();
		if (s.unibeamChargeStart >= 0L) {
			tickUnibeamCharge(p, s);
		}
		if (s.tractorTarget >= 0) {
			tickTractor(p, s);
		}
		if (s.hudOn) {
			tickHud(p, s);
		}
		if (s.boostUntil > now && now % 2 == 0) {
			// the boost's thruster trail (the speed itself is the client's flight, see flightBoostMultiplier)
			ServerLevel level = EraOneMk34Access.level(p);
			level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 3, 0.15, 0.05, 0.15, 0.02);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 1, 0.1, 0.05, 0.1, 0.02);
		}
	}

	/** The suit the moves belong to (gametests). */
	public static IronManSuit suit() {
		return IronManSuits.byId(SUIT);
	}
}
