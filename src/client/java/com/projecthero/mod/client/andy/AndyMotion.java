package com.projecthero.mod.client.andy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyStages;
import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: how Andy's whole body moves -- the flight's torpedo line (blended in and out, banking into turns, pitched with the
 * look, a flutter from the jets) and the head-only stages (the rolling head, the hand walk lowered onto its hands, the hops and
 * the limp). Client-side and per viewer: it only reads the synced state and the player's own movement. Applied to the
 * renderer's root pose ({@code AndyBodyRotationMixin}, after {@code setupRotations}); the limbs are set by {@link AndyPose}.
 */
public final class AndyMotion {
	/** Per player: blend into flight (0..1) now and last tick, bank now and last tick, distance rolled now and last tick. */
	private static final class Track {
		float blend, prevBlend, bank, prevBank;
		double rolled, prevRolled;
		Vec3 lastPos;
		float lastYaw;
		boolean boost;
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();

	/** One model px (of the 0.9375-scaled player), in blocks. */
	static final float PX = 0.9375f / 16f;
	/** The neck's height (the model's pivot line) and the head's centre, in blocks from the feet. */
	static final float NECK = 1.501f * 0.9375f, HEAD_CENTRE = NECK + 4f * PX;

	private AndyMotion() {
	}

	static void reset() {
		TRACKS.clear();
	}

	/** From the client tick, for every Andy in view. */
	static void tick(Player player, AndyState s) {
		Track t = TRACKS.computeIfAbsent(player.getUUID(), k -> new Track());
		t.prevBlend = t.blend;
		t.prevBank = t.bank;
		t.prevRolled = t.rolled;
		boolean flying = s.flag(AndyState.FLAG_PROPELLING);
		t.boost = s.flag(AndyState.FLAG_BOOST);
		// ease in fast (the take-off is a snap of blood), out a little slower
		t.blend = flying ? Math.min(1f, t.blend + 0.22f + 0.3f * (1f - t.blend)) : Math.max(0f, t.blend - 0.12f);
		float yaw = player.getYHeadRot();
		float rate = Mth.wrapDegrees(yaw - t.lastYaw);
		t.lastYaw = yaw;
		float wantBank = flying ? Mth.clamp(rate * 2.2f, -55f, 55f) : 0f;
		t.bank += (wantBank - t.bank) * 0.25f;
		Vec3 pos = player.position();
		if (t.lastPos != null) {
			Vec3 d = pos.subtract(t.lastPos);
			double yawR = Math.toRadians(player.yBodyRot);
			double fwd = d.x * -Math.sin(yawR) + d.z * Math.cos(yawR);
			if (d.horizontalDistanceSqr() < 4.0) {
				t.rolled += fwd;
			}
		}
		t.lastPos = pos;
	}

	static void forgetMissing(java.util.Set<UUID> present) {
		TRACKS.keySet().retainAll(present);
	}

	/** 0..1: how far into the flight pose. */
	public static float flightBlend(Player player, float partial) {
		Track t = TRACKS.get(player.getUUID());
		return t == null ? 0f : Mth.lerp(partial, t.prevBlend, t.blend);
	}

	/** The distance (blocks) rolled forward so far, for the rolling head. */
	static double rolled(Player player, float partial) {
		Track t = TRACKS.get(player.getUUID());
		return t == null ? 0.0 : Mth.lerp(partial, t.prevRolled, t.rolled);
	}

	/**
	 * The root pose (in {@code setupRotations}' frame: feet at 0, +y up, the body's facing -z after the yaw). Flight first;
	 * otherwise the stage.
	 */
	public static void apply(Player player, PoseStack pose, float partial, float scale) {
		AndyState s = Andy.peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		Track t = TRACKS.get(player.getUUID());
		float now = player.level().getGameTime() + partial;
		float blend = t == null ? 0f : Mth.lerp(partial, t.prevBlend, t.blend);
		boolean headOnly = s.headOnly(now);
		if (blend > 0.001f) {
			flight(player, s, t, pose, partial, scale, blend, headOnly, now);
			return;
		}
		if (player.isPassenger() || player.isSleeping()) {
			return;
		}
		int stage = AndyStages.stage(s, now);
		float swing = player.walkAnimation.position(partial);
		float amount = Math.min(1f, player.walkAnimation.speed(partial));
		switch (stage) {
			case AndyStages.ROLL -> {
				// the head on the ground, rolling about the axis across its travel by the distance covered
				double rolled = rolled(player, partial);
				float r = 4f * PX * scale;
				pose.translate(0f, r, 0f);
				pose.mulPose(Axis.XP.rotation((float) (-rolled / r)));
				pose.translate(0f, -HEAD_CENTRE * scale, 0f);
			}
			case AndyStages.ARM_WALK -> {
				// on his hands: the whole body lowered by an arm's length, a little bob per step, leaning into the walk
				float bob = Math.abs(Mth.sin(swing * 0.6662f)) * 0.05f * amount;
				pose.translate(0f, (-12f * PX + bob) * scale, 0f);
				pose.translate(0f, 0.7f * scale, 0f);
				pose.mulPose(Axis.XP.rotationDegrees(-8f * amount));
				pose.mulPose(Axis.ZP.rotationDegrees(4f * Mth.sin(swing * 0.6662f) * amount));
				pose.translate(0f, -0.7f * scale, 0f);
			}
			case AndyStages.NUB_HOP -> {
				// hopping on the growing nub: as low as the nub is short, a slow little hop
				int leg = growingLeg(s, now);
				float len = leg < 0 ? 0f : legLength(s, leg, now);
				float hop = Math.abs(Mth.sin(swing * 0.42f)) * 0.16f * amount;
				pose.translate(0f, (-(12f - len) * PX + hop) * scale, 0f);
				// the nub sits off centre: lean over it
				float side = leg == AndyState.RIGHT_LEG ? 1f : -1f;
				pose.mulPose(Axis.ZP.rotationDegrees(5f * side));
			}
			case AndyStages.LEG_HOP -> {
				float hop = Math.abs(Mth.sin(swing * 0.5f)) * 0.22f * amount;
				int leg = s.has(AndyState.RIGHT_LEG, now) ? AndyState.RIGHT_LEG : AndyState.LEFT_LEG;
				float side = leg == AndyState.RIGHT_LEG ? 1f : -1f;
				pose.translate(0f, hop * scale, 0f);
				pose.translate(0f, 0.7f * scale, 0f);
				pose.mulPose(Axis.ZP.rotationDegrees(6f * side));
				pose.mulPose(Axis.XP.rotationDegrees(-6f * amount));
				pose.translate(0f, -0.7f * scale, 0f);
			}
			case AndyStages.LIMP -> {
				int leg = growingLeg(s, now);
				float len = leg < 0 ? 12f : legLength(s, leg, now);
				// the body dips onto the short leg every other step
				float step = Mth.sin(swing * 0.6662f);
				float side = leg == AndyState.RIGHT_LEG ? 1f : -1f;
				float dip = Math.max(0f, step * side) * (12f - len) * PX * amount;
				pose.translate(0f, -dip * scale, 0f);
				pose.translate(0f, 0.7f * scale, 0f);
				pose.mulPose(Axis.ZP.rotationDegrees(-7f * side * Math.max(0f, step * side) * amount * (12f - len) / 12f * 1.6f));
				pose.translate(0f, -0.7f * scale, 0f);
			}
			default -> {
			}
		}
	}

	/** A leg growing back right now, or -1. */
	static int growingLeg(AndyState s, float now) {
		if (AndyStages.growing(s, AndyState.RIGHT_LEG, now)) {
			return AndyState.RIGHT_LEG;
		}
		return AndyStages.growing(s, AndyState.LEFT_LEG, now) ? AndyState.LEFT_LEG : -1;
	}

	/** How long (model px, of 12) a growing leg already reaches: the bone's tip leads, the muscle carries the weight. */
	static float legLength(AndyState s, int leg, float now) {
		float p = s.regrowth(leg, now);
		float qb = com.projecthero.mod.andy.AndyConfig.layer(com.projecthero.mod.andy.AndyConfig.LAYER_BONE, p);
		float qm = com.projecthero.mod.andy.AndyConfig.layer(com.projecthero.mod.andy.AndyConfig.LAYER_MUSCLE, p);
		return Math.min(12f, Math.max(11.9f * qb * qb * (3f - 2f * qb) * 0.85f, 12f * qm));
	}

	private static void flight(Player player, AndyState s, Track t, PoseStack pose, float partial, float scale, float blend, boolean headOnly, float now) {
		float e = blend * blend * (3f - 2f * blend);
		float pitch = player.getViewXRot(partial);
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
		float bank = t == null ? 0f : Mth.lerp(partial, t.prevBank, t.bank);
		boolean boost = t != null && t.boost;
		// the body follows the look exactly (not vanilla's lagging body yaw)
		pose.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(bodyYaw - headYaw) * e));
		if (headOnly) {
			// just the head: it leads, tumbling a little on the neck jet
			float wob = Mth.sin(now * 0.9f) * 9f + Mth.sin(now * 0.37f) * 6f;
			pose.translate(0f, HEAD_CENTRE * scale * (1f - 0.75f * e), 0f);
			pose.mulPose(Axis.XP.rotationDegrees((-pitch + wob) * e));
			pose.mulPose(Axis.ZP.rotationDegrees((bank * 0.6f + Mth.sin(now * 0.53f) * 7f) * e));
			pose.translate(0f, -HEAD_CENTRE * scale, 0f);
			return;
		}
		float pivot = 0.95f * scale;
		float flutter = Mth.sin(now * (boost ? 2.1f : 1.3f)) * (boost ? 2.6f : 1.6f);
		pose.translate(0f, pivot, 0f);
		// the torpedo: lying along the look, the head leading
		pose.mulPose(Axis.XP.rotationDegrees(-(90f + pitch + flutter) * e));
		// banking into turns: a roll about the body's own long axis
		pose.mulPose(Axis.YP.rotationDegrees(-bank * e));
		if (boost) {
			pose.scale(1f, 1f + 0.04f * e, 1f); // stretched by the speed
		}
		pose.translate(0f, -pivot, 0f);
	}
}
