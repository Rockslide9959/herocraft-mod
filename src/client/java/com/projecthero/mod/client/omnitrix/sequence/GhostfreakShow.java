package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.HipBend;
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
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.rot;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show, try to be as expressive as you can without bending the limbs"): Ghostfreak's
 * transformation from the show, shot for shot, cut to its own soundtrack. Compilation video second {@code v} is tick
 * {@code (v - 231.00) * 20}. The beats (ticks after the right-click):
 * <pre>
 *   0-6      the slap, from high above (Ben looking up into it)              v 231.00-231.30
 *   6-24     FULL GREEN FLASH                                                 v 231.3-232.2
 *   24-32    Ben's upper body coming out of the green, grinning             v 232.2-232.6   (green field, black Kirby dots)
 *   32-38    the face, from a little below, the grin widening               v 232.6-232.9
 *   38-46    right in on the face, the eyes wide, the skin going pale       v 232.9-233.3
 *   42-52    a black ring swallowing his left eye; the camera dives in      v 233.1-233.6
 *   49-58    BLACK -- the body changes inside it (52)                         v 233.45-233.9
 *   58-64    striped dots on the show green, a grey dome rising           v 233.9-234.2
 *   64-84    right on the grey skin, the black seam down it                 v 234.2-235.2
 *   84-112   the purple eye opens in the middle of the frame                v 235.2-236.6
 *   112-130  pulling back round the head, from below                        v 236.6-237.5
 *   130-152  in profile, rearing up, the claws curling by his face          v 237.5-238.6
 *   152-166  WHITE FLASH                                                      v 238.6-239.3
 *   166-218  floating front on, slanted up to the right, the open black     v 239.3-241.9
 *            front and both claws to the camera, the tail sweeping off low
 *            to the left; on the green ray burst (final-pose rework)
 *   218-230  FULL GREEN FLASH                                                 v 241.9-242.5
 *   230-242  back in the world, the green fading off                        v 242.5-243.1
 * </pre>
 * The pale wash and the black eye are drawn on the human face, the poses on the body, so everyone nearby sees them; flat-colour boxes,
 * no particles.
 */
public final class GhostfreakShow extends ShowSequenceClient {
	static final float SLAP_END = 6f, BEN_AT = 24f, FACE_AT = 32f, CLOSE_AT = 38f, RING_AT = 42f, DIVE_AT = 46f, BLACK_AT = 49f, DOME_AT = 58f,
			SKIN_AT = 64f, EYE_AT = 84f, ROUND_AT = 112f, REAR_AT = 130f, WHITE_AT = 152f, FLOAT_AT = 166f, GREEN2_AT = 218f, WORLD_AT = 230f;
	static final int TRANSFORM_AT = 52;

	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");

	private static final int BURST_IN = 0x8CFF5A, BURST_MID = 0x2ED81E, BURST_OUT = 0x0A6A08, BURST_RAY = 0x063A06;

	private static final KirbyDots.Style DOTS = KirbyDots.Style.show(), STRIPES = KirbyDots.Style.show().bands(4).width(1.6f).density(1.4f).clear(0.15f)
			.angle(35f).spread(6f), BURST_DOTS = KirbyDots.Style.show().bands(1).clear(0.75f).rim(1.4f).flow(1.4f);

	private static final float W = CameraTrack.W;
	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(new float[][] {
			// the slap, from high above and in front, Ben looking up into it
			// (v0.17.4: much closer and steeper, so his upper body fills the frame from above like the show's, the open hand near the lens)
			{ 0f, 0.6f, 18f, 2.12f, 1.3f, 0f, 0.14f },
			{ 6f, 0.55f, 18f, 2.1f, 1.3f, 0f, 0.14f } }, ShowShots.dialCloseUp(6.01f, BEN_AT),
			new float[][] {
					// the upper body, a little below and in front
					// (v0.17.4: waist up, his head at the right of the frame, the claw reaching down at the left)
					{ 24.01f, 1.45f, 20f, 1.3f, 1.28f, 0.14f, 0f },
					{ 32f, 1.3f, 16f, 1.32f, 1.3f, 0.12f, 0f },
					// the face, from a little below (v0.17.4: the face filling the frame)
					{ 32.01f, 0.7f, -10f, 1.5f, 1.6f, 0f, 0f },
					{ 38f, 0.62f, -8f, 1.52f, 1.6f, 0f, 0f },
					// right in on the face (v0.17.4: closer, both eyes across the frame)
					{ 38.01f, 0.46f, 4f, 1.6f, 1.6f, -0.02f, 0f },
					{ 46f, 0.4f, 2f, 1.6f, 1.6f, -0.04f, 0f },
					// diving into his left eye
					{ 46.01f, 0.46f, -4f, 1.66f, 1.66f, -0.12f, 0.12f },
					{ 52f, 0.3f, -4f, 1.66f, 1.66f, -0.12f, 0.12f },
					{ 52.01f, 0.6f, 0f, 1.5f, 1.8f, 0f, 0f },
					// the grey dome rising into the frame, from below
					{ 58f, 0.6f, 0f, 1.5f, 1.8f, 0f, 0f },
					{ 58.01f, 0.5f, 12f, 1.42f, 1.86f, 0f, 0f },
					{ 64f, 0.44f, 10f, 1.48f, 1.84f, 0f, 0f },
					// right on the skin
					// (v0.17.4: square on, the seam running down the middle of the frame)
					{ 64.01f, 0.24f, 6f, 1.76f, 1.78f, 0.02f, 0f },
					{ 84f, 0.2f, 0f, 1.74f, 1.76f, 0.02f, 0f },
					// the eye, pulling back off it (v0.17.4: centred on the eye and close, so the grey skin fills the frame round it)
					{ 84.01f, 0.4f, -4f, 1.64f, 1.64f, -0.14f, 0f },
					{ 112f, 0.5f, 8f, 1.64f, 1.64f, -0.14f, 0f },
					// round the head, from below
					{ 112.01f, 0.42f, 34f, 1.42f, 1.7f, 0f, 0f },
					{ 130f, 0.56f, 52f, 1.38f, 1.7f, 0f, 0f },
					// in profile, rearing (v0.17.4: close and from below, the head and the curling claws filling the top of the frame)
					{ 130.01f, 0.8f, 74f, 1.3f, 1.62f, 0f, 0.05f },
					{ 152f, 0.72f, 68f, 1.32f, 1.66f, 0f, 0.05f },
					// floating, nearly front on from a little to his right and a little below (final-pose rework: closer, so he fills the
					// frame like the show's, the open front and both claws to the camera; it was small, from three-quarters round)
					{ 152.01f, 1.72f, 12f, 0.95f, 1.1f, 0f, 0f },
					{ 230f, 1.6f, 10f, 0.95f, 1.12f, 0f, 0f } }));

	public GhostfreakShow() {
		super(Aliens.GHOSTFREAK.id());
	}

	@Override
	public float worldAt() {
		return WORLD_AT;
	}

	@Override
	public int burstAt() {
		return 220;
	}

	@Override
	public CameraTrack track() {
		return TRACK;
	}

	@Override
	public double fov(float t) {
		double fov = 62;
		if (t >= DIVE_AT && t < DOME_AT) {
			fov -= 22 * smooth((t - DIVE_AT) / 7f);
		} else if (t >= FLOAT_AT && t < WORLD_AT) {
			fov -= 4 * smooth((t - FLOAT_AT) / 40f);
		}
		return fov;
	}

	// ================================================================ the poses

	/**
	 * v0.17.3 touch-up (user: "make sure their limbs don't go weird or detach from the body and touch up the effects and poses"):
	 * Ghostfreak's body runs on a keyframed pose track ({@link FourArmsShow.Track}) with bent elbows ({@link BentLimbs}), every
	 * part from its rest place ({@link Xlr8Show#apply}): the old float pose lifted the torso, head and arms off their pivots by
	 * hand, and in the last shot the arms hung apart from the body. He has no legs (the tail), so the leg channels stay at rest.
	 */
	static final FourArmsShow.Track POSE = new FourArmsShow.Track()
			// ---- hanging still while the camera crawls over him, the claws drifting up, curled
			.at(TRANSFORM_AT).arms(-0.35f, 0f, 0.3f, 0.95f, 0.45f).head(-0.12f, 0f, 0f)
			.at(EYE_AT).arms(-0.5f, 0f, 0.36f, 1.15f, 0.5f).head(-0.18f, 0f, 0f)
			.at(REAR_AT - 0.5f).hold().arms(-0.55f, 0f, 0.38f, 1.2f, 0.5f)
			// ---- rearing up in profile (a cut): leaning back, head thrown back, the claws curling up past his face
			.at(REAR_AT).lean(-0.16f).head(-0.55f, 0f, 0f)
			.rArm(-2.35f, 0.2f, 0.22f, 1.6f, 0.45f).lArm(-1.45f, -0.3f, -0.4f, 1.45f, -0.5f)
			.at(141f).lean(-0.22f).head(-0.68f, 0f, 0.05f).rArm(-2.55f, 0.25f, 0.28f, 1.95f, 0.55f).lArm(-1.7f, -0.3f, -0.45f, 1.75f, -0.6f)
			.at(WHITE_AT - 0.5f).hold()
			// ---- floating (a cut, under the white; final-pose rework, the show's t 205): lifted, the open front to the camera,
			// slanted over to his left about the hips (ShowBody.TILT: head up at the right of frame, the tail sweeping off low to the
			// left), leaning back a touch, chin up; the right claw raised out to the side on a bent elbow (forearm up, fingers
			// splayed), the left claw hanging low by his side; it was pitched forward and laid out, seen from three-quarters
			.at(WHITE_AT).drop(-3f).lean(-0.1f).set(ShowBody.TILT, 0.72f).head(-0.3f, 0f, 0f)
			.rArm(-0.5f, 0.1f, 0.8f, 1.3f, 1.4f).lArm(-0.25f, 0f, -0.9f, 0.6f, -0.4f)
			.at(192f).rArm(-0.55f, 0.1f, 0.86f, 1.42f, 1.4f).lArm(-0.3f, 0f, -0.95f, 0.66f, -0.4f).head(-0.34f, 0f, 0.03f)
			.at(GREEN2_AT + 2f).hold()
			.at(WORLD_AT).hold().drop(0f).lean(0f).set(ShowBody.TILT, 0f).head(0f, 0f, 0f).arms(0f, 0f, 0.1f, 0.15f, 0f);

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		if (t >= TRANSFORM_AT) {
			float drift = Mth.sin(t * 0.09f);
			Xlr8Show.apply(m, POSE.sample(t), 0.04f * drift, t >= FLOAT_AT ? 0.08f * Mth.sin(t * 0.21f) : 0.05f * Mth.sin(t * 0.12f), t < WORLD_AT);
			return;
		}
		m.head.yRot = 0f;
		if (t < BEN_AT) {
			// the slap: looking up into the camera above him, the free hand flung out to the side, open, the elbow soft
			m.head.xRot = -0.7f;
			float w = smooth((t - 2f) / 3f);
			rot(m.rightArm, w, -0.5f, 0f, 1.1f);
			BentLimbs.bend(BentLimbs.RIGHT_ARM, 0.35f * w, 0.3f * w);
		} else {
			// the grin: the chin down, leaning into the camera, the hands up and spread like claws already (forearms up off bent
			// elbows, so the hands frame the face instead of a straight arm poking at the lens)
			float w = smooth((t - BEN_AT) / 5f);
			float wild = smooth((t - CLOSE_AT) / 8f);
			rot(m.rightArm, w, -1.05f + 0.05f * Mth.sin(t * 3.1f), 0.35f, 0.5f);
			rot(m.leftArm, w, -0.95f + 0.05f * Mth.sin(t * 2.7f), -0.3f, -0.45f);
			BentLimbs.bend(BentLimbs.RIGHT_ARM, (1.25f + 0.08f * Mth.sin(t * 2.3f)) * w, 0.55f * w);
			BentLimbs.bend(BentLimbs.LEFT_ARM, (1.15f + 0.08f * Mth.sin(t * 2.0f)) * w, -0.5f * w);
			m.head.xRot = Mth.lerp(w, m.head.xRot, 0.22f - 0.12f * wild + 0.04f * Mth.sin(t * 2.2f));
			m.head.zRot = 0.08f * w + 0.05f * wild * Mth.sin(t * 1.7f);
			HipBend.pitch(m, w, 0.12f);
		}
		if (m instanceof PlayerModel<?> pm) {
			pm.rightSleeve.copyFrom(m.rightArm);
			pm.leftSleeve.copyFrom(m.leftArm);
			pm.jacket.copyFrom(m.body);
		}
		m.hat.copyFrom(m.head);
	}

	/**
	 * v0.17.3 touch-up: the tail's shape while the show plays (from {@code GhostfreakTail}): {angle off his body's down axis toward
	 * his back at the root, at the tip (degrees), ripple (degrees), ripple phase, side swing}. Null when not playing. The holder's
	 * scripted camera counts as first person, which hid the world tail through the whole sequence.
	 */
	public static float[] tailShape(Player p, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.GHOSTFREAK.id());
		if (t < TRANSFORM_AT || t >= WORLD_AT) {
			return null;
		}
		if (t < REAR_AT) {
			// hanging under him, curling back, a slow ripple
			return new float[] { 14f, 42f, 14f, t * 0.2f, 0.1f };
		}
		if (t < WHITE_AT) {
			// swept forward under him as he rears back
			return new float[] { 22f, 62f, 16f, t * 0.26f, 0.08f };
		}
		// final-pose rework: sweeping off low to his right (the left of frame) and curling up and back at the tip, the wisp rippling
		// down its length (the show's S-curve); the sixth value is a steady swing out to his left (-: his right), growing to the tip
		return new float[] { 10f, 105f, 16f, t * 0.3f, 0.08f, -1.2f };
	}

	// ================================================================ the float's open front and claws (GhostfreakClient)

	/**
	 * Final-pose rework: from {@code GhostfreakClient.renderExtras}, through the float (the white flash to the world): the show draws
	 * him as one sheet with its open black front to the camera and long clawed hands. A black opening down the front of his body,
	 * narrow under the badge and widening to the hem, and three long pale fingers with dark tips on each hand (riding the bent
	 * forearm), splayed. Everyone nearby sees them.
	 */
	public static void renderAlien(PoseStack pose, MultiBufferSource buffers, int light, Player p, PlayerModel<?> model, float partial) {
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.GHOSTFREAK.id());
		if (t < WHITE_AT || t >= WORLD_AT) {
			return;
		}
		float g = ShowMath.pop(t - WHITE_AT, 4f);
		if (g <= 0.02f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		// the open front: strips widening from under the badge (y 5.8) to the hem, over the jacket layer
		AlienParts.on(pose, model.body, () -> {
			int n = 6;
			for (int i = 0; i < n; i++) {
				float y0 = 5.8f + i * (12.1f - 5.8f) / n, y1 = 5.8f + (i + 1) * (12.1f - 5.8f) / n;
				float hw = Mth.lerp((i + 0.5f) / n, 0.9f, 3.4f) * g;
				AlienParts.box(pose, vc, -hw, y0, -2.42f, hw, y1, -2.3f, 0f, 0f, 1f, 1f, 0xFF07070A, light);
			}
		});
		for (int side = 0; side < 2; side++) {
			boolean right = side == 0;
			float xc = right ? -1f : 1f;
			int limb = right ? BentLimbs.RIGHT_ARM : BentLimbs.LEFT_ARM;
			AlienParts.on(pose, right ? model.rightArm : model.leftArm, () -> {
				BentLimbs.toForearm(pose, limb, false);
				for (int f = -1; f <= 1; f++) {
					pose.pushPose();
					pose.translate((xc + f * 1.3f) / 16f, 9.7f / 16f, 0f);
					// splayed out from the hand, curling a little forward
					pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(f * 14f));
					pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-14f));
					float len = (f == 0 ? 3.6f : 3.1f) * g;
					AlienParts.box(pose, vc, -0.45f, 0f, -0.45f, 0.45f, len, 0.45f, 0f, 0f, 1f, 1f, 0xFFDAD6CE, light);
					AlienParts.box(pose, vc, -0.32f, len, -0.32f, 0.32f, len + 0.9f * g, 0.32f, 0f, 0f, 1f, 1f, 0xFF26242C, light);
					pose.popPose();
				}
			});
		}
	}

	// ================================================================ the pale face and the black eye

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < FACE_AT || t >= TRANSFORM_AT) {
			return;
		}
		int light = LightTexture.FULL_BRIGHT;
		// the face draining pale (translucent: the grin still shows)
		float pale = smooth((t - FACE_AT - 2f) / 12f);
		if (pale > 0f) {
			VertexConsumer wash = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
			int a = Math.round(120 * pale);
			AlienParts.on(pose, model.head, () -> AlienParts.box(pose, wash, -4.6f, -8.6f, -4.6f, 4.6f, 0.6f, 4.6f, 0f, 0f, 1f, 1f, (a << 24) | 0xF0E8E0,
					light));
		}
		// a black ring round his left eye, swelling until it swallows the face
		float ring = smooth((t - RING_AT) / 4f), swallow = smooth((t - DIVE_AT) / 6f);
		if (ring > 0f) {
			VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
			float r = 1.5f * ring + 5f * swallow;
			AlienParts.on(pose, model.head, () -> {
				disc(pose, solid, 2f, -4.3f, -4.7f, r, 0xFF050505, light);
				if (swallow < 0.4f) {
					// the eye still glaring in the middle of it
					disc(pose, solid, 2f, -4.3f, -4.75f, 0.75f * ring, 0xFFF4F4E8, light);
					disc(pose, solid, 2f, -4.3f, -4.8f, 0.4f * ring, 0xFF9CC21E, light);
				}
			});
		}
	}

	/** A flat round disc facing forward (-z): a stack of boxes. */
	private static void disc(PoseStack pose, VertexConsumer vc, float cx, float cy, float z, float r, int argb, int light) {
		int n = 6;
		for (int i = 0; i < n; i++) {
			float a = (i + 0.5f) / n * Mth.HALF_PI;
			float hx = Mth.cos(a) * r, hy = Mth.sin(a) * r;
			AlienParts.box(pose, vc, cx - hx, cy - hy, z - 0.02f, cx + hx, cy + hy, z, 0f, 0f, 1f, 1f, argb, light);
		}
	}

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 3f, 24f);
		// v0.17.3 touch-up: a faint cold lilac haze round the new body as it floats (additive: dim colours, or it washes him out)
		ShowShots.bodyGlow(pose, buffers, cam, p, t, partial, WHITE_AT + 4f, GREEN2_AT, 0x241E3A, 0x3E3858);
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		int shot = TRACK.shot(t);
		float inShot = t - TRACK.shotStart(t);
		if (t < SLAP_END) {
			// the tiled corridor floor below him
			st.gradient(0xB8B49C, 0x9A967E, 0x6E6A56);
		} else if (t < BEN_AT) {
			st.gradient(0xF2FFE0, 0x7CEB4C, 0x2DB51C);
		} else if (t < FACE_AT) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(shot), inShot, DOTS);
		} else if (t < CLOSE_AT) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(shot), inShot, DOTS);
		} else if (t < DOME_AT) {
			// right in on the face: only skin in the frame
			st.gradient(0xF6C69A, 0xE8AE80, 0xC88A60);
		} else if (t < WHITE_AT) {
			// the show green, crossed by broad stripes of dots
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(shot), inShot, STRIPES);
		} else {
			st.gradient(BURST_IN, BURST_MID, BURST_OUT);
			st.rays(st.seed, t - WHITE_AT, BURST_RAY, 18);
			wisps(st, t - WHITE_AT);
			KirbyDots.draw(st, st.shotSeed(shot) + 5, t - WHITE_AT, BURST_DOTS);
		}
	}

	/**
	 * v0.17.3 touch-up: ghostly wisps on the burst behind him -- long pale ribbons curling slowly across the frame from the right
	 * (where he is heading) off to the left, each tapering to nothing at both ends and rippling along its length.
	 */
	private static void wisps(Stage st, float t) {
		float a = smooth(t / 10f);
		if (a <= 0f) {
			return;
		}
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		long seed = st.seed;
		int seg = 16;
		for (int i = 0; i < 5; i++) {
			float base = (ShowMath.hash(seed, i + 700) * 2f - 1f) * 0.7f;
			float slope = -0.25f - 0.2f * ShowMath.hash(seed, i + 710);
			float drift = (t * (0.012f + 0.01f * ShowMath.hash(seed, i + 720)) + ShowMath.hash(seed, i + 730)) % 1f;
			float x0 = 1.4f - drift * 1.2f, len = 1.6f + 0.8f * ShowMath.hash(seed, i + 740), w = 0.02f + 0.025f * ShowMath.hash(seed, i + 750);
			float ph = t * 0.12f + i * 2.1f;
			float al = (0.16f + 0.12f * ShowMath.hash(seed, i + 760)) * a;
			for (int s = 0; s < seg; s++) {
				float f0 = s / (float) seg, f1 = (s + 1) / (float) seg;
				float xa = x0 - f0 * len, xb = x0 - f1 * len;
				float ya = base + slope * (xa - x0) * -0.4f + 0.09f * Mth.sin(f0 * 7f + ph), yb = base + slope * (xb - x0) * -0.4f + 0.09f * Mth.sin(f1 * 7f + ph);
				float wa = w * Mth.sin(f0 * Mth.PI), wb = w * Mth.sin(f1 * Mth.PI);
				st.put(vc, p, xa, ya + wa, 0xF4F0FF, al);
				st.put(vc, p, xb, yb + wb, 0xF4F0FF, al);
				st.put(vc, p, xb, yb - wb, 0xF4F0FF, al);
				st.put(vc, p, xa, ya - wa, 0xF4F0FF, al);
			}
		}
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 4.5f);
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, 10f, 1f, BEN_AT - 2f, 8f, 1f);
		// into the black of the eye, out of it onto the dome
		float black = env(t, BLACK_AT, 2f, DOME_AT - 1f, 2f);
		if (black > 0f) {
			st.fill(0x000000, black);
		}
		// the white flash, thinning onto the green burst
		ShowShots.flash(st, t, 0xFFFEF2, WHITE_AT, 1f, 163f, 14f, 1f);
		ShowShots.greenOut(st, t, GREEN2_AT, WORLD_AT, total());
	}
}
