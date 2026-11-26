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
 * v0.16.5: Cyborg's plates drawn over a player, on a player model a hair larger than the wearer's (the Nova / Ghost Rider
 * technique). The red eye glows ({@code cyborg_glow.png}).
 *
 * <p>v0.17.7 (user): the skin is the Teen Titans Cyborg from PlanetMinecraft
 * (planetminecraft.com/skin/teen-titans-cyborg-4002965, file {@code cyborg-11202576.png}, a wide-arm 64x64 skin) with Victor
 * Stone the person cleared out on both layers ({@code scratchpad/v0177/make_cyborg_skin.js}): the wearer's own face, scalp,
 * neck and upper arms show between the plates. A slim copy ({@code cyborg_slim.png}) goes on slim players, on a slim model.
 * Where both of Cyborg's layers are clear the wearer's own second layer is drawn too ({@link #gap}).
 *
 * <p><b>The forms</b> ({@link #anim}): the plates spread out from the chest core over the body (texels revealed in order of
 * their distance from the core, a blue edge on the newest ones) -- slowly over 10 s when the Mother Box first fuses, in
 * about a second on H. Civilian form folds them back into the core under a flickering hologram.
 */
public final class CyborgRender {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/cyborg/cyborg.png");
	/** v0.17.7: {@link #SKIN} remapped to the slim (3-pixel arm) layout. */
	public static final ResourceLocation SKIN_SLIM = ProjectHeroMod.id("textures/entity/cyborg/cyborg_slim.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/cyborg/cyborg_glow.png");
	/** The metal half of the head alone, drawn on {@link #cover()} just outside the wearer's own hat layer. */
	public static final ResourceLocation HEAD_COVER = ProjectHeroMod.id("textures/entity/cyborg/cyborg_head_cover.png");
	/** The chest core sits this many model pixels down from the top of the head. */
	public static final float CORE_HEIGHT = 11f;
	/** The farthest texel from the core (the soles). */
	public static final int STEPS = 21;

	private static PlayerModel<LivingEntity> model, slimModel;
	private static ModelPart cover;
	/** The spread frames, [0] wide, [1] slim. */
	private static ResourceLocation[][] frames;
	/** Which texels of the wide / slim skin are opaque (64x64), for {@link #gap}; null until the skins are read. */
	private static boolean[][] opaque;

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
		return model(false);
	}

	/** v0.17.7: the plates' model for a wide or a slim player. */
	public static PlayerModel<LivingEntity> model(boolean slim) {
		if (slim) {
			if (slimModel == null) {
				MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(PLATES), true);
				slimModel = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), true);
			}
			return slimModel;
		}
		if (model == null) {
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(PLATES), false);
			model = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return model;
	}

	/** How much larger than the wearer's skin the plates are drawn, model pixels. */
	private static final float PLATES = 0.04f;
	/** The head cover's inflation: a hair outside the wearer's hat layer (+0.5). */
	private static final float COVER = 0.52f;

	public static ResourceLocation skin(boolean slim) {
		return slim ? SKIN_SLIM : SKIN;
	}

	public static boolean slim(Player player) {
		return player instanceof net.minecraft.client.player.AbstractClientPlayer p
				&& p.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
	}

	/**
	 * v0.17.7: whether both of Cyborg's layers are clear over base texel ({@code u}, {@code v}) (the wearer's own skin shows
	 * there), so the wearer's second layer is drawn there too.
	 */
	public static boolean gap(boolean slim, int u, int v) {
		if (opaque == null) {
			build();
		}
		boolean[] o = opaque[slim ? 1 : 0];
		if (o == null) {
			return false;
		}
		int over = com.projecthero.mod.client.render.OverlayStrips.overlayIndex(u, v);
		return over >= 0 && !o[v * 64 + u] && !o[over];
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
		if (s.animId == CyborgState.ANIM_CONVERT && age < 0f) {
			return Anim.NONE; // v0.16.5: the 20 s of raw flesh before the plates (renderFlesh)
		}
		if (age < 0f && age > -40f) {
			age = 0f;
		}
		boolean running = s.formChangeAt > 0L && age >= 0f;
		if (s.combat && s.animId == CyborgState.ANIM_CONVERT && running && age < CyborgConfig.CONVERT_TICKS) {
			// the Mother Box fusing: the plates spread out from the chest core a pixel at a time (v0.16.5: 5 s, no glow)
			float t = Mth.clamp(age / CyborgConfig.CONVERT_TICKS, 0f, 1f);
			return new Anim(STEPS * t, false, 0f, false);
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
		return progress(player, 0f) > 0f || com.projecthero.mod.cyborg.Cyborg.fleshShows(player);
	}

	// ---------------------------------------------------------------- v0.16.5: the raw flesh (user)

	/** All but part of his face: raw muscle, between the Mother Box blast and the plates (and under them as they build). */
	public static final ResourceLocation FLESH = ProjectHeroMod.id("textures/entity/cyborg/cyborg_flesh.png");
	private static PlayerModel<LivingEntity> fleshModel;

	private static PlayerModel<LivingEntity> fleshModel() {
		if (fleshModel == null) {
			// a hair inside the plates (0.04) so they draw over it as they spread, a hair outside the skin
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.02f), false);
			fleshModel = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return fleshModel;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void renderFlesh(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent) {
		PlayerModel<LivingEntity> m = fleshModel();
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.setAllVisible(true);
		m.hat.visible = false;
		m.jacket.visible = false;
		m.leftSleeve.visible = false;
		m.rightSleeve.visible = false;
		m.leftPants.visible = false;
		m.rightPants.visible = false;
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(FLESH)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}

	/** First person: the raw-flesh arm. */
	public static void renderFleshArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right) {
		PlayerModel<LivingEntity> m = fleshModel();
		ModelPart a = right ? m.rightArm : m.leftArm;
		a.copyFrom(arm);
		a.visible = true;
		a.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(FLESH)), light, OverlayTexture.NO_OVERLAY);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static PlayerModel<LivingEntity> posed(HumanoidModel<?> parent) {
		return posed(parent, false);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static PlayerModel<LivingEntity> posed(HumanoidModel<?> parent, boolean slim) {
		PlayerModel<LivingEntity> m = model(slim);
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

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<?> parent, Anim a, float ageInTicks, boolean slim) {
		if (a.spread() <= 0f) {
			return;
		}
		if (a.hologramShowing()) {
			renderUnderHologram(pose, buffers, light, parent, a, ageInTicks, slim);
			return;
		}
		PlayerModel<LivingEntity> m = posed(parent, slim);
		boolean full = a.spread() >= STEPS - 0.01f;
		ResourceLocation tex = full ? skin(slim) : frame(a.spread(), slim);
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
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(frame(a.spread(), slim))), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		}
	}

	/**
	 * v0.17.7 (user): while the hologram sweeps over him, BOTH layers of the plates (and the head cover and the eye's glow) go
	 * texel by texel at the hologram's own line ({@link #holoTint}), not just the base layer -- so once it has passed nothing
	 * of Cyborg is left, and as it drains away both layers come back behind it. Drawn texel by texel on the parent model's
	 * parts at the plates' own sizes.
	 */
	private static void renderUnderHologram(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<?> parent, Anim a, float ageInTicks,
			boolean slim) {
		float line = a.holo();
		boolean building = a.building();
		com.projecthero.mod.client.render.OverlayStrips.Tint plates = (u, v) -> holoTint(u, v, line, building) == 0 ? 0xFFFFFFFF : 0;
		com.projecthero.mod.client.render.OverlayStrips.renderTexture(pose, buffers.getBuffer(RenderType.entityTranslucent(skin(slim))), light,
				parent, slim, plates, com.projecthero.mod.client.render.OverlayStrips.ALL, true, true, PLATES);
		com.projecthero.mod.client.render.OverlayStrips.renderTexture(pose, buffers.getBuffer(RenderType.entityTranslucent(HEAD_COVER)), light,
				parent, slim, plates, com.projecthero.mod.client.render.OverlayStrips.HEAD, true, false, COVER);
		int g = Math.round(255 * (0.8f + 0.2f * Mth.sin(ageInTicks * 0.25f)));
		int glow = (g << 24) | 0xFFFFFF;
		com.projecthero.mod.client.render.OverlayStrips.renderTexture(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)),
				LightTexture.FULL_BRIGHT, parent, slim, (u, v) -> plates.argb(u, v) == 0 ? 0 : glow,
				com.projecthero.mod.client.render.OverlayStrips.HEAD, true, false, PLATES);
	}

	/**
	 * First person under the hologram (v0.17.7): the plated arm, both layers, only where the hologram hasn't reached; over it
	 * the hologram of the wearer's own arm and sleeve where it has (vanilla's bare arm is already drawn under both).
	 */
	public static void renderArmUnderHologram(PoseStack pose, MultiBufferSource buffers, int light, net.minecraft.client.player.AbstractClientPlayer player,
			PlayerModel<?> model, boolean right, Anim a) {
		boolean slim = slim(player);
		float line = a.holo();
		boolean building = a.building();
		int part = right ? com.projecthero.mod.client.render.OverlayStrips.RIGHT_ARM : com.projecthero.mod.client.render.OverlayStrips.LEFT_ARM;
		com.projecthero.mod.client.render.OverlayStrips.renderTexture(pose, buffers.getBuffer(RenderType.entityTranslucent(skin(slim))), light,
				model, slim, (u, v) -> holoTint(u, v, line, building) == 0 ? 0xFFFFFFFF : 0, part, true, true, PLATES);
		com.projecthero.mod.client.render.OverlayStrips.renderTexture(pose,
				buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture())), light, model, slim, (u, v) -> {
					int t = holoTint(u, v, line, building);
					// the hologram where it has reached; ahead of it, the wearer's own sleeve where the plates have a gap
					return t != 0 ? t : gap(slim, u, v) ? 0xFFFFFFFF : 0;
				}, part, false, true, 0.07f);
		com.projecthero.mod.client.render.OverlayStrips.renderTexture(pose,
				buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture())), light, model, slim,
				(u, v) -> holoTint(u, v, line, building), part, true, false, 0.07f);
	}

	/** First person (v0.17.7): the wearer's own sleeve where both of the plates' layers have a gap (vanilla hides the sleeve). */
	public static void renderArmGaps(PoseStack pose, MultiBufferSource buffers, int light, net.minecraft.client.player.AbstractClientPlayer player,
			PlayerModel<?> model, boolean right) {
		boolean slim = slim(player);
		com.projecthero.mod.client.render.OverlayStrips.renderTexture(pose,
				buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture())), light, model, slim,
				(u, v) -> gap(slim, u, v) ? 0xFFFFFFFF : 0,
				right ? com.projecthero.mod.client.render.OverlayStrips.RIGHT_ARM : com.projecthero.mod.client.render.OverlayStrips.LEFT_ARM, false,
				true, 0f);
	}

	/** First person: the plated arm over the arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, float progress, boolean slim) {
		PlayerModel<LivingEntity> m = model(slim);
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		float spread = progress * STEPS;
		boolean full = spread >= STEPS - 0.01f;
		ResourceLocation tex = full ? skin(slim) : frame(spread, slim);
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

	/** Frames of the conversion. */
	private static final int CONVERT_FRAMES = 60;

	private static ResourceLocation frame(float spread, boolean slim) {
		if (frames == null) {
			build();
		}
		ResourceLocation[] f = frames[slim ? 1 : 0];
		if (f.length == 0) {
			return skin(slim);
		}
		int k = Math.max(0, Math.min(CONVERT_FRAMES, Math.round(spread / STEPS * CONVERT_FRAMES)));
		return f[k];
	}

	/**
	 * When skin texel ({@code u}, {@code v}) (64x64 coordinates) is plated in the conversion, 0..1: outward from the chest
	 * core, row by row, and within a row one texel after another in a scattered order -- a pixel at a time.
	 */
	public static float convertOrder(int u, int v) {
		float h = com.projecthero.mod.client.render.OverlayStrips.height(u, v);
		if (h < 0f) {
			return 2f;
		}
		int k = u * 668265263 ^ v * 374761393;
		k = (k ^ (k >>> 13)) * 1274126177;
		float jitter = ((k >>> 8) & 0xFFFF) / 65536f;
		return Mth.clamp((Math.abs(h - CORE_HEIGHT) + jitter * 0.98f) / (STEPS + 1f), 0f, 1f);
	}

	/** Frame {@code k} shows every texel whose {@link #convertOrder} is at most {@code k / CONVERT_FRAMES} -- no glowing edge. */
	private static void build() {
		frames = new ResourceLocation[2][];
		opaque = new boolean[2][];
		for (int s = 0; s < 2; s++) {
			build(s == 1);
		}
	}

	/** The spread frames and the opaque-texel map of one skin (v0.17.7: wide and slim). */
	private static void build(boolean slim) {
		int s = slim ? 1 : 0;
		frames[s] = new ResourceLocation[0];
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> res = mc.getResourceManager().getResource(skin(slim));
		if (res.isEmpty()) {
			return;
		}
		NativeImage src;
		try (InputStream in = res.get().open()) {
			src = NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[cyborg] could not read the skin for the plates", e);
			return;
		}
		int size = src.getWidth();
		int scale = Math.max(1, size / 64);
		boolean[] o = new boolean[64 * 64];
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				// any opaque pixel in the texel's block (an HD skin has scale x scale pixels per texel)
				for (int py = 0; py < scale && !o[y * 64 + x]; py++) {
					for (int px = 0; px < scale; px++) {
						if (((src.getPixelRGBA(x * scale + px, y * scale + py) >>> 24) & 0xFF) > 25) {
							o[y * 64 + x] = true;
							break;
						}
					}
				}
			}
		}
		opaque[s] = o;
		ResourceLocation[] f = new ResourceLocation[CONVERT_FRAMES + 1];
		for (int k = 0; k <= CONVERT_FRAMES; k++) {
			float line = k / (float) CONVERT_FRAMES;
			NativeImage img = new NativeImage(size, size, true);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					boolean on = k > 0 && convertOrder(x / scale, y / scale) <= line;
					img.setPixelRGBA(x, y, on ? src.getPixelRGBA(x, y) : 0);
				}
			}
			f[k] = mc.getTextureManager().register("projecthero_cyborg_spread_" + (slim ? "slim_" : "") + k, new DynamicTexture(img));
		}
		frames[s] = f;
		src.close();
	}
}
