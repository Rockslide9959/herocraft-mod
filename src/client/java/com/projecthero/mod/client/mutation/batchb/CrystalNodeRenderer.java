package com.projecthero.mod.client.mutation.batchb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p06.CrystalNodeEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.13.22 Crystalkinesis: a crystal node is drawn as the real amethyst-cluster block model, turned to grow out of
 * the surface it was planted on, full-bright so it reads as a glowing crystal at night. It grows in over its first
 * few ticks. A Resonance Spire is the same cluster at 2.2x on an amethyst base, slowly turning.
 */
public class CrystalNodeRenderer extends EntityRenderer<CrystalNodeEntity> {
	private static final BlockState BASE = Blocks.AMETHYST_BLOCK.defaultBlockState();

	public CrystalNodeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.2f;
	}

	@Override
	public void render(CrystalNodeEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int light) {
		var blocks = Minecraft.getInstance().getBlockRenderer();
		Direction facing = e.facing();
		BlockState cluster = Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, facing);
		float grow = Math.min(1.0f, (e.tickCount + partialTick) / 6.0f);
		int glow = LightTexture.FULL_BRIGHT;
		pose.pushPose();
		if (e.isSpire()) {
			float spin = (e.tickCount + partialTick) * 1.5f;
			pose.mulPose(Axis.YP.rotationDegrees(spin));
			// a squat amethyst plinth
			pose.pushPose();
			pose.scale(0.8f, 0.35f * grow, 0.8f);
			pose.translate(-0.5, 0.0, -0.5);
			blocks.renderSingleBlock(BASE, pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			pose.translate(0.0, 0.3 * grow, 0.0);
			float s = 2.2f * grow;
			pose.scale(s, s, s);
			pose.translate(-0.5, 0.0, -0.5);
			blocks.renderSingleBlock(cluster.setValue(AmethystClusterBlock.FACING, Direction.UP), pose, buffers, glow,
					OverlayTexture.NO_OVERLAY);
		} else {
			// grow out of the planted face: scale about the cell's attachment point
			double ax = -facing.getStepX() * 0.5;
			double ay = facing == Direction.UP ? 0.0 : facing == Direction.DOWN ? 1.0 : 0.5;
			double az = -facing.getStepZ() * 0.5;
			pose.translate(ax, ay, az);
			pose.scale(grow, grow, grow);
			pose.translate(-ax, -ay, -az);
			pose.translate(-0.5, 0.0, -0.5);
			blocks.renderSingleBlock(cluster, pose, buffers, glow, OverlayTexture.NO_OVERLAY);
		}
		pose.popPose();
		super.render(e, entityYaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(CrystalNodeEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
