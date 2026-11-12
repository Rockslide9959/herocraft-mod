package com.projecthero.mod.client.vision;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.cyborg.CyborgRender;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.vision.Vision;
import com.projecthero.mod.vision.VisionConfig;
import com.projecthero.mod.vision.data.VisionState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Vision's look on every player with the power (the state is synced, so every viewer sees it): the user's chosen skin
 * ("Vision" by Doctor_Aurus, PlanetMinecraft -- see docs/reference/vision/SOURCE.txt; licence to check before a public release) drawn
 * as a second skin a hair over the wearer's, wide or slim to match their model, head and all (Vision's face is the synthezoid's).
 * {@code scratchpad/vision/make_vision_suit.js} took the painted cape off it; the cloth cape is {@link VisionCapeLayer}. The wearer's
 * outer layer is hidden while it shows ({@link #hidesSkinOverlay}). The Mind Stone and the eyes glow ({@code suit_glow.png}).
 *
 * <ul>
 *   <li><b>Human Hologram</b> (V): the wearer's own skin, base and overlay, built over the suit a texel at a time from the top of the
 *   head down with a gold scan line (Cyborg's hologram: {@link CyborgRender#holoOrder}); once it's whole nothing of Vision is drawn
 *   and the player simply looks like themselves. Dropping it drains it away feet-first.</li>
 *   <li><b>Intangible</b> (Shift+V, a Phase Dash): a flickering gold-white ghost shell just outside the suit.</li>
 *   <li><b>Diamond Density</b> (Shift+C): the suit drawn darker and heavier.</li>
 * </ul>
 */
public class VisionSuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ResourceLocation SUIT = tex("suit");
	public static final ResourceLocation SUIT_SLIM = tex("suit_slim");
	public static final ResourceLocation GLOW = tex("suit_glow");
	public static final ResourceLocation GLOW_SLIM = tex("suit_glow_slim");

	/** The hologram's building edge: Mind Stone gold. */
	private static final int HOLO_EDGE = 0xFFFFD84A;
	private static final float HOLO_RIM = 0.035f;

	private static PlayerModel<AbstractClientPlayer> wide;
	private static PlayerModel<AbstractClientPlayer> slim;
	private static PlayerModel<AbstractClientPlayer> ghostWide;
	private static PlayerModel<AbstractClientPlayer> ghostSlim;

	public VisionSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	private static ResourceLocation tex(String name) {
		return ProjectHeroMod.id("textures/entity/vision/" + name + ".png");
	}

	static PlayerModel<AbstractClientPlayer> suitModel(boolean slimArms) {
		if (wide == null) {
			wide = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.06f), false), 64, 64).bakeRoot(), false);
			slim = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.06f), true), 64, 64).bakeRoot(), true);
			ghostWide = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.32f), false), 64, 64).bakeRoot(), false);
			ghostSlim = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.32f), true), 64, 64).bakeRoot(), true);
		}
		return slimArms ? slim : wide;
	}

	private static PlayerModel<AbstractClientPlayer> ghostModel(boolean slimArms) {
		suitModel(slimArms);
		return slimArms ? ghostSlim : ghostWide;
	}

	/** The hologram right now: {line 0..1 (how far down him it reaches), building 1 / draining 0}; null = no hologram at all. */
	public static float[] hologramLine(Player player, float partial) {
		VisionState s = Vision.peek(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.holoChangeAt + partial;
		float t = Mth.clamp(age / VisionConfig.HOLOGRAM_SHIMMER_TICKS, 0f, 1f);
		boolean running = s.holoChangeAt > 0L && age >= 0f && age < VisionConfig.HOLOGRAM_SHIMMER_TICKS;
		if (s.hologram) {
			return new float[] { running ? t : 1f, 1f };
		}
		return running ? new float[] { 1f - t, 0f } : null;
	}

	/** The whole hologram is up: nothing of Vision is drawn (no suit, no cape, the wearer's own outer layer back). Client-safe. */
	public static boolean fullHologram(Player player, float partial) {
		float[] h = hologramLine(player, partial);
		return h != null && h[1] > 0.5f && h[0] >= 1f;
	}

	/** The suit is being drawn on {@code player}. */
	public static boolean suitShows(Player player) {
		return Vision.hasPower(player) && !player.isInvisible() && !fullHologram(player, 0f);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !Vision.hasPower(player)) {
			return;
		}
		float[] holo = hologramLine(player, partialTick);
		if (holo != null && holo[1] > 0.5f && holo[0] >= 1f) {
			return; // the whole hologram: he looks like himself
		}
		boolean slimArms = player.getSkin().model() == PlayerSkin.Model.SLIM;
		renderOn(pose, buffers, light, getParentModel(), slimArms, Vision.dense(player), Vision.phased(player), ageInTicks + partialTick);
		if (holo != null) {
			float line = holo[0];
			boolean building = holo[1] > 0.5f;
			OverlayStrips.renderSkin(pose, buffers, light, player, getParentModel(), (u, v) -> holoTint(u, v, line, building), true, true, 0.07f);
		}
	}

	/** One texel of the hologram at {@code line}: 0 = not there (yet / any more), gold on the building edge, else white. */
	static int holoTint(int u, int v, float line, boolean building) {
		float o = CyborgRender.holoOrder(u, v);
		if (building ? o > line : o > line + 1e-4f || line <= 0f) {
			return 0;
		}
		return building && line - o < HOLO_RIM ? HOLO_EDGE : 0xFFFFFFFF;
	}

	/** Draws the suit posed like {@code parent}: the skin, the glowing stone and eyes, the density tint and the phase shell. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void renderOn(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slimArms, boolean dense,
			boolean phased, float time) {
		PlayerModel<AbstractClientPlayer> m = suitModel(slimArms);
		((HumanoidModel) parent).copyPropertiesTo(m);
		copyLayers(m);
		m.setAllVisible(true);
		int colour = dense ? 0xFF9A9AA6 : 0xFFFFFFFF;
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(slimArms ? SUIT_SLIM : SUIT)), light, OverlayTexture.NO_OVERLAY, colour);
		// the Mind Stone and the eyes: full bright, pulsing
		float pulse = 0.75f + 0.25f * Mth.sin(time * 0.15f);
		int glow = (Math.round(255 * pulse) << 24) | 0xFFFFFF;
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(slimArms ? GLOW_SLIM : GLOW)), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, glow);
		if (phased) {
			PlayerModel<AbstractClientPlayer> g = ghostModel(slimArms);
			((HumanoidModel) parent).copyPropertiesTo(g);
			copyLayers(g);
			g.setAllVisible(true);
			float flicker = 0.6f + 0.2f * Mth.sin(time * 0.9f) + 0.1f * Mth.sin(time * 2.3f + 1f);
			int a = Mth.clamp(Math.round(255 * flicker), 0, 255);
			// additive: a gold-white glow over the suit
			int shell = (a << 24) | 0xFFE6A0;
			g.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(slimArms ? SUIT_SLIM : SUIT)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					scale(shell, flicker));
		}
	}

	/** {@code argb}'s colour scaled by {@code k} (the additive types ignore alpha). */
	private static int scale(int argb, float k) {
		int r = Math.round(((argb >> 16) & 0xFF) * k), g = Math.round(((argb >> 8) & 0xFF) * k), b = Math.round((argb & 0xFF) * k);
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	private static void copyLayers(PlayerModel<AbstractClientPlayer> m) {
		m.hat.copyFrom(m.head);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
	}

	/** First person: the suited arm (base + sleeve + glow) over the vanilla one. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right) {
		boolean slimArms = player instanceof AbstractClientPlayer acp && acp.getSkin().model() == PlayerSkin.Model.SLIM;
		PlayerModel<AbstractClientPlayer> m = suitModel(slimArms);
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		int colour = Vision.dense(player) ? 0xFF9A9AA6 : 0xFFFFFFFF;
		ResourceLocation tex = slimArms ? SUIT_SLIM : SUIT;
		a.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY, colour);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY, colour);
	}

	/**
	 * From {@code PlayerModelMixin}: while the suit shows, the wearer's whole outer layer (hat / hair included: the suit's head is
	 * Vision's) is hidden, or it would poke through the suit drawn a hair over the base skin.
	 */
	public static boolean hidesSkinOverlay(Player player) {
		return suitShows(player);
	}
}
