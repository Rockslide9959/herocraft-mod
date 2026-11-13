package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.HipBend;
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
 *   194-222 side-on, facing right: the dash -- leaning forward, arms swung back, a wide planted stride, the tail rising out
 *           behind; on the green ray burst with a black shadow streak (final-pose rework)
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
					{ 128.01f, 1.15f, -150f, 1.0f, 0.8f, -0.3f, 0.05f },
					{ 136f, 1.02f, -158f, 0.95f, 0.78f, -0.32f, 0.05f },
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
					{ 176.01f, 1.9f, 10f, 0.42f, 0.78f, 0f, 0f },
					{ 192f, 1.8f, 26f, 0.45f, 0.78f, 0f, 0f },
					// side-on, the dash (final-pose rework: from his right, so he faces the right of frame in profile like the show's;
					// from three-quarters he read as turned to the camera and leaning away), a little low
					{ 192.01f, 2.2f, 80f, 0.78f, 0.82f, 0f, 0.1f },
					{ WORLD_AT, 2.05f, 77f, 0.78f, 0.82f, 0f, 0.1f } }))
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

	/**
	 * v0.17.3 touch-up (user: "make sure their limbs don't go weird or detach from the body and touch up the effects and poses"):
	 * XLR8's body runs on a keyframed pose track ({@link FourArmsShow.Track}: Catmull-Rom between keys, holds, snaps on the cuts),
	 * with bent elbows and knees ({@link BentLimbs}) -- the claws curl, the crouch sits deep on bent knees, the sprint drives one
	 * knee up and kicks the other shin up behind, the way the show draws him. Channels as in {@code FourArmsShow}.
	 */
	static final FourArmsShow.Track POSE = new FourArmsShow.Track()
			// ---- XLR8's eye (the change): standing easy
			.at(TRANSFORM_AT).arms(0f, 0f, 0.12f, 0.2f, 0f).legs(0f, 0f, 0.06f, 0.06f, 0f)
			// ---- the legs glowing: planted a little apart, knees soft, the claws hanging ready
			.at(LEGS_AT - 0.5f).hold()
			.at(LEGS_AT).arms(0.05f, 0f, 0.28f, 0.45f, 0.2f).legs(0f, 0f, 0.11f, 0.16f, 0.15f)
			.at(CLAW_AT - 0.5f).hold().arms(0.08f, 0f, 0.32f, 0.6f, 0.3f)
			// ---- the claws, close from behind: the arms swing back and out, the claws curling forward
			.at(CLAW_AT).arms(0.3f, 0f, 0.48f, 0.85f, 0.45f).head(-0.08f, 0f, 0f).lean(-0.04f)
			.at(TAIL_AT).arms(0.55f, 0f, 0.7f, 1.35f, 0.7f).head(-0.14f, 0f, 0f)
			// ---- the tail whipping past, the chest, the face: chest out, the claws held low and wide, curled
			.at(CHEST_AT).arms(0.42f, 0f, 0.6f, 1.15f, 0.55f).head(-0.1f, 0f, 0f).lean(-0.06f)
			.at(CROUCH_AT - 0.5f).hold().arms(0.35f, 0f, 0.55f, 1.0f, 0.45f).head(-0.06f, 0f, 0f)
			// ---- the low crouch (a cut): down on deep bent knees, knees out; one claw up by his face, the other out wide
			.at(CROUCH_AT).drop(3.2f).lean(0.3f).head(-0.5f, -0.12f, 0.05f)
			.rArm(-1.3f, 0.35f, 0.7f, 1.55f, 0.55f).lArm(-0.4f, -0.2f, -1.05f, 1.25f, -0.55f)
			.legs(-0.72f, 0f, 0.52f, 1.45f, 0.85f)
			.at(181f).hold()
			.at(RUN_AT - 0.5f).rArm(-1.4f, 0.4f, 0.75f, 1.7f, 0.6f).lArm(-0.5f, -0.25f, -1.15f, 1.35f, -0.6f).drop(3.5f).head(-0.55f, -0.12f, 0.05f)
			// ---- the dash (a cut; final-pose rework, the show's t 215): pitched forward, the chin up so the visor looks ahead; both arms
			// swung back with the elbows high behind him and the forearms folded forward (the blades flared); a wide planted stride, the
			// front foot under the hips on a bent knee, the back leg reaching far behind (it was a running knee-up, claws forward)
			.at(RUN_AT).drop(2.1f).lean(0.62f).head(-0.8f, -0.08f, 0f)
			.rArm(1.25f, 0f, 0.2f, 2.2f, 0.15f).lArm(0.9f, 0f, -0.25f, 1.6f, -0.15f)
			.rLeg(-0.6f, 0f, 0.14f, 1.2f, 0.1f).lLeg(0.55f, 0f, -0.12f, 0.3f, -0.1f)
			.at(206f).rArm(1.32f, 0f, 0.22f, 2.3f, 0.15f).lArm(0.95f, 0f, -0.27f, 1.68f, -0.15f).rLeg(-0.62f, 0f, 0.14f, 1.24f, 0.1f).lLeg(0.58f, 0f, -0.12f, 0.32f, -0.1f)
			.at(GREEN2_AT + 4f).hold()
			// eased to standing under the green flash
			.at(WORLD_AT).hold().drop(0f).lean(0f).head(0f, 0f, 0f).arms(0f, 0f, 0.1f, 0.15f, 0f).legs(0f, 0f, 0.05f, 0f, 0f);

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
		float[] k = POSE.sample(t);
		// a restless twitch in the claws (he never quite holds still), a breath in the chest
		float breathe = Mth.sin(t * 0.3f) * 0.03f;
		float twitch = t >= CLAW_AT && t < CROUCH_AT ? 0.06f * Mth.sin(t * 1.3f) : 0f;
		apply(m, k, breathe, twitch, t < WORLD_AT);
	}

	/**
	 * A sampled pose track row onto the model, every part from its rest place (nothing left over from the kit's own poses): the
	 * drop lowers the whole body (px), the lean bends it forward about the hips ({@link HipBend}, so the torso never leaves the
	 * legs), then the head, the limbs, and the elbows and knees ({@link BentLimbs}).
	 */
	static void apply(HumanoidModel<?> m, float[] k, float breathe, float twitch, boolean bend) {
		float drop = k[FourArmsShow.DROP];
		m.body.xRot = m.body.yRot = m.body.zRot = 0f;
		m.body.x = 0f;
		m.body.y = drop;
		m.body.z = 0f;
		m.head.x = 0f;
		m.head.y = drop;
		m.head.z = 0f;
		m.rightArm.x = -5f;
		m.leftArm.x = 5f;
		m.rightArm.y = m.leftArm.y = 2f + drop + k[FourArmsShow.SHRUG];
		m.rightArm.z = m.leftArm.z = 0f;
		m.rightLeg.x = -1.9f;
		m.leftLeg.x = 1.9f;
		m.rightLeg.y = m.leftLeg.y = 12f + drop;
		m.rightLeg.z = m.leftLeg.z = 0f;
		// final-pose rework: an optional sideways tilt about the hips and a waist twist from the track's spare channels (ShowBody.TILT
		// / TWIST, as ShowBody.apply reads them; 0 in every track that doesn't set them, so those poses are unchanged)
		float tilt = k[ShowBody.TILT], twist = k[ShowBody.TWIST];
		if (tilt != 0f) {
			ShowBody.tilt(m, tilt);
			m.rightArm.y += k[FourArmsShow.SHRUG];
			m.leftArm.y += k[FourArmsShow.SHRUG];
		}
		if (twist != 0f) {
			ShowBody.twist(m, twist);
		}
		HipBend.pitch(m, 1f, k[FourArmsShow.LEAN]);
		m.head.xRot = k[FourArmsShow.HX];
		m.head.yRot = k[FourArmsShow.HY] + twist;
		m.head.zRot = k[FourArmsShow.HZ] + tilt;
		int ra = FourArmsShow.RA, la = FourArmsShow.LA, rl = FourArmsShow.RL, ll = FourArmsShow.LL;
		m.rightArm.xRot = k[ra];
		m.rightArm.yRot = k[ra + 1] + twist;
		m.rightArm.zRot = k[ra + 2] + breathe + tilt;
		m.leftArm.xRot = k[la];
		m.leftArm.yRot = k[la + 1] + twist;
		m.leftArm.zRot = k[la + 2] - breathe + tilt;
		m.rightLeg.xRot = k[rl];
		m.rightLeg.yRot = k[rl + 1];
		m.rightLeg.zRot = k[rl + 2];
		m.leftLeg.xRot = k[ll];
		m.leftLeg.yRot = k[ll + 1];
		m.leftLeg.zRot = k[ll + 2];
		if (bend) {
			BentLimbs.bend(BentLimbs.RIGHT_ARM, k[ra + 3] + twitch, k[ra + 4]);
			BentLimbs.bend(BentLimbs.LEFT_ARM, k[la + 3] - twitch, k[la + 4]);
			BentLimbs.bend(BentLimbs.RIGHT_LEG, k[rl + 3], k[rl + 4]);
			BentLimbs.bend(BentLimbs.LEFT_LEG, k[ll + 3], k[ll + 4]);
		}
		copyLayers(m);
	}

	/**
	 * v0.17.3 touch-up: the tail's shape while the show plays (from {@code Xlr8Client}): {angle below his back at the root, at the
	 * tip (degrees, negative curls up), sway out to his left, ripple, ripple phase}. Null when not playing (the world tail).
	 * The holder's scripted camera counts as first person, which hid the world tail through the whole sequence.
	 */
	public static float[] tailShape(Player p, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.XLR8.id());
		if (t < 0f || t >= WORLD_AT) {
			return null;
		}
		float ph = t * 0.35f;
		if (t < CLAW_AT) {
			// hanging behind him, the tip lifting a little
			return new float[] { 58f, 12f, 0f, 0.12f, ph };
		}
		if (t < TAIL_AT) {
			// curling up behind as the claws flex
			float w = smooth((t - CLAW_AT) / 5f);
			return new float[] { Mth.lerp(w, 58f, 30f), Mth.lerp(w, 12f, -35f), 0.25f * w, 0.18f, ph };
		}
		if (t < CHEST_AT) {
			// the whip: swung across behind him, right to left, fast through the middle of the shot
			float w = smooth((t - TAIL_AT) / (CHEST_AT - TAIL_AT));
			return new float[] { 22f, -18f, Mth.lerp(w, -1.25f, 1.25f), 0.3f, ph * 2f };
		}
		if (t < CROUCH_AT) {
			return new float[] { 40f, -20f, 0.35f, 0.15f, ph };
		}
		if (t < RUN_AT) {
			// up behind him and curling over, the show's crouch
			return new float[] { 8f, -58f, -0.3f, 0.14f, ph };
		}
		// the dash: streaming out behind and rising (the back faces up as he leans: ~18 degrees above level at the root, ~33 at the
		// tip), rippling in the wind (final-pose rework: it was flat and, from three-quarters, foreshortened; side-on its full length
		// shows, about the show's 0.9 of his height); the sixth value stretches its links a little
		return new float[] { 18f, 2f, 0.06f, 0.2f, t * 0.9f, 1.12f };
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
			shadowStreak(st, t, smooth((t - RUN_AT) / 4f));
			speedLines(st, t - RUN_AT, smooth((t - RUN_AT - 2f) / 4f));
			KirbyDots.draw(st, st.shotSeed(shot) + 5, t - RUN_AT, BURST_DOTS);
		}
	}

	/**
	 * The final shot's black shadow streak (v0.17.3 touch-up: it was a hard-edged plank floating off to one side): a long smear
	 * that starts behind him, under his body, and widens out to the bottom-right corner (as the show draws it), its edges feathered;
	 * it grows out over the first ticks of the shot.
	 */
	private static void shadowStreak(Stage st, float t, float w) {
		if (w <= 0f) {
			return;
		}
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float reach = Mth.lerp(w, 0.2f, 1f);
		// the spine of the smear: from just behind him (a little below the middle) to past the corner
		float x0 = 0.02f, y0 = -0.12f, x1 = Mth.lerp(reach, x0, 1.7f), y1 = Mth.lerp(reach, y0, -1.25f);
		float h0 = 0.05f, h1 = 0.42f, f0 = 0.06f, f1 = 0.32f;
		int c = 0x081006;
		// the solid core, then a feathered fringe either side (alpha to 0 at the outer edge)
		quad(st, vc, p, x0, y0 + h0, x1, y1 + h1, x1, y1 - h1, x0, y0 - h0, c, 0.92f);
		st.put(vc, p, x0, y0 + h0 + f0, c, 0f);
		st.put(vc, p, x1, y1 + h1 + f1, c, 0f);
		st.put(vc, p, x1, y1 + h1, c, 0.92f);
		st.put(vc, p, x0, y0 + h0, c, 0.92f);
		st.put(vc, p, x0, y0 - h0, c, 0.92f);
		st.put(vc, p, x1, y1 - h1, c, 0.92f);
		st.put(vc, p, x1, y1 - h1 - f1, c, 0f);
		st.put(vc, p, x0, y0 - h0 - f0, c, 0f);
	}

	/** Pale speed lines racing past along the shadow streak (toward the bottom-right), a fresh set every few ticks. */
	private static void speedLines(Stage st, float t, float w) {
		if (w <= 0f) {
			return;
		}
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		long seed = st.seed;
		// along the streak's direction (down-right), in screen units
		float dx = 0.8f, dy = -0.6f;
		for (int i = 0; i < 12; i++) {
			float off = (ShowMath.hash(seed, i + 900) * 2f - 1f) * 1.1f;
			float speed = 0.16f + 0.1f * ShowMath.hash(seed, i + 920);
			float run = ((t * speed + ShowMath.hash(seed, i + 940) * 3f) % 3f) - 1.2f;
			float len = 0.25f + 0.45f * ShowMath.hash(seed, i + 960), th = 0.004f + 0.007f * ShowMath.hash(seed, i + 980);
			// perpendicular offset from the line through the middle, then running along it
			float cx = -dy * off + dx * run, cy = dx * off + dy * run;
			float ex = cx - dx * len, ey = cy - dy * len;
			int col = i % 3 == 0 ? 0xF4FFE8 : 0xB8F7A0;
			float a = 0.7f * w;
			st.put(vc, p, cx - dy * th, cy + dx * th, col, a);
			st.put(vc, p, ex - dy * th, ey + dx * th, col, 0f);
			st.put(vc, p, ex + dy * th, ey - dx * th, col, 0f);
			st.put(vc, p, cx + dy * th, cy - dx * th, col, a);
		}
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

	/**
	 * The face shot's warm light shafts, slanting in from the top left over his face (v0.17.3 touch-up: they were flat bars that
	 * turned into grey slabs over the black dots). Now a fan of soft beams from a point off the top-left corner: each beam is
	 * brightest down its middle and feathers to nothing at both edges and toward its end, with a warm wash where they cross the face.
	 */
	private static void lightShafts(Stage st, float t) {
		float a = env(t, FACE_AT, 2f, EYE_AT + 4f, 6f);
		if (a <= 0f) {
			return;
		}
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float ox = -1.35f, oy = 0.95f;
		// {angle below level (radians), half width at the far end, strength}
		float[][] beams = { { 0.42f, 0.11f, 0.6f }, { 0.58f, 0.07f, 0.45f }, { 0.3f, 0.06f, 0.4f }, { 0.74f, 0.09f, 0.34f }, { 0.18f, 0.05f, 0.28f } };
		int warm = 0xFFEC8A; // a saturated warm yellow: over the black dots it reads as gold light, not grey
		for (int i = 0; i < beams.length; i++) {
			float ang = beams[i][0] + 0.025f * Mth.sin(t * 0.15f + i * 1.7f);
			float dx = Mth.cos(ang), dy = -Mth.sin(ang), len = 2.3f;
			float nx = -dy, ny = dx; // perpendicular
			float w = beams[i][1], al = beams[i][2] * a * (0.85f + 0.15f * Mth.sin(t * 0.4f + i));
			float ex = ox + dx * len, ey = oy + dy * len;
			// the two feathered halves: full down the middle line, zero at the edges and at the far end
			for (int side = -1; side <= 1; side += 2) {
				st.put(vc, p, ox, oy, warm, al);
				st.put(vc, p, ex, ey, warm, 0f);
				st.put(vc, p, ex + side * nx * w, ey + side * ny * w, warm, 0f);
				st.put(vc, p, ox + side * nx * w * 0.15f, oy + side * ny * w * 0.15f, warm, 0f);
			}
		}
		// the warm wash on the lit side of his face
		st.radial(0xFFF6D8, 0xFFE8A0, 0xE8C060, 0.18f * a, 0f);
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
