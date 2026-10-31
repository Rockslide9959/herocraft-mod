package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Stinkfly's client kit -- a basic model on the player body: the green insect skin, thin segmented limbs (the player's
 * arms and legs are hidden and redrawn 2 px thick on the same bones), FOUR EYES ON STALKS wobbling on the head, mandibles,
 * two pairs of translucent WINGS on the back (folded along the back on the ground, a fast beat in flight), and a segmented
 * abdomen with a STINGER that curls under the body for the stab. A pose for every move.
 */
public final class StinkflyClient implements AlienClientKit {
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final float[][] LIFT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.3f, 0f, 0.2f, -1.3f, 0f, -0.2f, 0.2f, 0f, -1f, 0f, 0.1f, -1f, 0f, -0.1f, 0.3f),
			QuirkKeyframes.f(8, -1f, 0f, 0.2f, -1f, 0f, -0.2f, 0.15f, 0f, -0.9f, 0f, 0.1f, -0.9f, 0f, -0.1f, 0.3f), QuirkKeyframes.rest(12) };
	private static final FirstPersonClip FP_LIFT = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(3, 30f, -15f, 0f, -0.05f, -0.3f, -0.1f),
			FirstPersonClip.f(8, 25f, -15f, 0f, -0.05f, -0.25f, -0.05f), FirstPersonClip.f(12, 0f, 0f, 0f, 0f, 0f, 0f)).both();
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/stinkfly.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/stinkfly_extras.png");

	// extras rectangles (px on the 64x64 sheet): u0, v0, u1, v1
	private static final float[] STALK = { 32, 0, 36, 8 };
	private static final float[] EYE = { 36, 0, 44, 8 };
	private static final float[] TAIL = { 32, 8, 40, 16 };
	private static final float[] STINGER = { 40, 8, 44, 12 };
	private static final float[] LIMB = { 44, 0, 48, 16 };
	private static final float[] MANDIBLE = { 48, 0, 52, 4 };

	// {tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX} (radians)
	private static final float[][] SPIT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.3f, 0, 0.25f, 0.3f, 0, -0.25f, 0.15f, 0, 0, 0, 0, 0, 0, 0, -0.35f),
			QuirkKeyframes.rest(9) };
	private static final float[][] NET = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.6f, 0, 0.9f, -0.6f, 0, -0.9f, 0.25f, 0, 0, 0, 0, 0, 0, 0, -0.45f),
			QuirkKeyframes.rest(14) };
	private static final float[][] GAS = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, 0.5f, 0, 0.35f, 0.5f, 0, -0.35f, 0.45f, 0, -0.3f, 0, 0, 0.2f, 0, 0, 0.4f),
			QuirkKeyframes.f(18, 0.5f, 0, 0.35f, 0.5f, 0, -0.35f, 0.45f, 0, -0.3f, 0, 0, 0.2f, 0, 0, 0.4f), QuirkKeyframes.rest(24) };
	private static final float[][] STAB = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -0.5f, 0, 0.45f, -0.5f, 0, -0.45f, 0.45f, 0, -0.45f, 0, 0, 0.35f, 0, 0, 0.2f), QuirkKeyframes.rest(12) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.25f, 0.9f, 0, -0.25f, 0.4f, 0, 0.35f, 0, 0, 0.35f, 0, 0, -0.3f),
			QuirkKeyframes.f(10, 0.9f, 0, 0.25f, 0.9f, 0, -0.25f, 0.4f, 0, 0.35f, 0, 0, 0.35f, 0, 0, -0.3f), QuirkKeyframes.rest(14) };
	private static final float[][] DIVE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.8f, 0, 0.2f, -2.8f, 0, -0.2f, 0.3f, 0, 0.25f, 0, 0, 0.25f, 0, 0, -0.4f),
			QuirkKeyframes.f(56, -2.8f, 0, 0.2f, -2.8f, 0, -0.2f, 0.3f, 0, 0.25f, 0, 0, 0.25f, 0, 0, -0.4f), QuirkKeyframes.rest(60) };
	private static final float[][] GUST = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.3f, 0, 1.2f, -1.3f, 0, -1.2f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(7, -1.4f, 0, 0.2f, -1.4f, 0, -0.2f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(14) };
	private static final float[][] STORM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.6f, 0, 0.5f, -2.6f, 0, -0.5f, -0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.5f),
			QuirkKeyframes.f(20, -2.7f, 0, 0.6f, -2.7f, 0, -0.6f, -0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.5f), QuirkKeyframes.rest(26) };
	private static final float[][] JAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, 0.25f, 0, 0.3f, 0, 0, 0.1f, -0.35f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(7) };
	private static final float[][] TAKEOFF = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, 0, 0, -0.6f, 0, 0, -0.6f, 0, 0, 0),
			QuirkKeyframes.rest(14) };
	private static final float[][] SPLASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.5f, 0, 0.9f, 0.5f, 0, -0.9f, 0.5f, 0, -0.8f, 0, 0.1f, -0.8f, 0, -0.1f, 0.3f),
			QuirkKeyframes.rest(14) };

	private static final FirstPersonClip FP_SPIT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -8, 6, 0, 0.03f, 0.06f, 0.05f),
			FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_JAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 24, 0, 0.15f, 0.08f, -0.22f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, -35, 25, -0.2f, 0.2f, 0),
			FirstPersonClip.f(7, -30, 30, 0, 0.15f, 0.2f, -0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -60, -10, 15, -0.05f, 0.45f, 0.1f),
			FirstPersonClip.f(20, -62, -10, 15, -0.05f, 0.45f, 0.1f), FirstPersonClip.f(26, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DIVE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -70, 12, 0, 0.08f, 0.4f, 0.1f),
			FirstPersonClip.f(56, -70, 12, 0, 0.08f, 0.4f, 0.1f), FirstPersonClip.f(60, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LOW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 15, 15, 0, 0.1f, -0.15f, 0),
			FirstPersonClip.f(10, 15, 15, 0, 0.1f, -0.15f, 0), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();

	/** v0.17.2 (user: "Give his powers actual models and vfx instead of just glowing orbs"): from {@code OmnitrixClient}. */
	public static void init() {
		StinkflyGooFx.init();
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int light) {
		AlienParts.box(pose, vc, x0, y0, z0, x1, y1, z1, uv[0] / 64f, uv[1] / 64f, uv[2] / 64f, uv[3] / 64f, 0xFFFFFFFF, light);
	}

	/** In the air on his wings: the server's synced flag (the abilities' flying bit, which landing clears) -- a remote player's onGround is not reliable. */
	private static boolean flying(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & (StinkflyKit.F_FLYING | StinkflyKit.F_DIVING)) != 0;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		// v0.17.1 (user: "use this for the stinkfly model" -- a minecraftskins.com skin): the skin's own green limbs and claws now,
		// so no thin replacement limbs; the eye stalks, mandibles, tail and wings stay on top of it
		// the head: mandibles
		AlienParts.on(pose, model.head, () -> {
			// v0.17.1: no eye stalks -- the user's skin has his eyes on its face
			float open = s.animId == StinkflyKit.ANIM_SHOT && t < 9 ? Mth.sin(t / 9f * Mth.PI) * 0.5f : 0f;
			for (int side = -1; side <= 1; side += 2) {
				pose.pushPose();
				pose.translate(side * 2.2f / 16f, -1.0f / 16f, -4.0f / 16f);
				pose.mulPose(Axis.YP.rotation(side * (0.35f + open)));
				box(pose, vc, -0.6f, -0.6f, -2.6f, 0.6f, 0.6f, 0f, MANDIBLE, light);
				pose.popPose();
			}
		});
		// the abdomen and the stinger, curling under the body for the stab
		float curl = 0f;
		if (s.animId == StinkflyKit.ANIM_STAB && t < 12) {
			curl = t < 3 ? t / 3f : 1f - (t - 3f) / 9f;
		}
		float sway = Mth.sin(age * 0.1f) * 0.08f;
		final float c = curl;
		AlienParts.on(pose, model.body, () -> {
			pose.pushPose();
			pose.translate(0, 10.5f / 16f, 1.2f / 16f);
			pose.mulPose(Axis.XP.rotation(-0.55f - c * 1.6f));
			pose.mulPose(Axis.YP.rotation(sway));
			float[][] seg = { { 2.4f, 4.5f }, { 2.0f, 4.0f }, { 1.5f, 3.5f } };
			for (float[] g : seg) {
				box(pose, vc, -g[0], -g[0], 0f, g[0], g[0], g[1], TAIL, light);
				pose.translate(0, 0, (g[1] - 0.3f) / 16f);
				pose.mulPose(Axis.XP.rotation(-0.3f - c * 0.6f));
			}
			box(pose, vc, -0.6f, -0.6f, 0f, 0.6f, 0.6f, 3.2f, STINGER, light);
			pose.popPose();
		});
		// the wings: folded along the back on the ground, a fast beat in the air (v0.17.2: the reference's wing shapes)
		renderWings(pose, buffers, light, p, model, partial, age);
	}

	// ---------------------------------------------------------------- v0.17.2 (user: "Make stinkfly's wings look better and more like
	// how his wings actually look like, look at the reference image i sent"): two pairs of long pale yellow-green insect wings painted
	// from docs/reference/stinkfly (stinkfly_wings.png, scratchpad/ben10_v0171/gen_stinkfly_wings.js) -- the big upper pair a blade
	// sweeping up and out to a sharp point with a barb, a round cut-out and a hooked claw-like lobe; the smaller lower pair hooked
	// down. Each is one alpha-cut-out quad (both faces drawn) hinged at a small mount on his back.

	private static final ResourceLocation WINGS = ProjectHeroMod.id("textures/entity/omnitrix/stinkfly_wings.png");
	/** Upper / lower wing: the quad's size (model px), the wing root's texel in its 128x128 cell. */
	private static final float UPPER_SIZE = 28f, UPPER_ROOT_U = 2f, UPPER_ROOT_V = 105f;
	private static final float LOWER_SIZE = 17f, LOWER_ROOT_U = 2f, LOWER_ROOT_V = 38f;
	/** How spread the wings are (0 folded .. 1 flying), eased per player so take-off and landing open / close them smoothly. */
	private static final java.util.Map<AbstractClientPlayer, float[]> OPEN = new java.util.WeakHashMap<>();

	private static float openAmount(AbstractClientPlayer p, float partial) {
		float[] o = OPEN.get(p);
		if (o == null) {
			return flying(p) ? 1f : 0f;
		}
		return Mth.lerp(partial, o[0], o[1]);
	}

	private void renderWings(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age) {
		float open = openAmount(p, partial);
		boolean diving = (Omnitrix.state(p).kitFlags & StinkflyKit.F_DIVING) != 0;
		// the flying beat: fast (about 6 strokes a second shows as a blur of positions), the lower pair a quarter-stroke behind
		float beatSpeed = diving ? 3.1f : 2.4f;
		AlienParts.on(pose, model.body, () -> {
			// the mount: a small knot of chitin between the shoulder blades
			box(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS)), -1.2f, 2.6f, 1.8f, 1.2f, 5.6f, 2.9f, TAIL, light);
			VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(WINGS)); // fetched after the mount (another buffer ends this one)
			for (int side = -1; side <= 1; side += 2) {
				for (int pair = 0; pair < 2; pair++) {
					boolean upper = pair == 0;
					float phase = age * beatSpeed - (upper ? 0f : 1.3f) + (side < 0 ? 0.08f : 0f);
					float beat = Mth.sin(phase);
					float stroke = Mth.cos(phase);
					// spread (flying): the upper blade raised out behind him, beating through a wide arc; the lower pair lower
					// and shorter-stroked
					float flyRoll = upper ? -0.45f + beat * 0.42f : 0.1f + beat * 0.32f;
					float flyYaw = (upper ? 0.35f : 0.45f) + stroke * (upper ? 0.28f : 0.2f);
					float flyTilt = -0.25f + stroke * 0.12f;
					// folded (ground): swept back along his back, the blades pointing up and back, a faint idle quiver
					float quiver = Mth.sin(age * 0.09f + pair) * 0.03f;
					float foldRoll = (upper ? 0.35f : 0.6f) + quiver;
					float foldYaw = upper ? 1.38f : 1.32f;
					float foldTilt = upper ? -0.15f : 0.1f;
					if (diving) {
						// tucked back in the plunge, buzzing
						flyRoll = (upper ? 0.35f : 0.6f) + beat * 0.12f;
						flyYaw = 1.0f + stroke * 0.06f;
					}
					float roll = Mth.lerp(open, foldRoll, flyRoll);
					float yaw = Mth.lerp(open, foldYaw, flyYaw);
					float tilt = Mth.lerp(open, foldTilt, flyTilt);
					pose.pushPose();
					pose.translate(side * 0.9f / 16f, (upper ? 3.4f : 5.0f) / 16f, 2.7f / 16f);
					pose.mulPose(Axis.YP.rotation(-side * yaw));
					pose.mulPose(Axis.XP.rotation(tilt));
					pose.mulPose(Axis.ZP.rotation(side * roll));
					wingQuad(glass, pose.last(), light, side, upper);
					pose.popPose();
				}
			}
		});
	}

	/** One wing: the root texel at the hinge, the wing out along +x (side +1) / -x, up = -y; its cell of the sheet. */
	private static void wingQuad(VertexConsumer vc, PoseStack.Pose last, int light, int side, boolean upper) {
		float size = upper ? UPPER_SIZE : LOWER_SIZE;
		float ru = upper ? UPPER_ROOT_U : LOWER_ROOT_U, rv = upper ? UPPER_ROOT_V : LOWER_ROOT_V;
		float k = size / 128f / 16f; // texel -> blocks
		float x0 = side * (0f - ru) * k, x1 = side * (128f - ru) * k;
		float y0 = (0f - rv) * k, y1 = (128f - rv) * k;
		float u0 = upper ? 0f : 0.5f, u1 = u0 + 0.5f;
		// the normal points up: both faces lit like a pale membrane, never shaded dark from behind
		AlienParts.quad(vc, last, 0xFFFFFFFF, light, x0, y0, 0, x1, y0, 0, x1, y1, 0, x0, y1, 0, 0, -1, 0, u0, 0f, u1, 1f);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case StinkflyKit.ANIM_SHOT, StinkflyKit.ANIM_BARRAGE -> SPIT;
			case StinkflyKit.ANIM_NET -> NET;
			case StinkflyKit.ANIM_GAS -> GAS;
			case StinkflyKit.ANIM_STAB -> STAB;
			case StinkflyKit.ANIM_DASH -> DASH;
			case StinkflyKit.ANIM_DIVE -> DIVE;
			case StinkflyKit.ANIM_GUST -> GUST;
			case StinkflyKit.ANIM_STORM -> STORM;
			case StinkflyKit.ANIM_JAB -> JAB;
			case StinkflyKit.ANIM_TAKEOFF -> TAKEOFF;
			case StinkflyKit.ANIM_SPLASH -> SPLASH;
			case StinkflyKit.ANIM_LIFT -> LIFT;
			default -> null;
		};
		if (animId == StinkflyKit.ANIM_DIVE && (Omnitrix.state(p).kitFlags & StinkflyKit.F_DIVING) == 0 && ticks > 4) {
			clip = null; // the dive pose only while still diving
		}
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played && flying(p)) {
			// hovering: legs trailing, forearms held up like an insect's
			m.rightArm.xRot = -0.6f + Mth.sin(age * 0.3f) * 0.05f;
			m.leftArm.xRot = -0.6f - Mth.sin(age * 0.3f) * 0.05f;
			m.rightArm.zRot = 0.2f;
			m.leftArm.zRot = -0.2f;
			m.rightLeg.xRot = 0.45f + Mth.sin(age * 0.25f) * 0.08f;
			m.leftLeg.xRot = 0.35f - Mth.sin(age * 0.25f) * 0.08f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case StinkflyKit.ANIM_SHOT, StinkflyKit.ANIM_BARRAGE, StinkflyKit.ANIM_NET -> FP_SPIT;
			case StinkflyKit.ANIM_STAB, StinkflyKit.ANIM_JAB -> FP_JAB;
			case StinkflyKit.ANIM_GUST, StinkflyKit.ANIM_TAKEOFF -> FP_WIDE;
			case StinkflyKit.ANIM_STORM -> FP_UP;
			case StinkflyKit.ANIM_DIVE, StinkflyKit.ANIM_DASH -> FP_DIVE;
			case StinkflyKit.ANIM_GAS, StinkflyKit.ANIM_SPLASH -> FP_LOW;
			case StinkflyKit.ANIM_LIFT -> FP_LIFT;
			default -> null;
		};
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// v0.17.1: the skin's own arm (it has his claws painted on)
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var level = p.level();
		var r = p.getRandom();
		// v0.17.2: the wings ease open on take-off and fold away on landing (half-tucked in a dive)
		float[] o = OPEN.computeIfAbsent(p, k -> new float[] { flying(p) ? 1f : 0f, flying(p) ? 1f : 0f });
		float target = (Omnitrix.state(p).kitFlags & StinkflyKit.F_DIVING) != 0 ? 0.8f : flying(p) ? 1f : 0f;
		o[0] = o[1];
		o[1] += (target - o[1]) * (target > o[1] ? 0.35f : 0.2f);
		if (r.nextInt(25) == 0) {
			level.addParticle(ParticleTypes.ITEM_SLIME, p.getX() + (r.nextDouble() - 0.5) * 0.4, p.getY() + p.getBbHeight() * 0.4,
					p.getZ() + (r.nextDouble() - 0.5) * 0.4, 0, -0.05, 0);
		}
		if (flying(p) && r.nextInt(3) == 0) {
			// the wing shimmer
			level.addParticle(ParticleTypes.WHITE_ASH, p.getX() + (r.nextDouble() - 0.5) * 1.4, p.getY() + p.getBbHeight() * 0.7,
					p.getZ() + (r.nextDouble() - 0.5) * 1.4, 0, 0, 0);
		}
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "stinkfly's badge needs to be on his face"): on his forehead, between the eyes (head-bone pixels: the cube
		// spans x -4..4, y -8..0, z -4..4; just in front of the hat layer)
		return new float[] { 0f, -4.0f, -4.56f, 3.0f };
	}

	@Override
	public int badgeBone() {
		return BADGE_HEAD;
	}
}
