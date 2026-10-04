package com.projecthero.mod.ironman.ability;

import java.util.LinkedHashSet;
import java.util.Set;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.21: who must be sent an Iron Man beam (repulsor / Unibeam / laser). It used to go only to players <em>tracking
 * the shooter</em>, but a beam reaches much further than the entity-tracking range (min(view distance) x 16 blocks), so
 * the very player being shot from out there never saw the beam that hit them. Now: the shooter, everyone tracking the
 * shooter, and every player in the same level whose eyes are within {@link #RANGE} blocks of any point on the beam
 * segment -- de-duplicated, in that order.
 */
public final class IronManBeamRecipients {
	/** Blocks from the beam segment within which a viewer is sent the beam. */
	public static final double RANGE = 128.0;

	private IronManBeamRecipients() {
	}

	public static Set<ServerPlayer> recipients(ServerPlayer shooter, Vec3 start, Vec3 end) {
		Set<ServerPlayer> out = new LinkedHashSet<>();
		out.add(shooter);
		out.addAll(PlayerLookup.tracking(shooter));
		if (shooter.level() instanceof ServerLevel level) {
			for (ServerPlayer p : level.players()) {
				if (!out.contains(p) && distanceToSegmentSqr(p.getEyePosition(), start, end) <= RANGE * RANGE) {
					out.add(p);
				}
			}
		}
		return out;
	}

	/** Squared distance from {@code p} to the segment {@code a}-{@code b}. */
	public static double distanceToSegmentSqr(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		if (len2 < 1.0e-9) {
			return p.distanceToSqr(a);
		}
		double t = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len2));
		return p.distanceToSqr(a.add(ab.scale(t)));
	}
}
