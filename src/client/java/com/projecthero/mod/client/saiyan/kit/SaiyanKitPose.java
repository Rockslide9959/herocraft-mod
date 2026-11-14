package com.projecthero.mod.client.saiyan.kit;

import static com.projecthero.mod.client.quirk.QuirkKeyframes.AIM;
import static com.projecthero.mod.client.quirk.QuirkKeyframes.f;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.client.saiyan.SaiyanFx;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.kit.SaiyanKit;
import com.projecthero.mod.saiyan.kit.SaiyanKitConfig;
import com.projecthero.mod.saiyan.kit.SaiyanSpiritBomb;
import com.projecthero.mod.saiyan.kit.SpiritBombState;

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
 *       (degrees and blocks). v0.17.4: reworked with weight -- every strike winds up, snaps past its target, settles and
 *       recoils, with the other fist up in a guard that pulls back against it; the Kamehameha gathers, locks into the cupped
 *       hold, thrusts and fights the recoil; new clips for the Spirit Bomb, the power-up (V) and the transformation. They keep
 *       to the edges of the screen so the view stays clear.</li>
 * </ul>
 */
public final class SaiyanKitPose {
	private SaiyanKitPose() {
	}

	// guard: fists up in front of the chest
	private static final float GRX = -1.25f, GRY = -0.35f, GLX = -1.35f, GLY = 0.45f;

	// v0.17.4 first-person arm poses (pitch yaw roll x y z), declared before the clip tables that use them: vanilla's rest, the
	// off fist tucked out of sight, the guards, and the counter-pull of the guard fist while the other one strikes
	private static final float[] REST = { 0, 0, 0, 0, 0, 0 }, HIDE = { 25, 0, 0, 0, -0.3f, 0 };
	private static final float[] GUARD_M = { -10, 8, 14, 0.02f, 0.02f, 0.02f }, GUARD_O = { -12, 10, 14, 0.02f, 0.03f, 0.02f };
	private static final float[] GUARD_M_UP = { -12, 10, 16, 0.03f, 0.03f, 0f }, GUARD_O_UP = { -14, 12, 16, 0.03f, 0.04f, 0f };
	private static final float[] COUNTER_M = { -6, 6, 10, -0.02f, -0.02f, 0.07f }, COUNTER_O = { -6, 6, 10, -0.02f, -0.02f, 0.07f };
	private static final float[] COUNTER_M2 = { -8, 8, 12, -0.01f, -0.01f, 0.05f }, COUNTER_O2 = { -8, 8, 12, -0.01f, -0.01f, 0.05f };

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

		// ---- first person (v0.17.4 rework): {tick, main pitch yaw roll x y z, off pitch yaw roll x y z}. Every strike has a
		// wind-up (the fist chambers back), a snap past the target, a settle and a recoil before it eases home; the other fist
		// sits up in a guard and pulls back as the strike goes out, so both hands carry the weight. Nothing comes nearer the
		// camera than vanilla's own hand (z stays at or below ~0.1), and the hands keep to the screen's edges between strikes.
		fp(SaiyanKit.ANIM_JAB_R, true, fr(0, REST, GUARD_O), fr(1, 12, -6, 6, -0.05f, -0.07f, 0.09f, GUARD_O_UP),
				fr(2.2f, -10, 17, -16, 0.18f, 0.11f, -0.46f, COUNTER_O), fr(3.4f, -7, 15, -12, 0.15f, 0.09f, -0.4f, COUNTER_O2),
				fr(5.5f, 4, 3, 2, 0.03f, -0.04f, -0.06f, GUARD_O), fr(8, REST, GUARD_O));
		fp(SaiyanKit.ANIM_JAB_L, true, fr(0, GUARD_M, GUARD_O), frO(1, GUARD_M_UP, 12, -6, 6, -0.05f, -0.07f, 0.09f),
				frO(2.2f, COUNTER_M, -10, 17, -16, 0.18f, 0.11f, -0.46f), frO(3.4f, COUNTER_M2, -7, 15, -12, 0.15f, 0.09f, -0.4f),
				frO(5.5f, GUARD_M, 4, 3, 2, 0.03f, -0.04f, -0.06f), fr(8, GUARD_M, GUARD_O));
		// left uppercut: the off fist dips to load, then rips up through the centre and follows through high
		fp(SaiyanKit.ANIM_HOOK, true, fr(0, GUARD_M, GUARD_O), frO(1.2f, GUARD_M_UP, 28, -6, 10, -0.04f, -0.24f, 0.04f),
				frO(2.6f, COUNTER_M, -40, 22, -14, 0.17f, 0.22f, -0.27f), frO(3.6f, COUNTER_M, -48, 24, -18, 0.16f, 0.28f, -0.22f),
				frO(6.5f, GUARD_M, -8, 8, 8, 0.02f, 0.02f, 0f), fr(8, REST, GUARD_O));
		// the kicks: the arms swing out for balance against the leg
		fp(SaiyanKit.ANIM_KICK, true, fr(0, GUARD_M, GUARD_O), fpf(2, 30, -24, -20, -0.1f, -0.2f, 0.06f, -20, -18, 22, -0.12f, 0.06f, 0.02f),
				fpf(4, 34, -30, -24, -0.14f, -0.24f, 0.08f, -24, -22, 26, -0.14f, 0.08f, 0f),
				fpf(6, 24, -20, -16, -0.08f, -0.16f, 0.05f, -16, -14, 18, -0.08f, 0.04f, 0.01f), fr(8, REST, GUARD_O));
		fp(SaiyanKit.ANIM_FINISH, true, fr(0, GUARD_M, GUARD_O), fpf(2, 10, -40, -30, -0.25f, -0.05f, 0.05f, 10, -40, -30, -0.25f, -0.05f, 0.05f),
				fpf(5, 12, -42, -32, -0.26f, -0.06f, 0.05f, 12, -42, -32, -0.26f, -0.06f, 0.05f),
				fpf(7, 30, -20, -15, -0.08f, -0.2f, 0.06f, -18, -14, 20, -0.1f, 0.05f, 0f),
				fpf(9, 28, -18, -14, -0.07f, -0.18f, 0.05f, -16, -12, 18, -0.09f, 0.04f, 0f), fr(12, REST, GUARD_O));
		float[][] flip = { fr(0, REST, HIDE), fpf(2, -50, -10, -10, -0.06f, 0.32f, 0f, -50, -10, -10, -0.06f, 0.32f, 0f),
				fpf(4, -56, -12, -12, -0.06f, 0.36f, 0f, -56, -12, -12, -0.06f, 0.36f, 0f),
				fpf(7, 22, -12, 0, -0.05f, -0.2f, 0.04f, 22, -12, 0, -0.05f, -0.2f, 0.04f), fr(10, REST, HIDE) };
		fp(SaiyanKit.ANIM_FLIP_KICK, true, flip);
		fp(SaiyanKit.ANIM_WHIFF_KICK, true, flip);
		fp(SaiyanKit.ANIM_AXE_KICK, true, fr(0, GUARD_M, GUARD_O), fpf(2.5f, -25, -30, -25, -0.2f, 0.12f, 0.02f, -25, -30, -25, -0.2f, 0.12f, 0.02f),
				fpf(3.5f, 32, -10, 0, -0.04f, -0.24f, 0.04f, 32, -10, 0, -0.04f, -0.24f, 0.04f),
				fpf(5, 26, -10, 0, -0.04f, -0.2f, 0.03f, 26, -10, 0, -0.04f, -0.2f, 0.03f), fr(11, REST, HIDE));
		// Ki Dash: both arms swept back and down out of the way
		fp(SaiyanKit.ANIM_DASH, true, fr(0, REST, HIDE), fpf(2, 35, -14, -6, -0.08f, -0.34f, 0.1f, 35, -14, -6, -0.08f, -0.34f, 0.1f),
				fpf(SaiyanKitConfig.DASH_TICKS + 2, 38, -16, -6, -0.08f, -0.36f, 0.1f, 38, -16, -6, -0.08f, -0.36f, 0.1f),
				fr(SaiyanKitConfig.DASH_TICKS + 6, REST, HIDE));
		// Ki blasts: a palm thrust (heel of the hand first, palm turned out), a recoil that kicks the palm up, a settle
		fp(SaiyanKit.ANIM_BLAST_R, true, fr(0, REST, GUARD_O), fr(0.8f, 10, -4, 4, -0.04f, -0.05f, 0.08f, GUARD_O_UP),
				fr(2, -10, 17, -28, 0.17f, 0.09f, -0.36f, COUNTER_O), fr(2.8f, 4, 14, -24, 0.15f, 0.14f, -0.26f, COUNTER_O2),
				fr(4, -6, 15, -22, 0.15f, 0.09f, -0.31f, GUARD_O), fr(6, REST, GUARD_O));
		fp(SaiyanKit.ANIM_BLAST_L, true, fr(0, GUARD_M, GUARD_O), frO(0.8f, GUARD_M_UP, 10, -4, 4, -0.04f, -0.05f, 0.08f),
				frO(2, COUNTER_M, -10, 17, -28, 0.17f, 0.09f, -0.36f), frO(2.8f, COUNTER_M2, 4, 14, -24, 0.15f, 0.14f, -0.26f),
				frO(4, GUARD_M, -6, 15, -22, 0.15f, 0.09f, -0.31f), fr(6, GUARD_M, GUARD_O));
		// Meteor Smash: both fists climb overhead, hang at the top (anticipation), then hammer down past the target and bounce
		fp(SaiyanKit.ANIM_SMASH, true, fpf(0, -60, 18, 0, 0.12f, 0.45f, 0.08f, -60, 18, 0, 0.12f, 0.45f, 0.08f),
				fpf(3, -78, 22, 6, 0.15f, 0.62f, 0.08f, -78, 22, 6, 0.15f, 0.62f, 0.08f),
				fpf(4.4f, -80, 22, 8, 0.15f, 0.64f, 0.08f, -80, 22, 8, 0.15f, 0.64f, 0.08f),
				fpf(5.4f, 18, 26, 0, 0.25f, -0.14f, -0.22f, 18, 26, 0, 0.25f, -0.14f, -0.22f),
				fpf(6.6f, 8, 25, 0, 0.24f, -0.06f, -0.2f, 8, 25, 0, 0.24f, -0.06f, -0.2f),
				fpf(9, 12, 22, 0, 0.2f, -0.1f, -0.12f, 12, 22, 0, 0.2f, -0.1f, -0.12f), fr(13, REST, HIDE));
		// Dragon Rush's last blow: a huge chamber, an overshooting straight, a long recoil
		fp(SaiyanKit.ANIM_RUSH_FINISH, true, fr(0, REST, GUARD_O), fr(1, 14, -8, 8, -0.06f, -0.08f, 0.1f, GUARD_O_UP),
				fr(2.2f, -10, 18, -18, 0.18f, 0.12f, -0.5f, COUNTER_O), fr(3.5f, -8, 16, -14, 0.16f, 0.1f, -0.44f, COUNTER_O2),
				fr(6, 5, 4, 0, 0.03f, -0.05f, -0.06f, GUARD_O), fr(9, REST, GUARD_O));
		// Explosive Wave: arms cross tight at the chest (trembling: firstPerson adds it), then fling wide past the edges, settle
		fp(SaiyanKit.ANIM_WAVE, true, fr(0, REST, HIDE), fpf(2, -28, 38, 20, 0.26f, 0.1f, -0.04f, -28, 38, 20, 0.26f, 0.1f, -0.04f),
				fpf(3.5f, -14, -40, 34, -0.27f, 0.06f, 0f, -14, -40, 34, -0.27f, 0.06f, 0f),
				fpf(5, -10, -33, 28, -0.23f, 0.05f, 0f, -10, -33, 28, -0.23f, 0.05f, 0f),
				fpf(9, -8, -30, 25, -0.22f, 0.04f, 0f, -8, -30, 25, -0.22f, 0.04f, 0f), fr(13, REST, HIDE));
		// Solar Flare: a quick dip, then both hands snap up to the forehead, fingers spread
		fp(SaiyanKit.ANIM_FLARE, true, fr(0, REST, HIDE), fpf(1, 12, 10, 0, 0.02f, -0.08f, 0.02f, 12, 10, 0, 0.02f, -0.08f, 0.02f),
				fpf(2.2f, -54, 20, 4, 0.13f, 0.45f, 0.04f, -54, 20, 4, 0.13f, 0.45f, 0.04f),
				fpf(3, -50, 18, 0, 0.12f, 0.42f, 0.05f, -50, 18, 0, 0.12f, 0.42f, 0.05f),
				fpf(9, -50, 18, 0, 0.12f, 0.42f, 0.05f, -50, 18, 0, 0.12f, 0.42f, 0.05f), fr(13, REST, HIDE));
	}

	/** A frame from two 6-value arm poses. */
	private static float[] fr(float tick, float[] m, float[] o) {
		return fpf(tick, m[0], m[1], m[2], m[3], m[4], m[5], o[0], o[1], o[2], o[3], o[4], o[5]);
	}

	/** A frame: the main arm spelled out, the off arm from a pose. */
	private static float[] fr(float tick, float mp, float my, float mr, float mx, float mY, float mz, float[] o) {
		return fpf(tick, mp, my, mr, mx, mY, mz, o[0], o[1], o[2], o[3], o[4], o[5]);
	}

	/** A frame: the main arm from a pose, the off arm spelled out. */
	private static float[] frO(float tick, float[] m, float op, float oy, float or, float ox, float oY, float oz) {
		return fpf(tick, m[0], m[1], m[2], m[3], m[4], m[5], op, oy, or, ox, oY, oz);
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

	// ---------------------------------------------------------------- first person (v0.17.4 rework)

	/** The local player's power-up (V) hold: when it began and ended (tickCount), for its wind-up and its fade-out. */
	private static int chargeOn = Integer.MIN_VALUE / 2, chargeOff = Integer.MIN_VALUE / 2;
	private static boolean wasCharging;

	/** The local player's first-person arm clip this frame, or null (vanilla). */
	public static QuirkFirstPersonPose.Sample firstPerson(Player p, float partial) {
		float t = p.tickCount + partial;
		boolean charging = SaiyanFx.charging(p);
		if (charging != wasCharging) {
			if (charging) {
				chargeOn = p.tickCount;
			} else {
				chargeOff = p.tickCount;
			}
			wasCharging = charging;
		}
		QuirkFirstPersonPose.Sample bomb = spiritBomb(p, partial, t);
		if (bomb != null) {
			return bomb;
		}
		if (SaiyanKitClient.beamCharging(p)) {
			return kameCharge(p, partial, t);
		}
		if (SaiyanKitClient.beamFiring(p)) {
			return kameFire(p, partial, t);
		}
		QuirkFirstPersonPose.Sample move = moveClip(p, partial, t);
		if (move != null) {
			return move;
		}
		QuirkFirstPersonPose.Sample tf = transform(p, partial, t);
		if (tf != null) {
			return tf;
		}
		return powerUp(p, t, charging);
	}

	/** The synced move anims (combo, blasts, kicks, the rush, the rain...). */
	private static QuirkFirstPersonPose.Sample moveClip(Player p, float partial, float t) {
		float[] a = SaiyanKitClient.anim(p, partial);
		if (a == null) {
			return null;
		}
		int id = (int) a[0];
		float age = a[1];
		if (id == SaiyanKit.ANIM_RUSH) {
			return volley(age, SaiyanKitConfig.RUSH_TICKS + 2, 3f,
					new float[] { -6, 14, -12, 0.14f, 0.06f, -0.42f }, new float[] { 8, 4, 6, 0.02f, -0.08f, 0.04f }, 8f);
		}
		if (id == SaiyanKit.ANIM_RAIN) {
			float len = SaiyanKitConfig.RAIN_FIRE_AT + SaiyanKitConfig.RAIN_EVERY * SaiyanKitConfig.RAIN_SHOTS + 4;
			if (age < SaiyanKitConfig.RAIN_FIRE_AT) {
				// the leap: both arms thrown up, gathering
				float k = smooth01(age / 4f);
				float[] up = { -40 * k, 10 * k, -8 * k, 0.04f * k, 0.28f * k, 0f };
				return new QuirkFirstPersonPose.Sample(fpf(age, up[0], up[1], up[2], up[3], up[4], up[5], up[0], up[1], up[2], up[3], up[4], up[5]),
						QuirkKeyframes.weight(age, len), true);
			}
			return volley(age - SaiyanKitConfig.RAIN_FIRE_AT, len - SaiyanKitConfig.RAIN_FIRE_AT, SaiyanKitConfig.RAIN_EVERY * 2f,
					new float[] { 22, 12, -20, 0.12f, -0.02f, -0.3f }, new float[] { 6, 4, 0, 0.02f, -0.2f, 0.02f }, 5f);
		}
		float[][] frames = FP.get(id);
		if (frames == null) {
			return null;
		}
		float[] v = QuirkKeyframes.sampleN(frames, age, QuirkFirstPersonPose.COLS);
		if (v == null) {
			return null;
		}
		if (id == SaiyanKit.ANIM_WAVE && age < 3.5f) {
			// the crossed arms strain before they fling open
			float sh = 1.6f * Mth.sin(t * 4.4f) * smooth01(age / 2f);
			v[1] += sh;
			v[7] -= sh;
		} else if (id == SaiyanKit.ANIM_SMASH && age >= 5.4f && age < 8f) {
			// the hammer's impact rings up the arms
			float sh = 2.2f * (1f - (age - 5.4f) / 2.6f) * Mth.sin(t * 6.1f);
			v[1] += sh;
			v[7] += sh;
		}
		return new QuirkFirstPersonPose.Sample(v, QuirkKeyframes.weight(age, QuirkKeyframes.length(frames)), FP_TWO.getOrDefault(id, false));
	}

	/**
	 * Fists (or palms) in turn, one every {@code per}/2 ticks: each snaps out fast (the first third), then draws back slower,
	 * a little off-line every time (by up to {@code spread} degrees) so a flurry never looks like one clip on repeat.
	 */
	private static QuirkFirstPersonPose.Sample volley(float age, float len, float per, float[] out, float[] back, float spread) {
		if (age > len) {
			return null;
		}
		float[] v = new float[QuirkFirstPersonPose.COLS];
		v[0] = age;
		for (int arm = 0; arm < 2; arm++) {
			float a = age + (arm == 0 ? 0f : per * 0.5f);
			int n = (int) Math.floor(a / per);
			float ph = (a - n * per) / per;
			float k = ph < 0.33f ? 1f - (1f - ph / 0.33f) * (1f - ph / 0.33f) : 1f - smooth01((ph - 0.33f) / 0.67f);
			float jy = (hash(n * 2 + arm) - 0.5f) * spread, jp = (hash(n * 2 + arm + 77) - 0.5f) * spread;
			int o = arm == 0 ? 1 : 7;
			for (int c = 0; c < 6; c++) {
				v[o + c] = Mth.lerp(k, back[c], out[c]);
			}
			v[o] += jp * k;
			v[o + 1] += jy * k;
		}
		return new QuirkFirstPersonPose.Sample(v, QuirkKeyframes.weight(age, len), true);
	}

	/**
	 * The Kamehameha's charge: the hands reach forward, then sweep back to his side and cup round the ball (an overshoot as they
	 * lock in), trembling harder as it fills, drawn a little further back, breathing.
	 */
	private static QuirkFirstPersonPose.Sample kameCharge(Player p, float partial, float t) {
		float age = SaiyanKitClient.beamAge(p, partial);
		float c = SaiyanKitClient.beamCharge(p, partial);
		float[] reach = { -6, 12, -10, 0.12f, 0.02f, -0.26f, -6, 12, -10, 0.12f, 0.02f, -0.26f };
		float[] hold = KAME_HOLD;
		float k = age < 2f ? 0f : backOut(Mth.clamp((age - 2f) / 6f, 0f, 1f));
		float[] v = new float[QuirkFirstPersonPose.COLS];
		for (int i = 0; i < 12; i++) {
			v[i + 1] = Mth.lerp(k, reach[i], hold[i]);
		}
		float sh = (0.5f + 1.9f * c) * Mth.sin(t * 3.7f), sh2 = (0.4f + 1.4f * c) * Mth.sin(t * 4.9f + 1.3f);
		float breath = 0.012f * Mth.sin(t * 0.3f);
		v[1] += sh;
		v[7] += sh2;
		v[5] += breath;
		v[11] += breath;
		v[6] += 0.04f * c * k;
		v[12] += 0.04f * c * k;
		return new QuirkFirstPersonPose.Sample(v, Math.min(1f, age / 2f), true);
	}

	/**
	 * The Kamehameha fires: both palms thrust out of the cupped hold to the centre, overshoot, rock back with the recoil, then
	 * hold against the beam's push (a hard tremble along the arms); at the end they sag and let go.
	 */
	private static QuirkFirstPersonPose.Sample kameFire(Player p, float partial, float t) {
		float fa = SaiyanKitClient.beamFireAge(p, partial);
		int ft = SaiyanKitClient.beamFireTicks(p);
		float[] hold = KAME_HOLD;
		float[] over = { -7, 28, -27, 0.3f, 0.03f, -0.37f, -7, 28, -27, 0.3f, 0.03f, -0.37f };
		float[] push = { -4, 26, -25, 0.3f, 0.02f, -0.3f, -4, 26, -25, 0.3f, 0.02f, -0.3f };
		float[] sag = { 10, 12, -6, 0.14f, -0.12f, -0.1f, 10, 12, -6, 0.14f, -0.12f, -0.1f };
		float[] a, b;
		float k;
		if (fa < 2f) {
			a = hold;
			b = over;
			k = 1f - (1f - fa / 2f) * (1f - fa / 2f);
		} else if (fa < 4f) {
			a = over;
			b = push;
			k = smooth01((fa - 2f) / 2f);
		} else if (fa < ft - 4f) {
			a = push;
			b = push;
			k = 0f;
		} else {
			a = push;
			b = sag;
			k = smooth01((fa - (ft - 4f)) / 6f);
		}
		float[] v = new float[QuirkFirstPersonPose.COLS];
		v[0] = fa;
		for (int i = 0; i < 12; i++) {
			v[i + 1] = Mth.lerp(k, a[i], b[i]);
		}
		if (fa >= 2f && fa < ft - 2f) {
			float push2 = 1f - 0.5f * smooth01((fa - 2f) / ft);
			float sh = 1.3f * push2 * Mth.sin(t * 5.3f), dz = 0.012f * push2 * Mth.sin(t * 6.7f + 0.8f);
			v[1] += sh;
			v[7] += sh;
			v[6] += dz;
			v[12] += dz;
		}
		float w = Mth.clamp((ft + 6f - fa) / 4f, 0f, 1f);
		return new QuirkFirstPersonPose.Sample(v, w, true);
	}

	/**
	 * The transformation (Super Saiyan, Ultra Instinct): fists clench in at the chest and shake, then as the burst goes off the
	 * arms are thrown down and out, and ease back.
	 */
	private static QuirkFirstPersonPose.Sample transform(Player p, float partial, float t) {
		if (SaiyanFx.form(p) == SaiyanForm.BASE) {
			return null;
		}
		int tt = SaiyanFx.transformingTicks(p);
		if (tt >= 28) {
			return null;
		}
		float age = tt + partial;
		float[] v = QuirkKeyframes.sampleN(TRANSFORM, age, QuirkFirstPersonPose.COLS);
		if (v == null) {
			return null;
		}
		if (age < 5f) {
			float sh = 2.4f * Mth.sin(t * 5.2f) * smooth01(age / 2f);
			v[1] += sh;
			v[7] -= sh;
		}
		return new QuirkFirstPersonPose.Sample(v, Mth.clamp(Math.min(age / 1.5f, (28f - age) / 6f), 0f, 1f), true);
	}

	/** The Kamehameha's cupped hold, both hands round the ball low at his main side (main arm, then the off arm crossed over). */
	private static final float[] KAME_HOLD = { 0, -14, 12, -0.02f, 0.02f, 0.04f, -2, 36, -16, 0.95f, 0f, 0.02f };

	private static final float[][] TRANSFORM = {
			fpf(0, -10, 14, 18, 0.03f, 0.04f, 0.02f, -10, 14, 18, 0.03f, 0.04f, 0.02f),
			fpf(2.5f, -30, 26, 26, 0.06f, 0.12f, 0.02f, -30, 26, 26, 0.06f, 0.12f, 0.02f),
			fpf(4.5f, -34, 28, 30, 0.07f, 0.14f, 0.02f, -34, 28, 30, 0.07f, 0.14f, 0.02f),
			fpf(6, 26, -32, -22, -0.17f, -0.23f, 0.06f, 26, -32, -22, -0.17f, -0.23f, 0.06f),
			fpf(12, 22, -28, -18, -0.15f, -0.2f, 0.05f, 22, -28, -18, -0.15f, -0.2f, 0.05f),
			fpf(28, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0) };

	/**
	 * Powering up (V): the fists come up to the chest, then drive down and clench at the hips, knuckles in, straining (a fast
	 * tremble, and a surge every second and a half); they ease back down when he lets go.
	 */
	private static QuirkFirstPersonPose.Sample powerUp(Player p, float t, boolean charging) {
		float since = charging ? t - chargeOn : t - chargeOff;
		float w = charging ? smooth01(since / 2f) : 1f - smooth01(since / 5f);
		if (w <= 0.01f) {
			return null;
		}
		float on = charging ? since : 8f + since; // after a release, hold the clench pose while it fades
		float[] v = QuirkKeyframes.sampleN(POWER_UP, Math.min(on, 8f), QuirkFirstPersonPose.COLS);
		if (v == null) {
			return null;
		}
		float strain = smooth01((on - 5f) / 3f);
		float surge = (float) Math.pow(Math.max(0f, Mth.sin(t * 0.21f)), 8f);
		float sh = (1.1f + 1.4f * surge) * strain * Mth.sin(t * 4.3f), sh2 = (1.1f + 1.4f * surge) * strain * Mth.sin(t * 4.7f + 2f);
		v[1] += sh - 6f * surge * strain;
		v[7] += sh2 - 6f * surge * strain;
		v[3] += 4f * surge * strain;
		v[9] += 4f * surge * strain;
		return new QuirkFirstPersonPose.Sample(v, w, true);
	}

	private static final float[][] POWER_UP = {
			fpf(0, 0, 0, 0, 0, 0, 0, 25, 0, 0, 0, -0.3f, 0),
			fpf(3, -28, 22, 24, 0.04f, 0.12f, 0.02f, -28, 22, 24, 0.04f, 0.12f, 0.02f),
			fpf(5, 8, 6, 30, -0.02f, -0.06f, 0.02f, 8, 6, 30, -0.02f, -0.06f, 0.02f),
			fpf(8, 6, 6, 28, -0.02f, -0.04f, 0.02f, 6, 6, 28, -0.02f, -0.04f, 0.02f) };

	/**
	 * The Spirit Bomb: both arms rise overhead (hands at the top corners of the view, palms up) and hold there swaying while it
	 * gathers, shaking more as it grows; the throw swings them forward and down with a follow-through.
	 */
	private static QuirkFirstPersonPose.Sample spiritBomb(Player p, float partial, float t) {
		SpiritBombState s = SaiyanSpiritBomb.state(p);
		if (s.phase() == SaiyanSpiritBomb.NONE || p.level() == null) {
			return null;
		}
		float age = (float) (p.level().getGameTime() + partial - s.since());
		float[] up = { -58, -18, -14, -0.12f, 0.42f, -0.12f };
		float[] v = new float[QuirkFirstPersonPose.COLS];
		float w = 1f;
		switch (s.phase()) {
			case SaiyanSpiritBomb.RISE -> {
				// a dip to gather, then up
				float k = age < 2f ? 0f : backOut(Mth.clamp((age - 2f) / 7f, 0f, 1f));
				float dip = age < 3f ? Mth.sin(age / 3f * Mth.PI) : 0f;
				both(v, up, k);
				v[1] += 14f * dip;
				v[7] += 14f * dip;
				w = smooth01(age / 2f);
			}
			case SaiyanSpiritBomb.CHARGE -> {
				float r = s.radiusAt(p.level().getGameTime() + partial) / SaiyanSpiritBomb.RADIUS_MAX;
				both(v, up, 1f);
				float sway = 2.5f * Mth.sin(age * 0.25f), sh = (0.3f + 1.6f * r) * Mth.sin(t * 4.1f);
				v[1] += sway + sh;
				v[7] += sway - sh;
				v[5] += 0.01f * Mth.sin(t * 0.3f);
				v[11] += 0.01f * Mth.sin(t * 0.3f + 1f);
			}
			case SaiyanSpiritBomb.THROW -> {
				float[] fwd = { 18, 14, -6, 0.1f, -0.1f, -0.32f }, through = { 28, 10, 0, 0.08f, -0.2f, -0.16f };
				if (age < 4f) {
					float k = (age / 4f) * (age / 4f);
					lerpBoth(v, up, fwd, k);
				} else {
					lerpBoth(v, fwd, through, smooth01((age - 4f) / 5f));
				}
				w = Mth.clamp((14f - age) / 4f, 0f, 1f);
			}
			default -> {
				return null;
			}
		}
		v[0] = age;
		return w <= 0.01f ? null : new QuirkFirstPersonPose.Sample(v, w, true);
	}

	private static void both(float[] v, float[] pose, float k) {
		for (int c = 0; c < 6; c++) {
			v[1 + c] = pose[c] * k;
			v[7 + c] = pose[c] * k;
		}
	}

	private static void lerpBoth(float[] v, float[] a, float[] b, float k) {
		for (int c = 0; c < 6; c++) {
			v[1 + c] = Mth.lerp(k, a[c], b[c]);
			v[7 + c] = v[1 + c];
		}
	}

	private static float smooth01(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** Ease out with a small overshoot (a hand locking into place). */
	private static float backOut(float x) {
		float c = 1.6f, y = x - 1f;
		return 1f + (c + 1f) * y * y * y + c * y * y;
	}

	private static float hash(int n) {
		int h = n * 0x27d4eb2d;
		h ^= h >>> 15;
		h *= 0x165667b1;
		h ^= h >>> 13;
		return (h & 0xFFFF) / 65535f;
	}
}
