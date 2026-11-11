package com.projecthero.mod.client.saiyan.kit;

import static com.projecthero.mod.client.quirk.QuirkKeyframes.AIM;
import static com.projecthero.mod.client.quirk.QuirkKeyframes.f;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.saiyan.kit.SaiyanKit;
import com.projecthero.mod.saiyan.kit.SaiyanKitConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Saiyan revamp: the move kit's body poses.
 * <ul>
 *   <li><b>Third person</b> ({@code mixin.SaiyanKitPoseMixin}): keyframe tables in {@link QuirkKeyframes}' 16-column format
 *       (radians; an arm X of {@link QuirkKeyframes#AIM} points the arm along the look). Written for a right-handed Saiyan
 *       and mirrored for a left-handed one.</li>
 *   <li><b>Whole-body turns</b> ({@code mixin.SaiyanKitSpinMixin}): the spinning finisher, the back flip of the Rising Dragon
 *       Kick, the forward lean of a dash or a rush.</li>
 *   <li><b>First person</b> ({@code mixin.SaiyanKitFirstPersonMixin}): {@link QuirkFirstPersonPose}'s 13-column arm clips
 *       (degrees and blocks). They keep to the edges of the screen so the view stays clear.</li>
 * </ul>
 */
public final class SaiyanKitPose {
	private SaiyanKitPose() {
	}

	// guard: fists up in front of the chest
	private static final float GRX = -1.25f, GRY = -0.35f, GLX = -1.35f, GLY = 0.45f;

	private static final Map<Integer, float[][]> TP = new HashMap<>();
	private static final Map<Integer, float[][]> FP = new HashMap<>();
	private static final Map<Integer, Boolean> FP_TWO = new HashMap<>();

	static {
		// ---- third person: {tick, rArm xyz, lArm xyz, body x y, rLeg xyz, lLeg xyz, head x}
		TP.put(SaiyanKit.ANIM_JAB_R, new float[][] {
				f(0, GRX, GRY, 0, GLX, GLY, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0),
				f(1.5f, AIM, -0.05f, 0, -1.4f, 0.5f, 0, 0.12f, 0.35f, 0.15f, 0, 0, -0.2f, 0, 0, 0),
				f(3.5f, AIM, -0.05f, 0, -1.4f, 0.5f, 0, 0.12f, 0.3f, 0.15f, 0, 0, -0.2f, 0, 0, 0),
				f(6, GRX, GRY, 0, GLX, GLY, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0) });
		TP.put(SaiyanKit.ANIM_JAB_L, new float[][] {
				f(0, GRX, GRY, 0, GLX, GLY, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0),
				f(1.5f, -1.3f, -0.5f, 0, AIM, 0.05f, 0, 0.12f, -0.35f, -0.2f, 0, 0, 0.15f, 0, 0, 0),
				f(3.5f, -1.3f, -0.5f, 0, AIM, 0.05f, 0, 0.12f, -0.3f, -0.2f, 0, 0, 0.15f, 0, 0, 0),
				f(6, GRX, GRY, 0, GLX, GLY, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0) });
		// roundhouse with the right leg, arms tucked
		TP.put(SaiyanKit.ANIM_KICK, new float[][] {
				f(0, GRX, GRY, 0, GLX, GLY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(2, -0.9f, -0.2f, 0.3f, -0.4f, 0.2f, -0.7f, -0.15f, 0.5f, -1.0f, 0.3f, 0.8f, 0.1f, 0, 0, 0),
				f(4, -0.9f, -0.2f, 0.3f, -0.3f, 0.2f, -0.8f, -0.2f, 0.7f, -1.45f, 0.6f, 1.1f, 0.1f, 0, -0.1f, 0),
				f(8, GRX, GRY, 0, GLX, GLY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) });
		// left uppercut
		TP.put(SaiyanKit.ANIM_HOOK, new float[][] {
				f(0, GRX, GRY, 0, GLX, GLY, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0),
				f(1, -1.2f, -0.4f, 0, -0.6f, 0.1f, 0, 0.25f, -0.2f, 0, 0, 0, 0.2f, 0, 0, 0.1f),
				f(3, -1.3f, -0.45f, 0, -2.3f, 0.35f, -0.1f, -0.1f, -0.45f, -0.1f, 0, 0, 0.1f, 0, 0, -0.15f),
				f(6, GRX, GRY, 0, GLX, GLY, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0) });
		// the finisher: a spin (SaiyanKitSpinMixin) into a straight side kick
		TP.put(SaiyanKit.ANIM_FINISH, new float[][] {
				f(0, GRX, GRY, 0, GLX, GLY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(3, -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.1f, 0, -0.5f, 0, 0.3f, 0.2f, 0, 0, 0),
				f(6, -0.3f, 0, 1.1f, -0.6f, 0, -0.9f, -0.3f, 0, -1.6f, 0, 0.35f, 0.15f, 0, -0.1f, 0),
				f(9, -0.3f, 0, 1.1f, -0.6f, 0, -0.9f, -0.3f, 0, -1.6f, 0, 0.35f, 0.15f, 0, -0.1f, 0),
				f(12, GRX, GRY, 0, GLX, GLY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) });
		// Rising Dragon Kick: a back flip (spin mixin) with the right leg driving straight up
		TP.put(SaiyanKit.ANIM_FLIP_KICK, new float[][] {
				f(0, -0.4f, 0, 0.3f, -0.4f, 0, -0.3f, 0.2f, 0, 0.3f, 0, 0, 0.3f, 0, 0, 0),
				f(2, 0.5f, 0, 0.6f, 0.5f, 0, -0.6f, -0.2f, 0, -2.7f, 0, 0.1f, -0.5f, 0, 0, 0),
				f(7, 0.6f, 0, 0.7f, 0.6f, 0, -0.7f, -0.2f, 0, -2.6f, 0, 0.1f, -0.9f, 0, 0, 0),
				f(10, -0.5f, 0, 0.5f, -0.5f, 0, -0.5f, 0, 0, -0.6f, 0, 0, -0.4f, 0, 0, 0) });
		TP.put(SaiyanKit.ANIM_WHIFF_KICK, TP.get(SaiyanKit.ANIM_FLIP_KICK));
		// the axe kick: the heel high over the head, then down
		TP.put(SaiyanKit.ANIM_AXE_KICK, new float[][] {
				f(0, -0.5f, 0, 0.5f, -0.5f, 0, -0.5f, -0.1f, 0, -0.6f, 0, 0, -0.4f, 0, 0, 0),
				f(2.5f, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, -0.35f, 0, -2.9f, 0, 0.05f, 0.1f, 0, 0, 0),
				f(3.5f, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, 0.3f, 0, -0.5f, 0, 0.05f, 0.2f, 0, 0, 0.2f),
				f(8, -0.6f, 0, 0.5f, -0.6f, 0, -0.5f, 0.2f, 0, -0.4f, 0, 0, 0.1f, 0, 0, 0.1f),
				f(11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) });
		TP.put(SaiyanKit.ANIM_BLAST_R, new float[][] {
				f(0, -1.2f, -0.2f, 0, -1.1f, 0.3f, 0, 0.05f, 0.1f, 0, 0, 0, 0, 0, 0, 0),
				f(1, AIM, -0.1f, 0, -1.1f, 0.4f, 0, 0.08f, 0.35f, 0, 0, 0, 0, 0, 0, 0),
				f(3.5f, AIM, -0.1f, 0, -1.1f, 0.4f, 0, 0.08f, 0.3f, 0, 0, 0, 0, 0, 0, 0),
				f(6, -1.2f, -0.2f, 0, -1.1f, 0.3f, 0, 0.05f, 0.1f, 0, 0, 0, 0, 0, 0, 0) });
		TP.put(SaiyanKit.ANIM_BLAST_L, new float[][] {
				f(0, -1.1f, -0.3f, 0, -1.2f, 0.2f, 0, 0.05f, -0.1f, 0, 0, 0, 0, 0, 0, 0),
				f(1, -1.1f, -0.4f, 0, AIM, 0.1f, 0, 0.08f, -0.35f, 0, 0, 0, 0, 0, 0, 0),
				f(3.5f, -1.1f, -0.4f, 0, AIM, 0.1f, 0, 0.08f, -0.3f, 0, 0, 0, 0, 0, 0, 0),
				f(6, -1.1f, -0.3f, 0, -1.2f, 0.2f, 0, 0.05f, -0.1f, 0, 0, 0, 0, 0, 0, 0) });
		// the Kamehameha fires: both palms along the look, heels of the hands together, a wide stance
		TP.put(SaiyanKit.ANIM_KAME_FIRE, new float[][] {
				f(0, AIM, -0.28f, 0, AIM, 0.28f, 0, 0.1f, 0, -0.15f, 0, 0.3f, 0.25f, 0, -0.3f, 0),
				f(SaiyanKitConfig.BEAM_FIRE_TICKS + 4, AIM, -0.28f, 0, AIM, 0.28f, 0, 0.1f, 0, -0.15f, 0, 0.3f, 0.25f, 0, -0.3f, 0) });
		// Meteor Smash: both fists high over the head, then the hammer down
		TP.put(SaiyanKit.ANIM_SMASH, new float[][] {
				f(0, -2.9f, -0.35f, 0, -2.9f, 0.35f, 0, -0.2f, 0, -0.3f, 0, 0, 0.2f, 0, 0, -0.2f),
				f(4.2f, -3.05f, -0.4f, 0, -3.05f, 0.4f, 0, -0.3f, 0, -0.4f, 0, 0, 0.3f, 0, 0, -0.2f),
				f(5.5f, -0.5f, -0.35f, 0, -0.5f, 0.35f, 0, 0.55f, 0, -0.6f, 0, 0, 0.1f, 0, 0, 0.3f),
				f(9, -0.6f, -0.3f, 0, -0.6f, 0.3f, 0, 0.45f, 0, -0.5f, 0, 0, 0.1f, 0, 0, 0.3f),
				f(13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) });
		// Ki Dash: arms swept back, legs trailing (the lean is the spin mixin's)
		TP.put(SaiyanKit.ANIM_DASH, new float[][] {
				f(0, 0.6f, 0, 0.25f, 0.6f, 0, -0.25f, 0, 0, 0.3f, 0, 0, 0.5f, 0, 0, -0.3f),
				f(SaiyanKitConfig.DASH_TICKS + 2, 0.7f, 0, 0.3f, 0.7f, 0, -0.3f, 0, 0, 0.35f, 0, 0, 0.6f, 0, 0, -0.35f),
				f(SaiyanKitConfig.DASH_TICKS + 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) });
		TP.put(SaiyanKit.ANIM_RUSH_FINISH, new float[][] {
				f(0, -1.4f, -0.2f, 0, -1.2f, 0.4f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0),
				f(1.5f, AIM, 0, 0, 0.5f, 0.2f, -0.3f, 0.15f, 0.45f, 0.2f, 0, 0, -0.3f, 0, 0, 0),
				f(5, AIM, 0, 0, 0.5f, 0.2f, -0.3f, 0.15f, 0.45f, 0.2f, 0, 0, -0.3f, 0, 0, 0),
				f(9, GRX, GRY, 0, GLX, GLY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) });
		// Explosive Wave: crossed arms and a crouch, then flung wide, chest out, head back
		TP.put(SaiyanKit.ANIM_WAVE, new float[][] {
				fc(0, -1.2f, 0.7f, 0, -1.2f, -0.7f, 0, 0.35f, 0, -0.3f, 0, 0.2f, -0.3f, 0, -0.2f, 0.25f, 0.6f),
				fc(2, -1.3f, 0.8f, 0, -1.3f, -0.8f, 0, 0.4f, 0, -0.35f, 0, 0.25f, -0.35f, 0, -0.25f, 0.3f, 0.7f),
				fc(3.5f, -0.4f, 0, 1.6f, -0.4f, 0, -1.6f, -0.25f, 0, 0, 0, 0.35f, 0, 0, -0.35f, -0.35f, 0f),
				fc(9, -0.4f, 0, 1.5f, -0.4f, 0, -1.5f, -0.2f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.3f, 0f),
				fc(13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0f) });
		// Solar Flare: both hands flat at the forehead, fingers spread
		TP.put(SaiyanKit.ANIM_FLARE, new float[][] {
				f(0, -1.2f, 0, 0, -1.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(2, -2.55f, -0.75f, 0.2f, -2.55f, 0.75f, -0.2f, -0.15f, 0, 0.1f, 0, 0.25f, 0.1f, 0, -0.25f, -0.2f),
				f(9, -2.55f, -0.75f, 0.2f, -2.55f, 0.75f, -0.2f, -0.15f, 0, 0.1f, 0, 0.25f, 0.1f, 0, -0.25f, -0.2f),
				f(13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) });

		// ---- first person: {tick, main pitch yaw roll x y z, off pitch yaw roll x y z}
		fp(SaiyanKit.ANIM_JAB_R, false, fpf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), fpf(1.5f, -6, 14, 0, 0.14f, 0.1f, -0.38f, 0, 0, 0, 0, 0, 0),
				fpf(3, -6, 14, 0, 0.14f, 0.1f, -0.38f, 0, 0, 0, 0, 0, 0), fpf(6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
		fp(SaiyanKit.ANIM_JAB_L, true, fpf(0, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0), fpf(1.5f, 10, 0, 0, 0, -0.08f, 0, -6, 14, 0, 0.14f, 0.1f, -0.38f),
				fpf(3, 10, 0, 0, 0, -0.08f, 0, -6, 14, 0, 0.14f, 0.1f, -0.38f), fpf(6, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0));
		fp(SaiyanKit.ANIM_HOOK, true, fpf(0, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0), fpf(1, 8, 0, 0, 0, -0.06f, 0, 15, 10, 0, 0.05f, -0.2f, -0.05f),
				fpf(3, 8, 0, 0, 0, -0.06f, 0, -30, 20, -10, 0.16f, 0.18f, -0.25f), fpf(6, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0));
		float[][] down = { fpf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), fpf(2, 25, -10, 0, -0.05f, -0.18f, 0.05f, 0, 0, 0, 0, 0, 0),
				fpf(8, 25, -10, 0, -0.05f, -0.18f, 0.05f, 0, 0, 0, 0, 0, 0), fpf(11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
		for (int id : new int[] { SaiyanKit.ANIM_KICK, SaiyanKit.ANIM_FINISH, SaiyanKit.ANIM_FLIP_KICK, SaiyanKit.ANIM_WHIFF_KICK,
				SaiyanKit.ANIM_AXE_KICK, SaiyanKit.ANIM_DASH }) {
			FP.put(id, down);
			FP_TWO.put(id, false);
		}
		fp(SaiyanKit.ANIM_BLAST_R, false, fpf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), fpf(1, -8, 16, -20, 0.16f, 0.08f, -0.32f, 0, 0, 0, 0, 0, 0),
				fpf(3, -8, 16, -20, 0.16f, 0.08f, -0.32f, 0, 0, 0, 0, 0, 0), fpf(6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
		fp(SaiyanKit.ANIM_BLAST_L, true, fpf(0, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0),
				fpf(1, 10, 0, 0, 0, -0.06f, 0, -8, 16, -20, 0.16f, 0.08f, -0.32f), fpf(3, 10, 0, 0, 0, -0.06f, 0, -8, 16, -20, 0.16f, 0.08f, -0.32f),
				fpf(6, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0));
		float kf = SaiyanKitConfig.BEAM_FIRE_TICKS + 4;
		fp(SaiyanKit.ANIM_KAME_FIRE, true, fpf(0, -4, 26, -25, 0.3f, 0.02f, -0.3f, -4, 26, -25, 0.3f, 0.02f, -0.3f),
				fpf(kf, -4, 26, -25, 0.3f, 0.02f, -0.3f, -4, 26, -25, 0.3f, 0.02f, -0.3f));
		fp(SaiyanKit.ANIM_SMASH, true, fpf(0, -70, 20, 0, 0.15f, 0.55f, 0.1f, -70, 20, 0, 0.15f, 0.55f, 0.1f),
				fpf(4.2f, -75, 22, 0, 0.15f, 0.6f, 0.1f, -75, 22, 0, 0.15f, 0.6f, 0.1f), fpf(5.5f, 5, 25, 0, 0.25f, -0.05f, -0.2f, 5, 25, 0, 0.25f, -0.05f, -0.2f),
				fpf(9, 15, 20, 0, 0.2f, -0.12f, -0.1f, 15, 20, 0, 0.2f, -0.12f, -0.1f), fpf(13, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0));
		fp(SaiyanKit.ANIM_RUSH_FINISH, false, fpf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), fpf(1.5f, -8, 16, 0, 0.16f, 0.1f, -0.4f, 0, 0, 0, 0, 0, 0),
				fpf(5, -8, 16, 0, 0.16f, 0.1f, -0.4f, 0, 0, 0, 0, 0, 0), fpf(9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
		fp(SaiyanKit.ANIM_WAVE, true, fpf(0, 5, 30, 0, 0.25f, -0.05f, 0, 5, 30, 0, 0.25f, -0.05f, 0),
				fpf(2, 8, 35, 0, 0.3f, -0.08f, 0, 8, 35, 0, 0.3f, -0.08f, 0), fpf(3.5f, -10, -35, 30, -0.25f, 0.05f, 0, -10, -35, 30, -0.25f, 0.05f, 0),
				fpf(9, -8, -30, 25, -0.22f, 0.04f, 0, -8, -30, 25, -0.22f, 0.04f, 0), fpf(13, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0));
		fp(SaiyanKit.ANIM_FLARE, true, fpf(0, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0),
				fpf(2, -50, 18, 0, 0.12f, 0.42f, 0.05f, -50, 18, 0, 0.12f, 0.42f, 0.05f), fpf(9, -50, 18, 0, 0.12f, 0.42f, 0.05f, -50, 18, 0, 0.12f, 0.42f, 0.05f),
				fpf(13, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0));
	}

	private static float[] fc(float tick, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float rly,
			float rlz, float llx, float lly, float llz, float hx, float crouch) {
		return QuirkKeyframes.fc(tick, rax, ray, raz, lax, lay, laz, bx, by, rlx, rly, rlz, llx, lly, llz, hx, crouch);
	}

	private static float[] fpf(float tick, float mp, float my, float mr, float mx, float mY, float mz, float op, float oy, float or, float ox, float oY,
			float oz) {
		return new float[] { tick, mp, my, mr, mx, mY, mz, op, oy, or, ox, oY, oz };
	}

	private static void fp(int id, boolean two, float[]... frames) {
		FP.put(id, frames);
		FP_TWO.put(id, two);
	}

	// ---------------------------------------------------------------- third person

	/** From {@code HumanoidModel.setupAnim}'s tail (every viewer). */
	public static void apply(Player player, HumanoidModel<?> model) {
		if (player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		boolean left = player.getMainArm() == HumanoidArm.LEFT;
		float aimX = -Mth.HALF_PI + model.head.xRot, aimY = model.head.yRot;
		float t = player.tickCount + partial;
		// held poses: the Kamehameha's charge (cupped hands at his side) and its fire
		if (SaiyanKitClient.beamCharging(player)) {
			float c = SaiyanKitClient.beamCharge(player, partial);
			float shake = 0.02f * c * Mth.sin(t * 3.7f);
			float[] p = f(0, 0.55f + shake, -0.35f, 0.3f, -0.45f, -1.0f, 0.1f, 0.15f, -0.45f, -0.15f, 0, 0.3f, 0.25f, 0, -0.3f, 0.05f);
			put(model, p, 1f, left);
			return;
		}
		if (SaiyanKitClient.beamFiring(player)) {
			float[] p = QuirkKeyframes.sample(TP.get(SaiyanKit.ANIM_KAME_FIRE), 1f, aimX, aimY);
			put(model, p, 1f, left);
			return;
		}
		float[] a = SaiyanKitClient.anim(player, partial);
		if (a == null) {
			return;
		}
		int id = (int) a[0];
		float age = a[1];
		if (id == SaiyanKit.ANIM_RUSH) {
			rushPunches(model, age, aimX, left);
			return;
		}
		if (id == SaiyanKit.ANIM_RAIN) {
			rainPose(model, age, left);
			return;
		}
		float[][] frames = TP.get(id);
		if (frames == null) {
			return;
		}
		float[] p = QuirkKeyframes.sample(frames, age, aimX, aimY);
		if (p == null) {
			return;
		}
		put(model, p, QuirkKeyframes.weight(age, QuirkKeyframes.length(frames)), left);
	}

	/** Applies a frame, mirrored for a left-handed Saiyan (the right and left limbs swap, yaws and rolls flip). */
	private static void put(HumanoidModel<?> model, float[] p, float w, boolean left) {
		if (left) {
			float[] m = p.clone();
			m[1] = p[4];
			m[2] = -p[5];
			m[3] = -p[6];
			m[4] = p[1];
			m[5] = -p[2];
			m[6] = -p[3];
			m[8] = -p[8];
			m[9] = p[12];
			m[10] = -p[13];
			m[11] = -p[14];
			m[12] = p[9];
			m[13] = -p[10];
			m[14] = -p[11];
			p = m;
		}
		QuirkKeyframes.apply(model, p, w);
	}

	/** Dragon Rush: fists in turn faster than the eye, along the look. */
	private static void rushPunches(HumanoidModel<?> model, float age, float aimX, boolean left) {
		float len = SaiyanKitConfig.RUSH_TICKS + 2;
		if (age > len) {
			return;
		}
		float ph = (age % 3f) / 3f;
		float k1 = Mth.sin(ph * Mth.PI), k2 = Mth.sin(((ph + 0.5f) % 1f) * Mth.PI);
		float base = -0.9f;
		float[] p = f(0, Mth.lerp(k1, base, aimX), -0.15f + 0.15f * k1, 0, Mth.lerp(k2, base, aimX), 0.15f - 0.15f * k2, 0, 0.15f,
				0.3f * (k1 - k2), 0.4f, 0, 0, 0.7f, 0, 0, -0.3f);
		put(model, p, QuirkKeyframes.weight(age, len), left);
	}

	/** Ki Rain: the leap with knees up, then palms thrust down in turn. */
	private static void rainPose(HumanoidModel<?> model, float age, boolean left) {
		float len = SaiyanKitConfig.RAIN_FIRE_AT + SaiyanKitConfig.RAIN_EVERY * SaiyanKitConfig.RAIN_SHOTS + 4;
		if (age > len) {
			return;
		}
		float[] p;
		if (age < SaiyanKitConfig.RAIN_FIRE_AT) {
			p = f(0, -2.6f, -0.2f, 0.3f, -2.6f, 0.2f, -0.3f, -0.1f, 0, -0.9f, 0, 0.1f, -0.6f, 0, -0.1f, 0.4f);
		} else {
			float ph = ((age - SaiyanKitConfig.RAIN_FIRE_AT) % (SaiyanKitConfig.RAIN_EVERY * 2f)) / (SaiyanKitConfig.RAIN_EVERY * 2f);
			float k1 = Mth.sin(ph * Mth.PI), k2 = Mth.sin(((ph + 0.5f) % 1f) * Mth.PI);
			p = f(0, -0.7f - 0.5f * k1, -0.2f, 0.15f, -0.7f - 0.5f * k2, 0.2f, -0.15f, 0.35f, 0.2f * (k1 - k2), -0.5f, 0, 0.15f, -0.2f, 0, -0.15f, 0.6f);
		}
		put(model, p, QuirkKeyframes.weight(age, len), left);
	}

	// ---------------------------------------------------------------- whole-body turns

	/** From {@code PlayerRenderer.setupRotations}'s tail: the whole body turned about its middle. */
	public static void rotate(Player player, PoseStack pose, float partial) {
		float[] a = SaiyanKitClient.anim(player, partial);
		if (a == null) {
			return;
		}
		int id = (int) a[0];
		float age = a[1];
		float spinY = 0f, pitchX = 0f;
		switch (id) {
			case SaiyanKit.ANIM_FINISH -> {
				if (age < 5f) {
					float k = age / 5f;
					spinY = -360f * k * k * (3f - 2f * k);
				}
			}
			case SaiyanKit.ANIM_FLIP_KICK -> {
				if (age < 9f) {
					float k = age / 9f;
					pitchX = -360f * k * k * (3f - 2f * k);
				}
			}
			case SaiyanKit.ANIM_DASH -> pitchX = 40f * lean(age, SaiyanKitConfig.DASH_TICKS + 2);
			case SaiyanKit.ANIM_RUSH -> pitchX = 50f * lean(age, SaiyanKitConfig.RUSH_TICKS + 1);
			case SaiyanKit.ANIM_SMASH -> pitchX = 22f * lean(age, 10f);
			default -> {
			}
		}
		if (spinY != 0f) {
			pose.mulPose(Axis.YP.rotationDegrees(spinY));
		}
		if (pitchX != 0f) {
			pose.translate(0.0f, 0.9f, 0.0f);
			pose.mulPose(Axis.XP.rotationDegrees(pitchX));
			pose.translate(0.0f, -0.9f, 0.0f);
		}
	}

	/** 0..1..0 over {@code len} ticks: in fast, held, out over the last 3. */
	private static float lean(float age, float len) {
		if (age < 0f || age > len + 3f) {
			return 0f;
		}
		return Math.min(1f, Math.min(age / 2f, (len + 3f - age) / 3f));
	}

	// ---------------------------------------------------------------- first person

	/** The local player's first-person arm clip this frame, or null (vanilla). */
	public static QuirkFirstPersonPose.Sample firstPerson(Player p, float partial) {
		float t = p.tickCount + partial;
		if (SaiyanKitClient.beamCharging(p)) {
			// both hands low at his right side, cupped round the ball
			float c = SaiyanKitClient.beamCharge(p, partial);
			float sh = 1.2f * c * Mth.sin(t * 3.7f);
			float[] v = fpf(0, 22 + sh, -12, 10, -0.08f, -0.2f, 0.08f, 20 + sh, 48, -15, 0.62f, -0.24f, 0.05f);
			return new QuirkFirstPersonPose.Sample(v, Math.min(1f, SaiyanKitClient.beamAge(p, partial) / 3f), true);
		}
		if (SaiyanKitClient.beamFiring(p)) {
			float[] v = QuirkKeyframes.sampleN(FP.get(SaiyanKit.ANIM_KAME_FIRE), 1f, QuirkFirstPersonPose.COLS);
			return new QuirkFirstPersonPose.Sample(v, 1f, true);
		}
		float[] a = SaiyanKitClient.anim(p, partial);
		if (a == null) {
			return null;
		}
		int id = (int) a[0];
		float age = a[1];
		if (id == SaiyanKit.ANIM_RUSH || id == SaiyanKit.ANIM_RAIN) {
			float len = id == SaiyanKit.ANIM_RUSH ? SaiyanKitConfig.RUSH_TICKS + 2
					: SaiyanKitConfig.RAIN_FIRE_AT + SaiyanKitConfig.RAIN_EVERY * SaiyanKitConfig.RAIN_SHOTS + 4;
			if (age > len) {
				return null;
			}
			float per = id == SaiyanKit.ANIM_RUSH ? 3f : SaiyanKitConfig.RAIN_EVERY * 2f;
			float ph = (age % per) / per;
			float k1 = Mth.sin(ph * Mth.PI), k2 = Mth.sin(((ph + 0.5f) % 1f) * Mth.PI);
			float dz = id == SaiyanKit.ANIM_RUSH ? -0.38f : -0.25f, dp = id == SaiyanKit.ANIM_RUSH ? -6f : 18f;
			float[] v = fpf(0, dp * k1 + 8 * (1 - k1), 14 * k1, 0, 0.14f * k1, 0.06f * k1 - 0.08f * (1 - k1), dz * k1,
					dp * k2 + 20 * (1 - k2), 14 * k2, 0, 0.14f * k2, 0.06f * k2 - 0.25f * (1 - k2), dz * k2);
			return new QuirkFirstPersonPose.Sample(v, QuirkKeyframes.weight(age, len), true);
		}
		float[][] frames = FP.get(id);
		if (frames == null) {
			return null;
		}
		float[] v = QuirkKeyframes.sampleN(frames, age, QuirkFirstPersonPose.COLS);
		if (v == null) {
			return null;
		}
		return new QuirkFirstPersonPose.Sample(v, QuirkKeyframes.weight(age, QuirkKeyframes.length(frames)), FP_TWO.getOrDefault(id, false));
	}
}
