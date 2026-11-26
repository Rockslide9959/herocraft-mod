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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: Black Panther's suit -- the user's Planet Minecraft skin ("The Black Panther (MCU)", file 11967850, 64x64) on the
 * same hair-larger player model as Blue Beetle ({@link BlueBeetleRender#posed}).
 *
 * <p>The user: "Make the suit retract into a necklace". v0.17.7: the suit IS the crafted necklace -- H with it worn runs the suit
 * out texel by texel across the skin (the Blue Beetle geodesic, seeded at the necklace), the head last, behind a glowing violet
 * edge; H again (or taking the necklace off) plays it backwards. The necklace model matches the tooth band painted on the suit
 * skin and is drawn only while the suit is on and the necklace worn ({@link #renderNecklace}). v0.17.7: the claw model
 * is gone (user: "remove the claw model it looks bad please"). Shift+N hides the suit's head. The trim glows purple with the Kinetic bar.
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

	/** The parts of the necklace: the chest-and-back band (body space) and the run over each shoulder (arm space). */
	private static ModelPart necklaceBody;
	private static ModelPart necklaceRightShoulder;
	private static ModelPart necklaceLeftShoulder;

	/** Texture swatches in {@code necklace.png} (32 x 32, each an 8 x 8 flat colour): silver, the bright fang tip, black bead. */
	private static final int SILVER_U = 0, TIP_U = 8, BEAD_U = 16;

	/** How far proud of the body the necklace sits: the suit's jacket layer is at 2.29 (0.04 + 0.25), so nothing is ever coplanar. */
	private static final float FACE = 2.42f;

	/**
	 * One vibranium fang hanging from the band at ({@code x}, {@code y}): a broad root, a narrower middle and a fine tip that hooks a
	 * little forward ({@code dir} -1 = the chest side, +1 = the back), about one skin texel wide and a little over one tall -- the same
	 * size as the silver texels painted on the suit.
	 */
	private static void fang(CubeListBuilder silver, CubeListBuilder tip, float x, float y, int dir) {
		float z = dir < 0 ? -FACE : FACE;
		silver.addBox(x - 0.42f, y, dir < 0 ? z - 0.36f : z, 0.84f, 0.55f, 0.36f);
		silver.addBox(x - 0.3f, y + 0.5f, dir < 0 ? z - 0.42f : z + 0.02f, 0.6f, 0.45f, 0.4f);
		tip.addBox(x - 0.16f, y + 0.9f, dir < 0 ? z - 0.48f : z + 0.06f, 0.32f, 0.4f, 0.42f);
	}

	/** A black bead of the cord between two fangs (the black texels between the silver ones on the suit). */
	private static void bead(CubeListBuilder beads, float x, float y, int dir) {
		float z = dir < 0 ? -FACE - 0.3f : FACE;
		beads.addBox(x - 0.36f, y + 0.12f, z, 0.72f, 0.56f, 0.3f);
	}

	/**
	 * v0.17.7 (user: "Make the necklace model look like the necklace on the suit please"): the necklace rebuilt from the one painted on
	 * the suit skin ({@code black_panther.png}). On the skin it is a band of tooth texels one row below the collar: on the chest and the
	 * back, texels 0, 2, 5 and 7 of row 1 are silver ({@code #6E6E76}) and 1, 3, 4, 6 black; over each shoulder it runs front to back
	 * along the arm's top (column 2 of the right arm, 1 of the left), silver and black in turn, and drops one silver texel down the
	 * front and back of the shoulder. The model is that band in 3D: a silver fang over every silver texel, a black bead over every
	 * black one, the same spacing, the same row, standing just proud of the suit.
	 * <ul>
	 *   <li>Body space (y down, the chest at -z): fangs at x = +-1.5, +-3.5, beads at +-0.5, +-2.5, from y = 1 (row 1), chest and back.</li>
	 *   <li>Arm space: over the shoulder top (y = -2) at x = -0.5 (right arm) / +0.5 (left): bead, silver bead, bead, silver bead from
	 *       front to back, and a short fang down the front and back of the shoulder.</li>
	 * </ul>
	 */
	private static void buildNecklace() {
		if (necklaceBody != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		CubeListBuilder silver = CubeListBuilder.create().texOffs(SILVER_U, 0);
		CubeListBuilder tip = CubeListBuilder.create().texOffs(TIP_U, 0);
		CubeListBuilder beads = CubeListBuilder.create().texOffs(BEAD_U, 0);
		for (int dir = -1; dir <= 1; dir += 2) {
			for (float x : new float[] { -3.5f, -1.5f, 1.5f, 3.5f }) {
				fang(silver, tip, x, 1.0f, dir);
			}
			for (float x : new float[] { -2.5f, -0.5f, 0.5f, 2.5f }) {
				bead(beads, x, 1.0f, dir);
			}
		}
		mesh.getRoot().addOrReplaceChild("silver", silver, PartPose.ZERO);
		mesh.getRoot().addOrReplaceChild("tip", tip, PartPose.ZERO);
		mesh.getRoot().addOrReplaceChild("beads", beads, PartPose.ZERO);
		necklaceBody = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		necklaceRightShoulder = shoulder(-0.5f);
		necklaceLeftShoulder = shoulder(0.5f);
	}

	/** The run over one shoulder, in that arm's space (the arm's top face at y = -2, its sleeve at -2.29). */
	private static ModelPart shoulder(float x) {
		MeshDefinition mesh = new MeshDefinition();
		CubeListBuilder silver = CubeListBuilder.create().texOffs(SILVER_U, 0);
		CubeListBuilder tip = CubeListBuilder.create().texOffs(TIP_U, 0);
		CubeListBuilder beads = CubeListBuilder.create().texOffs(BEAD_U, 0);
		float top = -2.42f;
		// over the top, front to back: silver, black, silver, black (the arm's top-face texels)
		for (int i = 0; i < 4; i++) {
			float z = -1.5f + i;
			(i % 2 == 0 ? silver : beads).addBox(x - 0.36f, top - 0.34f, z - 0.36f, 0.72f, 0.34f, 0.72f);
		}
		// down the front and the back of the shoulder: one short fang each (the arm's front / back top-row silver texel)
		for (int dir = -1; dir <= 1; dir += 2) {
			float z = dir < 0 ? -FACE : FACE;
			silver.addBox(x - 0.36f, -2.42f, dir < 0 ? z - 0.34f : z, 0.72f, 0.7f, 0.34f);
			tip.addBox(x - 0.18f, -1.76f, dir < 0 ? z - 0.4f : z + 0.04f, 0.36f, 0.42f, 0.38f);
		}
		mesh.getRoot().addOrReplaceChild("silver", silver, PartPose.ZERO);
		mesh.getRoot().addOrReplaceChild("tip", tip, PartPose.ZERO);
		mesh.getRoot().addOrReplaceChild("beads", beads, PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32).bakeRoot();
	}

	/**
	 * The necklace on the posed model: the band in the body's space, the shoulder runs in each arm's. Drawn only while
	 * {@code BlackPanther.showsNecklace} (suited AND wearing the necklace) -- the layer checks. Plain silver like the painted one;
	 * with Kinetic stored it takes the suit trim's violet glow.
	 */
	public static void renderNecklace(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart body, ModelPart rightArm,
			ModelPart leftArm) {
		buildNecklace();
		float k = glow(player);
		int glowA = Mth.clamp(Math.round(190 * k), 0, 255);
		ModelPart[] parts = { necklaceBody, necklaceRightShoulder, necklaceLeftShoulder };
		ModelPart[] bones = { body, rightArm, leftArm };
		for (int i = 0; i < parts.length; i++) {
			if (!bones[i].visible) {
				continue;
			}
			pose.pushPose();
			bones[i].translateAndRotate(pose);
			parts[i].render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(NECKLACE)), light, OverlayTexture.NO_OVERLAY);
			if (glowA > 2) {
				parts[i].render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(NECKLACE)), LightTexture.FULL_BRIGHT,
						OverlayTexture.NO_OVERLAY, (glowA << 24) | 0xB070FF);
			}
			pose.popPose();
		}
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
