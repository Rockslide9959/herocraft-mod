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
 * edge; H again (or taking the necklace off) plays it backwards. The necklace model matches the gold necklace painted on the
 * suit skin and is drawn only while the suit is on and the necklace worn ({@link #renderNecklace}). v0.17.7: the claw model
 * is gone (user: "remove the claw model it looks bad please"). Shift+N hides the suit's head. The trim glows purple with the Kinetic bar.
 */
public final class BlackPantherRender {
	/**
	 * v0.17.7: the comics suit -- "Black Panther [Marvel Comics]" on PlanetMinecraft (planetminecraft.com/skin/black-panther-marvel-comics-6828688,
	 * file 19479117, wide arms; borrowed skin, ships publicly) with its painted cape and high collar taken off (they are models:
	 * {@link BlackPantherCapeLayer}, {@link BlackPantherCollarLayer}) and the back painted in: {@code scratchpad/v0177/make_bp_suit_skin.js}.
	 */
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/black_panther/black_panther.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/black_panther/black_panther_glow.png");
	public static final ResourceLocation NECKLACE = ProjectHeroMod.id("textures/entity/black_panther/necklace.png");
	/** The necklace's Kinetic glow map: the same swatches, gold ones violet like {@link #GLOW}, the gaps dark. */
	public static final ResourceLocation NECKLACE_GLOW = ProjectHeroMod.id("textures/entity/black_panther/necklace_glow.png");
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
			if (a.full() && com.projecthero.mod.blackpanther.BlackPantherAbilities.shrouded(player)) {
				renderShroud(pose, buffers, m, ageInTicks);
			}
		} finally {
			// the model is shared with Blue Beetle
			m.head.visible = true;
			m.hat.visible = true;
		}
	}

	/** The charged creeper's swirl, scrolling: the Kinetic Shroud's shimmer. */
	private static final ResourceLocation SHROUD_SWIRL = ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");

	/**
	 * v0.17.7 Kinetic Shroud: a violet energy shimmer over the whole suit (the charged creeper's swirl on a shell a little larger
	 * than the body), pulsing gently while it lasts.
	 */
	private static void renderShroud(PoseStack pose, MultiBufferSource buffers, PlayerModel<LivingEntity> m, float ageInTicks) {
		float scroll = ageInTicks * 0.012f;
		float pulse = 0.75f + 0.25f * Mth.sin(ageInTicks * 0.35f);
		int r = Math.round(150 * pulse), g = Math.round(70 * pulse), b = Math.round(255 * pulse);
		pose.pushPose();
		pose.scale(1.06f, 1.04f, 1.08f);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.energySwirl(SHROUD_SWIRL, scroll % 1f, (scroll * 0.6f) % 1f)), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, 0xFF000000 | (r << 16) | (g << 8) | b);
		pose.popPose();
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

	/** The necklace: a gold collar band round the top of the torso with its teeth on the chest, all in the body's space. */
	private static ModelPart necklace;

	/**
	 * Texture swatches in {@code necklace.png} (64 x 64, each a 16 x 16 flat colour, from {@code scratchpad/bp_v0177_necklace_textures.js}),
	 * the golds painted on the suit skin: highlight, mid, shadow, the near-black gap, the bright tips, the lighter centre plate.
	 */
	private static final int[] HI = { 0, 0 }, MID = { 16, 0 }, SHADE = { 32, 0 }, GAP = { 48, 0 }, TIP = { 0, 16 }, PLATE = { 16, 16 };

	/**
	 * How far out from the body's centre the band's inside sits (px): the suit's jacket layer is at 2.29 front / back and 4.29 at the
	 * sides (0.04 + 0.25), so no face of the necklace is ever coplanar with the skin, the jacket or the sleeves (also at +-2.29).
	 */
	private static final float IN_Z = 2.36f, IN_X = 4.36f;
	/** The band's three tiers, top to bottom: y range (px, down from the top of the torso) and how far out each face stands. */
	private static final float HI_Y0 = 0.08f, HI_Y1 = 0.40f, HI_OUT = 0.32f;
	private static final float MID_Y0 = 0.36f, MID_Y1 = 0.98f, MID_OUT = 0.28f;
	private static final float SH_Y0 = 0.94f, SH_Y1 = 1.18f, SH_OUT = 0.22f;

	/** A box from x0..x1 on the chest ({@code dir} -1) or the back (+1), {@code out} px proud of the band's inside. */
	private static void zBox(CubeListBuilder b, int[] tex, float x0, float x1, float y0, float y1, float in, float out, int dir) {
		b.texOffs(tex[0], tex[1]);
		b.addBox(x0, y0, dir < 0 ? -(in + out) : in, x1 - x0, y1 - y0, out);
	}

	/** One gold link of the band from x0..x1, chest or back: bright top bevel, mid face, darker lower lip (each a step further in). */
	private static void link(CubeListBuilder b, float x0, float x1, int dir) {
		zBox(b, HI, x0, x1, HI_Y0, HI_Y1, IN_Z, HI_OUT, dir);
		zBox(b, MID, x0 + 0.02f, x1 - 0.02f, MID_Y0, MID_Y1, IN_Z, MID_OUT, dir);
		zBox(b, SHADE, x0 + 0.04f, x1 - 0.04f, SH_Y0, SH_Y1, IN_Z, SH_OUT, dir);
	}

	/** The end links reach round the corner to the side band, so each tier's outer face lines up with the side band's. */
	private static void endLink(CubeListBuilder b, float inner, int side, int dir) {
		float[][] tiers = { { HI_Y0, HI_Y1, HI_OUT }, { MID_Y0, MID_Y1, MID_OUT }, { SH_Y0, SH_Y1, SH_OUT } };
		int[][] tex = { HI, MID, SHADE };
		for (int t = 0; t < 3; t++) {
			float outer = IN_X + tiers[t][2];
			float x0 = side < 0 ? -outer : inner, x1 = side < 0 ? -inner : outer;
			zBox(b, tex[t], x0, x1, tiers[t][0], tiers[t][1], IN_Z, tiers[t][2], dir);
		}
	}

	/** The dark cord showing in a gap between two links (the skin's near-black gap texels), set back behind the links' faces. */
	private static void cord(CubeListBuilder b, float x, int dir) {
		zBox(b, GAP, x - 0.62f, x + 0.62f, 0.32f, 0.92f, IN_Z - 0.02f, 0.16f, dir);
	}

	/**
	 * One claw-tooth hanging on the chest from {@code y0}: a broad shadowed root, a narrower mid-gold middle and a fine bright tip, each
	 * a little further forward so the tooth curls out from the suit like the claws painted on it.
	 */
	private static void tooth(CubeListBuilder b, float x, float y0, float len, float w, float in) {
		float y1 = y0 + len * 0.45f, y2 = y0 + len * 0.40f, y3 = y0 + len * 0.78f, y4 = y0 + len * 0.72f, y5 = y0 + len;
		zBox(b, SHADE, x - w * 0.5f, x + w * 0.5f, y0, y1, in, 0.32f, -1);
		zBox(b, MID, x - w * 0.35f, x + w * 0.35f, y2, y3, in + 0.04f, 0.32f, -1);
		zBox(b, TIP, x - w * 0.19f, x + w * 0.19f, y4, y5, in + 0.08f, 0.32f, -1);
	}

	/**
	 * v0.17.7 (user: "Make the necklace model look like the necklace on the suit please"): the gold necklace painted on the comics suit
	 * skin ({@code black_panther.png}) in 3D. On the skin: row 20 of the body (the top texel row of the torso) is a gold band all the
	 * way round -- chest texels 20 / 22-25 / 27 gold with near-black gaps at 21 and 26, both sides solid gold, the back the chest
	 * mirrored (gaps at 33 and 38); under it on the chest, row 21 has claw-teeth at 21, 23, 24 and 26 and row 22 a centre point at
	 * 23-24; the jacket layer raises gold over the same rows. The arms carry no gold, so the whole necklace lives in the body's space.
	 * <ul>
	 *   <li>Band (y 0.08-1.18, the skin's row 20), chest and back: links at x -4.68..-3.04 and 3.04..4.68 (round the corners) and a
	 *       wider, thicker centre plate at -1.96..1.96 (texels 22-25), with dark cord in the gaps at x = +-2.5 (texels 21 / 26).</li>
	 *   <li>Sides: one solid gold run each (texels 16-19 / 28-31), between the chest and back links.</li>
	 *   <li>Chest only: a short tooth hanging from each gap's cord at x = +-2.5, a slightly longer one each side of centre at +-1.2 and
	 *       the long centre fang at 0 down to y 3.4 (the painted point ends at y 3) -- four teeth and the fang, open front.</li>
	 * </ul>
	 * Inside faces at 2.36 / 4.36, outside up to 2.88: clear of the jacket (2.29) and of the high collar
	 * ({@link BlackPantherCollarLayer}: its yoke is at y -0.73, above the band, its wall at 6.35 px round the neck and open at the front,
	 * its back skirt 3.7 px or more behind the torso) -- nothing touches.
	 */
	private static void buildNecklace() {
		if (necklace != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		CubeListBuilder b = CubeListBuilder.create();
		for (int dir = -1; dir <= 1; dir += 2) {
			endLink(b, 3.04f, -1, dir);
			endLink(b, 3.04f, 1, dir);
			// the centre plate (texels 22-25), a little bolder than the links
			zBox(b, HI, -1.96f, 1.96f, 0.08f, 0.36f, IN_Z, 0.36f, dir);
			zBox(b, PLATE, -1.94f, 1.94f, 0.32f, 1.06f, IN_Z, 0.40f, dir);
			zBox(b, SHADE, -1.9f, 1.9f, 1.02f, 1.22f, IN_Z, 0.30f, dir);
			cord(b, -2.5f, dir);
			cord(b, 2.5f, dir);
		}
		// the sides: one gold run each, from the chest links to the back links
		for (int side = -1; side <= 1; side += 2) {
			float[][] tiers = { { HI_Y0, HI_Y1, HI_OUT }, { MID_Y0, MID_Y1, MID_OUT }, { SH_Y0, SH_Y1, SH_OUT } };
			int[][] tex = { HI, MID, SHADE };
			for (int t = 0; t < 3; t++) {
				float out = tiers[t][2];
				b.texOffs(tex[t][0], tex[t][1]);
				b.addBox(side < 0 ? -(IN_X + out) : IN_X, tiers[t][0], -IN_Z, out, tiers[t][1] - tiers[t][0], IN_Z * 2f);
			}
		}
		// the teeth on the chest: the outer two hang from the cord in the gaps (their root wraps the cord like a bail), the inner two
		// and the long centre fang from under the plate
		tooth(b, -2.5f, 0.55f, 1.65f, 0.8f, IN_Z + 0.04f);
		tooth(b, 2.5f, 0.55f, 1.65f, 0.8f, IN_Z + 0.04f);
		tooth(b, -1.2f, 0.9f, 1.45f, 0.7f, IN_Z + 0.04f);
		tooth(b, 1.2f, 0.9f, 1.45f, 0.7f, IN_Z + 0.04f);
		tooth(b, 0f, 0.9f, 2.5f, 1.1f, IN_Z + 0.06f);
		mesh.getRoot().addOrReplaceChild("necklace", b, PartPose.ZERO);
		necklace = LayerDefinition.create(mesh, 64, 64).bakeRoot();
	}

	/**
	 * The necklace on the posed model, in the body's space. Drawn only while {@code BlackPanther.showsNecklace} (suited AND wearing the
	 * necklace) -- the layer checks. Gold like the painted one; with Kinetic stored it takes the suit trims' violet glow
	 * ({@link #NECKLACE_GLOW}, emissive, alpha = the Kinetic bar like the trims).
	 */
	public static void renderNecklace(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart body) {
		if (!body.visible) {
			return;
		}
		buildNecklace();
		float k = glow(player);
		int glowA = Mth.clamp(Math.round(255 * k), 0, 255);
		pose.pushPose();
		body.translateAndRotate(pose);
		necklace.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(NECKLACE)), light, OverlayTexture.NO_OVERLAY);
		if (glowA > 2) {
			necklace.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(NECKLACE_GLOW)), LightTexture.FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, (glowA << 24) | 0xFFFFFF);
		}
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
