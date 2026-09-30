package com.projecthero.mod.client.moonknight;

import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.data.MoonKnightAction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.4: every Truncheon move's animation, for every viewer (driven by the synced {@link MoonKnightAction} anim id +
 * start, like the rest of {@link MoonKnightPose}):
 * <ul>
 *   <li>third person -- keyframed arms / body / legs on the vanilla model (the GeckoLib suit copies it): the summon
 *       (raised to the moon, swung down to ready), the stow, the combo's forehand, backhand and two-handed overhead
 *       smash, and the staff spin, which also turns the whole body a full circle ({@link #spinDegrees}, applied by
 *       {@code MoonKnightTruncheonSpinMixin} in {@code PlayerRenderer.setupRotations});</li>
 *   <li>first person -- the held truncheon itself sweeps / smashes / twirls about the hand ({@link #firstPerson}, from
 *       {@code ItemInHandRendererTruncheonMixin}), on top of vanilla's own swing.</li>
 * </ul>
 * Body frames are {@code {tick, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX}} in radians
 * (arm X negative = forward / up, arm Y positive = toward the player's right, body Y positive = torso turned right).
 * The server stamps the combo poses when the hit lands, so they start at the strike rather than a slow wind-up.
 */
public final class MoonKnightTruncheonPose {
	private record Pose(float[][] frames, float fadeIn) {
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

	/** C press: the truncheon appears raised high, then is swung down to the ready. */
	private static final Pose DRAW = new Pose(new float[][] {
			rest(0),
			f(3, -2.6f, -0.2f, 0.25f, 0, 0, 0, -0.05f, 0, 0, 0, -0.2f),
			f(7, -1.0f, 0.35f, 0.15f, 0, 0, 0, 0.05f, 0.1f, 0, 0, 0),
			rest(12) }, 2f);
	/** C tap with it out: tucked back to the hip as it fades. */
	private static final Pose STOW = new Pose(new float[][] {
			rest(0),
			f(3, 0.35f, 0, 0.25f, 0, 0, 0, 0, -0.1f, 0, 0, 0),
			rest(9) }, 2f);
	/** Combo hit 1, forehand: from out on the right, across the body to the left. */
	private static final Pose HIT_1 = new Pose(new float[][] {
			f(0, -1.5f, 0.9f, 0.3f, -0.3f, 0, -0.1f, 0, 0.5f, 0, 0, 0),
			f(3, -1.45f, -0.85f, 0, 0.25f, 0, -0.1f, 0.1f, -0.5f, 0, 0, 0.05f),
			f(6, -1.25f, -1.0f, 0, 0.2f, 0, -0.1f, 0.05f, -0.45f, 0, 0, 0),
			rest(11) }, 1f);
	/** Combo hit 2, backhand: from across on the left, back out to the right. */
	private static final Pose HIT_2 = new Pose(new float[][] {
			f(0, -1.35f, -1.05f, 0, 0.2f, 0, -0.1f, 0, -0.5f, 0, 0, 0),
			f(3, -1.5f, 1.0f, 0.35f, -0.3f, 0, -0.15f, 0.05f, 0.55f, 0, 0, 0.05f),
			f(6, -1.25f, 1.1f, 0.3f, -0.2f, 0, -0.1f, 0.05f, 0.5f, 0, 0, 0),
			rest(11) }, 1f);
	/** Combo hit 3, the overhead smash: both hands up and back, then down through the target with a lunge. */
	private static final Pose SMASH = new Pose(new float[][] {
			f(0, -3.0f, -0.15f, 0, -2.85f, 0.35f, 0, -0.15f, 0.05f, 0, 0, -0.2f),
			f(2, -3.1f, -0.15f, 0, -2.95f, 0.35f, 0, -0.2f, 0.05f, 0, 0, -0.25f),
			f(5, -0.55f, -0.15f, 0, -0.65f, 0.45f, 0, 0.35f, 0, -0.5f, 0.45f, 0.3f),
			f(9, -0.5f, -0.15f, 0, -0.6f, 0.45f, 0, 0.32f, 0, -0.45f, 0.4f, 0.25f),
			rest(15) }, 1f);
	/** C hold, the staff spin: both hands on the staff held out in front while the whole body turns a circle. */
	private static final Pose SPIN = new Pose(new float[][] {
			rest(0),
			f(1, -0.45f, -0.25f, 0.1f, -0.6f, 0.6f, 0, 0, 0, -0.2f, 0.2f, 0),
			f(MoonKnightConfig.STAFF_SPIN_TURN_TICKS + 2, -0.45f, -0.25f, 0.1f, -0.6f, 0.6f, 0, 0, 0, -0.2f, 0.2f, 0),
			rest(MoonKnightConfig.STAFF_SPIN_TICKS) }, 1f);

	/** First-person frames {@code {tick, x, y, z, xDeg, yDeg, zDeg}} in the hand's space (after vanilla's arm transform). */
	private static final float[][] FP_DRAW = {
			{0, 0, -0.3f, 0, 0, 0, -60},
			{4, 0, 0.05f, 0, 0, 0, 10},
			{8, 0, 0, 0, 0, 0, 0} };
	private static final float[][] FP_HIT_1 = {
			{0, 0, 0, 0, 0, 0, 0},
			{1, 0.18f, 0.05f, 0, 0, 30, -15},
			{3, -0.32f, -0.02f, -0.05f, -10, -45, 30},
			{5, -0.28f, -0.03f, -0.05f, -8, -40, 25},
			{10, 0, 0, 0, 0, 0, 0} };
	private static final float[][] FP_HIT_2 = {
			{0, 0, 0, 0, 0, 0, 0},
			{1, -0.25f, 0.05f, 0, 0, -35, 20},
			{3, 0.2f, -0.02f, -0.05f, -10, 40, -30},
			{5, 0.18f, -0.03f, -0.05f, -8, 35, -25},
			{10, 0, 0, 0, 0, 0, 0} };
	private static final float[][] FP_SMASH = {
			{0, 0, 0, 0, 0, 0, 0},
			{1, -0.1f, 0.3f, 0.05f, -35, 0, 0},
			{2, -0.1f, 0.35f, 0.05f, -40, 0, 0},
			{5, -0.08f, -0.06f, -0.1f, 35, 0, 0},
			{8, -0.08f, -0.05f, -0.08f, 30, 0, 0},
			{14, 0, 0, 0, 0, 0, 0} };
	/** The staff twirls about the hand, brought in toward the middle of the view so it stays on screen. */
	private static final float[][] FP_SPIN = {
			{0, 0, 0, 0, 0, 0, 0},
			{2, -0.4f, 0.25f, -0.1f, 0, 0, 0},
			{MoonKnightConfig.STAFF_SPIN_TURN_TICKS + 2, -0.4f, 0.25f, -0.1f, 0, 0, -720},
			{MoonKnightConfig.STAFF_SPIN_TURN_TICKS + 6, 0, 0, 0, 0, 0, -720} };

	private MoonKnightTruncheonPose() {
	}

	private static Pose poseFor(int id) {
		return switch (id) {
			case MoonKnightAnim.TRUNCHEON_DRAW -> DRAW;
			case MoonKnightAnim.TRUNCHEON_STOW -> STOW;
			case MoonKnightAnim.TRUNCHEON_HIT_1 -> HIT_1;
			case MoonKnightAnim.TRUNCHEON_HIT_2 -> HIT_2;
			case MoonKnightAnim.TRUNCHEON_SLAM -> SMASH;
			case MoonKnightAnim.STAFF_SPIN -> SPIN;
			default -> null;
		};
	}

	private static float[][] firstPersonFor(int id) {
		return switch (id) {
			case MoonKnightAnim.TRUNCHEON_DRAW -> FP_DRAW;
			case MoonKnightAnim.TRUNCHEON_HIT_1 -> FP_HIT_1;
			case MoonKnightAnim.TRUNCHEON_HIT_2 -> FP_HIT_2;
			case MoonKnightAnim.TRUNCHEON_SLAM -> FP_SMASH;
			case MoonKnightAnim.STAFF_SPIN -> FP_SPIN;
			default -> null;
		};
	}

	/** Is {@code animId} one of the truncheon's (drawn here rather than by {@link MoonKnightPose})? */
	public static boolean handles(int animId) {
		return poseFor(animId) != null;
	}

	/** From {@link MoonKnightPose#apply}: blend the playing truncheon move over the model. */
	static void apply(HumanoidModel<?> m, MoonKnightAction a, long now, float partial) {
		Pose p = poseFor(a.animId);
		if (p == null || MoonKnightPose.firstPersonHand) {
			return;
		}
		float tick = now - a.animStart + partial;
		float[][] fr = p.frames();
		float[] v = MoonKnightPose.sample(fr, tick);
		if (v == null) {
			return;
		}
		float end = fr[fr.length - 1][0];
		float w = Mth.clamp(Math.min(p.fadeIn() <= 0f ? 1f : tick / p.fadeIn(), (end - tick) / 3f), 0f, 1f);
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, v[1]);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, v[2]);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, v[3]);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, v[4]);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, v[5]);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, v[6]);
		m.body.xRot = Mth.lerp(w, m.body.xRot, v[7]);
		m.body.yRot = Mth.lerp(w, m.body.yRot, v[8]);
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, m.rightLeg.xRot * 0.3f + v[9]);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, m.leftLeg.xRot * 0.3f + v[10]);
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + v[11]);
		m.hat.copyFrom(m.head);
		// the shoulders turn with the torso (as vanilla's own attack swing does), so a twisted body keeps its arms on
		float bodyY = m.body.yRot;
		m.rightArm.z = Mth.sin(bodyY) * 5.0f;
		m.rightArm.x = -Mth.cos(bodyY) * 5.0f;
		m.leftArm.z = -Mth.sin(bodyY) * 5.0f;
		m.leftArm.x = Mth.cos(bodyY) * 5.0f;
	}

	/** The staff spin's whole-body turn right now, in degrees (0 when not spinning). Every viewer. */
	public static float spinDegrees(Player player, float partial) {
		if (!MoonKnight.isTransformed(player)) {
			return 0f;
		}
		MoonKnightAction a = MoonKnightAnim.action(player);
		if (a.animId != MoonKnightAnim.STAFF_SPIN) {
			return 0f;
		}
		float t = (player.level().getGameTime() - a.animStart + partial - 1f) / MoonKnightConfig.STAFF_SPIN_TURN_TICKS;
		if (t <= 0f || t >= 1f) {
			return 0f;
		}
		float eased = t * t * (3f - 2f * t);
		return eased * 360f;
	}

	/**
	 * First person, just before the held truncheon is drawn (in the hand's space): sweep / smash / twirl it for the
	 * playing move. {@code arm} is the hand it is in (a left main hand mirrors the motion).
	 */
	public static void firstPerson(Player player, HumanoidArm arm, float partial, PoseStack pose) {
		if (!MoonKnight.isTransformed(player)) {
			return;
		}
		MoonKnightAction a = MoonKnightAnim.action(player);
		float[][] fr = firstPersonFor(a.animId);
		if (fr == null) {
			return;
		}
		float tick = player.level().getGameTime() - a.animStart + partial;
		if (tick < 0f || tick > fr[fr.length - 1][0]) {
			return;
		}
		float[] v = null;
		for (int i = 1; i < fr.length; i++) {
			if (tick <= fr[i][0]) {
				float[] p = fr[i - 1];
				float[] q = fr[i];
				float t = (tick - p[0]) / Math.max(1e-4f, q[0] - p[0]);
				t = t * t * (3f - 2f * t);
				v = new float[7];
				for (int k = 1; k < 7; k++) {
					v[k] = Mth.lerp(t, p[k], q[k]);
				}
				break;
			}
		}
		if (v == null) {
			return;
		}
		float s = arm == HumanoidArm.RIGHT ? 1f : -1f;
		pose.translate(s * v[1], v[2], v[3]);
		pose.mulPose(Axis.YP.rotationDegrees(s * v[5]));
		pose.mulPose(Axis.XP.rotationDegrees(v[4]));
		pose.mulPose(Axis.ZP.rotationDegrees(s * v[6]));
	}
}
