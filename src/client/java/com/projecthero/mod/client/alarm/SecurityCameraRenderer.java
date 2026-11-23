package com.projecthero.mod.client.alarm;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.alarm.block.SecurityCameraBlock;
import com.projecthero.mod.alarm.block.SecurityCameraBlockEntity;
import com.projecthero.mod.alarm.block.SensorBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: a Security Camera's moving head -- a small grey box on the wall bracket, swept by the same formula the server
 * checks ({@link SecurityCameraBlockEntity#yaw}), with a red LED and a faint red view wedge while it is live.
 */
public class SecurityCameraRenderer implements BlockEntityRenderer<SecurityCameraBlockEntity> {
	private static final ResourceLocation TEX = ProjectHeroMod.id("textures/block/security_camera_head.png");

	public SecurityCameraRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public void render(SecurityCameraBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		BlockState s = be.getBlockState();
		if (be.getLevel() == null || !s.hasProperty(SecurityCameraBlock.FACING)) {
			return;
		}
		Direction f = s.getValue(SecurityCameraBlock.FACING);
		boolean live = s.getValue(SensorBlockEntity.LIVE);
		float time = be.getLevel().getGameTime() + partial;
		float yaw = live ? SecurityCameraBlockEntity.yaw(f, be.getBlockPos(), time) : f.toYRot();
		pose.pushPose();
		Vec3 pivot = new Vec3(0.5 + f.getStepX() * 0.12, 0.55, 0.5 + f.getStepZ() * 0.12);
		pose.translate(pivot.x, pivot.y, pivot.z);
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.mulPose(Axis.XP.rotationDegrees(live ? SecurityCameraBlockEntity.PITCH : 40f));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TEX));
		Matrix4f m = pose.last().pose();
		box(pose, vc, -0.14f, -0.12f, -0.12f, 0.14f, 0.12f, 0.36f, light);
		box(pose, vc, -0.09f, -0.08f, 0.36f, 0.09f, 0.08f, 0.42f, light); // the lens hood
		if (live) {
			VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
			boolean blink = ((int) (time / 10)) % 2 == 0;
			float a = blink ? 0.95f : 0.5f;
			quadColour(m, glow, -0.05f, -0.05f, 0.425f, 0.05f, 0.05f, 1f, 0.1f, 0.05f, a);
			// the faint view wedge
			float len = 3.0f;
			float half = (float) Math.tan(Math.toRadians(25)) * len;
			glow.addVertex(m, 0, 0, 0.42f).setColor(1f, 0.1f, 0.05f, 0.10f);
			glow.addVertex(m, -half, 0, len).setColor(1f, 0.1f, 0.05f, 0.0f);
			glow.addVertex(m, half, 0, len).setColor(1f, 0.1f, 0.05f, 0.0f);
			glow.addVertex(m, 0, 0, 0.42f).setColor(1f, 0.1f, 0.05f, 0.10f);
			glow.addVertex(m, 0, 0, 0.42f).setColor(1f, 0.1f, 0.05f, 0.10f);
			glow.addVertex(m, half, 0, len).setColor(1f, 0.1f, 0.05f, 0.0f);
			glow.addVertex(m, -half, 0, len).setColor(1f, 0.1f, 0.05f, 0.0f);
			glow.addVertex(m, 0, 0, 0.42f).setColor(1f, 0.1f, 0.05f, 0.10f);
		}
		pose.popPose();
	}

	private static void quadColour(Matrix4f m, VertexConsumer vc, float x0, float y0, float z, float x1, float y1, float r, float g, float b, float a) {
		vc.addVertex(m, x0, y0, z).setColor(r, g, b, a);
		vc.addVertex(m, x1, y0, z).setColor(r, g, b, a);
		vc.addVertex(m, x1, y1, z).setColor(r, g, b, a);
		vc.addVertex(m, x0, y1, z).setColor(r, g, b, a);
	}

	/** A textured box (the whole 16x16 texture on every face). */
	static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
		PoseStack.Pose p = pose.last();
		face(p, vc, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		face(p, vc, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
		face(p, vc, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
		face(p, vc, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		face(p, vc, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		face(p, vc, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
	}

	private static void face(PoseStack.Pose p, VertexConsumer vc, int light, float nx, float ny, float nz, float... v) {
		float[][] uv = { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 0, 0 } };
		for (int i = 0; i < 4; i++) {
			vc.addVertex(p, v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).setColor(255, 255, 255, 255).setUv(uv[i][0], uv[i][1])
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		}
	}

	@Override
	public int getViewDistance() {
		return 48;
	}
}
