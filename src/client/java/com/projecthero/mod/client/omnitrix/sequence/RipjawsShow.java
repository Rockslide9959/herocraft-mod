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
import net.minecraft.client.model.geom.ModelPart;
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
 * look exactly like how it looks in the show, try to be as expressive as you can without bending the limbs"): Ripjaws' transformation
 * from the show, shot for shot, cut to its own soundtrack. Compilation video second {@code v} is tick {@code (v - 186.30) * 20}. The
 * beats (ticks after the right-click):
 * <pre>
 *   0-5      the slap (front medium shot, the dark room)                 v 186.30-186.55
 *   4-25     FULL GREEN FLASH                                             v 186.5-187.55
 *   25-32    the dial close-up: grey fish skin spreading off the watch    v 187.55-187.9   (the shared show green, black Kirby dots)
 *   32-52    along the outstretched arm, the hand going webbed            v 187.9-188.9
 *   51-64    FULL GREEN FLASH, fading into...                             v 188.85-189.5
 *   60-72    ...the face, front, eyes screwed shut                        v 189.3-189.9 
 *   72-74    a white-out flash                                            v 189.9-190.0
 *   74-90    the face again; the lure stalk shoots out of the forehead    v 190.0-190.8
 *   90-104   the head in profile (facing left), the lure arching over     v 190.8-191.5
 *   104-118  right in on the neck: gills slicing open                     v 191.5-192.2
 *   118-130  from behind and below: the spine ridging up the back         v 192.2-192.8
 *   130-134  a white-out flash                                            v 192.8-193.0
 *   134-158  the head in profile (facing right): the cheeks swell         v 193.0-194.2
 *   158-174  the snout stretches, the teeth burst out                     v 194.2-195.0
 *   174-184  right in on the eye (pale green)                             v 195.0-195.5
 *   184-196  WHITE FLASH -- the body changes under it (187)               v 195.5-196.1
 *   196-206  the cream fading off Ripjaws' face                           v 196.1-196.6
 *   206-232  from low, pulling back up the body, the arms spreading       v 196.6-197.9
 *   232-238  WHITE FLASH                                                  v 197.9-198.2
 *   238-268  the claws-out stance on the green ray burst                  v 198.2-199.7
 *   268-284  FULL GREEN FLASH                                             v 199.7-200.5
 *   284-296  back in the world, the green fading off                      v 200.5-201.1
 * </pre>
 * The fish skin, the lure, the swelling cheeks, gills, spine and teeth are drawn on the human body (everyone nearby sees them, with
 * the poses and the dial's light); everything is flat-colour boxes, no particles.
 */
public final class RipjawsShow extends ShowSequenceClient {
	static final float SLAP_END = 5f, DIAL_AT = 25f, ARM_AT = 32f, GREEN2_AT = 51f, FACE_AT = 52f, NEG1_AT = 72f, LURE_AT = 74f, PROFILE_AT = 90f,
			NECK_AT = 104f, BACK_AT = 118f, NEG2_AT = 130f, CHEEK_AT = 130f, TEETH_AT = 158f, EYE_AT = 174f, WHITE_AT = 184f, REVEAL_AT = 200f,
			RISE_AT = 208f, WHITE2_AT = 232f, STANCE_AT = 238f, GREEN3_AT = 268f, WORLD_AT = 284f;
	static final int TRANSFORM_AT = 187;

	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");

	/** Colours: the green ray burst (the Kirby-dot shots all use the shared show green, Stage.showBackground). */
	private static final int BURST_IN = 0x9CF77A, BURST_MID = 0x3FD33A, BURST_OUT = 0x1D8A18, BURST_RAY = 0x15641A, BURST_DOT = 0x0D3F0B;
	/** Ripjaws' grey-teal fish skin, the pale belly / cheek cream, the lure, the gill lines, the teeth. */
	private static final int FISH = 0xFF9DB3AC, LURE = 0xFFE6DEC4, BULB = 0xFFF4F0DA, GILL = 0xFF3A3F36, TOOTH = 0xFFFFFFFF, CHEEK = 0xFFB4C4BC;

	private static final KirbyDots.Style DOTS = KirbyDots.Style.show(), KHAKI_DOTS = KirbyDots.Style.show().clear(0.3f),
			STANCE_DOTS = KirbyDots.Style.show().clear(0.45f), BURST_DOTS = KirbyDots.Style.show().bands(2).rgb(BURST_DOT).clear(0.5f).flow(1.6f);

	private static final float W = CameraTrack.W;
	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(ShowShots.slapShot(0f, SLAP_END), ShowShots.dialCloseUp(5.01f, DIAL_AT),
			new float[][] {
					// the dial, the skin spreading off it (from their left, looking down along the wrist)
					{ 25.01f, 0.5f, -40f, W, W, W, W },
					{ 32f, 0.44f, -50f, W, W, W, W },
					// along the outstretched arm, from under it, sliding toward the hand
					{ 32.01f, 0.7f, -94f, 1.02f, 1.26f, -0.42f, 0.3f },
					{ 52f, 0.58f, -80f, 1.04f, 1.26f, -0.5f, 0.56f },
					// the face, front, close
					{ 52.01f, 1.25f, 0f, 1.58f, 1.6f, 0f, 0f },
					{ 74f, 1.15f, 0f, 1.58f, 1.6f, 0f, 0f },
					{ 74.01f, 1.15f, 6f, 1.54f, 1.66f, -0.1f, 0f },
					{ 90f, 1.1f, 10f, 1.54f, 1.66f, -0.1f, 0f },
					// the head in profile, facing left
					{ 90.01f, 1.25f, -92f, 1.6f, 1.56f, 0f, 0.14f },
					{ 104f, 1.05f, -98f, 1.56f, 1.52f, 0f, 0.1f },
					// right in on the side of the neck
					{ 104.01f, 0.62f, -100f, 1.42f, 1.42f, -0.18f, 0f },
					{ 118f, 0.55f, -110f, 1.4f, 1.4f, -0.18f, 0f },
					// from behind and below, the back of the neck and the spine
					{ 118.01f, 1.05f, 168f, 1.12f, 1.5f, 0f, 0f },
					{ 130f, 0.95f, 160f, 1.1f, 1.5f, 0f, 0f },
					// the head in profile, facing right: the cheeks, the snout, the teeth
					{ 130.01f, 1.05f, 78f, 1.6f, 1.58f, 0f, 0.04f },
					{ 158f, 0.95f, 84f, 1.58f, 1.58f, 0f, 0.04f },
					{ 158.01f, 1.0f, 88f, 1.42f, 1.5f, 0f, 0.12f },
					{ 174f, 0.9f, 82f, 1.42f, 1.5f, 0f, 0.12f },
					// right in on the eye
					{ 174.01f, 0.45f, 28f, 1.68f, 1.67f, 0.1f, 0.2f },
					{ 200f, 0.38f, 22f, 1.68f, 1.67f, 0.1f, 0.2f },
					// Ripjaws' face, from a little below
					{ 200.01f, 0.78f, 0f, 1.42f, 1.62f, 0f, 0f },
					{ 208f, 0.86f, 4f, 1.4f, 1.6f, 0f, 0f },
					// from low, pulling back up the whole body as the arms spread
					{ 208.01f, 1.05f, 14f, 0.85f, 1.5f, 0f, 0f },
					{ 232f, 1.7f, 24f, 0.4f, 1.15f, 0f, 0f },
					// the stance: low, from his front left, claws out
					{ 232.01f, 1.45f, -30f, 0.3f, 1.1f, 0f, 0f },
					{ 284f, 1.35f, -26f, 0.32f, 1.12f, 0f, 0f } }))
			.shake(TEETH_AT, EYE_AT, 0.008f, 16f).shake(STANCE_AT, STANCE_AT + 14, 0.02f, 12f);

	public RipjawsShow() {
		super(Aliens.RIPJAWS.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public int burstAt() {
		return 272;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	@Override
	public double fov(float t) {
		double fov = 62;
		if (t >= ARM_AT && t < GREEN2_AT + 4) {
			fov -= 6 * smooth((t - ARM_AT) / 20f);
		} else if (t >= LURE_AT + 4 && t < PROFILE_AT) {
			// the lure shooting out: a punch in
			fov -= 6 * smooth((t - LURE_AT - 4) / 4f);
		} else if (t >= STANCE_AT && t < WORLD_AT) {
			fov -= 4 * smooth((t - STANCE_AT) / 30f);
		}
		return fov;
	}

	// ================================================================ the poses

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		m.head.yRot = 0f;
		if (t < TRANSFORM_AT) {
			float shake = smooth((t - DIAL_AT) / 20f);
			if (t >= ARM_AT && t < FACE_AT) {
				// the watch arm thrown out straight, forward and to the left, the fingers spread; the other hand off the dial
				float w = smooth((t - ARM_AT) / 4f);
				rot(m.leftArm, w, -1.45f + 0.04f * Mth.sin(t * 3.1f), -0.38f, -0.1f);
				m.leftArm.y = Mth.lerp(w, m.leftArm.y, 2f);
				rot(m.rightArm, w, 0.2f, 0f, 0.18f);
			} else if (t >= FACE_AT) {
				// arms down and rigid, fists clenched, the whole body shivering as it changes
				float w = smooth((t - FACE_AT) / 4f);
				rot(m.leftArm, w, 0.1f + 0.04f * Mth.sin(t * 2.9f), 0f, -0.3f);
				rot(m.rightArm, w, 0.1f + 0.04f * Mth.sin(t * 3.3f), 0f, 0.3f);
				float hx, hz = 0.03f * Mth.sin(t * 2.3f);
				if (t < PROFILE_AT) {
					// screwed up against it, the chin tucked; the lure jerks the head up as it shoots out
					hx = 0.18f - 0.22f * pop(t - LURE_AT - 4f, 5f);
				} else if (t < NECK_AT) {
					hx = 0.24f;
				} else if (t < NEG2_AT) {
					// the gills, the spine: the head thrown back
					hx = -0.38f;
				} else {
					// the cheeks, the snout: the head thrust forward, snarling
					hx = 0.12f + 0.05f * Mth.sin(t * 1.9f);
				}
				m.head.xRot = Mth.lerp(w, m.head.xRot, hx + 0.03f * Mth.sin(t * 2.7f));
				m.head.zRot = hz;
				m.body.xRot = Mth.lerp(w, m.body.xRot, t >= NECK_AT && t < NEG2_AT ? -0.1f : 0.04f);
			}
			if (shake > 0f) {
				m.leftArm.zRot += 0.03f * shake * Mth.cos(t * 3.7f);
				m.rightArm.zRot += 0.03f * shake * Mth.cos(t * 3.1f + 1f);
			}
		} else {
			float breathe = Mth.sin(t * 0.25f) * 0.04f;
			if (t < RISE_AT) {
				// the face: the head lifting, the jaw thrust out
				rot(m.head, 1f, -0.2f, 0f, 0f);
				rot(m.rightArm, 1f, 0.05f, 0f, 0.3f);
				rot(m.leftArm, 1f, 0.05f, 0f, -0.3f);
			} else if (t < WHITE2_AT) {
				// rising up: the arms spreading wide, the body twisting to his left
				float w = smooth((t - RISE_AT) / 20f);
				rot(m.rightArm, 1f, Mth.lerp(w, 0f, -0.35f), 0f, Mth.lerp(w, 0.3f, 1.25f) + breathe);
				rot(m.leftArm, 1f, Mth.lerp(w, 0f, -0.5f), 0f, Mth.lerp(w, -0.3f, -1.05f) - breathe);
				rot(m.head, 1f, Mth.lerp(w, -0.2f, -0.05f), 0.25f * w, 0f);
				m.body.yRot = 0.18f * w;
				rot(m.rightLeg, w, -0.05f, 0f, 0.22f);
				rot(m.leftLeg, w, 0.05f, 0f, -0.22f);
			} else {
				// the stance: legs wide, claws out to both sides, turned to his left, the head toward the camera
				float w = smooth((t - WHITE2_AT) / 4f);
				rot(m.rightArm, w, -0.3f + breathe, 0.15f, 1.15f);
				rot(m.leftArm, w, -0.45f - breathe, -0.15f, -1.05f);
				rot(m.rightLeg, w, -0.12f, 0f, 0.34f);
				rot(m.leftLeg, w, 0.12f, 0f, -0.34f);
				rot(m.head, w, -0.08f, -0.3f, 0f);
				m.body.yRot = Mth.lerp(w, m.body.yRot, 0.3f);
				m.body.xRot = Mth.lerp(w, m.body.xRot, 0.08f);
			}
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

	/** The snout lengthening and the cheeks puffing out (head scales), before the change. */
	@Override
	public void bulge(Player p, HumanoidModel<?> m, float t) {
		if (t >= CHEEK_AT && t < TRANSFORM_AT) {
			float throb = 1f + 0.04f * Mth.sin(t * 1.8f);
			float cheeks = 0.12f * smooth((t - CHEEK_AT - 4f) / 20f) * throb;
			float snout = 0.28f * smooth((t - TEETH_AT) / 10f);
			m.head.xScale = 1f + cheeks;
			m.head.zScale = 1f + snout;
			m.head.yScale = 1f + cheeks * 0.4f;
			m.hat.copyFrom(m.head);
		}
	}

	// ================================================================ the fish skin, the lure, the gills, the spine, the teeth

	/** Cheek and spine lumps: bone (0 head, 1 body), centre x y z (px), half-size, erupts at. */
	private static final float[][] LUMPS = {
			// the spine ridging up the back of the neck
			{ 1, 0f, 0.8f, 2.2f, 1.1f, 119 }, { 1, 0f, 3f, 2.2f, 1.0f, 121 }, { 1, 0f, 5.2f, 2.2f, 0.9f, 123 }, { 0, 0f, -1.6f, 4.3f, 1.1f, 120 },
			{ 0, 0f, -4f, 4.3f, 1.0f, 122 },
			// the cheeks puffing out
			{ 0, 4.0f, -3.4f, -1.0f, 1.8f, 136 }, { 0, -4.0f, -3.4f, -1.0f, 1.8f, 136 }, { 0, 3.8f, -2.6f, -2.8f, 1.4f, 141 },
			{ 0, -3.8f, -2.6f, -2.8f, 1.4f, 141 }, { 0, 3.4f, -3.0f, 1.0f, 1.4f, 146 }, { 0, -3.4f, -3.0f, 1.0f, 1.4f, 146 } };

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < DIAL_AT - 1f || t >= TRANSFORM_AT) {
			return;
		}
		int light = LightTexture.FULL_BRIGHT;
		VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		// the watch arm: the grey fish skin creeping up from the hand to the shoulder, the hand webbing out
		float arm = smooth((t - DIAL_AT) / 24f);
		if (arm > 0f) {
			float y0 = Mth.lerp(arm, 10.4f, -2.4f);
			AlienParts.on(pose, model.leftArm, () -> {
				AlienParts.box(pose, solid, -1.35f, y0, -2.35f, 3.35f, 12.35f, 2.35f, 0f, 0f, 1f, 1f, FISH, light);
				float web = smooth((t - 38f) / 10f);
				if (web > 0f) {
					// the webbed fingers: a flat fin past the hand, a few scale ridges
					AlienParts.box(pose, solid, -1.9f - 1.2f * web, 9.5f, -0.4f, 3.9f + 1.2f * web, 12.4f + 2.2f * web, 0.4f, 0f, 0f, 1f, 1f, FISH, light);
				}
				for (int i = 0; i < 4; i++) {
					float sy = 9f - i * 2.6f;
					if (sy > y0 + 0.5f) {
						AlienParts.box(pose, solid, 3.3f, sy, -1.2f + (i % 2) * 0.8f, 3.5f, sy + 0.25f, 0.2f + (i % 2) * 0.8f, 0f, 0f, 1f, 1f, GILL, light);
					}
				}
			});
		}
		// the rest of the body going grey from the neck down (after the arm shot)
		float rest = smooth((t - 96f) / 20f);
		if (rest > 0f) {
			float y1 = Mth.lerp(rest, 0f, 12.35f);
			AlienParts.on(pose, model.body, () -> AlienParts.box(pose, solid, -4.35f, -0.35f, -2.35f, 4.35f, y1, 2.35f, 0f, 0f, 1f, 1f, FISH, light));
			AlienParts.on(pose, model.rightArm, () -> AlienParts.box(pose, solid, -3.35f, -2.35f, -2.35f, 1.35f, Mth.lerp(rest, -2f, 12.35f), 2.35f, 0f, 0f,
					1f, 1f, FISH, light));
		}
		// the gills slicing open down the side of the neck
		float gills = smooth((t - NECK_AT - 2f) / 8f);
		if (gills > 0f) {
			AlienParts.on(pose, model.body, () -> {
				for (int i = 0; i < 3; i++) {
					float z = -1.2f + i * 1.1f, len = 4.2f * gills;
					AlienParts.box(pose, solid, 4.36f, 0.2f + i * 0.5f, z, 4.5f, 0.2f + i * 0.5f + len, z + 0.3f, 0f, 0f, 1f, 1f, GILL, light);
					AlienParts.box(pose, solid, -4.5f, 0.2f + i * 0.5f, z, -4.36f, 0.2f + i * 0.5f + len, z + 0.3f, 0f, 0f, 1f, 1f, GILL, light);
				}
			});
		}
		// the spine and the cheeks
		ModelPart[] bones = { model.head, model.body };
		for (float[] b : LUMPS) {
			float g = pop(t - b[5], 6f);
			if (g <= 0.02f) {
				continue;
			}
			float r = b[4] * g * (1f + 0.07f * Mth.sin(t * 1.3f + b[5]));
			int c = b[0] == 1 || b[3] > 4f ? FISH : CHEEK;
			AlienParts.on(pose, bones[(int) b[0]], () -> SwellLumps.lump(pose, solid, b[1], b[2], b[3], r, c, light));
		}
		// the lure: a stalk shooting out of the forehead, arching forward and over to his right, a bulb on the end
		float lure = smooth((t - LURE_AT - 4f) / 7f);
		if (lure > 0f) {
			AlienParts.on(pose, model.head, () -> {
				int n = 16;
				float sway = 0.6f * Mth.sin(t * 0.5f);
				// out to his right over the front shot; swung round to arch forward over the face from the profile shots on
				float fwd = t < PROFILE_AT ? 0f : 1f;
				float x1 = Mth.lerp(fwd, -6.5f, 0.5f), y1 = Mth.lerp(fwd, -9.5f, -10.5f), z1 = Mth.lerp(fwd, -5.5f, -9f);
				float x2 = Mth.lerp(fwd, -12.5f, 1f) + sway, y2 = Mth.lerp(fwd, -7.5f, -5.5f), z2 = Mth.lerp(fwd, -6.5f, -14f);
				for (int i = 0; i <= n; i++) {
					float s = lure * i / n;
					float u = 1f - s;
					float x = u * u * -0.5f + 2 * u * s * x1 + s * s * x2;
					float y = u * u * -6.2f + 2 * u * s * y1 + s * s * y2;
					float z = u * u * -4.2f + 2 * u * s * z1 + s * s * z2;
					float r = 0.8f - 0.25f * s;
					AlienParts.box(pose, solid, x - r, y - r, z - r, x + r, y + r, z + r, 0f, 0f, 1f, 1f, LURE, light);
					if (i == n) {
						float b = 1.6f * smooth((lure - 0.7f) / 0.3f);
						SwellLumps.lump(pose, solid, x, y + 0.6f, z, b, BULB, light);
					}
				}
			});
		}
		// the teeth bursting out of the stretching mouth, along the front and both sides
		float teeth = smooth((t - TEETH_AT - 2f) / 8f);
		if (teeth > 0f) {
			AlienParts.on(pose, model.head, () -> {
				// big fangs hanging off the jaw line, a smaller row pushing up from below
				for (int i = 0; i < 6; i++) {
					float x = -3.3f + i * 1.32f, h = (2.8f + 0.8f * (i % 2)) * teeth;
					AlienParts.box(pose, solid, x - 0.6f, -0.8f, -5.2f, x + 0.6f, -0.8f + h, -4.6f, 0f, 0f, 1f, 1f, TOOTH, light);
				}
				// the dark slit of the mouth they burst out of
				AlienParts.box(pose, solid, -4.95f, -1.5f, -5.25f, 4.95f, -0.7f, 1.2f, 0f, 0f, 1f, 1f, GILL, light);
				for (int side = -1; side <= 1; side += 2) {
					for (int i = 0; i < 4; i++) {
						float z = -3.6f + i * 1.3f, h = (3.0f - 0.35f * i) * teeth, x = side * 5.0f;
						AlienParts.box(pose, solid, x - 0.35f, -0.8f, z - 0.6f, x + 0.35f, -0.8f + h, z + 0.6f, 0f, 0f, 1f, 1f, TOOTH, light);
						AlienParts.box(pose, solid, x - 0.3f, 1.2f - h * 0.6f, z + 0.1f, x + 0.3f, 1.2f, z + 0.7f, 0f, 0f, 1f, 1f, TOOTH, light);
					}
				}
			});
		}
		// the face greying over (translucent, so the screwed-up face still shows)
		float face = smooth((t - LURE_AT) / 40f);
		if (face > 0f) {
			VertexConsumer wash = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
			int a = Math.round(190 * face);
			AlienParts.on(pose, model.head, () -> AlienParts.box(pose, wash, -4.6f, -8.6f, -4.6f, 4.6f, 0.6f, 4.6f, 0f, 0f, 1f, 1f, (a << 24) | 0xA8B4A8,
					light));
		}
	}

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 3f, 24f);
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		int shot = TRACK.shot(t);
		float inShot = t - TRACK.shotStart(t);
		if (t < SLAP_END) {
			st.gradient(0x4E5A64, 0x2C343C, 0x0C1014);
		} else if (t < DIAL_AT) {
			st.gradient(0xF2FFE0, 0x7CEB4C, 0x2DB51C);
		} else if (t < FACE_AT) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(shot), inShot, DOTS);
		} else if (t < EYE_AT) {
			st.showBackground();
			if (t >= NECK_AT && t < BACK_AT) {
				// right in on the neck: the skin fills the frame, a few dots at the edge
				KirbyDots.draw(st, st.shotSeed(shot), inShot, KirbyDots.Style.show().bands(0).rim(0.6f));
			} else {
				KirbyDots.draw(st, st.shotSeed(shot), inShot, KHAKI_DOTS);
			}
		} else if (t < REVEAL_AT) {
			// the eye's pale mint, then the cream of the flash
			st.gradient(0xDDF0DA, 0xC2DEC0, 0x9CC09A);
		} else if (t < STANCE_AT) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(shot), inShot, t >= RISE_AT ? STANCE_DOTS : DOTS);
		} else {
			st.gradient(BURST_IN, BURST_MID, BURST_OUT);
			st.rays(st.seed, t - STANCE_AT, BURST_RAY, 16);
			KirbyDots.draw(st, st.shotSeed(shot) + 5, t - STANCE_AT, BURST_DOTS);
		}
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 3.5f);
		// the green holds until the dial shot, then thins off it
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, 9f, 1f, DIAL_AT - 1f, 5f, 1f);
		// the second full green flash, fading over the face
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, GREEN2_AT, 2f, 61f, 10f, 1f);
		// the eye's close-up washed pale mint
		ShowShots.flash(st, t, 0xCFEBD0, EYE_AT, 1f, WHITE_AT, 1f, 0.4f);
		// the two white-outs
		ShowShots.flash(st, t, 0xF6F6EE, NEG1_AT, 0.5f, NEG1_AT + 1.5f, 1.5f, 0.85f);
		ShowShots.flash(st, t, 0xF6F6EE, NEG2_AT, 0.5f, NEG2_AT + 2.5f, 1.5f, 0.85f);
		// the white flash the body changes under, going cream and fading off Ripjaws' face
		ShowShots.flash(st, t, 0xFFFFFC, WHITE_AT, 1.5f, 194f, 2f, 1f);
		float cream = env(t, 194f, 0.5f, 199f, 9f);
		if (cream > 0f) {
			st.fill(ShowMath.lerpRgb(smooth((t - 194f) / 6f), 0xFFF6E0, 0xF4D7A0), cream * 0.92f);
		}
		ShowShots.flash(st, t, 0xF8FFF4, WHITE2_AT, 1f, 235f, 5f, 0.95f);
		ShowShots.greenOut(st, t, GREEN3_AT, WORLD_AT, total());
	}
}
