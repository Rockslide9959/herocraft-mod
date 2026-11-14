package com.projecthero.mod.client.raven;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenAbilityManager;
import com.projecthero.mod.raven.RavenConfig;
import com.projecthero.mod.raven.RavenMoves;
import com.projecthero.mod.raven.RavenSpell;
import com.projecthero.mod.raven.data.RavenState;
import com.projecthero.mod.raven.network.RavenFxPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.4 Raven animation round (user: "the flight animation needs to be fixed and improved ... add animations for her abilities"):
 * her whole body language, on the vanilla player model ({@code RavenPoseMixin}, at the very end of {@code PlayerModel.setupAnim} so it
 * wins over every shared pose) and on the first-person arms ({@link RavenFirstPerson}, through {@code QuirkFirstPersonPose}). Built
 * like {@code StarfirePose}: driven only by her synced {@link RavenState} (a move's start is the tick its cooldown stamp changed; the
 * holds are their synced flags and her effects), so every viewer sees the same thing. Keyframes are smoothstepped, every layer eases
 * in from whatever pose it lands on and out again, and the layers stack: flight under meditation under the holds under the moves.
 *
 * <ul>
 *   <li><b>Flight</b>: no walking swing at all in the air (vanilla's walk cycle is cleared first). A floaty sorceress hover: upright,
 *   legs together with one knee drifting back, arms held a little out from her sides and drifting slowly; cruising she leans into
 *   the way she flies (about 20 degrees) with her arms easing back; sprint-flying she leans further (about 50) with the arms swept
 *   back along her sides and the legs trailing; flying backward she leans back a touch with her palms forward. The shared flight
 *   feel banks her into turns and gives the hover its slow bob; the lean, the limbs and take-off / landing all blend smoothly.</li>
 *   <li><b>Dark Bolt</b> (R): a flick from alternating hands -- cocked by the ear, palm thrust along the look, followed through.</li>
 *   <li><b>Telekinesis</b> (Shift+R): one hand raised to the held thing, fingers trembling, the other out for balance; letting go
 *   flings it overhead and across.</li>
 *   <li><b>Soul Shield</b> (G): both palms snap out in front of her and she rocks back; <b>Soul-Self</b> (Shift+G): her chest
 *   arches and her arms fling back as the soul tears out, then she stands in a trance, head bowed, palms low, while she steers it.</li>
 *   <li><b>Telekinetic Crush</b> (Z): both hands lift, palms up, as the creatures rise, hold them overhead shaking, and slam down
 *   with a crouch. <b>Trigon's Wrath</b> (Shift+Z): arms thrown up and head back, then a hunched, clawed stance for the Wrath.</li>
 *   <li><b>Shadow Dash</b> (X): out of the streak with her arms trailing and the lean easing off; <b>Shadow Step Strike</b>
 *   (Shift+X): up out of the shadow in a crouch into a clawed uppercut.</li>
 *   <li><b>Dark Energy Wave</b> (C): hands drawn in and crossed while it gathers, then flung out wide along the look.
 *   <b>Demonic Ascension</b> (Shift+C): arms rise into a V, head back; then held a little out from her sides, palms out.</li>
 *   <li><b>Meditate</b> (Shift+V): floating cross-legged, hands resting out over her knees, a slow breath.</li>
 *   <li><b>The spells</b>: Void Vortex (hand out, circling), Shadow Binding (both palms pressed down, crouched), Soul Detonation (arms
 *   rise wide, head back, then snap together), Soul Devour (hand on the target, the other to her chest), Debris Storm (both hands
 *   lift; each block thrown is a flick), Dimensional Rift (a downward tearing slash), Soul Restoration (arms spread palms up; on a
 *   squadmate a reach), Telekinetic Pull (reach and yank back), Astral Sanctuary (arms up overhead, then spread down in a dome),
 *   Shadow Veil (arms crossed over her chest, head bowed).</li>
 *   <li><b>A streak of shadow</b> (Shadow Dash, sunk for Shadow Step): the body is hidden.</li>
 * </ul>
 * The trackers ({@link #TRACKS}) are client-side, dropped when leaving a world ({@link #clear}) and pruned to the players in view.
 */
public final class RavenPose {
	private RavenPose() {
	}

	// ================================================================ keyframes
	// Arm rows: {tick, xRot, yRot, zRot, aimX, aimY} for the RIGHT arm (yRot / zRot mirror for the left). aimX / aimY 1 = the value is
	// an offset from the look (pitch / yaw), 0 = absolute. Body rows: {tick, twist, lean, rightLegX, leftLegX, headX, headForward(px),
	// crouch, legSpread} for a right-side move (twist and the legs mirror for the left). Lean is degrees, the rest radians.

	private static final float[][] BOLT_ARM = {
			{ 0.0f, -0.35f, 0.15f, 0.15f, 0, 0 },
			{ 0.9f, -1.95f, 0.55f, 0.35f, 0, 0 },
			{ 2.0f, 0.0f, -0.05f, 0.0f, 1, 1 },
			{ 4.5f, 0.08f, -0.1f, 0.0f, 1, 1 },
			{ 11.0f, 0.3f, -0.15f, 0.1f, 1, 1 } };
	private static final float[][] BOLT_OTHER = {
			{ 0.0f, 0f, 0f, 0f, 0, 0 },
			{ 0.9f, -0.35f, 0f, 0.15f, 0, 0 },
			{ 2.0f, 0.3f, 0f, 0.22f, 0, 0 },
			{ 11.0f, 0.1f, 0f, 0.1f, 0, 0 } };
	private static final float[][] BOLT_BODY = {
			{ 0.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 0.9f, 0.3f, -2f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 2.0f, -0.3f, 5f, 0.12f, -0.08f, 0f, -0.3f, 0f, 0f },
			{ 4.5f, -0.32f, 5f, 0.12f, -0.08f, 0f, -0.3f, 0f, 0f },
			{ 11.0f, -0.05f, 1f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float BOLT_IN = 0.9f, BOLT_HOLD = 4.5f, BOLT_OUT = 11f;

	private static final float[][] TK_THROW = {
			{ 0f, -0.08f, -0.08f, 0f, 1, 1 },
			{ 1.5f, -2.5f, 0.2f, 0.25f, 0, 0 },
			{ 3.2f, 0.25f, -0.6f, -0.05f, 1, 1 },
			{ 12f, 0.25f, -0.25f, 0.05f, 1, 1 } };
	private static final float[][] TK_THROW_BODY = {
			{ 0f, -0.15f, 2f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 1.5f, 0.25f, -6f, 0f, 0f, -0.15f, 0f, 0f, 0f },
			{ 3.2f, -0.35f, 9f, 0.15f, -0.1f, 0.05f, -0.4f, 0f, 0f },
			{ 12f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] SHIELD_ARM = {
			{ 0f, -0.2f, 0f, 0.1f, 0, 0 },
			{ 2f, 0.08f, -0.42f, 0f, 1, 0 },
			{ 12f, 0.12f, -0.4f, 0f, 1, 0 },
			{ 20f, 0.35f, -0.2f, 0.1f, 1, 0 } };
	private static final float[][] SHIELD_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 2f, 0f, -7f, -0.1f, 0.15f, 0f, 0.3f, 0.1f, 0.04f },
			{ 12f, 0f, -4f, -0.05f, 0.1f, 0f, 0.2f, 0.05f, 0.04f },
			{ 20f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] SOUL_ARM = {
			{ 0f, 0f, 0f, 0.05f, 0, 0 },
			{ 3f, 0.6f, 0f, 1.05f, 0, 0 },
			{ 10f, 0.55f, 0f, 0.95f, 0, 0 },
			{ 18f, -0.3f, 0f, 0.35f, 0, 0 } };
	private static final float[][] SOUL_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 3f, 0f, -14f, -0.1f, 0.05f, -0.6f, 0.5f, 0f, 0.08f },
			{ 10f, 0f, -10f, -0.1f, 0.05f, -0.5f, 0.4f, 0f, 0.08f },
			{ 18f, 0f, 0f, 0f, 0f, 0.2f, 0f, 0f, 0f } };

	private static final float CRUSH_SLAM = RavenConfig.CRUSH_RISE_TICKS + RavenConfig.CRUSH_HOLD_TICKS;
	private static final float[][] CRUSH_ARM = {
			{ 0f, -0.5f, 0f, 0.2f, 0, 0 },
			{ RavenConfig.CRUSH_RISE_TICKS, -2.5f, -0.15f, 0.28f, 0, 0 },
			{ CRUSH_SLAM - 2f, -2.7f, -0.1f, 0.22f, 0, 0 },
			{ CRUSH_SLAM + 0.5f, -2.95f, -0.15f, 0.15f, 0, 0 },
			{ CRUSH_SLAM + 2f, 0.2f, -0.2f, 0.18f, 0, 0 },
			{ CRUSH_SLAM + 8f, 0.15f, -0.2f, 0.18f, 0, 0 },
			{ CRUSH_SLAM + 18f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] CRUSH_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ RavenConfig.CRUSH_RISE_TICKS, 0f, -8f, 0f, 0f, -0.35f, 0f, 0f, 0.06f },
			{ CRUSH_SLAM - 2f, 0f, -10f, 0f, 0f, -0.4f, 0f, 0f, 0.06f },
			{ CRUSH_SLAM + 0.5f, 0f, -12f, 0f, 0f, -0.45f, 0f, 0f, 0.06f },
			{ CRUSH_SLAM + 2f, 0f, 16f, -0.35f, 0.3f, 0.35f, 0f, 0.4f, 0.1f },
			{ CRUSH_SLAM + 8f, 0f, 14f, -0.3f, 0.25f, 0.3f, 0f, 0.35f, 0.1f },
			{ CRUSH_SLAM + 18f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] WRATH_ON_ARM = {
			{ 0f, 0f, 0f, 0.1f, 0, 0 },
			{ 4f, -2.6f, 0f, 0.75f, 0, 0 },
			{ 14f, -2.5f, 0f, 0.7f, 0, 0 },
			{ 26f, -0.8f, -0.1f, 0.45f, 0, 0 } };
	private static final float[][] WRATH_ON_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 4f, 0f, -12f, 0f, 0f, -0.6f, 0.4f, 0f, 0.1f },
			{ 14f, 0f, -10f, 0f, 0f, -0.55f, 0.4f, 0f, 0.1f },
			{ 26f, 0f, 10f, 0f, 0f, 0.15f, -0.4f, 0f, 0.08f } };
	private static final float[] WRATH_HOLD_ARM = { 0f, -0.8f, -0.1f, 0.45f, 0, 0 };
	private static final float[] WRATH_HOLD_BODY = { 0f, 0f, 10f, 0f, 0f, 0.15f, -0.4f, 0f, 0.08f };

	private static final float[][] DASH_OUT_ARM = {
			{ 0f, 1.1f, 0f, 0.25f, 0, 0 },
			{ 4f, 0.6f, 0f, 0.2f, 0, 0 },
			{ 12f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] DASH_OUT_BODY = {
			{ 0f, 0f, 22f, 0.3f, 0.45f, 0f, 0f, 0f, 0f },
			{ 4f, 0f, 10f, 0.15f, 0.25f, 0f, 0f, 0f, 0f },
			{ 12f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] ERUPT_LEAD = {
			{ 0f, 0.6f, 0f, 0.2f, 0, 0 },
			{ 2f, -2.9f, -0.1f, 0.1f, 0, 0 },
			{ 6f, -2.7f, -0.1f, 0.15f, 0, 0 },
			{ 14f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] ERUPT_OTHER = {
			{ 0f, 0.5f, 0f, 0.3f, 0, 0 },
			{ 2f, 0.45f, 0f, 0.45f, 0, 0 },
			{ 14f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] ERUPT_BODY = {
			{ 0f, 0f, 10f, -0.6f, 0.4f, 0.2f, 0f, 0.6f, 0.05f },
			{ 2f, 0f, -6f, 0.05f, 0.2f, -0.3f, 0f, 0f, 0f },
			{ 6f, 0f, -4f, 0.05f, 0.15f, -0.25f, 0f, 0f, 0f },
			{ 14f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float WAVE_GO = RavenConfig.WAVE_CHARGE_TICKS;
	private static final float[][] WAVE_ARM = {
			{ 0f, -0.2f, 0f, 0.1f, 0, 0 },
			{ WAVE_GO * 0.5f, -1.25f, -0.85f, 0.1f, 0, 0 },
			{ WAVE_GO, -1.3f, -0.95f, 0.05f, 0, 0 },
			{ WAVE_GO + 1.5f, 0.05f, 0.75f, 0.1f, 1, 0 },
			{ WAVE_GO + 6f, 0.1f, 0.85f, 0.15f, 1, 0 },
			{ WAVE_GO + 18f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] WAVE_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ WAVE_GO, 0f, -6f, 0f, 0f, 0.2f, 0f, 0.25f, 0f },
			{ WAVE_GO + 1.5f, 0f, 12f, 0.1f, -0.25f, 0f, -0.4f, 0f, 0.08f },
			{ WAVE_GO + 6f, 0f, 10f, 0.1f, -0.2f, 0f, -0.3f, 0f, 0.08f },
			{ WAVE_GO + 18f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	private static final float[][] ASCEND_ARM = {
			{ 0f, 0f, 0f, 0.1f, 0, 0 },
			{ 6f, -2.7f, 0f, 0.55f, 0, 0 },
			{ 16f, -2.6f, 0f, 0.6f, 0, 0 },
			{ 26f, 0.05f, 0f, 0.45f, 0, 0 } };
	private static final float[][] ASCEND_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 6f, 0f, -10f, 0f, 0f, -0.55f, 0.3f, 0f, 0.06f },
			{ 16f, 0f, -8f, 0f, 0f, -0.5f, 0.3f, 0f, 0.06f },
			{ 26f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	// spells
	private static final float[][] VORTEX_ARM = {
			{ 0f, -0.3f, 0f, 0.1f, 0, 0 },
			{ 3f, -0.1f, 0f, 0f, 1, 1 },
			{ 16f, -0.08f, 0f, 0f, 1, 1 },
			{ 24f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[] CHEST_ARM = { 0f, -1.05f, -0.65f, 0.05f, 0, 0 };
	private static final float[][] BIND_ARM = {
			{ 0f, -0.3f, 0f, 0.1f, 0, 0 },
			{ 4f, -0.75f, 0.1f, 0.35f, 0, 0 },
			{ 12f, -0.7f, 0.1f, 0.35f, 0, 0 },
			{ 20f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] BIND_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 4f, 0f, 12f, 0.1f, -0.05f, 0.4f, 0f, 0.35f, 0.06f },
			{ 12f, 0f, 12f, 0.1f, -0.05f, 0.4f, 0f, 0.35f, 0.06f },
			{ 20f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float DET = RavenConfig.DETONATE_DELAY;
	private static final float[][] DETONATE_ARM = {
			{ 0f, 0f, 0f, 0.1f, 0, 0 },
			{ 6f, -0.35f, 0f, 1.35f, 0, 0 },
			{ DET, -0.55f, 0f, 1.55f, 0, 0 },
			{ DET + 1.5f, -1.4f, -0.6f, 0.05f, 0, 0 },
			{ DET + 8f, -1.3f, -0.55f, 0.05f, 0, 0 },
			{ DET + 18f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] DETONATE_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 6f, 0f, -6f, 0f, 0f, -0.4f, 0.3f, 0f, 0.06f },
			{ DET, 0f, -8f, 0f, 0f, -0.5f, 0.3f, 0f, 0.06f },
			{ DET + 1.5f, 0f, 10f, 0.05f, -0.1f, 0.2f, -0.3f, 0.15f, 0.06f },
			{ DET + 8f, 0f, 8f, 0.05f, -0.1f, 0.2f, -0.3f, 0.1f, 0.06f },
			{ DET + 18f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] DEBRIS_ARM = {
			{ 0f, 0f, 0f, 0.1f, 0, 0 },
			{ 5f, -2.3f, 0f, 0.5f, 0, 0 },
			{ 12f, -2.2f, 0f, 0.55f, 0, 0 },
			{ 22f, -0.4f, 0f, 0.3f, 0, 0 } };
	private static final float[][] DEBRIS_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 5f, 0f, -6f, 0f, 0f, -0.4f, 0.2f, 0f, 0.06f },
			{ 12f, 0f, -5f, 0f, 0f, -0.35f, 0.2f, 0f, 0.06f },
			{ 22f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] RIFT_ARM = {
			{ 0f, -0.3f, 0f, 0.1f, 0, 0 },
			{ 2f, -2.9f, -0.2f, 0.2f, 0, 0 },
			{ 5f, -0.5f, -0.5f, 0f, 0, 0 },
			{ 10f, -0.45f, -0.45f, 0f, 0, 0 },
			{ 18f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] RIFT_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 2f, 0.25f, -5f, 0f, 0f, -0.2f, 0f, 0f, 0f },
			{ 5f, -0.3f, 9f, 0.1f, -0.1f, 0.2f, -0.3f, 0.1f, 0f },
			{ 10f, -0.25f, 7f, 0.1f, -0.1f, 0.15f, -0.2f, 0.05f, 0f },
			{ 18f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] REACH_ARM = {
			{ 0f, -0.3f, 0f, 0.1f, 0, 0 },
			{ 3f, -0.1f, 0f, 0f, 1, 1 },
			{ 14f, -0.1f, 0f, 0f, 1, 1 },
			{ 22f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] PULL_ARM = {
			{ 0f, -0.3f, 0f, 0.1f, 0, 0 },
			{ 2.5f, -0.05f, 0f, 0f, 1, 1 },
			{ 5f, -0.05f, 0f, 0f, 1, 1 },
			{ 8f, -1.05f, -0.55f, 0.1f, 0, 0 },
			{ 16f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] PULL_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 2.5f, -0.15f, 4f, 0f, 0f, 0f, -0.2f, 0f, 0f },
			{ 5f, -0.15f, 4f, 0f, 0f, 0f, -0.2f, 0f, 0f },
			{ 8f, 0.1f, -6f, -0.1f, 0.1f, -0.1f, 0.2f, 0f, 0f },
			{ 16f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] SANCTUARY_ARM = {
			{ 0f, 0f, 0f, 0.1f, 0, 0 },
			{ 5f, -2.85f, 0f, 0.25f, 0, 0 },
			{ 10f, -2.7f, 0f, 0.4f, 0, 0 },
			{ 16f, -0.7f, 0f, 1.25f, 0, 0 },
			{ 24f, -0.6f, 0f, 1.2f, 0, 0 },
			{ 34f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] SANCTUARY_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 5f, 0f, -6f, 0f, 0f, -0.45f, 0.2f, 0f, 0.05f },
			{ 10f, 0f, -6f, 0f, 0f, -0.4f, 0.2f, 0f, 0.05f },
			{ 16f, 0f, 2f, 0f, 0f, 0.1f, 0f, 0.1f, 0.08f },
			{ 24f, 0f, 2f, 0f, 0f, 0.1f, 0f, 0.1f, 0.08f },
			{ 34f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] VEIL_ARM = {
			{ 0f, -0.2f, 0f, 0.1f, 0, 0 },
			{ 4f, -1.15f, -0.95f, 0.05f, 0, 0 },
			{ 14f, -1.1f, -0.9f, 0.05f, 0, 0 },
			{ 22f, 0f, 0f, 0.05f, 0, 0 } };
	private static final float[][] VEIL_BODY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 4f, 0f, 6f, 0f, 0f, 0.35f, 0f, 0.2f, 0f },
			{ 14f, 0f, 6f, 0f, 0f, 0.35f, 0f, 0.2f, 0f },
			{ 22f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f } };

	/** Smoothstepped keyframe sample, clamped to the first / last row outside the table. */
	static float[] key(float[][] k, float t) {
		if (t <= k[0][0]) {
			return k[0];
		}
		for (int i = 1; i < k.length; i++) {
			if (t <= k[i][0]) {
				float[] a = k[i - 1], b = k[i];
				float u = (t - a[0]) / Math.max(1e-4f, b[0] - a[0]);
				u = u * u * (3f - 2f * u);
				float[] out = new float[a.length];
				out[0] = t;
				for (int c = 1; c < a.length; c++) {
					out[c] = Mth.lerp(u, a[c], b[c]);
				}
				return out;
			}
		}
		return k[k.length - 1];
	}

	static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** In over {@code [0, in]}, held to {@code hold}, out by {@code out}; 0 outside. */
	static float env(float t, float in, float hold, float out) {
		if (t < 0f || t >= out) {
			return 0f;
		}
		float a = in <= 0f ? 1f : smooth(t / in);
		float b = t <= hold ? 1f : 1f - smooth((t - hold) / (out - hold));
		return a * b;
	}

	// ================================================================ client tracking (per Raven in view)

	static final class Track {
		/** Each move id's last cooldown stamp seen, and when that move began (client game time). */
		final Map<String, Long> ready = new HashMap<>();
		final Map<String, Float> start = new HashMap<>();
		boolean seeded;
		/** Dark Bolt / Debris throws: [0] right, [1] left, this throw's and the previous throw's start. */
		final float[] throwAt = { -1000f, -1000f };
		final float[] prevAt = { -1000f, -1000f };
		float lastThrow = -1000f, prevThrow = -1000f;
		int lastSide = 1, prevSide = -1;
		int nextSide = 1;
		int lastDebris;
		boolean tk;
		float tkReleaseAt = -1000f;
		boolean soulOut;
		// eased weights (value, previous tick's value)
		float flyW, flyWPrev, medW, medWPrev, tkW, tkWPrev, soulW, soulWPrev, beamW, beamWPrev, chanW, chanWPrev;
		float ascW, ascWPrev, wrathW, wrathWPrev, debW, debWPrev;
		// flight
		double lastX, lastY, lastZ;
		boolean hasLast;
		float pace, pacePrev, fast, fastPrev, back, backPrev, climb, climbPrev;
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();

	/** Leaving a world. */
	public static void clear() {
		TRACKS.clear();
		LEAN_CACHE.clear();
	}

	/** Once a client tick: every Raven in view. */
	public static void clientTick(ClientLevel level) {
		if (level == null) {
			clear();
			return;
		}
		Set<UUID> present = new HashSet<>();
		long now = level.getGameTime();
		for (Player p : level.players()) {
			RavenState s = Raven.peek(p);
			if (s == null || !s.hasPower) {
				continue;
			}
			present.add(p.getUUID());
			tick(p, s, TRACKS.computeIfAbsent(p.getUUID(), k -> new Track()), now);
		}
		TRACKS.keySet().retainAll(present);
		if (LEAN_CACHE.size() > 64) {
			LEAN_CACHE.clear();
		}
	}

	private static void tick(Player p, RavenState s, Track tr, long now) {
		// move starts: a cooldown stamp that changed to a time still ahead is a new cast
		// (the first sight of a player only learns the stamps, so walking up to a Raven mid-cooldown plays nothing)
		for (Map.Entry<String, Long> e : s.abilityReadyAt.entrySet()) {
			Long seen = tr.ready.get(e.getKey());
			long r = e.getValue();
			if (seen == null || seen != r) {
				tr.ready.put(e.getKey(), r);
				if (tr.seeded && r > now && r - now <= 20 * 60 * 4) {
					tr.start.put(e.getKey(), (float) now);
					if (RavenAbilityManager.DARK_BOLT.equals(e.getKey())) {
						throwFrom(tr, now);
					}
				}
			}
		}
		tr.seeded = true;
		// a Debris Storm block thrown: a flick from the next hand
		if (s.debris < tr.lastDebris && s.debris >= 0) {
			throwFrom(tr, now);
		}
		tr.lastDebris = s.debris;
		// Telekinesis let go
		boolean tk = s.tkTarget >= 0;
		if (tr.tk && !tk) {
			tr.tkReleaseAt = now;
		}
		tr.tk = tk;
		if (p.tickCount % 2 == 0) {
			tr.soulOut = RavenMoves.soulOut(p);
		}
		// eased holds
		tr.flyWPrev = tr.flyW;
		tr.flyW = approach(tr.flyW, s.flying && !s.meditating ? 1f : 0f, s.flying ? 0.3f : 0.2f);
		tr.medWPrev = tr.medW;
		tr.medW = approach(tr.medW, s.meditating ? 1f : 0f, 0.2f);
		tr.tkWPrev = tr.tkW;
		tr.tkW = approach(tr.tkW, tk ? 1f : 0f, 0.35f);
		tr.soulWPrev = tr.soulW;
		tr.soulW = approach(tr.soulW, tr.soulOut ? 1f : 0f, 0.18f);
		tr.beamWPrev = tr.beamW;
		tr.beamW = approach(tr.beamW, RavenFx.running(RavenFxPayload.BEAM, p.getId(), now) ? 1f : 0f, 0.35f);
		tr.chanWPrev = tr.chanW;
		tr.chanW = approach(tr.chanW, RavenFx.running(RavenFxPayload.CHANNEL, p.getId(), now) ? 1f : 0f, 0.25f);
		tr.ascWPrev = tr.ascW;
		tr.ascW = approach(tr.ascW, s.ascendUntil > now ? 1f : 0f, 0.12f);
		tr.wrathWPrev = tr.wrathW;
		tr.wrathW = approach(tr.wrathW, s.wrathUntil > now ? 1f : 0f, 0.12f);
		tr.debWPrev = tr.debW;
		tr.debW = approach(tr.debW, s.debris > 0 ? 1f : 0f, 0.2f);
		// flight: how fast, sprinting, backward, climbing (position deltas: other players' velocity is unreliable on the client)
		double dx = tr.hasLast ? p.getX() - tr.lastX : 0, dy = tr.hasLast ? p.getY() - tr.lastY : 0, dz = tr.hasLast ? p.getZ() - tr.lastZ : 0;
		tr.lastX = p.getX();
		tr.lastY = p.getY();
		tr.lastZ = p.getZ();
		tr.hasLast = true;
		double hs = Math.hypot(dx, dz);
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		double fwd = -dx * Mth.sin(yaw) + dz * Mth.cos(yaw);
		tr.pacePrev = tr.pace;
		tr.fastPrev = tr.fast;
		tr.backPrev = tr.back;
		tr.climbPrev = tr.climb;
		tr.pace = approach(tr.pace, (float) Mth.clamp(hs / 0.45, 0.0, 1.0), 0.15f);
		tr.fast = approach(tr.fast, p.isSprinting() && hs > 0.3 ? 1f : 0f, 0.12f);
		tr.back = approach(tr.back, hs > 0.05 && fwd < -0.03 ? 1f : 0f, 0.15f);
		tr.climb = approach(tr.climb, (float) Mth.clamp(dy / 0.35, -1.0, 1.0), 0.15f);
	}

	private static void throwFrom(Track tr, long now) {
		int side = tr.nextSide;
		tr.nextSide = -side;
		int i = side > 0 ? 0 : 1;
		tr.prevAt[i] = tr.throwAt[i];
		tr.throwAt[i] = now;
		tr.prevThrow = tr.lastThrow;
		tr.prevSide = tr.lastSide;
		tr.lastThrow = now;
		tr.lastSide = side;
	}

	private static float approach(float v, float target, float rate) {
		float n = v + (target - v) * rate;
		return Math.abs(target - n) < 0.002f ? target : n;
	}

	private static float age(Track tr, String id, float now) {
		Float st = tr.start.get(id);
		return st == null ? 1e9f : now - st;
	}

	private static float w(Track tr, float partial, float prev, float cur) {
		return Mth.lerp(partial, prev, cur);
	}

	// ================================================================ evaluation

	private static final class Ctx {
		HumanoidModel<?> m;
		float aimX, aimY;
		float twist, lean, headX, headFwd, crouch, spread;
		final float[] armW = new float[2];

		void arm(int side, float[] row, float w) {
			arm(side, row, w, 0f, 0f);
		}

		/** Poses one arm toward a row (right-arm values; mirrored for the left), {@code dx} / {@code dy} on top. */
		void arm(int side, float[] row, float w, float dx, float dy) {
			if (m == null || w <= 0.001f) {
				return;
			}
			ModelPart part = side > 0 ? m.rightArm : m.leftArm;
			float x = row[1] + row[4] * aimX + dx;
			float y = side * row[2] + row[5] * aimY + dy;
			float z = side * row[3];
			part.xRot = Mth.lerp(w, part.xRot, x);
			part.yRot = Mth.lerp(w, part.yRot, y);
			part.zRot = Mth.lerp(w, part.zRot, z);
			int i = side > 0 ? 0 : 1;
			armW[i] += (1f - armW[i]) * w;
		}

		void both(float[] row, float w) {
			arm(1, row, w);
			arm(-1, row, w);
		}

		void body(float[] row, int side, float w, float legs) {
			if (w <= 0.001f) {
				return;
			}
			twist = Mth.lerp(w, twist, side * row[1]);
			lean = Mth.lerp(w, lean, row[2]);
			headX = Mth.lerp(w, headX, row[5]);
			headFwd = Mth.lerp(w, headFwd, row[6]);
			crouch = Mth.lerp(w, crouch, row[7]);
			spread = Mth.lerp(w, spread, row[8]);
			if (m != null && legs > 0f) {
				float rl = side > 0 ? row[3] : row[4];
				float ll = side > 0 ? row[4] : row[3];
				float lw = w * legs;
				m.rightLeg.xRot = Mth.lerp(lw, m.rightLeg.xRot, rl);
				m.leftLeg.xRot = Mth.lerp(lw, m.leftLeg.xRot, ll);
				m.rightLeg.yRot = Mth.lerp(lw, m.rightLeg.yRot, 0f);
				m.leftLeg.yRot = Mth.lerp(lw, m.leftLeg.yRot, 0f);
			}
		}

		void leg(ModelPart part, float x, float y, float z, float w) {
			if (m == null || w <= 0.001f) {
				return;
			}
			part.xRot = Mth.lerp(w, part.xRot, x);
			part.yRot = Mth.lerp(w, part.yRot, y);
			part.zRot = Mth.lerp(w, part.zRot, z);
		}
	}

	private static void evaluate(Player p, RavenState s, Ctx c, float partial) {
		Track tr = TRACKS.get(p.getUUID());
		if (tr == null) {
			return;
		}
		long gt = p.level().getGameTime();
		float now = gt + partial;
		float t = p.tickCount + partial;
		int lead = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		float breathe = Mth.sin(t * 0.06f);

		// ---- flight: the hover, the cruise, the sprint and the brake (backward), every limb set so nothing walk-cycles
		float flyW = w(tr, partial, tr.flyWPrev, tr.flyW);
		if (flyW > 0.001f) {
			float pace = w(tr, partial, tr.pacePrev, tr.pace);
			float fast = w(tr, partial, tr.fastPrev, tr.fast) * pace;
			float back = w(tr, partial, tr.backPrev, tr.back) * pace;
			float cruise = Math.max(0f, pace - fast - back);
			float hover = 1f - pace;
			if (c.m != null) {
				for (int side = 1; side >= -1; side -= 2) {
					float ph = side > 0 ? 0f : 1.7f;
					float drift = Mth.sin(t * 0.07f + ph), drift2 = Mth.sin(t * 0.05f + ph + 0.8f);
					float x = hover * (-0.12f + 0.07f * drift) + cruise * (0.42f + 0.04f * drift) + fast * 0.95f + back * -0.45f;
					float y = hover * 0.04f + cruise * 0.06f + fast * 0.08f;
					float z = hover * (0.42f + 0.07f * drift2) + cruise * (0.3f + 0.03f * drift2) + fast * 0.13f + back * 0.32f;
					c.arm(side, new float[] { 0, x, y, z, 0, 0 }, flyW);
				}
				float sway = Mth.sin(t * 0.055f), sway2 = Mth.sin(t * 0.045f + 1.1f);
				// legs together, one knee drifting back; trailing behind as she picks up speed
				float rl = hover * (0.04f + 0.05f * sway) + cruise * 0.16f + fast * 0.1f + back * -0.12f;
				float ll = hover * (0.2f - 0.05f * sway) + cruise * 0.3f + fast * 0.2f + back * 0.06f;
				float inward = 0.025f + 0.01f * sway2;
				c.leg(c.m.rightLeg, rl, 0f, -inward, flyW);
				c.leg(c.m.leftLeg, ll, 0f, inward, flyW);
				c.m.body.xRot = Mth.lerp(flyW, c.m.body.xRot, 0f);
			}
		}

		// ---- meditating: floating cross-legged, hands resting out over the knees
		float medW = w(tr, partial, tr.medWPrev, tr.medW);
		if (medW > 0.001f && c.m != null) {
			c.leg(c.m.rightLeg, -1.42f, -0.62f, 0f, medW);
			c.leg(c.m.leftLeg, -1.42f, 0.62f, 0f, medW);
			c.both(new float[] { 0, -0.55f, 0f, 0.42f + breathe * 0.03f, 0, 0 }, medW);
			c.headX = Mth.lerp(medW, c.headX, 0.12f + breathe * 0.03f);
		}

		// ---- Demonic Ascension held: arms a little out from the sides, palms out
		float ascW = w(tr, partial, tr.ascWPrev, tr.ascW) * (1f - medW);
		if (ascW > 0.001f) {
			c.both(new float[] { 0, -0.05f, 0f, 0.45f + breathe * 0.05f, 0, 0 }, ascW * 0.85f);
		}
		// ---- Trigon's Wrath held: hunched, clawed
		float wrathW = w(tr, partial, tr.wrathWPrev, tr.wrathW);
		if (wrathW > 0.001f) {
			float[] row = WRATH_HOLD_ARM.clone();
			row[1] += breathe * 0.06f;
			c.both(row, wrathW);
			c.body(WRATH_HOLD_BODY, 1, wrathW, 0f);
		}
		// ---- Soul-Self out: a trance, head bowed, palms low and forward
		float soulW = w(tr, partial, tr.soulWPrev, tr.soulW);
		if (soulW > 0.001f) {
			c.both(new float[] { 0, -0.4f + breathe * 0.04f, -0.08f, 0.32f, 0, 0 }, soulW);
			c.headX = Mth.lerp(soulW, c.headX, 0.3f);
		}
		// ---- Debris orbiting: the off hand raised, keeping the blocks up
		float debW = w(tr, partial, tr.debWPrev, tr.debW);
		if (debW > 0.001f) {
			c.arm(-lead, new float[] { 0, -1.9f + breathe * 0.05f, 0.1f, 0.45f, 0, 0 }, debW);
		}
		// ---- Soul Restoration's channel: arms spread, palms up
		float chanW = w(tr, partial, tr.chanWPrev, tr.chanW);
		if (chanW > 0.001f) {
			c.both(new float[] { 0, -0.35f, 0f, 1.05f + breathe * 0.05f, 0, 0 }, chanW);
			c.headX = Mth.lerp(chanW, c.headX, -0.25f);
		}
		// ---- Telekinesis held: the hand on the target, trembling; the other out for balance
		float tkW = w(tr, partial, tr.tkWPrev, tr.tkW);
		if (tkW > 0.001f) {
			float tremble = Mth.sin(now * 2.3f) * 0.03f;
			c.arm(lead, new float[] { 0, -0.08f + tremble, -0.08f, 0f, 1, 1 }, tkW);
			c.arm(-lead, new float[] { 0, -0.45f, 0f, 0.4f, 0, 0 }, tkW);
			c.body(new float[] { 0, -0.15f, 2f, 0f, 0f, 0f, -0.2f, 0f, 0.04f }, lead, tkW, 0f);
		}
		float ta = now - tr.tkReleaseAt;
		if (ta >= 0f && ta < 12f) {
			float w = env(ta, 0.01f, 3.2f, 12f);
			c.arm(lead, key(TK_THROW, ta), w);
			c.body(key(TK_THROW_BODY, ta), lead, w, 0.6f);
		}
		// ---- Soul Devour's beam: the hand on the target, the other to her chest
		float beamW = w(tr, partial, tr.beamWPrev, tr.beamW);
		if (beamW > 0.001f) {
			c.arm(lead, new float[] { 0, -0.05f, -0.05f, 0f, 1, 1 }, beamW);
			c.arm(-lead, CHEST_ARM, beamW);
			c.body(new float[] { 0, -0.2f, -5f, 0f, 0.1f, 0f, 0.3f, 0f, 0.04f }, lead, beamW, 0.5f);
		}

		// ---- moves (newest on top)
		float a;
		a = age(tr, RavenAbilityManager.ASCENSION, now);
		if (a < 26f) {
			float w = env(a, 4f, 16f, 26f);
			c.both(key(ASCEND_ARM, a), w);
			c.body(key(ASCEND_BODY, a), 1, w, 0.5f);
		}
		a = age(tr, RavenAbilityManager.TRIGONS_WRATH, now);
		if (a < 26f) {
			float w = env(a, 3f, 14f, 26f) * (1f - smooth((a - 14f) / 12f) * wrathW);
			c.both(key(WRATH_ON_ARM, a), w);
			c.body(key(WRATH_ON_BODY, a), 1, w, 0.5f);
		}
		a = age(tr, RavenAbilityManager.SOUL_SELF, now);
		if (a < 18f) {
			float w = env(a, 3f, 10f, 18f);
			c.both(key(SOUL_ARM, a), w);
			c.body(key(SOUL_BODY, a), 1, w, 0.5f);
		}
		a = age(tr, RavenAbilityManager.SOUL_SHIELD, now);
		if (a < 20f) {
			float w = env(a, 2f, 12f, 20f);
			c.both(key(SHIELD_ARM, a), w);
			c.body(key(SHIELD_BODY, a), 1, w, 0.6f);
		}
		a = age(tr, RavenAbilityManager.TELEKINETIC_CRUSH, now);
		if (a < CRUSH_SLAM + 18f) {
			float w = env(a, 2.5f, CRUSH_SLAM + 8f, CRUSH_SLAM + 18f);
			float[] row = key(CRUSH_ARM, a).clone();
			if (a > RavenConfig.CRUSH_RISE_TICKS && a < CRUSH_SLAM) {
				row[1] += Mth.sin(now * 2.6f) * 0.05f;
				row[3] += Mth.sin(now * 2.1f) * 0.03f;
			}
			c.both(row, w);
			c.body(key(CRUSH_BODY, a), 1, w, 1f);
		}
		a = age(tr, RavenAbilityManager.ENERGY_WAVE, now);
		if (a < WAVE_GO + 18f) {
			float w = env(a, 2f, WAVE_GO + 6f, WAVE_GO + 18f);
			float[] row = key(WAVE_ARM, a).clone();
			if (a < WAVE_GO) {
				row[1] += Mth.sin(now * 2.8f) * 0.04f * (a / WAVE_GO);
			}
			c.both(row, w);
			c.body(key(WAVE_BODY, a), 1, w, 0.8f);
		}
		a = age(tr, RavenAbilityManager.SHADOW_DASH, now) - RavenConfig.DASH_TICKS;
		if (a >= 0f && a < 12f) {
			float w = env(a, 0f, 3f, 12f);
			c.both(key(DASH_OUT_ARM, a), w);
			c.body(key(DASH_OUT_BODY, a), 1, w, 1f);
		}
		a = age(tr, RavenAbilityManager.SHADOW_STEP, now) - RavenConfig.STEP_SINK_TICKS;
		if (a >= 0f && a < 14f) {
			float w = env(a, 0f, 6f, 14f);
			c.arm(lead, key(ERUPT_LEAD, a), w);
			c.arm(-lead, key(ERUPT_OTHER, a), w);
			c.body(key(ERUPT_BODY, a), lead, w, 1f);
		}

		// ---- spells
		a = age(tr, RavenSpell.VOID_VORTEX.id, now);
		if (a < 24f) {
			float w = env(a, 3f, 16f, 24f);
			float circle = env(a, 3f, 16f, 20f) * 0.12f;
			c.arm(lead, key(VORTEX_ARM, a), w, Mth.sin(now * 0.9f) * circle, Mth.cos(now * 0.9f) * circle);
			c.arm(-lead, CHEST_ARM, w * 0.8f);
			c.body(new float[] { 0, -0.15f, 3f, 0f, 0f, 0f, -0.2f, 0f, 0.04f }, lead, w, 0.5f);
		}
		a = age(tr, RavenSpell.SHADOW_BINDING.id, now);
		if (a < 20f) {
			float w = env(a, 3f, 12f, 20f);
			c.both(key(BIND_ARM, a), w);
			c.body(key(BIND_BODY, a), 1, w, 1f);
		}
		a = age(tr, RavenSpell.SOUL_DETONATION.id, now);
		if (a < DET + 18f) {
			float w = env(a, 3f, DET + 8f, DET + 18f);
			float[] row = key(DETONATE_ARM, a).clone();
			if (a < DET) {
				row[3] += Mth.sin(now * 2.4f) * 0.04f * (a / DET);
			}
			c.both(row, w);
			c.body(key(DETONATE_BODY, a), 1, w, 0.6f);
		}
		a = age(tr, RavenSpell.DEBRIS_STORM.id, now);
		if (a < 22f) {
			float w = env(a, 3f, 12f, 22f);
			c.both(key(DEBRIS_ARM, a), w);
			c.body(key(DEBRIS_BODY, a), 1, w, 0.5f);
		}
		a = age(tr, RavenSpell.DIMENSIONAL_RIFT.id, now);
		if (a < 18f) {
			float w = env(a, 1.5f, 10f, 18f);
			c.arm(lead, key(RIFT_ARM, a), w);
			c.arm(-lead, new float[] { 0, -0.4f, 0f, 0.45f, 0, 0 }, w * 0.8f);
			c.body(key(RIFT_BODY, a), lead, w, 0.8f);
		}
		a = age(tr, RavenSpell.SOUL_RESTORATION.id, now);
		if (a < 22f) {
			float w = env(a, 3f, 14f, 22f) * (1f - chanW);
			c.arm(lead, key(REACH_ARM, a), w);
			c.arm(-lead, CHEST_ARM, w * 0.8f);
		}
		a = age(tr, RavenSpell.TELEKINETIC_PULL.id, now);
		if (a < 16f) {
			float w = env(a, 2f, 8f, 16f);
			c.arm(lead, key(PULL_ARM, a), w);
			c.body(key(PULL_BODY, a), lead, w, 0.6f);
		}
		a = age(tr, RavenSpell.ASTRAL_SANCTUARY.id, now);
		if (a < 34f) {
			float w = env(a, 3f, 24f, 34f);
			c.both(key(SANCTUARY_ARM, a), w);
			c.body(key(SANCTUARY_BODY, a), 1, w, 0.6f);
		}
		a = age(tr, RavenSpell.SHADOW_VEIL.id, now);
		if (a < 22f) {
			float w = env(a, 3f, 14f, 22f);
			c.both(key(VEIL_ARM, a), w);
			c.body(key(VEIL_BODY, a), 1, w, 0.6f);
		}

		// ---- Dark Bolt / Debris throws: per hand, the previous throw under the newest so quick casts blend
		float pa = now - tr.prevThrow, la = now - tr.lastThrow;
		c.body(key(BOLT_BODY, pa), tr.prevSide, env(pa, BOLT_IN, BOLT_HOLD, BOLT_OUT), 0.5f);
		c.body(key(BOLT_BODY, la), tr.lastSide, env(la, BOLT_IN, BOLT_HOLD, BOLT_OUT), 0.5f);
		for (int i = 0; i < 2; i++) {
			int side = i == 0 ? 1 : -1;
			float own = env(now - tr.throwAt[i], BOLT_IN, BOLT_HOLD, BOLT_OUT);
			float otherAge = now - tr.throwAt[1 - i];
			c.arm(side, key(BOLT_OTHER, otherAge), env(otherAge, BOLT_IN, BOLT_HOLD, BOLT_OUT) * 0.7f * (1f - own));
		}
		for (int i = 0; i < 2; i++) {
			int side = i == 0 ? 1 : -1;
			float a0 = now - tr.prevAt[i], a1 = now - tr.throwAt[i];
			c.arm(side, key(BOLT_ARM, a0), env(a0, BOLT_IN, BOLT_HOLD, BOLT_OUT));
			c.arm(side, key(BOLT_ARM, a1), env(a1, BOLT_IN, BOLT_HOLD, BOLT_OUT));
		}
	}

	// ================================================================ the model (every viewer)

	/** From {@code RavenPoseMixin}, at the end of {@code PlayerModel.setupAnim}. */
	public static void apply(Player player, HumanoidModel<?> m) {
		RavenState s = Raven.peek(player);
		if (s == null || !s.hasPower || player.level() == null || player.isPassenger()) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		Track tr = TRACKS.get(player.getUUID());
		// in the air there is no walking: clear vanilla's walk cycle before the flight pose blends in (it eases from here, so the
		// swing never shows, not even while the blend is still coming in)
		if (tr != null && s.flying && !s.meditating) {
			m.rightLeg.xRot = m.leftLeg.xRot = 0f;
			m.rightLeg.yRot = m.leftLeg.yRot = 0f;
			m.rightLeg.zRot = m.leftLeg.zRot = 0f;
			if (player.attackAnim <= 0f) {
				m.rightArm.xRot = m.leftArm.xRot = 0f;
				m.rightArm.yRot = m.leftArm.yRot = 0f;
			}
		}
		Ctx c = new Ctx();
		c.m = m;
		c.aimX = m.head.xRot - Mth.HALF_PI;
		c.aimY = m.head.yRot;
		evaluate(player, s, c, partial);

		if (Math.abs(c.twist) > 1e-4f) {
			float t = c.twist;
			m.body.yRot += t;
			float sin = Mth.sin(t) * 5f, cos = Mth.cos(t) * 5f;
			m.rightArm.z += sin;
			m.rightArm.x += 5f - cos;
			m.leftArm.z -= sin;
			m.leftArm.x -= 5f - cos;
			m.rightArm.yRot += t * (1f - c.armW[0]);
			m.leftArm.yRot += t * (1f - c.armW[1]);
		}
		if (Math.abs(c.headX) > 1e-4f || Math.abs(c.headFwd) > 1e-4f) {
			m.head.xRot += c.headX;
			m.head.z += c.headFwd;
			m.hat.copyFrom(m.head);
		}
		if (Math.abs(c.spread) > 1e-4f) {
			m.rightLeg.zRot += c.spread;
			m.leftLeg.zRot -= c.spread;
		}
		if (c.crouch > 0.01f) {
			QuirkKeyframes.crouch(m, c.crouch);
		}
	}

	private static final Map<Integer, float[]> LEAN_CACHE = new HashMap<>();

	/**
	 * From {@code FlightPoseHelper.lean}: her share of the whole-body lean (degrees forward). Flying, the shared flight lean (25 cruising,
	 * 90 flat out, plus the dive / climb) is eased down to a sorceress's lean -- about 20 cruising, about 50 sprinting, never flat --
	 * and the moves' own leans (rocked back by the Soul Shield, into the Crush's slam, the wave...) add on top.
	 */
	public static float adjustLean(Player player, float base, float partial) {
		RavenState s = Raven.peek(player);
		if (s == null || !s.hasPower || player.level() == null || player.isPassenger()) {
			return base;
		}
		float now = player.level().getGameTime() + partial;
		float[] hit = LEAN_CACHE.get(player.getId());
		if (hit != null && hit[0] == now && hit[1] == base) {
			return hit[2];
		}
		float flight = base;
		if (s.meditating) {
			flight = 0f;
		} else if (s.flying || Math.abs(base) > 0.01f) {
			flight = base <= 25f ? base * 0.8f : 20f + (base - 25f) * 0.45f;
		}
		Ctx c = new Ctx();
		evaluate(player, s, c, partial);
		float out = flight + c.lean;
		LEAN_CACHE.put(player.getId(), new float[] { now, base, out });
		return out;
	}

	// ================================================================ first person (the local player's bare arms)

	// rows: {tick, pitch, yaw, roll, x, y, z} (QuirkFirstPersonPose: pitch - = up into view, yaw + = toward the centre,
	// x + = toward the centre, y + = up, z - = into the screen)
	private static final float[][] FP_BOLT = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 0.9f, 18f, -15f, 0f, -0.08f, 0.1f, 0.08f },
			{ 2f, -14f, 22f, 6f, 0.18f, 0.14f, -0.36f },
			{ 4.5f, -10f, 20f, 5f, 0.16f, 0.1f, -0.3f },
			{ 11f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[] FP_REACH = { 0f, -20f, 18f, 0f, 0.14f, 0.12f, -0.3f };
	private static final float[] FP_PALMS = { 0f, -58f, 20f, -8f, 0.06f, 0.1f, -0.04f };
	private static final float[][] FP_CRUSH = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ RavenConfig.CRUSH_RISE_TICKS, -72f, 22f, 0f, 0.22f, 0.42f, -0.05f },
			{ CRUSH_SLAM - 2f, -78f, 22f, 0f, 0.22f, 0.48f, -0.05f },
			{ CRUSH_SLAM + 0.5f, -84f, 22f, 0f, 0.22f, 0.52f, -0.05f },
			{ CRUSH_SLAM + 2f, 30f, 20f, 0f, 0.25f, -0.3f, -0.35f },
			{ CRUSH_SLAM + 8f, 28f, 20f, 0f, 0.25f, -0.28f, -0.32f },
			{ CRUSH_SLAM + 18f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] FP_WAVE = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ WAVE_GO * 0.5f, -25f, 45f, 0f, 0.42f, 0.1f, 0f },
			{ WAVE_GO, -28f, 48f, 0f, 0.44f, 0.12f, 0f },
			{ WAVE_GO + 1.5f, -18f, -30f, -10f, -0.25f, 0.15f, -0.35f },
			{ WAVE_GO + 6f, -15f, -35f, -10f, -0.3f, 0.12f, -0.3f },
			{ WAVE_GO + 18f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[] FP_CHEST = { 0f, -20f, 40f, 10f, 0.35f, 0f, 0.15f };
	private static final float[] FP_UP = { 0f, -62f, -20f, -18f, -0.15f, 0.42f, 0f };
	private static final float[] FP_DOWN = { 0f, 25f, 25f, 0f, 0.2f, -0.1f, -0.35f };
	private static final float[] FP_WIDE = { 0f, -22f, -32f, -22f, -0.3f, 0.18f, 0f };
	private static final float[] FP_CLOSE = { 0f, -15f, 40f, 0f, 0.4f, 0.1f, -0.3f };
	private static final float[][] FP_RIFT = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 2f, -70f, 20f, 10f, 0.15f, 0.45f, -0.1f },
			{ 5f, 20f, 30f, 0f, 0.25f, -0.2f, -0.3f },
			{ 10f, 18f, 28f, 0f, 0.24f, -0.18f, -0.28f },
			{ 18f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] FP_PULL = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 2.5f, -18f, 15f, 0f, 0.12f, 0.12f, -0.32f },
			{ 5f, -18f, 15f, 0f, 0.12f, 0.12f, -0.32f },
			{ 8f, 10f, 40f, 0f, 0.35f, -0.05f, 0.15f },
			{ 16f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[][] FP_SANCTUARY = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 5f, -75f, 10f, 0f, 0.1f, 0.45f, 0f },
			{ 10f, -72f, 10f, 0f, 0.1f, 0.42f, 0f },
			{ 16f, -25f, -35f, -25f, -0.3f, 0.2f, 0f },
			{ 24f, -22f, -35f, -25f, -0.3f, 0.18f, 0f },
			{ 34f, 0f, 0f, 0f, 0f, 0f, 0f } };
	private static final float[] FP_CROSS = { 0f, -30f, 50f, 10f, 0.45f, 0.05f, 0.05f };
	private static final float[][] FP_ERUPT = {
			{ 0f, 0f, 0f, 0f, 0f, 0f, 0f },
			{ 2f, -70f, 15f, 0f, 0.12f, 0.45f, -0.2f },
			{ 6f, -65f, 15f, 0f, 0.12f, 0.42f, -0.2f },
			{ 14f, 0f, 0f, 0f, 0f, 0f, 0f } };
	/** The off arm out of view (vanilla never draws an empty off hand, so it slides in from below). */
	private static final float[] FP_HIDDEN = { 0f, 35f, 0f, 0f, 0f, -0.9f, 0.25f };
	private static final float[] FP_REST = { 0f, 0f, 0f, 0f, 0f, 0f, 0f };

	private static final class Fp {
		final float[] main = FP_REST.clone();
		final float[] off = FP_HIDDEN.clone();

		void lerp(float[] dst, float[] row, float w) {
			if (w <= 0.001f) {
				return;
			}
			for (int i = 1; i < 7; i++) {
				dst[i] = Mth.lerp(w, dst[i], row[i]);
			}
		}

		void both(float[] row, float w) {
			lerp(main, row, w);
			lerp(off, row, w);
		}
	}

	/** The local player's first-person arm sample (bare main hand), or null for vanilla. In flight the arms stay vanilla. */
	public static QuirkFirstPersonPose.Sample firstPerson(Player p, float partial) {
		RavenState s = Raven.peek(p);
		if (s == null || !s.hasPower || !p.getMainHandItem().isEmpty() || Raven.inShadow(p)) {
			return null;
		}
		Track tr = TRACKS.get(p.getUUID());
		if (tr == null) {
			return null;
		}
		float now = p.level().getGameTime() + partial;
		int mainSide = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		float breathe = Mth.sin((p.tickCount + partial) * 0.12f) * 2f;
		Fp f = new Fp();

		f.both(FP_WIDE, w(tr, partial, tr.chanWPrev, tr.chanW));
		f.lerp(f.main, FP_REACH, w(tr, partial, tr.tkWPrev, tr.tkW));
		float beamW = w(tr, partial, tr.beamWPrev, tr.beamW);
		f.lerp(f.main, FP_REACH, beamW);
		f.lerp(f.off, FP_CHEST, beamW);
		f.lerp(f.off, FP_UP, w(tr, partial, tr.debWPrev, tr.debW) * 0.8f);

		float a;
		a = now - tr.tkReleaseAt;
		if (a >= 0f && a < 12f) {
			f.lerp(f.main, key(FP_BOLT, Math.min(a + 1f, 11f)), env(a, 0.01f, 3.2f, 12f));
		}
		a = age(tr, RavenAbilityManager.ASCENSION, now);
		if (a < 26f) {
			f.both(FP_UP, env(a, 4f, 16f, 26f));
		}
		a = age(tr, RavenAbilityManager.SOUL_SELF, now);
		if (a < 18f) {
			f.both(FP_CHEST, env(a, 3f, 10f, 18f));
		}
		a = age(tr, RavenAbilityManager.SOUL_SHIELD, now);
		if (a < 20f) {
			float[] row = FP_PALMS.clone();
			row[1] += breathe;
			f.both(row, env(a, 2f, 12f, 20f));
		}
		a = age(tr, RavenAbilityManager.TELEKINETIC_CRUSH, now);
		if (a < CRUSH_SLAM + 18f) {
			float[] row = key(FP_CRUSH, a).clone();
			if (a > RavenConfig.CRUSH_RISE_TICKS && a < CRUSH_SLAM) {
				row[1] += Mth.sin(now * 2.6f) * 2.5f;
			}
			f.both(row, env(a, 2.5f, CRUSH_SLAM + 8f, CRUSH_SLAM + 18f));
		}
		a = age(tr, RavenAbilityManager.ENERGY_WAVE, now);
		if (a < WAVE_GO + 18f) {
			f.both(key(FP_WAVE, a), env(a, 2f, WAVE_GO + 6f, WAVE_GO + 18f));
		}
		a = age(tr, RavenAbilityManager.SHADOW_STEP, now) - RavenConfig.STEP_SINK_TICKS;
		if (a >= 0f && a < 14f) {
			f.lerp(f.main, key(FP_ERUPT, a), env(a, 0f, 6f, 14f));
		}
		a = age(tr, RavenSpell.VOID_VORTEX.id, now);
		if (a < 24f) {
			float w = env(a, 3f, 16f, 24f);
			float[] row = FP_REACH.clone();
			row[1] += Mth.sin(now * 0.9f) * 6f;
			row[2] += Mth.cos(now * 0.9f) * 6f;
			f.lerp(f.main, row, w);
			f.lerp(f.off, FP_CHEST, w * 0.8f);
		}
		a = age(tr, RavenSpell.SHADOW_BINDING.id, now);
		if (a < 20f) {
			f.both(FP_DOWN, env(a, 3f, 12f, 20f));
		}
		a = age(tr, RavenSpell.SOUL_DETONATION.id, now);
		if (a < DET + 18f) {
			float w = env(a, 3f, DET + 8f, DET + 18f);
			f.both(a < DET ? FP_WIDE : FP_CLOSE, a < DET ? w : w * smooth((a - DET) / 1.5f));
			if (a >= DET && a < DET + 1.5f) {
				f.both(FP_WIDE, w * (1f - smooth((a - DET) / 1.5f)));
			}
		}
		a = age(tr, RavenSpell.DEBRIS_STORM.id, now);
		if (a < 22f) {
			f.both(FP_UP, env(a, 3f, 12f, 22f));
		}
		a = age(tr, RavenSpell.DIMENSIONAL_RIFT.id, now);
		if (a < 18f) {
			f.lerp(f.main, key(FP_RIFT, a), env(a, 1.5f, 10f, 18f));
		}
		a = age(tr, RavenSpell.SOUL_RESTORATION.id, now);
		if (a < 22f) {
			float w = env(a, 3f, 14f, 22f) * (1f - w(tr, partial, tr.chanWPrev, tr.chanW));
			f.lerp(f.main, FP_REACH, w);
			f.lerp(f.off, FP_CHEST, w * 0.8f);
		}
		a = age(tr, RavenSpell.TELEKINETIC_PULL.id, now);
		if (a < 16f) {
			f.lerp(f.main, key(FP_PULL, a), env(a, 2f, 8f, 16f));
		}
		a = age(tr, RavenSpell.ASTRAL_SANCTUARY.id, now);
		if (a < 34f) {
			f.both(key(FP_SANCTUARY, a), env(a, 3f, 24f, 34f));
		}
		a = age(tr, RavenSpell.SHADOW_VEIL.id, now);
		if (a < 22f) {
			f.both(FP_CROSS, env(a, 3f, 14f, 22f));
		}
		// Dark Bolt / Debris throws
		for (int i = 0; i < 2; i++) {
			int side = i == 0 ? 1 : -1;
			float[] dst = side == mainSide ? f.main : f.off;
			float a0 = now - tr.prevAt[i], a1 = now - tr.throwAt[i];
			f.lerp(dst, key(FP_BOLT, a0), env(a0, BOLT_IN, BOLT_HOLD, BOLT_OUT));
			f.lerp(dst, key(FP_BOLT, a1), env(a1, BOLT_IN, BOLT_HOLD, BOLT_OUT));
		}

		boolean offShown = false;
		for (int i = 1; i < 7; i++) {
			offShown |= Math.abs(f.off[i] - FP_HIDDEN[i]) > 1e-3f;
		}
		boolean mainPosed = false;
		for (int i = 1; i < 7; i++) {
			mainPosed |= Math.abs(f.main[i]) > 1e-3f;
		}
		if (!offShown && !mainPosed) {
			return null;
		}
		float[] v = new float[QuirkFirstPersonPose.COLS];
		System.arraycopy(f.main, 1, v, 1, 6);
		System.arraycopy(f.off, 1, v, 7, 6);
		return new QuirkFirstPersonPose.Sample(v, 1f, offShown && p.getOffhandItem().isEmpty());
	}
}
