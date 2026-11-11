package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.minecraft.client.Camera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.rot;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.2 (user: "this is the fourarms transformation from the ben 10 show, can you try and recreate this in minecraft please, do your
 * absolute best ... use vfx and try not to use particles too much"), on the v0.17.3 framework: the episode's Four Arms transformation
 * shot for shot, cut to its own soundtrack. Video second {@code v} of the user's clip is tick {@code (v - 2.55) * 20} (compilation
 * video: {@code (v - 132.585) * 20}); the beats (ticks after the right-click):
 * <pre>
 *   0-5     the slap (front medium shot)                      v 2.55-2.8
 *   4-18    FULL GREEN FLASH                                   v 2.8-3.4
 *   14-29   the dial close-up, green light bursting off it     v 3.3-4.0
 *   29-49   the hand swells -- peach flesh, red veins, red rim v 4.0-5.0   (the cartoon stage: olive field, black Kirby dots)
 *   49-74   up the forearm, the shoulder, the chest            v 5.0-6.25
 *   74-89   the neck and the face                              v 6.25-7.0
 *   87-103  GOLDEN FLASH -- the body changes under it (95)     v 7.0-7.7
 *   101-128 from behind: the second pair of arms splits out    v 7.6-9.0
 *   128-136 whip pan round to the front                        v 9.0-9.35
 *   136-162 the red head, four eyes, the roar (from below)     v 9.35-10.65
 *   162-175 low-angle torso                                    v 10.65-11.3
 *   175-209 the full-body side-on power stance                 v 11.3-13.0
 *   207-216 WHITE FLASH                                        v 13.0-13.35
 *   211-249 the same stance on the green ray burst             v 13.1-15.0
 *   236-249 FULL GREEN FLASH                                   v 14.35-15.0
 *   249-261 back in the world, the green fading off           v 15.0-15.6
 * </pre>
 * The swelling and the poses are on the body, so everyone nearby sees those, plus the dial's light, a golden glow and the usual green
 * burst at the end; the music plays for them too. Everything is flat-colour quads (no particles).
 */
public final class FourArmsShow extends ShowSequenceClient {
	/** The beats (ticks). */
	static final float SLAP_END = 5f, WATCH_AT = 14f, HAND_AT = 29f, ARM_AT = 49f, FACE_AT = 74f, GOLD_AT = 87f, BACK_AT = 101f, WHIP_AT = 128f,
			HEAD_AT = 136f, TORSO_AT = 162f, POSE_AT = 175f, WHITE_AT = 207f, BURST_STAGE_AT = 211f, GREEN2_AT = 236f, WORLD_AT = 249f;
	static final int TRANSFORM_AT = 95;

	/** Colours: the olive stage, the green ray burst. */
	private static final int OLIVE_IN = 0xA9CB63, OLIVE_MID = 0x86AE48, OLIVE_OUT = 0x4C7524;
	private static final int BURST_IN = 0x9CF77A, BURST_MID = 0x3FD33A, BURST_OUT = 0x1D8A18, BURST_RAY = 0x15641A, BURST_DOT = 0x0D3F0B;

	/** The Kirby dots: the olive field's streams; the stance's (a wider clear middle round the whole body); the burst's, dark green. */
	private static final KirbyDots.Style OLIVE_DOTS = KirbyDots.Style.show(),
			STANCE_DOTS = KirbyDots.Style.show().clear(0.42f),
			BURST_DOTS = KirbyDots.Style.show().bands(2).rgb(BURST_DOT).clear(0.5f).flow(1.6f);

	private static final float W = CameraTrack.W;
	/** The camera (see {@link CameraTrack} for the key layout). */
	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(ShowShots.slapShot(0f, SLAP_END), ShowShots.dialCloseUp(5.01f, HAND_AT),
			new float[][] {
					// the hand swelling, from their left front, sliding up the arm
					{ 29.01f, 0.62f, -58f, 1.14f, 1.06f, -0.08f, 0.3f },
					{ 49f, 0.7f, -70f, 1.24f, 1.16f, -0.2f, 0.18f },
					{ 74f, 0.78f, -84f, 1.5f, 1.38f, -0.32f, 0.02f },
					// the face, from low at the side
					{ 74.01f, 0.72f, -18f, 1.4f, 1.6f, 0f, 0f },
					{ 101f, 0.6f, -14f, 1.44f, 1.62f, 0f, 0f },
					// from behind, low: the arms splitting
					{ 101.01f, 2.3f, 180f, 0.62f, 1.25f, 0f, 0f },
					{ 128f, 1.95f, 176f, 0.7f, 1.3f, 0f, 0f },
					// the whip pan round the right side to the face (the angle runs straight, so it sweeps)
					{ 132f, 1.35f, 95f, 1.2f, 1.5f, 0f, 0f },
					{ 136f, 0.92f, 4f, 1.32f, 1.6f, 0f, 0f },
					{ 162f, 0.76f, 0f, 1.3f, 1.63f, 0f, 0f },
					// low-angle torso
					{ 162.01f, 1.65f, 16f, 0.5f, 1.22f, 0f, 0f },
					{ 175f, 1.5f, 14f, 0.48f, 1.2f, 0f, 0f },
					// the full-body side-on stance (and the green burst behind it)
					{ 175.01f, 2.35f, 60f, 0.8f, 0.95f, 0f, 0f },
					{ 249f, 2.1f, 55f, 0.8f, 0.98f, 0f, 0f } }))
			// the whip pan runs at speed through its middle key; the roar shakes the shot a little
			.linear(WHIP_AT, HEAD_AT).shake(HEAD_AT + 8, TORSO_AT, 0.012f, 18f);

	public FourArmsShow() {
		super(Aliens.FOUR_ARMS.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public int burstAt() {
		return 238;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	@Override
	public double fov(float t) {
		// a slow push on the dial, the roar punching in
		double fov = 62;
		if (t >= WATCH_AT && t < HAND_AT) {
			fov -= 8 * smooth((t - WATCH_AT) / 15f);
		} else if (t >= HEAD_AT + 6 && t < TORSO_AT) {
			fov -= 7 * smooth((t - HEAD_AT - 6) / 4f);
		}
		return fov;
	}

	// ================================================================ the poses

	/**
	 * Before the change the raised-watch pose (still up: the dial was never let go) gets a tremble that builds, and the head thrown back
	 * as the face swells; after it, Four Arms' beats: arms flung out as the second pair splits, the roar, the flex, and the side-on
	 * power stance.
	 */
	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		m.head.yRot = 0f;
		if (t < TRANSFORM_AT) {
			float shake = smooth((t - HAND_AT) / 30f) * (t < GOLD_AT ? 1f : 0.5f);
			if (shake > 0f) {
				m.leftArm.xRot += 0.05f * shake * Mth.sin(t * 2.9f);
				m.leftArm.zRot += 0.04f * shake * Mth.cos(t * 3.7f);
				m.rightArm.xRot += 0.04f * shake * Mth.sin(t * 3.3f + 1f);
				m.body.xRot += 0.03f * shake * Mth.sin(t * 2.1f);
			}
			// the face swelling: the head jerks back, the chest arches
			float back = smooth((t - FACE_AT) / 8f);
			if (back > 0f) {
				m.head.xRot = Mth.lerp(back, m.head.xRot, -0.35f + 0.06f * Mth.sin(t * 2.4f));
				m.body.xRot = Mth.lerp(back, m.body.xRot, -0.12f);
				// the swollen arms drop away from the dial, hanging heavy and shaking (and out of the face's shot)
				m.leftArm.y = Mth.lerp(back, m.leftArm.y, 2f);
				m.rightArm.y = Mth.lerp(back, m.rightArm.y, 2f);
				rot(m.leftArm, back, 0.15f + 0.05f * Mth.sin(t * 3.1f), 0f, -0.45f);
				rot(m.rightArm, back, 0.15f + 0.05f * Mth.sin(t * 2.7f), 0f, 0.45f);
			}
			m.hat.copyFrom(m.head);
			return;
		}
		float breathe = Mth.sin(t * 0.25f) * 0.04f;
		if (t < WHIP_AT) {
			// from behind: the arms forced out and up as the second pair splits from them
			float w = smooth((t - TRANSFORM_AT) / 8f);
			rot(m.rightArm, w, 0.15f, 0f, 2.3f + breathe);
			rot(m.leftArm, w, 0.15f, 0f, -2.3f - breathe);
			rot(m.head, w, 0.25f, 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.12f);
		} else if (t < TORSO_AT) {
			// the roar: head thrown back, all four arms wide
			float w = smooth((t - WHIP_AT) / 6f);
			float roar = smooth((t - HEAD_AT - 6) / 4f);
			rot(m.rightArm, w, -0.55f, 0f, 0.95f + breathe);
			rot(m.leftArm, w, -0.55f, 0f, -0.95f - breathe);
			rot(m.head, w, Mth.lerp(roar, -0.15f, -0.6f), 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, -0.08f);
		} else if (t < POSE_AT) {
			// the flex
			float w = smooth((t - TORSO_AT) / 5f);
			rot(m.rightArm, w, -1.15f, 0.25f, 0.55f + breathe);
			rot(m.leftArm, w, -1.15f, -0.25f, -0.55f - breathe);
			rot(m.head, w, -0.25f, 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0f);
		} else {
			// the power stance: legs wide, leaning in, the top fists up and ready
			float w = smooth((t - POSE_AT) / 5f);
			rot(m.rightArm, w, -1.45f + breathe, 0.15f, 0.32f);
			rot(m.leftArm, w, -0.95f - breathe, -0.15f, -0.3f);
			rot(m.rightLeg, w, -0.15f, 0f, 0.38f);
			rot(m.leftLeg, w, 0.15f, 0f, -0.38f);
			rot(m.head, w, -0.1f, 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.18f);
			if (m instanceof PlayerModel<?> pm) {
				pm.rightPants.copyFrom(m.rightLeg);
				pm.leftPants.copyFrom(m.leftLeg);
			}
		}
		if (m instanceof PlayerModel<?> pm) {
			pm.rightSleeve.copyFrom(m.rightArm);
			pm.leftSleeve.copyFrom(m.leftArm);
			pm.jacket.copyFrom(m.body);
		}
		m.hat.copyFrom(m.head);
	}

	/**
	 * Four Arms' lower pair while it plays (from {@code FourArmsClient}): {x, y, z (pivot px), xRot, yRot, zRot} for the right arm (the
	 * left mirrors), and how far they have split out of the upper pair (0 = still inside it). Null when not playing.
	 */
	public static float[] lowerArms(Player p, HumanoidModel<?> m, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.FOUR_ARMS.id());
		if (t < 0f) {
			return null;
		}
		float split = smooth((t - BACK_AT - 2f) / 18f);
		float breathe = Mth.sin(t * 0.25f + 1f) * 0.04f;
		float xr, zr;
		if (t < WHIP_AT) {
			xr = 0.1f;
			zr = 1.15f;
		} else if (t < TORSO_AT) {
			xr = -0.2f;
			zr = 0.55f;
		} else if (t < POSE_AT) {
			xr = -0.45f;
			zr = 0.42f;
		} else {
			xr = -0.55f;
			zr = 0.5f;
		}
		zr += breathe;
		// still inside the upper arm: its pivot and its swing; split out: under it at the ribs, flared
		float x = Mth.lerp(split, m.rightArm.x, -5.4f), y = Mth.lerp(split, m.rightArm.y, 5.4f), z = Mth.lerp(split, m.rightArm.z, 1.0f);
		return new float[] { x, y, z, Mth.lerp(split, m.rightArm.xRot, xr), Mth.lerp(split, m.rightArm.yRot, 0f), Mth.lerp(split, m.rightArm.zRot, zr),
				split };
	}

	/**
	 * The swell through the model's own parts: the left arm thickens as the flesh takes it, then the chest, the right arm and the head;
	 * after the change the upper arms throb as the second pair forces its way out.
	 */
	@Override
	public void bulge(Player p, HumanoidModel<?> m, float t) {
		if (t < TRANSFORM_AT) {
			float throb = 1f + 0.05f * Mth.sin(t * 1.7f);
			float arm = 1f + 0.38f * smooth((t - HAND_AT - 2f) / 34f) * throb;
			float chest = 1f + 0.16f * smooth((t - 58f) / 24f) * throb;
			float rArm = 1f + 0.26f * smooth((t - 70f) / 16f) * throb;
			float head = 1f + 0.1f * smooth((t - FACE_AT - 2f) / 12f) * throb;
			m.leftArm.xScale = m.leftArm.zScale = arm;
			m.rightArm.xScale = m.rightArm.zScale = rArm;
			m.body.xScale = chest;
			m.body.zScale = 1f + (chest - 1f) * 1.4f;
			m.head.xScale = m.head.yScale = m.head.zScale = head;
			m.hat.copyFrom(m.head);
			m.leftArm.x += (arm - 1f) * 1.5f + (chest - 1f) * 3f;
			m.rightArm.x -= (rArm - 1f) * 1.5f + (chest - 1f) * 3f;
		} else if (t < WHIP_AT) {
			float k = 0.12f * (1f - smooth((t - BACK_AT) / 27f)) * (0.6f + 0.4f * Mth.sin(t * 1.9f));
			m.leftArm.xScale = m.leftArm.zScale = 1f + k;
			m.rightArm.xScale = m.rightArm.zScale = 1f + k;
		}
	}

	// ================================================================ the swelling flesh

	/** Bulges ({@link SwellLumps} rows): bone (0 left arm, 1 body, 2 head, 3 right arm), centre x y z (px), half-size (px), erupts at. */
	private static final float[][] BULGES = {
			// the hand and wrist, bubbling out of the watch
			{ 0, 1.2f, 9.4f, -2.1f, 1.5f, 29 }, { 0, 2.9f, 9.6f, 0.2f, 1.4f, 30.5f }, { 0, -0.9f, 8.8f, 0.9f, 1.3f, 32 }, { 0, 1.0f, 10.4f, 0.4f, 1.7f, 33 },
			{ 0, 1.6f, 7.2f, 2.1f, 1.5f, 35 }, { 0, 3.0f, 6.6f, -0.9f, 1.6f, 37 }, { 0, -0.2f, 5.8f, -2.2f, 1.5f, 39 },
			// up the forearm and the upper arm to the shoulder
			{ 0, 1.2f, 4.2f, 2.2f, 1.7f, 42 }, { 0, 3.1f, 3.1f, 0.6f, 1.9f, 45 }, { 0, -1.0f, 3.3f, -0.4f, 1.5f, 47 }, { 0, 1.0f, 1.2f, -2.2f, 1.9f, 50 },
			{ 0, 3.2f, 0.1f, 0.1f, 2.0f, 53 }, { 0, 1.1f, -1.4f, 2.0f, 1.9f, 56 }, { 0, 1.0f, -2.2f, 0.0f, 2.2f, 58 },
			// the chest and the back
			{ 1, 3.4f, 1.2f, -2.1f, 2.0f, 60 }, { 1, 3.5f, 1.6f, 2.1f, 2.0f, 62 }, { 1, 2.8f, 4.4f, -2.2f, 2.2f, 63 }, { 1, 1.0f, 1.4f, -2.3f, 1.8f, 66 },
			{ 1, 0.0f, -0.1f, 0.0f, 1.9f, 69 }, { 1, -1.6f, 3.2f, -2.3f, 2.0f, 70 }, { 1, -2.1f, 0.6f, 2.2f, 1.8f, 72 }, { 1, -3.5f, 1.5f, -2.0f, 2.0f, 74 },
			{ 1, 0.2f, 6.4f, -2.3f, 2.0f, 76 }, { 1, 2.6f, 8.6f, 2.1f, 1.7f, 79 },
			// the right arm
			{ 3, -1.0f, -1.6f, 0.0f, 2.0f, 74 }, { 3, -3.1f, 2.2f, 0.2f, 1.8f, 77 }, { 3, -1.0f, 5.2f, -2.2f, 1.7f, 80 }, { 3, -1.2f, 8.2f, 2.1f, 1.6f, 83 },
			// the neck and the face
			{ 2, 3.6f, -2.0f, -2.0f, 1.8f, 76 }, { 2, 2.4f, -0.6f, -4.0f, 1.5f, 79 }, { 2, 3.9f, -5.0f, 1.0f, 1.7f, 80 }, { 2, -3.5f, -2.4f, -1.6f, 1.8f, 82 },
			{ 2, 0.0f, -7.6f, -2.0f, 2.0f, 84 }, { 2, -2.0f, -1.0f, -4.1f, 1.4f, 86 }, { 2, -3.8f, -5.6f, 1.6f, 1.6f, 87 } };

	/**
	 * The swelling: lumpy peach flesh with red veins and the show's red rim, erupting out of the watch and spreading over the arm, the
	 * chest, the other arm and the face; each lump pops up springily and keeps throbbing. Gone under the golden flash.
	 */
	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < HAND_AT - 1f || t >= TRANSFORM_AT) {
			return;
		}
		ModelPart[] bones = { model.leftArm, model.body, model.head, model.rightArm };
		SwellLumps.render(pose, buffers, bones, BULGES, t, 7f, SwellLumps.SWELL, SwellLumps.SWELL_GLOW);
	}

	/**
	 * After the change: the new arms' red glow as they split out (from {@code FourArmsClient}, on the lower arms' own bones), fading by
	 * the whip pan.
	 */
	public static void renderSplitGlow(PoseStack pose, MultiBufferSource buffers, Player p, ModelPart[] arms, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.FOUR_ARMS.id());
		if (t < TRANSFORM_AT || t >= WHIP_AT + 6) {
			return;
		}
		float k = (1f - smooth((t - BACK_AT - 10f) / 25f)) * (0.7f + 0.3f * Mth.sin(t * 1.6f));
		int c = ShowMath.grey(k);
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(SwellLumps.SWELL_GLOW));
		for (int i = 0; i < arms.length; i++) {
			// the right arm's box runs -3..1 px, the left's -1..3
			float x0 = i == 0 ? -3.6f : -1.6f, x1 = i == 0 ? 1.6f : 3.6f;
			AlienParts.on(pose, arms[i], () -> AlienParts.box(pose, glow, x0, -2.6f, -2.6f, x1, 9.6f, 2.6f, 0f, 0f, 1f, 1f, c, LightTexture.FULL_BRIGHT));
		}
	}

	// ================================================================ in the world: the dial's light, the golden glow

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 3f, 24f);
		ShowShots.bodyGlow(pose, buffers, cam, p, t, partial, GOLD_AT - 2f, 97f, 0xFFC23A, 0xFFF4C8);
	}

	// ================================================================ the holder's screen

	/**
	 * v0.17.2 (user: "recreate the tranformation sequence frame for frame with the background and everything"): every shot has the
	 * episode's own background -- the dim room behind the slap, the green bloom round the dial, the olive field framed by black Kirby
	 * dots through the swelling and the reveal (a warm glow behind the stance), the dark-green ray burst.
	 */
	@Override
	public void drawStage(Stage st, float t) {
		if (t < SLAP_END) {
			st.gradient(0x56682E, 0x2E3C17, 0x0B1005);
		} else if (t < HAND_AT) {
			st.gradient(0xF2FFE0, 0x7CEB4C, 0x2DB51C);
		} else if (t < BURST_STAGE_AT) {
			st.gradient(OLIVE_IN, OLIVE_MID, OLIVE_OUT);
			if (t >= POSE_AT - 4f) {
				// the stance: a red-orange glow behind his body
				float w = smooth((t - POSE_AT + 4f) / 6f);
				st.radial(0xE0743A, 0xB8823E, OLIVE_MID, 0.85f * w, 0f);
			}
			// v0.17.3 (user: "in the show there are lines of tons of clumped together kirby dots in every frame and angle, make ours look
			// like that"): streams of clumped dots sweeping across every shot, a new layout at each cut
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - HAND_AT, t >= POSE_AT ? STANCE_DOTS : OLIVE_DOTS);
		} else {
			st.gradient(BURST_IN, BURST_MID, BURST_OUT);
			st.rays(st.seed, t - BURST_STAGE_AT, BURST_RAY, 16);
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)) + 5, t - BURST_STAGE_AT, BURST_DOTS);
		}
	}

	/** The full-screen flashes (green in, gold, white, green out and its fade), the dial's green bloom, and the whip pan's streaks. */
	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 3.5f);
		ShowShots.dialBloom(st, t, WATCH_AT, HAND_AT);
		ShowShots.goldFlash(st, t, GOLD_AT, 97f);
		ShowShots.whipStreaks(st, t, WHIP_AT, HEAD_AT);
		ShowShots.flash(st, t, 0xF3F8EC, WHITE_AT, 2.5f, 210f, 6f, 0.92f);
		ShowShots.greenOut(st, t, GREEN2_AT, WORLD_AT, total());
	}
}
