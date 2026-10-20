package com.projecthero.mod.ironman.ability;

import java.util.List;
import java.util.function.Consumer;

import com.projecthero.mod.ironman.entity.IronManMissileEntity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Era I (user): the Mk III Classic / Mk IV Hydro moves ({@code ability.era1.EraOneMk3Moves} /
 * {@code EraOneMk4Moves}) live in another package, so this hands them the comic kit's package-private building blocks
 * ({@link IronManComicFx}, {@link IronManComicState}, {@code IronManAbilities}) unchanged -- one thin public forwarder
 * per helper, so the shared classes themselves need no widening.
 */
public final class EraOneMk34Access {
	public static final int BEAM_REPULSOR = IronManComicFx.BEAM_REPULSOR;
	public static final int BEAM_CHARGED = IronManComicFx.BEAM_CHARGED;
	public static final int BEAM_UNIBEAM = IronManComicFx.BEAM_UNIBEAM;
	public static final int BEAM_PULSE = IronManComicFx.BEAM_PULSE;
	public static final int BEAM_LIGHT = IronManComicFx.BEAM_LIGHT;
	public static final int BEAM_TRACTOR = IronManComicFx.BEAM_TRACTOR;

	private EraOneMk34Access() {
	}

	// ------------------------------------------------------------------ IronManComicFx

	public static ServerLevel level(ServerPlayer p) {
		return IronManComicFx.level(p);
	}

	public static Vec3 palm(ServerPlayer p) {
		return IronManComicFx.palm(p);
	}

	/** The LEFT palm (the mirror of {@link IronManComicFx#palm}). */
	public static Vec3 leftPalm(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 right = IronManAbilities.rightOf(p, look);
		double s = p.getScale();
		return p.getEyePosition().add(look.scale(0.6 * s)).add(right.scale(-0.35 * s)).add(0, -0.35 * s, 0);
	}

	public static Vec3 chest(ServerPlayer p) {
		return IronManComicFx.chest(p);
	}

	public static Vec3 rightOf(ServerPlayer p, Vec3 look) {
		return IronManAbilities.rightOf(p, look);
	}

	public static LivingEntity target(ServerPlayer p, double range) {
		return IronManComicFx.target(p, range);
	}

	public static Vec3 centre(LivingEntity e) {
		return IronManComicFx.centre(e);
	}

	public static double wallDistance(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
		return IronManComicFx.wallDistance(p, from, dir, range);
	}

	public static Vec3 aimPoint(ServerPlayer p, double range) {
		return IronManComicFx.aimPoint(p, range);
	}

	public static Vec3 groundAt(ServerPlayer p, Vec3 at) {
		return IronManComicFx.groundAt(p, at);
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

	public static void shove(LivingEntity e, Vec3 from, double strength, double up) {
		IronManComicFx.shove(e, from, strength, up);
	}

	public static void pull(Entity e, Vec3 to, double speed) {
		IronManComicFx.pull(e, to, speed);
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

	public static IronManMissileEntity missile(ServerPlayer p, Vec3 from, Vec3 dir, double speed, float direct, float splash, float radius) {
		return IronManComicFx.missile(p, from, dir, speed, direct, splash, radius);
	}

	public static void spawn(ServerPlayer p, IronManMissileEntity m) {
		IronManComicFx.spawn(p, m);
	}

	public static Vec3 shoulder(ServerPlayer p, int i) {
		return IronManComicFx.shoulder(p, i);
	}

	public static List<LivingEntity> frontTargets(ServerPlayer p, double range, int n) {
		return IronManComicFx.frontTargets(p, range, n);
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

	public static void broadcastBeam(ServerPlayer p, Vec3 start, Vec3 end, int kind) {
		IronManAbilities.broadcastBeam(p, start, end, kind);
	}

	// ------------------------------------------------------------------ IronManComicState

	public static void later(ServerPlayer p, int ticks, Consumer<ServerPlayer> run) {
		IronManComicState.later(p, ticks, run);
	}

	/** Outline these for the wearer's own client only ({@code IronManComicMarkPayload}) -- never a glow flag. */
	public static void privateMark(ServerPlayer p, List<? extends Entity> entities, int ticks) {
		IronManComicState.privateMark(p, entities, ticks);
	}

	public static void stun(Mob mob, int ticks) {
		IronManComicState.stun(mob, ticks);
	}

	/** The comic kit's whirlpool area (pulls everything harmable in {@code radius} to its centre, 3 a second). */
	public static void whirlpool(ServerPlayer p, Vec3 centre, double radius, int ticks) {
		IronManComicState.area(p, IronManComicState.AreaKind.WHIRLPOOL, centre, radius, ticks);
	}

	public static boolean suited(ServerPlayer p) {
		return IronManComicState.suited(p);
	}
}
