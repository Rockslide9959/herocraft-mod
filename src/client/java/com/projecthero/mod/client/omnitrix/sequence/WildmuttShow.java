package com.projecthero.mod.client.omnitrix.sequence;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import software.bernie.geckolib.cache.object.GeoBone;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.env;
import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.rot;
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
 */
public final class WildmuttShow extends ShowSequenceClient {
	static final float SLAP_END = 6f, DIAL_AT = 25f, HAND_AT = 45f, ARM_AT = 66f, FACE_AT = 100f, ORANGE_AT = 128f, HUNCH_AT = 148f, SIDE_AT = 158f,
			CLAW_AT = 166f, STREAK_AT = 178f, HEAD_AT = 188f, PAW_AT = 210f, BACK_AT = 238f, LOW_AT = 244f, REAR_AT = 250f, LUNGE_AT = 262f,
			WHITE_AT = 268f, ROAR_AT = 270f, GREEN_AT = 322f, WORLD_AT = 338f;
	static final int TRANSFORM_AT = 180;

	private static final ResourceLocation FUR = ProjectHeroMod.id("textures/entity/omnitrix/sequence/wildmutt/fur.png");

	private static final KirbyDots.Style DARK_DOTS = KirbyDots.Style.show().size(1.2f),
			SPLOTCH_DOTS = KirbyDots.Style.show().bands(3).density(1.3f).size(1.5f).width(1.4f).rim(1.2f).clear(0.34f).flow(0.7f),
			ROAR_DOTS = KirbyDots.Style.show().bands(2).density(1.3f).size(1.6f).width(1.4f).rim(1.2f).clear(0.45f).angle(-25f).flow(0.5f);

	private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(ShowShots.slapShot(0f, SLAP_END), ShowShots.dialCloseUp(SLAP_END + 0.01f, HAND_AT),
			new float[][] {
					// the hand from above
					{ 45.01f, 0.28f, -60f, 1.75f, 1.22f, -0.33f, 0.66f },
					{ 66f, 0.26f, -70f, 1.75f, 1.22f, -0.33f, 0.62f },
					// up the forearm to the shoulder, from above at his left
					{ 66.01f, 0.42f, -84f, 1.8f, 1.22f, -0.33f, 0.5f },
					{ 90f, 0.42f, -88f, 1.8f, 1.25f, -0.33f, 0.22f },
					{ 100f, 0.45f, -95f, 1.82f, 1.3f, -0.33f, 0.04f },
					// the face, tilted and close
					{ 100.01f, 0.85f, -30f, 1.64f, 1.66f, -0.06f, 0f },
					{ 128f, 0.62f, -22f, 1.66f, 1.68f, -0.06f, 0f },
					// (under the orange)
					{ 128.01f, 0.36f, -20f, 1.52f, 1.62f, -0.02f, 0f },
					{ 148f, 0.36f, -20f, 1.52f, 1.62f, -0.02f, 0f },
					// hunched, from behind and above
					{ 148.01f, 1.25f, -145f, 1.75f, 1.15f, 0f, 0.05f },
					{ 158f, 1.1f, -138f, 1.7f, 1.15f, 0f, 0.05f },
					// side on, head hung
					{ 158.01f, 1.0f, -82f, 1.25f, 1.3f, 0f, 0.15f },
					{ 166f, 0.92f, -78f, 1.25f, 1.3f, 0f, 0.15f },
					// from in front, low
					{ 166.01f, 1.0f, 8f, 1.0f, 1.38f, 0f, 0.15f },
					{ 178f, 0.75f, 4f, 1.05f, 1.4f, 0f, 0.15f },
					// (under the streaks)
					{ 178.01f, 0.9f, 80f, 1.15f, 1.1f, 0f, 0.95f },
					{ 188f, 0.9f, 80f, 1.15f, 1.1f, 0f, 0.95f },
					// Wildmutt's head in profile
					{ 188.01f, 1.75f, 80f, 1.15f, 0.95f, 0f, 0.65f },
					{ 210f, 1.6f, 74f, 1.1f, 0.95f, 0f, 0.7f },
					// low on the front paws
					{ 210.01f, 1.35f, -62f, 0.2f, 0.32f, 0f, 0.6f },
					{ 238f, 1.4f, -50f, 0.22f, 0.36f, 0f, 0.5f },
					// his back passing close
					{ 238.01f, 0.85f, -110f, 1.75f, 1.3f, 0f, -0.2f },
					{ 244f, 0.8f, -140f, 1.75f, 1.3f, 0f, -0.4f },
					// the head low, side on
					{ 244.01f, 1.6f, 92f, 0.85f, 0.8f, 0f, 0.7f },
					{ 250f, 1.5f, 88f, 0.85f, 0.8f, 0f, 0.7f },
					// rearing up, from low in front
					{ 250.01f, 2.3f, 12f, 0.35f, 1.35f, 0f, 0.3f },
					{ 262f, 2.1f, 8f, 0.35f, 1.4f, 0f, 0.3f },
					// the lunge
					{ 262.01f, 2.4f, 18f, 0.3f, 0.6f, 0f, 0.5f },
					{ 268f, 1.7f, 12f, 0.3f, 0.6f, 0f, 0.6f },
					// the roar on the slope, side on and low
					{ 268.01f, 3.2f, -72f, 0.55f, 1.15f, 0f, 0.25f },
					{ 338f, 2.95f, -70f, 0.55f, 1.15f, 0f, 0.25f } }))
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

	@Override
	public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) {
		if (t >= TRANSFORM_AT) {
			return;
		}
		m.head.yRot = 0f;
		if (t >= HAND_AT && t < FACE_AT) {
			// the watch arm held straight out in front, so the camera can run along it from above
			rot(m.leftArm, 1f, -1.52f, 0.05f, 0f);
		}
		float shake = smooth((t - HAND_AT) / 30f);
		if (shake > 0f && t < FACE_AT) {
			m.leftArm.xRot += 0.05f * shake * Mth.sin(t * 2.9f);
			m.leftArm.zRot += 0.04f * shake * Mth.cos(t * 3.7f);
			m.rightArm.xRot += 0.04f * shake * Mth.sin(t * 3.3f + 1f);
		}
		if (t >= FACE_AT && t < HUNCH_AT) {
			// the face: head tipped over to one side, straining
			float w = smooth((t - FACE_AT + 2f) / 6f);
			rot(m.head, w, -0.2f + 0.05f * Mth.sin(t * 2.3f), 0.25f, 0.35f);
			rot(m.leftArm, w, 0.1f, 0f, -0.35f);
			rot(m.rightArm, w, 0.1f, 0f, 0.35f);
		} else if (t >= HUNCH_AT && t < CLAW_AT) {
			// hunched right over, the arms hanging, the head hung
			float w = smooth((t - HUNCH_AT) / 4f);
			float heave = 0.05f * Mth.sin(t * 0.9f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, 0.62f + heave);
			rot(m.head, w, 0.75f, 0f, 0f);
			rot(m.leftArm, w, -0.75f, 0.1f, -0.2f);
			rot(m.rightArm, w, -0.75f, -0.1f, 0.2f);
			// the body's lean pivots at the waist: the shoulders and the head follow it down and forward
			float dz = Mth.sin(m.body.xRot) * 12f, dy = (1f - Mth.cos(m.body.xRot)) * 12f;
			m.head.z -= dz * 0.9f;
			m.head.y += dy * 0.9f;
			m.leftArm.z -= dz * 0.8f;
			m.rightArm.z -= dz * 0.8f;
			m.leftArm.y += dy * 0.8f;
			m.rightArm.y += dy * 0.8f;
		} else if (t >= CLAW_AT) {
			// clawing up at the face, head back screaming; then the furry head drops and roars at the camera
			float w = smooth((t - CLAW_AT) / 2.5f);
			float drop = smooth((t - 171f) / 3f);
			rot(m.head, w, Mth.lerp(drop, -0.45f, 0.35f), 0f, 0f);
			rot(m.leftArm, w, Mth.lerp(drop, -2.05f, -1.1f), Mth.lerp(drop, 0.45f, 0.1f), -0.1f);
			rot(m.rightArm, w, Mth.lerp(drop, -2.05f, -1.1f), Mth.lerp(drop, -0.45f, -0.1f), 0.1f);
			m.body.xRot = Mth.lerp(w, m.body.xRot, Mth.lerp(drop, 0.05f, 0.3f));
			float b = m.body.xRot;
			float dz = Mth.sin(b) * 12f, dy = (1f - Mth.cos(b)) * 12f;
			m.head.z -= dz * 0.9f;
			m.head.y += dy * 0.9f;
			m.leftArm.z -= dz * 0.8f;
			m.rightArm.z -= dz * 0.8f;
		}
		HeatblastShow.copyLayers(m);
	}

	/**
	 * After the change, from {@code WildmuttModel.setCustomAnimations}: Wildmutt's bones posed for the playing shot (absolute
	 * rotations in degrees over whatever the idle clip set; {@code bone} looks one up by name, null if missing). Does nothing unless
	 * this sequence is playing on {@code p}.
	 */
	public static void poseBones(Player p, Function<String, GeoBone> bone) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = OmnitrixSequenceClient.age(p, partial, Aliens.WILDMUTT.id());
		if (t < TRANSFORM_AT) {
			return;
		}
		// {root, chest, neck, head, jaw, arm_r, forearm_r, arm_l, forearm_l} in degrees, root y (px)
		float root = 0, chest = 0, neck = 0, head = 0, jaw = -6, armR = 0, foreR = 0, armL = 0, foreL = 0, rootY = 0, headY = 0;
		if (t < PAW_AT) {
			// the head in profile: snarling, sniffing round
			float u = t - HEAD_AT;
			neck = 8f;
			head = -4f + 4f * Mth.sin(u * 0.35f);
			headY = 10f * Mth.sin(u * 0.18f);
			jaw = -10f - 8f * Math.abs(Mth.sin(u * 0.45f));
		} else if (t < BACK_AT) {
			// a paw lifting, reaching and planting, then the other
			float u = (t - PAW_AT) / 14f;
			float ph = u - (int) u;
			float lift = Mth.sin(Mth.clamp(ph / 0.7f, 0f, 1f) * Mth.PI);
			boolean right = ((int) u) % 2 == 0;
			float a = 38f * lift, f = -40f * lift;
			if (right) {
				armR = a;
				foreR = f;
			} else {
				armL = a;
				foreL = f;
			}
			chest = -4f;
			neck = -10f;
		} else if (t < LOW_AT) {
			chest = -4f;
			neck = -12f;
		} else if (t < REAR_AT) {
			// the head low, prowling
			chest = -8f;
			neck = -22f;
			head = -6f;
			jaw = -14f;
		} else if (t < LUNGE_AT) {
			// rearing up, the claws raised
			float w = smooth((t - REAR_AT) / 5f);
			root = 38f * w;
			rootY = 2f * w;
			armR = 70f * w;
			armL = 55f * w;
			foreR = -35f * w;
			foreL = -50f * w;
			neck = 10f * w;
			head = 12f * w;
			jaw = -10f - 20f * w;
		} else if (t < ROAR_AT) {
			// dropping into a lunge
			float w = smooth((t - LUNGE_AT) / 3f);
			root = Mth.lerp(w, 38f, -10f);
			rootY = Mth.lerp(w, 2f, -2.5f);
			armR = armL = Mth.lerp(w, 60f, 30f);
			foreR = foreL = -20f;
			neck = 10f;
			head = 18f;
			jaw = -32f;
		} else {
			// on the slope, sitting back, head flung up, roaring at the sky
			float w = smooth((t - ROAR_AT) / 6f);
			float roar = 0.5f + 0.5f * Mth.sin((t - ROAR_AT) * 0.12f);
			root = 22f * w;
			rootY = -1f * w;
			chest = 8f * w;
			neck = 32f * w;
			head = (26f + 6f * roar) * w;
			jaw = -(30f + 14f * roar) * w - 3f * Mth.sin(t * 2.4f);
			armR = armL = -22f * w;
			foreR = foreL = 6f * w;
		}
		set(bone.apply("root"), root, 0f);
		GeoBone r = bone.apply("root");
		if (r != null) {
			r.setPosY(rootY);
		}
		set(bone.apply("chest"), chest, 0f);
		set(bone.apply("neck"), neck, 0f);
		set(bone.apply("look"), 0f, headY);
		set(bone.apply("head"), head, 0f);
		set(bone.apply("jaw"), jaw, 0f);
		set(bone.apply("arm_r"), armR, 0f);
		set(bone.apply("forearm_r"), foreR, 0f);
		set(bone.apply("arm_l"), armL, 0f);
		set(bone.apply("forearm_l"), foreL, 0f);
	}

	private static void set(GeoBone b, float xDeg, float yDeg) {
		if (b != null) {
			b.setRotX(xDeg * Mth.DEG_TO_RAD);
			b.setRotY(yDeg * Mth.DEG_TO_RAD);
		}
	}

	// ================================================================ the swelling and the fur

	/** Swelling lumps ({@link SwellLumps} rows): the hand round the dial, up the arm, the chest and the face. */
	private static final float[][] LUMPS = lumps();

	private static float[][] lumps() {
		java.util.List<float[]> out = new java.util.ArrayList<>();
		long seed = 0x3A11D0L;
		int n = 0;
		// {bone, x0, x1, y0, y1, z0, z1, count, from, to, size}
		float[][] spans = { { 0, -1, 3, 6, 11, -2, 2, 12, 46, 62, 1.5f }, { 0, -1, 3, -2, 6, -2, 2, 12, 64, 92, 1.8f }, { 1, 0, 4, 0, 9, -2, 2, 8, 88, 104, 2f },
				{ 2, -4, 4, -8, 0, -4, 4, 14, 100, 124, 1.8f } };
		for (float[] s : spans) {
			for (int i = 0; i < (int) s[7]; i++, n++) {
				float f = (i + ShowMath.hash(seed, n * 5)) / s[7];
				float x = Mth.lerp(ShowMath.hash(seed, n * 5 + 1), s[1], s[2]);
				// along the arm, from the hand (y high) up toward the shoulder; on the head, from the jaw up
				float y = s[0] == 0 ? Mth.lerp(f, s[4], s[3]) : Mth.lerp(f, s[4], s[3]);
				float z = Mth.lerp(ShowMath.hash(seed, n * 5 + 2), s[5], s[6]);
				// push it out onto the nearest side face
				float cx = (s[1] + s[2]) / 2f, cz = (s[5] + s[6]) / 2f;
				if (Math.abs(x - cx) / (s[2] - s[1]) > Math.abs(z - cz) / (s[6] - s[5])) {
					x = x > cx ? s[2] : s[1];
				} else {
					z = z > cz ? s[6] : s[5];
				}
				out.add(new float[] { s[0], x, y, z, s[10] * (0.8f + 0.4f * ShowMath.hash(seed, n * 5 + 3)), Mth.lerp(f, s[8], s[9]) });
			}
		}
		return out.toArray(new float[0][]);
	}

	/** The fur ({@link HeatblastShow.Plates}): the head, the arms out of the sleeves, the back of the neck. */
	private static final float[][] FUR_PATCHES = { { 2, -4, 4, -8, 0, -4, 4, 44, 0, -4, 0, 136, 150, 1.25f, 0b11111 },
			{ 0, -1, 3, 4, 12, -2, 2, 14, 1, 12, 0, 140, 156, 1.2f, 0b1111 }, { 3, -3, 1, 4, 12, -2, 2, 14, -1, 12, 0, 140, 156, 1.2f, 0b1111 },
			{ 1, -4, 4, 0, 3, -2, 2, 8, 0, 0, 2, 142, 160, 1.3f, 0b1000 } };
	private static final float[][] FUR_PLATES = HeatblastShow.Plates.gen(0x7F0A2L, FUR_PATCHES);

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, PlayerModel<?> model, float t) {
		if (t < HAND_AT - 1f || t >= TRANSFORM_AT) {
			return;
		}
		ModelPart[] bones = { model.leftArm, model.body, model.head, model.rightArm };
		if (t < HUNCH_AT) {
			SwellLumps.render(pose, buffers, bones, LUMPS, t, 6f, SwellLumps.SWELL, SwellLumps.SWELL_GLOW);
		}
		if (t >= ORANGE_AT) {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FUR));
			HeatblastShow.Plates.render(pose, vc, bones, FUR_PLATES, t, 5f, 0.6f);
		}
	}

	@Override
	public void bulge(Player p, HumanoidModel<?> m, float t) {
		if (t >= TRANSFORM_AT) {
			return;
		}
		// the swollen arm and head; the hybrid's head and shoulders bulking up
		float throb = 1f + 0.05f * Mth.sin(t * 1.7f);
		float arm = 1f + 0.3f * smooth((t - HAND_AT) / 40f) * throb;
		float head = 1f + 0.12f * smooth((t - FACE_AT) / 20f) * throb + 0.18f * smooth((t - ORANGE_AT) / 20f);
		m.leftArm.xScale = m.leftArm.zScale = arm;
		m.head.xScale = m.head.yScale = m.head.zScale = head;
		m.hat.copyFrom(m.head);
		m.leftArm.x += (arm - 1f) * 1.5f;
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
			// the roar: olive and black, the pale slope underfoot
			st.showBackground();
			KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - ROAR_AT, ROAR_DOTS);
			VertexConsumer vc = st.flat();
			PoseStack.Pose p = st.last();
			st.put(vc, p, -1.5f, -0.28f, 0xE2DCB4, 1f);
			st.put(vc, p, -1.5f, -1.5f, 0xB8B088, 1f);
			st.put(vc, p, 1.5f, -1.5f, 0xB8B088, 1f);
			st.put(vc, p, 1.5f, -0.92f, 0xE2DCB4, 1f);
			for (int i = 0; i < 7; i++) {
				float y0 = -0.42f - i * 0.11f;
				HeatblastShow.line(st, vc, p, -1.2f + i * 0.08f, y0, -0.2f + i * 0.12f, y0 - 0.22f, 0.004f, 0x8A845E, 0.6f);
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
		HeatblastShow.line(st, vc, p, x - 0.5f, -1.2f, x + 0.3f, 1.2f, 0.16f, 0xFFF4C8, 0.55f * a);
		HeatblastShow.line(st, vc, p, x - 0.5f, -1.2f, x + 0.3f, 1.2f, 0.05f, 0xFFFFFF, 0.8f * a);
		HeatblastShow.line(st, vc, p, x + 0.4f, -1.2f, x + 1.2f, 1.2f, 0.035f, 0xC8280A, 0.6f * a);
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
			HeatblastShow.line(st, vc, p, x, y, x + len * 0.35f, y + len, 0.02f + 0.03f * ShowMath.hash(seed, i + 80), c, 0.9f * a);
		}
		float hide = env(t, 185f, 2f, HEAD_AT, 2f);
		if (hide > 0f) {
			st.fill(vc, p, 0xE8861E, hide);
		}
	}
}
