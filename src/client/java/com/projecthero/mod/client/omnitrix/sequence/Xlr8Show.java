package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.client.omnitrix.OmnitrixTransformFx;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.minecraft.client.Camera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.env;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.hash;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.rot;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show, try to be as expressive as you can without bending the limbs"): XLR8's transformation
 * shot for shot, cut to the compilation's audio (tick = (video s - 87.15) * 20). The beats (ticks after the right-click):
 * <pre>
 *   0-6     the slap (low front medium shot, warm room)
 *   4-24    FULL GREEN FLASH
 *   16-30   the dial close-up coming out of the green, light bursting off it
 *   30-36   up the arm to the chest, green light streaming
 *   36-62   Ben's face from his left front, eyes shut, head bowed; light shafts from the left; olive field, black Kirby dots
 *           (a slow push in to the eye)
 *   62-76   the eye, very close: the black visor lines slide in round it
 *   75-80   smear flash -- the body changes under it (77)
 *   78-90   XLR8's eye, very close, on teal
 *   89-100  FULL GREEN FLASH (the second)
 *   98-118  low front: the legs glowing pale green, blue sparks crackling, on bright green with dark-green dots
 *   118-128 the tilt up the black legs
 *   128-136 the claws, close, from behind his left
 *   136-146 the striped tail whipping past (whip pan round to the front)
 *   146-158 low angle on the chest and the badge (black field, gold-olive gaps)
 *   158-160 whip up to the face
 *   160-176 the face, front: the visor slides down over it (166-172)
 *   176-190 full body, front: the low crouch, claws out, tail up
 *   188-196 WHITE FLASH
 *   194-222 side-on: the sprinter's lean, on the green ray burst with a black shadow streak
 *   220-236 FULL GREEN FLASH, then the world, the green fading off by 259
 * </pre>
 * Everything is flat-colour quads (no particles). Limbs stay rigid: the expression is in whole-limb angles, the lean and the camera.
 */
public final class Xlr8Show extends ShowSequenceClient {
	static final float SLAP_END = 6f, DIAL_AT = 6f, ARM_AT = 30f, FACE_AT = 36f, EYE_AT = 62f, SMEAR_AT = 75f, XEYE_AT = 78f, GREEN1_AT = 89f,
			LEGS_AT = 98f, TILT_AT = 118f, CLAW_AT = 128f, TAIL_AT = 136f, CHEST_AT = 146f, WHIP2_AT = 157f, HEAD_AT = 160f, VISOR_AT = 166f,
			CROUCH_AT = 176f, WHITE_AT = 188f, RUN_AT = 192f, GREEN2_AT = 220f, WORLD_AT = 236f;
	static final int TRANSFORM_AT = 77;

	private static final int BURST_IN = 0x9CF77A, BURST_MID = 0x3FD33A, BURST_OUT = 0x1D8A18, BURST_RAY = 0x15641A, BURST_DOT = 0x0D3F0B;
	private static final int TEAL_IN = 0x4FB8BC, TEAL_MID = 0x2E8A94, TEAL_OUT = 0x14505A;

	private static final KirbyDots.Style FACE_DOTS = KirbyDots.Style.show().rim(1.3f).clear(0.38f),
			LEG_DOTS = KirbyDots.Style.show().rgb(0x1E6A10).bands(3).density(1.3f).clear(0.3f),
			DARK_DOTS = KirbyDots.Style.show().density(1.8f).rim(1.6f).bands(4).clear(0.25f),
			CROUCH_DOTS = KirbyDots.Style.show().density(1.6f).rim(1.5f).bands(3).clear(0.45f),
			BURST_DOTS = KirbyDots.Style.show().bands(2).rgb(BURST_DOT).clear(0.5f).flow(1.6f);

	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(ShowShots.slapShot(0f, SLAP_END), ShowShots.dialCloseUp(6.01f, ARM_AT),
			new float[][] {
					// up the raised arm from the dial to the chest, from his left front, low
					{ 30.01f, 0.55f, -62f, 1.0f, 1.12f, -0.05f, 0.3f },
					{ 36f, 0.62f, -48f, 1.3f, 1.4f, -0.05f, 0.12f },
					// the face, bowed, from his left front and a little above; the push in to the eye
					{ 36.01f, 0.8f, -32f, 1.58f, 1.7f, 0f, 0.1f },
					{ 52f, 0.62f, -30f, 1.6f, 1.72f, -0.02f, 0.12f },
					{ 62f, 0.45f, -28f, 1.62f, 1.73f, -0.05f, 0.15f },
					// the eye, very close
					{ 62.01f, 0.22f, -24f, 1.7f, 1.72f, -0.08f, 0.24f },
					{ 77f, 0.19f, -22f, 1.7f, 1.72f, -0.08f, 0.24f },
					// XLR8's eye
					{ 77.01f, 0.26f, -26f, 1.66f, 1.66f, -0.08f, 0.24f },
					{ 98f, 0.24f, -24f, 1.66f, 1.66f, -0.08f, 0.24f },
					// low in front: the legs, then the tilt up them
					{ 98.01f, 1.15f, 6f, 0.12f, 0.38f, 0f, 0f },
					{ 118f, 1.05f, 4f, 0.12f, 0.42f, 0f, 0f },
					{ 128f, 0.95f, 4f, 0.2f, 1.0f, 0f, 0f },
					// the claws, close, from behind his left
					{ 128.01f, 0.62f, -148f, 1.0f, 0.82f, -0.38f, 0.05f },
					{ 136f, 0.55f, -156f, 0.95f, 0.8f, -0.38f, 0.05f },
					// the tail whipping past: behind, low, swung round fast to the chest
					{ 136.01f, 0.75f, 178f, 0.7f, 0.75f, 0f, -0.3f },
					{ 141f, 0.7f, 100f, 0.8f, 0.95f, 0f, -0.1f },
					{ 146f, 0.72f, 24f, 0.85f, 1.2f, 0f, 0f },
					// low on the chest and the badge, tilting up
					{ 146.01f, 0.78f, 26f, 0.8f, 1.22f, 0f, 0f },
					{ 157f, 0.72f, 20f, 0.9f, 1.32f, 0f, 0f },
					// the whip up to the face
					{ 160f, 0.45f, 4f, 1.55f, 1.62f, 0f, 0f },
					{ 160.01f, 0.62f, 0f, 1.62f, 1.72f, 0f, 0f },
					{ 176f, 0.55f, 0f, 1.62f, 1.72f, 0f, 0f },
					// full body, the crouch, drifting round to his right
					{ 176.01f, 1.85f, 12f, 0.5f, 0.85f, 0f, 0f },
					{ 192f, 1.75f, 30f, 0.55f, 0.85f, 0f, 0f },
					// side-on, the sprinter's lean
					{ 192.01f, 1.75f, 48f, 0.45f, 0.8f, 0f, 0.15f },
					{ WORLD_AT, 1.65f, 44f, 0.45f, 0.8f, 0f, 0.15f } }))
			.linear(TAIL_AT, CHEST_AT).linear(WHIP2_AT, HEAD_AT);

	public Xlr8Show() {
		super(Aliens.XLR8.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public int burstAt() {
		return 224;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	@Override
	public double fov(float t) {
		double fov = 62;
		if (t >= DIAL_AT && t < ARM_AT) {
			fov -= 8 * smooth((t - 14f) / 16f);
		} else if (t >= VISOR_AT && t < CROUCH_AT) {
			fov -= 5 * smooth((t - VISOR_AT) / 6f);
		}
		return fov;
	}

	/** v0.17.3: the visor slides down over his face in the face close-up (from {@code Xlr8Client}); null when not playing. */
	public static Boolean visorDown(Player p, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.XLR8.id());
		if (t < 0f) {
			return null;
		}
		return t >= VISOR_AT;
	}

	// ================================================================ the poses

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		if (t < TRANSFORM_AT) {
			// the watch still up; through the face shot the head bows, calm, eyes shut, the light washing over him
			float bow = smooth((t - FACE_AT + 2f) / 10f);
			if (bow > 0f) {
				float sway = 0.03f * Mth.sin(t * 0.35f);
				rot(m.head, bow, (t < EYE_AT ? 0.18f : 0.08f) + sway, 0.12f, -0.08f);
				m.body.xRot = Mth.lerp(bow, m.body.xRot, 0.05f);
			}
			m.hat.copyFrom(m.head);
			return;
		}
		float breathe = Mth.sin(t * 0.3f) * 0.03f;
		if (t < LEGS_AT) {
			// the eye shot: still, the head level
			rot(m.head, 1f, 0.05f, 0f, 0f);
			rot(m.rightArm, 1f, 0f, 0f, 0.12f);
			rot(m.leftArm, 1f, 0f, 0f, -0.12f);
			rot(m.rightLeg, 1f, 0f, 0f, 0.06f);
			rot(m.leftLeg, 1f, 0f, 0f, -0.06f);
		} else if (t < CLAW_AT) {
			// standing, legs a little apart, as the glow sets into his legs
			rot(m.rightLeg, 1f, 0f, 0f, 0.1f);
			rot(m.leftLeg, 1f, 0f, 0f, -0.1f);
			rot(m.rightArm, 1f, 0f, 0f, 0.25f + breathe);
			rot(m.leftArm, 1f, 0f, 0f, -0.25f - breathe);
			rot(m.head, 1f, 0f, 0f, 0f);
		} else if (t < HEAD_AT) {
			// the claws flex out low behind, the tail swings, the chest swells
			float w = smooth((t - CLAW_AT) / 4f);
			rot(m.leftArm, w, 0.35f, 0f, -0.55f - breathe);
			rot(m.rightArm, w, 0.35f, 0f, 0.55f + breathe);
			rot(m.rightLeg, 1f, 0f, 0f, 0.12f);
			rot(m.leftLeg, 1f, 0f, 0f, -0.12f);
			rot(m.head, w, -0.12f, 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, -0.05f);
		} else if (t < CROUCH_AT) {
			// the face: chin up, straight at the camera
			rot(m.head, 1f, -0.06f, 0f, 0f);
			rot(m.leftArm, 1f, 0.3f, 0f, -0.5f);
			rot(m.rightArm, 1f, 0.3f, 0f, 0.5f);
		} else if (t < RUN_AT) {
			// the low crouch: down on spread legs, leaning in, the claws out wide and low, the head up
			float w = smooth((t - CROUCH_AT) / 3f);
			crouch(m, w, 0.42f);
			rot(m.rightArm, w, -0.35f, 0.2f, 1.05f + breathe);
			rot(m.leftArm, w, 0.25f, -0.2f, -0.95f - breathe);
			rot(m.rightLeg, w, -0.3f, 0.15f, 0.42f);
			rot(m.leftLeg, w, 0.15f, -0.15f, -0.42f);
			rot(m.head, w, -0.45f, -0.1f, 0f);
		} else {
			// the sprinter's lean: everything pitched forward, one leg driving out in front, the other back, the arms swept behind
			float w = smooth((t - RUN_AT) / 3f);
			float drift = 0.02f * Mth.sin(t * 0.4f);
			crouch(m, w, 0.62f);
			rot(m.rightLeg, w, -1.0f, 0f, 0.2f);
			rot(m.leftLeg, w, 0.75f, 0f, -0.25f);
			rot(m.rightArm, w, 1.05f + drift, 0f, 0.35f);
			rot(m.leftArm, w, 0.85f - drift, 0f, -0.45f);
			rot(m.head, w, -0.7f, 0.15f, 0f);
		}
		copyLayers(m);
	}

	/** A crouch by {@code w}: the body pitched forward {@code lean} on the hips, the head and arms down with it (vanilla's sneak). */
	private static void crouch(HumanoidModel<?> m, float w, float lean) {
		float k = lean / 0.5f;
		m.body.xRot = Mth.lerp(w, m.body.xRot, lean);
		m.body.y = Mth.lerp(w, m.body.y, 3.2f * k);
		m.head.y = Mth.lerp(w, m.head.y, 4.2f * k);
		m.rightArm.y = Mth.lerp(w, m.rightArm.y, 2f + 3.2f * k);
		m.leftArm.y = Mth.lerp(w, m.leftArm.y, 2f + 3.2f * k);
		m.rightLeg.y = Mth.lerp(w, m.rightLeg.y, 12.2f);
		m.leftLeg.y = Mth.lerp(w, m.leftLeg.y, 12.2f);
		m.rightLeg.z = Mth.lerp(w, m.rightLeg.z, 4f * k);
		m.leftLeg.z = Mth.lerp(w, m.leftLeg.z, 4f * k);
	}

	private static void copyLayers(HumanoidModel<?> m) {
		if (m instanceof PlayerModel<?> pm) {
			pm.rightSleeve.copyFrom(m.rightArm);
			pm.leftSleeve.copyFrom(m.leftArm);
			pm.rightPants.copyFrom(m.rightLeg);
			pm.leftPants.copyFrom(m.leftLeg);
			pm.jacket.copyFrom(m.body);
		}
		m.hat.copyFrom(m.head);
	}

	// ================================================================ in the world: the dial's light, the legs' glow and sparks

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 4f, 34f);
		float glow = env(t, LEGS_AT - 2f, 2f, 111f, 6f);
		if (glow <= 0f) {
			return;
		}
		Vec3 cp = cam.getPosition();
		Vector3f left = new Vector3f(cam.getLeftVector()), up = new Vector3f(cam.getUpVector());
		VertexConsumer vc = buffers.getBuffer(RenderType.eyes(OmnitrixTransformFx.GLOW));
		PoseStack.Pose last = pose.last();
		float s = p.getBbHeight() / 1.8f;
		Vec3 base = p.getPosition(partial);
		Vec3 fwd = ShowMath.forward(com.projecthero.mod.omnitrix.OmnitrixLongTransform.yaw(com.projecthero.mod.omnitrix.Omnitrix.state(p))), right = new Vec3(-fwd.z, 0, fwd.x);
		long seed = OmnitrixSequenceClient.seed(p);
		for (int leg = -1; leg <= 1; leg += 2) {
			for (int i = 0; i < 4; i++) {
				Vec3 at = base.add(right.scale(leg * 0.13 * s)).add(0, (0.1 + i * 0.2) * s, 0).subtract(cp);
				float pulse = 0.85f + 0.15f * Mth.sin(t * 1.3f + i + leg);
				ShowShots.glowQuad(vc, last, at, 0.3f * s * pulse, ShowMath.scaleRgb(0x9CFF8A, glow * 0.8f), left, up);
				ShowShots.glowQuad(vc, last, at, 0.14f * s, ShowMath.scaleRgb(0xE8FFE0, glow), left, up);
			}
		}
		// the blue sparks crackling round the legs
		float spark = env(t, 101f, 2f, 115f, 3f);
		if (spark > 0f) {
			VertexConsumer flat = buffers.getBuffer(com.projecthero.mod.client.omnitrix.OmnitrixFxRenderType.FLAT);
			int step = (int) (t / 2f);
			for (int i = 0; i < 7; i++) {
				int leg = i % 2 == 0 ? -1 : 1;
				Vec3 at = base.add(right.scale(leg * 0.13 * s)).add(0, (0.08 + 0.7 * hash(seed, step * 13 + i)) * s, 0).subtract(cp);
				float ang = hash(seed, step * 17 + i) * Mth.TWO_PI;
				ShowShots.kite(flat, last, at, left, up, ang, 0.02f, (0.18f + 0.2f * hash(seed, step * 7 + i)) * s, 0.012f * s, 0xEFFFFF, 0x4FD8FF,
						0.9f * spark);
			}
		}
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		if (t >= WORLD_AT - 0.5f) {
			return; // under the full green, and never over the world view
		}
		int shot = TRACK.shot(t);
		if (t < SLAP_END) {
			st.gradient(0xFFF0B8, 0xE8B866, 0x7A4A22);
		} else if (t < ARM_AT) {
			st.gradient(0xF2FFE0, 0x7CEB4C, 0x2DB51C);
		} else if (t < FACE_AT) {
			st.showBackground(); // v0.17.3: the shared show green (user: "the background for all the transformations the same green")
		} else if (t < TRANSFORM_AT) {
			st.showBackground(); // v0.17.3: the shared show green (user: "the background for all the transformations the same green")
			KirbyDots.draw(st, st.shotSeed(shot), t - FACE_AT, FACE_DOTS);
		} else if (t < LEGS_AT) {
			st.gradient(TEAL_IN, TEAL_MID, TEAL_OUT);
		} else if (t < CHEST_AT) {
			st.showBackground(); // v0.17.3: the shared show green (user: "the background for all the transformations the same green")
			KirbyDots.draw(st, st.shotSeed(shot), t - LEGS_AT, t < CLAW_AT ? LEG_DOTS : DARK_DOTS);
		} else if (t < WHITE_AT + 3f) {
			st.showBackground(); // v0.17.3: the shared show green (user: "the background for all the transformations the same green")
			KirbyDots.draw(st, st.shotSeed(shot), t - CHEST_AT, t >= CROUCH_AT ? CROUCH_DOTS : DARK_DOTS);
		} else {
			st.gradient(BURST_IN, BURST_MID, BURST_OUT);
			st.rays(st.seed, t - RUN_AT, BURST_RAY, 14);
			shadowStreak(st, smooth((t - RUN_AT) / 4f));
			KirbyDots.draw(st, st.shotSeed(shot) + 5, t - RUN_AT, BURST_DOTS);
		}
	}

	/** The final shot's black shadow streak, from behind him down to the bottom right corner. */
	private static void shadowStreak(Stage st, float w) {
		if (w <= 0f) {
			return;
		}
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		quad(st, vc, p, 0.12f, -0.22f, 0.3f, -0.12f, 1.5f, -0.6f, 1.5f, -1.05f, 0x0A0F06, 0.95f * w);
		
	}

	/** A flat four-corner shape in screen units. */
	static void quad(Stage st, VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
			int rgb, float a) {
		st.put(vc, p, x0, y0, rgb, a);
		st.put(vc, p, x1, y1, rgb, a);
		st.put(vc, p, x2, y2, rgb, a);
		st.put(vc, p, x3, y3, rgb, a);
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 4.5f);
		// the green holds over the start of the dial close-up, which comes out of it
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, 8f, 1f, 15f, 9f, 1f);
		ShowShots.dialBloom(st, t, 16f, ARM_AT + 4f);
		// the arm shot: the green light streaming up it
		float armGlow = env(t, ARM_AT, 0.5f, FACE_AT - 1f, 2f);
		if (armGlow > 0f) {
			st.radial(0xF8FFD0, 0xC8F070, 0x80C040, 0.45f * armGlow, 0.15f * armGlow);
		}
		lightShafts(st, t);
		visorLines(st, t);
		// the smear: a pale teal-white flash the body changes under
		ShowShots.flash(st, t, 0xDDF6EE, SMEAR_AT, 1.5f, 78.5f, 1.5f, 1f);
		// the second green flash, the legs coming out of it
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, GREEN1_AT, 1.5f, 96f, 6f, 1f);
		ShowShots.whipStreaks(st, t, TAIL_AT + 1f, CHEST_AT);
		ShowShots.whipStreaks(st, t, WHIP2_AT, HEAD_AT + 0.5f);
		ShowShots.flash(st, t, 0xF4F6F0, WHITE_AT, 1.5f, 193f, 9f, 0.95f);
		ShowShots.greenOut(st, t, GREEN2_AT, WORLD_AT, total());
	}

	/** The face shot's pale light shafts, slanting in from the left over his face. */
	private static void lightShafts(Stage st, float t) {
		float a = env(t, FACE_AT, 2f, EYE_AT + 4f, 6f);
		if (a <= 0f) {
			return;
		}
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float[][] shafts = { { 0.1f, 0.07f, 0.34f }, { -0.06f, 0.04f, 0.26f }, { 0.22f, 0.05f, 0.22f }, { -0.2f, 0.03f, 0.18f } };
		for (int i = 0; i < shafts.length; i++) {
			float y = shafts[i][0] + 0.02f * Mth.sin(t * 0.2f + i), h = shafts[i][1];
			float al = shafts[i][2] * a;
			// from the left edge, slanting a little down, fading out past the middle
			st.put(vc, p, -1.2f, y + h, 0xFFFBE0, al);
			st.put(vc, p, -1.2f, y - h, 0xFFFBE0, al);
			st.put(vc, p, 0.35f, y - 0.16f - h * 2.2f, 0xFFFBE0, 0f);
			st.put(vc, p, 0.35f, y - 0.16f + h * 2.2f, 0xFFFBE0, 0f);
		}
	}

	/** The eye close-up: the visor's black lines sliding in round the eye, the teal creeping in behind them. */
	private static void visorLines(Stage st, float t) {
		if (t < EYE_AT + 1f || t >= TRANSFORM_AT) {
			return;
		}
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float a = smooth((t - EYE_AT - 1f) / 5f);
		float b = smooth((t - 66f) / 8f);
		// a short black wedge under the eye, left of centre
		quad(st, vc, p, -0.32f, -0.1f, -0.12f - 0.05f * a, -0.06f, -0.08f, -0.2f * a - 0.02f, -0.3f, -0.17f, 0x050505, a);
		// the long stripe from the right, sliding in under the eye
		float x = Mth.lerp(b, 1.3f, 0.05f);
		quad(st, vc, p, x, -0.12f, 1.3f, -0.02f, 1.3f, -0.2f, x + 0.06f, -0.26f, 0x050505, b);
		// a dark streak dropping in from the top left
		float c = smooth((t - 68f) / 6f);
		quad(st, vc, p, -0.55f, 1.2f, -0.4f, 1.2f, -0.25f, Mth.lerp(c, 1.2f, 0.15f), -0.38f, Mth.lerp(c, 1.2f, 0.12f), 0x1A1206, 0.85f * c);
		// the teal smear creeping in at the end
		float teal = smooth((t - 72f) / 4f);
		if (teal > 0f) {
			st.radial(0xDFF0D8, 0x6EC4B8, 0x2E8A94, 0.1f * teal, 0.6f * teal);
		}
	}
}
