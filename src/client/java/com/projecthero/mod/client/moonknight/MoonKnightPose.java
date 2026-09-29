package com.projecthero.mod.client.moonknight;

import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.data.MoonKnightAction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Moon Knight's poses: every move's keyframed animation plus the held stances (Cape Glide arms spread, Cape Shroud arms
 * crossed, a charging throw cocked back, the Eye of Khonshu arms raised). Same technique as {@code AllMightPose} /
 * {@code SymbiotePose}: angles applied to the vanilla model after vanilla's own animation, driven by the synced
 * {@link MoonKnightAction}, so every viewer -- and the GeckoLib suit, which copies the vanilla bones -- agree.
 *
 * <p>Frames are {@code {tick, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX}} in radians
 * (arm X negative = forward / up). Moves flagged "aimed" add the head's pitch / yaw to that arm.
 */
public final class MoonKnightPose {
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

	private static final Pose TRANSFORM = new Pose(new float[][] {
			rest(0),
			f(6, -0.2f, 0, 0.55f, -0.2f, 0, -0.55f, 0, 0, 0, 0, -0.25f),
			f(22, -0.25f, 0, 0.65f, -0.25f, 0, -0.65f, -0.08f, 0, 0, 0, -0.35f),
			f(26, -1.2f, 0.5f, 0, -1.2f, -0.5f, 0, 0.2f, 0, 0, 0, 0.2f),
			rest(MoonKnightConfig.TRANSFORM_TICKS + 4) }, false, false);
	private static final Pose UNTRANSFORM = new Pose(new float[][] {
			rest(0),
			f(4, -0.4f, 0, 0.4f, -0.4f, 0, -0.4f, 0, 0, 0, 0, -0.1f),
			rest(12) }, false, false);
	private static final Pose DART_THROW = new Pose(new float[][] {
			f(0, -2.6f, -0.3f, 0, 0.2f, 0, 0, 0, 0.3f, 0, 0, 0),
			f(3, -1.4f, 0.1f, 0, 0.3f, 0, 0, 0.1f, -0.3f, 0, 0, 0),
			rest(9) }, true, false);
	private static final Pose DART_FAN = new Pose(new float[][] {
			f(0, -2.4f, 0.5f, 0, 0.2f, 0, 0, 0, 0.45f, 0, 0, 0),
			f(3, -1.5f, -0.6f, 0, 0.3f, 0, 0, 0.1f, -0.5f, 0, 0, 0),
			rest(11) }, true, false);
	private static final Pose MOON_MARK = new Pose(new float[][] {
			rest(0),
			f(3, -1.6f, 0, 0, -0.5f, 0.3f, 0, 0.05f, -0.25f, 0, 0, 0),
			f(8, -1.55f, 0, 0, -0.4f, 0.3f, 0, 0.05f, -0.2f, 0, 0, 0),
			rest(13) }, true, false);
	private static final Pose GRAPPLE_FIRE = new Pose(new float[][] {
			rest(0),
			f(2, -1.7f, 0, 0, 0.3f, 0, 0, 0, -0.2f, 0, 0, 0),
			f(14, -1.6f, 0, 0, 0.3f, 0, 0, 0, -0.2f, 0, 0, 0),
			rest(18) }, true, false);
	private static final Pose DIVE_KICK = new Pose(new float[][] {
			rest(0),
			f(3, -0.6f, 0, 0.8f, -0.6f, 0, -0.8f, -0.3f, 0, -1.5f, 0.4f, -0.2f),
			f(14, -0.6f, 0, 0.8f, -0.6f, 0, -0.8f, -0.3f, 0, -1.5f, 0.4f, -0.2f),
			rest(18) }, false, false);
	private static final Pose YANK = new Pose(new float[][] {
			rest(0),
			f(2, -1.7f, 0, 0, 0.2f, 0, 0, 0, -0.2f, 0, 0, 0),
			f(6, -0.6f, 0.3f, 0, 0.2f, 0, 0, -0.15f, 0.2f, 0.2f, -0.2f, 0),
			rest(12) }, true, false);
	private static final Pose TRUNCHEON_DRAW = new Pose(new float[][] {
			rest(0),
			f(3, -1.1f, -0.4f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0),
			rest(10) }, false, false);
	private static final Pose TRUNCHEON_SLAM = new Pose(new float[][] {
			f(0, -2.8f, 0.2f, 0, 0.2f, 0, 0, -0.1f, 0.2f, 0, 0, 0),
			f(3, -0.5f, -0.2f, 0, 0.2f, 0, 0, 0.3f, -0.2f, -0.3f, 0.3f, 0.1f),
			rest(10) }, false, false);
	private static final Pose STAFF_SPIN = new Pose(spinFrames(), false, false);
	private static final Pose GROUND_SLAM = new Pose(new float[][] {
			f(0, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, -0.2f, 0, 0, 0, -0.2f),
			f(4, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0.45f, 0, -0.6f, 0.6f, 0.2f),
			f(10, -0.35f, 0, 0.2f, -0.35f, 0, -0.2f, 0.4f, 0, -0.6f, 0.6f, 0.2f),
			rest(16) }, false, false);
	private static final Pose DIVE_SLAM = new Pose(new float[][] {
			rest(0),
			f(3, -2.9f, 0, 0.3f, -2.9f, 0, -0.3f, 0.3f, 0, -0.4f, -0.4f, 0.3f),
			f(40, -2.9f, 0, 0.3f, -2.9f, 0, -0.3f, 0.3f, 0, -0.4f, -0.4f, 0.3f) }, false, false);
	private static final Pose SHADOW_STEP = new Pose(new float[][] {
			rest(0),
			f(2, 0.5f, 0, 0.4f, 0.5f, 0, -0.4f, -0.3f, 0, 0.5f, -0.3f, 0.1f),
			rest(10) }, false, false);
	private static final Pose ALTER_SWAP = new Pose(new float[][] {
			rest(0),
			f(3, -0.9f, 0.6f, 0, -0.9f, -0.6f, 0, 0.25f, 0, 0, 0, 0.45f),
			f(8, -0.8f, 0.5f, 0, -0.8f, -0.5f, 0, 0.2f, 0, 0, 0, 0.4f),
			rest(14) }, false, false);
	private static final Pose FIST_OF_KHONSHU = new Pose(new float[][] {
			rest(0),
			f(4, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, 0.2f, 0, 0.1f, -0.1f, 0.3f),
			f(8, -2.2f, 0.2f, 0, -2.2f, -0.2f, 0, -0.2f, 0, 0, 0, -0.4f),
			rest(18) }, false, false);
	private static final Pose SCHOLARS_SIGHT = new Pose(new float[][] {
			rest(0),
			f(4, -1.9f, -0.6f, 0, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			f(12, -1.9f, -0.6f, 0, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			rest(18) }, false, false);
	private static final Pose VANISH = new Pose(new float[][] {
			rest(0),
			f(3, -1.3f, -0.8f, 0, -1.3f, 0.8f, 0, 0.4f, 0, 0.3f, -0.3f, 0.4f),
			rest(12) }, false, false);
	private static final Pose MOONBEAM = new Pose(new float[][] {
			rest(0),
			f(4, -3.0f, 0, 0.2f, 0.2f, 0, 0, -0.1f, 0, 0, 0, -0.5f),
			f(12, -2.9f, 0, 0.2f, 0.2f, 0, 0, -0.1f, 0, 0, 0, -0.5f),
			rest(18) }, false, false);
	private static final Pose EYE_CHARGE = new Pose(new float[][] {
			rest(0),
			f(8, -2.8f, 0, 0.5f, -2.8f, 0, -0.5f, -0.2f, 0, 0, 0, -0.7f),
			f(60, -2.9f, 0, 0.6f, -2.9f, 0, -0.6f, -0.25f, 0, 0, 0, -0.75f) }, false, false);
	private static final Pose EYE_RELEASE = new Pose(new float[][] {
			f(0, -2.9f, 0, 0.6f, -2.9f, 0, -0.6f, -0.25f, 0, 0, 0, -0.75f),
			f(4, -0.3f, 0, 1.6f, -0.3f, 0, -1.6f, -0.2f, 0, 0, 0, -0.5f),
			f(16, -0.3f, 0, 1.5f, -0.3f, 0, -1.5f, -0.2f, 0, 0, 0, -0.45f),
			rest(24) }, false, false);
	private static final Pose JUDGEMENT = new Pose(new float[][] {
			rest(0),
			f(3, -1.6f, 0, 0, 0.2f, 0, 0, 0, -0.2f, 0, 0, 0),
			f(10, -1.55f, 0, 0, 0.2f, 0, 0, 0, -0.2f, 0, 0, 0),
			rest(15) }, true, false);
	private static final Pose RESURRECT = new Pose(new float[][] {
			f(0, -0.4f, 0, 1.9f, -0.4f, 0, -1.9f, -0.3f, 0, 0, 0, -0.7f),
			f(20, -0.4f, 0, 1.8f, -0.4f, 0, -1.8f, -0.25f, 0, 0, 0, -0.6f),
			rest(30) }, false, false);

	private static float[][] spinFrames() {
		java.util.List<float[]> out = new java.util.ArrayList<>();
		out.add(rest(0));
		for (int t = 2; t <= 14; t += 2) {
			float yaw = (t % 4 == 0) ? 1.2f : -1.2f;
			out.add(f(t, -1.55f, 0, 1.2f, -1.55f, 0, -1.2f, 0, yaw, 0.2f, -0.2f, 0));
		}
		out.add(rest(18));
		return out.toArray(new float[0][]);
	}

	private MoonKnightPose() {
	}

	private static Pose poseFor(int id) {
		return switch (id) {
			case MoonKnightAnim.TRANSFORM -> TRANSFORM;
			case MoonKnightAnim.UNTRANSFORM -> UNTRANSFORM;
			case MoonKnightAnim.DART_THROW -> DART_THROW;
			case MoonKnightAnim.DART_FAN -> DART_FAN;
			case MoonKnightAnim.MOON_MARK -> MOON_MARK;
			case MoonKnightAnim.GRAPPLE_FIRE -> GRAPPLE_FIRE;
			case MoonKnightAnim.DIVE_KICK -> DIVE_KICK;
			case MoonKnightAnim.YANK -> YANK;
			case MoonKnightAnim.TRUNCHEON_DRAW -> TRUNCHEON_DRAW;
			case MoonKnightAnim.TRUNCHEON_SLAM -> TRUNCHEON_SLAM;
			case MoonKnightAnim.STAFF_SPIN -> STAFF_SPIN;
			case MoonKnightAnim.GROUND_SLAM -> GROUND_SLAM;
			case MoonKnightAnim.DIVE_SLAM -> DIVE_SLAM;
			case MoonKnightAnim.SHADOW_STEP -> SHADOW_STEP;
			case MoonKnightAnim.ALTER_SWAP -> ALTER_SWAP;
			case MoonKnightAnim.FIST_OF_KHONSHU -> FIST_OF_KHONSHU;
			case MoonKnightAnim.SCHOLARS_SIGHT -> SCHOLARS_SIGHT;
			case MoonKnightAnim.VANISH -> VANISH;
			case MoonKnightAnim.MOONBEAM -> MOONBEAM;
			case MoonKnightAnim.EYE_CHARGE -> EYE_CHARGE;
			case MoonKnightAnim.EYE_RELEASE -> EYE_RELEASE;
			case MoonKnightAnim.JUDGEMENT -> JUDGEMENT;
			case MoonKnightAnim.RESURRECT -> RESURRECT;
			default -> null;
		};
	}

	public static int lengthTicks(int id) {
		Pose p = poseFor(id);
		return p == null ? 0 : (int) p.frames()[p.frames().length - 1][0];
	}

	static float[] sample(float[][] f, float tick) {
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

	public static void apply(Player player, HumanoidModel<?> m) {
		if (!MoonKnight.hasPower(player)) {
			return;
		}
		MoonKnightAction a = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.MOON_KNIGHT_ACTION, null);
		if (a == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		if (a.animId != MoonKnightAnim.NONE) {
			Pose p = poseFor(a.animId);
			if (p != null && blend(m, p.frames(), now - a.animStart + partial, p.aimRight(), p.aimLeft())) {
				return;
			}
		}
		// held stances
		if (a.has(MoonKnightAction.FLAG_SHROUD)) {
			set(m, -1.35f, -0.6f, 0.0f, -1.35f, 0.6f, 0.0f);
		} else if (a.has(MoonKnightAction.FLAG_GLIDING)) {
			set(m, -0.15f, 0.0f, 1.35f, -0.15f, 0.0f, -1.35f);
		} else if (a.has(MoonKnightAction.FLAG_CHARGING)) {
			m.rightArm.xRot = -2.5f + m.head.xRot * 0.5f;
			m.rightArm.yRot = -0.3f;
		}
	}

	private static void set(HumanoidModel<?> m, float rx, float ry, float rz, float lx, float ly, float lz) {
		m.rightArm.xRot = rx;
		m.rightArm.yRot = ry;
		m.rightArm.zRot = rz;
		m.leftArm.xRot = lx;
		m.leftArm.yRot = ly;
		m.leftArm.zRot = lz;
	}

	private static boolean blend(HumanoidModel<?> m, float[][] f, float tick, boolean aimRight, boolean aimLeft) {
		float[] p = sample(f, tick);
		if (p == null) {
			return false;
		}
		float end = f[f.length - 1][0];
		float w = Math.max(0f, Math.min(1f, Math.min(tick / 2f, (end - tick) / 3f)));
		if (tick >= 2f && end - tick >= 3f) {
			w = 1f;
		}
		float headX = m.head.xRot;
		float headY = m.head.yRot;
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, p[1] + (aimRight ? headX : 0f));
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, p[2] + (aimRight ? headY : 0f));
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, p[3]);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, p[4] + (aimLeft ? headX : 0f));
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, p[5] + (aimLeft ? headY : 0f));
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, p[6]);
		m.body.xRot = Mth.lerp(w, m.body.xRot, p[7]);
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8]);
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, m.rightLeg.xRot * 0.3f + p[9]);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, m.leftLeg.xRot * 0.3f + p[10]);
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
		m.hat.copyFrom(m.head);
		return true;
	}
}
