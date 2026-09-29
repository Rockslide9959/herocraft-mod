package com.projecthero.mod.client.mutation;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.22: keyframed body animations for the experimental mutations, the same technique as
 * {@code AllMightPose}: the server names a pose and a start tick ({@link MutationVisuals#play}), every
 * client samples the keyframes on that shared game-time clock after vanilla's own animation, and because
 * armour copies the vanilla model's bones it follows too.
 *
 * <h2>Frame format</h2>
 * Each frame is {@code {tick, rightArmX, rightArmY, rightArmZ, leftArmX, leftArmY, leftArmZ, bodyX, bodyY,
 * rightLegX, leftLegX, headX}} in radians; arm X negative swings the arm forward / up (-1.57 = straight out
 * in front, -3.0 = overhead); right-arm Z positive raises it out to the side (left arm: negative);
 * right-arm Y negative turns it in toward the chest (left arm: positive). {@code headX} is ADDED to the
 * player's look pitch. Frames are smoothstep-interpolated.
 *
 * <p>The special value {@link #AIM} in an arm's X or Y slot means "point where the player is looking":
 * X becomes {@code head.xRot - 90deg} and Y becomes {@code head.yRot} (+ a tiny inward offset), so beams,
 * casts and grabs track the crosshair.
 *
 * <p>A pose with {@code loopFrom >= 0} is a channel / stance: after its last frame it keeps cycling from
 * {@code loopFrom} until the server stops it. One-shot poses fade out over their last 3 ticks.
 */
public final class MutationPose {
	/** Marker value for "aim this arm along the look direction". */
	public static final float AIM = 99f;

	/** @param loopFrom tick to loop back to after the last frame, or -1 for a one-shot */
	public record Def(float[][] frames, float loopFrom) {
		public float end() {
			return frames[frames.length - 1][0];
		}

		public boolean loops() {
			return loopFrom >= 0;
		}
	}

	private static final Map<String, Def> POSES = new HashMap<>();

	private MutationPose() {
	}

	/** Registers a one-shot pose. */
	public static void register(String id, float[][] frames) {
		POSES.put(id, new Def(frames, -1));
	}

	/** Registers a looping pose (channel / stance) that cycles from {@code loopFrom} to its last frame. */
	public static void registerLoop(String id, float loopFrom, float[][] frames) {
		POSES.put(id, new Def(frames, loopFrom));
	}

	public static Def get(String id) {
		return POSES.get(id);
	}

	public static boolean has(String id) {
		return POSES.containsKey(id);
	}

	public static java.util.Set<String> ids() {
		return java.util.Collections.unmodifiableSet(POSES.keySet());
	}

	/**
	 * Frame values at {@code tick} (fractional) of {@code def}, or null past the end of a one-shot. Returns 24 floats:
	 * [1..11] the fixed value of each channel, [13..23] how much of that channel is "aimed" (0..1), so a frame
	 * marked {@link #AIM} blends smoothly into and out of the look direction.
	 */
	static float[] sample(Def def, float tick) {
		float[][] f = def.frames();
		float end = def.end();
		if (tick < 0f) {
			return null;
		}
		if (tick > end) {
			if (!def.loops()) {
				return null;
			}
			float span = Math.max(1f, end - def.loopFrom());
			tick = def.loopFrom() + ((tick - def.loopFrom()) % span);
		}
		for (int i = 1; i < f.length; i++) {
			if (tick <= f[i][0]) {
				float[] a = f[i - 1];
				float[] b = f[i];
				float t = (tick - a[0]) / Math.max(1e-4f, b[0] - a[0]);
				t = t * t * (3f - 2f * t);
				float[] out = new float[24];
				for (int k = 1; k < 12; k++) {
					boolean aa = a[k] == AIM;
					boolean ba = b[k] == AIM;
					float av = aa ? (ba ? 0f : b[k]) : a[k];
					float bv = ba ? av : b[k];
					out[k] = Mth.lerp(t, av, bv);
					out[12 + k] = Mth.lerp(t, aa ? 1f : 0f, ba ? 1f : 0f);
				}
				return out;
			}
		}
		return null;
	}

	private static float channel(float[] p, int k, float aimed) {
		return Mth.lerp(p[12 + k], p[k], aimed);
	}

	/** Applies the player's current mutation pose to {@code m} (called at the tail of {@code HumanoidModel.setupAnim}). */
	public static void apply(Player player, HumanoidModel<?> m) {
		MutationVisualState s = MutationVisuals.state(player);
		if (s.anim().isEmpty()) {
			return;
		}
		Def def = POSES.get(s.anim());
		if (def == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float tick = (player.level().getGameTime() - s.animStart()) + partial;
		float[] p = sample(def, tick);
		if (p == null) {
			return;
		}
		float w = Math.min(1f, tick / 2f);
		if (!def.loops()) {
			w = Math.min(w, (def.end() - tick) / 3f);
		}
		w = Math.max(0f, w);
		float aimX = m.head.xRot - Mth.HALF_PI;
		float aimY = m.head.yRot;
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, channel(p, 1, aimX));
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, channel(p, 2, aimY - 0.08f));
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, p[3]);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, channel(p, 4, aimX));
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, channel(p, 5, aimY + 0.08f));
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, p[6]);
		m.body.xRot = Mth.lerp(w, m.body.xRot, p[7]);
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8]);
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, p[9]);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, p[10]);
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
		m.hat.copyFrom(m.head);
	}
}
