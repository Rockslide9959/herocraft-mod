package com.projecthero.mod.client.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.spider.SpiderAnim;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.data.SpiderManState;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.1: Spider-Man's poses -- every move's keyframed animation (the thwip, the punch combo, the uppercut, the zip
 * kick, the whip, the throws, the weave, the landing ...) and the held stances (Weave Mode, the counter guard, a
 * charging Impact Web, the swing-throw whirl, hanging under a tightrope, the swing itself). Same technique as
 * {@code MoonKnightPose}: angles applied to the vanilla model after vanilla's animation, driven by the synced
 * {@link SpiderMoveState}, so every viewer (and the GeckoLib suit, which copies the vanilla bones) agrees. Whole-body
 * flips and spins are {@link #bodyRotation}, applied by {@code PlayerRendererMixin}.
 *
 * <p>Frames are {@code {tick, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX}} in radians
 * (arm X negative = forward / up; arm Z positive = the right arm out to the side). "Aimed" arms add the head's pitch /
 * yaw so a web leaves toward the crosshair.
 */
public final class SpiderPose {
	private record Pose(float[][] frames, boolean aimRight, boolean aimLeft) {
	}

	private static float[] f(float tick, float... v) {
		float[] out = new float[12];
		out[0] = tick;
		System.arraycopy(v, 0, out, 1, Math.min(11, v.length));
		return out;
	}

	private static float[] rest(float tick) {
		return f(tick);
	}

	// the thwip: wrist snapped forward along the aim, the other hand back
	private static final Pose THWIP_RIGHT = new Pose(new float[][] {
			rest(0),
			f(1.5f, -1.65f, -0.1f, 0, 0.35f, 0, -0.1f, 0, -0.25f, 0, 0, 0),
			f(5, -1.55f, -0.08f, 0, 0.3f, 0, -0.1f, 0, -0.2f, 0, 0, 0),
			rest(8)}, true, false);
	private static final Pose THWIP_LEFT = new Pose(new float[][] {
			rest(0),
			f(1.5f, 0.35f, 0, 0.1f, -1.65f, 0.1f, 0, 0, 0.25f, 0, 0, 0),
			f(5, 0.3f, 0, 0.1f, -1.55f, 0.08f, 0, 0, 0.2f, 0, 0, 0),
			rest(8)}, false, true);
	private static final Pose THWIP_BOTH = new Pose(new float[][] {
			rest(0),
			f(2, -1.6f, 0.12f, 0, -1.6f, -0.12f, 0, 0.08f, 0, 0.15f, -0.2f, 0),
			f(6, -1.5f, 0.1f, 0, -1.5f, -0.1f, 0, 0.06f, 0, 0.15f, -0.2f, 0),
			rest(10)}, true, true);
	private static final Pose IMPACT_RELEASE = new Pose(new float[][] {
			f(0, -1.55f, 0, 0, -1.3f, 0.55f, 0, 0, -0.2f, 0, 0, 0),
			f(2, -2.05f, 0, 0, -0.6f, 0.3f, 0, -0.12f, -0.15f, 0.2f, -0.25f, 0),
			f(6, -1.6f, 0, 0, -0.2f, 0, 0, -0.05f, -0.1f, 0.1f, -0.1f, 0),
			rest(10)}, true, false);
	private static final Pose ZIP = new Pose(new float[][] {
			rest(0),
			f(2, -2.7f, -0.1f, 0, 0.5f, 0, -0.3f, 0.15f, -0.2f, 0.45f, 0.15f, -0.1f),
			f(8, -2.6f, -0.1f, 0, 0.5f, 0, -0.3f, 0.15f, -0.2f, 0.4f, 0.2f, -0.1f),
			rest(12)}, true, false);
	private static final Pose YANK = new Pose(new float[][] {
			f(0, -1.6f, 0.1f, 0, -1.6f, -0.1f, 0, 0, 0, 0, 0, 0),
			f(2, -1.7f, 0.1f, 0, -1.7f, -0.1f, 0, 0, 0, 0, 0, 0),
			f(6, -0.25f, -0.3f, 0.2f, -0.6f, 0.2f, 0, -0.25f, 0.45f, 0.35f, -0.4f, 0),
			rest(12)}, true, true);
	private static final Pose ZIP_KICK = new Pose(new float[][] {
			rest(0),
			f(2, -2.7f, 0, 0, 0.4f, 0, -0.4f, 0, 0, 0.5f, 0.6f, 0),
			f(7, -2.4f, 0, 0, 0.4f, 0, -0.4f, 0, 0, -0.9f, 1.1f, 0),
			f(9, 0.6f, 0, 0.5f, 0.6f, 0, -0.5f, 0, 0, -1.65f, 0.9f, 0.25f),
			f(26, 0.6f, 0, 0.5f, 0.6f, 0, -0.5f, 0, 0, -1.6f, 0.9f, 0.25f),
			rest(30)}, false, false);
	private static final Pose STRIKE_COMBO = new Pose(new float[][] {
			f(0, -0.6f, 0, 0, -0.6f, 0, 0, 0.1f, 0, 0.2f, -0.2f, 0),
			f(2, -1.65f, 0.15f, 0, -0.5f, 0, 0, 0.12f, -0.35f, 0.25f, -0.25f, 0),
			f(6, -0.5f, 0, 0, -0.6f, 0, 0, 0.1f, 0, 0.2f, -0.2f, 0),
			f(9, -0.4f, 0, 0, -1.65f, -0.15f, 0, 0.12f, 0.35f, -0.25f, 0.25f, 0),
			f(13, -0.6f, 0.2f, 0.3f, -0.5f, 0, 0, 0.05f, -0.4f, 0.2f, -0.2f, 0),
			f(16, -1.75f, -0.2f, 0, 0.3f, 0, -0.2f, 0.25f, -0.6f, 0.4f, -0.5f, 0),
			f(19, -1.6f, -0.2f, 0, 0.3f, 0, -0.2f, 0.2f, -0.5f, 0.35f, -0.4f, 0),
			rest(22)}, true, true);
	private static final Pose UPPERCUT = new Pose(new float[][] {
			f(0, 0.35f, 0, 0.15f, -0.9f, 0.4f, 0, 0.35f, -0.3f, 0.4f, -0.6f, 0.2f),
			f(3, -2.5f, 0.4f, -0.25f, 0.45f, 0, -0.3f, -0.22f, 0.35f, 0.15f, -0.15f, -0.45f),
			f(8, -2.45f, 0.35f, -0.2f, 0.4f, 0, -0.3f, -0.15f, 0.3f, 0.1f, -0.1f, -0.4f),
			rest(12)}, false, false);
	private static final Pose COUNTER_FLIP = new Pose(new float[][] {
			rest(0),
			f(2, -1.2f, 0, 0.3f, -1.2f, 0, -0.3f, 0, 0, -1.3f, -1.3f, -0.2f),
			f(9, -1.2f, 0, 0.3f, -1.2f, 0, -0.3f, 0, 0, -1.2f, -1.2f, -0.2f),
			f(11, 0.4f, 0, 0.4f, -1.5f, 0, -0.2f, 0, 0, -1.6f, 0.4f, 0),
			rest(14)}, false, false);
	private static final Pose WHIP = new Pose(new float[][] {
			rest(0),
			f(2, -1.45f, 0, 1.45f, 0.4f, 0, -0.5f, 0.1f, 0, 0.2f, -0.2f, 0),
			f(9, -1.45f, 0, 1.45f, 0.4f, 0, -0.5f, 0.1f, 0, 0.2f, -0.2f, 0),
			rest(12)}, false, false);
	private static final Pose HURL = new Pose(new float[][] {
			f(0, -1.6f, 0.1f, 0, -1.6f, -0.1f, 0, 0, 0, 0, 0, 0),
			f(5, -0.7f, -0.45f, 0, -0.7f, 0.45f, 0, -0.15f, 0, 0.2f, -0.3f, 0),
			f(9, -2.9f, -0.1f, 0, -2.9f, 0.1f, 0, -0.25f, 0, 0.3f, -0.4f, -0.2f),
			f(12, -1.1f, 0, 0, -1.1f, 0, 0, 0.35f, 0, -0.4f, 0.3f, 0.2f),
			rest(16)}, false, false);
	private static final Pose SWING_THROW_RELEASE = new Pose(new float[][] {
			f(0, -2.9f, 0, 0.3f, 0.3f, 0, -0.3f, 0, 0.3f, 0, 0, 0),
			f(3, -1.0f, -0.3f, 0, 0.4f, 0, -0.3f, 0.25f, -0.45f, -0.3f, 0.3f, 0),
			rest(10)}, false, false);
	private static final Pose TIGHTROPE_SHOT = new Pose(new float[][] {
			rest(0),
			f(2, -1.6f, 0, 0, -0.5f, 0.4f, 0, 0, -0.2f, 0, 0, 0),
			f(7, -1.55f, 0, 0, -0.5f, 0.4f, 0, 0, -0.2f, 0, 0, 0),
			rest(10)}, true, false);
	private static final Pose WALL_CAST = new Pose(new float[][] {
			rest(0),
			f(3, -1.55f, 0.35f, 0, -1.55f, -0.35f, 0, 0.05f, 0, 0.25f, -0.3f, 0),
			f(8, -1.5f, 0.3f, 0, -1.5f, -0.3f, 0, 0.05f, 0, 0.25f, -0.3f, 0),
			rest(12)}, false, false);
	private static final Pose WEAVE_SHOT = new Pose(new float[][] {
			rest(0),
			f(1.5f, -1.65f, 0, 0, -2.2f, 0.3f, 0, 0, -0.15f, 0, 0, 0),
			f(5, -1.55f, 0, 0, -2.1f, 0.3f, 0, 0, -0.15f, 0, 0, 0),
			rest(8)}, true, false);
	private static final Pose WEAVE_FINISH = new Pose(new float[][] {
			f(0, -1.6f, 0, 0, -1.6f, 0, 0, 0, 0, 0, 0, 0),
			f(6, -0.3f, 0, 1.4f, -0.3f, 0, -1.4f, -0.1f, 0, 0, 0, -0.2f),
			f(12, -0.3f, 0, 1.35f, -0.3f, 0, -1.35f, -0.1f, 0, 0, 0, -0.2f),
			rest(16)}, false, false);
	private static final Pose PULSE = new Pose(new float[][] {
			rest(0),
			f(3, 0.2f, 0, 0.3f, -2.4f, -0.9f, 0.2f, -0.05f, 0, 0, 0, -0.3f),
			f(10, 0.2f, 0, 0.3f, -2.3f, -0.9f, 0.2f, -0.05f, 0, 0, 0, -0.25f),
			rest(14)}, false, false);
	private static final Pose COCOON = new Pose(new float[][] {
			f(0, -1.55f, 0, 0, -1.55f, 0, 0, 0, 0, 0, 0, 0),
			f(4, -1.6f, 0.45f, 0, -1.5f, -0.45f, 0, 0.05f, 0.2f, 0, 0, 0),
			f(8, -1.5f, -0.45f, 0, -1.6f, 0.45f, 0, 0.05f, -0.2f, 0, 0, 0),
			f(12, -1.6f, 0.35f, 0, -1.5f, -0.35f, 0, 0.05f, 0.15f, 0, 0, 0),
			rest(16)}, true, true);
	private static final Pose BLOSSOM = new Pose(new float[][] {
			f(0, -0.9f, 0.8f, 0, -0.9f, -0.8f, 0, 0.35f, 0, 0, 0, 0.2f),
			f(3, -0.6f, 0, 2.3f, -0.6f, 0, -2.3f, -0.15f, 0, -0.2f, 0.2f, -0.4f),
			f(12, -0.6f, 0, 2.2f, -0.6f, 0, -2.2f, -0.15f, 0, -0.2f, 0.2f, -0.4f),
			rest(16)}, false, false);
	private static final Pose RELEASE_FLIP = new Pose(new float[][] {
			rest(0),
			f(2, -1.0f, 0, 0.3f, -1.0f, 0, -0.3f, 0.3f, 0, -1.4f, -1.4f, 0.3f),
			f(9, -1.0f, 0, 0.3f, -1.0f, 0, -0.3f, 0.3f, 0, -1.4f, -1.4f, 0.3f),
			rest(12)}, false, false);

	private SpiderPose() {
	}

	private static Pose poseFor(int id) {
		return switch (id) {
			case SpiderAnim.THWIP_RIGHT -> THWIP_RIGHT;
			case SpiderAnim.THWIP_LEFT -> THWIP_LEFT;
			case SpiderAnim.THWIP_BOTH -> THWIP_BOTH;
			case SpiderAnim.IMPACT_RELEASE -> IMPACT_RELEASE;
			case SpiderAnim.ZIP -> ZIP;
			case SpiderAnim.YANK -> YANK;
			case SpiderAnim.ZIP_KICK -> ZIP_KICK;
			case SpiderAnim.STRIKE_COMBO -> STRIKE_COMBO;
			case SpiderAnim.UPPERCUT -> UPPERCUT;
			case SpiderAnim.COUNTER_FLIP -> COUNTER_FLIP;
			case SpiderAnim.WHIP -> WHIP;
			case SpiderAnim.HURL -> HURL;
			case SpiderAnim.SWING_THROW_RELEASE -> SWING_THROW_RELEASE;
			case SpiderAnim.TIGHTROPE_SHOT -> TIGHTROPE_SHOT;
			case SpiderAnim.WALL_CAST -> WALL_CAST;
			case SpiderAnim.WEAVE_SHOT -> WEAVE_SHOT;
			case SpiderAnim.WEAVE_FINISH -> WEAVE_FINISH;
			case SpiderAnim.PULSE -> PULSE;
			case SpiderAnim.COCOON -> COCOON;
			case SpiderAnim.BLOSSOM -> BLOSSOM;
			case SpiderAnim.RELEASE_FLIP -> RELEASE_FLIP;
			default -> null;
		};
	}

	private static float now(Player player) {
		return player.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	/** Apply every pose for {@code player} (from {@code HumanoidModelMixin}). */
	public static void apply(Player player, HumanoidModel<?> m) {
		if (!SpiderMan.hasPower(player) || com.projecthero.mod.client.moonknight.MoonKnightPose.firstPersonHand) {
			return;
		}
		SpiderMoveState s = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		float t = now(player);
		if (SpiderSwing.isSwinging(player)) {
			swingBody(player, m, t, s != null && s.has(SpiderMoveState.FLAG_HANGING));
		}
		if (s == null) {
			return;
		}
		float perch = perchAmount(player);
		if (perch > 0f) {
			perch(m, perch);
		}
		// held stances first; a move's animation then blends over them
		if (s.has(SpiderMoveState.FLAG_WEAVING)) {
			m.rightArm.xRot = -1.57f + m.head.xRot;
			m.rightArm.yRot = m.head.yRot;
			m.leftArm.xRot = -2.1f;
			m.leftArm.yRot = 0.35f;
			m.leftArm.zRot = -0.1f;
		} else if (s.has(SpiderMoveState.FLAG_COUNTER)) {
			float k = Math.min(1f, (t - s.chargeStart) / 3f);
			m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -1.35f);
			m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, -0.55f);
			m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -1.35f);
			m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0.55f);
			HipBend.pitch(m, k, 0.4f);
			m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, -0.45f);
			m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, 0.3f);
		} else if (s.has(SpiderMoveState.FLAG_CHARGING_IMPACT)) {
			float shake = Mth.sin(t * 2.7f) * 0.03f * Math.min(1f, (t - s.chargeStart) / 20f);
			m.rightArm.xRot = -1.57f + m.head.xRot + shake;
			m.rightArm.yRot = m.head.yRot - 0.05f;
			m.leftArm.xRot = -1.35f + m.head.xRot * 0.6f;
			m.leftArm.yRot = 0.65f;
		} else if (s.has(SpiderMoveState.FLAG_SWING_THROW)) {
			float a = t * 0.55f;
			m.rightArm.xRot = -2.85f;
			m.rightArm.zRot = 0.25f + Mth.sin(a) * 0.3f;
			m.rightArm.yRot = Mth.cos(a) * 0.4f;
			m.leftArm.xRot = -0.4f;
			m.leftArm.zRot = -0.5f;
			m.body.yRot = Mth.sin(a) * 0.25f;
		} else if (s.has(SpiderMoveState.FLAG_CHARGING_BLOSSOM)) {
			float k = Math.min(1f, (t - s.chargeStart) / 6f);
			m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -0.95f);
			m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, -0.85f);
			m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -0.95f);
			m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0.85f);
			HipBend.pitch(m, k, 0.45f);
			m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, -0.5f);
			m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, 0.35f);
		}
		if (s.animId == SpiderAnim.LANDING) {
			landing(m, t - s.animStart);
			return;
		}
		Pose p = poseFor(s.animId);
		if (p != null) {
			blend(m, p.frames(), t - s.animStart, p.aimRight(), p.aimLeft());
		}
	}

	/** The swing: the free arm out for balance, knees drawn up one ahead of the other; hanging: both hands on the line. */
	private static void swingBody(Player player, HumanoidModel<?> m, float t, boolean hanging) {
		SpiderManState st = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		if (st == null) {
			return;
		}
		if (hanging) {
			m.rightArm.xRot = -2.95f;
			m.rightArm.zRot = -0.12f;
			m.leftArm.xRot = -2.95f;
			m.leftArm.zRot = 0.12f;
			m.rightLeg.xRot = Mth.sin(t * 0.2f) * 0.15f;
			m.leftLeg.xRot = -Mth.sin(t * 0.2f) * 0.15f;
			return;
		}
		boolean right = st.swingHandRight;
		if (right) {
			m.leftArm.xRot = -0.35f;
			m.leftArm.zRot = -1.0f;
		} else {
			m.rightArm.xRot = -0.35f;
			m.rightArm.zRot = 1.0f;
		}
		m.rightLeg.xRot = right ? -0.75f : 0.35f;
		m.leftLeg.xRot = right ? 0.35f : -0.75f;
		m.rightLeg.zRot = 0.08f;
		m.leftLeg.zRot = -0.08f;
	}

	/** The superhero landing: one knee down, the left fist on the ground, the right arm thrown out behind. */
	private static void landing(HumanoidModel<?> m, float t) {
		final float in = 2f;
		final float holdEnd = 11f;
		final float end = SpiderAnim.length(SpiderAnim.LANDING);
		if (t < 0f || t >= end) {
			return;
		}
		float k = t < in ? smooth(t / in) : t < holdEnd ? 1f : 1f - smooth((t - holdEnd) / (end - holdEnd));
		if (k <= 0f) {
			return;
		}
		float drop = 6.5f * k;
		float lean = 0.85f * k;
		float hipY = 12.0f + drop;
		float neckY = hipY - 12.0f * Mth.cos(lean);
		float neckZ = -12.0f * Mth.sin(lean);
		m.body.xRot = lean;
		m.body.y = neckY;
		m.body.z = neckZ;
		m.head.y = neckY;
		m.head.z = neckZ;
		m.head.xRot = Mth.lerp(k, m.head.xRot, t < 8f ? 0.2f : -0.45f);
		m.hat.copyFrom(m.head);
		float shoulderY = neckY + 2.0f * Mth.cos(lean);
		float shoulderZ = neckZ + 2.0f * Mth.sin(lean);
		m.rightArm.y = Mth.lerp(k, m.rightArm.y, shoulderY);
		m.leftArm.y = Mth.lerp(k, m.leftArm.y, shoulderY);
		m.rightArm.z = Mth.lerp(k, m.rightArm.z, shoulderZ);
		m.leftArm.z = Mth.lerp(k, m.leftArm.z, shoulderZ);
		// left fist planted, right arm flung out behind and up
		m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -0.1f);
		m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0.0f);
		m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -0.12f);
		m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, 0.9f);
		m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, 0.0f);
		m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 1.15f);
		m.rightLeg.y = hipY;
		m.leftLeg.y = hipY;
		m.rightLeg.z = Mth.lerp(k, m.rightLeg.z, -0.8f);
		m.leftLeg.z = Mth.lerp(k, m.leftLeg.z, 0.8f);
		m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, -0.95f);
		m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, 1.05f);
		m.rightLeg.zRot = Mth.lerp(k, m.rightLeg.zRot, 0.18f);
		m.leftLeg.zRot = Mth.lerp(k, m.leftLeg.zRot, -0.06f);
	}

	/** Per player: the eased perch amount (0..1) and the last time it was advanced (ns). */
	private static final java.util.Map<java.util.UUID, double[]> PERCH_EASE = new java.util.concurrent.ConcurrentHashMap<>();

	/** The ledge perch, eased in / out over about five ticks (0 = standing). */
	public static float perchAmount(Player player) {
		SpiderMoveState s = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		boolean on = s != null && s.has(SpiderMoveState.FLAG_PERCH) && SpiderMan.hasPower(player);
		double[] e = PERCH_EASE.get(player.getUUID());
		if (e == null) {
			if (!on) {
				return 0f;
			}
			e = new double[] {0.0, System.nanoTime()};
			PERCH_EASE.put(player.getUUID(), e);
		}
		long now = System.nanoTime();
		double dt = Math.min(0.2, (now - e[1]) / 1.0e9);
		e[1] = now;
		// a quarter second (5 ticks) from one to the other
		e[0] = Mth.clamp(e[0] + (on ? dt : -dt) * 4.0, 0.0, 1.0);
		if (!on && e[0] <= 0.0) {
			PERCH_EASE.remove(player.getUUID());
			return 0f;
		}
		return smooth((float) e[0]);
	}

	/**
	 * The perch (the user's sketch, "like the Legends mod"): seen from the front -- head, an upright body sunk low so its
	 * bottom is near the block, the two ARMS as the inner pieces running diagonally down and inward across the lower
	 * body to meet at the perch point between the feet, and the two LEGS as the outer pieces fanned far out to the
	 * sides, feet down-and-outward and reaching a little forward. The parts are deliberately moved off their joints
	 * (x / y / z as well as rotation) to make the shape; the player's outer skin layers and the suit copy the parts.
	 * Every position is blended from its vanilla value, so leaving the perch puts everything back.
	 */
	private static void perch(HumanoidModel<?> m, float k) {
		final float ground = 24.0f;
		// the body (12 px) sits low -- its bottom (the hips) 2 px above the block -- and leans ~20 degrees forward from
		// the hips (the neck moves, the hip point stays), the head following the neck
		final float hipY = 22.0f;
		final float lean = 0.35f;
		float neckY = hipY - 12.0f * Mth.cos(lean);
		float neckZ = -12.0f * Mth.sin(lean);
		m.body.xRot = Mth.lerp(k, m.body.xRot, lean);
		m.body.yRot = Mth.lerp(k, m.body.yRot, 0.0f);
		m.body.y = Mth.lerp(k, m.body.y, neckY);
		m.body.z = Mth.lerp(k, m.body.z, neckZ);
		m.head.y = Mth.lerp(k, m.head.y, neckY);
		m.head.z = Mth.lerp(k, m.head.z, neckZ - 0.3f);
		m.head.xRot = Mth.lerp(k, m.head.xRot, Mth.clamp(m.head.xRot, -0.1f, 0.45f));
		m.hat.copyFrom(m.head);
		// arms: inner pieces, from just below the shoulders diagonally down-and-in, hands meeting in front of the feet
		// shoulders 3 px down the leaning torso; the arm angles put both hands at the block, 0.8 px either side of the
		// centre line, in front of the feet
		float shoulderY = neckY + 3.0f * Mth.cos(lean);
		float shoulderZ = neckZ + 3.0f * Mth.sin(lean);
		float dx = 4.2f;
		float dy = ground - shoulderY;
		float dz = (float) Math.sqrt(Math.max(0.0f, 144.0f - dx * dx - dy * dy));
		float armFwd = (float) Math.asin(Mth.clamp(dz / 12.0f, 0.0f, 1.0f));
		float armIn = (float) Math.atan2(dx, dy);
		m.rightArm.x = Mth.lerp(k, -5.0f, -5.0f);
		m.leftArm.x = Mth.lerp(k, 5.0f, 5.0f);
		m.rightArm.y = Mth.lerp(k, m.rightArm.y, shoulderY);
		m.leftArm.y = Mth.lerp(k, m.leftArm.y, shoulderY);
		m.rightArm.z = Mth.lerp(k, m.rightArm.z, shoulderZ);
		m.leftArm.z = Mth.lerp(k, m.leftArm.z, shoulderZ);
		m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -armFwd);
		m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -armFwd);
		m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, 0.0f);
		m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0.0f);
		m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, -armIn);
		m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, armIn);
		// legs: outer pieces, moved out beside the lower body, fanned far out with the feet down-and-outward
		final float legOut = 0.9f;
		final float legFwd = 0.35f;
		float legY = ground - 12.0f * Mth.cos(legFwd) * Mth.cos(legOut);
		m.rightLeg.x = Mth.lerp(k, -1.9f, -4.0f);
		m.leftLeg.x = Mth.lerp(k, 1.9f, 4.0f);
		m.rightLeg.y = Mth.lerp(k, m.rightLeg.y, legY);
		m.leftLeg.y = Mth.lerp(k, m.leftLeg.y, legY);
		m.rightLeg.z = Mth.lerp(k, m.rightLeg.z, 0.5f);
		m.leftLeg.z = Mth.lerp(k, m.leftLeg.z, 0.5f);
		m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, -legFwd);
		m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, -legFwd);
		m.rightLeg.yRot = Mth.lerp(k, m.rightLeg.yRot, 0.0f);
		m.leftLeg.yRot = Mth.lerp(k, m.leftLeg.yRot, 0.0f);
		m.rightLeg.zRot = Mth.lerp(k, m.rightLeg.zRot, legOut);
		m.leftLeg.zRot = Mth.lerp(k, m.leftLeg.zRot, -legOut);
	}

	private static float smooth(float f) {
		f = Mth.clamp(f, 0f, 1f);
		return f * f * (3f - 2f * f);
	}

	/**
	 * A whole-body rotation for flips and spins: {@code {axis (0 = X pitch, 1 = Y yaw), degrees, pivot height}}, or
	 * null. Read by {@code PlayerRendererMixin}.
	 */
	public static float[] bodyRotation(Player player, float partialTicks) {
		if (!SpiderMan.hasPower(player)) {
			return null;
		}
		SpiderMoveState s = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		if (s == null) {
			return null;
		}
		float t = player.level().getGameTime() - s.animStart + partialTicks;
		return switch (s.animId) {
			case SpiderAnim.RELEASE_FLIP -> t >= 0 && t < 11 ? new float[] {0, 360f * smooth(t / 11f), 0.9f} : null;
			case SpiderAnim.COUNTER_FLIP -> t >= 0 && t < 10 ? new float[] {0, -360f * smooth(t / 10f), 0.9f} : null;
			case SpiderAnim.WHIP -> t >= 0 && t < 11 ? new float[] {1, -360f * smooth(t / 11f), 0f} : null;
			case SpiderAnim.ZIP_KICK -> t >= 8 && t < 28 ? new float[] {0, -55f * Math.min(1f, (t - 8f) / 2f)
					* Math.min(1f, (28f - t) / 3f), 0.9f} : null;
			case SpiderAnim.HURL -> t >= 9 && t < 15 ? new float[] {0, 18f * Mth.sin((t - 9f) / 6f * Mth.PI), 0f} : null;
			default -> null;
		};
	}

	private static float[] sample(float[][] f, float tick) {
		if (tick < 0f || tick > f[f.length - 1][0]) {
			return null;
		}
		for (int i = 1; i < f.length; i++) {
			if (tick <= f[i][0]) {
				float[] a = f[i - 1];
				float[] b = f[i];
				float t = (tick - a[0]) / Math.max(1e-4f, b[0] - a[0]);
				t = t * t * (3f - 2f * t);
				float[] out = new float[12];
				for (int k = 1; k < 12; k++) {
					out[k] = Mth.lerp(t, a[k], b[k]);
				}
				return out;
			}
		}
		return null;
	}

	private static void blend(HumanoidModel<?> m, float[][] f, float tick, boolean aimRight, boolean aimLeft) {
		float[] p = sample(f, tick);
		if (p == null) {
			return;
		}
		float end = f[f.length - 1][0];
		float w = Math.max(0f, Math.min(1f, Math.min(tick / 1.5f, (end - tick) / 3f)));
		if (f[0][1] != 0f || f[0][4] != 0f) {
			w = Math.max(0f, Math.min(1f, (end - tick) / 3f)); // a pose that starts posed (no ease in)
		}
		float headX = m.head.xRot;
		float headY = m.head.yRot;
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, p[1] + (aimRight ? headX : 0f));
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, p[2] + (aimRight ? headY : 0f));
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, p[3]);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, p[4] + (aimLeft ? headX : 0f));
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, p[5] + (aimLeft ? headY : 0f));
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, p[6]);
		HipBend.pitch(m, w, p[7]);
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8]);
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, m.rightLeg.xRot * 0.3f + p[9]);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, m.leftLeg.xRot * 0.3f + p[10]);
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
		m.hat.copyFrom(m.head);
	}
}
