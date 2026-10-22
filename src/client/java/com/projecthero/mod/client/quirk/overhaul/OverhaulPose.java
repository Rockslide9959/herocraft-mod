package com.projecthero.mod.client.quirk.overhaul;

import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.overhaul.Overhaul;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 revamp: Overhaul's keyframed poses (the One For All pose style), set on the vanilla humanoid model at the end
 * of {@code setupAnim}. Driven by the synced {@code animId / animStart} (his ids 40..59) and the Fusion timer, so every
 * viewer sees the same thing; the server times each move's effect to the keyframe where the palm lands.
 *
 * <p>Each pose is a list of frames {@code {tick, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX,
 * headX, crouch}} (radians; arm X negative = forward / up, right arm Z positive = out to his side; {@code headX} is added
 * to the look; {@code crouch} 0..1 lowers the body onto bent legs), smoothstepped between frames and faded in / out.
 * He touches with his RIGHT hand; the ground moves are both palms slapped flat to the floor, as in the anime.
 */
public final class OverhaulPose {
	// R: a wind-up, then a straight open-palm thrust, the body twisting into it
	private static final float[][] DISASSEMBLE = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 3, 0.55f, 0.25f, 0.1f, -0.5f, 0.3f, 0, 0.05f, 0.45f, 0.2f, -0.2f, 0, 0.15f },
			{ 5, -1.62f, -0.12f, 0, 0.45f, 0.2f, -0.1f, 0.18f, -0.5f, -0.45f, 0.4f, 0.05f, 0.2f },
			{ 9, -1.55f, -0.1f, 0, 0.45f, 0.2f, -0.1f, 0.16f, -0.45f, -0.45f, 0.4f, 0.05f, 0.2f },
			{ 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// Shift+V: both palms laid on the body, drawn back to the chest as it knits together -- then a slow sweep up and out
	// as the broken ground around him rebuilds (Restore)
	private static final float[][] REASSEMBLE = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 5, -1.35f, 0.35f, 0, -1.35f, -0.35f, 0, 0.15f, 0, 0, 0, 0.3f, 0 },
			{ 10, -1.3f, 0.3f, 0, -1.3f, -0.3f, 0, 0.15f, 0, 0, 0, 0.35f, 0 },
			{ 14, -0.95f, 0.7f, 0, -0.95f, -0.7f, 0, 0.05f, 0, 0, 0, 0.1f, 0 },
			{ 18, -0.45f, 0, 0.55f, -0.45f, 0, -0.55f, 0.35f, 0, -0.3f, 0.2f, 0.25f, 0.5f },
			{ 25, -1.45f, 0, 0.95f, -1.45f, 0, -0.95f, -0.1f, 0, 0, 0, -0.2f, 0 },
			{ 29, -1.4f, 0, 0.9f, -1.4f, 0, -0.9f, -0.08f, 0, 0, 0, -0.2f, 0 },
			{ 34, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// G: a dropping lunge, the right palm slapped to the floor (the spikes answer from tick 4)
	private static final float[][] SPIKE_WAVE = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 2, -2.2f, 0, 0.15f, 0.3f, 0, -0.3f, 0.2f, 0.15f, -0.3f, 0.2f, 0.1f, 0.3f },
			{ 3, -0.55f, -0.05f, 0.12f, 0.45f, 0, -0.35f, 0.62f, 0.1f, -0.9f, 0.55f, 0.35f, 1f },
			{ 10, -0.55f, -0.05f, 0.12f, 0.45f, 0, -0.35f, 0.6f, 0.1f, -0.9f, 0.55f, 0.3f, 1f },
			{ 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// Shift+G: down on one knee, both palms flat on the ground
	private static final float[][] SPIKE_FIELD = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 2, -2.4f, 0, 0.2f, -2.4f, 0, -0.2f, 0.1f, 0, -0.4f, 0.1f, -0.1f, 0.4f },
			{ 4, -0.6f, -0.2f, 0.3f, -0.6f, 0.2f, -0.3f, 0.7f, 0, -1.35f, 0.35f, 0.4f, 1f },
			{ 12, -0.6f, -0.2f, 0.3f, -0.6f, 0.2f, -0.3f, 0.7f, 0, -1.35f, 0.35f, 0.35f, 1f },
			{ 18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// C: palms down, then a two-handed heave up as the wall bursts out of the ground
	private static final float[][] WALL = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 3, -0.6f, -0.15f, 0.15f, -0.6f, 0.15f, -0.15f, 0.55f, 0, -0.6f, 0.3f, 0.35f, 1f },
			{ 5, -0.6f, -0.15f, 0.15f, -0.6f, 0.15f, -0.15f, 0.55f, 0, -0.6f, 0.3f, 0.35f, 1f },
			{ 9, -2.6f, -0.1f, 0.3f, -2.6f, 0.1f, -0.3f, -0.15f, 0, -0.1f, 0.15f, -0.3f, 0 },
			{ 12, -2.5f, -0.1f, 0.3f, -2.5f, 0.1f, -0.3f, -0.12f, 0, -0.1f, 0.15f, -0.25f, 0 },
			{ 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// Shift+Z: a palm slammed down, then he rises and closes his fist -- the pits swallow them
	private static final float[][] PIT = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 3, -0.5f, 0, 0.1f, 0.3f, 0, -0.3f, 0.6f, 0.2f, -0.7f, 0.4f, 0.4f, 0.9f },
			{ 6, -0.5f, 0, 0.1f, 0.3f, 0, -0.3f, 0.6f, 0.2f, -0.7f, 0.4f, 0.4f, 0.9f },
			{ 10, -1.55f, 0.2f, 0, 0.25f, 0, -0.2f, 0.05f, -0.35f, 0, 0, 0.1f, 0 },
			{ 13, -1.35f, 0.25f, 0, 0.25f, 0, -0.2f, 0.05f, -0.3f, 0, 0, 0.1f, 0 },
			{ 18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// Z: a low dash, the bare right hand reaching out ahead, left arm swept back
	private static final float[][] STRIKE = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 2, -1.6f, -0.1f, 0, 0.8f, 0.1f, -0.2f, 0.42f, -0.3f, -0.8f, 0.7f, -0.2f, 0.35f },
			{ 8, -1.6f, -0.1f, 0, 0.8f, 0.1f, -0.2f, 0.42f, -0.3f, -0.8f, 0.7f, -0.2f, 0.35f },
			{ 13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// Shift+R: grab the weapon / plate, then a wrenching twist as it comes apart
	private static final float[][] DISARM = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 3, -1.5f, -0.2f, 0, 0.2f, 0, -0.15f, 0.1f, -0.2f, -0.2f, 0.2f, 0, 0 },
			{ 6, -1.35f, 0.15f, 0.65f, 0.2f, 0, -0.15f, 0.12f, 0.35f, -0.2f, 0.2f, 0.1f, 0 },
			{ 9, -1.0f, 0.2f, 0.75f, 0.2f, 0, -0.15f, 0.05f, 0.4f, -0.1f, 0.1f, 0.1f, 0 },
			{ 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// V: arms crossed, hunched, as the ground streams into him -- then flung wide as the stone closes over
	private static final float[][] FUSION = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 6, -1.3f, -0.8f, 0, -1.3f, 0.8f, 0, 0.3f, 0, 0.15f, -0.15f, 0.4f, 0.5f },
			{ 11, -1.3f, -0.8f, 0, -1.3f, 0.8f, 0, 0.32f, 0, 0.15f, -0.15f, 0.45f, 0.55f },
			{ 14, -0.9f, 0.3f, 1.15f, -0.9f, -0.3f, -1.15f, -0.15f, 0, -0.1f, 0.1f, -0.3f, 0 },
			{ 19, -0.85f, 0.3f, 1.1f, -0.85f, -0.3f, -1.1f, -0.12f, 0, -0.1f, 0.1f, -0.25f, 0 },
			{ 24, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// Shift+X: a palm slapped down, then launched up off the rising pillar -- arms flung overhead, legs tucked
	private static final float[][] PILLAR = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 2, -0.55f, 0, 0.15f, 0.3f, 0, -0.3f, 0.55f, 0, -0.7f, 0.4f, 0.35f, 1f },
			{ 6, -2.85f, -0.1f, 0.35f, -2.85f, 0.1f, -0.35f, -0.15f, 0, -0.9f, 0.35f, -0.35f, 0 },
			{ 12, -2.6f, -0.1f, 0.4f, -2.6f, 0.1f, -0.4f, -0.1f, 0, -0.7f, 0.25f, -0.25f, 0 },
			{ 18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// Shift+C (off): the left hand pinches the right glove's fingertips and pulls it off
	private static final float[][] GLOVES_OFF = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -1.1f, -0.35f, 0, -1.15f, 0.7f, 0, 0.05f, 0, 0, 0, 0.35f, 0 },
			{ 7, -1.1f, -0.35f, 0, -1.15f, 0.7f, 0, 0.05f, 0, 0, 0, 0.35f, 0 },
			{ 10, -1.2f, -0.35f, 0, -0.5f, 0.2f, -0.55f, 0.05f, 0, 0, 0, 0.3f, 0 },
			{ 15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// Shift+C (on): the glove drawn back on and the cuff tugged tight
	private static final float[][] GLOVES_ON = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 4, -1.2f, -0.35f, 0, -0.5f, 0.2f, -0.55f, 0.05f, 0, 0, 0, 0.3f, 0 },
			{ 8, -1.1f, -0.35f, 0, -1.1f, 0.75f, 0, 0.05f, 0, 0, 0, 0.35f, 0 },
			{ 10, -1.0f, -0.3f, 0, -0.95f, 0.8f, 0, 0.05f, 0, 0, 0, 0.35f, 0 },
			{ 15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// X: Terrain Surf -- side-on surfer stance riding the wave, knees bent, arms out for balance, held while it runs
	private static final float[][] SURF = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 3, -0.5f, 0, 0.3f, 0.2f, 0, -0.3f, 0.45f, 0.2f, -0.6f, 0.3f, 0.3f, 1f },
			{ 7, -0.35f, 0.2f, 1.05f, -0.2f, -0.2f, -0.95f, 0.22f, 0.75f, -0.45f, 0.4f, -0.1f, 0.55f },
			{ 20, -0.45f, 0.2f, 1.15f, -0.1f, -0.2f, -0.85f, 0.25f, 0.8f, -0.5f, 0.45f, -0.1f, 0.6f },
			{ 31, -0.3f, 0.2f, 1.0f, -0.25f, -0.2f, -1.0f, 0.2f, 0.7f, -0.4f, 0.35f, -0.1f, 0.5f },
			{ 37, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// Fusion ending: the stone falls away and he sags
	private static final float[][] FUSION_END = {
			{ 0, 0.1f, 0, 0.6f, 0.1f, 0, -0.6f, 0.2f, 0, 0, 0, 0.35f, 0.3f },
			{ 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };
	// the bare-hand touch on a punch: a short palm push after the swing
	private static final float[][] TOUCH = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
			{ 3, -1.5f, -0.1f, 0, 0.2f, 0, -0.1f, 0.1f, -0.25f, 0, 0, 0, 0 },
			{ 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };

	private OverhaulPose() {
	}

	static float[][] frames(int animId) {
		if (animId == Overhaul.ANIM_DISASSEMBLE) {
			return DISASSEMBLE;
		} else if (animId == Overhaul.ANIM_REASSEMBLE) {
			return REASSEMBLE;
		} else if (animId == Overhaul.ANIM_SPIKE_WAVE) {
			return SPIKE_WAVE;
		} else if (animId == Overhaul.ANIM_SPIKE_FIELD) {
			return SPIKE_FIELD;
		} else if (animId == Overhaul.ANIM_WALL) {
			return WALL;
		} else if (animId == Overhaul.ANIM_PIT) {
			return PIT;
		} else if (animId == Overhaul.ANIM_STRIKE) {
			return STRIKE;
		} else if (animId == Overhaul.ANIM_DISARM) {
			return DISARM;
		} else if (animId == Overhaul.ANIM_FUSION) {
			return FUSION;
		} else if (animId == Overhaul.ANIM_PILLAR) {
			return PILLAR;
		} else if (animId == Overhaul.ANIM_GLOVES_OFF) {
			return GLOVES_OFF;
		} else if (animId == Overhaul.ANIM_GLOVES_ON) {
			return GLOVES_ON;
		} else if (animId == Overhaul.ANIM_SURF) {
			return SURF;
		} else if (animId == Overhaul.ANIM_FUSION_END) {
			return FUSION_END;
		} else if (animId == Overhaul.ANIM_TOUCH) {
			return TOUCH;
		}
		return null;
	}

	public static int length(int animId) {
		float[][] f = frames(animId);
		return f == null ? 0 : (int) f[f.length - 1][0];
	}

	/** Values at {@code tick} of {@code f} (13 floats, [0] unused), or null past the end. */
	static float[] sample(float[][] f, float tick) {
		if (tick < 0f || tick > f[f.length - 1][0]) {
			return null;
		}
		for (int i = 1; i < f.length; i++) {
			if (tick <= f[i][0]) {
				float[] a = f[i - 1], b = f[i];
				float t = (tick - a[0]) / Math.max(1e-4f, b[0] - a[0]);
				t = t * t * (3f - 2f * t);
				float[] out = new float[13];
				for (int k = 1; k < 13; k++) {
					out[k] = Mth.lerp(t, a[k], b[k]);
				}
				return out;
			}
		}
		return null;
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		if (!Overhaul.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		if (Overhaul.fused(player)) {
			// the heavy stone stance: arms held out from the plated body, a slow breathing sway
			float sw = Mth.sin((player.tickCount + partial) * 0.08f) * 0.05f;
			m.rightArm.zRot += 0.32f + sw;
			m.leftArm.zRot -= 0.32f + sw;
			m.rightArm.xRot = m.rightArm.xRot * 0.7f - 0.1f;
			m.leftArm.xRot = m.leftArm.xRot * 0.7f - 0.1f;
			m.head.xRot = Mth.lerp(0.3f, m.head.xRot, 0.1f);
		}
		QuirkState s = Quirks.peek(player);
		float[][] f = s == null || s.animId < Overhaul.ANIM_BASE || s.animId > Overhaul.ANIM_LAST ? null : frames(s.animId);
		if (f != null) {
			float tick = player.level().getGameTime() - s.animStart + partial;
			if (s.animId == Overhaul.ANIM_SURF && tick > 20f && Overhaul.running(player, Overhaul.V_SURF)) {
				// v0.16.7 (user): the surf runs as long as X is held -- rock gently in the riding stance (frames 20..31)
				float x = (tick - 20f) % 22f;
				tick = 20f + (x < 11f ? x : 22f - x);
			}
			float[] p = sample(f, tick);
			if (p != null) {
				float end = f[f.length - 1][0];
				float w = Math.max(0f, Math.min(1f, Math.min(tick / 1.5f + 0.2f, (end - tick) / 3f)));
				m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, p[1]);
				m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, p[2]);
				m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, p[3]);
				m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, p[4]);
				m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, p[5]);
				m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, p[6]);
				com.projecthero.mod.client.HipBend.pitch(m, w, p[7]);
				m.body.yRot = Mth.lerp(w, m.body.yRot, p[8]);
				m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, p[9]);
				m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, p[10]);
				m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
				crouch(m, w * p[12]);
			}
		}
		m.hat.copyFrom(m.head);
	}

	/** A crouch: legs bent, body lowered (the model's sneak offsets, weighted). */
	private static void crouch(HumanoidModel<?> model, float w) {
		if (w <= 0.001f || model.crouching) {
			return;
		}
		model.body.y += 3.2f * w;
		model.head.y += 4.2f * w;
		model.rightArm.y += 3.2f * w;
		model.leftArm.y += 3.2f * w;
		model.rightLeg.z += 4.0f * w;
		model.leftLeg.z += 4.0f * w;
		model.rightLeg.y += 0.2f * w;
		model.leftLeg.y += 0.2f * w;
	}
}
