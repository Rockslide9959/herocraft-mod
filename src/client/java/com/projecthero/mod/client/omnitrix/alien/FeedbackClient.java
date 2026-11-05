package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.feedback.FeedbackFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.feedback.FeedbackKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 Feedback's client kit (user: "build a feedback alien please ... use this skin:
 * https://www.planetminecraft.com/skin/feedback-ben-10-5251468/ Give him the tail and long ear things with the plug things at the end
 * please"). The user's skin on the player body (x1.05, lanky), its painted chest Omnitrix painted out
 * ({@code scratchpad/ben10_v0171/gen_feedback.js}) and the mod's badge in its place on his white chest panel; his one green eye drawn
 * full-bright ({@code feedback_glow.png}). The two antenna cables, the tail and their gold plugs are
 * {@link FeedbackCables}; his bolts {@link FeedbackFx}. The HUD shows his Charge.
 */
public final class FeedbackClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/feedback.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/omnitrix/feedback_glow.png");
	/** The chest badge (body-bone pixels): where the skin painted it (jacket texels 23..24 x 37..39), on the white panel. */
	public static final float[] BADGE = { 0f, 2.5f, -2.3f, 2.6f };

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.FEEDBACK.id(), new FeedbackClient());
		if (!registered) {
			registered = true;
			FeedbackFx.register();
		}
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, head x)
	// v0.17.2 round 3 (user: "edit and update animations on all moves please"): long loose limbs, whip-quick. A move lands on the press,
	// so each clip reaches its shape in two or three ticks, follows through past it and eases home.
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float rly, float rlz, float llx,
			float lly, float llz, float hx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, rly, rlz, llx, lly, llz, hx);
	}

	/** Feedback Blast: a two-finger snap of the arm, a kick up from the shot, the off arm dragged back behind it. */
	private static final float[][] BLAST = { QuirkKeyframes.rest(0), f(2, -1.7f, 0.12f, 0, 0.5f, 0, -0.3f, 0.08f, -0.45f, -0.25f, 0, 0, 0.3f, 0, 0, 0),
			f(4, -1.95f, 0.12f, 0.08f, 0.55f, 0, -0.32f, 0.0f, -0.52f, -0.2f, 0, 0, 0.26f, 0, 0, -0.06f),
			f(7, -1.68f, 0.1f, 0.04f, 0.38f, 0, -0.26f, 0.05f, -0.36f, -0.15f, 0, 0, 0.2f, 0, 0, 0), QuirkKeyframes.rest(12) };
	/** Rapid Fire: right, left, right, left, right, the shoulders rocking from one to the other on planted feet. */
	private static final float[][] RAPID = { QuirkKeyframes.rest(0), f(2, -1.72f, 0.15f, 0, 0.45f, 0, 0, 0.08f, -0.42f, -0.3f, 0, 0, 0.35f, 0, 0, 0),
			f(5, 0.45f, 0, 0, -1.72f, -0.15f, 0, 0.08f, 0.42f, -0.3f, 0, 0, 0.35f, 0, 0, 0), f(8, -1.72f, 0.15f, 0, 0.45f, 0, 0, 0.08f, -0.42f, -0.3f, 0, 0, 0.35f, 0, 0, 0),
			f(11, 0.45f, 0, 0, -1.72f, -0.15f, 0, 0.08f, 0.42f, -0.3f, 0, 0, 0.35f, 0, 0, 0), f(14, -1.8f, 0.15f, 0, 0.5f, 0, 0, 0.04f, -0.5f, -0.3f, 0, 0, 0.35f, 0, 0, -0.05f),
			f(17, -1.3f, 0.1f, 0.1f, 0.3f, 0, -0.1f, 0.04f, -0.3f, -0.15f, 0, 0, 0.2f, 0, 0, 0), QuirkKeyframes.rest(21) };
	/** Absorb's held stance: feet planted wide, arms spread wide and forward, palms out to take it, chin up. */
	private static final float[] ABSORB_HOLD = f(0, -1.0f, 0.35f, 0.95f, -1.0f, -0.35f, -0.95f, -0.14f, 0, -0.15f, 0, 0.16f, 0.25f, 0, -0.16f, -0.12f);
	/** The arms snap open past the stance and settle into it. */
	private static final float[][] ABSORB = { QuirkKeyframes.rest(0), f(3, -0.8f, 0.35f, 1.2f, -0.8f, -0.35f, -1.2f, -0.22f, 0, -0.15f, 0, 0.2f, 0.25f, 0, -0.2f, -0.2f),
			f(7, -1.0f, 0.35f, 0.95f, -1.0f, -0.35f, -0.95f, -0.14f, 0, -0.15f, 0, 0.16f, 0.25f, 0, -0.16f, -0.12f) };
	/** Letting go of Absorb: the arms come down and he shakes it out. */
	private static final float[][] ABSORB_END = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1, -1.0f, 0.35f, 0.95f, -1.0f, -0.35f, -0.95f, -0.14f, 0, -0.15f, 0, 0.16f, 0.25f, 0, -0.16f, -0.12f),
			f(5, 0.1f, 0, 0.35f, 0.1f, 0, -0.35f, 0.16f, 0, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(10) };
	/** An absorbed hit: rocked back on his heels, arms blown wider, and back into the stance. */
	private static final float[][] TAKE = { ABSORB_HOLD.clone(), f(2, -0.7f, 0.35f, 1.25f, -0.7f, -0.35f, -1.25f, -0.38f, 0, -0.3f, 0, 0.2f, 0.4f, 0, -0.2f, -0.3f),
			f(7, -1.0f, 0.35f, 0.95f, -1.0f, -0.35f, -0.95f, -0.14f, 0, -0.15f, 0, 0.16f, 0.25f, 0, -0.16f, -0.12f) };
	/** Redirect: both hands rammed out together, the recoil driving him back a step. */
	private static final float[][] REDIRECT = { QuirkKeyframes.rest(0), f(2, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, 0.32f, 0, -0.55f, 0, 0, 0.45f, 0, 0, -0.15f),
			f(5, -1.85f, -0.2f, 0, -1.85f, 0.2f, 0, 0.05f, 0, -0.4f, 0, 0, 0.5f, 0, 0, -0.1f), f(11, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, 0.1f, 0, -0.35f, 0, 0, 0.4f, 0, 0, 0),
			QuirkKeyframes.rest(16) };
	/** Conductoid Leap: springs off bent legs, arms swept behind, tucked at the top, and reaching for the ground coming down. */
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), f(2, 1.1f, 0, 0.3f, 1.1f, 0, -0.3f, 0.55f, 0, 0.5f, 0, 0, -0.9f, 0, 0, -0.35f),
			f(7, 0.7f, 0, 0.5f, 0.7f, 0, -0.5f, 0.3f, 0, -1.1f, 0, 0, -0.5f, 0, 0, -0.2f), f(13, -0.5f, 0, 0.9f, -0.5f, 0, -0.9f, 0.1f, 0, -0.6f, 0, 0.08f, 0.2f, 0, -0.08f, 0),
			QuirkKeyframes.rest(19) };
	/** Surge Flight's take-off: a dip, then the arms snap back and down as the jets light. */
	private static final float[][] SURGE = { QuirkKeyframes.rest(0), f(2, 0.3f, 0, 0.5f, 0.3f, 0, -0.5f, 0.35f, 0, -0.7f, 0, 0, -0.7f, 0, 0, -0.15f),
			f(5, 0.75f, 0, 0.5f, 0.75f, 0, -0.5f, 0.05f, 0, 0.25f, 0, 0, 0.1f, 0, 0, 0), QuirkKeyframes.rest(9) };
	/** Coming out of Surge Flight: legs swing down under him, arms out to catch his balance. */
	private static final float[][] LAND = { QuirkKeyframes.rest(0), f(3, -0.4f, 0, 0.95f, -0.4f, 0, -0.95f, -0.08f, 0, -0.4f, 0, 0.08f, 0.2f, 0, -0.08f, 0), QuirkKeyframes.rest(10) };
	/** Plug Whip: the hips throw the tail -- a coil to one side, a hard twist through, the arms flung the other way. */
	private static final float[][] WHIP = { QuirkKeyframes.rest(0), f(2, 0.3f, 0, 0.75f, -0.75f, 0, -0.5f, 0.05f, 0.85f, 0.25f, 0, 0, -0.25f, 0, 0, 0),
			f(5, -0.6f, 0, 1.0f, 0.4f, 0, -0.95f, 0.1f, -0.95f, -0.3f, 0, 0, 0.3f, 0, 0, 0), f(9, -0.35f, 0, 0.75f, 0.25f, 0, -0.75f, 0.03f, -0.6f, -0.1f, 0, 0, 0.1f, 0, 0, 0),
			QuirkKeyframes.rest(14) };
	/** Plug In's held stance: braced, leaning into the cords with both hands reaching for it. */
	private static final float[] PLUG_HOLD = f(0, -1.3f, 0.3f, 0.15f, -1.3f, -0.3f, -0.15f, 0.2f, 0, -0.4f, 0, 0.1f, 0.4f, 0, -0.1f, 0.05f);
	private static final float[][] PLUG = { QuirkKeyframes.rest(0), f(3, -1.5f, 0.3f, 0.15f, -1.5f, -0.3f, -0.15f, 0.32f, 0, -0.5f, 0, 0.1f, 0.45f, 0, -0.1f, 0.05f),
			f(7, -1.3f, 0.3f, 0.15f, -1.3f, -0.3f, -0.15f, 0.2f, 0, -0.4f, 0, 0.1f, 0.4f, 0, -0.1f, 0.05f) };
	/** The cords let go and he rocks back upright. */
	private static final float[][] PLUG_END = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1, -1.3f, 0.3f, 0.15f, -1.3f, -0.3f, -0.15f, 0.2f, 0, -0.4f, 0, 0.1f, 0.4f, 0, -0.1f, 0.05f),
			f(5, -0.1f, 0, 0.3f, -0.1f, 0, -0.3f, -0.12f, 0, -0.1f, 0, 0, 0.1f, 0, 0, -0.1f), QuirkKeyframes.rest(10) };
	/** Power Feed: both hands drawn in to his chest panel, head thrown back as it floods in. */
	private static final float[][] FEED = { QuirkKeyframes.rest(0), f(3, -1.35f, -0.9f, 0, -1.35f, 0.9f, 0, 0.18f, 0, -0.1f, 0, 0, 0.1f, 0, 0, 0.3f),
			f(9, -1.45f, -1.0f, 0, -1.45f, 1.0f, 0, -0.22f, 0, 0, 0, 0, 0, 0, 0, -0.5f), f(13, -1.2f, -0.9f, 0, -1.2f, 0.9f, 0, -0.14f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.rest(18) };
	/** Overclock: arched back, fists down and out, shaking with the power running through him. */
	private static final float[][] OVERCLOCK = { QuirkKeyframes.rest(0), f(3, 0.35f, 0, 0.6f, 0.35f, 0, -0.6f, -0.38f, 0, 0.05f, 0, 0.16f, 0.05f, 0, -0.16f, -0.45f),
			f(6, 0.3f, 0, 0.52f, 0.4f, 0, -0.68f, -0.32f, 0.07f, 0.05f, 0, 0.16f, 0.05f, 0, -0.16f, -0.4f),
			f(9, 0.42f, 0, 0.68f, 0.28f, 0, -0.52f, -0.36f, -0.07f, 0.05f, 0, 0.16f, 0.05f, 0, -0.16f, -0.42f),
			f(12, 0.3f, 0, 0.54f, 0.38f, 0, -0.64f, -0.3f, 0.05f, 0.05f, 0, 0.14f, 0.05f, 0, -0.14f, -0.36f), QuirkKeyframes.rest(18) };
	/** Full Discharge: everything leaves him at once -- flung open, up on his toes, then the spent sag. */
	private static final float[][] DISCHARGE = { QuirkKeyframes.rest(0), f(2, -0.3f, 0, 1.75f, -0.3f, 0, -1.75f, -0.42f, 0, 0.1f, 0, 0.32f, 0.1f, 0, -0.32f, -0.5f),
			f(9, -0.45f, 0, 1.55f, -0.45f, 0, -1.55f, -0.34f, 0, 0.1f, 0, 0.3f, 0.1f, 0, -0.3f, -0.4f),
			f(14, -0.45f, 0, 1.6f, -0.45f, 0, -1.6f, -0.36f, 0, 0.1f, 0, 0.3f, 0.1f, 0, -0.3f, -0.42f), f(19, 0.15f, 0, 0.35f, 0.15f, 0, -0.35f, 0.42f, 0, -0.25f, 0, 0, 0.25f, 0, 0, 0.35f),
			QuirkKeyframes.rest(26) };
	/** Feedback Cannon: heels of both hands together at the end of his arms, a braced stance, the beam pushing him back. */
	private static final float[][] CANNON = { QuirkKeyframes.rest(0), f(2, -1.6f, -0.4f, 0, -1.6f, 0.4f, 0, 0.26f, 0, -0.6f, 0, 0, 0.55f, 0, 0, -0.2f),
			f(6, -1.8f, -0.4f, 0, -1.8f, 0.4f, 0, 0.02f, 0, -0.5f, 0, 0, 0.6f, 0, 0, -0.1f), f(13, -1.65f, -0.4f, 0, -1.65f, 0.4f, 0, 0.08f, 0, -0.45f, 0, 0, 0.5f, 0, 0, -0.1f),
			QuirkKeyframes.rest(19) };
	/** Plug Fingers: a lanky straight right. */
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.65f, 0.2f, 0, 0.3f, 0, -0.1f, 0.18f, -0.5f, -0.35f, 0, 0, 0.3f, 0, 0, 0), QuirkKeyframes.rest(7) };

	// v0.17.2 round 3: every move has a first-person clip now
	private static final FirstPersonClip FP_BLAST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -24, 8, 0, 0.06f, 0.14f, -0.42f),
			FirstPersonClip.f(4, -36, 8, 0, 0.06f, 0.2f, -0.3f), FirstPersonClip.f(7, -22, 6, 0, 0.05f, 0.12f, -0.3f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_RAPID = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -24, 8, 0, 0.06f, 0.14f, -0.42f),
			FirstPersonClip.f(4, -14, 6, 0, 0.04f, 0.04f, -0.14f), FirstPersonClip.f(6, -24, 8, 0, 0.06f, 0.14f, -0.42f), FirstPersonClip.f(8, -14, 6, 0, 0.04f, 0.04f, -0.14f),
			FirstPersonClip.f(10, -24, 8, 0, 0.06f, 0.14f, -0.42f), FirstPersonClip.f(12, -14, 6, 0, 0.04f, 0.04f, -0.14f), FirstPersonClip.f(14, -24, 8, 0, 0.06f, 0.14f, -0.42f),
			FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	/** Absorb, for as long as V is held: palms up and out at the edges of the view (a clip this long ends when ANIM_ABSORB_END replaces it). */
	private static final FirstPersonClip FP_ABSORB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -24, -22, -16, -0.12f, 0.2f, -0.2f),
			FirstPersonClip.f(5, -20, -16, -12, -0.08f, 0.16f, -0.22f), FirstPersonClip.f(100000, -20, -16, -12, -0.08f, 0.16f, -0.22f)).both();
	private static final FirstPersonClip FP_ABSORB_END = FirstPersonClip.of(FirstPersonClip.f(0, -20, -16, -12, -0.08f, 0.16f, -0.22f), FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TAKE = FirstPersonClip.of(FirstPersonClip.f(0, -20, -16, -12, -0.08f, 0.16f, -0.22f), FirstPersonClip.f(2, -34, -26, -20, -0.14f, 0.26f, -0.04f),
			FirstPersonClip.f(7, -20, -16, -12, -0.08f, 0.16f, -0.22f), FirstPersonClip.f(100000, -20, -16, -12, -0.08f, 0.16f, -0.22f)).both();
	private static final FirstPersonClip FP_REDIRECT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -18, 14, 0, 0.14f, 0.14f, -0.5f),
			FirstPersonClip.f(5, -30, 14, 0, 0.14f, 0.2f, -0.34f), FirstPersonClip.f(11, -20, 12, 0, 0.12f, 0.14f, -0.36f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LEAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 34, 0, 0, 0, -0.45f, 0.1f),
			FirstPersonClip.f(9, 26, 0, 0, 0, -0.38f, 0.08f), FirstPersonClip.f(14, -10, -20, -14, -0.08f, 0.04f, -0.1f), FirstPersonClip.f(19, 0, 0, 0, 0, 0, 0)).both();
	/** Surge Flight, for as long as it lasts: the hands swept down and back out of the view (ended by ANIM_LAND). */
	private static final FirstPersonClip FP_SURGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, 42, 0, 0, 0, -0.6f, 0.15f),
			FirstPersonClip.f(100000, 42, 0, 0, 0, -0.6f, 0.15f)).both();
	private static final FirstPersonClip FP_LAND = FirstPersonClip.of(FirstPersonClip.f(0, 42, 0, 0, 0, -0.6f, 0.15f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WHIP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -14, -34, -20, -0.2f, 0.08f, -0.12f),
			FirstPersonClip.f(5, -18, 38, 16, 0.3f, 0.08f, -0.26f), FirstPersonClip.f(9, -10, 26, 10, 0.2f, 0.04f, -0.16f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0));
	/** Plug In, while plugged: hands reaching out at it (ended by ANIM_PLUG_END). */
	private static final FirstPersonClip FP_PLUG = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -24, 10, 0, 0.1f, 0.16f, -0.46f),
			FirstPersonClip.f(6, -20, 10, 0, 0.1f, 0.12f, -0.38f), FirstPersonClip.f(100000, -20, 10, 0, 0.1f, 0.12f, -0.38f)).both();
	private static final FirstPersonClip FP_PLUG_END = FirstPersonClip.of(FirstPersonClip.f(0, -20, 10, 0, 0.1f, 0.12f, -0.38f), FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FEED = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 34, 14, 0.2f, 0.0f, -0.14f),
			FirstPersonClip.f(9, -36, 40, 16, 0.24f, -0.06f, -0.06f), FirstPersonClip.f(13, -28, 32, 14, 0.2f, -0.02f, -0.1f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_OVERCLOCK = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, -6, -10, -0.04f, 0.26f, -0.16f),
			FirstPersonClip.f(6, -26, -10, -6, -0.06f, 0.22f, -0.18f), FirstPersonClip.f(9, -33, -3, -12, -0.02f, 0.28f, -0.15f), FirstPersonClip.f(12, -27, -8, -8, -0.05f, 0.24f, -0.17f),
			FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DISCHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -18, -44, -34, -0.24f, 0.14f, -0.14f),
			FirstPersonClip.f(9, -22, -38, -30, -0.2f, 0.16f, -0.16f), FirstPersonClip.f(14, -18, -42, -32, -0.22f, 0.14f, -0.14f), FirstPersonClip.f(19, 22, -6, -6, -0.02f, -0.34f, 0.04f),
			FirstPersonClip.f(26, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CANNON = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -16, 22, 0, 0.22f, 0.12f, -0.5f),
			FirstPersonClip.f(6, -28, 22, 0, 0.22f, 0.18f, -0.32f), FirstPersonClip.f(13, -20, 22, 0, 0.22f, 0.14f, -0.38f), FirstPersonClip.f(19, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -14, 10, 0, 0.08f, 0.1f, -0.44f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		return BADGE;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		// the cords and plugs first (solid), then the full-bright eye
		FeedbackCables.render(pose, buffers, light, p, model, partial, age);
		if (FeedbackKit.holdFlying(p)) {
			FeedbackFx.jets(pose, buffers, model, age);
		}
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF8C8C8C); // a soft glow: the skin's own green shows through
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// v0.17.2 round 2 (user: "remove the plugs on his fingers please, they dont look good in first person either"): the skin's own arm
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		FeedbackCables.tick(p);
		ThrustFlightPose.tick(p, FeedbackKit.holdFlying(p));
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		// the held stances go on first and a clip plays over them, so anything he does mid-stance blends back into it
		boolean held = false;
		float air = ThrustFlightPose.amount(p, age - Mth.floor(age));
		if (air > 0f) {
			flightPose(p, m, age, air);
			held = true;
		}
		if ((flags & FeedbackKit.F_ABSORB) != 0) {
			QuirkKeyframes.apply(m, ABSORB_HOLD, 1f);
			m.rightArm.zRot += Mth.sin(age * 0.6f) * 0.04f;
			m.leftArm.zRot -= Mth.sin(age * 0.6f) * 0.04f;
			m.rightArm.xRot += Mth.sin(age * 0.23f) * 0.05f;
			m.leftArm.xRot += Mth.sin(age * 0.23f + 1f) * 0.05f;
			held = true;
		}
		if ((flags & FeedbackKit.F_PLUG) != 0) {
			QuirkKeyframes.apply(m, PLUG_HOLD, 1f);
			m.rightArm.xRot += Mth.sin(age * 1.1f) * 0.03f; // the current shaking his arms
			m.leftArm.xRot -= Mth.sin(age * 1.1f) * 0.03f;
			held = true;
		}
		float[][] clip = switch (animId) {
			case FeedbackKit.ANIM_BLAST -> BLAST;
			case FeedbackKit.ANIM_RAPID -> RAPID;
			case FeedbackKit.ANIM_REDIRECT -> REDIRECT;
			case FeedbackKit.ANIM_LEAP -> LEAP;
			case FeedbackKit.ANIM_SURGE -> SURGE;
			case FeedbackKit.ANIM_LAND -> LAND;
			case FeedbackKit.ANIM_WHIP -> WHIP;
			case FeedbackKit.ANIM_FEED -> FEED;
			case FeedbackKit.ANIM_OVERCLOCK -> OVERCLOCK;
			case FeedbackKit.ANIM_DISCHARGE -> DISCHARGE;
			case FeedbackKit.ANIM_CANNON -> CANNON;
			case FeedbackKit.ANIM_PUNCH -> PUNCH;
			case FeedbackKit.ANIM_TAKE -> (flags & FeedbackKit.F_ABSORB) != 0 ? TAKE : null;
			case FeedbackKit.ANIM_ABSORB -> (flags & FeedbackKit.F_ABSORB) != 0 ? ABSORB : null;
			case FeedbackKit.ANIM_ABSORB_END -> ABSORB_END;
			case FeedbackKit.ANIM_PLUG -> (flags & FeedbackKit.F_PLUG) != 0 ? PLUG : null;
			case FeedbackKit.ANIM_PLUG_END -> PLUG_END;
			default -> null;
		};
		return clip != null && QuirkKeyframes.play(m, clip, ticks, 1f) || held;
	}

	/** Surge Flight: arms swept back down his sides with the palms firing behind him, long legs trailing, the tail streaming after. */
	private static void flightPose(AbstractClientPlayer p, HumanoidModel<?> m, float age, float k) {
		// (near 0: the body lies flat in flight, so any more and the jets would point up out of his back)
		float sway = Mth.sin(age * 0.5f) * 0.03f;
		m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, 0.03f + sway);
		m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, 0f);
		m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0.16f);
		m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, 0.03f - sway);
		m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0f);
		m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -0.16f);
		m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, 0.1f + Mth.sin(age * 0.33f) * 0.07f);
		m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, 0.1f - Mth.sin(age * 0.33f) * 0.07f);
		m.rightLeg.zRot = Mth.lerp(k, m.rightLeg.zRot, 0.05f);
		m.leftLeg.zRot = Mth.lerp(k, m.leftLeg.zRot, -0.05f);
		ThrustFlightPose.headOnAim(p, m, age - Mth.floor(age));
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case FeedbackKit.ANIM_BLAST -> FP_BLAST;
			case FeedbackKit.ANIM_RAPID -> FP_RAPID;
			case FeedbackKit.ANIM_ABSORB -> FP_ABSORB;
			case FeedbackKit.ANIM_ABSORB_END -> FP_ABSORB_END;
			case FeedbackKit.ANIM_TAKE -> FP_TAKE;
			case FeedbackKit.ANIM_REDIRECT -> FP_REDIRECT;
			case FeedbackKit.ANIM_LEAP -> FP_LEAP;
			case FeedbackKit.ANIM_SURGE -> FP_SURGE;
			case FeedbackKit.ANIM_LAND -> FP_LAND;
			case FeedbackKit.ANIM_WHIP -> FP_WHIP;
			case FeedbackKit.ANIM_PLUG -> FP_PLUG;
			case FeedbackKit.ANIM_PLUG_END -> FP_PLUG_END;
			case FeedbackKit.ANIM_FEED -> FP_FEED;
			case FeedbackKit.ANIM_OVERCLOCK -> FP_OVERCLOCK;
			case FeedbackKit.ANIM_DISCHARGE -> FP_DISCHARGE;
			case FeedbackKit.ANIM_CANNON -> FP_CANNON;
			case FeedbackKit.ANIM_PUNCH -> FP_PUNCH;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the HUD: his Charge

	@Override
	public int hudHeight() {
		return 10;
	}

	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		int c = FeedbackKit.charge(p);
		boolean full = c >= FeedbackKit.MAX_CHARGE;
		boolean flash = (p.level().getGameTime() / 6) % 2 == 0;
		String value = full && flash ? "FULL" : String.valueOf(c);
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.feedback.meter"),
				c / (float) FeedbackKit.MAX_CHARGE, 0xFF000000 | FeedbackKit.COLOUR, value, full ? 0xFFBFE6FF : 0xFFE2E2E2);
	}
}
