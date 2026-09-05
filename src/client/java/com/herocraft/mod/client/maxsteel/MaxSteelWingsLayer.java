package com.herocraft.mod.client.maxsteel;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.maxsteel.MaxSteel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws {@link MaxSteelWingsModel} on the back of any player who is transformed as Max Steel and in
 * Turbo Flight (v0.6.17). Reads the synced {@code MAX_STEEL_FLYING} flag, so it shows for other
 * players too.
 */
public class MaxSteelWingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");
	private static final int BLUE = 0xFF5AA8FF;

	private final MaxSteelWingsModel model;

	public MaxSteelWingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
			EntityModelSet models) {
		super(parent);
		this.model = new MaxSteelWingsModel(models.bakeLayer(MaxSteelWingsModel.LAYER));
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !MaxSteel.isTransformed(player)
				|| !player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)) {
			return;
		}
		// Same placement vanilla's ElytraLayer uses: render at the model root (already at neck height
		// and carrying the whole-body flight lean from PlayerRendererMixin), nudged back off the spine.
		pose.pushPose();
		pose.translate(0.0F, 0.0F, 0.125F);
		VertexConsumer buffer = buffers.getBuffer(RenderType.armorCutoutNoCull(TEXTURE));
		model.render(pose, buffer, packedLight, OverlayTexture.NO_OVERLAY, BLUE);
		pose.popPose();
	}
}
