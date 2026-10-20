package com.projecthero.mod.ironman.ability;

import java.util.List;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Era I (MK12): a public window onto the package-private comic building blocks ({@link IronManComicFx},
 * {@link IronManComicState}, a few {@link IronManComicMoves} moves) for the Era I move classes in {@code ability.era1}.
 * Pure delegation -- the comic code stays the one implementation.
 */
public final class IronManComicAccess {
	public static final int BEAM_REPULSOR = IronManComicFx.BEAM_REPULSOR;
	public static final int BEAM_CHARGED = IronManComicFx.BEAM_CHARGED;

	private IronManComicAccess() {
	}

	public static ServerLevel level(ServerPlayer p) {
		return IronManComicFx.level(p);
	}

	public static Vec3 aim(ServerPlayer p, double range) {
		return IronManComicFx.aim(p, range);
	}

	public static Vec3 palm(ServerPlayer p) {
		return IronManComicFx.palm(p);
	}

	public static Vec3 chest(ServerPlayer p) {
		return IronManComicFx.chest(p);
	}

	public static Vec3 centre(LivingEntity e) {
		return IronManComicFx.centre(e);
	}

	public static LivingEntity target(ServerPlayer p, double range) {
		return IronManComicFx.target(p, range);
	}

	public static double wallDistance(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
		return IronManComicFx.wallDistance(p, from, dir, range);
	}

	public static List<LivingEntity> around(ServerPlayer p, Vec3 centre, double radius) {
		return IronManComicFx.around(p, centre, radius);
	}

	public static List<LivingEntity> cone(ServerPlayer p, Vec3 from, Vec3 dir, double range, double halfDeg) {
		return IronManComicFx.cone(p, from, dir, range, halfDeg);
	}

	public static List<LivingEntity> along(ServerPlayer p, Vec3 a, Vec3 b, double width) {
		return IronManComicFx.along(p, a, b, width);
	}

	public static boolean hit(ServerPlayer p, LivingEntity e, float damage) {
		return IronManComicFx.hit(p, e, damage);
	}

	public static boolean burn(ServerPlayer p, LivingEntity e, float damage, int fireTicks) {
		return IronManComicFx.burn(p, e, damage, fireTicks);
	}

	public static void shove(LivingEntity e, Vec3 from, double strength, double up) {
		IronManComicFx.shove(e, from, strength, up);
	}

	public static void launch(ServerPlayer p, Vec3 velocity) {
		IronManComicFx.launch(p, velocity);
	}

	public static List<LivingEntity> beam(ServerPlayer p, Vec3 from, Vec3 dir, double range, double width, boolean pierce, int kind,
			float damage, double knock) {
		return IronManComicFx.beam(p, from, dir, range, width, pierce, kind, damage, knock);
	}

	public static LivingEntity repulsor(ServerPlayer p, float damage, double range, int kind) {
		return IronManComicFx.repulsor(p, damage, range, kind);
	}

	public static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions particle, int points) {
		IronManComicFx.ring(level, c, radius, particle, points);
	}

	public static void groundRing(ServerLevel level, Vec3 c, double radius, int points) {
		IronManComicFx.groundRing(level, c, radius, points);
	}

	public static void cloud(ServerLevel level, Vec3 c, ParticleOptions particle, int count, double spread, double speed) {
		IronManComicFx.cloud(level, c, particle, count, spread, speed);
	}

	public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions particle, double perBlock) {
		IronManComicFx.line(level, a, b, particle, perBlock);
	}

	public static void sound(ServerPlayer p, SoundEvent s, float volume, float pitch) {
		IronManComicFx.sound(p, s, volume, pitch);
	}

	public static void soundAt(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
		IronManComicFx.soundAt(level, at, s, volume, pitch);
	}

	public static void soundAt(ServerLevel level, Vec3 at, net.minecraft.core.Holder<SoundEvent> s, float volume, float pitch) {
		IronManComicFx.soundAt(level, at, s, volume, pitch);
	}

	public static void pose(ServerPlayer p, int anim, int ticks) {
		IronManComicFx.pose(p, anim, ticks);
	}

	/** EMP-style stun (loses its target, barely moves) for {@code ticks}. */
	public static void stun(Mob mob, int ticks) {
		IronManComicState.stun(mob, ticks);
	}

	/** Pull loose items within {@code radius} toward {@code to}; returns how many. */
	public static int pullItems(ServerPlayer p, Vec3 to, double radius, double speed) {
		return IronManComicState.pullItems(p, to, radius, speed);
	}

	/** A comic smoke cloud ({@code IronManComicState} SMOKE area: blinds what is inside, mobs inside lose the owner). */
	public static void smokeArea(ServerPlayer p, Vec3 centre, double radius, int ticks) {
		IronManComicState.area(p, IronManComicState.AreaKind.SMOKE, centre, radius, ticks);
	}

	/** The comic Magnetic Repel (shove everything in {@code radius}, deflect items / projectiles). */
	public static boolean repel(ServerPlayer p, double radius, double strength, float damage) {
		return IronManComicMoves.repel(p, radius, strength, damage);
	}

	/** The comic chest-lamp flash: blinds, slows and weakens everything in the cone; mobs drop the wearer as target. */
	public static boolean lampFlash(ServerPlayer p, double range, double halfDeg, int blindTicks) {
		return IronManComicMoves.lampFlash(p, range, halfDeg, blindTicks);
	}
}
