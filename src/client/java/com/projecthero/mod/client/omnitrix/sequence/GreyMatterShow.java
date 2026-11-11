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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.env;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.pop;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.rot;
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

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		float flex = Mth.sin(t * 0.55f);
		if (t < TRANSFORM_AT) {
			if (t < FRONT_AT - 6f) {
				m.hat.copyFrom(m.head);
				return;
			}
			if (t < REACH_AT) {
				// the new hands held up and out to the sides, looking from one to the other, flexing
				float w = smooth((t - FRONT_AT + 6f) / 10f);
				rot(m.rightArm, w, -0.75f + 0.06f * flex, 0.3f, 0.75f);
				rot(m.leftArm, w, -0.75f - 0.06f * flex, -0.3f, -0.75f);
				rot(m.head, w, 0.08f, 0.25f * Mth.sin(t * 0.12f), 0f);
			} else if (t < SWING_AT) {
				// one arm thrown straight at the camera (his left), the other swung back, the grin
				float w = smooth((t - REACH_AT) / 4f);
				rot(m.leftArm, w, -1.55f, 0.35f, -0.15f);
				rot(m.rightArm, w, 0.45f, 0f, 0.35f);
				rot(m.head, w, -0.05f, -0.3f, 0.05f);
				m.body.yRot = Mth.lerp(w, m.body.yRot, -0.2f);
			} else {
				// arms out wide as the camera comes round behind
				float w = smooth((t - SWING_AT) / 4f);
				rot(m.leftArm, w, 0f, 0f, -1.35f);
				rot(m.rightArm, w, 0f, 0f, 1.35f);
				rot(m.head, w, 0.1f, 0f, 0f);
			}
			copyLayers(m);
			return;
		}
		if (t < FACE_AT) {
			// his back: the arms still out, the head (just grown) bowed
			rot(m.leftArm, 1f, 0f, 0f, -1.2f);
			rot(m.rightArm, 1f, 0f, 0f, 1.2f);
			rot(m.head, 1f, 0.25f, 0f, 0f);
		} else if (t < NAPE_AT) {
			// the face: the head rocking from side to side, chin up, the grin
			float rock = Mth.sin((t - FACE_AT) * 0.24f);
			rot(m.head, 1f, -0.15f, 0.2f * rock, 0.28f * rock);
			rot(m.leftArm, 1f, 0f, 0f, -0.4f);
			rot(m.rightArm, 1f, 0f, 0f, 0.4f);
		} else if (t < FRONT2_AT) {
			// from behind: the head turning to look back over his shoulder
			float turn = smooth((t - 140f) / 10f);
			rot(m.head, 1f, -0.1f, -0.9f * turn, 0f);
			rot(m.leftArm, 1f, 0.1f, 0f, -0.35f);
			rot(m.rightArm, 1f, 0.1f, 0f, 0.35f);
		} else if (t < STANCE_AT + 4f) {
			// the face, front: steady, the hands rising
			float w = smooth((t - FRONT2_AT) / 16f);
			rot(m.head, 1f, -0.05f, 0f, 0f);
			rot(m.leftArm, w, -0.35f, 0f, -0.55f);
			rot(m.rightArm, w, -0.35f, 0f, 0.55f);
			rot(m.rightLeg, w, 0f, 0f, 0.3f);
			rot(m.leftLeg, w, 0f, 0f, -0.3f);
		} else if (t < LEAP_AT - 4f) {
			// the wide stance: legs planted wide, a lean in, the hands spread low and out, fingers flexing
			float w = smooth((t - STANCE_AT - 4f) / 4f);
			rot(m.leftArm, w, -0.35f + 0.05f * flex, 0f, -0.75f);
			rot(m.rightArm, w, -0.35f - 0.05f * flex, 0f, 0.75f);
			rot(m.rightLeg, 1f, -0.1f, 0f, 0.45f);
			rot(m.leftLeg, 1f, 0.1f, 0f, -0.45f);
			rot(m.head, 1f, -0.12f, 0f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.18f);
		} else {
			// the leap: one arm flung up and out, the other reaching down at the camera, legs splayed, the body tipped over
			float w = smooth((t - LEAP_AT + 4f) / 4f);
			float drift = 0.03f * Mth.sin(t * 0.3f);
			rot(m.rightArm, w, -0.5f, 0f, 2.15f + drift);
			rot(m.leftArm, w, -1.05f, -0.2f, -0.55f - drift);
			rot(m.rightLeg, w, -0.35f, 0f, 0.85f);
			rot(m.leftLeg, w, 0.25f, 0f, -0.6f);
			rot(m.head, w, -0.2f, -0.15f, 0.25f);
			m.body.zRot = Mth.lerp(w, m.body.zRot, 0.12f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.15f);
		}
		copyLayers(m);
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

	// ================================================================ the grey hands (on the human body)

	/** Before the change: Ben's hands swell into big grey Galvan hands, popping up springily (left first, the watch hand). */
	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < HANDS_AT - 2f || t >= TRANSFORM_AT) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(GM_SKIN));
		// the grey of the side of Grey Matter's own head (his skin's hands are the same grey)
		float u0 = 1f / 64f, v0 = 10f / 64f, u1 = 5f / 64f, v1 = 14f / 64f;
		for (int side = 0; side < 2; side++) {
			float g = pop(t - HANDS_AT - side * 3f, 8f);
			if (g <= 0.02f) {
				continue;
			}
			float wig = 0.15f * Mth.sin(t * 0.9f + side * 2f);
			float grow = 0.7f + 1.1f * g;
			// the arm's box: right -3..1, left -1..3 (px); the hand fills its last 4 px, swollen
			float cx = side == 0 ? 1f : -1f;
			float y0 = 7.6f - 1.2f * g, y1 = 11f + 1.6f * g + wig;
			AlienParts.on(pose, side == 0 ? model.leftArm : model.rightArm, () -> AlienParts.box(pose, vc, cx - 2f * grow, y0, -2f * grow, cx + 2f * grow, y1,
					2f * grow, u0, v0, u1, v1, 0xFFFFFFFF, LightTexture.FULL_BRIGHT));
			// three thick finger stubs splayed out of the bottom
			for (int f = -1; f <= 1; f++) {
				float fx = cx + f * 1.5f * grow, fz = -0.6f * grow;
				float len = (2.4f + 0.6f * f * f) * g * (1f + 0.2f * Mth.sin(t * 0.8f + f + side));
				AlienParts.on(pose, side == 0 ? model.leftArm : model.rightArm, () -> AlienParts.box(pose, vc, fx - 0.55f, y1 - 0.2f, fz - 0.55f, fx + 0.55f,
						y1 + len, fz + 0.55f, u0, v0, u1, v1, 0xFFFFFFFF, LightTexture.FULL_BRIGHT));
			}
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
