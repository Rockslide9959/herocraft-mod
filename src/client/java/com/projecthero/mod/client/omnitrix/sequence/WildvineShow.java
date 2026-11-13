package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.minecraft.client.Camera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show, try to be as expressive as you can without bending the limbs"): Wildvine's
 * transformation, shot for shot from the compilation video (tick = (v - 277.45) * 20). The beats (ticks after the right-click):
 * <pre>
 *   0-3     the slap, from above the dial (purple swirl)
 *   3-22    Ben's frightened face, the green swirling up and fading over it
 *   22-40   FULL GREEN
 *   40-53   the green clearing: a tilt from his wrist up his side to his face (the show green, Kirby dots)
 *   53-80   his face, set and frowning; green leaf blades grow up round it, pushing in on his eye
 *   80-86   right on his eye, a leaf wiping across
 *   86-96   FULL GREEN FLASH -- the body changes under it (90)
 *   96-104  white streams on the show green as the green clears (the camera above him)
 *   104-130 Wildvine's eye: the white ring going grey and dark, pulling back
 *   130-160 his head in profile, the seed pods sprouting down his back
 *   160-166 from above and behind: the pods
 *   166-186 close on his chest, the arms coming up
 *   186-196 a whip up to his face (a white blur)
 *   196-216 his face, vines lashing across it, then the roar
 *   216-222 a vine arm swipes across the camera
 *   222-258 the whole body: the arm cracking out like a whip, then the crouch, claws up (the show green, Kirby dots)
 *   258-265 WHITE FLASH (fading off to ~285)
 *   262-326 the crouch from low at his right, on the green ray burst
 *   311-326 FULL GREEN FLASH, then the world, the green fading off by 339
 * </pre>
 * The pods sprouting are on his body ({@link #pods}, through {@code WildvineFx}), so everyone nearby sees them, and the poses.
 */
public final class WildvineShow extends ShowSequenceClient {
	static final float SLAP_END = 3.5f, FACE_AT = 3.5f, GREEN1_AT = 4f, CLEAR_AT = 40f, TILT_AT = 40f, FACE2_AT = 53f, LEAVES_AT = 60f, EYE_AT = 80f,
			GREEN2_AT = 85f, STREAMS_AT = 96f, RING_AT = 102f, PROFILE_AT = 130f, PODS_AT = 140f, ABOVE_AT = 160f, CHEST_AT = 166f, WHIP_AT = 186f,
			VFACE_AT = 196f, SWIPE_AT = 216f, WIDE_AT = 222f, CLAWS_AT = 240f, WHITE_AT = 258f, BURST_AT = 262f, GREEN3_AT = 311f, WORLD_AT = 326f;
	static final int TRANSFORM_AT = 90;

		private static final int BURST_IN = 0xB8FF92, BURST_MID = 0x3FCF34, BURST_OUT = 0x14701A, BURST_RAY = 0x0F5214, BURST_DOT = 0x0B3A0C;
	private static final int LEAF = 0x5CCF66, LEAF_DARK = 0x2C8A38;

	private static final KirbyDots.Style FIELD_DOTS = KirbyDots.Style.show().bands(3).density(1.6f).width(1.7f).size(1.2f).rim(0.6f).clear(0.3f),
			WHITE_DOTS = KirbyDots.Style.show().rgb(0xF2FFE6).bands(3).angle(-32f).spread(6f).density(1.8f).width(1.8f).size(1.3f).rim(0.8f).clear(0.1f),
			BURST_DOTS = KirbyDots.Style.show().bands(2).rgb(BURST_DOT).angle(-40f).density(1.4f).width(1.5f).clear(0.5f).flow(1.4f);

	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(ShowShots.dialCloseUp(0f, SLAP_END), new float[][] {
			// his face, a little from above
			{ 3.51f, 0.78f, 6f, 1.78f, 1.6f, 0f, 0f },
			{ 40f, 0.64f, 4f, 1.74f, 1.6f, 0f, 0f },
			// the tilt from the wrist up his side to the face
			{ 40.01f, 0.72f, -16f, 1.05f, 0.98f, -0.22f, 0.25f },
			{ 53f, 0.68f, -4f, 1.6f, 1.6f, 0f, 0.05f },
			// the face, then in on his left eye
			{ 53.01f, 0.88f, 0f, 1.62f, 1.56f, 0f, 0f },
			{ 70f, 0.78f, 0f, 1.62f, 1.58f, 0f, 0f },
			{ 80f, 0.32f, 0f, 1.68f, 1.68f, -0.09f, 0.18f },
			{ 80.01f, 0.25f, 4f, 1.69f, 1.69f, -0.1f, 0.24f },
			{ 90f, 0.2f, 4f, 1.69f, 1.69f, -0.1f, 0.24f },
			// Wildvine's eye, pulling back
			{ 90.01f, 0.9f, -12f, 2.5f, 3.0f, 0f, 0.2f },
			{ 103f, 0.9f, -12f, 2.5f, 3.1f, 0f, 0.2f },
			{ 103.01f, 0.3f, -12f, 1.58f, 1.64f, 0f, 0.22f },
			{ 130f, 0.62f, -30f, 1.5f, 1.6f, 0f, 0.15f },
			// the head in profile, the pods sprouting
			{ 130.01f, 1.25f, -105f, 1.6f, 1.38f, 0f, -0.05f },
			{ 160f, 1.35f, -125f, 1.62f, 1.32f, 0f, -0.08f },
			// from above and behind
			{ 160.01f, 0.75f, 175f, 2.05f, 1.2f, 0f, -0.05f },
			{ 166f, 0.7f, 175f, 2.0f, 1.2f, 0f, -0.05f },
			// close on his chest
			{ 166.01f, 0.72f, 18f, 1.35f, 1.28f, 0f, 0.1f },
			{ 186f, 0.62f, 8f, 1.3f, 1.25f, 0f, 0.1f },
			// the whip up to the face
			{ 196f, 0.42f, 0f, 1.62f, 1.62f, 0f, 0.2f },
			{ 206f, 0.5f, 0f, 1.62f, 1.62f, 0f, 0.18f },
			{ 216f, 0.82f, 0f, 1.56f, 1.58f, 0f, 0.1f },
			{ 222f, 1.0f, -10f, 1.5f, 1.5f, 0f, 0.1f },
			// the whole body, a little from his left
			{ 222.01f, 2.5f, -24f, 0.95f, 0.92f, 0f, 0f },
			{ 262f, 2.3f, -24f, 0.95f, 0.92f, 0f, 0f },
			// the crouch from low at his right, on the burst
			{ 262.01f, 2.45f, 38f, 0.55f, 1.05f, 0f, 0f },
			{ 326f, 2.25f, 36f, 0.55f, 1.08f, 0f, 0f } })).linear(WHIP_AT, VFACE_AT).shake(VFACE_AT + 10f, SWIPE_AT, 0.01f, 10f);

	public WildvineShow() {
		super(Aliens.WILDVINE.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	@Override
	public double fov(float t) {
		double fov = 62;
		if (t >= RING_AT && t < PROFILE_AT) {
			fov += 6 * smooth((t - RING_AT) / 28f);
		} else if (t >= VFACE_AT + 8f && t < SWIPE_AT) {
			fov -= 5 * smooth((t - VFACE_AT - 8f) / 4f);
		}
		return fov;
	}

	// ================================================================ the poses

	/**
	 * v0.17.3 touch-up (user: "make sure their limbs don't go weird or detach from the body and touch up the effects and poses"): one
	 * keyframed pose track ({@link FourArmsShow.Track}: Catmull-Rom through the keys, held where the show holds, snapped on the cuts),
	 * posed through {@link ShowBody} (the hunch, twist and lean turn about the hips with the shoulders following, so the arms never float
	 * off), with bent elbows and knees ({@link BentLimbs}): the fright with the shoulders up, the hands clutching his head, and the show's
	 * spider-legged crouch with the knees splayed high and out. The whip-cracking arm stretches ({@link #bulge}).
	 */
	private static final float[] DIAL = { -0.78f, 0.05f, -0.12f, 1.62f, 1.18f };
	static final FourArmsShow.Track POSE = ShowBody.track()
			// ---- the slap, from above the dial
			.at(0f).lArm(DIAL[0], DIAL[1], DIAL[2], DIAL[3], DIAL[4]).rArm(-0.9f, -0.5f, 0.2f, 1.4f, 0.3f).head(0.3f, 0f, 0f)
			.at(2f).rArm(-1.25f, -0.55f, 0.1f, 0.6f, 0f)
			// ---- the fright: head back and over, shoulders up round the neck, the hand pulled up to his chest
			.at(FACE_AT + 5f).head(-0.12f, 0f, 0.12f).lean(-0.1f).shrug(-0.8f).rArm(-1.0f, -0.4f, 0.15f, 1.25f, 0.3f).lArm(-0.45f, 0.1f, -0.2f, 1.3f, 1.1f)
			.at(36f).hold().head(-0.14f, 0f, 0.14f)
			// ---- the arms dropped, hands open; then set and frowning, the fists closing
			.at(TILT_AT + 4f).head(0.05f, 0f, 0f).lean(0f).shrug(0f).lArm(-0.3f, 0f, -0.25f, 0.35f, 0f).rArm(-0.15f, 0f, 0.3f, 0.3f, 0f)
			.at(FACE2_AT + 5f).head(0.22f, 0f, -0.06f).lean(0.08f).lArm(-0.35f, 0f, -0.3f, 0.6f, -0.3f).rArm(-0.3f, 0f, 0.32f, 0.6f, 0.3f)
			.at(TRANSFORM_AT).hold()
			// ---- Wildvine: loose, the arms hanging, swaying
			.at(TRANSFORM_AT + 0.01f).head(0.1f, 0f, 0f).lean(0f).arms(0.05f, 0f, 0.18f, 0.25f, 0.2f).legs(0f, 0f, 0.06f, 0.15f, 0.2f)
			.at(PROFILE_AT).hold()
			// the head bowing in profile as the pods sprout
			.at(155f).head(0.25f, 0f, 0f).lean(0.15f).arms(0f, 0f, 0.25f, 0.5f, 0.4f)
			.at(ABOVE_AT - 0.01f).hold()
			// ---- from above: hunched right over, his back to the sky
			.at(ABOVE_AT).head(0.5f, 0f, 0f).lean(0.45f).arms(-0.4f, 0f, 0.4f, 0.9f, 0.5f).legs(-0.15f, 0f, 0.3f, 0.4f, 1.57f)
			.at(CHEST_AT).hold()
			// ---- the hands come up round his sides (never through the lens) and clutch the top of his head
			.at(CHEST_AT + 0.01f).head(0.3f, 0f, 0f).lean(0.1f).arms(-0.8f, 0f, 1.2f, 1.3f, 1.2f)
			.at(CHEST_AT + 8f).head(0.1f, 0f, 0f).lean(-0.05f).arms(-2.5f, -0.3f, 0.45f, 0.95f, -0.6f)
			.at(VFACE_AT).hold()
			// ---- the roar: the arms flung out wide behind it, elbows bent, the head thrown back
			.at(VFACE_AT + 5f).head(0.05f, 0f, 0f).lean(0.12f).arms(-0.6f, 0f, 1.35f, 0.6f, 1.2f)
			.at(VFACE_AT + 9f).head(-0.28f, 0f, 0f)
			.at(WIDE_AT - 0.01f).hold()
			// ---- the whole body: the right arm cracking out like a whip and sweeping round, the left drawn back; spider legs splayed
			.at(WIDE_AT).drop(5f).lean(0.3f).split(-0.35f).head(-0.15f, 0f, 0f).rArm(-1.55f, 1.25f, 0.25f, 0.1f, 0f).lArm(0.6f, 0f, -0.5f, 0.9f, -0.6f)
			.rLeg(-0.45f, 0f, 1.35f, 1.3f, 1.57f).lLeg(-0.1f, 0f, -1.4f, 1.4f, -1.57f)
			.at(WIDE_AT + 4f).hold()
			.at(CLAWS_AT - 2f).split(0.25f).rArm(-1.55f, -0.3f, 0.25f, 0.15f, 0f)
			// ---- the crouch, both clawed hands raised, elbows bent
			.at(CLAWS_AT + 5f).split(0f).head(-0.2f, 0f, 0f).rArm(-1.0f, 0.2f, 1.3f, 1.25f, 1.3f).lArm(-0.85f, -0.2f, -1.4f, 1.15f, -1.3f)
			.at(BURST_AT - 0.01f).hold()
			// ---- the last stance, low at his right: claws forward, the legs splayed out like roots, knees high
			.at(BURST_AT).drop(5.5f).lean(0.32f).head(-0.25f, 0f, 0f).rArm(-1.35f, 0.15f, 0.75f, 1.1f, 0.9f).lArm(-1.15f, -0.1f, -0.9f, 1.2f, -0.8f)
			.legs(-0.3f, 0f, 1.5f, 1.45f, 1.57f)
			.at(WORLD_AT).hold()
			// eased to standing under the green out
			.at(WORLD_AT + 12f).hold().drop(0f).lean(0f).split(0f).head(0f, 0f, 0f).arms(0f, 0f, 0.1f, 0f, 0f).legs(0f, 0f, 0.04f, 0f, 0f);

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		float[] k = POSE.sample(t);
		ShowBody.apply(m, k, true);
		if (t < TRANSFORM_AT) {
			if (t >= FACE_AT && t < TILT_AT) {
				// the fright's shudder
				m.head.xRot += Mth.sin(t * 2.6f) * 0.03f * smooth((t - FACE_AT) / 5f);
			} else if (t >= TILT_AT) {
				float tremble = Mth.sin(t * 3.1f) * 0.03f;
				m.leftArm.xRot += tremble;
				m.rightArm.zRot += tremble;
			}
		} else {
			// the plant's slow sway, all through
			float sway = Mth.sin(t * 0.21f) * 0.07f;
			m.rightArm.zRot += sway;
			m.leftArm.zRot -= sway;
			if (t < ABOVE_AT) {
				m.head.zRot += sway * 0.5f;
			}
		}
		ShowBody.layers(m);
	}

	/** The whip: the right arm stretches out long as it cracks across, and snaps back as it sweeps round. */
	@Override
	public void bulge(Player p, HumanoidModel<?> m, float t) {
		float stretch = ShowMath.env(t, WIDE_AT + 0.5f, 3f, CLAWS_AT - 6f, 6f);
		if (stretch <= 0f) {
			return;
		}
		float y = 1f + 0.9f * stretch, xz = 1f - 0.18f * stretch;
		m.rightArm.yScale = y;
		m.rightArm.xScale = m.rightArm.zScale = xz;
		if (m instanceof PlayerModel<?> pm) {
			pm.rightSleeve.yScale = y;
			pm.rightSleeve.xScale = pm.rightSleeve.zScale = xz;
		}
	}

	// ================================================================ the pods (WildvineFx)

	/** How grown back-pod {@code i} is while his sequence sprouts them (0..1), or -1 when the sequence doesn't say. */
	public static float pods(Player p, float partial, int i) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.WILDVINE.id());
		if (t < TRANSFORM_AT || t >= ABOVE_AT + 10f) {
			return -1f;
		}
		return ShowMath.pop(t - PODS_AT - i * 2.5f, 6f);
	}

	// ================================================================ the world: the dial's light

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 0f, 4f);
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		int shot = TRACK.shot(t);
		if (t < SLAP_END) {
			st.gradient(0x8A8EF0, 0x5A5ED0, 0x2E3290);
		} else if (t < TILT_AT) {
			// the green swirling up behind his face
			st.gradient(0x9CF0B0, 0x3FAF84, 0x30489A);
			st.rays(st.seed, t * 2f, 0x7DE8C0, 14);
		} else if (t < TRANSFORM_AT) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(shot), t - TILT_AT, FIELD_DOTS);
		} else if (t < RING_AT + 8f) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(shot) + 3, t - STREAMS_AT, WHITE_DOTS);
		} else if (t < BURST_AT) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(shot), t - RING_AT, FIELD_DOTS);
		} else {
			st.gradient(BURST_IN, BURST_MID, BURST_OUT);
			st.rays(st.seed + 9, t - BURST_AT, BURST_RAY, 16);
			// v0.17.3 touch-up: the show's long black shadow streak out behind him, down to the bottom right, under the dots
			float sw = smooth((t - BURST_AT) / 5f);
			if (sw > 0f) {
				Xlr8Show.quad(st, st.flat(), st.last(), -0.05f, -0.42f, 0.12f, -0.3f, 1.7f, -0.45f, 1.7f, -1.4f, 0x020702, 0.9f * sw);
			}
			KirbyDots.draw(st, st.shotSeed(shot) + 5, t - BURST_AT, BURST_DOTS);
		}
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.dialBloom(st, t, 0f, SLAP_END + 2f);
		// the first green: fading up over his face, full, then clearing off the tilt
		float g1 = ShowMath.env(t, GREEN1_AT, 20f, CLEAR_AT, 10f);
		if (g1 > 0f) {
			st.fill(ShowShots.FLASH_GREEN, g1);
		}
		leaves(st, t);
		// a leaf wiping across the eye, into the second green flash
		float wipe = smooth((t - 81f) / 5f);
		if (t >= 81f && t < 90f) {
			CannonboltShow.lens(st, Mth.lerp(wipe, 1.9f, 0f), 0f, 1.4f, 1.2f, 1.2f, LEAF_DARK, 1f);
		}
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, GREEN2_AT, 2f, 94f, 4f, 1f);
		ring(st, t);
		// the whip up: a white blur
		ShowShots.flash(st, t, 0xF0FFE8, WHIP_AT, 2f, VFACE_AT - 2f, 3f, 0.75f);
		vines(st, t);
		// the arm swiping across the camera
		if (t >= SWIPE_AT && t < WIDE_AT + 1f) {
			float s = smooth((t - SWIPE_AT) / 6f);
			CannonboltShow.lens(st, Mth.lerp(s, -2.2f, 2.2f), Mth.lerp(s, 0.6f, -0.3f), 1.6f, 0.75f, -0.35f, 0x2E9C3A, 1f);
			CannonboltShow.lens(st, Mth.lerp(s, -2.2f, 2.2f) + 0.05f, Mth.lerp(s, 0.6f, -0.3f) + 0.1f, 1.4f, 0.45f, -0.35f, 0x55C85E, 1f);
		}
		ShowShots.flash(st, t, 0xFFFFFF, WHITE_AT, 1.5f, 264f, 20f, 1f);
		ShowShots.greenOut(st, t, GREEN3_AT, WORLD_AT, total());
	}

	/** Leaf blades growing up from below round his face, swaying, the inner ones crossing in front of it. */
	private static final float[][] LEAVES = {
			// x base, lean (radians, + leans right), length, width, starts
			{ -0.95f, 0.25f, 1.45f, 0.17f, 60f }, { 0.9f, -0.2f, 1.5f, 0.16f, 61f }, { -0.62f, 0.12f, 1.2f, 0.14f, 63f }, { 0.66f, -0.14f, 1.3f, 0.15f, 64f },
			{ -1.25f, 0.38f, 1.6f, 0.2f, 62f }, { 1.2f, -0.35f, 1.65f, 0.2f, 63f }, { -0.42f, 0.05f, 0.95f, 0.12f, 68f }, { 0.48f, -0.1f, 1.0f, 0.12f, 69f } };

	private static void leaves(Stage st, float t) {
		if (t < LEAVES_AT || t >= GREEN2_AT + 3f) {
			return;
		}
		float k = st.k();
		for (int i = 0; i < LEAVES.length; i++) {
			float[] l = LEAVES[i];
			float g = smooth((t - l[4]) / 9f);
			if (g <= 0f) {
				continue;
			}
			float len = l[2] * g;
			float lean = l[1] + 0.05f * Mth.sin(t * 0.3f + i);
			// the push-in on the eye carries the blades out of frame
			float push = 1f + 0.9f * smooth((t - 70f) / 10f);
			float bx = l[0] * k * push, by = -1.15f;
			float ang = Mth.HALF_PI - lean;
			float cx = bx + Mth.cos(ang) * len * 0.5f * k, cy = by + Mth.sin(ang) * len * 0.5f;
			CannonboltShow.lens(st, cx, cy, len * 0.5f, l[3] + 0.015f, ang, LEAF_DARK, 1f);
			CannonboltShow.lens(st, cx + 0.01f, cy, len * 0.47f, l[3] * 0.72f, ang, LEAF, 1f);
		}
	}

	/** Wildvine's eye forming: a white ring round the green, going grey and dark as the camera pulls back off it. */
	private static void ring(Stage st, float t) {
		float a = ShowMath.env(t, RING_AT, 2f, 126f, 5f);
		if (a <= 0f) {
			return;
		}
		float u = smooth((t - RING_AT) / 26f);
		float r = Mth.lerp(u, 0.62f, 0.34f);
		int rim = u < 0.45f ? ShowMath.lerpRgb(u / 0.45f, 0xF6F8F2, 0xB8BCB4) : ShowMath.lerpRgb((u - 0.45f) / 0.55f, 0xB8BCB4, 0x283028);
		int fill = ShowMath.lerpRgb(u, 0x16B83E, 0x2CF09A);
		PoseStack.Pose p = st.last();
		float k = st.k(), x = -0.08f * k, y = 0.05f;
		// the green inside as a round dot, the rim as a flat annulus round it (they never overlap, so the buffers' order can't matter)
		st.dot(st.dots(), p, x, y, r * 0.8f, fill, Math.round(245 * a));
		VertexConsumer vc = st.flat();
		int n = 48;
		for (int i = 0; i < n; i++) {
			float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n, r0 = r * 0.8f, r1 = r;
			st.put(vc, p, x + Mth.cos(a0) * r0 * k, y + Mth.sin(a0) * r0, rim, a);
			st.put(vc, p, x + Mth.cos(a0) * r1 * k, y + Mth.sin(a0) * r1, rim, a);
			st.put(vc, p, x + Mth.cos(a1) * r1 * k, y + Mth.sin(a1) * r1, rim, a);
			st.put(vc, p, x + Mth.cos(a1) * r0 * k, y + Mth.sin(a1) * r0, rim, a);
		}
	}

	/** Vines lashing across his face, then whipping off. */
	private static void vines(Stage st, float t) {
		float a = ShowMath.env(t, VFACE_AT, 0.5f, 203f, 4f);
		if (a <= 0f) {
			return;
		}
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float off = 3.2f * smooth((t - 203f) / 5f);
		for (int i = 0; i < 6; i++) {
			float y0 = -0.75f + 0.3f * i, amp = 0.12f + 0.04f * (i % 3), ph = i * 1.9f + t * 0.15f;
			float th = 0.022f + 0.008f * (i % 2);
			int col = i % 2 == 0 ? 0x2E9C3A : 0x6ADC72;
			float dir = i % 2 == 0 ? 1f : -1f;
			int n = 28;
			for (int j = 0; j < n; j++) {
				float x0 = -1.2f + 2.4f * j / n, x1 = -1.2f + 2.4f * (j + 1) / n;
				float ya = y0 + amp * Mth.sin(x0 * 2.2f + ph), yb = y0 + amp * Mth.sin(x1 * 2.2f + ph);
				float sx = dir * off;
				st.put(vc, p, x0 + sx, ya - th, col, a);
				st.put(vc, p, x1 + sx, yb - th, col, a);
				st.put(vc, p, x1 + sx, yb + th, col, a);
				st.put(vc, p, x0 + sx, ya + th, col, a);
			}
		}
	}
}
