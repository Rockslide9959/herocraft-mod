package com.projecthero.mod.ironman;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.27, explicit user request: flying into the ground in Iron Man armour -- the same "hard landing" the client
 * plays the three-point superhero landing for ({@link IronManFlightLook#isHardLanding}) -- slams everything around the
 * landing point: {@value #DAMAGE} damage within {@value #RADIUS} blocks, knocked outward. Never the wearer, never a
 * squadmate (and the usual {@link HeroTargets#canHarm} rules: pets, PvP-off players, armour stands...). A
 * {@value #COOLDOWN_TICKS}-tick cooldown keeps it from being spammed.
 *
 * <p>The server cannot read a client-moved player's velocity, so it keeps its own short history of per-tick position
 * deltas while the player flies (the same lookback the client uses) and judges the landing from that.
 */
public final class IronManLandingSlam {
	public static final float DAMAGE = 20f;
	public static final double RADIUS = 3.5;
	public static final int COOLDOWN_TICKS = 60;
	public static final double KNOCKBACK = 1.2;

	private static final Map<UUID, Track> TRACKS = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

	private IronManLandingSlam() {
	}

	private static final class Track {
		Vec3 last;
		final double[] speeds = new double[IronManFlightLook.LANDING_LOOKBACK_TICKS];
		final double[] descents = new double[IronManFlightLook.LANDING_LOOKBACK_TICKS];
		int i;
	}

	/** Flight toggled on / off in the air: forget the old motion history. */
	public static void reset(ServerPlayer player) {
		TRACKS.remove(player.getUUID());
	}

	/** Every server tick while the player flies: remember this tick's motion. */
	public static void recordFlightTick(ServerPlayer player) {
		Track t = TRACKS.computeIfAbsent(player.getUUID(), k -> new Track());
		Vec3 now = player.position();
		if (t.last != null) {
			Vec3 d = now.subtract(t.last);
			int slot = t.i++ % t.speeds.length;
			t.speeds[slot] = d.length();
			t.descents[slot] = Math.max(0.0, -d.y);
		}
		t.last = now;
	}

	/** The flight just ended on the ground: slam if it was a hard landing and the cooldown allows. */
	public static boolean onLanded(ServerPlayer player) {
		Track t = TRACKS.remove(player.getUUID());
		if (t == null) {
			return false;
		}
		// the move that touched down is not in the history yet (onGround is checked before the record)
		Vec3 now = player.position();
		double peakSpeed = 0.0, peakDescent = 0.0;
		if (t.last != null) {
			Vec3 d = now.subtract(t.last);
			peakSpeed = d.length();
			peakDescent = Math.max(0.0, -d.y);
		}
		for (int k = 0; k < t.speeds.length; k++) {
			peakSpeed = Math.max(peakSpeed, t.speeds[k]);
			peakDescent = Math.max(peakDescent, t.descents[k]);
		}
		if (!IronManFlightLook.isHardLanding(peakSpeed, peakDescent, true)) {
			return false;
		}
		return trySlam(player);
	}

	/** Slam at the player's feet unless it is on cooldown. Public for the gametests. */
	public static boolean trySlam(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long until = COOLDOWN_UNTIL.get(player.getUUID());
		if (until != null && now < until) {
			return false;
		}
		COOLDOWN_UNTIL.put(player.getUUID(), now + COOLDOWN_TICKS);
		slam(player, player.position());
		return true;
	}

	/** Damage + knock back everything harmable around {@code center}; returns how many were hit. */
	public static int slam(ServerPlayer player, Vec3 center) {
		ServerLevel level = (ServerLevel) player.level();
		AABB box = new AABB(center, center).inflate(RADIUS, RADIUS * 0.6, RADIUS);
		List<LivingEntity> hits = HeroTargets.harmable(level, player, box);
		int n = 0;
		for (LivingEntity e : hits) {
			if (e == player || e.position().distanceTo(center) > RADIUS + e.getBbWidth() * 0.5) {
				continue;
			}
			if (e instanceof Player other && Squads.areAllies(player, other)) {
				continue; // never a squadmate, friendly fire or not
			}
			AbilityHelpers.hurt(player, e, DAMAGE);
			Vec3 away = e.position().subtract(center);
			Vec3 flat = new Vec3(away.x, 0.0, away.z);
			flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
			e.push(flat.x * KNOCKBACK, 0.45, flat.z * KNOCKBACK);
			e.hurtMarked = true;
			n++;
		}
		level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.2, center.z, 2, 0.6, 0.1, 0.6, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.1, center.z, 24, RADIUS * 0.5, 0.05, RADIUS * 0.5, 0.08);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.3f);
		return n;
	}

	/** Server stopped: drop the per-player scratch maps (see ServerStateReset). */
	public static void clearSessionState() {
		TRACKS.clear();
		COOLDOWN_UNTIL.clear();
	}
}
