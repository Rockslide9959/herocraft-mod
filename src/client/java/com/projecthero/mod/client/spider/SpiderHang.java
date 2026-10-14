package com.projecthero.mod.client.spider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.render.JointedLimbs;
import com.projecthero.mod.spider.SpiderClimb;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: the classic upside-down web hang, posed with jointed limbs. He hangs head-down, body straight; the line comes
 * down from above to where his feet meet -- the feet up top, pressed together sole to sole round the line -- and both
 * knees are bent sharply and splayed out wide to the sides (thighs out sideways, shins back up and in to the feet: the
 * perch's frog diamond, inverted -- with a model's 6 + 6 px leg bones and the feet together, the knees can only sit
 * about level with the hips-plus-a-few-px, not below them). Both arms reach "up" toward the feet, elbows bent and out,
 * the hands together on the line a little below the feet; the head is at the bottom, looking where he looks. The line
 * is drawn from above down through the gap between his soles to the hands' grip ({@link #lineEnd}).
 *
 * <p>Built like {@link SpiderPerch}: every limb is a two-bone chain solved in model space (the model is posed upright
 * and the whole body is turned over afterwards by {@link #applyBodyRotation}, so model +y -- "down" -- ends up pointing
 * up), the upper bone becomes the vanilla part's rotation and the lower bone the {@link JointedLimbs} joint, so the
 * skin, the red suit, armour and the GeckoLib Black Suit all bend the same way. Everything is blended by the eased hang
 * amount ({@link #amount}): the body flips over (a cartwheel about its middle) and the limbs fold in over about a
 * quarter second, and unfold again when he lets go.
 *
 * <p>Model space: px, +y down, the front is -z, the model's right is -x.
 */
public final class SpiderHang {
	/** Hips (vanilla pivots) and where the feet meet, up the line from them: a little forward of the hips. */
	private static final float HIP_Y = 12.0f;
	private static final float HIP_X = 2.2f;
	private static final float FOOT_Y = 17.6f;
	private static final float FOOT_X = 1.8f;
	private static final float FOOT_Z = -1.2f;
	/** Where the knees push: out to the side and toward his head (-y). */
	private static final float LEG_POLE_X = 1.0f;
	private static final float LEG_POLE_Y = -0.7f;
	private static final float LEG_POLE_Z = 0.0f;
	/** Shoulders: pulled in and shrugged toward the hips ("reaching up" the line). */
	private static final float SHOULDER_X = 4.4f;
	private static final float SHOULDER_Y = 3.8f;
	/** The hands' pivot-line ends: together on the line in front of the hips, a little below the feet. */
	private static final float HAND_X = 1.0f;
	private static final float HAND_Y = 12.0f;
	private static final float HAND_Z = -3.0f;
	/** Elbows point out and back. */
	private static final float ARM_POLE_X = 1.0f;
	private static final float ARM_POLE_Y = 0.2f;
	private static final float ARM_POLE_Z = 0.4f;
	/** Where the hands grip the line (model px): the line is drawn to here, through the feet. */
	private static final float GRIP_Y = 10.8f;
	private static final float GRIP_Z = -2.8f;

	/** PlayerRenderer's model scale and LivingEntityRenderer's model offset. */
	private static final float MODEL_SCALE = 0.9375f;
	private static final float MODEL_OFFSET = 1.501f;

	/** Per player: {eased amount 0..1, last update ns}. */
	private static final Map<UUID, double[]> EASE = new ConcurrentHashMap<>();
	/** Per player: when he was last seen crawling a ceiling (ns) -- a hang dropped from it starts already flipped. */
	private static final Map<UUID, Long> CEILING_SEEN = new ConcurrentHashMap<>();

	private SpiderHang() {
	}

	/** Model y (px) of a point -> its height above the bottom of the box once the body is flipped (blocks). */
	private static double flippedHeight(Player player, float modelY) {
		return player.getBbHeight() - (MODEL_OFFSET - modelY / 16.0) * MODEL_SCALE;
	}

	/**
	 * How far below the top of his box the feet meet (blocks) at full hang -- {@code SpiderMoves.HANG_FEET_BELOW_TOP}
	 * (common code, used for the line length) must match this (0.38).
	 */
	public static double feetBelowTop() {
		return (MODEL_OFFSET - FOOT_Y / 16.0) * MODEL_SCALE;
	}

	/** Called while the ceiling-crawl flip is drawn. */
	public static void noteCeiling(Player player) {
		CEILING_SEEN.put(player.getUUID(), System.nanoTime());
	}

	/** The hang, eased in / out over about five ticks (0 = not hanging). */
	public static float amount(Player player) {
		boolean on = SpiderMan.hasPower(player) && SpiderMoves.hangingUpsideDown(player);
		UUID id = player.getUUID();
		double[] e = EASE.get(id);
		if (e == null) {
			if (!on) {
				return 0f;
			}
			Long seen = CEILING_SEEN.get(id);
			// dropped straight off a ceiling he was crawling (already upside down): no flip-over
			double start = seen != null && System.nanoTime() - seen < 400_000_000L ? 1.0 : 0.0;
			e = new double[] {start, System.nanoTime()};
			EASE.put(id, e);
		}
		if (!on && SpiderClimb.onCeiling(player)) {
			EASE.remove(id); // zipped back onto the ceiling: the crawl's own flip takes over at once
			return 0f;
		}
		long now = System.nanoTime();
		double dt = Math.min(0.2, (now - e[1]) / 1.0e9);
		e[1] = now;
		e[0] = Mth.clamp(e[0] + (on ? dt : -dt) * 4.0, 0.0, 1.0);
		if (!on && e[0] <= 0.0) {
			EASE.remove(id);
			return 0f;
		}
		float f = (float) e[0];
		return f * f * (3f - 2f * f);
	}

	/** The last eased amount without advancing it (for the line end, which is drawn after the body). */
	private static float peek(Player player) {
		double[] e = EASE.get(player.getUUID());
		if (e == null) {
			return 0f;
		}
		float f = (float) e[0];
		return f * f * (3f - 2f * f);
	}

	/** How fast he is going up / down the line (blocks per tick, from the last tick's move). */
	private static double verticalSpeed(Player player) {
		return player.getY() - player.yo;
	}

	/**
	 * Turn the body over (from {@code PlayerRendererMixin#setupRotations}): a cartwheel about the middle of his box,
	 * 180 degrees at full hang, plus a small sway about the top of the line -- livelier while he climbs or lowers.
	 */
	public static void applyBodyRotation(Player player, PoseStack poseStack, float partialTicks) {
		float k = amount(player);
		if (k <= 0f) {
			return;
		}
		float h = player.getBbHeight();
		float t = player.tickCount + partialTicks;
		float moving = (float) Mth.clamp(Math.abs(verticalSpeed(player)) / 0.35, 0.0, 1.0);
		float swayX = (Mth.sin(t * 0.09f) * 1.6f + Mth.sin(t * 0.55f) * 4.0f * moving) * k;
		float swayZ = (Mth.cos(t * 0.07f) * 1.2f + Mth.cos(t * 0.45f) * 2.5f * moving) * k;
		// sway about the top of the box (where the line holds him)
		poseStack.translate(0.0f, h, 0.0f);
		poseStack.mulPose(Axis.XP.rotationDegrees(swayX));
		poseStack.mulPose(Axis.ZP.rotationDegrees(swayZ));
		poseStack.translate(0.0f, -h, 0.0f);
		// the flip: about the middle of the box (at k = 1 exactly the old translate(h) + 180 deg about Z)
		poseStack.translate(0.0f, h * 0.5f, 0.0f);
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f * k));
		poseStack.translate(0.0f, -h * 0.5f, 0.0f);
	}

	/** Pose {@code m} for {@code player} (from {@code SpiderPose.apply}, after vanilla's animation and the perch). */
	public static void apply(Player player, HumanoidModel<?> m) {
		float k = amount(player);
		if (k <= 0f) {
			return;
		}
		SpiderMoveState s = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		boolean weaving = s != null && s.has(SpiderMoveState.FLAG_WEAVING);
		float t = player.tickCount;
		float moving = (float) Mth.clamp(Math.abs(verticalSpeed(player)) / 0.35, 0.0, 1.0);
		pose(m, k, weaving, Mth.sin(t * 0.9f) * moving);
	}

	/**
	 * The hang at strength {@code k}. {@code weaving}: the right arm leaves the line and aims where he looks (Weave
	 * Mode). {@code bob} (-1..1): the knees flex a little while he climbs / lowers.
	 */
	public static void pose(HumanoidModel<?> m, float k, boolean weaving, float bob) {
		// ---- the head: the body is turned over about its facing axis, which mirrors pitch and yaw -- undo that, so he
		// looks where the player looks (down at the ground when the player looks down)
		float headX = -Mth.clamp(m.head.xRot, -1.2f, 1.2f);
		float headY = -m.head.yRot;
		SpiderPerch.set(m.head, k, new Vector3f(0f, 0f, 0f), headX, headY, 0f);
		m.hat.copyFrom(m.head);
		// ---- the torso: straight
		SpiderPerch.set(m.body, k, new Vector3f(0f, 0f, 0f), 0f, 0f, 0f);

		// ---- legs: hip -> knee (6 px) -> sole (6 px); the soles meet at the line, the knees pushed out wide
		for (int side = -1; side <= 1; side += 2) {
			ModelPart leg = side < 0 ? m.rightLeg : m.leftLeg;
			Vector3f hip = new Vector3f(side * HIP_X, HIP_Y, 0f);
			Vector3f foot = new Vector3f(side * FOOT_X, FOOT_Y - bob * 0.6f, FOOT_Z);
			Vector3f pole = new Vector3f(side * LEG_POLE_X, LEG_POLE_Y, LEG_POLE_Z).normalize();
			// the toes point out to the side and up the line a little, so the soles face each other round the line
			Vector3f toe = new Vector3f(side * 0.55f, 0.35f, -0.75f).normalize();
			SpiderPerch.Chain c = SpiderPerch.solve(hip, foot, 6f, 6f, pole, toe, false);
			SpiderPerch.place(leg, k, hip, c.upper());
			JointedLimbs.setJoint(side < 0 ? JointedLimbs.RIGHT_LEG : JointedLimbs.LEFT_LEG, c.joint(k));
		}

		// ---- arms: shoulder -> elbow (4 px) -> hand end (6 px), up the line to just below the feet
		for (int side = -1; side <= 1; side += 2) {
			ModelPart arm = side < 0 ? m.rightArm : m.leftArm;
			Vector3f shoulder = new Vector3f(side * SHOULDER_X, SHOULDER_Y, 0f);
			if (weaving && side < 0) {
				// Weave Mode: the right arm straight out along the aim (pitch / yaw mirrored back like the head's)
				SpiderPerch.set(arm, k, shoulder, -1.5708f + headX, headY, 0f);
				JointedLimbs.setJoint(JointedLimbs.RIGHT_ARM, null);
				continue;
			}
			// the arm box's centre line is 1 px outboard of its pivot (wide arms): aim the pivot line 1 px inboard
			Vector3f hand = new Vector3f(side * HAND_X, HAND_Y, HAND_Z);
			Vector3f pole = new Vector3f(side * ARM_POLE_X, ARM_POLE_Y, ARM_POLE_Z).normalize();
			SpiderPerch.Chain c = SpiderPerch.solve(shoulder, hand, 4f, 6f, pole, null, true);
			SpiderPerch.place(arm, k, shoulder, c.upper());
			JointedLimbs.setJoint(side < 0 ? JointedLimbs.RIGHT_ARM : JointedLimbs.LEFT_ARM, c.joint(k));
		}
	}

	/**
	 * Where the hang line ends (world): his hands' grip on the line just below his feet, eased from the old tie-on point
	 * (the top of his box) by the hang amount. The line from above runs down through the point where his feet meet.
	 */
	public static Vec3 lineEnd(Player player, float partial) {
		Vec3 pos = player.getPosition(partial);
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (player == mc.player && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == player) {
			// his own first-person view (the camera sits up by his feet): end the line well above the camera, so it never
			// runs down through the crosshair -- he sees it only looking up
			return player.getEyePosition(partial).add(0, 0.6, 0);
		}
		Vec3 top = pos.add(0, player.getBbHeight() - 0.05, 0);
		float k = peek(player);
		if (k <= 0f) {
			k = 1f; // not drawn yet this frame (first person): the full hang
		}
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		Vec3 front = Vec3.directionFromRotation(0.0f, bodyYaw);
		double fwd = -GRIP_Z / 16.0 * MODEL_SCALE;
		Vec3 grip = pos.add(front.x * fwd, flippedHeight(player, GRIP_Y), front.z * fwd);
		return top.lerp(grip, k);
	}
}
