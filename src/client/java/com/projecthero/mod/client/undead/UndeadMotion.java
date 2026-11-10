package com.projecthero.mod.client.undead;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadStages;
import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: how Undead's whole body moves -- the flight's torpedo line (blended in and out, banking into turns, pitched with the
 * look, a flutter from the jets) and the head-only stages (the rolling head, the hand walk lowered onto its hands, the hops and
 * the limp). Client-side and per viewer: it only reads the synced state and the player's own movement. Applied to the
 * renderer's root pose ({@code UndeadBodyRotationMixin}, after {@code setupRotations}); the limbs are set by {@link UndeadPose}.
 */
public final class UndeadMotion {
	/** Per player: blend into flight (0..1) now and last tick, bank now and last tick, distance rolled now and last tick. */
	private static final class Track {
		float blend, prevBlend, bank, prevBank;
		double rolled, prevRolled;
		Vec3 lastPos;
		float lastYaw;
		/** v0.17.3: his smoothed travel (blocks / tick), now and last tick -- the flight leans along it. */
		Vec3 vel = Vec3.ZERO, prevVel = Vec3.ZERO;
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();

	/** One model px (of the 0.9375-scaled player), in blocks. */
	static final float PX = 0.9375f / 16f;
	/** The neck's height (the model's pivot line) and the head's centre, in blocks from the feet. */
	static final float NECK = 1.501f * 0.9375f, HEAD_CENTRE = NECK + 4f * PX;

	private UndeadMotion() {
	}

	static void reset() {
		TRACKS.clear();
	}

	/** From the client tick, for every Undead in view. */
	static void tick(Player player, UndeadState s) {
		Track t = TRACKS.computeIfAbsent(player.getUUID(), k -> new Track());
		t.prevBlend = t.blend;
		t.prevBank = t.bank;
		t.prevRolled = t.rolled;
		boolean flying = s.flag(UndeadState.FLAG_PROPELLING);
		t.prevVel = t.vel;
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
			if (d.lengthSqr() < 16.0) {
				t.vel = t.vel.lerp(d, 0.45);
			}
			double yawR = Math.toRadians(player.yBodyRot);
			double fwd = d.x * -Math.sin(yawR) + d.z * Math.cos(yawR);
			if (d.horizontalDistanceSqr() < 4.0) {
				t.rolled += fwd;
				// the rolling head knocks along the ground
				if (!flying && player.onGround() && d.horizontalDistanceSqr() > 1.0e-4 && player.tickCount % 5 == 0
						&& UndeadStages.stage(s, player.level().getGameTime()) == UndeadStages.ROLL) {
					UndeadSounds.headRoll(player, (float) Math.sqrt(d.horizontalDistanceSqr()) * 20f);
				}
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

	/** v0.17.3: 0 (hovering) .. 1 (flying flat out, ~12 b/s and up): how far the flight pose has gone from hover to torpedo. */
	public static float flightSpeed(Player player, float partial) {
		Track t = TRACKS.get(player.getUUID());
		if (t == null) {
			return 0f;
		}
		Vec3 v = t.prevVel.lerp(t.vel, partial);
		double sp = Math.sqrt(v.x * v.x + v.z * v.z) + Math.abs(v.y) * 0.25; // (rising / sinking stays the hover pose)
		float m = (float) Mth.clamp((sp - 0.08) / 0.52, 0.0, 1.0);
		return m * m * (3f - 2f * m);
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
		UndeadState s = Undead.peek(player);
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
		float age = now - s.animStart;
		if (s.animId == UndeadState.ANIM_BLOOD_WHIRL && age >= 0f && age < com.projecthero.mod.undead.UndeadEjectConfig.WHIRL_TICKS) {
			// v0.17.3 Blood Whirlwind: the whole body turns with the spin (the arms' streams with it), the body yaw stays his own
			float spin = (float) Math.toDegrees(com.projecthero.mod.undead.UndeadBloodMoves.whirlAngle(age));
			pose.mulPose(Axis.YP.rotationDegrees(-spin));
			return;
		}
		if (s.animId == UndeadState.ANIM_EJECT_FEET && age >= 0f && age < 22f && t != null) {
			// v0.17.3 Eject Feet: thrown, the body leans into the flight (easing back up for the landing)
			Vec3 v = t.prevVel.lerp(t.vel, partial);
			double flat = Math.sqrt(v.x * v.x + v.z * v.z);
			float w = Math.min(1f, (22f - age) / 6f) * Mth.clamp(age / 2f, 0f, 1f);
			float lean = (float) Mth.clamp(flat / 0.9, 0.0, 1.0) * 38f * w;
			pose.translate(0f, 0.9f * scale, 0f);
			pose.mulPose(Axis.XP.rotationDegrees(-lean));
			pose.translate(0f, -0.9f * scale, 0f);
			return;
		}
		int stage = UndeadStages.stage(s, now);
		float swing = player.walkAnimation.position(partial);
		float amount = Math.min(1f, player.walkAnimation.speed(partial));
		switch (stage) {
			case UndeadStages.ROLL -> {
				// the head on the ground, rolling about the axis across its travel by the distance covered
				double rolled = rolled(player, partial);
				float r = 4f * PX * scale;
				pose.translate(0f, r, 0f);
				pose.mulPose(Axis.XP.rotation((float) (-rolled / r)));
				pose.translate(0f, -HEAD_CENTRE * scale, 0f);
			}
			case UndeadStages.ARM_WALK -> {
				// on his hands: the whole body lowered by an arm's length, a little bob per step, leaning into the walk
				float bob = Math.abs(Mth.sin(swing * 0.6662f)) * 0.05f * amount;
				pose.translate(0f, (-12f * PX + bob) * scale, 0f);
				pose.translate(0f, 0.7f * scale, 0f);
				pose.mulPose(Axis.XP.rotationDegrees(-8f * amount));
				pose.mulPose(Axis.ZP.rotationDegrees(4f * Mth.sin(swing * 0.6662f) * amount));
				pose.translate(0f, -0.7f * scale, 0f);
			}
			case UndeadStages.CRAWL -> {
				// another player's crawl pose comes from the server a tick or two after the part times say "crawl", and vanilla
				// only lays the body down once its swim amount starts rising: until then keep him on the ground (the head where
				// it lay) instead of popping up to standing height
				if (player.getSwimAmount(partial) < 1.0e-3f) {
					pose.translate(0f, (4f * PX - HEAD_CENTRE) * scale, 0f);
				}
			}
			case UndeadStages.SHUFFLE -> {
				// both stumps growing together: knees bent (UndeadPose), the body as low as their tips reach, a side-to-side
				// waddle with each step and a little bob
				float tip = shuffleTip(bothLegsLength(s, now));
				float step = Mth.sin(swing * 0.75f);
				float bob = Math.abs(step) * 0.035f * amount;
				pose.translate(0f, (-(12f - tip) * PX + bob) * scale, 0f);
				pose.translate(0f, 0.5f * scale, 0f);
				pose.mulPose(Axis.ZP.rotationDegrees(7f * step * amount));
				pose.mulPose(Axis.XP.rotationDegrees(-5f));
				pose.translate(0f, -0.5f * scale, 0f);
			}
			case UndeadStages.WOBBLE -> {
				// up on the new legs, not steady on them yet: a sway even standing still, bigger with each short step
				float len = bothLegsLength(s, now);
				float steady = Math.min(1f, Math.max(0f, (Math.min(s.regrowth(UndeadState.RIGHT_LEG, now), s.regrowth(UndeadState.LEFT_LEG, now))
						- UndeadStages.LEG_STAND) / (1f - UndeadStages.LEG_STAND)));
				float shake = 1f - 0.6f * steady;
				float sway = (Mth.sin(now * 0.31f) * 2.6f + Mth.sin(now * 0.83f) * 1.2f) * shake;
				float step = Mth.sin(swing * 0.6662f);
				pose.translate(0f, -(12f - len) * PX * scale, 0f);
				pose.translate(0f, 0.7f * scale, 0f);
				pose.mulPose(Axis.ZP.rotationDegrees(sway + 5f * step * amount * shake));
				pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(now * 0.47f) * 2.2f * shake - 3f * amount));
				pose.translate(0f, -0.7f * scale, 0f);
			}
			case UndeadStages.LIMP -> {
				int leg = shortLeg(s, now);
				float len = leg < 0 ? 12f : legLength(s, leg, now);
				// the body dips onto the short leg every other step
				float step = Mth.sin(swing * 0.6662f);
				float side = leg == UndeadState.RIGHT_LEG ? 1f : -1f;
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

	/** The leg that is gone or still growing back (limping on the other), or -1. */
	static int shortLeg(UndeadState s, float now) {
		if (!s.has(UndeadState.RIGHT_LEG, now)) {
			return UndeadState.RIGHT_LEG;
		}
		return !s.has(UndeadState.LEFT_LEG, now) ? UndeadState.LEFT_LEG : -1;
	}

	/** The stump shuffle's knee bend (radians, the legs angled back from the hip). */
	static final float KNEEL = 0.6f;

	/** How high (model px) the bent stumps hold the hips. */
	static float shuffleTip(float len) {
		return len * Mth.cos(KNEEL);
	}

	/** The shorter of the two legs (model px): both grow together, so they are nearly the same. */
	static float bothLegsLength(UndeadState s, float now) {
		return Math.min(legLength(s, UndeadState.RIGHT_LEG, now), legLength(s, UndeadState.LEFT_LEG, now));
	}

	/** How long (model px, of 12) a growing leg already reaches: the bone's tip leads, the muscle carries the weight. */
	static float legLength(UndeadState s, int leg, float now) {
		float p = s.regrowth(leg, now);
		float qb = com.projecthero.mod.undead.UndeadConfig.layer(com.projecthero.mod.undead.UndeadConfig.LAYER_BONE, p);
		float qm = com.projecthero.mod.undead.UndeadConfig.layer(com.projecthero.mod.undead.UndeadConfig.LAYER_MUSCLE, p);
		return Math.min(12f, Math.max(11.9f * qb * qb * (3f - 2f * qb) * 0.85f, 12f * qm));
	}

	/**
	 * v0.17.3: the flight's body line. Hovering he hangs upright on his jets with a slow bob; moving, he leans over along the
	 * travel -- flat out he lies along it, head first (diving or climbing with it), sliding sideways he banks into it, backing up he
	 * tips back -- and banks into turns. Just a head: crown first along the travel, tumbling a little on the neck's jet.
	 */
	private static void flight(Player player, UndeadState s, Track t, PoseStack pose, float partial, float scale, float blend, boolean headOnly, float now) {
		float e = blend * blend * (3f - 2f * blend);
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
		float bank = t == null ? 0f : Mth.lerp(partial, t.prevBank, t.bank);
		Vec3 v = t == null ? Vec3.ZERO : t.prevVel.lerp(t.vel, partial);
		float m = flightSpeed(player, partial);
		// the body follows the look exactly (not vanilla's lagging body yaw)
		pose.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(bodyYaw - headYaw) * e));
		// the travel in his own frame: forward along the look's yaw, to his right, up
		double yawR = Math.toRadians(headYaw);
		double fwd = v.x * -Math.sin(yawR) + v.z * Math.cos(yawR);
		double side = v.x * -Math.cos(yawR) + v.z * -Math.sin(yawR);
		double flat = Math.sqrt(fwd * fwd + side * side);
		float travelPitch = (float) Math.toDegrees(Math.atan2(-v.y, Math.max(1.0e-4, flat))); // + diving
		if (headOnly) {
			float wob = Mth.sin(now * 0.9f) * 8f + Mth.sin(now * 0.37f) * 5f;
			pose.translate(0f, Mth.lerp(e, HEAD_CENTRE, 0.25f) * scale, 0f);
			pose.mulPose(Axis.XP.rotationDegrees((-(90f + travelPitch) * m + wob) * e));
			pose.mulPose(Axis.YP.rotationDegrees((-bank * 0.8f + Mth.sin(now * 0.53f) * 10f) * e));
			pose.translate(0f, -HEAD_CENTRE * scale, 0f);
			return;
		}
		float pivot = 0.95f * scale;
		float bob = Mth.sin(now * 0.35f) * 0.04f * (1f - m);
		float flutter = Mth.sin(now * 1.3f) * 1.6f * m;
		// forward travel lays him along it (head first); backing up tips him back a little; sideways he banks over
		float fwdShare = (float) (flat < 1.0e-4 ? 1.0 : fwd / flat);
		// straight up or down he stays upright (rising / sinking on the jets); the more of the travel is level, the more he lies along it
		float level = (float) Mth.clamp(flat / Math.max(1.0e-4, v.length()), 0.0, 1.0);
		level = (float) Math.sqrt(level);
		float lean = (fwdShare >= 0f ? (90f + travelPitch) * fwdShare : 22f * fwdShare) * level;
		// v0.17.3 (user: "when i fly to the right i lean like this ... this doesnt look right"): sliding sideways he tips his head INTO the
		// slide, a little (this frame faces -z with +x his right, so leaning right is a negative Z roll; it was +28, hanging him away from it)
		float sideBank = (float) (flat < 1.0e-4 ? 0.0 : side / flat) * -14f;
		pose.translate(0f, pivot + bob * scale, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(-(Mth.clamp(lean, -30f, 170f) * m + flutter + 6f * (1f - m)) * e));
		pose.mulPose(Axis.ZP.rotationDegrees(sideBank * level * m * e * (fwdShare >= 0f ? 1f - fwdShare * 0.7f : 1f)));
		// banking into turns: a roll about the body's own long axis
		pose.mulPose(Axis.YP.rotationDegrees(-bank * e * m));
		pose.translate(0f, -pivot, 0f);
	}
}
