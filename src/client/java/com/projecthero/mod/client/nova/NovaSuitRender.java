package com.projecthero.mod.client.nova;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.nova.NovaConfig;
import com.projecthero.mod.nova.data.NovaState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.13: the Nova Corps uniform drawn over a player -- the user's armour-only skin (Skindex #17170737 "Nova Richard Rider
 * 2.0", gold brightened, the bare-skin mouth / chin / head-underside texels made transparent) on a second, wide player
 * model a hair larger than the wearer's (base layer +0.04, outer layer +0.29, helmet +0.54). The same skin-rig idea as the
 * Superman / Mark 8 suits, but as a render layer rather than armour items: nothing to equip, drop or lose. The
 * transparent texels stay transparent, so the wearer's own face shows in the helmet opening; the wearer's own skin
 * overlay layer and armour are hidden while the uniform is on ({@code PlayerModelMixin}, {@code HumanoidArmorLayerMixin}).
 *
 * <p>The cyan chest star and eye lenses glow (an emissive {@code eyes} pass of {@code nova_suit_glow.png}); while NOVA
 * OVERLOAD runs the whole suit glows gold.
 *
 * <p><b>Suit-up</b>: golden energy wraps the body from the feet up -- texel rows are revealed bottom-up over
 * {@link NovaConfig#SUIT_UP_TICKS} with a bright gold scan line on the newest rows (frames pre-built once from the skin
 * layout: every texel's height on the body is known from the vanilla player cube UVs). Suit-down plays it backwards, the
 * suit unravelling from the head down.
 */
public final class NovaSuitRender {
	public static final ResourceLocation SUIT = ProjectHeroMod.id("textures/entity/nova/nova_suit.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/nova/nova_suit_glow.png");
	/** Model pixels from the top of the head to the soles. */
	public static final int ROWS = 32;
	private static final int EDGE_ARGB = 0xFFFFE27A;

	private static PlayerModel<LivingEntity> model;
	private static ResourceLocation[] frames;
	private static ResourceLocation[] edges;

	private NovaSuitRender() {
	}

	/** The wide suit model (built once). */
	public static PlayerModel<LivingEntity> model() {
		if (model == null) {
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.04f), false);
			model = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return model;
	}

	// ---------------------------------------------------------------- state

	private static NovaState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.NOVA_STATE, null);
	}

	/**
	 * How much of the uniform is on: 0 = none, 1 = all, in between while it wraps on (suit-up) or unravels (suit-down).
	 */
	public static float progress(Player player, float partial) {
		NovaState s = state(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return 0f;
		}
		float age = player.level().getGameTime() - s.suitChangeAt + partial;
		if (s.suited) {
			if (s.suitChangeAt <= 0L || age >= NovaConfig.SUIT_UP_TICKS || age < 0f) {
				return 1f;
			}
			return Math.max(0.02f, age / NovaConfig.SUIT_UP_TICKS);
		}
		if (s.suitChangeAt <= 0L || age >= NovaConfig.SUIT_DOWN_TICKS || age < 0f) {
			return 0f;
		}
		return 1f - age / NovaConfig.SUIT_DOWN_TICKS;
	}

	/** Is any of the uniform on this player (fully, or wrapping / unravelling)? */
	public static boolean visible(Player player) {
		return progress(player, 0f) > 0f && !player.isInvisible();
	}

	/** The wearer's own skin overlay and armour are hidden whenever any of the uniform is on. */
	public static boolean hidesSkinOverlay(Player player) {
		return progress(player, 0f) > 0f;
	}

	public static boolean overloaded(Player player) {
		NovaState s = state(player);
		return s != null && s.suited && s.overloadUntil > player.level().getGameTime();
	}

	// ---------------------------------------------------------------- drawing

	/** Copies {@code parent}'s pose onto the suit model, every part shown. */
	public static PlayerModel<LivingEntity> posed(HumanoidModel<?> parent) {
		PlayerModel<LivingEntity> m = model();
		copy(parent, m);
		return m;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void copy(HumanoidModel<?> parent, PlayerModel<LivingEntity> m) {
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		m.setAllVisible(true);
	}

	/**
	 * Draws the uniform over {@code parent} (already posed for this frame) at {@code progress} of the way on, faded to
	 * {@code alpha}.
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, float progress, float alpha,
			boolean overload) {
		if (progress <= 0f || alpha <= 0.01f) {
			return;
		}
		PlayerModel<LivingEntity> m = posed(parent);
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
		int color = FastColor.ARGB32.color(a, 255, 255, 255);
		boolean full = progress >= 0.999f;
		ResourceLocation tex = full ? SUIT : frame(progress, false);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, color);
		if (full) {
			// the cyan star and lenses: full-bright but ordinary alpha (an additive pass washed them out to white)
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
			if (overload) {
				float pulse = 0.22f + 0.1f * (float) Math.sin((System.currentTimeMillis() % 100000L) / 120.0);
				int g = FastColor.ARGB32.color(255, Math.round(255 * pulse), Math.round(200 * pulse), Math.round(70 * pulse));
				m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(SUIT)), light, OverlayTexture.NO_OVERLAY, g);
			}
		} else {
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(frame(progress, true))), light, OverlayTexture.NO_OVERLAY, color);
		}
	}

	/** First person: the suit arm over the arm vanilla just drew ({@code arm} is the vanilla arm part, already posed). */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, float progress) {
		PlayerModel<LivingEntity> m = model();
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		boolean full = progress >= 0.999f;
		ResourceLocation tex = full ? SUIT : frame(progress, false);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		if (!full) {
			a.render(pose, buffers.getBuffer(RenderType.eyes(frame(progress, true))), light, OverlayTexture.NO_OVERLAY);
			sleeve.render(pose, buffers.getBuffer(RenderType.eyes(frame(progress, true))), light, OverlayTexture.NO_OVERLAY);
		}
	}

	// ---------------------------------------------------------------- the reveal frames

	private static ResourceLocation frame(float progress, boolean edge) {
		if (frames == null) {
			build();
		}
		if (frames.length == 0) {
			return SUIT;
		}
		int k = Math.max(0, Math.min(ROWS, Math.round(progress * ROWS)));
		return edge ? edges[k] : frames[k];
	}

	/**
	 * Every texel's height on the body, in model pixels from the top of the head (0) to the soles (32); -1 for texels no
	 * cube face uses. Base and outer layers share their cube's geometry.
	 */
	static float[] heights() {
		float[] h = new float[64 * 64];
		java.util.Arrays.fill(h, -1f);
		// u, v, w, h, d, top -- head + hat, body + jacket, arms + sleeves, legs + trousers
		int[][] cubes = {
				{ 0, 0, 8, 8, 8, 0 }, { 32, 0, 8, 8, 8, 0 },
				{ 16, 16, 8, 12, 4, 8 }, { 16, 32, 8, 12, 4, 8 },
				{ 40, 16, 4, 12, 4, 8 }, { 40, 32, 4, 12, 4, 8 }, { 32, 48, 4, 12, 4, 8 }, { 48, 48, 4, 12, 4, 8 },
				{ 0, 16, 4, 12, 4, 20 }, { 0, 32, 4, 12, 4, 20 }, { 16, 48, 4, 12, 4, 20 }, { 0, 48, 4, 12, 4, 20 } };
		for (int[] c : cubes) {
			int u = c[0], v = c[1], w = c[2], hh = c[3], d = c[4], top = c[5];
			// top and bottom faces
			for (int y = v; y < v + d; y++) {
				for (int x = u + d; x < u + d + w; x++) {
					h[y * 64 + x] = top;
				}
				for (int x = u + d + w; x < u + d + 2 * w; x++) {
					h[y * 64 + x] = top + hh;
				}
			}
			// the four sides
			for (int y = v + d; y < v + d + hh; y++) {
				for (int x = u; x < u + 2 * d + 2 * w; x++) {
					h[y * 64 + x] = top + (y - v - d) + 0.5f;
				}
			}
		}
		return h;
	}

	private static void build() {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> res = mc.getResourceManager().getResource(SUIT);
		if (res.isEmpty()) {
			frames = new ResourceLocation[0];
			return;
		}
		NativeImage src;
		try (InputStream in = res.get().open()) {
			src = NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[nova] could not read the suit texture for the suit-up wrap", e);
			frames = new ResourceLocation[0];
			return;
		}
		float[] h = heights();
		frames = new ResourceLocation[ROWS + 1];
		edges = new ResourceLocation[ROWS + 1];
		int edgeAbgr = toAbgr(EDGE_ARGB);
		for (int k = 0; k <= ROWS; k++) {
			float from = ROWS - k; // texels at or below this height (counted down from the head) are on
			NativeImage img = new NativeImage(64, 64, true);
			NativeImage edge = new NativeImage(64, 64, true);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int px = src.getPixelRGBA(x, y);
					float th = h[y * 64 + x];
					boolean on = th >= 0f && th >= from;
					boolean rim = on && th < from + 2.0f && k < ROWS;
					int alpha = (px >>> 24) & 0xFF;
					img.setPixelRGBA(x, y, on ? (rim && alpha > 0 ? edgeAbgr : px) : 0);
					edge.setPixelRGBA(x, y, rim && alpha > 0 ? edgeAbgr : 0);
				}
			}
			frames[k] = mc.getTextureManager().register("projecthero_nova_wrap_" + k, new DynamicTexture(img));
			edges[k] = mc.getTextureManager().register("projecthero_nova_wrap_edge_" + k, new DynamicTexture(edge));
		}
		src.close();
	}

	/** NativeImage pixels are ABGR. */
	private static int toAbgr(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >>> 16) & 0xFF;
		int g = (argb >>> 8) & 0xFF;
		int b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}
}
