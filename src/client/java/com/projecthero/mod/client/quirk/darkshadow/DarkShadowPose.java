package com.projecthero.mod.client.quirk.darkshadow;

import static com.projecthero.mod.client.quirk.QuirkKeyframes.AIM;
import static com.projecthero.mod.client.quirk.QuirkKeyframes.f;

import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.darkshadow.DarkShadow;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Quirk revamp: Tokoyami's keyframed poses (synced {@code animId / animStart}, so every viewer sees the
 * same), applied at the END of setupAnim. v0.17.1: every move is a 16-column {@link QuirkKeyframes} table (the shared
 * format; {@link QuirkKeyframes#AIM} in an arm column means "at the target / along the look") played with a column mask, so
 * a move that only moves his arms leaves the walk cycle's legs alone; smoothstepped, faded in over 2 ticks and out over the
 * last 3:
 * <ul>
 *   <li>Strike: wound back across the body, then the arm thrown out at the target ("Dark Shadow!"). Black Ankh dash: a
 *       low lunge, arms swept back, then a claw rake.</li>
 *   <li>Dark Fang: both clawed hands forward, juddering with each bite. Grab: open hand out, clenched and hauled back;
 *       Slam: the fist raised and brought down.</li>
 *   <li>Shield (held): arms crossed before the face. Black Ankh: arms flung wide, then fists clenched in a hunch; while it
 *       lasts, a low predatory stance. Abyssal: arms thrown up as the wings beat; gliding, arms swept back like wings.</li>
 *   <li>Fetch / Scout: an arm sent out at the point / up and away. Nova: curled in, then flung wide, head back.</li>
 *   <li>Recall: a hand to the chest; Summon: an arm swept out. Flare: a light held high, the face turned from it.</li>
 *   <li>Berserk (held): hunched, clutching his head, shaking. Calmed: slumped, head bowed.</li>
 * </ul>
 * Negative {@code xRot} swings a limb forward / up.
 */
public final class DarkShadowPose {
	// ---------------------------------------------------------------- the moves (16-column QuirkKeyframes + column masks)
	//            tick  rArm x y z           lArm x y z           body x y     rLeg x y z   lLeg x y z   head
	/** ANIM_STRIKE. */
	static final float[][] STRIKE = {
			f(0, -0.6f, 0.9f, 0.3f, -0.3f, 0, -0.2f, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0),
			f(2.5f, -1.9f, 0.9f, 0.5f, 0.2247f, 0, -0.2874f, 0, 0.4f, 0, 0, 0, 0, 0, 0, 0),
			f(4.5f, AIM, AIM, 0, 0.6f, 0, -0.35f, 0, -0.25f, 0, 0, 0, 0, 0, 0, 0),
			f(9, AIM, AIM, 0, 0.276f, 0, -0.188f, 0, -0.088f, 0, 0, 0, 0, 0, 0, 0),
			f(12, -0.4f, 0, 0.1f, 0.1f, 0, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int STRIKE_MASK = 0x1fe;
	/** ANIM_ANKH_DASH. */
	static final float[][] ANKH_DASH = {
			f(0, 0.9f, 0, 0.45f, 0.9f, 0, -0.45f, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(2, 0.9f, 0, 0.45f, 0.9f, 0, -0.45f, 0.55f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(3, 0.9f, 0, 0.45f, 0.9f, 0, -0.45f, 0.5362f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(5, -1.7f, -0.35f, -0.1f, 0.9f, 0, -0.45f, 0.4516f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(6, -1.575f, -0.3266f, -0.0844f, 0.9f, 0, -0.45f, 0.3984f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(9, -0.9f, -0.2f, 0, 0.3f, 0, -0.2f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int ANKH_DASH_MASK = 0x1fe;
	/** ANIM_FANG. */
	static final float[][] FANG = {
			f(0, -1f, -0.2f, 0, -1f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(3, -1.55f, -0.3f, 0.1f, -1.5f, 0.3f, -0.1f, 0.22f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(20, -1.55f, -0.3f, 0.1f, -1.5f, 0.3f, -0.1f, 0.22f, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int FANG_MASK = 0x1fe;
	/** ANIM_GRAB. */
	static final float[][] GRAB = {
			f(0, -0.8f, 0, 0.3f, 0, 0, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(3, AIM, AIM, 0, 0, 0, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(6, AIM, AIM, 0, 0, 0, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(9, -1.1f, 0.2f, 0.15f, 0, 0, 0, -0.12f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(12, -1f, 0.2f, 0.15f, 0, 0, 0, -0.12f, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int GRAB_MASK = 0x18e;
	/** ANIM_SLAM. */
	static final float[][] SLAM = {
			f(0, -1.1f, 0.2f, 0.15f, -0.3f, 0, -0.2f, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(3.5f, -2.95f, 0, 0.1f, -2.6f, 0, -0.1f, -0.2f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(6, -0.35f, 0, 0.1f, -0.3f, 0, -0.1f, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(11, -0.3f, 0, 0.1f, 0, 0, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int SLAM_MASK = 0x1fe;
	/** ANIM_ANKH. */
	static final float[][] ANKH = {
			f(0, -0.2f, 0, 0.3f, -0.2f, 0, -0.3f, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(4, -0.3f, 0, 1.35f, -0.3f, 0, -1.35f, -0.0606f, 0, 0, 0, 0, 0, 0, 0, -0.45f),
			f(7, -0.3f, 0, 1.35f, -0.3f, 0, -1.35f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.45f),
			f(10, -0.55f, 0, 0.3f, -0.55f, 0, -0.3f, 0.18f, 0, 0, 0, 0, 0, 0, 0, 0.2f),
			f(14, -0.45f, 0.1f, 0.25f, -0.45f, -0.1f, -0.25f, 0.12f, 0, 0, 0, 0, 0, 0, 0, 0.1f) };
	static final int ANKH_MASK = 0x81fe;
	/** ANIM_ABYSSAL. */
	static final float[][] ABYSSAL = {
			f(0, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(3, -2.9f, 0, 0.4f, -2.9f, 0, -0.4f, 0, 0, 0, 0, 0, 0, 0, 0, -0.5f),
			f(8, -2.4f, 0, 1f, -2.4f, 0, -1f, 0, 0, 0, 0, 0, 0, 0, 0, -0.3272f),
			f(14, 0.2f, 0, 1.1f, 0.2f, 0, -1.1f, 0, 0, 0, 0, 0, 0, 0, 0, -0.1f) };
	static final int ABYSSAL_MASK = 0x807e;
	/** ANIM_FETCH. */
	static final float[][] FETCH = {
			f(0, -0.7f, 0.4f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(3, AIM, AIM, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(8, AIM, AIM, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(14, -0.9f, 0.2f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int FETCH_MASK = 0xe;
	/** ANIM_SCOUT. */
	static final float[][] SCOUT = {
			f(0, -0.8f, 0.3f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(4, -2.5f, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			f(12, -2.3f, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.2f) };
	static final int SCOUT_MASK = 0x800e;
	/** ANIM_NOVA. */
	static final float[][] NOVA = {
			f(0, -0.5f, 0, 0, -0.5f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(5, -1.25f, 0.95f, 0, -1.2f, -0.95f, 0, 0.35f, 0, 0, 0, 0, 0, 0, 0, 0.4f),
			f(7.5f, -2.5f, 0, 1.1f, -2.5f, 0, -1.1f, -0.18f, 0, 0, 0, 0, 0, 0, 0, -0.55f),
			f(12, -2.2f, 0, 0.9f, -2.2f, 0, -0.9f, -0.1092f, 0, 0, 0, 0, 0, 0, 0, -0.3335f),
			f(18, -0.3f, 0, 0.1f, -0.3f, 0, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int NOVA_MASK = 0x81fe;
	/** ANIM_RECALL. */
	static final float[][] RECALL = {
			f(0, -0.4f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(4, -1.25f, 0.75f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.35f),
			f(12, -1.2f, 0.7f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.3f) };
	static final int RECALL_MASK = 0x800e;
	/** ANIM_SUMMON. */
	static final float[][] SUMMON = {
			f(0, -1.2f, 0.7f, 0, 0, 0, 0, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(5, -1.45f, -0.2f, 1f, 0, 0, 0, -0.15f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(14, -0.3f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int SUMMON_MASK = 0x18e;
	/** ANIM_FLARE. */
	static final float[][] FLARE = {
			f(0, -0.3f, 0, 0, -1f, 0, -0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(4, -1.8232f, 0.5824f, 0, -3f, 0.1f, -0.15f, 0, 0, 0, 0, 0, 0, 0, 0, 0.224f),
			f(5, -2f, 0.65f, 0, -3f, 0.1f, -0.15f, 0, 0, 0, 0, 0, 0, 0, 0, 0.25f),
			f(20, -2f, 0.65f, 0, -3f, 0.1f, -0.15f, 0, 0, 0, 0, 0, 0, 0, 0, 0.25f),
			f(22, -1.6875f, 0.5484f, 0, -3f, 0.1f, -0.15f, 0, 0, 0, 0, 0, 0, 0, 0, 0.25f),
			f(28, 0, 0, 0, -0.4f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int FLARE_MASK = 0x807e;
	/** ANIM_CALM. */
	static final float[][] CALM = {
			f(0, -0.4f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(6, -1.8f, 0.7f, 0, 0.15f, 0, -0.1f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.55f),
			f(16, -1.7f, 0.7f, 0, 0.1284f, 0, -0.0784f, 0.1654f, 0, 0, 0, 0, 0, 0, 0, 0.4852f),
			f(28, 0.1f, 0, 0.05f, 0.1f, 0, -0.05f, 0.12f, 0, 0, 0, 0, 0, 0, 0, 0.4f) };
	static final int CALM_MASK = 0x81fe;
	// ---- v0.17.1 revamp
	/** ANIM_RAMPAGE: braced wide, the commanding right arm thrown up and swept with each of its three swings. */
	static final float[][] RAMPAGE = {
			f(0, -0.9f, 0.2f, 0.2f, 0.2f, 0, -0.25f, 0, 0, 0, 0, 0.12f, 0, 0, -0.12f, 0),
			f(2, -2.7f, 0.5f, 0.35f, 0.35f, 0, -0.45f, -0.14f, 0.1f, -0.15f, 0, 0.22f, 0.25f, 0, -0.22f, -0.25f),
			f(3.5f, -2.2f, -0.55f, 0.15f, 0.35f, 0, -0.45f, -0.06f, -0.2f, -0.15f, 0, 0.22f, 0.25f, 0, -0.22f, -0.15f),
			f(6, -2.6f, -0.4f, 0.3f, 0.3f, 0, -0.4f, -0.12f, -0.15f, -0.15f, 0, 0.22f, 0.25f, 0, -0.22f, -0.25f),
			f(8.5f, -2.2f, 0.6f, 0.15f, 0.3f, 0, -0.4f, -0.06f, 0.2f, -0.15f, 0, 0.22f, 0.25f, 0, -0.22f, -0.15f),
			f(11, -2.95f, 0, 0.1f, -2.5f, 0, -0.15f, -0.2f, 0, -0.2f, 0, 0.24f, 0.3f, 0, -0.24f, -0.35f),
			f(13.5f, -1.1f, 0, 0.15f, -0.9f, 0, -0.15f, 0.32f, 0, -0.35f, 0, 0.24f, 0.4f, 0, -0.24f, 0.25f),
			f(18, -0.3f, 0, 0.1f, 0, 0, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int RAMPAGE_MASK = 0xfffe;
	/** ANIM_LUNGE: a point at the spot, then yanked after it -- body pitched forward, arm still out, legs trailing. */
	static final float[][] LUNGE = {
			f(0, -0.8f, 0.3f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			f(2, AIM, AIM, 0, 0.3f, 0, -0.2f, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(4, AIM, AIM, 0, 0.9f, 0, -0.4f, 0.55f, 0, 0.6f, 0, 0, -0.35f, 0, 0, -0.35f),
			f(9, -1.6f, 0, 0, 0.8f, 0, -0.35f, 0.45f, 0, 0.5f, 0, 0, -0.3f, 0, 0, -0.3f),
			f(12, -0.3f, 0, 0, 0.1f, 0, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int LUNGE_MASK = 0xfffe;
	/** ANIM_TOSS: the fist raised over his head flung forward as it hurls the body. */
	static final float[][] TOSS = {
			f(0, -2.8f, 0, 0.25f, -0.3f, 0, -0.2f, -0.2f, 0.25f, 0, 0, 0, 0, 0, 0, -0.2f),
			f(3, -1.15f, -0.2f, 0, 0.35f, 0, -0.25f, 0.28f, -0.3f, 0, 0, 0, 0, 0, 0, 0.1f),
			f(6, -1.0f, -0.2f, 0, 0.35f, 0, -0.25f, 0.2f, -0.25f, 0, 0, 0, 0, 0, 0, 0.05f),
			f(10, -0.3f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int TOSS_MASK = 0x81fe;
	/** ANIM_CATCH: landing in its arms -- crouched, arms out for balance. */
	static final float[][] CATCH = {
			f(0, -0.4f, 0, 1.1f, -0.4f, 0, -1.1f, 0.35f, 0, -0.6f, 0, 0.1f, -0.6f, 0, -0.1f, 0.2f),
			f(5, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, 0.25f, 0, -0.4f, 0, 0.08f, -0.4f, 0, -0.08f, 0.1f),
			f(16, 0, 0, 0.1f, 0, 0, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int CATCH_MASK = 0xfffe;
	/** ANIM_BLACKBODY: it pours over him -- curled in, then arms flung wide and head thrown back as the wings open. */
	static final float[][] BLACKBODY = {
			f(0, -1.3f, -0.7f, 0, -1.3f, 0.7f, 0, 0.35f, 0, 0, 0, 0, 0, 0, 0, 0.45f),
			f(5, -1.4f, -0.8f, 0, -1.4f, 0.8f, 0, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.5f),
			f(9, -2.7f, 0, 1.25f, -2.7f, 0, -1.25f, -0.28f, 0, 0, 0, 0, 0, 0, 0, -0.6f),
			f(18, -2.5f, 0, 1.15f, -2.5f, 0, -1.15f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.45f),
			f(26, -0.3f, 0, 0.4f, -0.3f, 0, -0.4f, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int BLACKBODY_MASK = 0x81fe;
	/** ANIM_DIVE: the claw dive -- talons thrown forward, then swept back past him. */
	static final float[][] DIVE = {
			f(0, -2.0f, -0.3f, 0, -2.0f, 0.3f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(3, -1.6f, 0.35f, 0, -1.6f, -0.35f, 0, 0.25f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(6, 0.6f, 0, 0.5f, 0.6f, 0, -0.5f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0),
			f(12, 0.2f, 0, 0.9f, 0.2f, 0, -0.9f, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int DIVE_MASK = 0x7e;
	/** ANIM_UNLEASH: arms crossed, gathering -- then thrown out, head back: "Let loose, Dark Shadow!" */
	static final float[][] UNLEASH = {
			f(0, -1.0f, -0.6f, 0, -1.0f, 0.6f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.2f),
			f(4, -1.45f, -0.85f, 0, -1.45f, 0.85f, 0, 0.32f, 0, -0.2f, 0, 0.1f, 0.2f, 0, -0.1f, 0.4f),
			f(8, -2.3f, 0.1f, 1.05f, -2.3f, -0.1f, -1.05f, -0.22f, 0, -0.1f, 0, 0.18f, 0.1f, 0, -0.18f, -0.55f),
			f(16, -2.1f, 0.1f, 0.95f, -2.1f, -0.1f, -0.95f, -0.15f, 0, -0.1f, 0, 0.18f, 0.1f, 0, -0.18f, -0.4f),
			f(22, -0.3f, 0, 0.2f, -0.3f, 0, -0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	static final int UNLEASH_MASK = 0xfffe;

	private DarkShadowPose() {
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	static float[][] frames(int id) {
		return switch (id) {
			case DarkShadow.ANIM_STRIKE -> STRIKE;
			case DarkShadow.ANIM_ANKH_DASH -> ANKH_DASH;
			case DarkShadow.ANIM_FANG -> FANG;
			case DarkShadow.ANIM_GRAB -> GRAB;
			case DarkShadow.ANIM_SLAM -> SLAM;
			case DarkShadow.ANIM_ANKH -> ANKH;
			case DarkShadow.ANIM_ABYSSAL -> ABYSSAL;
			case DarkShadow.ANIM_FETCH -> FETCH;
			case DarkShadow.ANIM_SCOUT -> SCOUT;
			case DarkShadow.ANIM_NOVA -> NOVA;
			case DarkShadow.ANIM_RECALL -> RECALL;
			case DarkShadow.ANIM_SUMMON -> SUMMON;
			case DarkShadow.ANIM_FLARE -> FLARE;
			case DarkShadow.ANIM_CALM -> CALM;
			case DarkShadow.ANIM_RAMPAGE -> RAMPAGE;
			case DarkShadow.ANIM_LUNGE -> LUNGE;
			case DarkShadow.ANIM_TOSS -> TOSS;
			case DarkShadow.ANIM_CATCH -> CATCH;
			case DarkShadow.ANIM_BLACKBODY -> BLACKBODY;
			case DarkShadow.ANIM_DIVE -> DIVE;
			case DarkShadow.ANIM_UNLEASH -> UNLEASH;
			default -> null;
		};
	}

	/** The columns each move drives (the rest keep vanilla's walk). */
	static int mask(int id) {
		return switch (id) {
			case DarkShadow.ANIM_STRIKE -> STRIKE_MASK;
			case DarkShadow.ANIM_ANKH_DASH -> ANKH_DASH_MASK;
			case DarkShadow.ANIM_FANG -> FANG_MASK;
			case DarkShadow.ANIM_GRAB -> GRAB_MASK;
			case DarkShadow.ANIM_SLAM -> SLAM_MASK;
			case DarkShadow.ANIM_ANKH -> ANKH_MASK;
			case DarkShadow.ANIM_ABYSSAL -> ABYSSAL_MASK;
			case DarkShadow.ANIM_FETCH -> FETCH_MASK;
			case DarkShadow.ANIM_SCOUT -> SCOUT_MASK;
			case DarkShadow.ANIM_NOVA -> NOVA_MASK;
			case DarkShadow.ANIM_RECALL -> RECALL_MASK;
			case DarkShadow.ANIM_SUMMON -> SUMMON_MASK;
			case DarkShadow.ANIM_FLARE -> FLARE_MASK;
			case DarkShadow.ANIM_CALM -> CALM_MASK;
			case DarkShadow.ANIM_RAMPAGE -> RAMPAGE_MASK;
			case DarkShadow.ANIM_LUNGE -> LUNGE_MASK;
			case DarkShadow.ANIM_TOSS -> TOSS_MASK;
			case DarkShadow.ANIM_CATCH -> CATCH_MASK;
			case DarkShadow.ANIM_BLACKBODY -> BLACKBODY_MASK;
			case DarkShadow.ANIM_DIVE -> DIVE_MASK;
			case DarkShadow.ANIM_UNLEASH -> UNLEASH_MASK;
			default -> 0;
		};
	}

	static int length(int id) {
		float[][] f = frames(id);
		return f == null ? 0 : (int) QuirkKeyframes.length(f);
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!DarkShadow.is(player) || player.level() == null) {
			return;
		}
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		// the look (or the target) as an arm angle
		float aimX = model.head.xRot - Mth.HALF_PI, aimY = model.head.yRot;
		Vec3 reach = DarkShadow.reach(player);
		if (reach != null && s.animId != 0) {
			Vec3 shoulder = player.getPosition(partial).add(0, player.getBbHeight() * 0.78, 0);
			Vec3 d = reach.subtract(shoulder);
			double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
			float body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
			float dirYaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
			aimX = -Mth.HALF_PI - (float) Math.atan2(d.y, horiz);
			aimY = Mth.clamp(Mth.wrapDegrees(dirYaw - body) * Mth.DEG_TO_RAD, -1.2f, 1.2f);
		}

		// ---- held states
		if (DarkShadow.unleashed(player)) {
			// v0.17.1 Unleash: in control -- feet planted, the right arm thrust out directing the frenzy
			float sway = 0.05f * Mth.sin(t * 0.4f);
			pose(r, 0.9f, -1.65f + sway, -0.15f, 0.1f);
			pose(l, 0.9f, -0.45f, 0.2f, -0.35f);
			model.rightLeg.zRot = Mth.lerp(0.7f, model.rightLeg.zRot, 0.16f);
			model.leftLeg.zRot = Mth.lerp(0.7f, model.leftLeg.zRot, -0.16f);
		} else if (DarkShadow.berserk(player)) {
			// losing control: hunched, clutching his head, shaking
			float shake = 0.07f * Mth.sin(t * 1.9f);
			model.body.xRot = Mth.lerp(0.8f, model.body.xRot, 0.38f);
			pose(r, 1f, -2.55f + shake, -0.55f, 0.15f);
			pose(l, 1f, -2.55f - shake, 0.55f, -0.15f);
			model.head.xRot = Mth.lerp(0.8f, model.head.xRot, 0.35f + shake);
			model.hat.copyFrom(model.head);
			model.rightLeg.z += 2.5f;
			model.leftLeg.z += 2.5f;
			model.rightLeg.y -= 0.6f;
			model.leftLeg.y -= 0.6f;
			return;
		}
		if (DarkShadow.shielding(player)) {
			pose(r, 1f, -1.65f, -0.85f, 0f);
			pose(l, 1f, -1.5f, 0.85f, 0f);
			model.head.xRot = Mth.lerp(0.7f, model.head.xRot, 0.25f);
			model.hat.copyFrom(model.head);
		}
		if (DarkShadow.ankh(player) && !player.isFallFlying()) {
			// a low, predatory stance: claws forward
			pose(r, 0.55f, -0.45f + 0.04f * Mth.sin(t * 0.1f), 0.1f, 0.25f);
			pose(l, 0.55f, -0.45f - 0.04f * Mth.sin(t * 0.1f), -0.1f, -0.25f);
		}
		if (DarkShadow.abyssal(player) && player.isFallFlying()) {
			// gliding: arms swept back along the wings
			pose(r, 1f, 0.2f, 0f, 1.15f + 0.05f * Mth.sin(t * 0.3f));
			pose(l, 1f, 0.2f, 0f, -1.15f - 0.05f * Mth.sin(t * 0.3f));
		}
		if (DarkShadow.val(player, DarkShadow.V_TOSS) == 1L) {
			// Shadow Toss: the fist held high as it carries the body overhead
			pose(r, 1f, -2.7f + 0.05f * Mth.sin(t * 0.25f), -0.1f, 0.25f);
			pose(l, 0.6f, -0.35f, 0.1f, -0.3f);
		}
		if (DarkShadow.val(player, DarkShadow.V_FETCH) == 1L) {
			pose(r, 1f, aimX, aimY, 0f); // being carried along: the arm still out at the point
		}

		// ---- the moves' keyframes
		float age = player.level().getGameTime() - s.animStart + partial;
		float[][] frames = frames(s.animId);
		if (frames == null || age < 0f || age >= QuirkKeyframes.length(frames)) {
			return;
		}
		float len = QuirkKeyframes.length(frames);
		float w = Math.min(1f, age / 2f + 0.2f) * Math.min(1f, (len - age) / 3f);
		float[] p = QuirkKeyframes.sample(frames, age, aimX, aimY);
		if (p == null) {
			return;
		}
		if (s.animId == DarkShadow.ANIM_FANG) {
			// the bites judder the clawed hands
			float jud = 0.12f * Mth.sin(age * 1.6f) * Math.min(1f, age / 3f);
			p[QuirkKeyframes.R_ARM_X] += jud;
			p[QuirkKeyframes.L_ARM_X] -= jud;
		}
		QuirkKeyframes.applyMasked(model, p, w, mask(s.animId));
		if (s.animId == DarkShadow.ANIM_FLARE) {
			// the face turned away from the light (head yaw is not a keyframe column)
			float turn = age < 5f ? age / 5f : age < 22f ? 1f : Math.max(0f, 1f - (age - 22f) / 6f);
			model.head.yRot = Mth.lerp(w, model.head.yRot, model.head.yRot + 0.45f * turn);
			model.hat.copyFrom(model.head);
		}
	}
}
