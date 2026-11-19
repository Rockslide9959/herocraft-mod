package com.projecthero.mod.symbiote;

import java.util.function.Consumer;

import com.projecthero.mod.network.SymbioteVfxPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 tendril-vfx round (user: "instead of spawning in particles for the symbiote powers can you make it the tentacle models and
 * vfx please, for example when player uses symbiote lunge can you make it so that symbiote tendril come off the players legs and push
 * off the ground launching them instead of having the particles"): the Normal host's moves no longer throw squid ink, dust rings,
 * crits or sweep particles. The server sends one small {@link SymbioteVfxPayload} per effect to everyone tracking the host (and the
 * host), and each client draws it as living tendrils in the host's suit colours ({@code client.symbiote.SymbioteVfxClient}). The big
 * tendrils that already were entities ({@code SymbioteTendrilEntity}, the spikes, the blade) are unchanged; the shield, the Spear's
 * charge, the Dive Bomb and Onslaught's wind-up are drawn from synced state, so they need no packets at all.
 *
 * <p>Purely cosmetic: nothing here touches damage, cooldowns or targeting. No static state but the test listener.
 */
public final class SymbioteVfx {
	/** Short tendrils splay out of a hit point and snap back (pos, dir = the blow's direction, size ~ 1). */
	public static final int IMPACT = 1;
	/** A ring of tendrils erupts from the ground and lashes outward to {@code size} blocks (slams, landings, shockwaves). */
	public static final int GROUND_BURST = 2;
	/** Lunge: tendrils sprout from the host's legs, plant on the ground at pos and push off (dir = the launch). */
	public static final int LEG_PUSH = 3;
	/** Tendrils coil round {@code target} for {@code duration} ticks (grabs, pins, the Devour pull, Onslaught's victims). */
	public static final int WRAP = 4;
	/** Tendrils burst out of the host's body all round and recede (Form changes, the shield going up, cloaking, revives). */
	public static final int BODY_BURST = 5;
	/** Maw Bite: two great jaw-tendrils from the host's shoulders clamp shut on pos / {@code target}. */
	public static final int JAWS = 6;
	/** Blade Slash: a living blade-tendril sweeps an arc in front of the host (dir = the aim). */
	public static final int SLASH = 7;
	/** Wall climb: tendrils from the hands grip the wall at pos. */
	public static final int WALL_GRIP = 8;
	/** Spike Rain's target circle: small tendrils writhe up round its edge (radius {@code size}) for {@code duration} ticks. */
	public static final int GROUND_MARK = 9;

	/** Viewers further than this see nothing (the tendrils are small). */
	private static final double RANGE = 96.0;

	/** Set by a gametest to see what would be sent; null in play. */
	public static volatile Consumer<SymbioteVfxPayload> testListener;

	private SymbioteVfx() {
	}

	/** From {@code SymbioteSuitColours.initialize} (common init). */
	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(SymbioteVfxPayload.TYPE, SymbioteVfxPayload.CODEC);
	}

	public static void impact(ServerPlayer owner, Vec3 at, Vec3 dir, float size) {
		send(owner, IMPACT, -1, at, dir, size, 10);
	}

	/** An impact on a creature: on its middle, the blow coming from the host. */
	public static void impact(ServerPlayer owner, Entity target, float size) {
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
		send(owner, IMPACT, target.getId(), at, at.subtract(owner.position().add(0, owner.getBbHeight() * 0.5, 0)), size, 10);
	}

	public static void groundBurst(ServerPlayer owner, Vec3 ground, float radius) {
		send(owner, GROUND_BURST, -1, ground, Vec3.ZERO, radius, 14);
	}

	public static void legPush(ServerPlayer owner, Vec3 ground, Vec3 launch) {
		send(owner, LEG_PUSH, -1, ground, launch, 1.0f, 11);
	}

	public static void wrap(ServerPlayer owner, Entity target, int ticks, float size) {
		send(owner, WRAP, target.getId(), target.position(), Vec3.ZERO, size, ticks);
	}

	public static void bodyBurst(ServerPlayer owner, float size) {
		send(owner, BODY_BURST, -1, owner.position(), Vec3.ZERO, size, 14);
	}

	public static void jaws(ServerPlayer owner, Entity target, Vec3 at, float size) {
		send(owner, JAWS, target == null ? -1 : target.getId(), at, owner.getLookAngle(), size, 9);
	}

	public static void slash(ServerPlayer owner, float reach) {
		send(owner, SLASH, -1, owner.position(), owner.getLookAngle(), reach, 7);
	}

	public static void wallGrip(ServerPlayer owner, Vec3 wall) {
		send(owner, WALL_GRIP, -1, wall, Vec3.ZERO, 1.0f, 8);
	}

	public static void groundMark(ServerPlayer owner, Vec3 centre, float radius, int ticks) {
		send(owner, GROUND_MARK, -1, centre, Vec3.ZERO, radius, ticks);
	}

	/** Sends one effect to the host and everyone tracking them within {@link #RANGE}. */
	public static void send(ServerPlayer owner, int kind, int target, Vec3 at, Vec3 dir, float size, int duration) {
		SymbioteVfxPayload p = new SymbioteVfxPayload(kind, owner.getId(), target, (float) at.x, (float) at.y, (float) at.z,
				(float) dir.x, (float) dir.y, (float) dir.z, size, duration);
		Consumer<SymbioteVfxPayload> listener = testListener;
		if (listener != null) {
			listener.accept(p);
		}
		if (!(owner.level() instanceof ServerLevel level)) {
			return;
		}
		for (ServerPlayer viewer : PlayerLookup.tracking(owner)) {
			if (viewer != owner && viewer.level() == level && viewer.distanceToSqr(at) < RANGE * RANGE && ServerPlayNetworking.canSend(viewer, SymbioteVfxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, p);
			}
		}
		if (owner.connection != null && ServerPlayNetworking.canSend(owner, SymbioteVfxPayload.TYPE)) {
			ServerPlayNetworking.send(owner, p);
		}
	}
}
