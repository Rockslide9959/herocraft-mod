package com.projecthero.mod.client.cyborg;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.cyborg.CyborgConfig;
import com.projecthero.mod.cyborg.data.CyborgState;

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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.5: Cyborg's plates drawn over a player -- the user's NovaSkin skin (post 4807583109, 128x128 HD) with Victor
 * Stone's human half of the head cut out ({@code scratchpad/gen_cyborg_v0165.js}), so the wearer's own face and hair show
 * there; on a wide player model a hair larger than the wearer's (the Nova / Ghost Rider technique). The red eye, the chest
 * core and the trims glow ({@code cyborg_glow.png}).
 *
 * <p><b>The forms</b> ({@link #anim}): the plates spread out from the chest core over the body (texels revealed in order of
 * their distance from the core, a blue edge on the newest ones) -- slowly over 10 s when the Mother Box first fuses, in
 * about a second on H. Civilian form folds them back into the core under a flickering hologram.
 */
public final class CyborgRender {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/cyborg/cyborg.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/cyborg/cyborg_glow.png");
	/** The chest core sits this many model pixels down from the top of the head. */
	private static final float CORE_HEIGHT = 11f;
	/** The farthest texel from the core (the soles). */
	public static final int STEPS = 21;
	private static final int EDGE_ARGB = 0xFF6AD8FF;

	private static PlayerModel<LivingEntity> model;
	private static ResourceLocation[] frames;
	private static ResourceLocation[] edges;

	private CyborgRender() {
	}

	/**
	 * @param spread how far the plates reach from the core, model pixels (0 = none, {@link #STEPS} = all of him)
	 * @param fresh  the newest texels glow (a change is running)
	 * @param holo   0..1 the civilian hologram flicker over what is left
	 */
	public record Anim(float spread, boolean fresh, float holo) {
		static final Anim NONE = new Anim(0f, false, 0f);
		static final Anim FULL = new Anim(STEPS, false, 0f);

		public boolean anything() {
			return spread > 0f;
		}
	}

	public static PlayerModel<LivingEntity> model() {
		if (model == null) {
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.04f), false);
			model = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return model;
	}

	public static Anim anim(Player player, float partial) {
		CyborgState s = player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
		if (s == null || !s.hasPower || player.level() == null) {
			return Anim.NONE;
		}
		float age = player.level().getGameTime() - s.formChangeAt + partial;
		if (age < 0f && age > -40f) {
			age = 0f;
		}
		if (s.combat) {
			float total = s.animId == CyborgState.ANIM_CONVERT ? CyborgConfig.CONVERT_TICKS : CyborgConfig.PLATES_ON_TICKS;
			if (s.formChangeAt <= 0L || age >= total || age < 0f) {
				return Anim.FULL;
			}
			float t = Mth.clamp(age / total, 0f, 1f);
			// the conversion hesitates and jerks as the box fights the body; H is one smooth sweep
			if (s.animId == CyborgState.ANIM_CONVERT) {
				t = t + 0.03f * Mth.sin(age * 0.9f);
			}
			return new Anim(STEPS * Mth.clamp(t, 0f, 1f), true, 0f);
		}
		float total = CyborgConfig.PLATES_OFF_TICKS;
		if (s.formChangeAt <= 0L || age >= total || age < 0f) {
			return Anim.NONE;
		}
		float t = Mth.clamp(age / total, 0f, 1f);
		return new Anim(STEPS * (1f - t), true, 1f - t);
	}

	public static float progress(Player player, float partial) {
		return anim(player, partial).spread() / STEPS;
	}

	public static boolean hidesSkinOverlay(Player player) {
		return progress(player, 0f) > 0f;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static PlayerModel<LivingEntity> posed(HumanoidModel<?> parent) {
		PlayerModel<LivingEntity> m = model();
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		m.setAllVisible(true);
		return m;
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, Anim a, float ageInTicks) {
		if (a.spread() <= 0f) {
			return;
		}
		PlayerModel<LivingEntity> m = posed(parent);
		boolean full = a.spread() >= STEPS - 0.01f;
		ResourceLocation tex = full ? SKIN : frame(a.spread(), false);
		int alpha = 255;
		if (a.holo() > 0f) {
			// the hologram: the folding plates flicker
			alpha = Math.round(255 * (0.55f + 0.45f * Math.abs(Mth.sin(ageInTicks * 1.7f))));
		}
		int color = (alpha << 24) | 0xFFFFFF;
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, color);
		if (full) {
			float pulse = 0.8f + 0.2f * Mth.sin(ageInTicks * 0.25f);
			int g = Math.round(255 * pulse);
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					(g << 24) | 0xFFFFFF);
		} else if (a.fresh()) {
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(frame(a.spread(), true))), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		}
	}

	/** First person: the plated arm over the arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, float progress) {
		PlayerModel<LivingEntity> m = model();
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		float spread = progress * STEPS;
		boolean full = spread >= STEPS - 0.01f;
		ResourceLocation tex = full ? SKIN : frame(spread, false);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		if (full) {
			a.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		}
	}

	// ---------------------------------------------------------------- the spread frames

	private static ResourceLocation frame(float spread, boolean edge) {
		if (frames == null) {
			build();
		}
		if (frames.length == 0) {
			return SKIN;
		}
		int k = Math.max(0, Math.min(STEPS, Math.round(spread)));
		return edge ? edges[k] : frames[k];
	}

	private static float[] heights() {
		float[] h = new float[64 * 64];
		java.util.Arrays.fill(h, -1f);
		int[][] cubes = {
				{ 0, 0, 8, 8, 8, 0 }, { 32, 0, 8, 8, 8, 0 },
				{ 16, 16, 8, 12, 4, 8 }, { 16, 32, 8, 12, 4, 8 },
				{ 40, 16, 4, 12, 4, 8 }, { 40, 32, 4, 12, 4, 8 }, { 32, 48, 4, 12, 4, 8 }, { 48, 48, 4, 12, 4, 8 },
				{ 0, 16, 4, 12, 4, 20 }, { 0, 32, 4, 12, 4, 20 }, { 16, 48, 4, 12, 4, 20 }, { 0, 48, 4, 12, 4, 20 } };
		for (int[] c : cubes) {
			int u = c[0], v = c[1], w = c[2], hh = c[3], d = c[4], top = c[5];
			float bottom = top == 0 ? hh - 0.1f : top + hh;
			for (int y = v; y < v + d; y++) {
				for (int x = u + d; x < u + d + w; x++) {
					h[y * 64 + x] = top;
				}
				for (int x = u + d + w; x < u + d + 2 * w; x++) {
					h[y * 64 + x] = bottom;
				}
			}
			for (int y = v + d; y < v + d + hh; y++) {
				for (int x = u; x < u + 2 * d + 2 * w; x++) {
					h[y * 64 + x] = top + (y - v - d) + 0.5f;
				}
			}
		}
		return h;
	}

	/** Frame {@code k} shows every texel within {@code k} model pixels (up or down) of the chest core. */
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
			ProjectHeroMod.LOGGER.warn("[cyborg] could not read the skin for the plates", e);
			frames = new ResourceLocation[0];
			return;
		}
		int size = src.getWidth();
		int scale = Math.max(1, size / 64);
		float[] h = heights();
		frames = new ResourceLocation[STEPS + 1];
		edges = new ResourceLocation[STEPS + 1];
		int edgeAbgr = toAbgr(EDGE_ARGB);
		for (int k = 0; k <= STEPS; k++) {
			NativeImage img = new NativeImage(size, size, true);
			NativeImage edge = new NativeImage(size, size, true);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					int px = src.getPixelRGBA(x, y);
					float th = h[(y / scale) * 64 + (x / scale)];
					float dist = th < 0f ? 99f : Math.abs(th - CORE_HEIGHT);
					boolean on = dist <= k && k > 0;
					boolean rim = on && k < STEPS && dist > k - 1.6f;
					int alpha = (px >>> 24) & 0xFF;
					img.setPixelRGBA(x, y, on ? (rim && alpha > 0 ? edgeAbgr : px) : 0);
					edge.setPixelRGBA(x, y, rim && alpha > 0 ? edgeAbgr : 0);
				}
			}
			frames[k] = mc.getTextureManager().register("projecthero_cyborg_spread_" + k, new DynamicTexture(img));
			edges[k] = mc.getTextureManager().register("projecthero_cyborg_spread_edge_" + k, new DynamicTexture(edge));
		}
		src.close();
	}

	private static int toAbgr(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >>> 16) & 0xFF;
		int g = (argb >>> 8) & 0xFF;
		int b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}
}
