package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.env;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.hash;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.pop;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show"): Ripjaws' transformation from the show, shot for shot, cut to its own soundtrack.
 * Compilation video second {@code v} is tick {@code (v - 186.30) * 20}. The beats (ticks after the right-click):
 * <pre>
 *   0-5      the slap (front medium shot, the dark room)                 v 186.30-186.55
 *   4-25     FULL GREEN FLASH                                             v 186.5-187.55
 *   25-32    the dial close-up: grey fish skin spreading off the watch    v 187.55-187.9   (the shared show green, black Kirby dots)
 *   32-52    along the outstretched arm, the fingers splaying, webbing    v 187.9-188.9
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
 *   174-184  right in on the eye, screwed shut, the skin gone pale mint   v 195.0-195.5
 *   184-196  WHITE FLASH -- the body changes under it (187)               v 195.5-196.1
 *   196-206  the cream fading off Ripjaws' face                           v 196.1-196.6
 *   206-232  from low, pulling back up the body, the arms spreading       v 196.6-197.9
 *   232-238  WHITE FLASH                                                  v 197.9-198.2
 *   238-268  the claws-out stance on the green ray burst                  v 198.2-199.7
 *   268-284  FULL GREEN FLASH                                             v 199.7-200.5
 *   284-296  back in the world, the green fading off                      v 200.5-201.1
 * </pre>
 * The fish skin, the lure, the swelling cheeks, gills, spine and teeth are drawn on the human body (everyone nearby sees them, with
 * the poses and the dial's light).
 *
 * <p><b>v1b touch-up</b> (user: "make sure their limbs don't go weird or detach from the body and touch up the effects and poses"):
 * one keyframed pose track ({@link LimbKeys}) with bent elbows and knees, so the arms bend into claws (v1 held them out in a straight
 * T) and the torso leans about its hips; the grey skin is drawn over the limbs with their mitred joints ({@link Shards#sleeve}), so it
 * bends with them, with a jagged leading edge and the show's little scale chevrons; the hand splays into webbed fingers instead of a
 * flat plate; the gills are slit lines on the side of the neck (v1's stood off the shoulders like bars); the teeth are triangles; the
 * lure is a smooth tapering stalk; the eye close-up is drawn (a screwed-shut eye on pale mint skin).
 */
public final class RipjawsShow extends ShowSequenceClient {
	static final float SLAP_END = 5f, DIAL_AT = 25f, ARM_AT = 32f, GREEN2_AT = 51f, FACE_AT = 52f, NEG1_AT = 72f, LURE_AT = 74f, PROFILE_AT = 90f,
			NECK_AT = 104f, BACK_AT = 118f, NEG2_AT = 130f, CHEEK_AT = 130f, TEETH_AT = 158f, EYE_AT = 174f, WHITE_AT = 184f, REVEAL_AT = 200f,
			RISE_AT = 208f, WHITE2_AT = 232f, STANCE_AT = 238f, GREEN3_AT = 268f, WORLD_AT = 284f;
	static final int TRANSFORM_AT = 187;

	/** Colours: the green ray burst (the Kirby-dot shots all use the shared show green, Stage.showBackground). */
	private static final int BURST_IN = 0x9CF77A, BURST_MID = 0x3FD33A, BURST_OUT = 0x1D8A18, BURST_RAY = 0x15641A, BURST_DOT = 0x0D3F0B;
	/** Ripjaws' grey-teal fish skin (and its light / dark), the pale webbing and cheek cream, the lure, the gill lines, the teeth. */
	private static final int FISH = 0xFF9DB3AC, FISH_LIGHT = 0xBFD2CB, FISH_DARK = 0x768C85, WEB = 0xFFC9DAD3, LURE = 0xFFE6DEC4, BULB = 0xFFF4F0DA,
			GILL = 0xFF3A3F36, TOOTH = 0xFFFFFFFF, CHEEK = 0xFFD8D4C0;

	private static final KirbyDots.Style DOTS = KirbyDots.Style.show(), KHAKI_DOTS = KirbyDots.Style.show().clear(0.3f),
			STANCE_DOTS = KirbyDots.Style.show().clear(0.45f), BURST_DOTS = KirbyDots.Style.show().bands(2).rgb(BURST_DOT).clear(0.5f).flow(1.6f);

	private static final float W = CameraTrack.W;
	// v0.17.4 (user: "make the camera angles and poses match up better with what shows in the transformation scenes"): re-framed
	// against the show -- the slap waist up; the arm from above and right in, running across the frame; the face shots, the profiles
	// and the back of the neck all much closer (the show's heads fill the frame); the lure shot three-quarters from his right; the
	// rise and the stance pulled round to the front, the stance far enough back for the whole spread body
	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(Xlr8Show.slapClose(SLAP_END), ShowShots.dialCloseUp(5.01f, DIAL_AT),
			new float[][] {
					// the dial, the skin spreading off it (from their left, looking down along the wrist)
					{ 25.01f, 0.5f, -40f, W, W, W, W },
					{ 32f, 0.44f, -50f, W, W, W, W },
					// along the outstretched arm, from above it and close, sliding toward the hand
					{ 32.01f, 0.55f, -132f, 1.85f, 1.36f, -0.36f, 0.58f },
					{ 52f, 0.5f, -120f, 1.8f, 1.34f, -0.4f, 0.72f },
					// the face, front, right in
					{ 52.01f, 0.86f, 0f, 1.56f, 1.6f, 0f, 0f },
					{ 74f, 0.8f, 0f, 1.56f, 1.6f, 0f, 0f },
					// the lure shooting out: three-quarters from his right (he faces the right of frame), the stalk arching up over
					{ 74.01f, 0.85f, 28f, 1.54f, 1.62f, -0.05f, 0f },
					{ 90f, 0.8f, 32f, 1.54f, 1.62f, -0.05f, 0f },
					// the head in profile, facing left, close
					{ 90.01f, 0.85f, -92f, 1.52f, 1.56f, 0f, 0.12f },
					{ 104f, 0.72f, -98f, 1.5f, 1.52f, 0f, 0.1f },
					// right in on the side of the neck, under the ear, where the gills open
					{ 104.01f, 0.62f, -98f, 1.5f, 1.53f, 0f, 0.02f },
					{ 118f, 0.55f, -106f, 1.5f, 1.53f, 0f, 0.04f },
					// from behind and below, the back of the neck and the spine, close
					{ 118.01f, 0.8f, 162f, 1.2f, 1.55f, 0f, 0f },
					{ 130f, 0.72f, 156f, 1.2f, 1.55f, 0f, 0f },
					// the head in profile, facing right, filling the frame: the cheeks, the snout, the teeth
					{ 130.01f, 0.76f, 60f, 1.58f, 1.58f, 0f, 0.06f },
					{ 158f, 0.7f, 62f, 1.58f, 1.58f, 0f, 0.06f },
					{ 158.01f, 0.74f, 50f, 1.52f, 1.55f, 0f, 0.14f },
					{ 174f, 0.68f, 48f, 1.52f, 1.55f, 0f, 0.14f },
					// right in on the eye
					{ 174.01f, 0.45f, 28f, 1.68f, 1.67f, 0.1f, 0.2f },
					{ 200f, 0.38f, 22f, 1.68f, 1.67f, 0.1f, 0.2f },
					// Ripjaws' face, from a little below
					{ 200.01f, 0.78f, 0f, 1.42f, 1.62f, 0f, 0f },
					{ 208f, 0.86f, 4f, 1.4f, 1.6f, 0f, 0f },
					// from low in front, pulling back up the whole body as the arms spread
					{ 208.01f, 1.0f, -14f, 0.6f, 1.45f, 0f, 0f },
					{ 232f, 1.75f, -6f, 0.35f, 1.15f, 0f, 0f },
					// the stance: low and nearly front on, far enough back for the whole body, claws out wide
					{ 232.01f, 2.15f, 10f, 0.35f, 0.95f, 0f, 0f },
					{ 284f, 2.05f, 12f, 0.35f, 0.95f, 0f, 0f } }))
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

	/** The whole body, matched to the video's frames (see the class beat table). */
	static final LimbKeys POSE = new LimbKeys()
			// ---- the watch arm thrown out straight, forward and to his left, fingers spread; the other arm braced at his side
			.at(ARM_AT).lArm(-1.45f, -0.04f, -0.1f, 0.08f, 0f).rArm(0.15f, 0f, 0.22f, 0.45f, 0.1f).head(0.12f, -0.3f, 0f).lean(0.04f)
			.legs(0f, 0f, 0.05f, 0.05f, 0f)
			.at(GREEN2_AT).lArm(-1.5f, -0.06f, -0.12f, 0.12f, 0f)
			.at(FACE_AT - 0.5f).hold()
			// ---- the face: fists clenched, elbows bent and pulled in, shoulders hunched up round the neck, chin tucked
			.at(FACE_AT).arms(0.12f, 0f, 0.26f, 0.55f, 0.15f).head(0.18f, 0f, 0f).shrug(-0.7f).lean(0.04f)
			.at(PROFILE_AT - 0.5f).hold()
			.at(PROFILE_AT).head(0.24f, 0f, 0f).shrug(-0.9f)
			.at(NECK_AT - 0.5f).hold()
			// ---- the gills and the spine: the head thrown back, the chest out, straining
			.at(NECK_AT).head(-0.38f, 0f, 0f).lean(-0.1f).shrug(-1.1f).arms(0.18f, 0f, 0.36f, 0.75f, 0.25f)
			.at(NEG2_AT - 0.5f).hold()
			// ---- the cheeks, the snout: the head thrust forward, snarling
			.at(NEG2_AT).head(0.12f, 0f, 0f).lean(0.1f).shrug(-0.5f).arms(0.1f, 0f, 0.3f, 0.65f, 0.2f)
			.at(TRANSFORM_AT - 0.5f).hold()
			// ---- Ripjaws: the head lifting, the jaw thrust out
			.at(TRANSFORM_AT).head(-0.2f, 0f, 0f).arms(0.05f, 0f, 0.3f, 0.45f, 0.15f).lean(0f).shrug(0f)
			.at(RISE_AT).hold()
			// rising up: the arms spreading, elbows bent, claws open; the body twisting to his left
			.at(WHITE2_AT).head(-0.05f, 0.25f, 0f).twist(0.18f, 0f).rArm(-0.4f, 0.15f, 1.15f, 0.95f, 0.9f).lArm(-0.55f, -0.1f, -1.0f, 1.05f, -0.9f)
			.legs(-0.05f, 0f, 0.2f, 0.12f, 0.15f)
			.at(WHITE2_AT + 3f).hold()
			// ---- the stance: legs wide, his left leg forward and bent, claws out to both sides, the head toward the camera
			// (v0.17.4: matched to the show's spread -- both arms out wide to the sides, the forearms curled up into the claws, the body
			// square to the lens and leaning back a little, legs wider; it was twisted with one arm crossing the frame)
			.at(STANCE_AT - 2f).head(-0.18f, -0.1f, 0f).twist(0.08f, 0.1f).lean(-0.06f).drop(1.3f)
			.rArm(-0.4f, 0f, 1.85f, 1.2f, 1.4f).lArm(-0.3f, 0f, -0.95f, 0.9f, -1.2f)
			.rLeg(0.1f, 0f, 0.42f, 0.2f, 0.2f).lLeg(-0.1f, 0f, -0.45f, 0.2f, -0.2f)
			.at(GREEN3_AT).hold()
			.at(WORLD_AT).head(0f, 0f, 0f).twist(0f, 0f).lean(0f).drop(0f).arms(0f, 0f, 0.1f, 0.15f, 0f).legs(0f, 0f, 0.03f, 0f, 0f);

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		if (t < ARM_AT || t >= WORLD_AT) {
			Xlr8Show.slapArm(m, t, SLAP_END);
			return;
		}
		LimbKeys.apply(m, POSE.sample(t));
		if (t < TRANSFORM_AT) {
			// shivering as it changes; the lure jerks the head up as it shoots out
			float shake = smooth((t - DIAL_AT) / 20f);
			m.leftArm.zRot += 0.03f * shake * Mth.cos(t * 3.7f);
			m.rightArm.zRot += 0.03f * shake * Mth.cos(t * 3.1f + 1f);
			if (t < FACE_AT) {
				m.leftArm.xRot += 0.04f * Mth.sin(t * 3.1f);
			} else {
				m.head.xRot += 0.03f * Mth.sin(t * 2.7f) - 0.22f * pop(t - LURE_AT - 4f, 5f) + (t >= NEG2_AT ? 0.05f * Mth.sin(t * 1.9f) : 0f);
				m.head.zRot += 0.03f * Mth.sin(t * 2.3f);
			}
		} else {
			float breathe = Mth.sin(t * 0.25f) * 0.04f;
			m.rightArm.zRot += breathe;
			m.leftArm.zRot -= breathe;
		}
		LimbKeys.copyLayers(m);
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
			{ 0, 3.9f, -2.6f, -1.6f, 2.1f, 136 }, { 0, -3.9f, -2.6f, -1.6f, 2.1f, 136 }, { 0, 3.7f, -2.2f, -3.0f, 1.6f, 141 },
			{ 0, -3.7f, -2.2f, -3.0f, 1.6f, 141 }, { 0, 3.5f, -2.9f, 0.6f, 1.5f, 146 }, { 0, -3.5f, -2.9f, 0.6f, 1.5f, 146 } };

	/** Scale chevrons on the watch arm (bone px): {face, y, across 0..1}. */
	private static final float[][] CHEVRONS = chevrons();

	private static float[][] chevrons() {
		java.util.List<float[]> out = new java.util.ArrayList<>();
		long seed = 0x5CA1E5L;
		int[] faces = { Shards.PX, Shards.NZ, Shards.NX, Shards.PZ, Shards.TOP };
		for (int i = 0; i < 22; i++) {
			out.add(new float[] { faces[i % 4], -1f + 10.6f * hash(seed, i * 2), 0.2f + 0.6f * hash(seed, i * 2 + 1) });
		}
		return out.toArray(new float[0][]);
	}

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < DIAL_AT - 1f || t >= TRANSFORM_AT) {
			return;
		}
		int light = LightTexture.FULL_BRIGHT;
		boolean slim = p.getSkin().model() == PlayerSkin.Model.SLIM;
		VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(Shards.WHITE));
		float xMax = slim ? 2f : 3f;
		// the watch arm: the grey fish skin creeping up from the hand to the shoulder, bent with the arm
		float arm = smooth((t - DIAL_AT) / 24f);
		if (arm > 0f) {
			float y0 = Mth.lerp(arm, 8.4f, -2.3f);
			Shards.sleeve(pose, solid, model.leftArm, BentLimbs.LEFT_ARM, slim, y0 + 2f, 12.1f, 0.3f, FISH, light);
			// its jagged leading edge
			if (arm < 0.999f) {
				int[] faces = { Shards.PX, Shards.NX, Shards.PZ, Shards.NZ };
				for (int i = 0; i < 8; i++) {
					int face = faces[i % 4];
					float across = i < 4 ? 0.3f : 0.72f;
					float[] xz = onFace(face, across, xMax, 0.3f);
					float ang = 180f + (i % 2 == 0 ? 14f : -14f);
					Shards.onLimb(pose, model.leftArm, BentLimbs.LEFT_ARM, slim, y0, () -> Shards.draw(pose, solid, face, xz[0], y0 + 0.4f, xz[1], 2.2f, 0.9f,
							0.12f, ang, 0.9f, 1f, light, FISH_LIGHT, FISH & 0xFFFFFF, FISH_DARK));
				}
			}
			// the little scale chevrons the show draws on the skin
			for (float[] c : CHEVRONS) {
				if (c[1] < y0 + 1.2f || (int) c[0] == Shards.TOP) {
					continue;
				}
				int face = (int) c[0];
				float[] xz = onFace(face, c[2], xMax, 0.33f);
				Shards.onLimb(pose, model.leftArm, BentLimbs.LEFT_ARM, slim, c[1], () -> {
					Shards.draw(pose, solid, face, xz[0], c[1], xz[1], 1.1f, 0.12f, 0.03f, 35f, 0.5f, 1f, light, FISH_DARK, FISH_DARK, FISH_DARK);
					Shards.draw(pose, solid, face, xz[0], c[1], xz[1], 1.1f, 0.12f, 0.03f, -35f, 0.5f, 1f, light, FISH_DARK, FISH_DARK, FISH_DARK);
				});
			}
			// the fingers splaying out of the hand, webbed between
			float web = smooth((t - 38f) / 10f);
			if (web > 0f) {
				Shards.onLimb(pose, model.leftArm, BentLimbs.LEFT_ARM, slim, 10f, () -> hand(pose, solid, web, xMax, light));
			}
		}
		// the rest of the body going grey from the neck down (after the arm shot), bent with the right arm
		float rest = smooth((t - 96f) / 20f);
		if (rest > 0f) {
			float y1 = Mth.lerp(rest, 0f, 12.35f);
			AlienParts.on(pose, model.body, () -> AlienParts.box(pose, solid, -4.35f, -0.35f, -2.35f, 4.35f, y1, 2.35f, 0f, 0f, 1f, 1f, FISH, light));
			Shards.sleeve(pose, solid, model.rightArm, BentLimbs.RIGHT_ARM, slim, 0f, Mth.lerp(rest, 0f, 12.1f), 0.3f, FISH, light);
		}
		// the gills slicing open down the side of the neck, under the ear
		float gills = smooth((t - NECK_AT - 2f) / 8f);
		if (gills > 0f) {
			AlienParts.on(pose, model.head, () -> {
				PoseStack.Pose last = pose.last();
				for (int side = -1; side <= 1; side += 2) {
					float x = side * 4.62f;
					for (int i = 0; i < 3; i++) {
						float z0 = -1.4f + i * 1.45f, yTop = -5.2f + i * 0.4f, len = 4.6f * gills;
						// a slanted slit, sweeping back as it runs down
						float[] a = { x, yTop, z0 }, b = { x, yTop, z0 + 0.6f }, c = { x, yTop + len, z0 + 0.6f + len * 0.42f }, d = { x, yTop + len, z0 + len * 0.42f };
						Shards.quad(last, solid, a, b, c, d, GILL, light);
					}
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
		// the lure: a smooth tapering stalk shooting out of the forehead, arching over, a bulb on the end
		float lure = smooth((t - LURE_AT - 4f) / 7f);
		if (lure > 0f) {
			AlienParts.on(pose, model.head, () -> {
				int n = 30;
				float sway = 0.6f * Mth.sin(t * 0.5f);
				// out to his right over the front shot; swung round to arch forward over the face from the profile shots on
				float fwd = t < PROFILE_AT + 0.01f ? 0f : 1f;
				float x1 = Mth.lerp(fwd, -6.5f, 0.5f), y1 = Mth.lerp(fwd, -11.5f, -12.5f), z1 = Mth.lerp(fwd, -5.5f, -8f);
				float x2 = Mth.lerp(fwd, -12.5f, 1f) + sway, y2 = Mth.lerp(fwd, -8.5f, -5.5f), z2 = Mth.lerp(fwd, -6.5f, -13f);
				for (int i = 0; i <= n; i++) {
					float s = lure * i / n;
					float u = 1f - s;
					float x = u * u * -0.5f + 2 * u * s * x1 + s * s * x2;
					float y = u * u * -7.8f + 2 * u * s * y1 + s * s * y2;
					float z = u * u * -4.3f + 2 * u * s * z1 + s * s * z2;
					float r = 0.62f - 0.3f * s;
					AlienParts.box(pose, solid, x - r, y - r, z - r, x + r, y + r, z + r, 0f, 0f, 1f, 1f, LURE, light);
					if (i == n) {
						float b = 1.5f * smooth((lure - 0.7f) / 0.3f);
						SwellLumps.lump(pose, solid, x, y + 0.7f, z, b, BULB, light);
					}
				}
			});
		}
		// the teeth bursting out of the stretching mouth: big triangles along the front and both sides
		float teeth = smooth((t - TEETH_AT - 2f) / 8f);
		if (teeth > 0f) {
			AlienParts.on(pose, model.head, () -> {
				PoseStack.Pose last = pose.last();
				// the dark slit of the mouth they burst out of
				AlienParts.box(pose, solid, -4.62f, -1.6f, -4.62f, 4.62f, -0.9f, 1.4f, 0f, 0f, 1f, 1f, GILL, light);
				for (int i = 0; i < 5; i++) {
					float x = -3.6f + i * 1.8f, h = (3.4f + 1.0f * (i % 2)) * teeth;
					Shards.tri(last, solid, new float[] { x - 0.9f, -0.9f, -4.7f }, new float[] { x + 0.9f, -0.9f, -4.7f }, new float[] { x, -0.9f + h, -4.75f }, TOOTH,
							light);
				}
				for (int side = -1; side <= 1; side += 2) {
					float x = side * 4.7f;
					for (int i = 0; i < 4; i++) {
						float z = -3.4f + i * 1.45f, h = (3.8f - 0.45f * i) * teeth;
						// the upper row hanging down, the lower row pushing up between them
						Shards.tri(last, solid, new float[] { x, -0.9f, z - 0.7f }, new float[] { x, -0.9f, z + 0.7f }, new float[] { x, -0.9f + h, z }, TOOTH, light);
						Shards.tri(last, solid, new float[] { x, -1.6f, z }, new float[] { x, -1.6f, z + 1.3f }, new float[] { x, -1.6f - h * 0.55f, z + 0.65f }, TOOTH,
								light);
					}
				}
			});
		}
		// the face greying over (translucent, so the screwed-up face still shows)
		float face = smooth((t - LURE_AT) / 40f);
		if (face > 0f) {
			VertexConsumer wash = buffers.getBuffer(RenderType.entityTranslucent(Shards.WHITE));
			int a = Math.round(150 * face);
			AlienParts.on(pose, model.head, () -> AlienParts.box(pose, wash, -4.6f, -8.6f, -4.6f, 4.6f, 0.6f, 4.6f, 0f, 0f, 1f, 1f, (a << 24) | 0xC9CCBC,
					light));
		}
	}

	/** {x, z} of a point {@code across} (0..1) a left-arm face, {@code proud} px off the skin. */
	private static float[] onFace(int face, float across, float xMax, float proud) {
		return switch (face) {
			case Shards.PX -> new float[] { xMax + proud, -2f + 4f * across };
			case Shards.NX -> new float[] { -1f - proud, -2f + 4f * across };
			case Shards.PZ -> new float[] { -1f + (xMax + 1f) * across, 2f + proud };
			default -> new float[] { -1f + (xMax + 1f) * across, -2f - proud };
		};
	}

	/** The hand (left arm bone px, its end at y 10): four fingers fanning out of it, the pale webbing stretched between them. */
	private static void hand(PoseStack pose, VertexConsumer vc, float web, float xMax, int light) {
		float cx = (xMax - 1f) * 0.5f, half = (xMax + 1f) * 0.5f;
		float len = 2.4f * web;
		float[][] tips = new float[4][];
		for (int i = 0; i < 4; i++) {
			float f = (i - 1.5f) / 1.5f;
			float ang = f * 0.55f * web;
			float bx = cx + f * half * 0.8f;
			pose.pushPose();
			pose.translate(bx / 16f, 9.9f / 16f, 0f);
			pose.mulPose(Axis.ZP.rotation(-ang));
			AlienParts.box(pose, vc, -0.42f, 0f, -0.45f, 0.42f, len, 0.45f, 0f, 0f, 1f, 1f, FISH, light);
			// the claw tip
			AlienParts.box(pose, vc, -0.3f, len, -0.3f, 0.3f, len + 0.7f * web, 0.3f, 0f, 0f, 1f, 1f, 0xFF2E3530, light);
			pose.popPose();
			tips[i] = new float[] { bx + Mth.sin(ang) * len * 0.85f, 9.9f + Mth.cos(ang) * len * 0.85f };
		}
		PoseStack.Pose last = pose.last();
		for (int i = 0; i < 3; i++) {
			float ax = cx + ((i - 1.5f) / 1.5f) * half * 0.8f, bx = cx + ((i + 1 - 1.5f) / 1.5f) * half * 0.8f;
			float[] a = { ax, 9.9f, 0f }, b = { bx, 9.9f, 0f }, c = { tips[i + 1][0], tips[i + 1][1], 0f }, d = { tips[i][0], tips[i][1], 0f };
			Shards.quad(last, vc, a, b, c, d, WEB, light);
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
		if (t >= EYE_AT && t < WHITE_AT + 2f) {
			eye(st, t);
		}
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

	/**
	 * v1b: right in on the eye (v1 was a blur of the head under a mint wash): pale mint skin, the eye screwed shut under a heavy dark
	 * brow, squint creases fanning off it, the cream fin points of the new head at the left edge, pushing in.
	 */
	private static void eye(Stage st, float t) {
		float u = t - EYE_AT;
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float k = st.k();
		float s = 1f + 0.015f * u;
		float cx = 0.08f * k - 0.01f * u * k, cy = 0.02f;
		st.gradient(0xD6EEDA, 0xBFE2C6, 0xA2CDAA);
		// the fin points of the new head along the left edge
		for (int i = 0; i < 4; i++) {
			float y0 = -1.1f + i * 0.55f;
			st.put(vc, p, -1.5f, y0, 0xEDE6C8, 1f);
			st.put(vc, p, -1.5f, y0 + 0.5f, 0xEDE6C8, 1f);
			st.put(vc, p, -0.95f * k + 0.05f * i, y0 + 0.62f, 0xF6F0D6, 1f);
			st.put(vc, p, -1.5f, y0 + 0.5f, 0xEDE6C8, 1f);
			Shards.line(st, vc, p, -1.5f, y0 + 0.5f, -0.95f * k + 0.05f * i, y0 + 0.62f, 0.006f, 0x8A9A8A, 0.9f);
		}
		// the brow: a thick dark stroke slanting down toward the nose
		Shards.line(st, vc, p, cx - 0.62f * k * s, cy + 0.42f * s, cx + 0.38f * k * s, cy + 0.12f * s, 0.055f * s, 0x16201A, 1f);
		Shards.line(st, vc, p, cx - 0.42f * k * s, cy + 0.5f * s, cx + 0.1f * k * s, cy + 0.34f * s, 0.02f * s, 0x16201A, 1f);
		// the shut lid: a curved stroke, heavy in the middle
		float[] lx = { -0.5f, -0.25f, 0.02f, 0.28f, 0.48f }, ly = { 0.02f, -0.06f, -0.08f, -0.04f, 0.06f };
		for (int i = 0; i < 4; i++) {
			float w = (i == 1 || i == 2 ? 0.032f : 0.022f) * s;
			Shards.line(st, vc, p, cx + lx[i] * k * s, cy + ly[i] * s, cx + lx[i + 1] * k * s, cy + ly[i + 1] * s, w, 0x16201A, 1f);
		}
		// squint creases fanning out of the corners
		for (int i = 0; i < 3; i++) {
			float a = 0.25f + i * 0.28f;
			Shards.line(st, vc, p, cx + 0.56f * k * s, cy + 0.04f * s, cx + (0.56f + 0.3f * Mth.cos(a)) * k * s, cy + (0.04f - 0.3f * Mth.sin(a)) * s,
					0.008f * s, 0x3C4A40, 0.9f);
			Shards.line(st, vc, p, cx - 0.56f * k * s, cy - 0.02f * s, cx - (0.56f + 0.26f * Mth.cos(a)) * k * s, cy - (0.02f + 0.26f * Mth.sin(a)) * s,
					0.008f * s, 0x3C4A40, 0.9f);
		}
		// the white flash already rising
		ShowShots.flash(st, t, 0xFFFFFC, WHITE_AT - 3f, 3f, WHITE_AT + 3f, 1f, 0.6f);
	}
}
