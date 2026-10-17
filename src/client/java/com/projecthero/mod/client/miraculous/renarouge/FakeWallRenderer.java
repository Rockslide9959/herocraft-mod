package com.projecthero.mod.client.miraculous.renarouge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.miraculous.renarouge.entity.FakeWallEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;


/**
 * v0.16.6: the illusion wall -- the ground's own block drawn in every free cell of the wall (it rises out of the ground
 * row by row as it appears, sinks back as it goes), with a faint orange shimmer playing over both faces.
 */
public class FakeWallRenderer extends EntityRenderer<FakeWallEntity> {
	public FakeWallRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0f;
	}

	@Override
	public void render(FakeWallEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		BlockState look = e.look();
		int h = e.height();
		AABB box = e.wallBox();
		float age = e.tickCount + partialTick;
		float life = e.lifeTicks();
		// rows rise in over 8 ticks and sink away over the last 8
		float shown = Math.min(Mth.clamp(age / 8f, 0f, 1f), Mth.clamp((life - age) / 8f, 0f, 1f)) * h;
		var dispatcher = Minecraft.getInstance().getBlockRenderer();
		var model = dispatcher.getBlockModel(look);
		int bx0 = Mth.floor(box.minX), by0 = Mth.floor(box.minY), bz0 = Mth.floor(box.minZ);
		int bx1 = Mth.floor(box.maxX), bz1 = Mth.floor(box.maxZ);
		for (int x = bx0; x < bx1; x++) {
			for (int z = bz0; z < bz1; z++) {
				for (int j = 0; j < h; j++) {
					float row = Mth.clamp(shown - j, 0f, 1f);
					if (row <= 0f) {
						continue;
					}
					BlockPos bp = new BlockPos(x, by0 + j, z);
					if (!level.getBlockState(bp).canBeReplaced()) {
						continue;
					}
					pose.pushPose();
					pose.translate(bp.getX() - e.getX(), bp.getY() - e.getY(), bp.getZ() - e.getZ());
					if (row < 1f) {
						pose.translate(0.5f, 0f, 0.5f);
						pose.scale(1f, row, 1f);
						pose.translate(-0.5f, 0f, -0.5f);
					}
					// as FallingBlockRenderer does: the world-lit tesselation into the moving-block layer
					dispatcher.getModelRenderer().tesselateBlock(level, model, look, bp, pose,
							buffers.getBuffer(net.minecraft.client.renderer.ItemBlockRenderTypes.getMovingBlockRenderType(look)), false,
							net.minecraft.util.RandomSource.create(), look.getSeed(bp), OverlayTexture.NO_OVERLAY);
					pose.popPose();
				}
			}
		}
		// the shimmer: two sheets of faint orange glass a hair outside the faces, pulsing (the chunk translucent pass draws
		// after the blocks above, so they never hide them)
		AABB b = box;
		float a = 0.16f + 0.10f * Mth.sin(age * 0.25f);
		TextureAtlasSprite glass = Minecraft.getInstance().getBlockRenderer().getBlockModel(Blocks.ORANGE_STAINED_GLASS.defaultBlockState())
				.getParticleIcon();
		VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
		PoseStack.Pose last = pose.last();
		float x0 = (float) (b.minX - e.getX()), x1 = (float) (b.maxX - e.getX());
		float y0 = (float) (b.minY - e.getY()), y1 = (float) (b.minY - e.getY() + Math.min(shown, h));
		float z0 = (float) (b.minZ - e.getZ()), z1 = (float) (b.maxZ - e.getZ());
		float eps = 0.012f;
		int lt = LevelRenderer.getLightColor(level, BlockPos.containing(b.getCenter()));
		if (e.alongX()) {
			for (float z : new float[] { z0 - eps, z1 + eps }) {
				quad(vc, last, glass, a, lt, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z);
			}
		} else {
			for (float x : new float[] { x0 - eps, x1 + eps }) {
				quad(vc, last, glass, a, lt, x, y0, z0, x, y0, z1, x, y1, z1, x, y1, z0);
			}
		}
	}

	/** One double-sided glass quad. */
	private static void quad(VertexConsumer vc, PoseStack.Pose last, TextureAtlasSprite sp, float alpha, int light, float... p) {
		int al = (int) (255 * Mth.clamp(alpha, 0f, 1f));
		float[][] uv = { { sp.getU0(), sp.getV1() }, { sp.getU1(), sp.getV1() }, { sp.getU1(), sp.getV0() }, { sp.getU0(), sp.getV0() } };
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 0; k < 4; k++) {
				int i = pass == 0 ? k : 3 - k;
				vc.addVertex(last, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(255, 255, 255, al).setUv(uv[i][0], uv[i][1]).setLight(light)
						.setNormal(last, 0f, 1f, 0f);
			}
		}
	}

	@Override
	public boolean shouldRender(FakeWallEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		return frustum.isVisible(e.wallBox().inflate(0.5));
	}

	@Override
	public ResourceLocation getTextureLocation(FakeWallEntity e) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
