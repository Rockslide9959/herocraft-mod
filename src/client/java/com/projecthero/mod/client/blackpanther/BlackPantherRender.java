package com.projecthero.mod.client.blackpanther;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.client.bluebeetle.BlueBeetleRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: Black Panther's suit -- the user's Planet Minecraft skin ("The Black Panther (MCU)", file 11967850, 64x64) on the
 * same hair-larger player model as Blue Beetle ({@link BlueBeetleRender#posed}).
 *
 * <p>The user: "Make the suit retract into a necklace". The necklace is a small voxel chain of silver beads and fangs round the
 * wearer's collar, there whenever they hold the power. The suit runs out of it texel by texel across the skin (the Blue Beetle
 * geodesic, seeded at the necklace), the head last, behind a glowing violet edge; H again plays it backwards, so it drains back
 * into the necklace. The necklace is drawn over the suit too (v0.17.3 tester round), and not at all once it is taken off (hold
 * H). N adds three vibranium claws to each fist; Shift+N hides the suit's head. The trim glows purple with the Kinetic bar.
 */
public final class BlackPantherRender {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/black_panther/black_panther.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/black_panther/black_panther_glow.png");
	public static final ResourceLocation NECKLACE = ProjectHeroMod.id("textures/entity/black_panther/necklace.png");
	public static final int FRAMES = 64;
	/** The body takes this share of the build; the head takes the rest, so the mask closes last. */
	private static final float BODY_SHARE = 0.74f;

	private static ResourceLocation[] frames;
	private static float[] orders;
	private static ModelPart necklace;

	private BlackPantherRender() {
	}

	/** {@code cover}: how far the suit has run, 0..1. */
	public record Anim(float cover) {
		static final Anim NONE = new Anim(0f);
		static final Anim FULL = new Anim(1f);

		public boolean anything() {
			return cover > 0f;
		}

		public boolean full() {
			return cover >= 1f;
		}
	}

	static BlackPantherState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
	}

	public static Anim anim(Player player, float partial) {
		BlackPantherState s = state(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return Anim.NONE;
		}
		float age = player.level().getGameTime() - s.suitChangeAt + partial;
		if (age < 0f && age > -40f) {
			age = 0f;
		}
		if (s.suited) {
			float total = BlackPantherConfig.SUIT_UP_TICKS;
			if (s.suitChangeAt <= 0L || age >= total || age < 0f) {
				return Anim.FULL;
			}
			return new Anim(Mth.clamp(age / total, 0f, 1f));
		}
		float total = BlackPantherConfig.SUIT_DOWN_TICKS;
		if (s.suitChangeAt <= 0L || age >= total || age < 0f) {
			return Anim.NONE;
		}
		return new Anim(1f - Mth.clamp(age / total, 0f, 1f));
	}

	public static float progress(Player player, float partial) {
		return anim(player, partial).cover();
	}

	public static boolean hidesSkinOverlay(Player player) {
		return progress(player, 0f) > 0f;
	}

	/** The trim's glow, 0..1: the Kinetic bar. */
	private static float glow(Player player) {
		BlackPantherState s = state(player);
		return s == null ? 0f : Mth.clamp(s.kinetic / BlackPantherConfig.KINETIC_MAX, 0f, 1f);
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, Player player, Anim a, float ageInTicks) {
		PlayerModel<LivingEntity> m = BlueBeetleRender.posed(parent);
		// Shift+N: unmasked, the suit's head (and its hat layer) is not drawn -- the wearer's own face and hair show
		boolean unmasked = unmasked(player);
		if (unmasked) {
			m.head.visible = false;
			m.hat.visible = false;
		}
		try {
			ResourceLocation tex = a.full() ? SKIN : frame(a.cover());
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			float k = glow(player);
			if (a.full() && k > 0.01f) {
				float pulse = 0.8f + 0.2f * Mth.sin(ageInTicks * 0.15f);
				int alpha = Mth.clamp(Math.round(255 * k * pulse), 0, 255);
				m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
						(alpha << 24) | 0xFFFFFF);
			}
		} finally {
			// the model is shared with Blue Beetle
			m.head.visible = true;
			m.hat.visible = true;
		}
	}

	/** Shift+N: the mask is off (synced state). */
	public static boolean unmasked(Player player) {
		BlackPantherState s = state(player);
		return s != null && s.hasPower && s.suited && s.unmasked;
	}

	/** The wearer's own hat / hair layer shows: the mask is off and the suit is on. */
	public static boolean showsWearerHat(Player player) {
		return unmasked(player);
	}

	// ---------------------------------------------------------------- the claws (N)

	/** [right, left] x [third person, first person]. */
	private static final ModelPart[] CLAWS = new ModelPart[4];

	/**
	 * v0.17.4 (user: "make them show up around the tip of his hand in finger positions that make sense"): five short curved
	 * vibranium claws, one out of each fingertip. In the arm bone's own space -- the suit's arm is always the wide 4 px arm
	 * ({@link BlueBeetleRender#model}, slim wearers included): the hand is the bottom face (y = 10, the sleeve's skin at 10.3),
	 * x -3..1 on the right arm (+x the inner side, toward the body), -1..3 on the left, the front at z = -2.
	 *
	 * <p>The four fingers sit side by side along the front edge of the hand; each claw grows out of the fingertip in three
	 * tapering segments that hook forward like a cat's (dark vibranium at the root, bright silver at the point). The thumb's
	 * claw, shorter, comes out of the inner side of the hand, set back from the fingers. The first-person set is a little longer
	 * and thicker so it reads at the bottom of the screen.
	 */
	private static ModelPart claws(boolean right, boolean firstPerson) {
		int i = (right ? 0 : 1) + (firstPerson ? 2 : 0);
		if (CLAWS[i] != null) {
			return CLAWS[i];
		}
		float k = firstPerson ? 1.35f : 1.0f; // length
		float t = firstPerson ? 1.2f : 1.0f; // thickness
		float centre = right ? -1f : 1f;
		float inner = right ? 1f : -1f; // toward the body
		MeshDefinition mesh = new MeshDefinition();
		var root = mesh.getRoot();
		// the fingers, little finger outermost; the middle two a touch longer
		float[] fx = { -1.45f, -0.5f, 0.45f, 1.35f };
		float[] len = { 0.85f, 1.0f, 1.0f, 0.9f };
		for (int f = 0; f < 4; f++) {
			float x = centre + (right ? fx[f] : -fx[f]);
			root.addOrReplaceChild("finger" + f, claw(len[f] * k, t), PartPose.offset(x, 10.0f, -1.55f));
		}
		// the thumb: out of the inner side, further back, angled in and down
		root.addOrReplaceChild("thumb", claw(0.7f * k, t * 0.95f),
				PartPose.offsetAndRotation(centre + inner * 1.75f, 9.4f, -0.9f, 0f, 0f, -inner * 0.55f));
		CLAWS[i] = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		return CLAWS[i];
	}

	/**
	 * One claw at its root (0, 0, 0), growing down (+y) and hooking forward (-z): three segments, each narrower, further down and
	 * further forward than the last.
	 */
	private static CubeListBuilder claw(float len, float t) {
		CubeListBuilder c = CubeListBuilder.create();
		// the root sheath (dark vibranium), set into the fingertip
		c.texOffs(16, 8).addBox(-0.26f * t, -0.2f, -0.28f * t, 0.52f * t, 0.75f * len + 0.2f, 0.56f * t);
		// the body (silver), bending forward
		c.texOffs(24, 8).addBox(-0.2f * t, 0.55f * len, -0.34f * t - 0.28f * len, 0.4f * t, 0.65f * len, 0.42f * t);
		// the hooked point (bright)
		c.texOffs(8, 8).addBox(-0.13f * t, 1.15f * len, -0.3f * t - 0.62f * len, 0.26f * t, 0.5f * len, 0.3f * t);
		c.texOffs(8, 8).addBox(-0.08f * t, 1.5f * len, -0.22f * t - 0.95f * len, 0.16f * t, 0.3f * len, 0.36f * t);
		return c;
	}

	/** The claws are out (N), the suit is fully on. */
	public static boolean clawsShown(Player player) {
		BlackPantherState s = state(player);
		return s != null && s.hasPower && s.suited && s.clawsOut && !s.necklaceOff;
	}

	/** Both hands' claws on the third-person model (the pose already in the entity's model space). */
	public static void renderClaws(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> model) {
		renderClaws(pose, buffers, light, player, model.rightArm, true, false);
		renderClaws(pose, buffers, light, player, model.leftArm, false, false);
	}

	/** One hand's claws; {@code firstPerson} draws the longer blades the player sees on their own hand. */
	public static void renderClaws(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right, boolean firstPerson) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		ModelPart blades = claws(right, firstPerson);
		blades.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(NECKLACE)), light, OverlayTexture.NO_OVERLAY);
		float k = glow(player);
		if (k > 0.01f) {
			blades.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(NECKLACE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					(Mth.clamp(Math.round(150 * k), 0, 255) << 24) | 0xB070FF);
		}
		pose.popPose();
	}

	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right, float progress) {
		PlayerModel<LivingEntity> m = BlueBeetleRender.model();
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		ResourceLocation tex = progress >= 1f ? SKIN : frame(progress);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		float k = glow(player);
		if (progress >= 1f && k > 0.01f) {
			int color = (Mth.clamp(Math.round(255 * k), 0, 255) << 24) | 0xFFFFFF;
			a.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
			sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
		}
	}

	// ---------------------------------------------------------------- the necklace

	private static ModelPart necklaceGems;

	/** The front chain's height at {@code x} (body space, y down): a hanging curve, steep at the shoulders, flat at the pendant. */
	private static float chainY(float x) {
		float u = Math.min(1f, Math.abs(x) / 3.3f);
		return 0.25f + 3.75f * (1f - (float) Math.pow(u, 1.7));
	}

	/** A round-looking bead: a cube with a slightly wider and a slightly taller cross through it. */
	private static void bead(CubeListBuilder c, float x, float y, float z, float r) {
		c.addBox(x - r, y - r * 0.8f, z - r * 0.8f, 2 * r, 1.6f * r, 1.6f * r);
		c.addBox(x - r * 0.8f, y - r, z - r * 0.8f, 1.6f * r, 2 * r, 1.6f * r);
		c.addBox(x - r * 0.8f, y - r * 0.8f, z - r, 1.6f * r, 1.6f * r, 2 * r);
	}

	/**
	 * v0.17.4 (user: "improve his necklace model please it needs to look better"): the Black Panther's necklace, rebuilt. Body space,
	 * y down, the chest is -z (its face at z = -2, the suit's jacket at -2.3).
	 * <ul>
	 *   <li>The front chain hangs in a curve from the shoulders to the breastbone: big round vibranium-silver beads with small
	 *       gunmetal spacers between them, each bead touching the next.</li>
	 *   <li>Over each shoulder a short run of spacers to the back, and a straight run of small beads along the back collar.</li>
	 *   <li>The pendant: a silver bezel holding a purple vibranium gem, a panther's fang hanging from it (three tapering
	 *       segments that hook forward) with a shorter claw angled out on each side.</li>
	 * </ul>
	 * The gems are a separate part ({@link #necklaceGems}) so they can shimmer on their own. 32 x 32 swatches in
	 * {@code necklace.png} (silver, gunmetal, fang, gem, dark silver, highlight, then the claw colours).
	 */
	private static ModelPart necklace() {
		if (necklace == null) {
			MeshDefinition mesh = new MeshDefinition();
			var root = mesh.getRoot();
			CubeListBuilder silver = CubeListBuilder.create().texOffs(0, 0);
			CubeListBuilder spacers = CubeListBuilder.create().texOffs(8, 0);
			CubeListBuilder links = CubeListBuilder.create().texOffs(0, 8);
			// the front chain: beads every 0.95 along the curve's length, spacers between
			float z = -2.62f;
			for (int side = -1; side <= 1; side += 2) {
				float x = side * 3.3f;
				float y = chainY(x);
				boolean big = true;
				int n = 0;
				while (side * x > 0.9f && n++ < 30) {
					if (big) {
						bead(silver, x, y, z, 0.42f);
					} else {
						spacers.addBox(x - 0.24f, y - 0.24f, z - 0.24f, 0.48f, 0.48f, 0.48f);
					}
					// step along the curve: 0.62 between a bead and a spacer
					float step = 0.56f;
					float nx = x, ny = y;
					for (int s = 0; s < 40; s++) {
						nx -= side * 0.02f;
						ny = chainY(nx);
						if (Math.hypot(nx - x, ny - y) >= step) {
							break;
						}
					}
					x = nx;
					y = ny;
					big = !big;
				}
			}
			// over the shoulders: a run of spacers from the front chain's top to the back collar
			for (int side = -1; side <= 1; side += 2) {
				for (int i = 0; i < 7; i++) {
					float zz = -2.35f + i * 0.78f;
					links.addBox(side * 3.3f - 0.26f, -0.05f - 0.26f, zz - 0.26f, 0.52f, 0.52f, 0.52f);
				}
			}
			// the back collar: small beads straight across
			for (int i = 0; i < 9; i++) {
				float x = -3.2f + i * 0.8f;
				links.addBox(x - 0.32f, 0.2f - 0.3f, 2.38f, 0.64f, 0.6f, 0.5f);
			}
			// the pendant's bezel: a silver frame round the gem, and a bail joining it to the chain
			float py = chainY(0f) + 0.55f;
			silver.addBox(-0.85f, py - 0.75f, -2.78f, 1.7f, 1.5f, 0.45f);
			silver.addBox(-0.25f, py - 1.1f, -2.72f, 0.5f, 0.5f, 0.4f);
			root.addOrReplaceChild("silver", silver, PartPose.ZERO);
			root.addOrReplaceChild("spacers", spacers, PartPose.ZERO);
			root.addOrReplaceChild("links", links, PartPose.ZERO);
			// the fang: three tapering segments under the bezel, hooking forward
			float fy = py + 0.7f;
			CubeListBuilder fang = CubeListBuilder.create().texOffs(16, 0)
					.addBox(-0.36f, 0f, -0.3f, 0.72f, 0.9f, 0.55f)
					.addBox(-0.27f, 0.85f, -0.42f, 0.54f, 0.8f, 0.48f)
					.addBox(-0.17f, 1.6f, -0.56f, 0.34f, 0.65f, 0.4f)
					.addBox(-0.09f, 2.2f, -0.7f, 0.18f, 0.4f, 0.34f);
			root.addOrReplaceChild("fang", fang, PartPose.offset(0f, fy, -2.55f));
			// the two side claws, shorter, angled out
			for (int side = -1; side <= 1; side += 2) {
				CubeListBuilder claw = CubeListBuilder.create().texOffs(16, 0)
						.addBox(-0.25f, 0f, -0.25f, 0.5f, 0.75f, 0.45f)
						.addBox(-0.17f, 0.7f, -0.36f, 0.34f, 0.6f, 0.38f)
						.addBox(-0.09f, 1.25f, -0.48f, 0.18f, 0.4f, 0.32f);
				root.addOrReplaceChild("claw" + (side > 0 ? "R" : "L"), claw,
						PartPose.offsetAndRotation(side * 0.75f, fy - 0.25f, -2.55f, 0f, 0f, -side * 0.45f));
			}
			necklace = LayerDefinition.create(mesh, 32, 32).bakeRoot();

			// the gems: the pendant's stone, and a small one on each side of the chain where it turns over the shoulder
			MeshDefinition gm = new MeshDefinition();
			CubeListBuilder gems = CubeListBuilder.create().texOffs(24, 0)
					.addBox(-0.55f, py - 0.5f, -2.95f, 1.1f, 1.0f, 0.4f)
					.addBox(-0.35f, py - 0.65f, -2.9f, 0.7f, 1.3f, 0.35f);
			for (int side = -1; side <= 1; side += 2) {
				float gx = side * 2.25f;
				float gy = chainY(gx);
				gems.addBox(gx - 0.22f, gy + 0.3f, -2.88f, 0.44f, 0.44f, 0.3f);
			}
			gm.getRoot().addOrReplaceChild("gems", gems, PartPose.ZERO);
			necklaceGems = LayerDefinition.create(gm, 32, 32).bakeRoot();
		}
		return necklace;
	}

	/**
	 * Round the collar, in the posed body's space, drawn after the suit and a little proud of it so it stays on show over the
	 * full suit. Pushed out over worn armour (which the suit hides, so only while none of the suit is on). The silver takes a slow
	 * violet shimmer, the gems pulse on their own, and both glow brighter with the Kinetic bar.
	 */
	public static void renderNecklace(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart body) {
		pose.pushPose();
		body.translateAndRotate(pose);
		if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && progress(player, 0f) <= 0f) {
			pose.scale(1.28f, 1f, 1.6f);
		}
		ModelPart chain = necklace();
		chain.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(NECKLACE)), light, OverlayTexture.NO_OVERLAY);
		necklaceGems.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(NECKLACE)), light, OverlayTexture.NO_OVERLAY);
		float k = glow(player);
		float time = player.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		// the silver's shimmer: a slow violet sheen, stronger with the bar
		float sheen = 0.5f + 0.5f * Mth.sin(time * 0.08f);
		int silverA = Mth.clamp(Math.round(28 * sheen + 200 * k), 0, 255);
		if (silverA > 2) {
			chain.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(NECKLACE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					(silverA << 24) | 0xB070FF);
		}
		// the gems always glow a little, pulsing
		float pulse = 0.55f + 0.45f * Mth.sin(time * 0.15f);
		int gemA = Mth.clamp(Math.round(110 + 90 * pulse + 55 * k), 0, 255);
		necklaceGems.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(NECKLACE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				(gemA << 24) | 0xFFFFFF);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the order of the build

	/** When base-layer texel (u, v) turns into suit, 0..1 (2 = unused); overlay texels follow the base texel under them. */
	public static float order(int u, int v) {
		if (orders == null) {
			float[] from = BlueBeetleRender.geodesicFrom(0f, 1.5f, -2f);
			float bodyMax = 0f, headMin = Float.MAX_VALUE, headMax = 0f;
			for (int i = 0; i < from.length; i++) {
				if (from[i] < 0f) {
					continue;
				}
				if (head(i % 64, i / 64)) {
					headMin = Math.min(headMin, from[i]);
					headMax = Math.max(headMax, from[i]);
				} else {
					bodyMax = Math.max(bodyMax, from[i]);
				}
			}
			float[] o = new float[64 * 64];
			java.util.Arrays.fill(o, 2f);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int base = BlueBeetleRender.baseTexel(x, y);
					if (base < 0 || from[base] < 0f) {
						continue;
					}
					float jitter = 0.05f * Mth.frac(Mth.sin(x * 12.9898f + y * 78.233f) * 43758.547f);
					float t = head(base % 64, base / 64)
							? BODY_SHARE + (1f - BODY_SHARE) * (from[base] - headMin) / Math.max(1f, headMax - headMin)
							: BODY_SHARE * from[base] / Math.max(1f, bodyMax);
					o[y * 64 + x] = Mth.clamp(0.95f * t + jitter, 0f, 1f);
				}
			}
			orders = o;
		}
		return u < 0 || v < 0 || u >= 64 || v >= 64 ? 2f : orders[v * 64 + u];
	}

	private static boolean head(int u, int v) {
		return v < 16 && u < 32;
	}

	private static ResourceLocation frame(float cover) {
		if (frames == null) {
			build();
		}
		if (frames.length == 0) {
			return SKIN;
		}
		return frames[Math.max(0, Math.min(FRAMES, (int) Math.floor(cover * FRAMES)))];
	}

	private static void build() {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> res = mc.getResourceManager().getResource(SKIN);
		if (res.isEmpty()) {
			frames = new ResourceLocation[0];
			return;
		}
		NativeImage src;
		try (InputStream in = res.get().open()) {
			src = NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[black_panther] could not read the suit skin", e);
			frames = new ResourceLocation[0];
			return;
		}
		int size = src.getWidth();
		int scale = Math.max(1, size / 64);
		frames = new ResourceLocation[FRAMES + 1];
		for (int k = 0; k <= FRAMES; k++) {
			float line = k / (float) FRAMES;
			NativeImage img = new NativeImage(size, size, true);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					float o = order(x / scale, y / scale);
					int px = src.getPixelRGBA(x, y);
					if (k == 0 || o > line) {
						img.setPixelRGBA(x, y, 0);
					} else if (k < FRAMES && line - o < EDGE && ((px >>> 24) & 0xFF) > 0) {
						img.setPixelRGBA(x, y, edge(px, (line - o) / EDGE)); // the build's frontier glows violet
					} else {
						img.setPixelRGBA(x, y, px);
					}
				}
			}
			frames[k] = mc.getTextureManager().register("projecthero_black_panther_build_" + k, new DynamicTexture(img));
		}
		src.close();
	}

	/** How far behind the build's frontier the violet edge band reaches (in build order, 0..1). */
	private static final float EDGE = 0.07f;

	/** The suit's moving edge: hot violet at the frontier, fading into the suit behind it ({@code t} 0 = the frontier). */
	private static int edge(int abgr, float t) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		float k = 1f - t;
		r = (int) Mth.lerp(k, r, 205);
		g = (int) Mth.lerp(k, g, 130);
		b = (int) Mth.lerp(k, b, 255);
		return (a << 24) | (b << 16) | (g << 8) | r;
	}
}
