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

	/** v0.13.21 Cape Glide: how far the arms spread out as wings (radians of Z roll) and the legs part. */
	static final float GLIDE_ARM_SPREAD = 1.3f;
	static final float GLIDE_LEG_SPREAD = 0.16f;

	/** v0.13.21: set while vanilla draws the first-person hand (render thread), which must not take the glide stance. */
	public static boolean firstPersonHand;

	/** Per player: the eased glide amount (0..1) and the last time it was advanced (ns). */
	private static final java.util.Map<java.util.UUID, double[]> GLIDE_EASE = new java.util.concurrent.ConcurrentHashMap<>();

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

	/** v0.13.21 X Dash: lean into it, arms swept back. */
	private static final Pose DASH = new Pose(new float[][] {
			rest(0),
			f(2, 0.8f, 0, 0.25f, 0.8f, 0, -0.25f, 0.35f, 0, -0.6f, 0.5f, -0.25f),
			f(6, 0.8f, 0, 0.25f, 0.8f, 0, -0.25f, 0.35f, 0, -0.6f, 0.5f, -0.25f),
			rest(10) }, false, false);
	/** v0.13.21 a glide kick: arms stay spread as wings, one leg snaps down into the mob. */
	private static final Pose GLIDE_KICK = new Pose(new float[][] {
			f(0, -0.15f, 0, GLIDE_ARM_SPREAD, -0.15f, 0, -GLIDE_ARM_SPREAD, 0, 0, 0, 0, 0),
			f(2, -0.15f, 0, GLIDE_ARM_SPREAD, -0.15f, 0, -GLIDE_ARM_SPREAD, 0, 0, -1.4f, 0.35f, 0),
			f(6, -0.15f, 0, GLIDE_ARM_SPREAD, -0.15f, 0, -GLIDE_ARM_SPREAD, 0, 0, -1.2f, 0.3f, 0),
			f(10, -0.15f, 0, GLIDE_ARM_SPREAD, -0.15f, 0, -GLIDE_ARM_SPREAD, 0, 0, 0, 0, 0) }, false, false);

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
			case MoonKnightAnim.DASH -> DASH;
			case MoonKnightAnim.GLIDE_KICK -> GLIDE_KICK;
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
		// held stances first; a move's animation then blends over them (and back to them as it ends)
		if (a.has(MoonKnightAction.FLAG_GLIDING) && !firstPersonHand) {
			// v0.13.21: flat out, arms spread as wings and legs apart -- the cape stretches between them. The body
			// itself is tipped forward by MoonKnightGlidePoseMixin, so the head takes that lean back out and keeps
			// looking where the player looks.
			set(m, -0.15f, 0.0f, GLIDE_ARM_SPREAD, -0.15f, 0.0f, -GLIDE_ARM_SPREAD);
			m.body.xRot = 0.0f;
			m.rightLeg.xRot = 0.0f;
			m.leftLeg.xRot = 0.0f;
			m.rightLeg.yRot = 0.0f;
			m.leftLeg.yRot = 0.0f;
			m.rightLeg.zRot = GLIDE_LEG_SPREAD;
			m.leftLeg.zRot = -GLIDE_LEG_SPREAD;
			m.head.xRot -= glideLean(player, partial) * Mth.DEG_TO_RAD;
			m.hat.copyFrom(m.head);
		} else if (a.has(MoonKnightAction.FLAG_CAPE_BLOCK)) {
			set(m, -1.35f, -0.6f, 0.0f, -1.35f, 0.6f, 0.0f);
		} else if (a.has(MoonKnightAction.FLAG_CHARGING)) {
			m.rightArm.xRot = -2.5f + m.head.xRot * 0.5f;
			m.rightArm.yRot = -0.3f;
		}
		if (a.animId != MoonKnightAnim.NONE) {
			Pose p = poseFor(a.animId);
			if (p != null) {
				blend(m, p.frames(), now - a.animStart + partial, p.aimRight(), p.aimLeft());
			}
		}
	}

	/** v0.13.21: is this player in a Cape Glide (synced flag, suited)? */
	public static boolean isGliding(Player player) {
		return MoonKnight.isTransformed(player) && MoonKnightAnim.flag(player, MoonKnightAction.FLAG_GLIDING);
	}

	/**
	 * v0.13.21: how far (degrees) the gliding body is tipped forward right now: eased in / out over about a fifth of a
	 * second, about 72 degrees level and steeper (up to 92) looking down. 0 when not gliding.
	 */
	public static float glideLean(Player player, float partialTick) {
		boolean on = isGliding(player);
		double[] e = GLIDE_EASE.get(player.getUUID());
		if (e == null) {
			if (!on) {
				return 0.0f;
			}
			e = new double[]{0.0, System.nanoTime()};
			GLIDE_EASE.put(player.getUUID(), e);
		}
		long t = System.nanoTime();
		double dt = Math.min(0.2, (t - e[1]) / 1.0e9);
		e[1] = t;
		e[0] += ((on ? 1.0 : 0.0) - e[0]) * (1.0 - Math.exp(-dt * 12.0));
		if (!on && e[0] < 0.01) {
			GLIDE_EASE.remove(player.getUUID());
			return 0.0f;
		}
		float pitch = Mth.clamp(player.getViewXRot(partialTick), -30.0f, 60.0f);
		return (float) e[0] * (72.0f + pitch / 3.0f);
	}

	/** Forget every player's easing (world change). */
	public static void clear() {
		GLIDE_EASE.clear();
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
