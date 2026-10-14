package com.projecthero.mod.client.hulk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.hulk.entity.HulkBoulderEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.4 (user): the chunk of ground the Hulk rips up, drawn from the real blocks he tore it out of -- a jagged cluster
 * of block pieces, the surface block (grass, sand, stone...) across the top, what lay under it in the middle and the
 * deeper block at the bottom, each piece a little rotated and sized differently so it reads as a torn lump of earth, not a
 * cube. Tumbles once thrown. (Replaces the v0.13.14 GeckoLib boulder.)
 */
public class HulkBoulderRenderer extends EntityRenderer<HulkBoulderEntity> {
	/** One piece: offset (blocks, from the chunk's centre), size, a fixed tilt, and its layer (0 top, 1 middle, 2 deep). */
	private record Piece(float x, float y, float z, float size, float yaw, float pitch, float roll, int layer) {
	}

	private static final Piece[] PIECES = {
			// the top: the surface layer, a broad, uneven slab
			new Piece(0.0f, 0.55f, 0.0f, 0.95f, 8, 4, -3, 0),
			new Piece(0.62f, 0.45f, 0.2f, 0.75f, -14, 10, 6, 0),
			new Piece(-0.6f, 0.48f, -0.15f, 0.8f, 22, -6, 9, 0),
			new Piece(0.1f, 0.42f, 0.66f, 0.7f, -30, -12, 4, 0),
			new Piece(-0.15f, 0.5f, -0.65f, 0.72f, 12, 8, -10, 0),
			// the middle: what lay under the surface
			new Piece(0.0f, -0.05f, 0.0f, 1.05f, -5, 0, 5, 1),
			new Piece(0.7f, -0.1f, -0.35f, 0.7f, 25, -15, 12, 1),
			new Piece(-0.72f, -0.05f, 0.3f, 0.75f, -18, 12, -8, 1),
			new Piece(0.3f, -0.15f, 0.72f, 0.65f, 40, 6, 14, 1),
			new Piece(-0.35f, -0.1f, -0.7f, 0.68f, -35, -10, 6, 1),
			// the bottom: deeper rock, tapering to a ragged point
			new Piece(0.0f, -0.62f, 0.05f, 0.8f, 15, 20, -12, 2),
			new Piece(0.42f, -0.55f, -0.3f, 0.55f, -25, -8, 18, 2),
			new Piece(-0.4f, -0.58f, 0.32f, 0.58f, 33, 14, -6, 2),
			new Piece(0.05f, -0.95f, -0.05f, 0.45f, 5, 30, 25, 2),
	};

	/** Sized for a big Hulk (the pieces above are a ~2-block lump before this). */
	private static final float SCALE = 1.15f;

	private final BlockRenderDispatcher blocks;

	public HulkBoulderRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.blocks = context.getBlockRenderDispatcher();
		this.shadowRadius = 0.9f;
	}

	@Override
	public void render(HulkBoulderEntity boulder, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		pose.translate(0.0, 0.9, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, boulder.yRotO, boulder.getYRot())));
		if (boulder.flying()) {
			float t = boulder.tickCount + partialTick;
			pose.mulPose(Axis.XP.rotationDegrees(t * 17.0f));
			pose.mulPose(Axis.ZP.rotationDegrees(t * 7.0f));
		} else {
			// held overhead: a slow, heavy wobble
			float t = boulder.tickCount + partialTick;
			pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.15f) * 3.0f));
		}
		pose.scale(SCALE, SCALE, SCALE);
		BlockState[] layers = { boulder.block(0), boulder.block(1), boulder.block(2) };
		for (Piece p : PIECES) {
			BlockState st = layers[p.layer()];
			if (st.isAir()) {
				continue;
			}
			pose.pushPose();
			pose.translate(p.x(), p.y(), p.z());
			pose.mulPose(Axis.YP.rotationDegrees(p.yaw()));
			pose.mulPose(Axis.XP.rotationDegrees(p.pitch()));
			pose.mulPose(Axis.ZP.rotationDegrees(p.roll()));
			pose.scale(p.size(), p.size(), p.size());
			pose.translate(-0.5, -0.5, -0.5);
			this.blocks.renderSingleBlock(st, pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
		pose.popPose();
		super.render(boulder, yaw, partialTick, pose, buffers, light);
	}

	@Override
	@SuppressWarnings("deprecation")
	public ResourceLocation getTextureLocation(HulkBoulderEntity boulder) {
		return TextureAtlas.LOCATION_BLOCKS;
	}

}
