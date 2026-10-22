package com.projecthero.mod.client.titanshifter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.projecthero.mod.titanshifter.entity.TitanCrystalWallEntity;

import net.minecraft.client.Minecraft;
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
 * v0.16.7 (user, Titan fixes): one column of the Crystal Wall -- four amethyst blocks capped with crystal buds. It
 * thrusts up out of the ground (the columns one after another, so the wall grows from one side), stands, and in its last
 * {@link TitanCrystalWallEntity#BREAK_TICKS} ticks cracks: the blocks shiver and shrink away from the top down.
 */
public class TitanCrystalWallRenderer extends EntityRenderer<TitanCrystalWallEntity> {
	private static final BlockState BLOCK = Blocks.AMETHYST_BLOCK.defaultBlockState();
	private static final BlockState BUD = Blocks.LARGE_AMETHYST_BUD.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
	private static final int RISE_TICKS = 6;

	public TitanCrystalWallRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(TitanCrystalWallEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = e.tickCount + partialTick - e.index() * 2.0f;
		if (age <= 0.0f) {
			return;
		}
		var blocks = Minecraft.getInstance().getBlockRenderer();
		float rise = Math.min(1.0f, age / RISE_TICKS);
		rise = 1.0f - (1.0f - rise) * (1.0f - rise) * (1.0f - rise);
		float left = e.life() - (e.tickCount + partialTick);
		float crack = left < TitanCrystalWallEntity.BREAK_TICKS ? 1.0f - Math.max(0.0f, left) / TitanCrystalWallEntity.BREAK_TICKS : 0.0f;
		pose.pushPose();
		// out of the ground: the column slides up from below (the ground hides the part still under it)
		pose.translate(-0.5f, -TitanCrystalWallEntity.HEIGHT * (1.0f - rise), -0.5f);
		long seed = e.getId() * 341873128712L;
		for (int h = 0; h <= TitanCrystalWallEntity.HEIGHT; h++) {
			boolean bud = h == TitanCrystalWallEntity.HEIGHT;
			// cracking runs from the top down
			float c = Math.min(1.0f, Math.max(0.0f, crack * 1.6f - (TitanCrystalWallEntity.HEIGHT - h) * 0.15f));
			float s = 1.0f - c;
			if (s <= 0.02f) {
				continue;
			}
			pose.pushPose();
			pose.translate(0.5f, h + 0.5f, 0.5f);
			if (c > 0.0f) {
				float j = (float) Math.sin((e.tickCount + partialTick) * 2.7f + h * 1.3f + seed % 7) * 6.0f * c;
				pose.mulPose(Axis.YP.rotationDegrees(j));
				pose.mulPose(Axis.ZP.rotationDegrees(j * 0.7f));
			}
			pose.scale(s, s, s);
			pose.translate(-0.5f, -0.5f, -0.5f);
			blocks.renderSingleBlock(bud ? BUD : BLOCK, pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
		pose.popPose();
		super.render(e, yaw, partialTick, pose, buffers, light);
	}

	@Override
	@SuppressWarnings("deprecation")
	public ResourceLocation getTextureLocation(TitanCrystalWallEntity e) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
