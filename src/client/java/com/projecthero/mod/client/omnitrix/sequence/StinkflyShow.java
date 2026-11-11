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
 * look exactly like how it looks in the show, try to be as expressive as you can without bending the limbs"): Stinkfly's
 * transformation shot for shot, cut to the compilation's audio (tick = (video s - 160.70) * 20). The beats (ticks after the slap):
 * <pre>
 *   0-6     the slap (front medium shot, the dim barn)
 *   4-32    FULL GREEN FLASH, thinning off the next shot to 42
 *   32-56   over the raised forearm: the hand swells yellow-green, spiky, the dial blazing; olive field, black Kirby dots
 *   56-88   the same arm: the hand darkens and shrinks into a small black claw
 *   87-100  WHITE FLASH, fading off the face
 *   95-145  Ben's face close, front: eyes screwed shut, the grin straining; green streaks race behind him (108-125), then four
 *           orange eye-stalk orbs sprout round his head on brown rays (125-145)
 *   145-150 WHITE FLASH -- the body changes under it (146)
 *   150-200 low on the torso, glowing pale cyan: the insect arms flung out, the wings bursting up behind (165-200)
 *   200-205 dark cut
 *   205-228 his forehead badge, close, a glint off it at 220
 *   228-235 WHITE FLASH
 *   235-245 the head and its eye-stalks, front, out of the white
 *   245-290 full body hovering, low in front, arms out, the camera easing back; green ray burst, black shadow below
 *   287-296 FULL GREEN FLASH, then a YELLOW flash (295-304)
 *   304-322 the world, the green fading off
 * </pre>
 */
public final class StinkflyShow extends ShowSequenceClient {
	static final float SLAP_END = 6f, ARM_AT = 32f, CLAW_AT = 56f, WHITE1_AT = 87f, FACE_AT = 95f, STREAK_AT = 108f, ORBS_AT = 125f,
			WHITE2_AT = 144.5f, TORSO_AT = 150f, WINGS_AT = 165f, DARK_AT = 200f, BADGE_AT = 205f, WHITE3_AT = 228f, HEAD_AT = 235f, HOVER_AT = 245f,
			GREEN2_AT = 287f, YELLOW_AT = 295f, WORLD_AT = 306f;
	static final int TRANSFORM_AT = 146;

	private static final ResourceLocation SF_SKIN = ProjectHeroMod.id("textures/entity/omnitrix/stinkfly.png");

	private static final int BURST_IN = 0x8CF06A, BURST_MID = 0x34C82C, BURST_OUT = 0x167A12, BURST_RAY = 0x0F5410, BURST_DOT = 0x0A3A08;

	private static final KirbyDots.Style DOTS = KirbyDots.Style.show().density(1.5f).rim(1.6f).clear(0.34f),
			FACE_DOTS = KirbyDots.Style.show().density(1.3f).rim(1.6f).clear(0.48f),
			BURST_DOTS = KirbyDots.Style.show().bands(3).rgb(BURST_DOT).density(1.4f).clear(0.45f).flow(1.6f);

	private static final float W = CameraTrack.W;
	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(ShowShots.slapShot(0f, SLAP_END), new float[][] {
			// over the raised forearm, from above at his left
			{ 6.01f, 0.95f, -40f, W, W, W, W },
			{ CLAW_AT, 0.85f, -46f, W, W, W, W },
			{ WHITE1_AT + 3f, 0.78f, -50f, W, W, W, W },
			// the face, close, front
			{ WHITE1_AT + 3.01f, 0.95f, 0f, 1.6f, 1.6f, 0f, 0f },
			{ ORBS_AT, 0.88f, 0f, 1.6f, 1.6f, 0f, 0f },
			{ TRANSFORM_AT, 0.84f, 0f, 1.6f, 1.6f, 0f, 0f },
			// low on the torso
			{ TRANSFORM_AT + 0.01f, 1.15f, 0f, 0.95f, 1.15f, 0f, 0f },
			{ DARK_AT, 1.3f, 0f, 0.95f, 1.2f, 0f, 0f },
			// the top of the head, then the forehead badge, close
			{ DARK_AT + 0.01f, 0.5f, 0f, 1.75f, 1.7f, 0f, 0f },
			{ BADGE_AT, 0.48f, 0f, 1.75f, 1.7f, 0f, 0f },
			{ BADGE_AT + 0.01f, 0.42f, 0f, 1.52f, 1.52f, 0f, 0.12f },
			{ WHITE3_AT + 2f, 0.38f, 0f, 1.52f, 1.52f, 0f, 0.12f },
			// the head and eye-stalks
			{ WHITE3_AT + 2.01f, 0.95f, 0f, 1.5f, 1.45f, 0f, 0f },
			{ HOVER_AT, 0.9f, 0f, 1.5f, 1.45f, 0f, 0f },
			// full body hovering, low in front, easing back
			{ HOVER_AT + 0.01f, 1.2f, -14f, 0.7f, 1.1f, 0f, 0f },
			{ 262f, 1.65f, -10f, 0.6f, 1.0f, 0f, 0f },
			{ WORLD_AT, 1.7f, -8f, 0.6f, 1.0f, 0f, 0f } })).shake(FACE_AT, TRANSFORM_AT, 0.006f, 60f);

	public StinkflyShow() {
		super(Aliens.STINKFLY.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public int burstAt() {
		return 292;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	@Override
	public double fov(float t) {
		double fov = 62;
		if (t >= BADGE_AT && t < WHITE3_AT + 2f) {
			fov -= 6 * smooth((t - BADGE_AT) / 18f);
		}
		return fov;
	}

	// ================================================================ the poses

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		if (t < TRANSFORM_AT) {
			if (t >= WHITE1_AT) {
				// the face: chin up, straining, a tremble that builds
				float w = smooth((t - WHITE1_AT) / 6f);
				float shake = 0.03f * smooth((t - STREAK_AT) / 20f);
				rot(m.head, w, -0.12f + shake * Mth.sin(t * 3.1f), shake * Mth.cos(t * 2.7f), 0f);
				m.body.xRot = Mth.lerp(w, m.body.xRot, -0.06f);
			}
			m.hat.copyFrom(m.head);
			return;
		}
		float buzz = Mth.sin(t * 2.4f) * 0.02f;
		if (t < DARK_AT) {
			// the insect arms flung out, low and wide, trembling as the wings burst
			float w = smooth((t - TRANSFORM_AT) / 6f);
			rot(m.rightArm, w, -0.35f + buzz, 0f, 1.15f);
			rot(m.leftArm, w, -0.35f - buzz, 0f, -1.15f);
			rot(m.rightLeg, w, 0f, 0f, 0.25f);
			rot(m.leftLeg, w, 0f, 0f, -0.25f);
			rot(m.head, w, -0.25f, 0f, 0f);
		} else if (t < HOVER_AT) {
			// the head close-ups: level, straight at the camera
			rot(m.head, 1f, 0f, 0f, 0f);
			rot(m.rightArm, 1f, -0.3f, 0f, 0.9f);
			rot(m.leftArm, 1f, -0.3f, 0f, -0.9f);
		} else {
			// hovering: head up, the arms out wide and forward, claws ready, legs dangling apart, the body tipped forward a little
			float w = smooth((t - HOVER_AT) / 4f);
			float bob = Mth.sin(t * 0.35f) * 0.04f;
			rot(m.rightArm, w, -0.75f + bob, 0.2f, 1.0f);
			rot(m.leftArm, w, -0.75f - bob, -0.2f, -1.0f);
			rot(m.rightLeg, w, 0.25f + bob, 0f, 0.35f);
			rot(m.leftLeg, w, 0.35f - bob, 0f, -0.3f);
			rot(m.head, w, -0.4f, 0.1f, 0f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.22f);
		}
		if (m instanceof PlayerModel<?> pm) {
			pm.rightSleeve.copyFrom(m.rightArm);
			pm.leftSleeve.copyFrom(m.leftArm);
			pm.rightPants.copyFrom(m.rightLeg);
			pm.leftPants.copyFrom(m.leftLeg);
			pm.jacket.copyFrom(m.body);
		}
		m.hat.copyFrom(m.head);
	}

	// ================================================================ the hand (on the human body)

	/** The watch hand: it swells yellow-green and spiky, then darkens and shrinks into a small black claw. */
	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < ARM_AT || t >= WHITE1_AT + 3f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SF_SKIN));
		float u0 = 42f / 64f, v0 = 21f / 64f, u1 = 46f / 64f, v1 = 27f / 64f;
		float g = pop(t - ARM_AT - 2f, 9f);
		float claw = smooth((t - CLAW_AT) / 22f);
		float grow = (0.8f + 0.7f * g) * (1f - 0.35f * claw);
		int tint = ShowMath.lerpRgb(claw, 0xFFFFFF, 0x141414) | 0xFF000000;
		float wig = 0.2f * Mth.sin(t * 0.7f);
		float y0 = 7.4f, y1 = 11.2f + 1.2f * g * (1f - claw) + wig;
		AlienParts.on(pose, model.leftArm, () -> {
			AlienParts.box(pose, vc, 1f - 2.1f * grow, y0, -2.1f * grow, 1f + 2.1f * grow, y1, 2.1f * grow, u0, v0, u1, v1, tint, LightTexture.FULL_BRIGHT);
			// the spiky fingers, splayed, then curling in shorter as it turns into the claw
			for (int f = 0; f < 4; f++) {
				float ang = (f - 1.5f) * 0.55f;
				float len = (3.2f - 1.8f * claw) * g;
				float fx = 1f + Mth.sin(ang) * 1.9f * grow, fz = -Mth.cos(ang) * 0.6f * grow;
				AlienParts.box(pose, vc, fx - 0.45f, y1 - 0.3f, fz - 0.45f, fx + 0.45f, y1 + len, fz + 0.45f, u0, v0, u1, v1, tint, LightTexture.FULL_BRIGHT);
			}
		});
	}

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 4f, 44f);
		// the new body glowing pale cyan as it forms
		ShowShots.bodyGlow(pose, buffers, cam, p, t, partial, TRANSFORM_AT, 178f, 0x5AC8F0, 0xC8F4FF);
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		if (t >= WORLD_AT - 0.5f) {
			return; // under the full green, and never over the world view
		}
		int shot = TRACK.shot(t);
		if (t < SLAP_END) {
			st.gradient(0x5E6474, 0x3A4252, 0x161A22);
		} else if (t < DARK_AT) {
			st.showBackground(); // v0.17.3: the shared show green (user: "the background for all the transformations the same green")
			if (t >= FACE_AT && t < TRANSFORM_AT) {
				streaks(st, t);
				orbs(st, t);
				KirbyDots.draw(st, st.shotSeed(shot), t - FACE_AT, FACE_DOTS);
			} else {
				KirbyDots.draw(st, st.shotSeed(shot), t - ARM_AT, DOTS);
			}
		} else if (t < BADGE_AT) {
			st.gradient(0x6E6A8A, 0x2A2838, 0x050508);
		} else if (t < HEAD_AT) {
			// the badge: on black, a lilac band down the right
			st.gradient(0x101014, 0x08080A, 0x020203);
			Xlr8Show.quad(st, st.flat(), st.last(), 0.42f, -1.5f, 1.5f, -1.5f, 1.5f, 1.5f, 0.42f, 1.5f, 0x9A96C2, 1f);
		} else if (t < HOVER_AT) {
			st.showBackground(); // v0.17.3: the shared show green (user: "the background for all the transformations the same green")
			KirbyDots.draw(st, st.shotSeed(shot), t - HEAD_AT, DOTS);
		} else {
			st.gradient(BURST_IN, BURST_MID, BURST_OUT);
			st.rays(st.seed, t - HOVER_AT, BURST_RAY, 14);
			// the black shadow below him, up from the bottom
			float w = smooth((t - HOVER_AT) / 4f);
			Xlr8Show.quad(st, st.flat(), st.last(), -0.25f, -1.5f, 0.35f, -1.5f, 0.12f, -0.25f, -0.02f, -0.22f, 0x061004, 0.9f * w);
			KirbyDots.draw(st, st.shotSeed(shot) + 5, t - HOVER_AT, BURST_DOTS);
		}
	}

	/** The face shot's light-green streaks racing sideways behind him, crackling. */
	private static void streaks(Stage st, float t) {
		float a = env(t, STREAK_AT, 2f, ORBS_AT, 4f);
		if (a <= 0f) {
			return;
		}
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		long seed = st.seed;
		for (int i = 0; i < 10; i++) {
			float y = (ShowMath.hash(seed, i + 500) * 2f - 1f) * 0.75f;
			float h = 0.012f + 0.02f * ShowMath.hash(seed, i + 520);
			float len = 0.4f + 0.7f * ShowMath.hash(seed, i + 540);
			float x0 = -1.4f + ((t - STREAK_AT) * (0.08f + 0.06f * ShowMath.hash(seed, i + 560)) + ShowMath.hash(seed, i + 580) * 2.8f) % 2.8f;
			int col = i % 3 == 0 ? 0xE6FF7A : 0x9EDC3A;
			st.rect(vc, p, x0, y - h, x0 + len, y + h, col, 0.15f * a, 0.9f * a);
		}
	}

	/** The four orange eye-stalk orbs sprouting round his head, on brown rays. */
	private static void orbs(Stage st, float t) {
		float g = pop(t - ORBS_AT, 6f);
		if (g <= 0.02f) {
			return;
		}
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float k = st.k();
		// brown rays out to both sides behind the head
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 0; i < 3; i++) {
				float y = -0.25f + i * 0.22f;
				st.put(vc, p, side * 0.1f, y + 0.02f, 0x6A3A14, 0f);
				st.put(vc, p, side * 1.4f, y + 0.12f, 0x6A3A14, 0.75f * g);
				st.put(vc, p, side * 1.4f, y - 0.06f, 0x6A3A14, 0.75f * g);
				st.put(vc, p, side * 0.1f, y - 0.02f, 0x6A3A14, 0f);
			}
		}
		VertexConsumer dots = st.dots();
		float[][] at = { { -0.36f, 0.24f }, { 0.36f, 0.24f }, { -0.33f, -0.16f }, { 0.33f, -0.16f } };
		for (int i = 0; i < at.length; i++) {
			float r = 0.085f * g * (1f + 0.06f * Mth.sin(t * 0.9f + i));
			float x = at[i][0] * k * 1.6f, y = at[i][1];
			// the stalk, then the dark rim and the orange orb
			st.dot(dots, p, x * 0.75f, y * 0.75f, r * 0.55f, 0x2E5A12);
			st.dot(dots, p, x, y, r * 1.25f, 0x3A2408);
			st.dot(dots, p, x, y, r, 0xF2A21C);
			st.dot(dots, p, x - r * 0.3f * k, y + r * 0.3f, r * 0.35f, 0xFFE07A);
		}
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 4.5f);
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, 9f, 1f, ARM_AT - 2f, 10f, 1f);
		ShowShots.flash(st, t, 0xFFFFFF, WHITE1_AT, 1f, 93f, 8f, 1f);
		ShowShots.flash(st, t, 0xFFFFFF, WHITE2_AT, 0.8f, 149f, 7f, 1f);
		// the torso's cyan wash as the new body forms
		float cyan = env(t, TORSO_AT, 1f, 160f, 14f);
		if (cyan > 0f) {
			st.radial(0xEFFCFF, 0xA8E8FF, 0x5AC8F0, 0.4f * cyan, 0f);
		}
		// the glint off the badge
		float glint = env(t, 219f, 1f, 222f, 3f);
		if (glint > 0f) {
			st.radial(0xFFFFFF, 0xDDE6FF, 0x8890B0, 0.6f * glint, 0f);
		}
		ShowShots.flash(st, t, 0xFFFFFF, WHITE3_AT, 0.8f, 233f, 10f, 1f);
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, GREEN2_AT, 3f, 295f, 1f, 1f);
		ShowShots.flash(st, t, 0xF2EE1E, YELLOW_AT - 0.5f, 1f, 303f, 2f, 1f);
		ShowShots.greenOut(st, t, 302f, WORLD_AT, total());
	}
}
