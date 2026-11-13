package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.minecraft.client.Camera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.env;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.pop;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show, try to be as expressive as you can without bending the limbs"): Grey Matter's
 * transformation shot for shot, cut to the compilation's audio (tick = (video s - 111.10) * 20). The beats (ticks after the slap):
 * <pre>
 *   0-6     the slap (front medium shot, the dim room)
 *   4-28    FULL GREEN FLASH, fading off the next shot 28-46
 *   28-62   front, pulling back: his hands swell into big grey Galvan hands (38-50), held up and out, fingers flexing; olive field,
 *           black Kirby dots
 *   62-80   from his left front: one arm thrown out at the camera, the grin; two one-tick negative flashes (66, 75)
 *   78-86   the camera swings round behind him, arms out wide
 *   86-88   WHITE FLASH -- the body changes under it (86)
 *   86-98   his back, the badge, close, whipping round
 *   98-108  close on the shoulder and neck, the big head coming up into frame
 *   108-134 Grey Matter's face close: the frog eyes, the toothy grin, the head rocking side to side (negative flash at 114)
 *   134-150 the back of his head, from behind (negative flash at 142), whip round
 *   150-172 the face from the front, dimming and brightening, pulling back
 *   172-192 full body, the wide stance, hands spread low
 *   190-202 FULL GREEN FLASH
 *   200-230 the leap: one arm flung up, the other reaching at the camera, legs splayed, on the green ray burst
 *   228-240 FULL GREEN FLASH, then the world, the green fading off by 254
 * </pre>
 */
public final class GreyMatterShow extends ShowSequenceClient {
	static final float SLAP_END = 6f, FRONT_AT = 28f, HANDS_AT = 38f, REACH_AT = 62f, SWING_AT = 78f, WHITE_AT = 85f, BACK_AT = 86f,
			NECK_AT = 98f, FACE_AT = 108f, NAPE_AT = 134f, FRONT2_AT = 150f, STANCE_AT = 172f, GREEN1_AT = 189f, LEAP_AT = 200f, GREEN2_AT = 228f,
			WORLD_AT = 240f;
	static final int TRANSFORM_AT = 86;
	/** The one- or two-tick negative flashes: the field goes white with olive blotches. */
	private static final float[] NEGATIVE = { 66f, 75f, 114f, 132f, 142f };

	private static final ResourceLocation GM_SKIN = ProjectHeroMod.id("textures/entity/omnitrix/grey_matter.png");

	private static final int BURST_IN = 0x9CF77A, BURST_MID = 0x3FD33A, BURST_OUT = 0x1D8A18, BURST_RAY = 0x15641A, BURST_DOT = 0x0D3F0B;

	private static final KirbyDots.Style DOTS = KirbyDots.Style.show().density(1.5f).rim(1.5f).clear(0.36f),
			NEG_DOTS = KirbyDots.Style.show().density(1.5f).rim(1.5f).clear(0.36f).rgb(0x9DBE4E),
			BURST_DOTS = KirbyDots.Style.show().bands(3).rgb(BURST_DOT).density(1.4f).clear(0.42f).flow(1.6f);

	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(ShowShots.slapShot(0f, SLAP_END), new float[][] {
			// the green: framing him from the front, slightly right, pulling back as it fades
			{ 6.01f, 1.25f, 14f, 1.35f, 1.25f, 0f, 0f },
			{ 28f, 1.35f, 12f, 1.35f, 1.22f, 0f, 0f },
			{ 62f, 2.0f, 8f, 1.25f, 1.15f, 0f, 0f },
			// his left front, the arm thrown at the camera
			{ 62.01f, 1.55f, -34f, 1.4f, 1.3f, 0f, 0.1f },
			{ 78f, 1.4f, -46f, 1.4f, 1.32f, 0f, 0.1f },
			// swinging round behind him
			{ 86f, 1.5f, -172f, 1.4f, 1.3f, 0f, 0f },
			// his back, the badge close, whipping round
			{ 86.01f, 0.95f, 172f, 1.32f, 1.3f, 0.06f, 0f },
			{ 98f, 0.75f, 140f, 1.3f, 1.28f, 0.06f, 0f },
			// the shoulder and neck, close, the head coming up
			{ 98.01f, 0.95f, -125f, 1.55f, 1.62f, -0.08f, 0f },
			{ FACE_AT, 0.95f, -105f, 1.65f, 1.75f, -0.05f, 0f },
			// the face, close, from his left front
			{ FACE_AT + 0.01f, 1.25f, -28f, 1.8f, 1.9f, 0f, 0.05f },
			{ NAPE_AT, 1.15f, -14f, 1.82f, 1.9f, 0f, 0.05f },
			// the back of his head, from below behind
			{ NAPE_AT + 0.01f, 1.8f, 176f, 1.45f, 1.85f, 0f, 0f },
			{ 146f, 1.7f, 172f, 1.5f, 1.85f, 0f, 0f },
			{ FRONT2_AT, 1.6f, 60f, 1.75f, 1.88f, 0f, 0f },
			// the face from the front, a little low, pulling back to the full body
			{ FRONT2_AT + 0.01f, 1.25f, 2f, 1.72f, 1.88f, 0f, 0f },
			{ STANCE_AT, 2.1f, 0f, 1.55f, 1.65f, 0f, 0f },
			{ 190f, 3.3f, 0f, 1.1f, 1.05f, 0f, 0f },
			// the leap, low in front
			{ 190.01f, 2.3f, -14f, 0.6f, 1.05f, 0f, 0f },
			{ WORLD_AT, 2.15f, -10f, 0.6f, 1.05f, 0f, 0f } })).linear(SWING_AT, BACK_AT).linear(146f, FRONT2_AT);

	public GreyMatterShow() {
		super(Aliens.GREY_MATTER.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public int burstAt() {
		return 232;
	}

	/** Grey Matter is a third of a player: the camera keys shrink with him (the base class only ever grows them). */
	@Override
	public float cameraScale(Player p, float t) {
		// (his drawn body sits lower and smaller than his hitbox: the frog legs splay, so 0.85 of it, found on film)
		return t >= transformAt() ? 0.85f * Mth.clamp(p.getBbHeight() / 1.8f, 0.2f, 1f) : 1f;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	private static boolean negative(float t) {
		for (float n : NEGATIVE) {
			if (t >= n && t < n + 1.5f) {
				return true;
			}
		}
		return false;
	}

	// ================================================================ the poses

	/**
	 * v0.17.3 touch-up (user: "make sure their limbs don't go weird or detach from the body and touch up the effects and poses"): one
	 * keyframed pose track ({@link FourArmsShow.Track}: Catmull-Rom through the keys, held where the show holds), posed through
	 * {@link ShowBody} so the torso turns about the hips with the shoulders following, and the elbows and knees bend ({@link BentLimbs}):
	 * the slap hand up by his head, the new hands held up palms out, the frog crouch with the knees out, and the leap.
	 */
	private static final float[] DIAL = { -0.78f, 0.05f, -0.12f, 1.62f, 1.18f };
	static final FourArmsShow.Track POSE = ShowBody.track()
			// ---- the slap: the dial arm across the belly; the right hand flung up by his head, then slapping down
			.at(0f).head(0.04f, 0.05f, 0.1f).lArm(DIAL[0], DIAL[1], DIAL[2], DIAL[3], DIAL[4]).rArm(-0.35f, 0f, 1.1f, 1.9f, 0.6f)
			.legs(0f, 0f, 0.05f, 0.08f, 0f)
			.at(2f).rArm(-0.4f, 0f, 2.6f, 0.45f, 1.5f).head(0.02f, 0.05f, 0.08f)
			.at(4f).hold().rArm(-0.5f, 0f, 0.5f, 2.2f, -0.25f).head(0.08f, 0f, -0.12f)
			.at(6.5f).rArm(-1.0f, -0.6f, 0.2f, 0.95f, 0.35f).head(0.3f, 0f, 0f)
			.at(18f).hold()
			// ---- the new hands held up and out, palms forward, looking from one to the other
			.at(FRONT_AT).head(0.08f, 0f, 0f).rArm(-0.55f, 0.3f, 0.85f, 1.35f, 1.1f).lArm(-0.55f, -0.3f, -0.85f, 1.35f, -1.1f).legs(0f, 0f, 0.1f, 0.15f, 0.3f)
			.at(40f).head(0.05f, -0.28f, 0.05f).rArm(-0.95f, 0.2f, 0.6f, 1.5f, 1.0f).lArm(-0.45f, -0.3f, -0.95f, 1.1f, -1.2f)
			.at(52f).head(0.05f, 0.28f, -0.05f).rArm(-0.45f, 0.3f, 0.95f, 1.1f, 1.2f).lArm(-0.95f, -0.2f, -0.55f, 1.5f, -1.0f)
			.at(REACH_AT).hold().head(0.05f, 0.1f, 0f)
			// ---- the left hand thrown at the camera, the other swung back; the grin
			.at(65f).head(-0.05f, -0.3f, 0.05f).split(-0.25f).lArm(-1.55f, 0.35f, -0.15f, 0.15f, 0f).rArm(0.45f, 0f, 0.4f, 0.7f, 0.3f)
			.at(76f).hold().lArm(-1.45f, 0.4f, -0.2f, 0.25f, 0f)
			// ---- the arms out wide as the camera swings round behind him
			.at(82f).head(0.1f, 0f, 0f).split(0f).arms(0f, 0f, 1.35f, 0.4f, 1.0f)
			.at(TRANSFORM_AT).hold()
			// ---- Grey Matter's back: the arms still out, the new head bowed
			.at(TRANSFORM_AT + 0.01f).head(0.25f, 0f, 0f).arms(0f, 0f, 1.2f, 0.5f, 1.0f).legs(0f, 0f, 0.15f, 0.15f, 1.57f)
			.at(NECK_AT + 4f).hold()
			// ---- the face: the arms down, loose (the head rocks on top, below)
			.at(FACE_AT).head(-0.15f, 0f, 0f).arms(0f, 0f, 0.4f, 0.35f, 0.3f)
			.at(NAPE_AT).hold().arms(0.1f, 0f, 0.35f, 0.3f, 0.3f).head(-0.1f, 0f, 0f)
			.at(FRONT2_AT).head(-0.05f, 0f, 0f)
			// ---- the face, front: the hands rising, the knees starting to spread
			.at(164f).arms(-0.35f, 0f, 0.55f, 0.9f, 0.6f).legs(0f, 0f, 0.35f, 0.35f, 1.57f).drop(0.5f)
			// ---- the frog crouch: knees out, a lean in, the hands spread low and wide, chin up
			.at(STANCE_AT + 4f).drop(3f).lean(0.3f).head(-0.28f, 0f, 0f).arms(-0.55f, 0f, 0.85f, 0.85f, 0.3f).legs(-0.35f, 0f, 1.0f, 1.1f, 1.57f)
			.at(LEAP_AT - 6f).hold()
			// ---- the leap: one arm flung up and out, the other reaching at the camera, the legs kicked out bent, the body tipped over
			.at(LEAP_AT).drop(2f).lean(0.2f).set(ShowBody.TILT, 0.12f).head(-0.25f, -0.15f, 0.22f)
			.rArm(-0.5f, 0f, 2.15f, 0.25f, 1.2f).lArm(-1.25f, -0.2f, -0.45f, 0.25f, 0f)
			.rLeg(-0.6f, 0f, 1.1f, 1.3f, 1.57f).lLeg(0.1f, 0f, -1.0f, 1.1f, -1.57f)
			.at(GREEN2_AT + 6f).hold()
			// eased to standing under the green out
			.at(WORLD_AT + 10f).hold().drop(0f).lean(0f).set(ShowBody.TILT, 0f).head(0f, 0f, 0f).arms(0f, 0f, 0.1f, 0f, 0f).legs(0f, 0f, 0.04f, 0f, 0f);

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		float[] k = POSE.sample(t);
		ShowBody.apply(m, k, true);
		float flex = Mth.sin(t * 0.55f);
		if (t >= FRONT_AT && t < REACH_AT) {
			// the fingers working: the arms flex a little as he stares at the hands
			m.rightArm.xRot += 0.06f * flex;
			m.leftArm.xRot -= 0.06f * flex;
		} else if (t >= FACE_AT && t < NAPE_AT) {
			// the face: the head rocking from side to side, the grin
			float rock = Mth.sin((t - FACE_AT) * 0.24f);
			m.head.yRot += 0.2f * rock;
			m.head.zRot += 0.28f * rock;
		} else if (t >= NAPE_AT && t < FRONT2_AT) {
			// from behind: the head turning to look back over his shoulder
			m.head.yRot -= 0.9f * smooth((t - 140f) / 10f);
		} else if (t >= STANCE_AT && t < LEAP_AT) {
			m.rightArm.xRot -= 0.05f * flex;
			m.leftArm.xRot += 0.05f * flex;
		} else if (t >= LEAP_AT) {
			m.rightArm.zRot += 0.03f * Mth.sin(t * 0.3f);
		}
		ShowBody.layers(m);
	}

	// ================================================================ the grey hands (on the human body)

	/**
	 * Before the change: Ben's hands swell into big grey Galvan hands, popping up springily (left first, the watch hand). v0.17.3
	 * touch-up: they ride the bent forearm ({@link BentLimbs#toForearm}), the three long fingers splay and curl with the flexing, and a
	 * thumb sticks out to the side, the way the show draws them.
	 */
	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < HANDS_AT - 2f || t >= TRANSFORM_AT) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(GM_SKIN));
		boolean slim = p.getSkin().model() == PlayerSkin.Model.SLIM;
		// the grey of the side of Grey Matter's own head (his skin's hands are the same grey)
		float u0 = 1f / 64f, v0 = 10f / 64f, u1 = 5f / 64f, v1 = 14f / 64f;
		int light = LightTexture.FULL_BRIGHT;
		for (int side = 0; side < 2; side++) {
			float g = pop(t - HANDS_AT - side * 3f, 8f);
			if (g <= 0.02f) {
				continue;
			}
			int s = side;
			float wig = 0.15f * Mth.sin(t * 0.9f + side * 2f);
			float grow = 0.7f + 1.1f * g;
			// the arm's box: right -3..1, left -1..3 (px); the hand fills its last 4 px, swollen
			float cx = side == 0 ? 1f : -1f;
			float y0 = 7.6f - 1.2f * g, y1 = 11f + 1.6f * g + wig;
			// which way is out from the body (the thumb's side): -x for the right arm, +x for the left
			float out = side == 0 ? 1f : -1f;
			AlienParts.on(pose, side == 0 ? model.leftArm : model.rightArm, () -> {
				BentLimbs.toForearm(pose, s == 0 ? BentLimbs.LEFT_ARM : BentLimbs.RIGHT_ARM, slim);
				// the palm, a little flatter front to back than it is wide
				AlienParts.box(pose, vc, cx - 2f * grow, y0, -1.6f * grow, cx + 2f * grow, y1, 1.6f * grow, u0, v0, u1, v1, 0xFFFFFFFF, light);
				// three long fingers, splayed out of the bottom and curling forward with the flex
				for (int f = -1; f <= 1; f++) {
					float fx = cx + f * 1.45f * grow, fz = -0.4f * grow;
					float len = (2.8f + 0.5f * f * f) * g * (1f + 0.15f * Mth.sin(t * 0.8f + f + s));
					float curl = 0.25f + 0.2f * Mth.sin(t * 0.55f + f * 0.9f + s * 2f);
					pose.pushPose();
					pose.translate(fx / 16f, (y1 - 0.3f) / 16f, fz / 16f);
					pose.mulPose(com.mojang.math.Axis.ZP.rotation(f * 0.22f));
					pose.mulPose(com.mojang.math.Axis.XP.rotation(curl));
					AlienParts.box(pose, vc, -0.6f, 0f, -0.6f, 0.6f, len, 0.6f, u0, v0, u1, v1, 0xFFFFFFFF, light);
					// the round fingertip pad
					AlienParts.box(pose, vc, -0.8f, len - 0.4f, -0.8f, 0.8f, len + 0.7f, 0.8f, u0, v0, u1, v1, 0xFFFFFFFF, light);
					pose.popPose();
				}
				// the thumb, out to the side and down
				pose.pushPose();
				pose.translate((cx + out * 2f * grow) / 16f, (y0 + 1.5f * grow) / 16f, -0.3f * grow / 16f);
				pose.mulPose(com.mojang.math.Axis.ZP.rotation(-out * 0.9f));
				AlienParts.box(pose, vc, -0.6f, 0f, -0.6f, 0.6f, 2.4f * g, 0.6f, u0, v0, u1, v1, 0xFFFFFFFF, light);
				pose.popPose();
			});
		}
	}

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 3f, 22f);
		ShowShots.bodyGlow(pose, buffers, cam, p, t, partial, 82f, 89f, 0xE8FFE0, 0xFFFFFF);
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		if (t >= WORLD_AT - 0.5f) {
			return; // under the full green, and never over the world view
		}
		int shot = TRACK.shot(t);
		if (t < SLAP_END) {
			st.gradient(0x7A5A3A, 0x4E3622, 0x1E140C);
		} else if (t < GREEN1_AT + 3f) {
			if (negative(t)) {
				st.gradient(0xFFFFFF, 0xF4F8EC, 0xE6EED8);
				KirbyDots.draw(st, st.shotSeed(shot), t - FRONT_AT, NEG_DOTS);
				return;
			}
			st.showBackground(); // v0.17.3: the shared show green (user: "the background for all the transformations the same green")
			// the field's brighter diagonal band
			lightBand(st);
			KirbyDots.draw(st, st.shotSeed(shot), t - FRONT_AT, DOTS);
		} else {
			st.gradient(BURST_IN, BURST_MID, BURST_OUT);
			st.rays(st.seed, t - LEAP_AT, BURST_RAY, 14);
			shadowStreak(st, smooth((t - LEAP_AT) / 4f));
			KirbyDots.draw(st, st.shotSeed(shot) + 5, t - LEAP_AT, BURST_DOTS);
		}
	}

	/** A soft lighter band across the olive field, low left to high right. */
	private static void lightBand(Stage st) {
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		Xlr8Show.quad(st, vc, p, -1.5f, -0.35f, 1.5f, 0.25f, 1.5f, 0.55f, -1.5f, -0.05f, 0xD8F08A, 0.45f);
	}

	/** The leap's black shadow streak behind him, up to the top right. */
	private static void shadowStreak(Stage st, float w) {
		if (w <= 0f) {
			return;
		}
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		Xlr8Show.quad(st, vc, p, -0.1f, -0.2f, 0.12f, -0.3f, 1.5f, 0.75f, 1.5f, 1.35f, 0x081205, 0.9f * w);
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 4.5f);
		// the green holds, then thins off the front shot slowly (his new hands come out of it)
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, 9f, 1f, FRONT_AT, 18f, 1f);
		ShowShots.flash(st, t, 0xFFFFFF, WHITE_AT, 0.5f, 88f, 3f, 1f);
		ShowShots.whipStreaks(st, t, 90f, NECK_AT);
		ShowShots.whipStreaks(st, t, 146f, FRONT2_AT + 1f);
		// the front shot's flicker: the light dims and comes back
		float dim = env(t, 158f, 2f, 166f, 3f);
		if (dim > 0f) {
			st.fill(0x101408, 0.45f * dim * (0.8f + 0.2f * Mth.sin(t * 2.3f)));
		}
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, GREEN1_AT, 2f, 196f, 8f, 1f);
		ShowShots.greenOut(st, t, GREEN2_AT, WORLD_AT, total());
	}
}
