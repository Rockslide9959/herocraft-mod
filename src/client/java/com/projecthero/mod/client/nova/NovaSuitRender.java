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
import net.minecraft.util.Mth;
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
 * <p><b>v0.15.15 suit-up</b> ({@link #anim}): the Nova Corps Helmet -- the suit model's own head and helmet layer --
 * appears in both hands in front of the chest, is raised overhead and lowered onto the head ({@link NovaPose} aims the
 * arms at it), then the rest of the uniform materialises from the neck down: texel rows are revealed top-down with a
 * bright gold scan line on the newest rows (frames pre-built once from the skin layout: every texel's height on the body
 * is known from the vanilla player cube UVs). <b>Suit-down</b> runs it the other way: the body dematerialises from the
 * feet up to the neck, then the hands lift the helmet off and it dissolves overhead.
 */
public final class NovaSuitRender {
	public static final ResourceLocation SUIT = ProjectHeroMod.id("textures/entity/nova/nova_suit.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/nova/nova_suit_glow.png");
	/** Model pixels from the top of the head to the soles. */
	public static final int ROWS = 32;
	/** Model pixels from the top of the head to the neck: the helmet. */
	public static final int HEAD_ROWS = 8;
	private static final int EDGE_ARGB = 0xFFFFE27A;

	private static PlayerModel<LivingEntity> model;
	private static ResourceLocation[] frames;
	private static ResourceLocation[] edges;

	private NovaSuitRender() {
	}

	/**
	 * Where the suit-up / suit-down is at this frame.
	 *
	 * @param cut         how far down the uniform is on, in model pixels from the top of the head (0 = nothing,
	 *                    {@link #HEAD_ROWS} = just the helmet, {@link #ROWS} = all of it)
	 * @param helmetHeld  the helmet is in the hands (not on the head): drawn on its own at {@code hy} / {@code hz}
	 * @param hy          the held helmet's head-pivot offset from the wearer's head pivot, model pixels (negative = up)
	 * @param hz          ... and forward offset (negative = in front)
	 * @param helmetAlpha the held helmet's opacity (it materialises in the hands / dissolves overhead)
	 * @param arms        0..1: how far the arms are taken over to hold the helmet
	 * @param fresh       the newest rows glow (a reveal or dissolve is running)
	 */
	public record Anim(float cut, boolean helmetHeld, float hy, float hz, float helmetAlpha, float arms, boolean fresh) {
		static final Anim NONE = new Anim(0f, false, 0f, 0f, 0f, 0f, false);
		static final Anim FULL = new Anim(ROWS, false, 0f, 0f, 0f, 0f, false);

		public boolean anything() {
			return cut > 0f || (helmetHeld && helmetAlpha > 0.01f);
		}
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

	private static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** The helmet held in front of the chest (start of the raise) and overhead (top of the raise). */
	private static final float CHEST_Y = 3f;
	private static final float CHEST_Z = -8f;
	private static final float OVER_Y = -7f;
	private static final float OVER_Z = -1f;

	/** The suit-up / suit-down at this moment (see {@link Anim}). */
	public static Anim anim(Player player, float partial) {
		NovaState s = state(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return Anim.NONE;
		}
		float age = player.level().getGameTime() - s.suitChangeAt + partial;
		if (s.suited) {
			if (s.suitChangeAt <= 0L || age >= NovaConfig.SUIT_UP_TICKS || age < 0f) {
				return Anim.FULL;
			}
			return suitUp(age);
		}
		if (s.suitChangeAt <= 0L || age >= NovaConfig.SUIT_DOWN_TICKS || age < 0f) {
			return Anim.NONE;
		}
		return suitDown(age);
	}

	/** Suit-up: raise (helmet chest -> overhead), lower (overhead -> head), then the body from the neck down. */
	static Anim suitUp(float age) {
		float raise = NovaConfig.SUIT_HELMET_RAISE_TICKS;
		float on = NovaConfig.SUIT_HELMET_ON_TICK;
		if (age < raise) {
			float t = smooth(age / raise);
			return new Anim(0f, true, Mth.lerp(t, CHEST_Y, OVER_Y), Mth.lerp(t, CHEST_Z, OVER_Z), Math.min(1f, age / 4f), 1f, false);
		}
		if (age < on) {
			float t = smooth((age - raise) / (on - raise));
			return new Anim(0f, true, Mth.lerp(t, OVER_Y, 0f), Mth.lerp(t, OVER_Z, 0f), 1f, 1f, false);
		}
		float body = (age - on) / (NovaConfig.SUIT_UP_TICKS - on);
		float arms = 1f - Mth.clamp((age - on) / 8f, 0f, 1f);
		return new Anim(HEAD_ROWS + (ROWS - HEAD_ROWS) * Mth.clamp(body, 0f, 1f), false, 0f, 0f, 0f, arms, true);
	}

	/** Suit-down: the body from the feet up to the neck, the hands come up, the helmet is lifted off and dissolves. */
	static Anim suitDown(float age) {
		float body = NovaConfig.SUIT_DOWN_BODY_TICKS;
		float total = NovaConfig.SUIT_DOWN_TICKS;
		float grab = body + 5f; // hands on the helmet
		float lifted = total - 7f; // overhead
		if (age < body) {
			float t = age / body;
			return new Anim(HEAD_ROWS + (ROWS - HEAD_ROWS) * (1f - t), false, 0f, 0f, 0f, 0f, true);
		}
		if (age < grab) {
			float w = smooth((age - body) / (grab - body));
			return new Anim(HEAD_ROWS, false, 0f, 0f, 0f, w, false);
		}
		if (age < lifted) {
			float t = smooth((age - grab) / (lifted - grab));
			return new Anim(0f, true, Mth.lerp(t, 0f, OVER_Y), Mth.lerp(t, 0f, OVER_Z), 1f, 1f, false);
		}
		float t = Mth.clamp((age - lifted) / (total - lifted), 0f, 1f);
		return new Anim(0f, true, OVER_Y - 1.5f * t, OVER_Z, 1f - t, 1f - smooth(t), false);
	}

	/**
	 * How much of the uniform is on the body, 0..1 ({@link Anim#cut} / {@link #ROWS}): 0 while the helmet is only in the
	 * hands, {@code 0.25} with just the helmet on, 1 fully suited. (The first-person sleeve uses it.)
	 */
	public static float progress(Player player, float partial) {
		return anim(player, partial).cut() / ROWS;
	}

	/** Is any of the uniform on this player (fully, materialising / dematerialising, or the helmet in his hands)? */
	public static boolean visible(Player player) {
		return anim(player, 0f).anything() && !player.isInvisible();
	}

	/** The wearer's own skin overlay and armour are hidden whenever any of the uniform is on the body. */
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

	/** A player's uniform at this frame: the body (as far as it is on) and the helmet in the hands, if it is. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, Anim a, boolean overload) {
		if (a.cut() > 0f) {
			renderBody(pose, buffers, light, parent, a.cut(), 1f, overload, a.fresh());
		}
		if (a.helmetHeld() && a.helmetAlpha() > 0.01f) {
			renderHeldHelmet(pose, buffers, light, parent, a.hy(), a.hz(), a.helmetAlpha());
		}
	}

	/**
	 * Draws the uniform over {@code parent} (already posed for this frame) at {@code progress} of the way on (0..1 of
	 * {@link #ROWS}, top-down), faded to {@code alpha}. (The Centurion uses it with progress 1.)
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, float progress, float alpha,
			boolean overload) {
		renderBody(pose, buffers, light, parent, progress * ROWS, alpha, overload, progress < 0.999f);
	}

	private static void renderBody(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, float cut, float alpha,
			boolean overload, boolean fresh) {
		if (cut <= 0f || alpha <= 0.01f) {
			return;
		}
		PlayerModel<LivingEntity> m = posed(parent);
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
		int color = FastColor.ARGB32.color(a, 255, 255, 255);
		boolean full = cut >= ROWS - 0.01f;
		ResourceLocation tex = full ? SUIT : frame(cut, false);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, color);
		if (full || cut >= HEAD_ROWS - 0.01f) {
			// the cyan star and lenses: full-bright but ordinary alpha (an additive pass washed them out to white)
			PlayerModel<LivingEntity> g = m;
			if (!full) {
				// still materialising: only the helmet's lenses glow (the star lights up once the chest is whole)
				g.setAllVisible(false);
				g.head.visible = true;
				g.hat.visible = true;
			}
			g.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
			g.setAllVisible(true);
			if (full && overload) {
				float pulse = 0.22f + 0.1f * (float) Math.sin((System.currentTimeMillis() % 100000L) / 120.0);
				int gc = FastColor.ARGB32.color(255, Math.round(255 * pulse), Math.round(200 * pulse), Math.round(70 * pulse));
				m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(SUIT)), light, OverlayTexture.NO_OVERLAY, gc);
			}
		}
		if (!full && fresh) {
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(frame(cut, true))), light, OverlayTexture.NO_OVERLAY, color);
		}
	}

	/**
	 * The Nova Corps Helmet in the hands: the suit model's head and helmet layer on their own, upright and facing the
	 * way the body faces, its pivot {@code hy} / {@code hz} model pixels from the wearer's head pivot; a golden sheen while
	 * it materialises or dissolves.
	 */
	public static void renderHeldHelmet(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, float hy, float hz,
			float alpha) {
		PlayerModel<LivingEntity> m = model();
		m.head.resetPose();
		m.head.x = parent.head.x;
		m.head.y = parent.head.y + hy;
		m.head.z = parent.head.z + hz;
		m.hat.copyFrom(m.head);
		int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255f);
		int color = FastColor.ARGB32.color(a, 255, 255, 255);
		m.head.render(pose, buffers.getBuffer(RenderType.entityTranslucent(SUIT)), light, OverlayTexture.NO_OVERLAY, color);
		m.hat.render(pose, buffers.getBuffer(RenderType.entityTranslucent(SUIT)), light, OverlayTexture.NO_OVERLAY, color);
		m.head.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
		m.hat.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
		if (alpha < 0.999f) {
			// materialising / dissolving: a gold sheen over it
			float k = 1f - alpha;
			int gc = FastColor.ARGB32.color(255, Math.round(255 * k), Math.round(200 * k), Math.round(80 * k));
			m.hat.render(pose, buffers.getBuffer(RenderType.eyes(SUIT)), light, OverlayTexture.NO_OVERLAY, gc);
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
		float cut = progress * ROWS;
		boolean full = cut >= ROWS - 0.01f;
		ResourceLocation tex = full ? SUIT : frame(cut, false);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		if (!full) {
			a.render(pose, buffers.getBuffer(RenderType.eyes(frame(cut, true))), light, OverlayTexture.NO_OVERLAY);
			sleeve.render(pose, buffers.getBuffer(RenderType.eyes(frame(cut, true))), light, OverlayTexture.NO_OVERLAY);
		}
	}

	// ---------------------------------------------------------------- the reveal frames

	/** Frame for a cut-off {@code cut} model pixels down from the top of the head. */
	private static ResourceLocation frame(float cut, boolean edge) {
		if (frames == null) {
			build();
		}
		if (frames.length == 0) {
			return SUIT;
		}
		int k = Math.max(0, Math.min(ROWS, Math.round(cut)));
		return edge ? edges[k] : frames[k];
	}

	/**
	 * Every texel's height on the body, in model pixels from the top of the head (0) to the soles (32); -1 for texels no
	 * cube face uses. Base and outer layers share their cube's geometry. The head's underside counts as 7.9, so a cut at
	 * the neck (8) shows the whole helmet and none of the shoulders.
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
			float bottom = top == 0 ? hh - 0.1f : top + hh;
			// top and bottom faces
			for (int y = v; y < v + d; y++) {
				for (int x = u + d; x < u + d + w; x++) {
					h[y * 64 + x] = top;
				}
				for (int x = u + d + w; x < u + d + 2 * w; x++) {
					h[y * 64 + x] = bottom;
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

	/** Top-down: frame {@code k} shows every texel at or above {@code k} model pixels down from the top of the head. */
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
			ProjectHeroMod.LOGGER.warn("[nova] could not read the suit texture for the suit-up reveal", e);
			frames = new ResourceLocation[0];
			return;
		}
		float[] h = heights();
		frames = new ResourceLocation[ROWS + 1];
		edges = new ResourceLocation[ROWS + 1];
		int edgeAbgr = toAbgr(EDGE_ARGB);
		for (int k = 0; k <= ROWS; k++) {
			NativeImage img = new NativeImage(64, 64, true);
			NativeImage edge = new NativeImage(64, 64, true);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int px = src.getPixelRGBA(x, y);
					float th = h[y * 64 + x];
					boolean on = th >= 0f && th < k;
					boolean rim = on && k > HEAD_ROWS && k < ROWS && th >= k - 2.0f && th >= HEAD_ROWS;
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
