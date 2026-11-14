package com.projecthero.mod.client.omnitrix.sequence;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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

import software.bernie.geckolib.cache.object.GeoBone;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.env;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.smooth;

/**
 * v0.17.3 (user: "create transformation sequence for all the aliens that are in this video ... please make the posing and everything
 * look exactly like how it looks in the show, try to be as expressive as you can without bending the limbs"): Wildmutt's show
 * transformation, shot for shot. Compilation video second {@code v} is tick {@code (v - 31.10) * 20}; the beats:
 * <pre>
 *   0-6     the slap (the alley, front medium)
 *   5-25    FULL GREEN FLASH
 *   25-45   the dial close-up, green light bursting off it
 *   45-66   the hand from above: peach flesh bubbling up round the dial, red veins (dark green field, black dots)
 *   66-100  up the forearm to the shoulder, the swelling chasing it
 *   100-128 the face, tilted and close, swelling over
 *   128-148 ORANGE FLASH: glossy fur filling the frame
 *   148-158 from behind: Ben hunched over, orange fur bursting out of his clothes (olive field, black splotches, a yellow glow)
 *   158-166 side on, head hung
 *   166-178 from in front: clawing at his face, then the furry head dropping, roaring at the camera
 *   178-188 orange streaks whipping across black                                  (the body becomes Wildmutt at 180)
 *   188-210 Wildmutt's eyeless head in profile, snarling
 *   210-238 low on the front paws, a claw lifting and planting
 *   238-244 his back passing close
 *   244-250 the head low, side on
 *   250-262 rearing up from low in front, the claws raised
 *   262-268 dropping into a lunge
 *   268-282 WHITE FLASH, fading off the last shot
 *   280-325 on the slope, head thrown back, roaring at the sky
 *   322-338 FULL GREEN FLASH, then the alley, the green fading
 * </pre>
 * Before the change the swelling and fur are on the human body; after it, {@code WildmuttModel} hands its bones to
 * {@link #poseBones} while this plays, so everyone sees the poses.
 *
 * <p><b>v1b touch-up</b> (user: "make sure their limbs don't go weird or detach from the body and touch up the effects and poses"):
 * the human body runs on one keyframed pose track ({@link LimbKeys}) with bent elbows and knees, the hunch leans about the hips (v1
 * swung the torso off the legs), the claws come up to the face; the swelling lumps are smaller and sunk into the skin (v1 piled
 * whole cubes round the hand and face) with red veins crawling ahead of them; the fur bursts out in spiky cel-shaded tufts
 * ({@link Shards}) that ride the bent forearms; Wildmutt himself pitches about his hips with every limb posed by its world angle, so
 * rearing and the roar on the slope keep the hind feet planted and the front paws on the ground (v1 sank one end and floated the other).
 */
public final class WildmuttShow extends ShowSequenceClient {
	static final float SLAP_END = 6f, DIAL_AT = 25f, HAND_AT = 45f, ARM_AT = 66f, FACE_AT = 100f, ORANGE_AT = 128f, HUNCH_AT = 148f, SIDE_AT = 158f,
			CLAW_AT = 166f, STREAK_AT = 178f, HEAD_AT = 188f, PAW_AT = 210f, BACK_AT = 238f, LOW_AT = 244f, REAR_AT = 250f, LUNGE_AT = 262f,
			WHITE_AT = 268f, ROAR_AT = 270f, GREEN_AT = 322f, WORLD_AT = 338f;
	static final int TRANSFORM_AT = 180;

	private static final KirbyDots.Style DARK_DOTS = KirbyDots.Style.show().size(1.2f),
			SPLOTCH_DOTS = KirbyDots.Style.show().bands(3).density(1.3f).size(1.5f).width(1.4f).rim(1.2f).clear(0.34f).flow(0.7f),
			ROAR_DOTS = KirbyDots.Style.show().bands(2).density(1.3f).size(1.6f).width(1.4f).rim(1.2f).clear(0.45f).angle(-25f).flow(0.5f);

	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(
			// v0.17.4: waist up and close from his left front, the flung arm across the top (the shared slap shot was full length)
			new float[][] { { 0f, 1.2f, -16f, 1.48f, 1.32f, 0f, 0.15f }, { SLAP_END, 1.1f, -14f, 1.46f, 1.3f, 0f, 0.15f } }, ShowShots.dialCloseUp(SLAP_END + 0.01f, HAND_AT),
			new float[][] {
					// the hand from above (v0.17.4: straight down over the back of the hand, the dial in the middle and the fingers
					// spread toward the top of the frame, as in the show; it was from the side, the strap filling the top)
					{ 45.01f, 0.28f, -60f, 1.75f, 1.22f, -0.33f, 0.66f },
					{ 66f, 0.26f, -70f, 1.75f, 1.22f, -0.33f, 0.62f },
					// up the forearm to the shoulder (v0.17.4: lower and along the arm, the swelling flesh filling the frame and the
					// sleeve behind it; it stayed over the watch strap)
					{ 66.01f, 0.5f, -66f, 1.55f, 1.36f, -0.33f, 0.16f },
					{ 90f, 0.48f, -74f, 1.6f, 1.4f, -0.3f, 0.06f },
					{ 100f, 0.5f, -80f, 1.66f, 1.44f, -0.3f, 0.0f },
					// the face, tilted and close (v0.17.4: closer)
					{ 100.01f, 0.68f, -30f, 1.64f, 1.66f, -0.06f, 0f },
					{ 128f, 0.5f, -22f, 1.66f, 1.68f, -0.06f, 0f },
					// (under the orange)
					{ 128.01f, 0.36f, -20f, 1.52f, 1.62f, -0.02f, 0f },
					{ 148f, 0.36f, -20f, 1.52f, 1.62f, -0.02f, 0f },
					// hunched, from behind and above
					// (v0.17.4: these three are all closer -- the show fills the frame with the hunched back, then the head and
					// shoulder, then the face and claws)
					{ 148.01f, 0.9f, -145f, 1.8f, 1.2f, 0f, 0.1f },
					{ 158f, 0.8f, -138f, 1.75f, 1.2f, 0f, 0.1f },
					// side on, head hung
					{ 158.01f, 1.2f, -76f, 1.62f, 1.6f, 0f, 0.3f },
					{ 166f, 1.12f, -72f, 1.62f, 1.6f, 0f, 0.3f },
					// from in front, low, the head dropping into the lens
					{ 166.01f, 0.9f, 10f, 1.2f, 1.45f, 0f, 0.15f },
					{ 178f, 0.48f, 4f, 1.2f, 1.4f, 0f, 0.15f },
					// (under the streaks)
					{ 178.01f, 0.9f, 80f, 1.15f, 1.1f, 0f, 0.95f },
					{ 188f, 0.9f, 80f, 1.15f, 1.1f, 0f, 0.95f },
					// Wildmutt's head in profile
					// (v0.17.4: in close, the head filling the right of the frame and a front leg at the bottom left, as the show)
					{ 188.01f, 1.2f, 78f, 1.1f, 1.0f, 0f, 0.9f },
					{ 210f, 1.08f, 72f, 1.05f, 1.0f, 0f, 0.95f },
					// low on the front paws (v0.17.4: from his right and down at the paw, so the leg runs in from the top left and
					// the claws point right and fill the frame; it was the whole front of him from his left)
					{ 210.01f, 0.95f, 62f, 0.3f, 0.3f, 0f, 0.6f },
					{ 238f, 0.88f, 54f, 0.3f, 0.32f, 0f, 0.55f },
					// his back passing close
					{ 238.01f, 0.85f, -110f, 1.75f, 1.3f, 0f, -0.2f },
					{ 244f, 0.8f, -140f, 1.75f, 1.3f, 0f, -0.4f },
					// the head low, side on
					{ 244.01f, 1.6f, 92f, 0.85f, 0.8f, 0f, 0.7f },
					{ 250f, 1.5f, 88f, 0.85f, 0.8f, 0f, 0.7f },
					// rearing up, from low in front
					// (v0.17.4: closer, chest to head filling the frame from below)
					{ 250.01f, 1.55f, 12f, 0.4f, 1.45f, 0f, 0.3f },
					{ 262f, 1.4f, 8f, 0.4f, 1.5f, 0f, 0.3f },
					// the lunge
					{ 262.01f, 2.4f, 18f, 0.3f, 0.6f, 0f, 0.5f },
					{ 268f, 1.7f, 12f, 0.3f, 0.6f, 0f, 0.6f },
					// the roar on the slope, low (final-pose rework: three-quarters front from his right, so he faces the right of frame
					// turned toward the camera with both front paws showing, like the show's; it was side-on from his left)
					{ 268.01f, 2.85f, 38f, 0.5f, 1.05f, 0f, 0.3f },
					{ 338f, 2.65f, 34f, 0.5f, 1.08f, 0f, 0.3f } }))
			.shake(LUNGE_AT, WHITE_AT, 0.02f, 6f).shake(285f, GREEN_AT, 0.01f, 40f);

	public WildmuttShow() {
		super(Aliens.WILDMUTT.id());
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
		if (t >= DIAL_AT && t < HAND_AT) {
			fov -= 8 * smooth((t - DIAL_AT) / 20f);
		}
		return fov;
	}

	// ================================================================ the human poses (before the change)

	/** The human body before the change, matched to the video's frames (see the class beat table). */
	static final LimbKeys POSE = new LimbKeys()
			// ---- v0.17.4: the slap (the show's first frames): the watch forearm forward and low, the right arm flung wide out to the
			// side, open, then slapping down on the dial
			.at(0f).lArm(-0.95f, 0.3f, 0f, 0.95f, 1.0f).rArm(-0.15f, 0f, 1.55f, 0.35f, 1.4f).head(0.12f, -0.1f, 0.05f).legs(0f, 0f, 0.05f, 0.05f, 0f)
			.at(3f).hold().rArm(-0.9f, -0.4f, 0.3f, 1.4f, 0.2f).head(0.25f, 0f, 0f)
			.at(SLAP_END + 2f).hold()
			// ---- the watch arm held straight out in front, so the camera can run along it from above; the other fist clenched
			.at(HAND_AT).lArm(-1.52f, 0.05f, 0f, 0.04f, 0f).rArm(0.05f, 0f, 0.15f, 0.35f, 0.1f).head(0.25f, 0f, 0f).legs(0f, 0f, 0.04f, 0.04f, 0f)
			.at(FACE_AT - 0.5f).hold().rArm(0.1f, 0f, 0.2f, 0.55f, 0.15f).shrug(-0.5f)
			// ---- the face: head tipped over to one side, straining, the shoulders up round the neck
			.at(FACE_AT).head(-0.2f, 0.25f, 0.35f).arms(0.1f, 0f, 0.32f, 0.5f, 0.15f).shrug(-0.7f)
			.at(ORANGE_AT).head(-0.26f, 0.3f, 0.44f).shrug(-1.1f).arms(0.14f, 0f, 0.36f, 0.75f, 0.2f)
			.at(HUNCH_AT - 0.5f).hold()
			// ---- hunched right over (about the hips: the torso stays on the legs), arms hanging, head hung, knees giving
			.at(HUNCH_AT).lean(0.62f).head(0.55f, 0f, 0f).arms(-0.3f, -0.08f, 0.12f, 0.5f, 0.1f).shrug(-0.4f).legs(-0.12f, 0f, 0.08f, 0.32f, 0f).drop(0.5f)
			.at(SIDE_AT).lean(0.72f).head(0.75f, 0f, 0f).arms(-0.22f, -0.06f, 0.1f, 0.4f, 0f)
			.at(CLAW_AT - 0.5f).hold()
			// ---- clawing up at the face, elbows high, head back screaming...
			// (v0.17.4: the claws low at the chest, the face clear behind them, as the show; they were up over the head)
			.at(CLAW_AT).lean(0.12f).head(-0.3f, 0f, 0.08f).rArm(-0.45f, -0.3f, 0.85f, 1.5f, -0.5f).lArm(-0.45f, 0.3f, -0.85f, 1.5f, 0.5f).shrug(-0.9f)
			.legs(-0.05f, 0f, 0.1f, 0.2f, 0f).drop(0.3f)
			.at(170.5f).hold()
			// ...then the furry head drops and roars at the camera, the claws coming down in front
			// (v0.17.4: the claws drop low and wide, into the bottom corners, so the face fills the lens; they swung up into it)
			.at(173.5f).lean(0.34f).head(0.3f, 0f, 0f).arms(-0.6f, -0.25f, 0.55f, 1.0f, -0.4f).shrug(-0.3f)
			.at(TRANSFORM_AT).hold();

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		if (t < 0f || t >= TRANSFORM_AT) {
			return;
		}
		LimbKeys.apply(m, POSE.sample(t));
		// the swelling: the whole body trembling, then heaving
		float shake = smooth((t - HAND_AT) / 30f);
		if (t < FACE_AT) {
			m.leftArm.xRot += 0.05f * shake * Mth.sin(t * 2.9f);
			m.leftArm.zRot += 0.04f * shake * Mth.cos(t * 3.7f);
			m.rightArm.xRot += 0.04f * shake * Mth.sin(t * 3.3f + 1f);
		} else if (t < HUNCH_AT) {
			m.head.xRot += 0.05f * Mth.sin(t * 2.3f);
		} else if (t < CLAW_AT) {
			m.head.xRot += 0.05f * Mth.sin(t * 0.9f);
		}
		LimbKeys.copyLayers(m);
	}

	// ================================================================ Wildmutt's bones (after the change)

	/** Wildmutt's hip joint (geo px, y up, +z back): the body pitches about it, so the hind legs stay planted. */
	private static final float HIP_Y = 17.5f, HIP_Z = 10f;

	/**
	 * After the change, from {@code WildmuttModel.setCustomAnimations}: Wildmutt's bones posed for the playing shot (absolute
	 * rotations in degrees over whatever the idle clip set; {@code bone} looks one up by name, null if missing). Does nothing unless
	 * this sequence is playing on {@code p}.
	 *
	 * <p>v1b: the body pitches about the hips, not the root's origin under the belly (v1 rearing and roaring sank the hind feet into
	 * the ground and lifted the front paws off it). Every limb is posed by its angle in the world ("global" below) and turned into the
	 * bone's own rotation by taking off its parents', so the hind legs stand straight or fold into a sit, and the paws stay flat.
	 */
	public static void poseBones(Player p, Function<String, GeoBone> bone) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.WILDMUTT.id());
		if (t < TRANSFORM_AT) {
			return;
		}
		// the body's pitch about the hips (+ nose up) and how far the hips drop (px); the chest / neck / head / jaw on top of it
		float pitch = 0, drop = 0, chest = 0, neck = 0, head = 0, jaw = -6, headY = 0;
		// limbs, world angles (degrees, + swings the far end forward): upper, middle, (the paw / foot stays flat unless set)
		float armR = 0, foreR = 0, armL = 0, foreL = 0, pawR = 0, pawL = 0, thighR = 0, shinR = 0, thighL = 0, shinL = 0;
		if (t < PAW_AT) {
			// the head in profile: snarling, sniffing round
			float u = t - HEAD_AT;
			neck = 8f;
			head = -4f + 4f * Mth.sin(u * 0.35f);
			headY = 10f * Mth.sin(u * 0.18f);
			jaw = -10f - 8f * Math.abs(Mth.sin(u * 0.45f));
			armR = armL = 4f;
		} else if (t < BACK_AT) {
			// a paw lifting, reaching forward and planting, then the other; the chest dipping toward the lifted side
			float u = (t - PAW_AT) / 14f;
			float ph = u - (int) u;
			float lift = Mth.sin(Mth.clamp(ph / 0.7f, 0f, 1f) * Mth.PI);
			boolean right = ((int) u) % 2 == 0;
			float a = 30f * lift + 6f, f = -38f * lift, pw = -14f * lift;
			if (right) {
				armR = a;
				foreR = a + f;
				pawR = pw;
				armL = -4f;
			} else {
				armL = a;
				foreL = a + f;
				pawL = pw;
				armR = -4f;
			}
			pitch = -3f;
			chest = -2f;
			neck = -10f;
			jaw = -8f;
		} else if (t < LOW_AT) {
			pitch = -3f;
			neck = -12f;
			armR = armL = 4f;
		} else if (t < REAR_AT) {
			// the head low, prowling: shoulders down between the front legs
			pitch = -6f;
			drop = 0.5f;
			chest = -4f;
			neck = -22f;
			head = -6f;
			jaw = -16f;
			armR = armL = 14f;
			foreR = foreL = -6f;
		} else if (t < LUNGE_AT) {
			// rearing up on the hind legs, the front claws curled up in front of the chest
			float w = smooth((t - REAR_AT) / 5f);
			pitch = 44f * w;
			drop = 0.8f * w;
			neck = -8f * w;
			head = 4f * w;
			jaw = -10f - 22f * w;
			armR = 80f * w;
			armL = 66f * w;
			foreR = 10f * w;
			foreL = -4f * w;
			pawR = pawL = -60f * w;
			thighR = thighL = 8f * w;
			shinR = shinL = -8f * w;
		} else if (t < ROAR_AT) {
			// dropping into a lunge, nose down, the front paws thrown forward
			float w = smooth((t - LUNGE_AT) / 3f);
			pitch = Mth.lerp(w, 44f, -12f);
			drop = Mth.lerp(w, 0.8f, 1.5f);
			neck = 8f;
			head = 16f;
			jaw = -34f;
			armR = armL = Mth.lerp(w, 70f, 36f);
			foreR = foreL = Mth.lerp(w, 0f, 22f);
			pawR = pawL = 0f;
			thighR = thighL = Mth.lerp(w, 8f, -22f);
			shinR = shinL = Mth.lerp(w, -8f, 10f);
		} else {
			// on the slope, sitting back on the haunches, the front paws planted out ahead, the head tipped back roaring up at the sky
			// (final-pose rework, the show's t 310: the snout up at about 60 degrees, not straight up; the jaw wide open; the front legs
			// reaching forward onto the slope, not hanging straight)
			float w = smooth((t - ROAR_AT) / 6f);
			float roar = 0.5f + 0.5f * Mth.sin((t - ROAR_AT) * 0.12f);
			pitch = 22f * w;
			drop = 5.5f * w;
			chest = 6f * w;
			neck = 22f * w;
			head = (12f + 6f * roar) * w;
			jaw = -(40f + 10f * roar) * w - 3f * Mth.sin(t * 2.4f);
			armR = armL = 16f * w;
			foreR = foreL = 8f * w;
			thighR = thighL = 30f * w;
			shinR = shinL = -34f * w;
		}
		// the body about the hips: the root's pitch, and the shift that keeps the hip joint where it was (then the drop)
		float a = pitch * Mth.DEG_TO_RAD, c = Mth.cos(a), s = Mth.sin(a);
		GeoBone r = bone.apply("root");
		if (r != null) {
			r.setRotX(a);
			r.setRotY(0f);
			r.setPosY(HIP_Y * (1f - c) + HIP_Z * s - drop);
			r.setPosZ(HIP_Z * (1f - c) - HIP_Y * s);
		}
		set(bone.apply("chest"), chest, 0f);
		set(bone.apply("neck"), neck, 0f);
		set(bone.apply("look"), 0f, headY);
		set(bone.apply("head"), head, 0f);
		set(bone.apply("jaw"), jaw, 0f);
		// the front legs hang from the chest (pitch + chest), the hind legs from the hips (pitch)
		float base = pitch + chest;
		limb(bone, "arm_r", "forearm_r", "paw_r", base, armR, foreR, pawR);
		limb(bone, "arm_l", "forearm_l", "paw_l", base, armL, foreL, pawL);
		limb(bone, "thigh_r", "shin_r", "foot_r", pitch, thighR, shinR, 0f);
		limb(bone, "thigh_l", "shin_l", "foot_l", pitch, thighL, shinL, 0f);
	}

	/** A three-bone limb from world angles (degrees): each bone gets its world angle minus its parent's. */
	private static void limb(Function<String, GeoBone> bone, String upper, String middle, String end, float parent, float gUpper, float gMiddle, float gEnd) {
		set(bone.apply(upper), gUpper - parent, 0f);
		set(bone.apply(middle), gMiddle - gUpper, 0f);
		set(bone.apply(end), gEnd - gMiddle, 0f);
	}

	private static void set(GeoBone b, float xDeg, float yDeg) {
		if (b != null) {
			b.setRotX(xDeg * Mth.DEG_TO_RAD);
			b.setRotY(yDeg * Mth.DEG_TO_RAD);
		}
	}

	// ================================================================ the swelling, the veins and the fur

	/** Swelling lumps {bone, x, y, z, half-size, erupt}: the hand round the dial, up the arm, the chest and the face. */
	private static final float[][] LUMPS = lumps();

	private static float[][] lumps() {
		java.util.List<float[]> out = new java.util.ArrayList<>();
		long seed = 0x3A11D0L;
		int n = 0;
		// {bone, x0, x1, y0, y1, z0, z1, count, from, to, size}
		float[][] spans = { { 0, -1, 3, 6, 10, -2, 2, 8, 46, 62, 0.85f }, { 0, -1, 3, -2, 6, -2, 2, 9, 64, 92, 1.0f }, { 1, 0, 4, 0, 9, -2, 2, 8, 88, 104, 1.3f },
				{ 2, -4, 4, -8, 0, -4, 4, 14, 100, 124, 1.15f } };
		for (float[] sp : spans) {
			for (int i = 0; i < (int) sp[7]; i++, n++) {
				float f = (i + ShowMath.hash(seed, n * 5)) / sp[7];
				float x = Mth.lerp(ShowMath.hash(seed, n * 5 + 1), sp[1], sp[2]);
				// along the arm, from the hand (y high) up toward the shoulder; on the head, from the jaw up
				float y = Mth.lerp(f, sp[4], sp[3]);
				float z = Mth.lerp(ShowMath.hash(seed, n * 5 + 2), sp[5], sp[6]);
				float size = sp[10] * (0.8f + 0.4f * ShowMath.hash(seed, n * 5 + 3));
				// onto the nearest side face, sunk into it so it swells out of the skin (v1 stood whole cubes off it)
				float cx = (sp[1] + sp[2]) / 2f, cz = (sp[5] + sp[6]) / 2f, sink = size * 0.4f;
				if (Math.abs(x - cx) / (sp[2] - sp[1]) > Math.abs(z - cz) / (sp[6] - sp[5])) {
					x = x > cx ? sp[2] - sink : sp[1] + sink;
				} else {
					z = z > cz ? sp[6] - sink : sp[5] + sink;
				}
				out.add(new float[] { sp[0], x, y, z, size, Mth.lerp(f, sp[8], sp[9]) });
			}
		}
		return out.toArray(new float[0][]);
	}

	/** Red veins crawling up the watch arm and over the face: {bone (0 arm, 2 head), face, x0, y0, x1, y1 (in-face px), erupt}. */
	private static final float[][] VEINS = veins();

	private static float[][] veins() {
		java.util.List<float[]> out = new java.util.ArrayList<>();
		long seed = 0x7E1A5L;
		int n = 0;
		// arm: wandering lines up the front, top and outer faces, from the dial toward the shoulder
		int[] armFaces = { Shards.NZ, Shards.PX, Shards.PZ, Shards.NX };
		for (int v = 0; v < 6; v++) {
			int face = armFaces[v % 4];
			float a = 0.2f + 0.6f * ShowMath.hash(seed, n++), y = 9.6f;
			for (int k = 0; k < 6; k++) {
				float a2 = Mth.clamp(a + (ShowMath.hash(seed, n++) - 0.5f) * 0.5f, 0.08f, 0.92f), y2 = y - 1.4f - 0.8f * ShowMath.hash(seed, n++);
				out.add(new float[] { 0, face, a, y, a2, y2, 48f + (9.6f - y) * 3.2f + v });
				a = a2;
				y = y2;
			}
		}
		// face: veins fanning up the cheeks from the jaw
		for (int v = 0; v < 5; v++) {
			float a = 0.15f + 0.7f * ShowMath.hash(seed, n++), y = -0.5f;
			for (int k = 0; k < 4; k++) {
				float a2 = Mth.clamp(a + (ShowMath.hash(seed, n++) - 0.5f) * 0.3f, 0.05f, 0.95f), y2 = y - 1.3f - 0.6f * ShowMath.hash(seed, n++);
				out.add(new float[] { 2, Shards.NZ, a, y, a2, y2, 102f + k * 3.5f + v });
				a = a2;
				y = y2;
			}
		}
		return out.toArray(new float[0][]);
	}

	/** Fur tufts {bone (0 left arm, 1 body, 2 head, 3 right arm), face, x, y, z, angle, erupt}, bursting out at the orange flash. */
	private static final float[][] FUR = fur();

	private static float[][] fur() {
		java.util.List<float[]> out = new java.util.ArrayList<>();
		long seed = 0x7F0A2L;
		int n = 0;
		for (int i = 0; i < 46; i++, n += 4) {
			float h1 = ShowMath.hash(seed, n), h2 = ShowMath.hash(seed, n + 1), h3 = ShowMath.hash(seed, n + 2);
			int bone, face;
			float x, y, z;
			if (i < 24) {
				// the head: all round, the top shaggiest
				bone = 2;
				int f = i % 6;
				face = f < 2 ? Shards.TOP : f == 2 ? Shards.PX : f == 3 ? Shards.NX : f == 4 ? Shards.PZ : Shards.NZ;
				x = face == Shards.PX ? 4.55f : face == Shards.NX ? -4.55f : -3.6f + 7.2f * h1;
				z = face == Shards.PZ ? 4.55f : face == Shards.NZ ? -4.55f : -3.6f + 7.2f * h2;
				y = face == Shards.TOP ? -8.55f : face == Shards.NZ ? -8f + 3f * h2 : -8f + 7f * h2;
			} else if (i < 40) {
				// the arms, out of the sleeves: outer and back faces, down to the wrist
				bone = i < 32 ? 0 : 3;
				boolean outer = i % 2 == 0;
				face = outer ? (bone == 0 ? Shards.PX : Shards.NX) : Shards.PZ;
				x = outer ? (bone == 0 ? 3.3f : -3.3f) : (bone == 0 ? 1f : -1f) + (h1 - 0.5f) * 2.6f;
				z = outer ? -1.6f + 3.2f * h2 : 2.3f;
				y = -1.4f + 10.8f * h3;
			} else {
				// the back of the neck, over the collar
				bone = 1;
				face = Shards.PZ;
				x = -3.6f + 7.2f * h1;
				y = 0.6f + 3f * h2;
				z = 2.3f;
			}
			float erupt = (bone == 2 ? 134f : bone == 1 ? 142f : 138f) + 14f * h3;
			out.add(new float[] { bone, face, x, y, z, (h1 - 0.5f) * 50f, erupt });
		}
		return out.toArray(new float[0][]);
	}

	private static final int FUR_LIGHT = 0xFFC060, FUR_MID = 0xF08A1E, FUR_DARK = 0xA8500E, VEIN = 0xB8201A;

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < HAND_AT - 1f || t >= TRANSFORM_AT) {
			return;
		}
		int light = LightTexture.FULL_BRIGHT;
		boolean slim = p.getSkin().model() == PlayerSkin.Model.SLIM;
		float xMax = slim ? 2f : 3f;
		ModelPart[] bones = { model.leftArm, model.body, model.head, model.rightArm };
		int[] limbs = { BentLimbs.LEFT_ARM, -1, -1, BentLimbs.RIGHT_ARM };
		if (t < HUNCH_AT) {
			// the lumps, then their glow (lumps below an elbow ride the forearm)
			// (one buffer at a time: fetching another ends the one before)
			for (int pass = 0; pass < 2; pass++) {
				VertexConsumer vc = buffers.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(SwellLumps.SWELL) : RenderType.eyes(SwellLumps.SWELL_GLOW));
				for (int i = 0; i < LUMPS.length; i++) {
					float[] b = LUMPS[i];
					float g = ShowMath.pop(t - b[5], 6f);
					if (g <= 0.02f) {
						continue;
					}
					float r = b[4] * g * (pass == 0 ? 1f : 1.18f) * (1f + 0.09f * Mth.sin(t * (0.8f + 0.05f * i) + i * 1.7f));
					int bi = (int) b[0];
					int c = pass == 0 ? 0xFFFFFFFF : ShowMath.grey(0.5f + 0.2f * Mth.sin(t * 1.3f + i));
					Runnable draw = () -> SwellLumps.lump(pose, vc, b[1], b[2], b[3], r, c, light);
					if (limbs[bi] >= 0) {
						Shards.onLimb(pose, bones[bi], limbs[bi], slim, b[2], draw);
					} else {
						AlienParts.on(pose, bones[bi], draw);
					}
				}
			}
			// the red veins crawling under the skin
			VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(Shards.WHITE));
			for (float[] v : VEINS) {
				float g = smooth((t - v[6]) / 4f);
				if (g <= 0.01f) {
					continue;
				}
				int bi = (int) v[0], face = (int) v[1];
				float[] a = facePoint(bi, face, v[2], v[3], xMax), e = facePoint(bi, face, v[4], Mth.lerp(g, v[3], v[5]), xMax);
				Runnable draw = () -> vein(pose.last(), solid, face, a, e, light);
				if (bi == 0) {
					Shards.onLimb(pose, model.leftArm, BentLimbs.LEFT_ARM, slim, (v[3] + v[5]) * 0.5f, draw);
				} else {
					AlienParts.on(pose, model.head, draw);
				}
			}
		}
		if (t >= ORANGE_AT) {
			// the fur bursting out in spiky tufts, hanging down and back
			VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(Shards.WHITE));
			for (float[] f : FUR) {
				float g = ShowMath.pop(t - f[6], 5f);
				if (g <= 0.02f) {
					continue;
				}
				int bi = (int) f[0], face = (int) f[1];
				float len = bi == 2 ? 3.0f : 2.4f;
				Runnable draw = () -> Shards.draw(pose, solid, face, f[2], f[3], f[4], len, 0.95f, 1.5f, f[5], 0.95f, g, light, FUR_LIGHT, FUR_MID, FUR_DARK);
				if (limbs[bi] >= 0) {
					Shards.onLimb(pose, bones[bi], limbs[bi], slim, f[3], draw);
				} else {
					AlienParts.on(pose, bones[bi], draw);
				}
			}
		}
	}

	/** A point on one of the arm's / head's faces ({@code across} 0..1 over the face, {@code y} bone px), 0.3 px proud of the skin. */
	private static float[] facePoint(int bone, int face, float across, float y, float xMax) {
		if (bone == 2) {
			return new float[] { -4f + 8f * across, y, -4.56f };
		}
		return switch (face) {
			case Shards.PX -> new float[] { xMax + 0.32f, y, -2f + 4f * across };
			case Shards.NX -> new float[] { -1.32f, y, -2f + 4f * across };
			case Shards.PZ -> new float[] { -1f + (xMax + 1f) * across, y, 2.32f };
			default -> new float[] { -1f + (xMax + 1f) * across, y, -2.32f };
		};
	}

	/** A thin red line on a face from {@code a} to {@code b}. */
	private static void vein(PoseStack.Pose p, VertexConsumer vc, int face, float[] a, float[] b, int light) {
		float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
		// across the line, in the face's plane
		float wx, wy, wz;
		if (face == Shards.PX || face == Shards.NX) {
			float l = Mth.sqrt(dy * dy + dz * dz) + 1e-4f;
			wx = 0;
			wy = -dz / l * 0.16f;
			wz = dy / l * 0.16f;
		} else {
			float l = Mth.sqrt(dx * dx + dy * dy) + 1e-4f;
			wx = -dy / l * 0.16f;
			wy = dx / l * 0.16f;
			wz = 0;
		}
		Shards.quad(p, vc, new float[] { a[0] - wx, a[1] - wy, a[2] - wz }, new float[] { a[0] + wx, a[1] + wy, a[2] + wz },
				new float[] { b[0] + wx, b[1] + wy, b[2] + wz }, new float[] { b[0] - wx, b[1] - wy, b[2] - wz }, VEIN, light);
	}

	@Override
	public void bulge(Player p, HumanoidModel<?> m, float t) {
		if (t >= TRANSFORM_AT) {
			return;
		}
		// the swollen arm and head; the hybrid's head and shoulders bulking up
		float throb = 1f + 0.05f * Mth.sin(t * 1.7f);
		float arm = 1f + 0.22f * smooth((t - HAND_AT) / 40f) * throb;
		float head = 1f + 0.1f * smooth((t - FACE_AT) / 20f) * throb + 0.16f * smooth((t - ORANGE_AT) / 20f);
		m.leftArm.xScale = m.leftArm.zScale = arm;
		m.head.xScale = m.head.yScale = m.head.zScale = head;
		m.hat.copyFrom(m.head);
		if (m instanceof PlayerModel<?> pm) {
			pm.leftSleeve.copyFrom(m.leftArm);
		}
	}

	@Override
	public void renderWorldFx(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial) {
		ShowShots.dialLight(pose, buffers, cam, p, t, OmnitrixSequenceClient.seed(p), 4f, 40f);
		ShowShots.bodyGlow(pose, buffers, cam, p, t, partial, ORANGE_AT, HUNCH_AT - 4f, 0xE0701A, 0xFFE09A);
	}

	// ================================================================ the holder's screen

	@Override
	public void drawStage(Stage st, float t) {
		if (t < SLAP_END) {
			st.gradient(0x9A8A64, 0x6A5C40, 0x2E2818);
		} else if (t < HAND_AT) {
			st.gradient(0xF2FFE0, 0x7CEB4C, 0x2DB51C);
		} else if (t < FACE_AT) {
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - HAND_AT, DARK_DOTS);
		} else if (t < HUNCH_AT) {
			st.gradient(0xF4E4C8, 0xD0A880, 0x7A5A3A);
		} else if (t < STREAK_AT || (t >= HEAD_AT && t < WHITE_AT + 4f)) {
			// olive and black splotches, a hot yellow glow low in the frame
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - TRACK.shotStart(t), SPLOTCH_DOTS);
			VertexConsumer vc = st.flat();
			st.rect(vc, st.last(), -1.5f, -1.5f, 1.5f, -0.55f, 0xFFB21E, 0.55f, 0.15f);
		} else if (t < HEAD_AT) {
			st.fill(0x080404, 1f);
		} else {
			// the roar: the show's pale khaki sky with black blobs (final-pose rework; it was the green swirl), the pale slope underfoot
			st.gradient(0xCCC7A0, 0xB4AE8A, 0x7E7A60);
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - ROAR_AT, ROAR_DOTS);
			VertexConsumer vc = st.flat();
			PoseStack.Pose p = st.last();
			st.put(vc, p, -1.5f, -0.28f, 0xE2DCB4, 1f);
			st.put(vc, p, -1.5f, -1.5f, 0xB8B088, 1f);
			st.put(vc, p, 1.5f, -1.5f, 0xB8B088, 1f);
			st.put(vc, p, 1.5f, -0.92f, 0xE2DCB4, 1f);
			// the slope's ink edge against the pale sky
			Shards.line(st, vc, p, -1.5f, -0.28f, 1.5f, -0.92f, 0.005f, 0x4A4632, 0.85f);
			for (int i = 0; i < 7; i++) {
				float y0 = -0.42f - i * 0.11f;
				Shards.line(st, vc, p, -1.2f + i * 0.08f, y0, -0.2f + i * 0.12f, y0 - 0.22f, 0.004f, 0x8A845E, 0.6f);
			}
		}
	}

	@Override
	public void drawOverlay(Stage st, float t) {
		ShowShots.slapFlash(st, t, 4f);
		ShowShots.flash(st, t, ShowShots.FLASH_GREEN, 6f, 1f, 24f, 4f, 1f);
		ShowShots.dialBloom(st, t, DIAL_AT, HAND_AT);
		if (t >= ORANGE_AT - 2f && t < HUNCH_AT + 2f) {
			orange(st, t);
		}
		if (t >= STREAK_AT && t < HEAD_AT + 2f) {
			streaks(st, t);
		}
		ShowShots.flash(st, t, 0xFFFFFF, WHITE_AT, 1f, 276f, 22f, 1f);
		ShowShots.greenOut(st, t, GREEN_AT, WORLD_AT, total());
	}

	/** Glossy orange fur filling the frame: a hot centre, a white sheen sliding across. */
	private static void orange(Stage st, float t) {
		float a = env(t, ORANGE_AT - 2f, 2f, HUNCH_AT - 1f, 2.5f);
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		st.radial(vc, p, 0xFFE07A, 0xFF8C1A, 0xB83A0A, a, a);
		float u = t - ORANGE_AT;
		float x = -0.9f + 0.09f * u;
		Shards.line(st, vc, p, x - 0.5f, -1.2f, x + 0.3f, 1.2f, 0.16f, 0xFFF4C8, 0.55f * a);
		Shards.line(st, vc, p, x - 0.5f, -1.2f, x + 0.3f, 1.2f, 0.05f, 0xFFFFFF, 0.8f * a);
		Shards.line(st, vc, p, x + 0.4f, -1.2f, x + 1.2f, 1.2f, 0.035f, 0xC8280A, 0.6f * a);
	}

	/** Orange-yellow streaks tearing across black, then the orange of his hide. */
	private static void streaks(Stage st, float t) {
		VertexConsumer vc = st.flat();
		PoseStack.Pose p = st.last();
		float a = env(t, STREAK_AT, 0.5f, HEAD_AT - 0.5f, 1.5f);
		st.fill(vc, p, 0x0A0604, a);
		long seed = st.seed + 77;
		float u = t - STREAK_AT;
		for (int i = 0; i < 18; i++) {
			float x = (ShowMath.hash(seed, i) * 3.2f - 1.6f) + u * (0.12f + 0.06f * ShowMath.hash(seed, i + 40));
			x = ((x + 1.6f) % 3.2f) - 1.6f;
			float y = ShowMath.hash(seed, i + 20) * 2.4f - 1.2f;
			float len = 0.3f + 0.4f * ShowMath.hash(seed, i + 60);
			int c = i % 3 == 0 ? 0xFFF09A : i % 3 == 1 ? 0xFFB01E : 0xE86A10;
			Shards.line(st, vc, p, x, y, x + len * 0.35f, y + len, 0.02f + 0.03f * ShowMath.hash(seed, i + 80), c, 0.9f * a);
		}
		float hide = env(t, 185f, 2f, HEAD_AT, 2f);
		if (hide > 0f) {
			st.fill(vc, p, 0xE8861E, hide);
		}
	}
}
