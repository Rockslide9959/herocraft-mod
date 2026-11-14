package com.projecthero.mod.client.omnitrix.alien;

import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.benwolf.BenwolfKit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * v0.17.4 (user: "Blitzwolfer's hunt sprint doesn't look that good, his head goes up and his arms dont actually touch the ground. also
 * give blitzwolfer a bushy wolf tail please"): Benwolf's Hunt Sprint as a real four-legged gallop, and his tail.
 *
 * <p><b>The gallop.</b> A player model can't bend at the waist into a quadruped (its parts aren't jointed to each other), so the WHOLE
 * body is pitched forward about the hips ({@link #bodyTilt}, applied in {@code StinkflyFlightPoseMixin}'s {@code setupRotations}) until
 * the back is near level, and every limb is then posed against that pitch ({@link #pose}) so it hangs at a chosen angle in the WORLD:
 * the forelegs (the arms) hang from the underside of the chest, reaching forward and down, and the hind legs swing under the hips. The
 * gait is a rotary gallop -- the two forelegs a beat apart, the hind pair half a stride off them -- driven by the walk animation's own
 * distance, so the stride matches his speed, and the back flexes with it. Each frame the lowest paw is found from the limb angles and
 * the whole body is lifted or dropped onto it, so the paws actually plant on the ground. The head is turned against the pitch so he
 * looks level and ahead (following the look a little), sitting forward of the shoulders.
 *
 * <p><b>The tail</b> ({@link #renderTail}): a bushy wolf's tail from the small of his back -- seven tapering links (fat in the middle, a
 * pale tip), each a box with a second one turned 45 degrees round it for a fluffy, rounded section. Standing it droops in a soft curve;
 * walking it lifts and swings with his stride; in the sprint it streams out behind, raised, bouncing with the gallop. Every viewer;
 * not drawn in first person.
 */
public final class BenwolfSprint {
	private static final class State {
		float on, onO;
	}

	private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();

	/** The hip pivot (model px above the feet) and the player renderer's model scale. */
	private static final float HIP = 12f, MODEL_SCALE = 0.9375f;
	/** How far the forelegs hang from the underside of the chest (model px toward the chest) and the arm's length to the paw. */
	private static final float ARM_DROP = 3.6f, ARM_LEN = 10f, LEG_LEN = 12f;
	/** The back's pitch from upright (degrees) and how far the head sits forward of / below the neck (px). */
	private static final float PITCH = 80f, HEAD_FWD = 1.2f, HEAD_DOWN = 2.6f;

	private BenwolfSprint() {
	}

	/** Every client tick for every transformed Benwolf in view. */
	public static void tick(AbstractClientPlayer p) {
		boolean sprint = (Omnitrix.state(p).kitFlags & BenwolfKit.F_SPRINT) != 0;
		if (!sprint && !STATES.containsKey(p)) {
			return;
		}
		State s = STATES.computeIfAbsent(p, k -> new State());
		s.onO = s.on;
		s.on = Mth.approach(s.on, sprint ? 1f : 0f, sprint ? 0.25f : 0.2f);
		if (!sprint && s.on <= 0f && s.onO <= 0f) {
			STATES.remove(p);
		}
	}

	private static float smooth(float t) {
		return t * t * (3f - 2f * t);
	}

	/** How far into the sprint posture {@code p} is, 0..1. */
	public static float amount(AbstractClientPlayer p, float partial) {
		State s = STATES.get(p);
		return s == null ? 0f : smooth(Mth.lerp(partial, s.onO, s.on));
	}

	/** The gait this frame: {pitch (deg), fore R, fore L, hind R, hind L (world angles, radians, + = paw behind the joint), stride 0..1}. */
	private static float[] gait(AbstractClientPlayer p, float partial) {
		float speed = p.walkAnimation.speed(partial);
		float g = p.walkAnimation.position(partial) * 0.95f;
		float amp = Mth.clamp(speed * 1.6f, 0.15f, 1f);
		float pitch = PITCH + 3.5f * Mth.sin(2f * g) * amp; // the back flexes twice a stride
		float fr = -0.14f + 0.6f * amp * Mth.sin(g);
		float fl = -0.14f + 0.6f * amp * Mth.sin(g - 0.75f);
		float hr = 0.24f + 0.55f * amp * Mth.sin(g + Mth.PI);
		float hl = 0.24f + 0.55f * amp * Mth.sin(g + Mth.PI - 0.75f);
		return new float[] { pitch, fr, fl, hr, hl, amp };
	}

	/**
	 * The whole-body attitude for {@code StinkflyFlightPoseMixin}: {pitch forward (deg), lift (model px), hip pivot (model px)}, or null when
	 * he isn't in (or easing out of) the sprint.
	 */
	public static float[] bodyTilt(AbstractClientPlayer p, float partial) {
		if (!isBenwolf(p)) {
			return null;
		}
		float k = amount(p, partial);
		if (k < 0.01f) {
			return null;
		}
		float[] gt = gait(p, partial);
		float th = gt[0] * k * Mth.DEG_TO_RAD;
		// the shoulders after the pitch (the arm pivot hangs ARM_DROP toward the chest, 10 px up the body from the hips)
		float shU = -ARM_DROP * Mth.sin(th) + 10f * Mth.cos(th) + HIP;
		float low = Float.MAX_VALUE;
		for (int i = 1; i <= 2; i++) {
			low = Math.min(low, shU - ARM_LEN * Mth.cos(gt[i] * k));
		}
		for (int i = 3; i <= 4; i++) {
			low = Math.min(low, HIP - LEG_LEN * Mth.cos(gt[i] * k));
		}
		return new float[] { gt[0] * k, -low, HIP };
	}

	/** Model px to blocks in {@code setupRotations}' space. */
	public static float px(float scale) {
		return MODEL_SCALE / 16f * scale;
	}

	private static boolean isBenwolf(AbstractClientPlayer p) {
		return Omnitrix.transformed(p) && AlienClients.shownKit(p) instanceof BenwolfClient;
	}

	/** The limbs and head against the pitch (after vanilla's setupAnim). True if the sprint posture is on at all. */
	public static boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, float partial) {
		float k = amount(p, partial);
		if (k <= 0f) {
			return false;
		}
		float[] gt = gait(p, partial);
		float th = gt[0] * k * Mth.DEG_TO_RAD;
		// the body is upright in its own frame (the pitch is the whole model's); no crouch offsets
		m.body.xRot = Mth.lerp(k, m.body.xRot, 0f);
		m.body.yRot = Mth.lerp(k, m.body.yRot, 0f);
		m.body.y = Mth.lerp(k, m.body.y, 0f);
		m.body.z = Mth.lerp(k, m.body.z, 0f);
		// forelegs: hung from the underside of the chest, at their world angle
		limb(m.rightArm, k, gt[1] * k - th, 2f, -ARM_DROP, 0.06f);
		limb(m.leftArm, k, gt[2] * k - th, 2f, -ARM_DROP, -0.06f);
		// hind legs from the hips
		limb(m.rightLeg, k, gt[3] * k - th, 12f, 0.4f, 0.03f);
		limb(m.leftLeg, k, gt[4] * k - th, 12f, 0.4f, -0.03f);
		// the head: level and ahead, following the look a little (its turn is a yaw about the WORLD's up, so it is composed against the
		// pitch and read back as the model part's z-y-x angles)
		float yaw = m.head.yRot, look = Mth.clamp(m.head.xRot, -0.9f, 0.9f);
		Quaternionf q = new Quaternionf().rotationX(-th).rotateY(yaw * 0.8f).rotateX(look * 0.45f - 0.05f * k);
		Vector3f e = new Vector3f();
		q.getEulerAnglesZYX(e);
		m.head.xRot = Mth.lerp(k, m.head.xRot, e.x);
		m.head.yRot = Mth.lerp(k, m.head.yRot, e.y);
		m.head.zRot = Mth.lerp(k, m.head.zRot, e.z);
		m.head.y = Mth.lerp(k, m.head.y, -HEAD_FWD);
		m.head.z = Mth.lerp(k, m.head.z, -HEAD_DOWN);
		m.hat.copyFrom(m.head);
		if (m instanceof PlayerModel<?> pm) {
			pm.jacket.copyFrom(m.body);
			pm.rightSleeve.copyFrom(m.rightArm);
			pm.leftSleeve.copyFrom(m.leftArm);
			pm.rightPants.copyFrom(m.rightLeg);
			pm.leftPants.copyFrom(m.leftLeg);
		}
		return true;
	}

	private static void limb(net.minecraft.client.model.geom.ModelPart part, float k, float xRot, float y, float z, float zRot) {
		part.xRot = Mth.lerp(k, part.xRot, xRot);
		part.yRot = Mth.lerp(k, part.yRot, 0f);
		part.zRot = Mth.lerp(k, part.zRot, zRot);
		part.y = Mth.lerp(k, part.y, y);
		part.z = Mth.lerp(k, part.z, z);
	}

	// ---------------------------------------------------------------- the tail

	/** The links' full widths (px), fat in the middle; each link's length. */
	private static final float[] WIDTH = { 2.2f, 3.3f, 4.1f, 4.5f, 4.3f, 3.6f, 2.4f };
	private static final float LINK = 1.9f;

	/** The bushy tail on the body bone (third person). */
	public static void renderTail(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age, float[] fur, float[] furDark, float[] tip) {
		float k = amount(p, partial);
		float speed = Mth.clamp(p.walkAnimation.speed(partial), 0f, 1f);
		float walk = p.walkAnimation.position(partial);
		float g = walk * 0.95f;
		// the root's angle from straight down toward his back (body frame), and how much each link curls on up
		float lift = Math.max(speed * (1f - k), 0f);
		float base = Mth.lerp(k, 30f + 14f * lift, 40f);
		float curl = Mth.lerp(k, 6f - 2f * lift, -1f);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(BenwolfClient.SKIN));
		AlienParts.on(pose, model.body, () -> {
			pose.pushPose();
			pose.translate(0f, 10.6f / 16f, 1.7f / 16f); // the small of his back, just inside it
			pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(base));
			for (int i = 0; i < WIDTH.length; i++) {
				// the swing: a slow idle sway, the stride's wag, and in the sprint a bounce with the gallop
				float sway = Mth.sin(age * 0.11f - i * 0.45f) * (4f + 2f * i) * (1f - k * 0.6f) + Mth.sin(g * 0.5f - i * 0.5f) * 9f * speed * (1f - k);
				float bounce = Mth.sin(g * 2f - i * 0.6f) * 6f * k * Mth.clamp(speed * 1.6f, 0.2f, 1f);
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sway * (i == 0 ? 0.5f : 0.35f)));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees((i == 0 ? 0f : curl) + bounce * 0.4f));
				float w = WIDTH[i] * 0.5f, d = w * 0.9f;
				float[] uv = i == WIDTH.length - 1 ? tip : i == 0 ? furDark : fur;
				float y0 = -0.15f, y1 = LINK + 0.2f;
				AlienParts.box(pose, vc, -w, y0, -d, w, y1, d, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light);
				// the fluff: the same box turned 45 degrees round the link, a little slimmer and offset along it
				pose.pushPose();
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(45f));
				float w2 = w * 0.88f;
				float[] uv2 = i >= WIDTH.length - 2 ? tip : fur;
				AlienParts.box(pose, vc, -w2, y0 + 0.35f, -w2, w2, y1 - 0.1f, w2, uv2[0], uv2[1], uv2[2], uv2[3], 0xFFFFFFFF, light);
				pose.popPose();
				pose.translate(0f, LINK / 16f, 0f);
			}
			pose.popPose();
		});
	}
}
