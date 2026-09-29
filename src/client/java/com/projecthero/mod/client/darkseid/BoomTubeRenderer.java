package com.projecthero.mod.client.darkseid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.darkseid.entity.BoomTubeEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import org.joml.Matrix4f;

/**
 * A Boom Tube: two counter-rotating, full-bright discs of crackling white-blue light that always face the viewer,
 * sized by the tube's open/close ramp. Elite and overload tubes burn red at the rim; Darkseid's own are huge.
 */
public class BoomTubeRenderer extends EntityRenderer<BoomTubeEntity> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/boom_tube.png");

	public BoomTubeRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(BoomTubeEntity tube, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		float open = tube.openFraction(partialTick);
		if (open <= 0.01f) {
			return;
		}
		float size = tube.size() * open;
		float age = tube.tickCount + partialTick;
		int tint = switch (tube.kind()) {
			case ELITE, OVERLOAD -> 0xFFB0A0;
			case EXIT, ENTRANCE -> 0xE8F0FF;
			default -> 0xD0E8FF;
		};
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
		poseStack.pushPose();
		poseStack.translate(0.0f, 0.25f, 0.0f);
		poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
		poseStack.pushPose();
		poseStack.mulPose(Axis.ZP.rotationDegrees(age * 9.0f));
		disc(vc, poseStack, size * 0.5f, tint, 0.95f);
		poseStack.popPose();
		poseStack.pushPose();
		poseStack.mulPose(Axis.ZP.rotationDegrees(-age * 14.0f));
		disc(vc, poseStack, size * 0.36f, 0xFFFFFF, 1.0f);
		poseStack.popPose();
		poseStack.popPose();
		super.render(tube, yaw, partialTick, poseStack, buffers, light);
	}

	private static void disc(VertexConsumer vc, PoseStack poseStack, float half, int rgb, float alpha) {
		PoseStack.Pose pose = poseStack.last();
		Matrix4f m = pose.pose();
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = (int) (alpha * 255);
		int full = 0xF000F0;
		vc.addVertex(m, -half, -half, 0).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
		vc.addVertex(m, half, -half, 0).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
		vc.addVertex(m, half, half, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
		vc.addVertex(m, -half, half, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose, 0, 0, 1);
	}

	@Override
	public ResourceLocation getTextureLocation(BoomTubeEntity entity) {
		return TEXTURE;
	}
}
