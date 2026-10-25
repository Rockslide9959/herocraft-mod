package com.projecthero.mod.client.silversurfer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.galactus.GalactusRenderers;
import com.projecthero.mod.silversurfer.SilverSurfer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: how the Silver Surfer looks -- the Galactus event Herald's own skin (Planet Minecraft skin 6740557,
 * {@link GalactusRenderers#SURFER_SKIN}) on the player rig in place of the wearer's skin (the body through
 * {@code getTextureLocation}), its glowmask full-bright, a slow chrome glint sliding over him, and the board under his
 * feet while he surfs.
 *
 * <p>The skin is a wide-arm (Steve) skin, so his arms are always drawn from a wide-arm model ({@link #wide()}) posed like
 * the real ones -- a slim-armed wearer would otherwise show the skin's arms mis-sampled; the player model's own arms and
 * sleeves are hidden while he has the power ({@code SilverSurferPoseMixin}).
 */
public final class SilverSurferRender {
	public static final ResourceLocation SKIN = GalactusRenderers.SURFER_SKIN;
	public static final ResourceLocation GLOW = GalactusRenderers.SURFER_GLOW;
	public static final ResourceLocation SHEEN = ProjectHeroMod.id("textures/entity/silversurfer/chrome_sheen.png");
	public static final ResourceLocation STATUE = ProjectHeroMod.id("textures/entity/silversurfer/statue_silver.png");

	private static PlayerModel<AbstractClientPlayer> wide;
	private static GalactusRenderers.BoardObjectRenderer board;

	private SilverSurferRender() {
	}

	/** Has the look (client-safe: every viewer reads the synced state). */
	public static boolean shows(Player player) {
		return SilverSurfer.hasPower(player);
	}

	static PlayerModel<AbstractClientPlayer> wide() {
		if (wide == null) {
			wide = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
		}
		return wide;
	}

	static GalactusRenderers.BoardObjectRenderer board() {
		if (board == null) {
			board = new GalactusRenderers.BoardObjectRenderer();
		}
		return board;
	}

	/** Poses the wide arms like the wearer's (slim arms hang half a pixel lower). */
	static void poseArms(AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> parent) {
		PlayerModel<AbstractClientPlayer> w = wide();
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		copy(w.leftArm, parent.leftArm, slim);
		copy(w.rightArm, parent.rightArm, slim);
		w.leftSleeve.copyFrom(w.leftArm);
		w.rightSleeve.copyFrom(w.rightArm);
		w.leftArm.visible = w.rightArm.visible = true;
		w.leftSleeve.visible = w.rightSleeve.visible = true;
	}

	private static void copy(ModelPart to, ModelPart from, boolean slim) {
		to.copyFrom(from);
		if (slim) {
			to.y -= 0.5f;
		}
	}

	/** The arms (and their sleeves) in the Surfer's skin, in {@code type}. */
	static void renderArms(PoseStack pose, VertexConsumer vc, int light, int overlay, int colour) {
		PlayerModel<AbstractClientPlayer> w = wide();
		w.leftArm.render(pose, vc, light, overlay, colour);
		w.rightArm.render(pose, vc, light, overlay, colour);
		w.leftSleeve.render(pose, vc, light, overlay, colour);
		w.rightSleeve.render(pose, vc, light, overlay, colour);
	}

	/** First person: the posed arm (only {@code right} or left) in the Surfer's skin, glow and sheen. */
	public static void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right) {
		PlayerModel<AbstractClientPlayer> w = wide();
		ModelPart a = right ? w.rightArm : w.leftArm;
		ModelPart s = right ? w.rightSleeve : w.leftSleeve;
		a.copyFrom(arm);
		a.y = 2.0f;
		s.copyFrom(a);
		a.visible = s.visible = true;
		a.render(pose, buffers.getBuffer(RenderType.entitySolid(SKIN)), light, OverlayTexture.NO_OVERLAY);
		s.render(pose, buffers.getBuffer(RenderType.entityTranslucent(SKIN)), light, OverlayTexture.NO_OVERLAY);
		a.render(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		float t = sheenTime();
		a.render(pose, buffers.getBuffer(RenderType.energySwirl(SHEEN, t, t * 0.5f)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				0xFF3A3E46);
	}

	static float sheenTime() {
		Minecraft mc = Minecraft.getInstance();
		float time = mc.level == null ? 0f : (mc.level.getGameTime() % 4000L) + mc.getTimer().getGameTimeDeltaPartialTick(true);
		return time * 0.006f;
	}
}
