package com.projecthero.mod.client.revamp.batcha;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.revamp.batcha.ThrownChunkEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** v0.13.22: draws a {@link ThrownChunkEntity} as the real block it was torn from, scaled up and tumbling in flight. */
public class ThrownChunkRenderer extends EntityRenderer<ThrownChunkEntity> {
	private final BlockRenderDispatcher blocks;

	public ThrownChunkRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.blocks = context.getBlockRenderDispatcher();
		this.shadowRadius = 0.5f;
	}

	@Override
	public void render(ThrownChunkEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int light) {
		BlockState state = entity.blockState();
		if (state.getRenderShape() != RenderShape.MODEL) {
			return;
		}
		float s = entity.scale();
		pose.pushPose();
		pose.translate(0.0, 0.5 * s, 0.0);
		float t = entity.tickCount + partialTick;
		if (entity.flying()) {
			pose.mulPose(Axis.XP.rotationDegrees(t * 21f));
			pose.mulPose(Axis.ZP.rotationDegrees(t * 13f));
		} else {
			// held: a slow wobble so it reads as heavy and alive in the hands
			pose.mulPose(Axis.YP.rotationDegrees((float) Math.sin(t * 0.15) * 6f));
			pose.mulPose(Axis.XP.rotationDegrees((float) Math.sin(t * 0.21) * 4f));
		}
		pose.scale(s, s, s);
		pose.translate(-0.5, -0.5, -0.5);
		blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		super.render(entity, yaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(ThrownChunkEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
