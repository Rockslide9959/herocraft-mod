package com.projecthero.mod.client.flash;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.flash.FlashRing;
import com.projecthero.mod.flash.FlashSuit;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.14.11: the gold Flash Ring on the right fist -- a small band round the front of the hand with the red lightning
 * emblem facing out, drawn for anyone with a ring on (every viewer: the attachment is synced). Sits proud of the Flash
 * glove when the suit is on, on the skin otherwise.
 */
public class FlashRingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/flash_ring.png");
	private static final ModelPart BARE = ring(0.0f);
	private static final ModelPart GLOVED = ring(0.6f);

	public FlashRingLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	/** The band: 1.6 x 1.4 px across the front knuckles, 0.6 px proud of the hand, pushed {@code out} further forward. */
	private static ModelPart ring(float out) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("ring", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-2.3f, 8.4f, -2.6f - out, 1.6f, 1.4f, 0.8f), PartPose.ZERO);
		return LayerDefinition.create(mesh, 16, 16).bakeRoot();
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || FlashRing.worn(player).isEmpty()) {
			return;
		}
		boolean gloved = FlashSuit.isSuit(player.getItemBySlot(EquipmentSlot.CHEST)) && FlashSuitReveal.progress(player, partialTick) > 0.2f;
		poseStack.pushPose();
		getParentModel().rightArm.translateAndRotate(poseStack);
		(gloved ? GLOVED : BARE).render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light,
				OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
	}
}
