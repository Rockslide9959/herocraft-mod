package com.projecthero.mod.client.miraculous.renarouge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.miraculous.renarouge.RenaRougeConfig;
import com.projecthero.mod.miraculous.renarouge.entity.MirageBubbleEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** v0.16.6: the Mirage's orange bubble -- a glowing billboard that swells and wobbles as it floats, until it pops. */
public class MirageBubbleRenderer extends EntityRenderer<MirageBubbleEntity> {
	public MirageBubbleRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0f;
	}

	@Override
	public void render(MirageBubbleEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = e.tickCount + partialTick;
		float t = Mth.clamp(age / RenaRougeConfig.MIRAGE_BUBBLE_TICKS, 0f, 1f);
		float size = 0.25f + 1.1f * t;
		float wob = 1f + 0.06f * Mth.sin(age * 1.3f);
		pose.pushPose();
		pose.translate(0f, 0.25f, 0f);
		pose.mulPose(entityRenderDispatcher.cameraOrientation());
		pose.scale(size * wob, size / wob, size);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(RenaRougeRender.BUBBLE));
		PoseStack.Pose last = pose.last();
		vertex(vc, last, -0.5f, -0.5f, 0f, 1f);
		vertex(vc, last, 0.5f, -0.5f, 1f, 1f);
		vertex(vc, last, 0.5f, 0.5f, 1f, 0f);
		vertex(vc, last, -0.5f, 0.5f, 0f, 0f);
		vertex(vc, last, -0.5f, 0.5f, 0f, 0f);
		vertex(vc, last, 0.5f, 0.5f, 1f, 0f);
		vertex(vc, last, 0.5f, -0.5f, 1f, 1f);
		vertex(vc, last, -0.5f, -0.5f, 0f, 1f);
		pose.popPose();
		super.render(e, yaw, partialTick, pose, buffers, light);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose last, float x, float y, float u, float v) {
		vc.addVertex(last, x, y, 0f).setColor(255, 255, 255, 235).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, 1f, 0f);
	}

	@Override
	public ResourceLocation getTextureLocation(MirageBubbleEntity e) {
		return RenaRougeRender.BUBBLE;
	}
}
