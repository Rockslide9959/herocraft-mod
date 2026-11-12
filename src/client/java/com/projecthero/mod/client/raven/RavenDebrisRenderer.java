package com.projecthero.mod.client.raven;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.raven.entity.RavenDebrisEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven: a block in her telekinetic grip -- the block itself (tumbling slowly), wrapped in a see-through black shell with violet
 * seams. An energy copy (mob griefing off) is darker: more shadow than block.
 */
public class RavenDebrisRenderer extends EntityRenderer<RavenDebrisEntity> {
	private final BlockRenderDispatcher blocks;

	public RavenDebrisRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.blocks = ctx.getBlockRenderDispatcher();
		this.shadowRadius = 0.4f;
	}

	@Override
	public void render(RavenDebrisEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		BlockState state = e.blockState();
		float t = e.tickCount + partial;
		pose.pushPose();
		pose.translate(0f, 0.49f, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(t * 3.5f + e.getId() * 37f));
		pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.08f + e.getId()) * 14f));
		if (state.getRenderShape() == RenderShape.MODEL) {
			pose.pushPose();
			pose.scale(0.92f, 0.92f, 0.92f);
			pose.translate(-0.5f, -0.5f, -0.5f);
			blocks.renderSingleBlock(state, pose, buffers, e.energyCopy() ? Math.max(light, LightTexture.pack(4, 4)) : light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
		PoseStack.Pose last = pose.last();
		double h = 0.53;
		int shell = e.energyCopy() ? 0xA60A0412 : 0x58100618;
		cube(buffers, last, h, shell);
		float pulse = 0.6f + 0.4f * Mth.sin(t * 0.4f);
		edges(buffers, last, h + 0.01, RavenDraw.fade(0x906A2BD0, pulse));
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	private static void cube(MultiBufferSource buffers, PoseStack.Pose pose, double h, int argb) {
		Vec3[] c = corners(h);
		int[][] faces = { { 0, 1, 2, 3 }, { 4, 5, 6, 7 }, { 0, 1, 5, 4 }, { 2, 3, 7, 6 }, { 1, 2, 6, 5 }, { 0, 3, 7, 4 } };
		for (int[] f : faces) {
			RavenDraw.quad(RavenDraw.shadow(buffers), pose, c[f[0]], c[f[1]], c[f[2]], c[f[3]], argb);
		}
	}

	/** Thin glowing strips along the twelve edges. */
	private static void edges(MultiBufferSource buffers, PoseStack.Pose pose, double h, int argb) {
		Vec3[] c = corners(h);
		int[][] e = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, { 4, 5 }, { 5, 6 }, { 6, 7 }, { 7, 4 }, { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } };
		for (int[] k : e) {
			Vec3 a = c[k[0]], b = c[k[1]];
			Vec3 mid = a.add(b).scale(0.5);
			Vec3 out = mid.normalize().scale(0.05);
			RavenDraw.glowQuad(RavenDraw.glow(buffers), pose, a, b, b.add(out), a.add(out), argb);
		}
	}

	private static Vec3[] corners(double h) {
		return new Vec3[] { new Vec3(-h, -h, -h), new Vec3(h, -h, -h), new Vec3(h, -h, h), new Vec3(-h, -h, h), new Vec3(-h, h, -h), new Vec3(h, h, -h),
				new Vec3(h, h, h), new Vec3(-h, h, h) };
	}

	@Override
	public ResourceLocation getTextureLocation(RavenDebrisEntity e) {
		return RavenDraw.WHITE;
	}
}
