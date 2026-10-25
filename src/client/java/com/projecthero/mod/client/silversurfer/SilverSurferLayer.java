package com.projecthero.mod.client.silversurfer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.galactus.GalactusRenderers;
import com.projecthero.mod.silversurfer.SilverSurfer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * v0.17.1: the Silver Surfer on every player with the power (every viewer: the state is synced) -- the wide arms in his
 * skin ({@link SilverSurferRender}), the glowmask full-bright, the chrome glint, and the board under his feet while he
 * surfs (banking with him: {@code SilverSurferSkinMixin} rolls the whole body into turns).
 */
public class SilverSurferLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	/** He stands this high on the board (blocks), as the Galactus event's Herald does. */
	private static final float BOARD_TOP = GalactusRenderers.BOARD_TOP;

	public SilverSurferLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !SilverSurferRender.shows(player)) {
			return;
		}
		PlayerModel<AbstractClientPlayer> parent = getParentModel();
		boolean phase = SilverSurfer.phasing(player);
		int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
		int colour = phase ? 0x99FFFFFF : 0xFFFFFFFF;
		SilverSurferRender.poseArms(player, parent);
		// the arms in his skin (the player model's own are hidden: they may be slim)
		SilverSurferRender.renderArms(pose, buffers.getBuffer(phase ? RenderType.entityTranslucent(SilverSurferRender.SKIN)
				: RenderType.entityCutoutNoCull(SilverSurferRender.SKIN)), light, overlay, colour);
		// the glow (his eyes, the cosmic lines) and the chrome glint sliding over the silver
		parent.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(SilverSurferRender.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		SilverSurferRender.renderArms(pose, buffers.getBuffer(RenderType.eyes(SilverSurferRender.GLOW)), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		float t = SilverSurferRender.sheenTime();
		int sheen = phase ? 0xFF1A1C20 : 0xFF3A3E46;
		parent.renderToBuffer(pose, buffers.getBuffer(RenderType.energySwirl(SilverSurferRender.SHEEN, t, t * 0.5f)), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, sheen);
		SilverSurferRender.renderArms(pose, buffers.getBuffer(RenderType.energySwirl(SilverSurferRender.SHEEN, t, t * 0.5f)),
				LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, sheen);
		if (SilverSurfer.surfing(player)) {
			renderBoard(pose, buffers, light, partialTick);
		}
	}

	/** The board under his feet (model space: +y is down, the feet at y = 1.501). */
	private static void renderBoard(PoseStack pose, MultiBufferSource buffers, int light, float partialTick) {
		pose.pushPose();
		pose.translate(0.0, 1.501, 0.0);
		pose.scale(-1.0f, -1.0f, 1.0f);
		float undo = 1.0f / 0.9375f; // the player renderer's own scale
		pose.scale(undo, undo, undo);
		pose.translate(0.0, -BOARD_TOP, 0.0);
		pose.translate(-0.5, -0.51, -0.5); // GeoObjectRenderer centres on a block
		SilverSurferRender.board().render(pose, GalactusRenderers.BoardAnimatable.INSTANCE, buffers, null, null,
				Math.max(light, LightTexture.pack(10, 10)), partialTick);
		pose.popPose();
	}
}
