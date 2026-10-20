package com.projecthero.mod.ironman.ability;

import java.util.List;

import com.projecthero.mod.ironman.ability.IronManComicState.AreaKind;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Era I Mk V Stealth: the comic kit's package-private building blocks ({@link IronManComicFx},
 * {@link IronManComicState}) opened up for {@code ability.era1.EraOneMk5Moves} -- the Mk V reuses the Mark 5 comic moves'
 * aiming, stealth beam, private marks, EMP stun and holo-decoy instead of copying them. A separate thin class so the
 * shared comic classes stay untouched (several Era I agents work on them in parallel).
 */
public final class IronManComicMk5Access {
	public static final int BEAM_STEALTH = IronManComicFx.BEAM_STEALTH;
	public static final int DECOY_TICKS = 10 * 20;

	private IronManComicMk5Access() {
	}

	public static ServerLevel level(ServerPlayer p) {
		return IronManComicFx.level(p);
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

	/** The lock, else what is under the crosshair (harmable only), else null. */
	public static LivingEntity target(ServerPlayer p, double range) {
		return IronManComicFx.target(p, range);
	}

	public static double wallDistance(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
		return IronManComicFx.wallDistance(p, from, dir, range);
	}

	public static List<LivingEntity> around(ServerPlayer p, Vec3 centre, double radius) {
		return IronManComicFx.around(p, centre, radius);
	}

	public static boolean hit(ServerPlayer p, LivingEntity e, float damage) {
		return IronManComicFx.hit(p, e, damage);
	}

	public static void control(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks, int amp) {
		IronManComicFx.control(e, effect, ticks, amp);
	}

	/** The Mark 5's thin cyan stealth beam (stops at the first enemy), drawn for everyone. Returns what it hit. */
	public static List<LivingEntity> stealthBeam(ServerPlayer p, Vec3 from, Vec3 dir, double range, float damage) {
		return IronManComicFx.beam(p, from, dir, range, 0.6, false, IronManComicFx.BEAM_STEALTH, damage, 0.0);
	}

	public static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions particle, int points) {
		IronManComicFx.ring(level, c, radius, particle, points);
	}

	public static void pose(ServerPlayer p, int anim, int ticks) {
		IronManComicFx.pose(p, anim, ticks);
	}

	/** The comic EMP stun (target dropped, Slowness VII + Weakness IV, sparks). */
	public static void stun(Mob mob, int ticks) {
		IronManComicState.stun(mob, ticks);
	}

	/** Outline these for the wearer alone ({@code IronManComicMarkPayload}). */
	public static void privateMark(ServerPlayer p, List<? extends Entity> entities, int ticks) {
		IronManComicState.privateMark(p, entities, ticks);
	}

	/** The Mark 5 Holo-Decoy: one at a time, where the wearer stands; mobs on the wearer go for it instead. */
	public static void decoy(ServerPlayer p, int ticks) {
		for (IronManComicState.Area a : IronManComicState.areasOf(p, AreaKind.DECOY)) {
			a.until = 0L;
		}
		IronManComicState.area(p, AreaKind.DECOY, p.position(), 2.0, ticks);
	}

	/** Gametest / HUD: is a holo-decoy of {@code p} standing right now, and where. */
	public static Vec3 decoyAt(ServerPlayer p) {
		List<IronManComicState.Area> decoys = IronManComicState.areasOf(p, AreaKind.DECOY);
		return decoys.isEmpty() ? null : decoys.get(0).centre;
	}

	/** "Unaware" as the Mark 5 comic moves read it: a mob not targeting the wearer. */
	public static boolean unaware(ServerPlayer p, LivingEntity e) {
		return IronManComicMoves.unaware(p, e);
	}

	/** Robots, turrets and Ultron's machines (what an EMP shuts down hardest). */
	public static boolean robotic(LivingEntity e) {
		return IronManComicMoves.robotic(e);
	}
}
