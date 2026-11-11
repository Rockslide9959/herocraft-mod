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

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.rot;
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

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		m.head.yRot = 0f;
		if (t < TRANSFORM_AT) {
			human(m, t);
		} else {
			wildvine(m, t);
		}
		if (m instanceof PlayerModel<?> pm) {
			pm.rightSleeve.copyFrom(m.rightArm);
			pm.leftSleeve.copyFrom(m.leftArm);
			pm.jacket.copyFrom(m.body);
			pm.rightPants.copyFrom(m.rightLeg);
			pm.leftPants.copyFrom(m.leftLeg);
		}
		m.hat.copyFrom(m.head);
	}

	/** Ben: the slap, the fright (head back, shrinking from it), then set and frowning as the leaves come up. */
	private static void human(HumanoidModel<?> m, float t) {
		if (t < TILT_AT) {
			float slap = smooth(t / 2f);
			rot(m.rightArm, slap, -1.25f, -0.55f, 0.1f);
			float fear = smooth((t - FACE_AT) / 5f);
			float shudder = Mth.sin(t * 2.6f) * 0.03f * fear;
			m.head.xRot = Mth.lerp(fear, m.head.xRot, -0.12f + shudder);
			m.head.zRot = Mth.lerp(fear, m.head.zRot, 0.12f);
			m.body.xRot = Mth.lerp(fear, m.body.xRot, -0.1f);
			return;
		}
		// the arms dropped, hands open; the head down, glaring
		float w = smooth((t - TILT_AT) / 4f);
		float tremble = Mth.sin(t * 3.1f) * 0.03f;
		rot(m.leftArm, w, -0.3f + tremble, 0f, -0.25f);
		rot(m.rightArm, w, -0.15f, 0f, 0.3f + tremble);
		float set = smooth((t - FACE2_AT) / 5f);
		m.head.xRot = Mth.lerp(w, m.head.xRot, Mth.lerp(set, 0.05f, 0.22f));
		m.head.zRot = Mth.lerp(w, m.head.zRot, Mth.lerp(set, 0f, -0.06f));
		m.body.xRot = Mth.lerp(w, m.body.xRot, 0.08f * set);
	}

	/** Wildvine: loose and swaying, hunched over his pods, the roar, the whip of an arm, the crouch with claws up. */
	private static void wildvine(HumanoidModel<?> m, float t) {
		float sway = Mth.sin(t * 0.21f) * 0.07f;
		if (t < ABOVE_AT) {
			rot(m.rightArm, 1f, 0.05f, 0f, 0.18f + sway);
			rot(m.leftArm, 1f, 0.05f, 0f, -0.18f - sway);
			m.head.xRot = 0.1f + 0.15f * smooth((t - PROFILE_AT) / 25f);
			m.head.zRot = sway * 0.5f;
			m.body.xRot = 0.15f * smooth((t - PROFILE_AT) / 25f);
		} else if (t < CHEST_AT) {
			// hunched, his back to the sky
			rot(m.rightArm, 1f, -0.4f, 0f, 0.4f);
			rot(m.leftArm, 1f, -0.4f, 0f, -0.4f);
			m.head.xRot = 0.5f;
			m.body.xRot = 0.45f;
		} else if (t < VFACE_AT) {
			// the hands come up and in, over the head
			float w = smooth((t - CHEST_AT) / 8f);
			rot(m.rightArm, w, -2.5f, -0.3f, 0.45f + sway);
			rot(m.leftArm, w, -2.5f, 0.3f, -0.45f - sway);
			m.head.xRot = Mth.lerp(w, 0.5f, 0.1f);
			m.body.xRot = Mth.lerp(w, 0.45f, -0.05f);
		} else if (t < WIDE_AT) {
			// the roar: head thrust forward, arms flung out wide behind it
			float w = smooth((t - VFACE_AT) / 5f);
			float roar = smooth((t - VFACE_AT - 8f) / 4f);
			rot(m.rightArm, w, -0.6f, 0f, 1.35f + sway);
			rot(m.leftArm, w, -0.6f, 0f, -1.35f - sway);
			m.head.xRot = Mth.lerp(roar, 0.05f, -0.28f);
			m.body.xRot = 0.12f;
		} else if (t < CLAWS_AT) {
			// the arm cracking out like a whip: the right flung out to the side and swept round, the left pulled back
			float w = smooth((t - WIDE_AT) / 3f);
			float sweep = smooth((t - WIDE_AT - 4f) / 12f);
			rot(m.rightArm, w, -1.55f, Mth.lerp(sweep, -1.1f, 0.5f), 0.25f);
			rot(m.leftArm, w, 0.6f, 0f, -0.5f);
			legsWide(m, w, 0.62f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.3f);
			m.body.yRot = Mth.lerp(sweep, -0.35f, 0.25f) * w;
			m.head.xRot = -0.15f;
		} else if (t < BURST_AT) {
			// the crouch, both clawed hands raised
			float w = smooth((t - CLAWS_AT) / 5f);
			rot(m.rightArm, w, -1.85f + sway, 0.2f, 0.95f);
			rot(m.leftArm, w, -1.6f - sway, -0.2f, -1.1f);
			legsWide(m, 1f, 0.62f);
			m.body.xRot = 0.3f;
			m.body.yRot = Mth.lerp(w, 0.25f, 0f);
			m.head.xRot = -0.2f;
		} else {
			// the last stance: low, claws forward, legs splayed like roots
			rot(m.rightArm, 1f, -1.35f + sway, 0.15f, 0.55f);
			rot(m.leftArm, 1f, -1.15f - sway, -0.1f, -0.7f);
			legsWide(m, 1f, 0.68f);
			m.body.xRot = 0.32f;
			m.head.xRot = -0.25f;
		}
	}

	private static void legsWide(HumanoidModel<?> m, float w, float out) {
		rot(m.rightLeg, w, -0.15f, 0f, out);
		rot(m.leftLeg, w, 0.1f, 0f, -out);
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
