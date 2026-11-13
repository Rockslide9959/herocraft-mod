package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.env;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.hash;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.pop;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show"): Diamondhead's show transformation, shot for shot. Compilation video second {@code v}
 * is tick {@code (v - 61.95) * 20}; the beats:
 * <pre>
 *   0-16    WHITE-GREEN FLASH (the slap is already under it)
 *   16-36   Ben from low in front, the watch forearm across his chest, the other hand on it: crystal sprouting from the dial
 *   36-50   the arm flung out to his left: the crystal sheathing it down to the hand (olive field, heavy black dots)
 *   50-68   the hand close up, crystal swallowing the fingers
 *   68-78   the fist whips out of frame, only the dots
 *   78-112  Ben's face, pushing in: crystal facets spreading over his left cheek and hair
 *   112-134 the eye close-up: Ben's eye, the yellow flooding up, Diamondhead's hexagonal eye        (the body changes at 126)
 *   132-138 smeared out
 *   138-150 Diamondhead's head, three-quarter profile
 *   150-190 behind his shoulder, low: crystal spikes thrusting up off his shoulders, the shoulders hunching
 *   190-196 a dark green whip pan
 *   196-208 the forearms crossed in front of the chest, the Omnitrix under them
 *   208-240 pulling back: the forearms swing open into a double-arm flex
 *   240-246 WHITE FLASH, fading off the final pose
 *   246-278 the stance on the green burst: legs wide, front knee bent, fists out low, one driven at the camera
 *   277-287 FULL GREEN FLASH, then a hard cut back to the world
 * </pre>
 * <b>v1b touch-up</b> (user: "make sure their limbs don't go weird or detach from the body and touch up the effects and poses"):
 * one keyframed pose track ({@link LimbKeys}) with bent elbows and knees ({@link BentLimbs}) in place of the rigid per-beat poses
 * (which snapped back to the vanilla pose between beats, leaned the torso off its legs and held the arms in a straight T); the crystal
 * is a sheath that grows over the arm (bent with it) edged with cel-shaded facets lying flat on the skin ({@link Shards}), not loose
 * cubes standing off it; the face facets lie flat on the cheek and hair; the shoulder spikes are faceted and thrust up from the body.
 */
public final class DiamondheadShow extends ShowSequenceClient {
	static final float FLASH_END = 16f, OUT_AT = 36f, HAND_AT = 50f, WHIP_AT = 68f, FACE_AT = 78f, EYE_AT = 112f, PROFILE_AT = 137.5f, BACK_AT = 150f,
			WHIP2_AT = 190f, FISTS_AT = 196f, PULL_AT = 208f, HIGH_AT = 221f, WHITE_AT = 240f, POSE_AT = 246f, GREEN_AT = 277f, WORLD_AT = 287f;
	static final int TRANSFORM_AT = 126;

	/** The show's crystal: highlight, body, shadow, and the sheath's own mint. */
	private static final int C_LIGHT = 0xE6FFF4, C_MID = 0x8EEDC6, C_DARK = 0x37A979, SHEATH = 0xFFA6F2D2, SHEATH_DEEP = 0xFF74D9AE;

	private static final KirbyDots.Style DOTS = KirbyDots.Style.show().density(1.25f).size(1.35f).width(1.3f).rim(1.15f).clear(0.34f),
			GREEN_DOTS = DOTS.copy().alpha(170),
			BURST_DOTS = KirbyDots.Style.show().bands(2).density(1.0f).size(1.35f).width(1.2f).rim(1.1f).clear(0.48f).flow(1.3f);

	private static final CameraTrack TRACK = new CameraTrack(new float[][] {
			// under the flash: the usual slap framing
			{ 0f, 1.6f, 10f, 1.3f, 1.15f, 0f, 0.15f },
			// Ben from low in front, the watch forearm across his chest
			{ 16f, 1.25f, -8f, 0.95f, 1.3f, -0.05f, 0.1f },
			{ 36f, 1.0f, -18f, 1.0f, 1.34f, -0.06f, 0.1f },
			// side on from his left: the arm thrust out forward across the frame, his head at the right (v1b: pulled back, the
			// v1 camera stood beside the hand and saw the arm end-on)
			{ 36.01f, 1.5f, -92f, 1.34f, 1.24f, -0.33f, 0.36f },
			{ 50f, 1.3f, -95f, 1.32f, 1.24f, -0.33f, 0.46f },
			// the hand close
			{ 50.01f, 0.85f, -98f, 1.28f, 1.22f, -0.33f, 0.62f },
			{ 68f, 0.75f, -104f, 1.28f, 1.22f, -0.33f, 0.68f },
			// the whip: off the hand into empty dots
			{ 76f, 0.6f, -40f, 1.5f, 1.45f, -0.3f, 1.4f },
			// the face, pushing in
			{ 76.01f, 0.95f, -4f, 1.62f, 1.6f, 0f, 0f },
			{ 104f, 0.62f, -6f, 1.62f, 1.62f, 0.04f, 0f },
			{ 112f, 0.45f, -6f, 1.62f, 1.64f, 0.06f, 0f },
			// (under the eye)
			{ 112.01f, 0.3f, -6f, 1.62f, 1.64f, 0.06f, 0f },
			{ 137.5f, 0.3f, -6f, 1.62f, 1.64f, 0.06f, 0f },
			// the head, three-quarter profile from his left (v1 was so close it showed only a flat green wall)
			{ 137.51f, 0.95f, -52f, 1.62f, 1.64f, 0f, 0.02f },
			{ 150f, 0.86f, -58f, 1.62f, 1.64f, 0f, 0.02f },
			// low behind his left shoulder, looking up at the spikes
			{ 150.01f, 0.75f, -150f, 1.2f, 1.62f, 0.15f, 0f },
			{ 190f, 0.62f, -160f, 1.22f, 1.6f, 0.15f, 0f },
			// the whip round to the front
			{ 196f, 0.55f, -20f, 1.3f, 1.35f, 0f, 0f },
			// the crossed forearms, then pulling back
			{ 196.01f, 0.78f, 6f, 1.2f, 1.25f, 0f, 0f },
			{ 208f, 1.15f, 8f, 1.0f, 1.3f, 0f, 0f },
			{ 221f, 1.95f, -10f, 0.7f, 1.08f, 0f, 0f },
			{ 240f, 2.05f, -14f, 0.7f, 1.05f, 0f, 0f },
			// the stance on the burst, from his right front, low
			{ 240.01f, 2.15f, 30f, 0.55f, 0.92f, 0f, 0f },
			{ 287f, 2.0f, 28f, 0.55f, 0.92f, 0f, 0f } }).linear(68f, 76f).linear(190f, 196f);

	public DiamondheadShow() {
		super(Aliens.DIAMONDHEAD.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	// ================================================================ the poses

	/** The whole body, matched to the video's frames (see the class beat table). */
	static final LimbKeys POSE = new LimbKeys()
			// ---- low in front: the watch forearm across the chest, the right hand gripping it, looking down at the dial
			.at(FLASH_END).head(0.32f, 0.3f, 0f).lArm(-1.05f, 0.3f, 0.05f, 1.5f, 1.25f).rArm(-0.8f, -0.45f, 0.05f, 1.15f, -0.55f)
			.legs(0f, 0f, 0.05f, 0.06f, 0f).lean(0.04f)
			.at(30f).head(0.38f, 0.32f, 0.04f).lArm(-1.15f, 0.32f, 0.05f, 1.55f, 1.3f).rArm(-0.9f, -0.5f, 0.05f, 1.2f, -0.6f)
			.at(OUT_AT - 0.5f).hold()
			// ---- the arm flung out forward to his left, fingers spread; the other fist down at his side, braced
			.at(OUT_AT + 0.01f).head(0.04f, 0.2f, 0f).lArm(-1.6f, 0.08f, -0.02f, 0.14f, 0f).rArm(-0.2f, 0f, 0.3f, 0.6f, 0.2f).lean(0.08f).shrug(-0.4f)
			.at(HAND_AT).lArm(-1.55f, 0.1f, -0.02f, 0.22f, 0f)
			.at(WHIP_AT).hold().lArm(-1.58f, 0.12f, -0.04f, 0.3f, 0f)
			// ---- the face: square to the camera, the brow down, the shoulders up round the neck
			.at(FACE_AT - 2f).head(0.1f, 0.04f, 0.05f).arms(0.08f, 0f, 0.22f, 0.35f, 0.1f).lean(0f).shrug(-0.6f)
			.at(EYE_AT).head(0.06f, 0.06f, 0.09f).shrug(-1.0f)
			.at(TRANSFORM_AT - 0.5f).hold()
			// ---- Diamondhead: the head turned, arms braced
			.at(TRANSFORM_AT).head(-0.12f, 0.12f, 0f).arms(0.05f, 0f, 0.28f, 0.4f, 0.15f).shrug(0f).lean(0.03f).legs(0f, 0f, 0.07f, 0.08f, 0f)
			.at(PROFILE_AT + 12f).hold()
			// behind his shoulder: the shoulders hunching up as the spikes thrust, the head turning toward his left
			.at(BACK_AT).head(-0.12f, 0f, 0f).arms(0.1f, 0f, 0.32f, 0.55f, 0.25f).shrug(-0.5f).lean(0.12f)
			.at(175f).head(-0.2f, -0.35f, 0f).arms(0.14f, 0f, 0.38f, 0.7f, 0.3f).shrug(-1.1f).lean(0.16f)
			.at(WHIP2_AT).hold()
			// ---- the forearms crossed in front of the chest, rising (a cut under the whip)
			.at(FISTS_AT).head(0.16f, 0f, 0f).shrug(-0.6f).lean(0.06f).legs(0f, 0f, 0.07f, 0.1f, 0f)
			.rArm(-1.12f, -0.3f, 0.06f, 1.85f, -1.45f).lArm(-0.98f, 0.3f, -0.06f, 1.85f, 1.45f)
			.at(PULL_AT - 1f).hold().head(0.1f, 0f, 0f).rArm(-1.3f, -0.3f, 0.06f, 1.9f, -1.5f).lArm(-1.16f, 0.3f, -0.06f, 1.9f, 1.5f)
			// pulling back: the forearms swing open...
			.at(214f).head(-0.04f, 0.06f, 0f).arms(-0.85f, 0.25f, 0.95f, 1.75f, 0.75f).shrug(-0.2f).lean(0f).legs(0f, 0f, 0.14f, 0.12f, 0.2f)
			// ...into the double-arm flex, chest out, turned a little to his left
			.at(HIGH_AT).head(-0.18f, 0.16f, 0f).arms(-0.15f, 0.05f, 1.5f, 1.95f, 1.3f).shrug(0f).lean(-0.1f).twist(0.12f, 0f)
			.legs(0f, 0f, 0.17f, 0.14f, 0.3f)
			.at(WHITE_AT).hold().head(-0.22f, 0.2f, 0f).arms(-0.12f, 0.05f, 1.52f, 2.0f, 1.3f)
			.at(POSE_AT - 0.5f).hold()
			// ---- the stance: legs wide, the right knee forward and bent, fists out low, the right one driven at the camera
			.at(POSE_AT).head(0.08f, 0.25f, 0f).lean(0.18f).drop(1.6f).twist(0.2f, 0f)
			.rArm(-0.95f, 0.15f, 0.55f, 0.85f, 0.5f).lArm(-0.3f, -0.1f, -0.85f, 1.05f, -0.9f)
			.rLeg(-0.55f, 0.1f, 0.28f, 0.8f, 0.25f).lLeg(0.35f, -0.1f, -0.32f, 0.22f, -0.2f)
			.at(GREEN_AT).hold()
			// eased to standing under the green flash
			.at(WORLD_AT).head(0f, 0f, 0f).lean(0f).drop(0f).twist(0f, 0f).arms(0f, 0f, 0.1f, 0.15f, 0f).legs(0f, 0f, 0.03f, 0f, 0f);

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		if (t < FLASH_END || t >= WORLD_AT) {
			return;
		}
		float[] k = POSE.sample(t);
		LimbKeys.apply(m, k);
		if (t < TRANSFORM_AT) {
			// the arm trembling as the crystal races down it
			float shake = t >= OUT_AT && t < WHIP_AT + 4f ? 1f : 0.4f;
			m.leftArm.xRot += 0.025f * shake * Mth.sin(t * 3.3f);
			m.leftArm.zRot += 0.02f * shake * Mth.cos(t * 2.9f);
			m.head.zRot += t >= FACE_AT ? 0.02f * Mth.sin(t * 1.9f) : 0f;
		} else {
			float breathe = Mth.sin(t * 0.3f) * 0.035f;
			m.rightArm.zRot += breathe;
			m.leftArm.zRot -= breathe;
		}
		LimbKeys.copyLayers(m);
	}

	// ================================================================ the crystal

	/**
	 * Facets on the sheath (left arm, bone px): {face, y, across (0..1), length, angle}. The sheath grows from the dial both ways, so a
	 * facet shows once the sheath covers its y.
	 */
	private static final float[][] ARM_FACETS = armFacets();

	private static float[][] armFacets() {
		java.util.List<float[]> out = new java.util.ArrayList<>();
		long seed = 0xD1A40DL;
		int[] faces = { Shards.PX, Shards.NX, Shards.PZ, Shards.NZ };
		for (int i = 0; i < 34; i++) {
			int face = faces[i % 4];
			float y = -1.5f + 11.5f * hash(seed, i * 3);
			out.add(new float[] { face, y, 0.15f + 0.7f * hash(seed, i * 3 + 1), 2.2f + 1.6f * hash(seed, i * 3 + 2), (hash(seed, i * 7) - 0.5f) * 50f });
		}
		return out.toArray(new float[0][]);
	}

	/** Face facets (head px, -z is the face): {face, x, y, z, length, width, erupt}. Spreading from the left cheek up into the hair. */
	private static final float[][] FACE_FACETS = faceFacets();

	private static float[][] faceFacets() {
		java.util.List<float[]> out = new java.util.ArrayList<>();
		long seed = 0xFACE5L;
		for (int i = 0; i < 40; i++) {
			float r = hash(seed, i * 5);
			float x, y, z;
			int face;
			if (i < 22) {
				// the face's left half (+x), from the cheek up
				face = Shards.NZ;
				x = 0.4f + 3.5f * hash(seed, i * 5 + 1);
				y = -0.8f - 7f * r;
				z = -4.55f;
			} else if (i < 32) {
				face = Shards.PX;
				x = 4.55f;
				y = -0.8f - 7f * r;
				z = -3.6f + 7f * hash(seed, i * 5 + 1);
			} else {
				face = Shards.TOP;
				x = -1f + 5f * hash(seed, i * 5 + 1);
				y = -8.55f;
				z = -3.6f + 7f * r;
			}
			float d = Mth.sqrt((x - 2.5f) * (x - 2.5f) + (y + 1.8f) * (y + 1.8f));
			float erupt = 80f + d * 3.4f + hash(seed, i * 5 + 3) * 3f;
			out.add(new float[] { face, x, y, z, 1.9f + 1.1f * hash(seed, i * 5 + 2), 0.75f + 0.4f * hash(seed, i * 5 + 4), erupt });
		}
		return out.toArray(new float[0][]);
	}

	/** After the change, behind his shoulders: spikes thrusting up (body px: {x, z, height, angle, erupt}). */
	private static final float[][] SPIKES = { { 2.8f, 0.6f, 5.5f, 8f, 152f }, { -2.8f, 0.6f, 5.5f, -8f, 154f }, { 1.4f, 1.4f, 3.6f, 20f, 160f },
			{ -1.4f, 1.4f, 3.6f, -20f, 162f }, { 3.6f, -0.6f, 3.2f, -10f, 166f }, { -3.6f, -0.6f, 3.2f, 10f, 168f } };

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < 18f) {
			return;
		}
		int light = LightTexture.FULL_BRIGHT;
		boolean slim = p.getSkin().model() == PlayerSkin.Model.SLIM;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(Shards.WHITE));
		if (t < TRANSFORM_AT) {
			// the sheath on the watch arm: out of the dial (on the forearm), down over the hand and up toward the shoulder
			float down = smooth((t - 20f) / 36f), up = smooth((t - 24f) / 50f);
			float yHand = Mth.lerp(down, 7.4f, 10.3f), yUp = Mth.lerp(up, 6.2f, -2.2f);
			if (yHand - yUp > 0.2f) {
				Shards.sleeve(pose, vc, model.leftArm, BentLimbs.LEFT_ARM, slim, yUp + 2f, yHand + 2f, 0.32f, SHEATH, light);
				// the edges: facets pointing the way it spreads
				float xMax = slim ? 2f : 3f;
				for (int i = 0; i < 4; i++) {
					float a = (i % 2 == 0 ? 1 : -1) * 18f;
					edgeFacets(pose, vc, model, slim, yUp + 0.6f, 180f + a, xMax, i, light);
					if (down < 1f) {
						edgeFacets(pose, vc, model, slim, yHand - 0.6f, a, xMax, i + 4, light);
					}
				}
				for (float[] f : ARM_FACETS) {
					if (f[1] < yUp + 0.9f || f[1] > yHand - 0.9f) {
						continue;
					}
					armFacet(pose, vc, model, slim, (int) f[0], f[1], f[2], f[3], f[4], 1f, xMax, light);
				}
			}
			// the face: facets lying on the cheek, the side of the head and the hair
			for (float[] f : FACE_FACETS) {
				float g = pop(t - f[6], 5f);
				if (g <= 0.02f) {
					continue;
				}
				AlienParts.on(pose, model.head, () -> Shards.draw(pose, vc, (int) f[0], f[1], f[2], f[3], f[4], f[5], 0.42f,
						f[1] * 13f + f[2] * 7f, 0.55f, g, light, C_LIGHT, C_MID, C_DARK));
			}
		} else if (t < WHIP2_AT + 2f) {
			for (float[] s : SPIKES) {
				float g = pop(t - s[4], 7f);
				if (g <= 0.02f) {
					continue;
				}
				AlienParts.on(pose, model.body, () -> Shards.draw(pose, vc, Shards.TOP, s[0], -0.05f, s[1], 2.6f, 1.2f, s[2], 90f + s[3], 0.5f, g, light,
						C_LIGHT, C_MID, C_DARK));
			}
		}
	}

	/** Two facets on face {@code i % 4} of the sheath's edge at {@code y} (bone px, the top of the arm is -2). */
	private static void edgeFacets(PoseStack pose, VertexConsumer vc, PlayerModel<?> model, boolean slim, float y, float angle, float xMax, int i, int light) {
		for (int j = 0; j < 2; j++) {
			armFacet(pose, vc, model, slim, new int[] { Shards.PX, Shards.NX, Shards.PZ, Shards.NZ }[i % 4], y, 0.28f + 0.44f * j, 2.0f, angle + (j == 0 ? -12f : 12f),
					1f, xMax, light);
		}
	}

	/** One facet on the left arm's face {@code face} at {@code y} (bone px), {@code across} 0..1 over the face. */
	private static void armFacet(PoseStack pose, VertexConsumer vc, PlayerModel<?> model, boolean slim, int face, float y, float across, float len, float angle,
			float g, float xMax, int light) {
		float x, z;
		switch (face) {
			case Shards.PX -> {
				x = xMax + 0.32f;
				z = -2f + 4f * across;
			}
			case Shards.NX -> {
				x = -1.32f;
				z = -2f + 4f * across;
			}
			case Shards.PZ -> {
				x = -1f + (xMax + 1f) * across;
				z = 2.32f;
			}
			default -> {
				x = -1f + (xMax + 1f) * across;
				z = -2.32f;
			}
		}
		float fx = x, fz = z;
		Shards.onLimb(pose, model.leftArm, BentLimbs.LEFT_ARM, slim, y, () -> Shards.draw(pose, vc, face, fx, y, fz, len, 0.85f, 0.55f, angle, 0.6f, g, light,
				C_LIGHT, C_MID, C_DARK));
	}

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 14f, 30f);
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		if (t < FLASH_END) {
			st.gradient(0xF2FFE0, 0x9CF070, 0x5AC830);
		} else if (t < OUT_AT) {
			st.gradient(0x9AF06A, 0x52C82E, 0x1E7A12);
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - FLASH_END, GREEN_DOTS);
		} else if (t < WHITE_AT + 6f) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - TRACK.shotStart(t), DOTS);
		} else {
			st.gradient(0x9CFA6A, 0x3EDC22, 0x0E5E08);
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)) + 5, t - WHITE_AT, BURST_DOTS);
		}
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		// the opening flash: white-green, then the green wash that fades off Ben
		ShowShots.flash(st, t, 0xF0FFE2, -1f, 0.01f, 8f, 6f, 1f);
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, 6f, 3f, 16f, 18f, 0.8f);
		if (t >= EYE_AT && t < PROFILE_AT + 0.5f) {
			eye(st, t);
		}
		ShowShots.whipStreaks(st, t, WHIP_AT, FACE_AT - 1f);
		if (t >= WHIP2_AT && t < FISTS_AT + 0.5f) {
			// the dark green smear
			st.fill(0x0E3A12, 0.85f * env(t, WHIP2_AT, 1f, FISTS_AT - 1f, 1f));
			ShowShots.whipStreaks(st, t, WHIP2_AT, FISTS_AT + 1f);
		}
		ShowShots.flash(st, t, 0xFFFFFF, WHITE_AT, 1f, POSE_AT, 10f, 1f);
		ShowShots.greenOut(st, t, GREEN_AT, WORLD_AT, WORLD_AT + 1f);
	}

	/**
	 * The eye close-up, over crystal facets: Ben's eye (white, yellow-green iris, pupil), the yellow flooding up from below, then
	 * Diamondhead's hexagonal yellow eye, smeared away. v1b: the facets round it are cel-shaded (a light, a mid and a dark plane each,
	 * with white edge glints), the eye pushes in slowly and the whites carry a shadow under the upper lid, like the show's.
	 */
	private static void eye(Stage st, float t) {
		float u = t - EYE_AT;
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float k = st.k();
		float push = 1f + 0.012f * u;
		st.fill(vc, p, 0x3FBF78, 1f);
		// crystal facets round it
		quad(st, vc, p, -1.5f, 1.5f, -0.3f, 1.5f, -0.55f, 0.55f, -1.5f, 0.2f, 0x8FEFC0);
		quad(st, vc, p, -1.5f, 0.2f, -0.55f, 0.55f, -0.9f, -0.4f, -1.5f, -0.9f, 0x5ED79C);
		quad(st, vc, p, 0.4f, -1.5f, 1.5f, -1.5f, 1.5f, -0.2f, 0.7f, -0.6f, 0x2E9A60);
		quad(st, vc, p, -0.9f, -0.4f, 0.7f, -0.6f, 0.4f, -1.5f, -1.5f, -1.5f, 0x48BA80);
		quad(st, vc, p, 0.6f, 1.5f, 1.5f, 1.5f, 1.5f, 0.6f, 0.9f, 0.7f, 0xC4F8E0);
		Shards.line(st, vc, p, -1.5f, 0.2f, -0.55f, 0.55f, 0.008f, 0xF2FFF8, 0.9f);
		Shards.line(st, vc, p, 0.7f, -0.6f, 1.5f, -0.2f, 0.008f, 0xF2FFF8, 0.7f);
		Shards.line(st, vc, p, 0.9f, 0.7f, 1.5f, 0.6f, 0.006f, 0xFFFFFF, 0.8f);
		float drift = 0.02f * u;
		float cx = (0.05f - drift) * k, cy = 0.05f;
		float morph = smooth((u - 9f) / 6f);
		float hex = smooth((u - 14f) / 2f);
		if (hex < 1f) {
			// Ben's eye
			ellipse(st, vc, p, cx, cy, 0.62f * k * push, 0.38f * push, 24, 0f, 0x0A0A0A, 1f);
			// the white goes yellow as the iris and the pupil melt into it
			int white = ShowMath.lerpRgb(morph, 0xF4F4F0, 0xF4E21E);
			ellipse(st, vc, p, cx, cy, 0.58f * k * push, 0.34f * push, 24, 0f, white, 1f);
			// the lid's shadow over the top of the white
			ellipse(st, vc, p, cx, cy + 0.17f * push, 0.5f * k * push, 0.12f * push, 20, 0f, ShowMath.scaleRgb(white, 0.82f), 1f);
			float iris = 1f - morph;
			ellipse(st, vc, p, cx + 0.06f * k, cy + 0.06f, 0.27f * k * push, 0.27f * push, 24, 0f, 0x1A1A08, iris);
			ellipse(st, vc, p, cx + 0.06f * k, cy + 0.06f, 0.24f * k * push, 0.24f * push, 24, 0f, 0xC6DA1E, iris);
			ellipse(st, vc, p, cx + 0.08f * k, cy + 0.08f, 0.1f * k * push, 0.1f * push, 18, 0f, 0x050505, iris);
			ellipse(st, vc, p, cx + 0.0f * k, cy + 0.15f, 0.05f * k * push, 0.05f * push, 12, 0f, 0xFFFFFF, iris);
		}
		if (hex > 0f) {
			// Diamondhead's eye: a black-rimmed yellow hexagon, a darker lower half and a white glint
			ellipse(st, vc, p, cx, cy, 0.66f * k * push, 0.36f * push, 6, 0f, 0x050505, hex);
			ellipse(st, vc, p, cx, cy, 0.6f * k * push, 0.31f * push, 6, 0f, 0xF2E01E, hex);
			ellipse(st, vc, p, cx, cy - 0.1f * push, 0.42f * k * push, 0.14f * push, 6, 0f, 0xD8BC10, hex);
			ellipse(st, vc, p, cx - 0.12f * k, cy + 0.12f, 0.16f * k * push, 0.06f * push, 12, 0f, 0xFFFBD0, hex);
		}
		ShowShots.whipStreaks(st, t, PROFILE_AT - 5f, PROFILE_AT + 1f);
	}

	/** A filled quad given its four screen corners. */
	private static void quad(Stage st, VertexConsumer vc, PoseStack.Pose p, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy,
			int rgb) {
		st.put(vc, p, ax, ay, rgb, 1f);
		st.put(vc, p, bx, by, rgb, 1f);
		st.put(vc, p, cx, cy, rgb, 1f);
		st.put(vc, p, dx, dy, rgb, 1f);
	}

	/** A filled ellipse (an {@code n}-gon; 6 = a hexagon) of radii {@code rx} (screen widths) / {@code ry} (screen heights). */
	static void ellipse(Stage st, VertexConsumer vc, PoseStack.Pose p, float cx, float cy, float rx, float ry, int n, float rot, int rgb, float a) {
		for (int i = 0; i < n; i++) {
			float a0 = rot + i * Mth.TWO_PI / n, a1 = rot + (i + 1) * Mth.TWO_PI / n;
			st.put(vc, p, cx, cy, rgb, a);
			st.put(vc, p, cx + Mth.cos(a0) * rx, cy + Mth.sin(a0) * ry, rgb, a);
			st.put(vc, p, cx + Mth.cos(a1) * rx, cy + Mth.sin(a1) * ry, rgb, a);
			st.put(vc, p, cx, cy, rgb, a);
		}
	}
}
