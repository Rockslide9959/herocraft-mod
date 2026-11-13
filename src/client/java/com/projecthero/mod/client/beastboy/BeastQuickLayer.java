package com.projecthero.mod.client.beastboy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.beastboy.morph.BeastMorph;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * v0.17.3 Beast Boy morph round: a human-form quick morph's animal part (gorilla arms, rhino / T-Rex / elephant head, elephant legs,
 * octopus arms -- the new blocky models' parts) drawn on his posed player model, so it stays on the joint and follows his pose
 * ({@code QuickMorphs}). His own part is swollen or hidden by {@code BeastMorph.adjustModel}.
 */
public final class BeastQuickLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public BeastQuickLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		BeastMorph.renderQuickLayer(getParentModel(), player, pose, buffers, light, LivingEntityRenderer.getOverlayCoords(player, 0f), partialTick);
	}
}
