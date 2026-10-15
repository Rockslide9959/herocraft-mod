package com.projecthero.mod.client.spider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.client.render.JointedLimbs;
import com.projecthero.mod.spider.SpiderWallRun;
import com.projecthero.mod.spider.data.SpiderManState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.2: the Wall Run pose. The body turns to face along the wall and rolls over sideways until his feet are on the
 * wall face (about 68 degrees), and he runs: long strides with jointed knees, arms pumping with bent elbows, the head
 * counter-rolled to look along the run. Eased in and out. Driven by the synced {@link SpiderWallRun#FLAG} and climb face
 * for other players (the owner uses his own client state, so it starts without a round trip).
 */
public final class SpiderWallRunPose {
	/** Body roll at full run (degrees). */
	private static final float ROLL = 68f;
	/** Distance from his box's centre line to the wall face (blocks): the pivot is set so the soles land on it. */
	private static final float WALL_GAP = 0.32f;

	/** Per player: {eased amount, last face index, last nanos}. */
	private static final Map<UUID, double[]> EASE = new ConcurrentHashMap<>();

	private SpiderWallRunPose() {
	}

	private static boolean running(Player player) {
		if (player == Minecraft.getInstance().player) {
			return SpiderWallRunClient.active();
		}
		return SpiderWallRun.running(player);
	}

	private static Direction face(Player player) {
		if (player == Minecraft.getInstance().player && SpiderWallRunClient.face() != null) {
			return SpiderWallRunClient.face();
		}
		SpiderManState s = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		if (s == null || s.climbMode() != SpiderManState.CLIMB_WALL) {
			return null;
		}
		Direction d = Direction.from3DDataValue(s.climbFace());
		return d.getAxis().isHorizontal() ? d : null;
	}

	/** Advance and return the eased amount (0..1); {@code e[1]} keeps the last wall face for the blend out. */
	private static double[] ease(Player player) {
		UUID id = player.getUUID();
		Direction f = face(player);
		boolean on = running(player) && f != null;
		double[] e = EASE.get(id);
		if (e == null) {
			if (!on) {
				return null;
			}
			e = new double[] {0.0, f.get3DDataValue(), System.nanoTime()};
			EASE.put(id, e);
		}
		long now = System.nanoTime();
		double dt = Math.min(0.1, Math.max(0.0, (now - (long) e[2]) / 1.0e9));
		e[2] = now;
		if (on) {
			e[1] = f.get3DDataValue();
		}
		e[0] = Mth.clamp(e[0] + (on ? dt * 6.0 : -dt * 5.0), 0.0, 1.0);
		if (!on && e[0] <= 0.0) {
			EASE.remove(id);
			return null;
		}
		return e;
	}

	private static float smooth(double f) {
		float x = (float) Mth.clamp(f, 0.0, 1.0);
		return x * x * (3f - 2f * x);
	}

	/** The yaw (degrees) of the way he runs along {@code face}: the tangent on the side he looks toward. */
	private static float runYaw(Player player, Direction face, float partial) {
		float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot) * Mth.DEG_TO_RAD;
		double lx = -Mth.sin(headYaw);
		double lz = Mth.cos(headYaw);
		double tx = -face.getStepZ();
		double tz = face.getStepX();
		double s = lx * tx + lz * tz >= 0.0 ? 1.0 : -1.0;
		return (float) Math.toDegrees(Math.atan2(-tx * s, tz * s));
	}

	/** Is the wall on his right while running at {@code yawDeg}? */
	private static boolean wallOnRight(Direction face, float yawDeg) {
		float y = yawDeg * Mth.DEG_TO_RAD;
		double rx = -Mth.cos(y);
		double rz = -Mth.sin(y);
		return rx * face.getStepX() + rz * face.getStepZ() > 0.0;
	}

	/** Turn the body along the wall and roll it over onto its side, feet on the wall (from {@code SpiderSwingPose}). */
	public static void applyBodyRotation(Player player, PoseStack pose, float partial) {
		double[] e = ease(player);
		if (e == null) {
			return;
		}
		float k = smooth(e[0]);
		if (k <= 0.001f) {
			return;
		}
		Direction face = Direction.from3DDataValue((int) e[1]);
		float runYaw = runYaw(player, face, partial);
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		pose.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(bodyYaw - runYaw) * k));
		float roll = ROLL * k * Mth.DEG_TO_RAD;
		float pivot = Math.min(0.9f, WALL_GAP / Math.max(0.36f, Mth.sin(roll)));
		pose.translate(0.0f, pivot, 0.0f);
		pose.mulPose(Axis.ZP.rotation(wallOnRight(face, runYaw) ? roll : -roll));
		pose.translate(0.0f, -pivot, 0.0f);
	}

	/** The running stride (from {@code SpiderSwingPose.applyLimbs}). */
	public static void applyLimbs(Player player, HumanoidModel<?> m, float partial) {
		double[] e = EASE.get(player.getUUID());
		if (e == null) {
			return;
		}
		float k = smooth(e[0]);
		if (k <= 0.001f) {
			return;
		}
		Direction face = Direction.from3DDataValue((int) e[1]);
		float runYaw = runYaw(player, face, partial);
		boolean right = wallOnRight(face, runYaw);
		float ph = (player.tickCount + partial) * 0.95f;
		float s = Mth.sin(ph);
		float c = Mth.cos(ph);
		// a slight forward lean into the run
		HipBend.pitch(m, k, 0.22f);
		// legs: long strides; the knee folds as the leg comes through (recovery), opens as it reaches forward
		float thighR = -0.25f - 1.0f * s;
		float thighL = -0.25f + 1.0f * s;
		float kneeR = 0.25f + 1.45f * Math.max(0f, c);
		float kneeL = 0.25f + 1.45f * Math.max(0f, -c);
		SpiderPerch.set(m.rightLeg, k, new Vector3f(m.rightLeg.x, m.rightLeg.y, m.rightLeg.z), thighR, 0f, 0.04f);
		SpiderPerch.set(m.leftLeg, k, new Vector3f(m.leftLeg.x, m.leftLeg.y, m.leftLeg.z), thighL, 0f, -0.04f);
		JointedLimbs.setJoint(JointedLimbs.RIGHT_LEG, new Quaternionf().rotationX(kneeR * k));
		JointedLimbs.setJoint(JointedLimbs.LEFT_LEG, new Quaternionf().rotationX(kneeL * k));
		// arms pump against the legs, elbows bent
		SpiderPerch.set(m.rightArm, k, new Vector3f(m.rightArm.x, m.rightArm.y, m.rightArm.z), -0.2f + 0.95f * s, 0f, 0.12f);
		SpiderPerch.set(m.leftArm, k, new Vector3f(m.leftArm.x, m.leftArm.y, m.leftArm.z), -0.2f - 0.95f * s, 0f, -0.12f);
		JointedLimbs.setJoint(JointedLimbs.RIGHT_ARM, new Quaternionf().rotationX(-1.35f * k));
		JointedLimbs.setJoint(JointedLimbs.LEFT_ARM, new Quaternionf().rotationX(-1.35f * k));
		// the head looks along the run, counter-rolled against the body's lean
		float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
		m.head.yRot = Mth.lerp(k, m.head.yRot, Mth.clamp(Mth.wrapDegrees(headYaw - runYaw), -60f, 60f) * Mth.DEG_TO_RAD);
		m.head.zRot = Mth.lerp(k, m.head.zRot, (right ? -1f : 1f) * ROLL * 0.45f * Mth.DEG_TO_RAD);
		m.hat.copyFrom(m.head);
	}

	public static void clear() {
		EASE.clear();
	}
}
