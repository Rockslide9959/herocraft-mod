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
	/** The metal half of the head alone, drawn on {@link #cover()} just outside the wearer's own hat layer. */
	public static final ResourceLocation HEAD_COVER = ProjectHeroMod.id("textures/entity/cyborg/cyborg_head_cover.png");
	/** The chest core sits this many model pixels down from the top of the head. */
	public static final float CORE_HEIGHT = 11f;
	/** The farthest texel from the core (the soles). */
	public static final int STEPS = 21;
	private static final int EDGE_ARGB = 0xFF6AD8FF;

	private static PlayerModel<LivingEntity> model;
	private static ModelPart cover;
	private static ResourceLocation[] frames;
	private static ResourceLocation[] edges;

	private CyborgRender() {
	}

	/**
	 * @param spread how far the plates reach from the core, model pixels (0 = none, {@link #STEPS} = all of him)
	 * @param fresh  the newest texels glow (a change is running)
	 * @param holo   how far down him the hologram reaches, 0..1 (every texel whose {@link #holoOrder} is at or below it is
	 *               hologram); 1 = the whole hologram (he just looks like his old self: nothing of Cyborg is drawn)
	 * @param building the hologram is building (the forehead projector is on); false while it drains away
	 */
	public record Anim(float spread, boolean fresh, float holo, boolean building) {
		static final Anim NONE = new Anim(0f, false, 0f, false);
		static final Anim FULL = new Anim(STEPS, false, 0f, false);
		static final Anim HOLOGRAM = new Anim(STEPS, false, 1f, false);

		public boolean anything() {
			return spread > 0f && holo < 1f;
		}

		public boolean hologramShowing() {
			return holo > 0f && holo < 1f;
		}
	}

	public static PlayerModel<LivingEntity> model() {
		if (model == null) {
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.04f), false);
			model = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return model;
	}

	/**
	 * A head cube a hair outside the vanilla hat layer (+0.5): with {@link #HEAD_COVER} on it, the metal half of the head
	 * hides the wearer's hat / hair layer there, while on the human half (transparent in the cover) it shows.
	 */
	private static ModelPart cover() {
		if (cover == null) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("head", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(0, 0)
					.addBox(-4f, -8f, -4f, 8f, 8f, 8f, new CubeDeformation(0.52f)), net.minecraft.client.model.geom.PartPose.ZERO);
			cover = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("head");
		}
		return cover;
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
		boolean running = s.formChangeAt > 0L && age >= 0f;
		if (s.combat && s.animId == CyborgState.ANIM_CONVERT && running && age < CyborgConfig.CONVERT_TICKS) {
			// the Mother Box fusing: the plates spread from the core, hesitating and jerking as it fights the body
			float t = Mth.clamp(age / CyborgConfig.CONVERT_TICKS, 0f, 1f) + 0.03f * Mth.sin(age * 0.9f);
			return new Anim(STEPS * Mth.clamp(t, 0f, 1f), true, 0f, false);
		}
		if (!s.combat) {
			// the hologram building over him from the top of the head down
			if (running && age < CyborgConfig.PLATES_OFF_TICKS) {
				return new Anim(STEPS, false, Mth.clamp(age / CyborgConfig.PLATES_OFF_TICKS, 0f, 1f), true);
			}
			return Anim.HOLOGRAM;
		}
		if (s.animId == CyborgState.ANIM_PLATES_ON && running && age < CyborgConfig.PLATES_ON_TICKS) {
			// the hologram dropped: it drains away from the feet up
			return new Anim(STEPS, false, 1f - Mth.clamp(age / CyborgConfig.PLATES_ON_TICKS, 0f, 1f), false);
		}
		return Anim.FULL;
	}

	/** How much of him is plates, 0..1 (0 with the whole hologram up: first person then shows his old arm). */
	public static float progress(Player player, float partial) {
		Anim a = anim(player, partial);
		return a.holo() >= 1f ? 0f : a.spread() / STEPS;
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
		int color = 0xFFFFFFFF;
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, color);
		if (full && a.holo() <= 0f) {
			ModelPart c = cover();
			c.copyFrom(parent.head);
			c.render(pose, buffers.getBuffer(RenderType.entityTranslucent(HEAD_COVER)), light, OverlayTexture.NO_OVERLAY, color);
		}
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

	// ---------------------------------------------------------------- the hologram

	/** The cyan of the projector light and of the hologram's building edge. */
	private static final int HOLO_EDGE = 0xFF7FE6FF;
	/** The newest texels of a building hologram are cyan for this much of the line. */
	private static final float HOLO_RIM = 0.035f;
	/** The blue lamp on his forehead (head-part space, model pixels), just outside the head cover. */
	private static final float LAMP_X = 0.5f, LAMP_Y = -6.5f, LAMP_Z = -4.6f;

	/**
	 * When base texel ({@code u}, {@code v}) is reached by the hologram, 0..1: the top of the head first, row by row down to
	 * the soles, and within a row one texel after another in a scattered order, so it visibly builds a pixel at a time.
	 */
	public static float holoOrder(int u, int v) {
		float h = com.projecthero.mod.client.render.OverlayStrips.height(u, v);
		if (h < 0f) {
			return 2f;
		}
		int k = u * 73856093 ^ v * 19349663;
		k = (k ^ (k >>> 13)) * 1274126177;
		float jitter = ((k >>> 8) & 0xFFFF) / 65536f;
		return Mth.clamp((Math.min(h, 31.99f) + jitter * 0.98f) / 32f, 0f, 1f);
	}

	/** The hologram's tint for one texel at {@code line}: 0 = not there (yet / any more), cyan on the building edge, else white. */
	public static int holoTint(int u, int v, float line, boolean building) {
		float o = holoOrder(u, v);
		if (building ? o > line : o > line + 1e-4f || line <= 0f) {
			return 0;
		}
		return building && line - o < HOLO_RIM ? HOLO_EDGE : 0xFFFFFFFF;
	}

	/**
	 * The building / draining hologram over the plates: the wearer's own skin, base and overlay, a hair outside the plates,
	 * texel by texel; while it builds, a cone of cyan light from the lamp on his forehead to the line it is drawing.
	 */
	public static void renderHologram(PoseStack pose, MultiBufferSource buffers, int light, net.minecraft.client.player.AbstractClientPlayer player,
			PlayerModel<?> parent, Anim a, float ageInTicks) {
		float line = a.holo();
		boolean building = a.building();
		com.projecthero.mod.client.render.OverlayStrips.renderSkin(pose, buffers, light, player, parent,
				(u, v) -> holoTint(u, v, line, building), true, true, 0.07f);
		if (building) {
			renderProjector(pose, buffers, parent, line, ageInTicks);
		}
	}

	private static void renderProjector(PoseStack pose, MultiBufferSource buffers, HumanoidModel<?> parent, float line, float age) {
		// the lamp, in model space
		PoseStack lamp = new PoseStack();
		parent.head.translateAndRotate(lamp);
		org.joml.Vector4f p = new org.joml.Vector4f(LAMP_X / 16f, LAMP_Y / 16f, LAMP_Z / 16f, 1f);
		lamp.last().pose().transform(p);
		// the line it is drawing: a ring round him at that height (model y: top of the head -0.5, soles 1.5)
		float h = line * 32f;
		float y = (h - 8f) / 16f;
		float hx = h < 8f ? 0.30f : 0.55f, hz = h < 8f ? 0.30f : 0.22f;
		com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		org.joml.Matrix4f m = pose.last().pose();
		float flicker = 0.75f + 0.25f * Mth.sin(age * 3.1f);
		float[][] ring = { { -hx, -hz }, { hx, -hz }, { hx, hz }, { -hx, hz } };
		for (int i = 0; i < 4; i++) {
			float[] c0 = ring[i], c1 = ring[(i + 1) % 4];
			// the cone, faint, from both sides (the lightning type is culled)
			tri(vc, m, p.x(), p.y(), p.z(), c0[0], y, c0[1], c1[0], y, c1[1], 0.55f * flicker);
			tri(vc, m, p.x(), p.y(), p.z(), c1[0], y, c1[1], c0[0], y, c0[1], 0.55f * flicker);
			// a bright ray from the lamp down each edge of the cone
			float rw = 0.012f;
			quadGlowA(vc, m, p.x() - rw, p.y(), p.z(), p.x() + rw, p.y(), p.z(), c0[0] + rw, y, c0[1], c0[0] - rw, y, c0[1], 0.85f * flicker);
			quadGlowA(vc, m, c0[0] - rw, y, c0[1], c0[0] + rw, y, c0[1], p.x() + rw, p.y(), p.z(), p.x() - rw, p.y(), p.z(), 0.85f * flicker);
			// the scan line itself, a bright band
			band(vc, m, c0[0], c0[1], c1[0], c1[1], y, 0.9f * flicker);
		}
		// the lamp's own glow, a little diamond facing forward
		float r = 0.04f;
		float z = p.z() - 0.005f;
		quadGlow(vc, m, p.x(), p.y() - r, z, p.x() + r, p.y(), z, p.x(), p.y() + r, z, p.x() - r, p.y(), z);
		quadGlow(vc, m, p.x() - r, p.y(), z, p.x(), p.y() + r, z, p.x() + r, p.y(), z, p.x(), p.y() - r, z);
	}

	/** One triangle on the lightning type (a quad type: the last corner repeats). Bright at the apex, fading outward. */
	private static void tri(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, float x0, float y0, float z0, float x1, float y1,
			float z1, float x2, float y2, float z2, float alpha) {
		vc.addVertex(m, x0, y0, z0).setColor(0.55f, 0.92f, 1f, alpha);
		vc.addVertex(m, x1, y1, z1).setColor(0.3f, 0.75f, 1f, alpha * 0.35f);
		vc.addVertex(m, x2, y2, z2).setColor(0.3f, 0.75f, 1f, alpha * 0.35f);
		vc.addVertex(m, x2, y2, z2).setColor(0.3f, 0.75f, 1f, alpha * 0.35f);
	}

	private static void quadGlow(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, float x0, float y0, float z0, float x1, float y1,
			float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		vc.addVertex(m, x0, y0, z0).setColor(0.8f, 0.97f, 1f, 1f);
		vc.addVertex(m, x1, y1, z1).setColor(0.8f, 0.97f, 1f, 1f);
		vc.addVertex(m, x2, y2, z2).setColor(0.8f, 0.97f, 1f, 1f);
		vc.addVertex(m, x3, y3, z3).setColor(0.8f, 0.97f, 1f, 1f);
	}

	/** A thin horizontal band from (x0, z0) to (x1, z1) at height y, from both sides. */
	private static void band(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, float x0, float z0, float x1, float z1, float y,
			float alpha) {
		float t = 0.012f;
		quadGlowA(vc, m, x0, y - t, z0, x1, y - t, z1, x1, y + t, z1, x0, y + t, z0, alpha);
		quadGlowA(vc, m, x0, y + t, z0, x1, y + t, z1, x1, y - t, z1, x0, y - t, z0, alpha);
	}

	private static void quadGlowA(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, float x0, float y0, float z0, float x1, float y1,
			float z1, float x2, float y2, float z2, float x3, float y3, float z3, float alpha) {
		vc.addVertex(m, x0, y0, z0).setColor(0.7f, 0.95f, 1f, alpha);
		vc.addVertex(m, x1, y1, z1).setColor(0.7f, 0.95f, 1f, alpha);
		vc.addVertex(m, x2, y2, z2).setColor(0.7f, 0.95f, 1f, alpha);
		vc.addVertex(m, x3, y3, z3).setColor(0.7f, 0.95f, 1f, alpha);
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
