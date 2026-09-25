package com.projecthero.mod.client.allmight;

import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * All Might's poses (v0.12.33): keyframed limb poses for every Smash, the Full Cowl flex, the Leap and the
 * transformation, applied to the humanoid model after vanilla's own animation. They are driven by the synced
 * {@link AllMightState#animId} / {@link AllMightState#animStart}, i.e. by the very game-time clock the server uses to
 * schedule each hit -- so the punch lands on the keyframe where the fist is fully extended and nothing can desync.
 * Because the costume's GeckoLib armour copies its bones from the vanilla model, the armour follows every pose too.
 *
 * <p>Each pose is a list of frames {@code {tick, rightArmX, rightArmY, rightArmZ, leftArmX, leftArmY, leftArmZ, bodyX, bodyY,
 * rightLegX, leftLegX, headX}} in radians (arm X negative = swung forward/up), interpolated with a smoothstep and faded
 * in/out over the first/last ticks.
 */
public final class AllMightPose {
	private static final float[][] DETROIT = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 5, 0.9f, -0.2f, 0, -0.9f, 0.4f, 0, 0.1f, 0.5f, 0.3f, -0.3f, 0 },
			{ 6, -1.6f, -0.15f, 0, 0.4f, 0.2f, 0, 0.3f, -0.6f, -0.4f, 0.4f, 0 },
			{ 10, -1.6f, -0.15f, 0, 0.4f, 0.2f, 0, 0.3f, -0.6f, -0.4f, 0.4f, 0 },
			{ 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] TEXAS = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 6, 1.3f, 0.2f, 0, 1.3f, -0.2f, 0, -0.25f, 0, 0.3f, -0.3f, -0.15f },
			{ 8, -1.55f, 0.1f, 0, -1.55f, -0.1f, 0, 0.35f, 0, -0.4f, 0.4f, 0.1f },
			{ 18, -1.55f, 0.1f, 0, -1.55f, -0.1f, 0, 0.35f, 0, -0.4f, 0.4f, 0.1f },
			{ 24, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] CAROLINA = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 5, 0.9f, 0, 0, 0.9f, 0, 0, 0.5f, 0, 0.7f, -0.7f, 0.2f },
			{ 7, -1.55f, -0.1f, 0, -1.55f, 0.1f, 0, 1.0f, 0, 0.6f, -0.3f, -0.6f },
			{ 18, -1.55f, -0.1f, 0, -1.55f, 0.1f, 0, 1.0f, 0, 0.6f, -0.3f, -0.6f },
			{ 26, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] NEW_HAMPSHIRE = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 6, 0.8f, 0, 0, 0.8f, 0, 0, 0.5f, 0, 0.8f, 0.8f, 0.3f },
			{ 9, -3.0f, 0, 0, -3.0f, 0, 0, -0.2f, 0, -0.2f, 0.2f, -0.3f },
			{ 22, -3.0f, 0, 0, -3.0f, 0, 0, -0.2f, 0, -0.2f, 0.2f, -0.3f },
			{ 32, -1.6f, -0.1f, 0, 0.3f, 0, 0, 0.5f, 0, 0.4f, -0.4f, 0.2f },
			{ 40, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] COWL = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -1.0f, 0.4f, 0.35f, -1.0f, -0.4f, -0.35f, -0.1f, 0, 0.1f, -0.1f, -0.1f },
			{ 10, -1.0f, 0.4f, 0.35f, -1.0f, -0.4f, -0.35f, -0.1f, 0, 0.1f, -0.1f, -0.1f },
			{ 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] UNITED_STATES = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 22, 1.7f, -0.3f, 0, -1.0f, 0.5f, 0, 0.15f, 0.9f, 0.5f, -0.5f, 0 },
			{ 29, 1.9f, -0.3f, 0, -1.0f, 0.5f, 0, 0.2f, 1.0f, 0.5f, -0.5f, 0 },
			{ 31, -1.65f, -0.1f, 0, 0.3f, 0.2f, 0, 0.55f, -0.8f, -0.5f, 0.5f, 0.1f },
			{ 44, -1.65f, -0.1f, 0, 0.3f, 0.2f, 0, 0.55f, -0.8f, -0.5f, 0.5f, 0.1f },
			{ 58, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] LEAP = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 3, 0.6f, 0, 0, 0.6f, 0, 0, 0.45f, 0, 0.7f, 0.7f, 0.2f },
			{ 6, -2.8f, 0, 0, -2.8f, 0, 0, -0.15f, 0, -0.2f, 0.2f, -0.3f },
			{ 14, -2.4f, 0, 0, -2.4f, 0, 0, -0.1f, 0, 0, 0, -0.2f },
			{ 22, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] TRANSFORM_UP = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 8, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, 0.1f, 0, 0.05f, -0.05f, 0.1f },
			{ 16, -0.25f, 0, 1.45f, -0.25f, 0, -1.45f, -0.18f, 0, 0.12f, -0.12f, -0.25f },
			{ 24, -0.25f, 0, 1.45f, -0.25f, 0, -1.45f, -0.18f, 0, 0.12f, -0.12f, -0.25f },
			{ 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	private static final float[][] TRANSFORM_DOWN = {
			{ 0, -0.2f, 0, 0.8f, -0.2f, 0, -0.8f, 0, 0, 0, 0, 0 },
			{ 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };

	private AllMightPose() {
	}

	private static float[][] frames(int animId) {
		return switch (animId) {
			case AllMightState.ANIM_DETROIT -> DETROIT;
			case AllMightState.ANIM_TEXAS -> TEXAS;
			case AllMightState.ANIM_CAROLINA -> CAROLINA;
			case AllMightState.ANIM_NEW_HAMPSHIRE -> NEW_HAMPSHIRE;
			case AllMightState.ANIM_COWL -> COWL;
			case AllMightState.ANIM_UNITED_STATES -> UNITED_STATES;
			case AllMightState.ANIM_LEAP -> LEAP;
			case AllMightState.ANIM_TRANSFORM_UP -> TRANSFORM_UP;
			case AllMightState.ANIM_TRANSFORM_DOWN -> TRANSFORM_DOWN;
			default -> null;
		};
	}

	/** Pose values at {@code tick} (fractional) of {@code f}, or null past the end. */
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
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || !s.hasPower || s.animId == AllMightState.ANIM_NONE) {
			return;
		}
		float[][] f = frames(s.animId);
		if (f == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float tick = (player.level().getGameTime() - s.animStart) + partial;
		float[] p = sample(f, tick);
		if (p == null) {
			return;
		}
		// fade in over the first 2 ticks and out over the last 3, so it blends with the walk cycle instead of snapping
		float end = f[f.length - 1][0];
		float w = Math.min(1f, Math.min(tick / 2f, (end - tick) / 3f));
		w = Math.max(0f, w);
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, p[1]);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, p[2]);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, p[3]);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, p[4]);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, p[5]);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, p[6]);
		m.body.xRot = Mth.lerp(w, m.body.xRot, p[7]);
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8]);
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, p[9]);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, p[10]);
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
		m.hat.copyFrom(m.head);
	}

	/** Used by tests: whether an animation id has a pose. */
	public static boolean hasPose(int animId) {
		return frames(animId) != null;
	}

	/** Used by the HUD/tests: the length of a pose in ticks (0 if none). */
	public static int lengthTicks(int animId) {
		float[][] f = frames(animId);
		return f == null ? 0 : (int) f[f.length - 1][0];
	}

	/** Whether the wearer currently looks fully transformed (used to keep the pose helper referenced in one place). */
	public static boolean fullPower(Player player) {
		return AllMight.isFullPower(player);
	}
}
