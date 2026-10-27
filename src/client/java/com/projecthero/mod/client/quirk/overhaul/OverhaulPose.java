package com.projecthero.mod.client.quirk.overhaul;

import static com.projecthero.mod.client.quirk.QuirkKeyframes.fc;
import static com.projecthero.mod.client.quirk.QuirkKeyframes.restC;

import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.overhaul.Overhaul;
import com.projecthero.mod.quirk.overhaul.OverhaulConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 revamp: Overhaul's keyframed poses (the One For All pose style), set on the vanilla humanoid model at the end
 * of {@code setupAnim}. Driven by the synced {@code animId / animStart} (his ids 40..59), the Fusion timer and the held key, so
 * every viewer sees the same thing; the server times each move's effect to the keyframe where the palm lands.
 *
 * <p>Each pose is a list of frames in the shared {@link QuirkKeyframes} format (v0.17.1: the 16 columns -- full leg
 * rotation -- plus the 17th {@code crouch} column, {@link QuirkKeyframes#fc}; radians; arm X negative = forward / up, right
 * arm Z positive = out to his side; {@code headX} is added to the look; {@code crouch} 0..1 lowers the body onto bent legs),
 * smoothstepped between frames and faded in / out. Every attack has a wind-up, a contact frame on the server's hit tick and a
 * follow-through. He touches with his RIGHT hand; the ground moves are both palms slapped flat to the floor, as in the anime.
 */
public final class OverhaulPose {
	// R: a glove tug, a wind-up, then a straight two-finger thrust, the body twisting into it
	private static final float[][] DISASSEMBLE = {
			restC(0),
			fc(2, -0.9f, -0.3f, 0, -1.0f, 0.55f, 0, 0.05f, 0.2f, 0, 0, 0, 0, 0, 0, 0.25f, 0.1f),
			fc(4, 0.55f, 0.25f, 0.1f, -0.5f, 0.3f, 0, 0.05f, 0.45f, 0.2f, 0, 0, -0.2f, 0, 0, 0, 0.15f),
			fc(6, -1.62f, -0.12f, 0, 0.45f, 0.2f, -0.1f, 0.18f, -0.5f, -0.45f, 0, 0, 0.4f, 0, 0, 0.05f, 0.2f),
			fc(10, -1.55f, -0.1f, 0, 0.45f, 0.2f, -0.1f, 0.16f, -0.45f, -0.45f, 0, 0, 0.4f, 0, 0, 0.05f, 0.2f),
			restC(16) };
	// V: both palms laid on the body (or the ally), drawn back to the chest as it knits together
	private static final float[][] REASSEMBLE = {
			restC(0),
			fc(5, -1.35f, 0.35f, 0, -1.35f, -0.35f, 0, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0.3f, 0),
			fc(10, -1.3f, 0.3f, 0, -1.3f, -0.3f, 0, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0.35f, 0),
			fc(14, -0.95f, 0.7f, 0, -0.95f, -0.7f, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0.1f, 0),
			fc(20, -0.4f, 0.2f, 0.25f, -0.4f, -0.2f, -0.25f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			restC(26) };
	// G: a dropping lunge, the right palm slapped to the floor (the spikes answer from tick 4)
	private static final float[][] SPIKE_WAVE = {
			restC(0),
			fc(2, -2.2f, 0, 0.15f, 0.3f, 0, -0.3f, 0.2f, 0.15f, -0.3f, 0, 0, 0.2f, 0, 0, 0.1f, 0.3f),
			fc(3, -0.55f, -0.05f, 0.12f, 0.45f, 0, -0.35f, 0.62f, 0.1f, -0.9f, 0, 0, 0.55f, 0, 0, 0.35f, 1f),
			fc(10, -0.55f, -0.05f, 0.12f, 0.45f, 0, -0.35f, 0.6f, 0.1f, -0.9f, 0, 0, 0.55f, 0, 0, 0.3f, 1f),
			restC(16) };
	// Shift+G: down on one knee, both palms flat on the ground
	private static final float[][] SPIKE_FIELD = {
			restC(0),
			fc(2, -2.4f, 0, 0.2f, -2.4f, 0, -0.2f, 0.1f, 0, -0.4f, 0, 0, 0.1f, 0, 0, -0.1f, 0.4f),
			fc(4, -0.6f, -0.2f, 0.3f, -0.6f, 0.2f, -0.3f, 0.7f, 0, -1.35f, 0, 0, 0.35f, 0, 0, 0.4f, 1f),
			fc(12, -0.6f, -0.2f, 0.3f, -0.6f, 0.2f, -0.3f, 0.7f, 0, -1.35f, 0, 0, 0.35f, 0, 0, 0.35f, 1f),
			restC(18) };
	// hold G: Thorn Forest -- down on one knee, both palms pressed flat, rising slowly as he aims (frames 6..30 loop)
	private static final float[][] THORN = {
			restC(0),
			fc(3, -0.6f, -0.2f, 0.3f, -0.6f, 0.2f, -0.3f, 0.72f, 0, -1.4f, 0, 0.05f, 0.4f, 0, -0.05f, 0.45f, 1f),
			fc(6, -0.62f, -0.22f, 0.3f, -0.62f, 0.22f, -0.3f, 0.75f, 0, -1.45f, 0, 0.05f, 0.4f, 0, -0.05f, 0.5f, 1f),
			fc(18, -0.75f, -0.18f, 0.28f, -0.75f, 0.18f, -0.28f, 0.62f, 0, -1.25f, 0, 0.05f, 0.35f, 0, -0.05f, 0.2f, 0.85f),
			fc(30, -0.62f, -0.22f, 0.3f, -0.62f, 0.22f, -0.3f, 0.75f, 0, -1.45f, 0, 0.05f, 0.4f, 0, -0.05f, 0.5f, 1f),
			restC(34) };
	// Shift+C: palms down, then a two-handed heave up as the wall bursts out of the ground
	private static final float[][] WALL = {
			restC(0),
			fc(3, -0.6f, -0.15f, 0.15f, -0.6f, 0.15f, -0.15f, 0.55f, 0, -0.6f, 0, 0, 0.3f, 0, 0, 0.35f, 1f),
			fc(5, -0.6f, -0.15f, 0.15f, -0.6f, 0.15f, -0.15f, 0.55f, 0, -0.6f, 0, 0, 0.3f, 0, 0, 0.35f, 1f),
			fc(9, -2.6f, -0.1f, 0.3f, -2.6f, 0.1f, -0.3f, -0.15f, 0, -0.1f, 0, 0, 0.15f, 0, 0, -0.3f, 0),
			fc(12, -2.5f, -0.1f, 0.3f, -2.5f, 0.1f, -0.3f, -0.12f, 0, -0.1f, 0, 0, 0.15f, 0, 0, -0.25f, 0),
			restC(16) };
	// C: Shrapnel Burst -- crouched, the right palm low behind, then swept up and out as the ground flies
	private static final float[][] SHRAPNEL = {
			restC(0),
			fc(2, 0.35f, 0.2f, 0.35f, -0.4f, 0, -0.25f, 0.45f, 0.4f, -0.5f, 0, 0, 0.3f, 0, 0, 0.3f, 0.7f),
			fc(4, -0.2f, -0.2f, 0.2f, -0.3f, 0, -0.25f, 0.35f, 0.1f, -0.4f, 0, 0, 0.3f, 0, 0, 0.2f, 0.5f),
			fc(6, -2.35f, -0.35f, 0.25f, 0.35f, 0, -0.3f, -0.12f, -0.35f, -0.1f, 0, 0, 0.1f, 0, 0, -0.25f, 0.1f),
			fc(10, -2.25f, -0.3f, 0.25f, 0.3f, 0, -0.3f, -0.1f, -0.3f, -0.1f, 0, 0, 0.1f, 0, 0, -0.2f, 0),
			restC(16) };
	// Shift+Z: a palm slammed down, then he rises and closes his fist -- the pits swallow them
	private static final float[][] PIT = {
			restC(0),
			fc(3, -0.5f, 0, 0.1f, 0.3f, 0, -0.3f, 0.6f, 0.2f, -0.7f, 0, 0, 0.4f, 0, 0, 0.4f, 0.9f),
			fc(6, -0.5f, 0, 0.1f, 0.3f, 0, -0.3f, 0.6f, 0.2f, -0.7f, 0, 0, 0.4f, 0, 0, 0.4f, 0.9f),
			fc(10, -1.55f, 0.2f, 0, 0.25f, 0, -0.2f, 0.05f, -0.35f, 0, 0, 0, 0, 0, 0, 0.1f, 0),
			fc(13, -1.35f, 0.25f, 0, 0.25f, 0, -0.2f, 0.05f, -0.3f, 0, 0, 0, 0, 0, 0, 0.1f, 0),
			restC(18) };
	// Z: a low dash, the bare right hand reaching out ahead, left arm swept back
	private static final float[][] STRIKE = {
			restC(0),
			fc(2, -1.6f, -0.1f, 0, 0.8f, 0.1f, -0.2f, 0.42f, -0.3f, -0.8f, 0, 0, 0.7f, 0, 0, -0.2f, 0.35f),
			fc(8, -1.6f, -0.1f, 0, 0.8f, 0.1f, -0.2f, 0.42f, -0.3f, -0.8f, 0, 0, 0.7f, 0, 0, -0.2f, 0.35f),
			restC(13) };
	// Shift+R: a quick wrist grab, then a wrenching twist as it comes apart
	private static final float[][] DISARM = {
			restC(0),
			fc(3, -1.5f, -0.2f, 0, 0.2f, 0, -0.15f, 0.1f, -0.2f, -0.2f, 0, 0, 0.2f, 0, 0, 0, 0),
			fc(6, -1.35f, 0.15f, 0.65f, 0.2f, 0, -0.15f, 0.12f, 0.35f, -0.2f, 0, 0, 0.2f, 0, 0, 0.1f, 0),
			fc(9, -1.0f, 0.2f, 0.75f, 0.2f, 0, -0.15f, 0.05f, 0.4f, -0.1f, 0, 0, 0.1f, 0, 0, 0.1f, 0),
			restC(14) };
	// Shift+V: arms crossed, hunched, as the ground streams into him -- then flung wide as the stone closes over
	private static final float[][] FUSION = {
			restC(0),
			fc(6, -1.3f, -0.8f, 0, -1.3f, 0.8f, 0, 0.3f, 0, 0.15f, 0, 0, -0.15f, 0, 0, 0.4f, 0.5f),
			fc(11, -1.3f, -0.8f, 0, -1.3f, 0.8f, 0, 0.32f, 0, 0.15f, 0, 0, -0.15f, 0, 0, 0.45f, 0.55f),
			fc(14, -0.9f, 0.3f, 1.15f, -0.9f, -0.3f, -1.15f, -0.15f, 0, -0.1f, 0, 0, 0.1f, 0, 0, -0.3f, 0),
			fc(19, -0.85f, 0.3f, 1.1f, -0.85f, -0.3f, -1.1f, -0.12f, 0, -0.1f, 0, 0, 0.1f, 0, 0, -0.25f, 0),
			restC(24) };
	// Shift+X: a palm slapped down, then launched up off the rising pillar -- arms flung overhead, legs tucked
	private static final float[][] PILLAR = {
			restC(0),
			fc(2, -0.55f, 0, 0.15f, 0.3f, 0, -0.3f, 0.55f, 0, -0.7f, 0, 0, 0.4f, 0, 0, 0.35f, 1f),
			fc(6, -2.85f, -0.1f, 0.35f, -2.85f, 0.1f, -0.35f, -0.15f, 0, -0.9f, 0, 0, 0.35f, 0, 0, -0.35f, 0),
			fc(12, -2.6f, -0.1f, 0.4f, -2.6f, 0.1f, -0.4f, -0.1f, 0, -0.7f, 0, 0, 0.25f, 0, 0, -0.25f, 0),
			restC(18) };
	// the glove tug: the left hand pinches the right glove's fingertips and pulls it off
	private static final float[][] GLOVES_OFF = {
			restC(0),
			fc(4, -1.1f, -0.35f, 0, -1.15f, 0.7f, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0.35f, 0),
			fc(7, -1.1f, -0.35f, 0, -1.15f, 0.7f, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0.35f, 0),
			fc(10, -1.2f, -0.35f, 0, -0.5f, 0.2f, -0.55f, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0.3f, 0),
			restC(15) };
	// hold C at 100: Total Disassembly -- slammed down on both knees, both palms flat, head bowed; held while the waves roll
	private static final float[][] ULTIMATE = {
			restC(0),
			fc(2, -2.6f, -0.1f, 0.25f, -2.6f, 0.1f, -0.25f, 0.05f, 0, -0.3f, 0, 0, -0.3f, 0, 0, -0.3f, 0.3f),
			fc(4, -0.5f, -0.25f, 0.35f, -0.5f, 0.25f, -0.35f, 0.85f, 0, -1.5f, 0.1f, 0.1f, -1.5f, -0.1f, -0.1f, 0.55f, 1f),
			fc(58, -0.55f, -0.25f, 0.35f, -0.55f, 0.25f, -0.35f, 0.8f, 0, -1.5f, 0.1f, 0.1f, -1.5f, -0.1f, -0.1f, 0.45f, 1f),
			fc(62, -0.9f, 0.3f, 0.8f, -0.9f, -0.3f, -0.8f, 0.1f, 0, -0.2f, 0, 0, 0.1f, 0, 0, -0.1f, 0.2f),
			restC(68) };
	// X: Terrain Surf -- side-on surfer stance riding the wave, knees bent, arms out for balance, held while it runs
	private static final float[][] SURF = {
			restC(0),
			fc(3, -0.5f, 0, 0.3f, 0.2f, 0, -0.3f, 0.45f, 0.2f, -0.6f, 0, 0, 0.3f, 0, 0, 0.3f, 1f),
			fc(7, -0.35f, 0.2f, 1.05f, -0.2f, -0.2f, -0.95f, 0.22f, 0.75f, -0.45f, 0, 0, 0.4f, 0, 0, -0.1f, 0.55f),
			fc(20, -0.45f, 0.2f, 1.15f, -0.1f, -0.2f, -0.85f, 0.25f, 0.8f, -0.5f, 0, 0, 0.45f, 0, 0, -0.1f, 0.6f),
			fc(31, -0.3f, 0.2f, 1.0f, -0.25f, -0.2f, -1.0f, 0.2f, 0.7f, -0.4f, 0, 0, 0.35f, 0, 0, -0.1f, 0.5f),
			restC(37) };
	// tap X: Ground Shift -- he stands perfectly still, feet together, arms a little out: the floor carries him
	private static final float[][] SHIFT = {
			restC(0),
			fc(2, 0.25f, 0, 0.35f, 0.25f, 0, -0.35f, -0.08f, 0, 0, 0, 0.04f, 0, 0, -0.04f, -0.05f, 0.25f),
			fc(8, 0.2f, 0, 0.4f, 0.2f, 0, -0.4f, -0.06f, 0, 0, 0, 0.04f, 0, 0, -0.04f, -0.05f, 0.2f),
			restC(13) };
	// Fusion ending: the stone falls away and he sags
	private static final float[][] FUSION_END = {
			fc(0, 0.1f, 0, 0.6f, 0.1f, 0, -0.6f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.35f, 0.3f),
			restC(10) };
	// the bare-hand touch on a punch (and Disassembly mining): a short palm push after the swing
	private static final float[][] TOUCH = {
			restC(0),
			fc(3, -1.5f, -0.1f, 0, 0.2f, 0, -0.1f, 0.1f, -0.25f, 0, 0, 0, 0, 0, 0, 0, 0),
			restC(8) };
	// Shift+V with a damaged item in the off hand: Mend -- it is cupped in both hands before the chest, head bowed over it
	private static final float[][] MEND = {
			restC(0),
			fc(4, -1.1f, 0.55f, 0, -1.1f, -0.55f, 0, 0.12f, 0, 0, 0, 0, 0, 0, 0, 0.45f, 0),
			fc(14, -1.15f, 0.5f, 0, -1.15f, -0.5f, 0, 0.12f, 0, 0, 0, 0, 0, 0, 0, 0.5f, 0),
			restC(20) };
	// hold V: Restore -- both palms pushed out, then swept slowly wide as the broken ground rebuilds
	private static final float[][] RESTORE = {
			restC(0),
			fc(4, -1.45f, 0.2f, 0, -1.45f, -0.2f, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, -0.1f, 0),
			fc(16, -1.45f, -0.25f, 0.85f, -1.45f, 0.25f, -0.85f, -0.05f, 0, 0, 0, 0, 0, 0, 0, -0.2f, 0),
			fc(26, -1.4f, -0.3f, 0.95f, -1.4f, 0.3f, -0.95f, -0.05f, 0, 0, 0, 0, 0, 0, 0, -0.2f, 0),
			restC(32) };

	/** The Plus Ultra hold: crouched low, both palms hovering over the ground, trembling (the ground already crawling). */
	private static final float[] HOLD = QuirkKeyframes.fc(0, -0.75f, -0.2f, 0.25f, -0.75f, 0.2f, -0.25f, 0.6f, 0, -1.1f, 0, 0.08f, 0.3f, 0, -0.08f,
			0.4f, 0.85f);

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
		} else if (animId == Overhaul.ANIM_ULTIMATE) {
			return ULTIMATE;
		} else if (animId == Overhaul.ANIM_SURF) {
			return SURF;
		} else if (animId == Overhaul.ANIM_FUSION_END) {
			return FUSION_END;
		} else if (animId == Overhaul.ANIM_TOUCH) {
			return TOUCH;
		} else if (animId == Overhaul.ANIM_THORN) {
			return THORN;
		} else if (animId == Overhaul.ANIM_SHRAPNEL) {
			return SHRAPNEL;
		} else if (animId == Overhaul.ANIM_SHIFT) {
			return SHIFT;
		} else if (animId == Overhaul.ANIM_MEND) {
			return MEND;
		} else if (animId == Overhaul.ANIM_RESTORE) {
			return RESTORE;
		}
		return null;
	}

	public static int length(int animId) {
		float[][] f = frames(animId);
		return f == null ? 0 : (int) f[f.length - 1][0];
	}

	/** The Plus Ultra hold (the kit's own stance). */
	static void hold(HumanoidModel<?> m, float charge, float t) {
		float[] p = HOLD.clone();
		float shake = Mth.sin(t * 2.9f) * 0.035f * (0.4f + charge);
		p[QuirkKeyframes.R_ARM_X] += shake;
		p[QuirkKeyframes.L_ARM_X] -= shake;
		apply(m, p, Math.min(1f, 0.3f + charge));
	}

	private static void apply(HumanoidModel<?> m, float[] p, float w) {
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, p[1]);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, p[2]);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, p[3]);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, p[4]);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, p[5]);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, p[6]);
		com.projecthero.mod.client.HipBend.pitch(m, w, p[7]);
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8]);
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, p[QuirkKeyframes.R_LEG_X]);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, p[QuirkKeyframes.L_LEG_X]);
		QuirkKeyframes.legTwist(m, p, w);
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[QuirkKeyframes.HEAD_X]);
		QuirkKeyframes.crouch(m, w * p[QuirkKeyframes.CROUCH]);
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		if (!Overhaul.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = player.tickCount + partial;
		if (Overhaul.fused(player) && !Overhaul.ultimate(player)) {
			// the heavy stone stance: arms held out from the plated body, a slow breathing sway
			float sw = Mth.sin(t * 0.08f) * 0.05f;
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
			if (s.animId == Overhaul.ANIM_THORN && tick > 6f && Overhaul.thornAiming(player)) {
				// v0.17.1: kneeling, palms pressed, rising slowly while he aims the forest (frames 6..30, ping-pong)
				float x = (tick - 6f) % 48f;
				tick = 6f + (x < 24f ? x : 48f - x);
			} else if (s.animId == Overhaul.ANIM_THORN && tick > 6f && Overhaul.holdCharge(player, AbilitySlot.SLOT_2, OverhaulConfig.HOLD_TICKS) >= 0f) {
				tick = 6f; // still deciding between the tap and the hold: palm down
			}
			if (s.animId == Overhaul.ANIM_ULTIMATE && tick > 4f && tick < 58f && Overhaul.ultimate(player)) {
				tick = 4f + ((tick - 4f) % 6f) * 0.2f; // pressed down hard, a tremor through the arms
			}
			float[] p = QuirkKeyframes.sample(f, tick);
			if (p != null) {
				float end = f[f.length - 1][0];
				float w = Math.max(0f, Math.min(1f, Math.min(tick / 1.5f + 0.2f, (end - tick) / 3f)));
				if (s.animId == Overhaul.ANIM_ULTIMATE && Overhaul.ultimate(player)) {
					float tr = Mth.sin(t * 3.1f) * 0.03f;
					p[QuirkKeyframes.R_ARM_X] += tr;
					p[QuirkKeyframes.L_ARM_X] -= tr;
				}
				apply(m, p, w);
			}
		}
		// hold V (before Restore fires): the palms come up and out, gathering
		float restore = Overhaul.holdCharge(player, AbilitySlot.SLOT_5, OverhaulConfig.RESTORE_HOLD_TICKS);
		if (restore > 0.15f && (s == null || s.animId != Overhaul.ANIM_RESTORE)) {
			float k = Math.min(1f, (restore - 0.15f) / 0.5f);
			m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -1.35f);
			m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -1.35f);
			m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0.25f * restore);
			m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -0.25f * restore);
		}
		m.hat.copyFrom(m.head);
	}
}
