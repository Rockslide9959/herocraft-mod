package com.projecthero.mod.client.spider;

import com.projecthero.mod.spider.entity.ThrownBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** v0.16.1: a block thrown by Grab &amp; Hurl -- drawn as itself, tumbling about its centre. */
public class ThrownBlockRenderer extends EntityRenderer<ThrownBlockEntity> {
	private final BlockRenderDispatcher blocks;

	public ThrownBlockRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.blocks = context.getBlockRenderDispatcher();
		this.shadowRadius = 0.4f;
	}

	@Override
	public void render(ThrownBlockEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers,
			int light) {
		BlockState state = entity.blockState();
		if (state.getRenderShape() != RenderShape.MODEL) {
			return;
		}
		float age = entity.tickCount + partialTicks;
		pose.pushPose();
		pose.translate(0.0, 0.5, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(age * 9.0f));
		pose.mulPose(Axis.XP.rotationDegrees(age * 13.0f));
		pose.translate(-0.5, -0.5, -0.5);
		blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		super.render(entity, yaw, partialTicks, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(ThrownBlockEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
