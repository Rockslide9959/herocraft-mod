package com.projecthero.mod.ironman.ability.era1;

import java.util.List;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.ability.IronManComicAccess;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.era1.EraOneMk12State.Rt;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Era I: the Mk I CAVE-FORGED moves (plan: {@code docs/IRONMAN_ERA1_PLAN.md}, kit ids {@code e1mk1_*}).
 * Grey scrap iron, no flight -- a wrist flamethrower, heavy fists, rocket-boot jumps, smoke and scrap, a magnet and a
 * brace, and the overloaded arc reactor. Numbers are the kit table's ({@code move.damage()}); what each move leaves running
 * lives in {@link EraOneMk12State}.
 */
public final class EraOneMk1Moves {
	/** Rocket Jump: 1.18 blocks/tick straight up = an ~8-block rise. */
	public static final double ROCKET_JUMP_VY = 1.18;
	/** Rocket Leap: a long forward arc of ~14 blocks. */
	public static final double ROCKET_LEAP_BLOCKS = 14.0;
	/** Iron Fist / Haymaker reach, and their knockback. */
	public static final double FIST_REACH = 4.0;
	public static final double IRON_FIST_KNOCK = 2.4;
	public static final double HAYMAKER_KNOCK = 1.8;
	/** Scrap Burst: 4 pellets out to 8 blocks. */
	public static final int SCRAP_PELLETS = 4;
	public static final double SCRAP_RANGE = 8.0;
	/** Magnetic Repel: everything within 7 blocks. */
	public static final double REPEL_RADIUS = 7.0;

	private EraOneMk1Moves() {
	}

	static void init() {
		EraOneMk12State.initialize();
		IronManEraOne.register("e1mk1_flamethrower", EraOneMk1Moves::flamethrower);
		IronManEraOne.register("e1mk1_fire_wall", tap(EraOneMk1Moves::fireWall));
		IronManEraOne.register("e1mk1_iron_fist", tap(EraOneMk1Moves::ironFist));
		IronManEraOne.register("e1mk1_haymaker", EraOneMk1Moves::haymaker);
		IronManEraOne.register("e1mk1_rocket_jump", tap(EraOneMk1Moves::rocketJump));
		IronManEraOne.register("e1mk1_rocket_leap", tap(EraOneMk1Moves::rocketLeap));
		IronManEraOne.register("e1mk1_smoke_screen", tap(EraOneMk1Moves::smokeScreen));
		IronManEraOne.register("e1mk1_scrap_burst", tap(EraOneMk1Moves::scrapBurst));
		IronManEraOne.register("e1mk1_magnetic_repel", tap((p, suit, m) -> IronManComicAccess.repel(p, REPEL_RADIUS, 1.8, m.damage())));
		IronManEraOne.register("e1mk1_brace", tap(EraOneMk1Moves::brace));
		IronManEraOne.register("e1mk1_reactor_overload", tap(EraOneMk1Moves::reactorOverload));
	}

	/** A TAP move: only the press does anything. */
	@FunctionalInterface
	interface Tap {
		boolean fire(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m);
	}

	static IronManEraOne.Handler tap(Tap t) {
		return (p, suit, m, pressed) -> pressed && t.fire(p, suit, m);
	}

	// ================================================================== R: flamethrower / fire wall

	/** HOLD: press lights the stream (it runs while held), release stops it. */
	private static boolean flamethrower(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m, boolean pressed) {
		Rt r = EraOneMk12State.rt(p, suit);
		if (!pressed) {
			if (r.flame) {
				r.flame = false;
				IronManSounds.play(p, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.4f);
			}
			return false;
		}
		if (r.flame) {
			return false;
		}
		r.flame = true;
		r.flameStart = p.level().getGameTime();
		IronManSounds.move(p, IronManSounds.FLAMETHROWER_IGNITE, 1.0f, 1.0f);
		return true;
	}

	/** One tick of the stream. Returns false when it has to stop. */
	static boolean flameTick(ServerPlayer p, IronManSuit suit, Rt r, long now) {
		if (!drain(p, suit, "e1mk1_flamethrower")) {
			return false;
		}
		ServerLevel level = IronManComicAccess.level(p);
		IronManAbilityFx.hold(p, IronManAbilityFx.FLAME);
		Vec3 eye = p.getEyePosition();
		Vec3 dir = IronManComicAccess.aim(p, EraOneMk12State.FLAME_RANGE);
		double len = IronManComicAccess.wallDistance(p, eye, dir, EraOneMk12State.FLAME_RANGE);
		if ((now - r.flameStart) % EraOneMk12State.FLAME_HIT_EVERY == 0) {
			IronManComicKit.Move m = IronManComicKit.byId("e1mk1_flamethrower");
			float perHit = (m == null ? 8f : m.damage()) * EraOneMk12State.FLAME_HIT_EVERY / 20f;
			EraOneMk12State.asFlame(() -> {
				for (LivingEntity e : IronManComicAccess.cone(p, eye, dir, len + 0.5, EraOneMk12State.FLAME_HALF_DEG)) {
					IronManComicAccess.burn(p, e, perHit, EraOneMk12State.FLAME_FIRE_TICKS);
				}
			});
		}
		Vec3 nozzle = IronManComicAccess.palm(p);
		boolean doubled = r.overloadFlameUntil > now;
		for (double d = 0.5; d <= len + 0.01; d += 0.5) {
			Vec3 fx = nozzle.add(dir.scale(d));
			double spread = 0.04 + 0.07 * d;
			level.sendParticles(ParticleTypes.FLAME, fx.x, fx.y, fx.z, 2, spread, spread, spread, 0.01);
			if (doubled && d % 1.5 < 0.01) {
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, fx.x, fx.y, fx.z, 1, spread, spread, spread, 0.01);
			}
		}
		if (now % 4 == 0) {
			IronManSounds.loop(p, IronManSounds.FLAMETHROWER, 0.8f, doubled ? 0.8f : 1.0f);
		}
		return true;
	}

	/** A 5-wide line of fire across your path, 3 blocks ahead, for 4 s (3 damage a second to anything touching it). */
	private static boolean fireWall(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Vec3 flat = flatLook(p);
		Vec3 centre = groundAt(p, p.position().add(flat.scale(EraOneMk12State.FIRE_WALL_AHEAD)));
		Vec3 right = new Vec3(-flat.z, 0, flat.x);
		EraOneMk12State.rt(p, suit);
		EraOneMk12State.addFireWall(p, centre, right);
		ServerLevel level = IronManComicAccess.level(p);
		IronManComicAccess.line(level, centre.add(right.scale(-2.5)), centre.add(right.scale(2.5)), ParticleTypes.LAVA, 1.5);
		IronManComicAccess.line(level, IronManComicAccess.palm(p), centre, ParticleTypes.FLAME, 3.0);
		IronManComicAccess.pose(p, IronManAbilityFx.FLAME, 12);
		IronManComicAccess.soundAt(level, centre, SoundEvents.FIRECHARGE_USE, 1.2f, 0.7f);
		IronManSounds.move(p, IronManSounds.FLAMETHROWER_IGNITE, 1.0f, 0.8f);
		return true;
	}

	// ================================================================== G: iron fist / haymaker

	private static LivingEntity fistTarget(ServerPlayer p) {
		LivingEntity t = IronManComicAccess.target(p, FIST_REACH);
		if (t == null) {
			List<LivingEntity> close = IronManComicAccess.cone(p, p.getEyePosition(), p.getLookAngle(), FIST_REACH, 40.0);
			t = close.isEmpty() ? null : close.get(0);
		}
		return t;
	}

	/** 18, big knockback. A swing at nothing still goes off (a punch is a punch). */
	private static boolean ironFist(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		LivingEntity t = fistTarget(p);
		IronManComicAccess.pose(p, IronManAbilityFx.PUNCH, 10);
		IronManSounds.move(p, IronManSounds.PUNCH, 1.1f, 0.8f);
		if (t != null) {
			IronManComicAccess.hit(p, t, m.damage());
			IronManComicAccess.shove(t, p.position(), IRON_FIST_KNOCK, 0.45);
			impact(p, t, false);
		} else {
			IronManSounds.play(p, SoundEvents.PLAYER_ATTACK_WEAK, 0.8f, 0.7f);
		}
		return true;
	}

	/**
	 * HOLD: the press commits (pays, cooldown) and winds the fist back; the release throws it -- full 36 + a 1.5 s stagger
	 * after a 1 s charge, less (down to half) if let go sooner. A 3 s hold throws it anyway.
	 */
	private static boolean haymaker(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m, boolean pressed) {
		Rt r = EraOneMk12State.rt(p, suit);
		long now = p.level().getGameTime();
		if (pressed) {
			if (r.hayStart != 0L) {
				return false;
			}
			r.hayStart = now;
			IronManComicAccess.pose(p, IronManAbilityFx.COMIC_GUARD, 8);
			IronManSounds.move(p, IronManSounds.SERVO, 1.0f, 0.6f);
			return true;
		}
		if (r.hayStart == 0L) {
			return false;
		}
		throwHaymaker(p, r, now);
		return false;
	}

	static void haymakerWindup(ServerPlayer p, Rt r, long now) {
		long held = now - r.hayStart;
		IronManAbilityFx.hold(p, IronManAbilityFx.COMIC_GUARD);
		Vec3 fist = IronManComicAccess.palm(p);
		IronManComicAccess.level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, fist.x, fist.y, fist.z, (int) Math.min(6, 1 + held / 4), 0.08,
				0.08, 0.08, 0.02);
		if (held == EraOneMk12State.CHARGE_TICKS) {
			IronManSounds.play(p, IronManSounds.REPULSOR_READY, 0.7f, 0.6f);
			IronManComicAccess.level(p).sendParticles(ParticleTypes.FLAME, fist.x, fist.y, fist.z, 10, 0.1, 0.1, 0.1, 0.03);
		} else if (held % 6 == 0) {
			IronManSounds.loop(p, IronManSounds.SERVO, 0.4f, 0.6f + held / 60f);
		}
		if (held >= EraOneMk12State.CHARGE_MAX_HOLD) {
			throwHaymaker(p, r, now);
		}
	}

	/** The charge share 0.5..1 for a hold of {@code held} ticks (full at {@link EraOneMk12State#CHARGE_TICKS}). */
	public static float chargeShare(long held) {
		return (float) Math.max(0.5, Math.min(1.0, held / (double) EraOneMk12State.CHARGE_TICKS));
	}

	private static void throwHaymaker(ServerPlayer p, Rt r, long now) {
		long held = now - r.hayStart;
		r.hayStart = 0L;
		IronManComicKit.Move m = IronManComicKit.byId("e1mk1_haymaker");
		boolean full = held >= EraOneMk12State.CHARGE_TICKS;
		float dmg = (m == null ? 36f : m.damage()) * chargeShare(held);
		IronManComicAccess.pose(p, IronManAbilityFx.PUNCH, 12);
		IronManSounds.move(p, IronManSounds.PUNCH, 1.3f, full ? 0.55f : 0.75f);
		LivingEntity t = fistTarget(p);
		if (t == null) {
			IronManSounds.play(p, SoundEvents.PLAYER_ATTACK_WEAK, 0.9f, 0.5f);
			return;
		}
		IronManComicAccess.hit(p, t, dmg);
		IronManComicAccess.shove(t, p.position(), HAYMAKER_KNOCK * (full ? 1.0 : 0.6), 0.5);
		if (full && t instanceof Mob mob) {
			IronManComicAccess.stun(mob, EraOneMk12State.STAGGER_TICKS);
		} else if (full) {
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, EraOneMk12State.STAGGER_TICKS, 4);
		}
		impact(p, t, full);
	}

	private static void impact(ServerPlayer p, LivingEntity t, boolean heavy) {
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 c = IronManComicAccess.centre(t);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, heavy ? 30 : 14, 0.3, 0.3, 0.3, 0.25);
		level.sendParticles(heavy ? ParticleTypes.EXPLOSION : ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		IronManComicAccess.soundAt(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.1f, heavy ? 0.6f : 0.8f);
		if (heavy) {
			IronManComicAccess.soundAt(level, c, SoundEvents.ANVIL_LAND, 0.7f, 0.6f);
		}
	}

	// ================================================================== X: rocket jump / rocket leap

	/** Straight up ~8 blocks on the boot rockets; fall-safe until you land. */
	private static boolean rocketJump(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Vec3 v = p.getDeltaMovement();
		IronManComicAccess.launch(p, new Vec3(v.x * 0.5, ROCKET_JUMP_VY, v.z * 0.5));
		rocketFx(p, suit, 1.0f);
		return true;
	}

	/** A long forward arc of ~14 blocks; fall-safe until you land. */
	private static boolean rocketLeap(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Vec3 flat = flatLook(p);
		Vec3 v = AbilityHelpers.ballisticLaunch(new Vec3(flat.x, 0.45, flat.z), ROCKET_LEAP_BLOCKS, p.onGround());
		IronManComicAccess.launch(p, v);
		rocketFx(p, suit, 0.85f);
		return true;
	}

	private static void rocketFx(ServerPlayer p, IronManSuit suit, float pitch) {
		Rt r = EraOneMk12State.rt(p, suit);
		long now = p.level().getGameTime();
		r.rocketStart = now;
		r.rocketUntil = now + EraOneMk12State.ROCKET_SAFE_TICKS;
		p.resetFallDistance();
		ServerLevel level = IronManComicAccess.level(p);
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 24, 0.25, 0.05, 0.25, 0.06);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY(), p.getZ(), 14, 0.35, 0.05, 0.35, 0.03);
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
		IronManComicAccess.pose(p, IronManAbilityFx.COMIC_LAUNCH, 12);
		IronManComicAccess.sound(p, IronManSounds.FLIGHT_BURST, 1.1f, pitch);
		IronManSounds.play(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.6f);
	}

	// ================================================================== Z: smoke screen / scrap burst

	/** A 6 s smoke cloud (r 5): mobs inside lose you; while you stand in it nothing within 16 blocks keeps you targeted. */
	private static boolean smokeScreen(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		ServerLevel level = IronManComicAccess.level(p);
		IronManComicAccess.smokeArea(p, p.position(), 5.0, EraOneMk12State.SMOKE_TICKS);
		Rt r = EraOneMk12State.rt(p, suit);
		r.smokeUntil = p.level().getGameTime() + EraOneMk12State.SMOKE_TICKS;
		r.smokeAt = p.position();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16.0), e -> e.getTarget() == p)) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		IronManComicAccess.cloud(level, p.position().add(0, 1, 0), ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, 30, 1.5, 0.02);
		IronManComicAccess.cloud(level, p.position().add(0, 0.5, 0), ParticleTypes.LARGE_SMOKE, 50, 2.0, 0.05);
		IronManComicAccess.pose(p, IronManAbilityFx.COMIC_FLARE, 10);
		IronManComicAccess.soundAt(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 1.2f, 0.6f);
		return true;
	}

	/** A close shotgun cone of scrap: 4 pellets x 7, out to 8 blocks; each one hits the first thing on its line. */
	private static boolean scrapBurst(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = IronManComicAccess.aim(p, SCRAP_RANGE);
		Vec3 right = new Vec3(-look.z, 0, look.x);
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		// a fixed spread pattern (a diamond round the aim) so the cone is the same every shot
		double[][] spread = { { 0.0, 0.05 }, { 0.0, -0.05 }, { 0.07, 0.0 }, { -0.07, 0.0 } };
		ItemParticleOption scrap = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.IRON_NUGGET));
		for (int i = 0; i < SCRAP_PELLETS; i++) {
			Vec3 dir = look.add(right.scale(spread[i % 4][0])).add(up.scale(spread[i % 4][1])).normalize();
			double len = IronManComicAccess.wallDistance(p, eye, dir, SCRAP_RANGE);
			List<LivingEntity> hits = IronManComicAccess.along(p, eye, eye.add(dir.scale(len)), 0.45);
			Vec3 end = eye.add(dir.scale(len));
			if (!hits.isEmpty()) {
				LivingEntity t = hits.get(0);
				end = IronManComicAccess.centre(t);
				IronManComicAccess.hit(p, t, m.damage());
				IronManComicAccess.shove(t, eye, 0.35, 0.1);
			}
			IronManComicAccess.line(level, IronManComicAccess.palm(p), end, ParticleTypes.CRIT, 2.0);
			level.sendParticles(scrap, end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0.12);
		}
		Vec3 muzzle = IronManComicAccess.palm(p);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.03);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 6, 0.08, 0.08, 0.08, 0.05);
		IronManComicAccess.pose(p, IronManAbilityFx.REPULSOR, 8);
		IronManComicAccess.soundAt(level, muzzle, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.7f);
		IronManComicAccess.soundAt(level, muzzle, SoundEvents.ANVIL_PLACE, 0.5f, 1.6f);
		return true;
	}

	// ================================================================== V: brace (repel is the shared comic one)

	/** 4 s: 60% of every hit stopped, no knockback, rooted to the spot. */
	private static boolean brace(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Rt r = EraOneMk12State.rt(p, suit);
		EraOneMk12State.startBrace(p, r, p.level().getGameTime());
		ServerLevel level = IronManComicAccess.level(p);
		IronManComicAccess.groundRing(level, p.position(), 0.8, 10);
		IronManComicAccess.pose(p, IronManAbilityFx.COMIC_GUARD, 12);
		IronManComicAccess.sound(p, IronManSounds.CLAMP, 1.2f, 0.5f);
		IronManComicAccess.sound(p, IronManSounds.SERVO, 1.0f, 0.5f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk12.brace"), true);
		return true;
	}

	// ================================================================== C: reactor overload (both suits)

	/** The next hit lands x2 (armed 10 s) -- on the Mk I, lighting the flamethrower instead gives 3 s of double flame. */
	static boolean reactorOverload(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m) {
		Rt r = EraOneMk12State.rt(p, suit);
		long now = p.level().getGameTime();
		r.overloadUntil = now + EraOneMk12State.OVERLOAD_ARMED_TICKS;
		r.overloadFlameUntil = 0L;
		ServerLevel level = IronManComicAccess.level(p);
		Vec3 c = IronManComicAccess.chest(p);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 0.4, 0.5, 0.4, 0.25);
		IronManComicAccess.ring(level, p.position().add(0, 1.0, 0), 1.2, ParticleTypes.FLAME, 20);
		IronManComicAccess.pose(p, IronManAbilityFx.COMIC_FLARE, 14);
		IronManComicAccess.sound(p, IronManSounds.POWER_UP, 1.0f, 0.7f);
		IronManComicAccess.soundAt(level, c, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.5f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk12.overload_armed"), true);
		return true;
	}

	// ================================================================== helpers

	static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
	}

	/** The ground at or below {@code at} (within 4 blocks), else {@code at}. */
	private static Vec3 groundAt(ServerPlayer p, Vec3 at) {
		var hit = p.level().clip(new net.minecraft.world.level.ClipContext(at.add(0, 1.0, 0), at.subtract(0, 4.0, 0),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, p));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? at : hit.getLocation();
	}

	/** The held / toggled move's per-second energy (the kit table's {@code energyPerSecond}). False = out of power. */
	static boolean drain(ServerPlayer p, IronManSuit suit, String moveId) {
		return true; // TODO(CORE drain): IronManEraOne per-tick drain of IronManComicKit.energyPerSecond(moveId) / 20
	}
}
