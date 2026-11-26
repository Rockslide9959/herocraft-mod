package com.projecthero.mod.client.cyborg;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.cyborg.CyborgConfig;
import com.projecthero.mod.cyborg.data.CyborgState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.5: Cyborg's look. The red eye glows ({@code cyborg_glow.png}).
 *
 * <p>v0.17.7 (user): the skin is the Teen Titans Cyborg from PlanetMinecraft
 * (planetminecraft.com/skin/teen-titans-cyborg-4002965, file {@code cyborg-11202576.png}, a wide-arm 64x64 skin) with Victor
 * Stone the person cleared out on both layers ({@code scratchpad/v0177/make_cyborg_skin.js}): the wearer's own face, scalp,
 * neck and upper arms show between the parts. A slim copy ({@code cyborg_slim.png}) goes on slim players.
 *
 * <p>v0.17.7 round 2 (user: "the cyborg head looks too big ... make it normal sized"): Cyborg is no longer drawn on a larger
 * model (and head cover) over the player. The player is drawn by vanilla at his normal size with ONE skin made from his own
 * and Cyborg's ({@link CyborgSkins} / {@link CyborgSkinComposite}): Cyborg's texels wherever Cyborg has any, his own (both
 * layers) only where Cyborg is clear. Only the eye's glow is drawn here, on the player's own head and hat parts.
 *
 * <p><b>The forms</b> ({@link #anim}): the parts spread out from the chest core over the body (texels revealed in order of
 * their distance from the core, {@link #convertOrder}) when the Mother Box first fuses. Civilian form puts his old skin back
 * under a hologram that builds from the head down ({@link #holoTint}) and drains away from the feet up. Both are baked into
 * his skin texel by texel ({@link CyborgSkins}).
 */
public final class CyborgRender {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/cyborg/cyborg.png");
	/** v0.17.7: {@link #SKIN} remapped to the slim (3-pixel arm) layout. */
	public static final ResourceLocation SKIN_SLIM = ProjectHeroMod.id("textures/entity/cyborg/cyborg_slim.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/cyborg/cyborg_glow.png");
	/** v0.16.5 (user): the raw flesh between the Mother Box blast and the parts (v0.17.7: baked into his skin, {@link CyborgSkins}). */
	public static final ResourceLocation FLESH = ProjectHeroMod.id("textures/entity/cyborg/cyborg_flesh.png");
	/** The chest core sits this many model pixels down from the top of the head. */
	public static final float CORE_HEIGHT = 11f;
	/** The farthest texel from the core (the soles). */
	public static final int STEPS = 21;

	private CyborgRender() {
	}

	/**
	 * @param spread how far the parts reach from the core, model pixels (0 = none, {@link #STEPS} = all of him)
	 * @param fresh  the newest texels glow (unused since v0.16.5)
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

	public static boolean slim(Player player) {
		return player instanceof net.minecraft.client.player.AbstractClientPlayer p
				&& p.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
	}

	public static Anim anim(Player player, float partial) {
		CyborgState s = player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
		if (s == null || !s.hasPower || player.level() == null) {
			return Anim.NONE;
		}
		float age = player.level().getGameTime() - s.formChangeAt + partial;
		if (s.animId == CyborgState.ANIM_CONVERT && age < 0f) {
			return Anim.NONE; // v0.16.5: the 20 s of raw flesh before the parts
		}
		if (age < 0f && age > -40f) {
			age = 0f;
		}
		boolean running = s.formChangeAt > 0L && age >= 0f;
		if (s.combat && s.animId == CyborgState.ANIM_CONVERT && running && age < CyborgConfig.CONVERT_TICKS) {
			// the Mother Box fusing: the parts spread out from the chest core a pixel at a time (v0.16.5: 5 s, no glow)
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

	/** How much of him is Cyborg, 0..1 (0 with the whole hologram up). */
	public static float progress(Player player, float partial) {
		Anim a = anim(player, partial);
		return a.holo() >= 1f ? 0f : a.spread() / STEPS;
	}

	/** Worn armour is hidden while any of Cyborg (or the flesh) shows (HumanoidArmorLayerMixin). */
	public static boolean hidesSkinOverlay(Player player) {
		return progress(player, 0f) > 0f || com.projecthero.mod.cyborg.Cyborg.fleshShows(player);
	}

	/**
	 * v0.17.7 round 2: the red eye's glow on the player's own head and hat parts (vanilla's sizes: nothing bigger than his
	 * head); under the hologram only where it hasn't reached. Only while he is drawn with his Cyborg skin.
	 */
	public static void renderGlow(PoseStack pose, MultiBufferSource buffers, PlayerModel<?> parent, Anim a, float ageInTicks, boolean slim) {
		int g = Math.round(255 * (0.8f + 0.2f * Mth.sin(ageInTicks * 0.25f)));
		int glow = (g << 24) | 0xFFFFFF;
		if (a.hologramShowing()) {
			float line = a.holo();
			boolean building = a.building();
			com.projecthero.mod.client.render.OverlayStrips.renderTexture(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)),
					LightTexture.FULL_BRIGHT, parent, slim, (u, v) -> holoTint(u, v, line, building) == 0 ? glow : 0,
					com.projecthero.mod.client.render.OverlayStrips.HEAD, true, true, 0f);
			return;
		}
		if (a.spread() < STEPS - 0.01f) {
			return;
		}
		ModelPart[] parts = { parent.head, parent.hat };
		for (ModelPart p : parts) {
			if (p.visible) {
				p.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow);
			}
		}
	}

	// ---------------------------------------------------------------- the hologram

	/** The cyan of the projector light and of the hologram's building edge. */
	private static final int HOLO_EDGE = 0xFF7FE6FF;
	/** The newest texels of a building hologram are cyan for this much of the line. */
	private static final float HOLO_RIM = 0.035f;
	/** The blue lamp on his forehead (head-part space, model pixels), just outside his hat layer. */
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
	 * The building hologram's projector: a cone of cyan light from the lamp on his forehead to the line it is drawing. (v0.17.7
	 * round 2: his old skin under the line is baked into his texture by {@link CyborgSkins}, at his normal size.)
	 */
	public static void renderHologram(PoseStack pose, MultiBufferSource buffers, int light, net.minecraft.client.player.AbstractClientPlayer player,
			PlayerModel<?> parent, Anim a, float ageInTicks) {
		if (a.building()) {
			renderProjector(pose, buffers, parent, a.holo(), ageInTicks);
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

	// ---------------------------------------------------------------- the Mother Box spread

	/**
	 * When skin texel ({@code u}, {@code v}) (64x64 coordinates) turns Cyborg in the conversion, 0..1: outward from the chest
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
}
