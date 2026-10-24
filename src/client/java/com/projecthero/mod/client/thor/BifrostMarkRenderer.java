package com.projecthero.mod.client.thor;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.stormbreaker.BifrostEntities;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the rune circle the Bifrost burns into the ground -- rings of Elder Futhark runes round a knotwork centre. It is
 * drawn twice: a scorched layer lit like the ground under it (it stays, fading out over its last minute), and a glowing
 * white-gold layer over it that is hot when the bridge lands and cools away over {@link BifrostEntities#MARK_GLOW_TICKS}.
 */
public class BifrostMarkRenderer extends EntityRenderer<BifrostEntities.Mark> {
	public static final ResourceLocation RUNES = ProjectHeroMod.id("textures/entity/bifrost_runes.png");
	private static final float HALF = 3.2f;

	public BifrostMarkRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(BifrostEntities.Mark e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = e.markAge() + partial;
		float fadeOut = Mth.clamp((BifrostEntities.MARK_TICKS - age) / 1200f, 0f, 1f);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		float grow = Mth.clamp(age / 6f, 0f, 1f);
		float half = HALF * (0.6f + 0.4f * grow);
		// the scorch, lit like the ground
		quad(buffers.getBuffer(RenderType.entityTranslucent(RUNES)), pose, half, 0.0f, 0x2A1A0F, 0.85f * fadeOut * grow, light);
		// the glow, cooling away
		float glow = 1f - Mth.clamp(age / BifrostEntities.MARK_GLOW_TICKS, 0f, 1f);
		if (glow > 0.01f) {
			float pulse = 0.85f + 0.15f * Mth.sin(age * 0.4f);
			int colour = age < 20f ? 0xFFFFFF : 0xFFE6A0;
			quad(buffers.getBuffer(RenderType.entityTranslucentEmissive(RUNES)), pose, half, 0.004f, colour, glow * pulse, LightTexture.FULL_BRIGHT);
		}
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	private static void quad(VertexConsumer vc, PoseStack pose, float half, float y, int rgb, float alpha, int light) {
		Matrix4f m = pose.last().pose();
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = Mth.clamp((int) (alpha * 255), 0, 255);
		vc.addVertex(m, -half, y, -half).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 1, 0);
		vc.addVertex(m, -half, y, half).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 1, 0);
		vc.addVertex(m, half, y, half).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 1, 0);
		vc.addVertex(m, half, y, -half).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 1, 0);
	}

	@Override
	public boolean shouldRender(BifrostEntities.Mark e, Frustum frustum, double x, double y, double z) {
		return frustum.isVisible(e.getBoundingBoxForCulling());
	}

	@Override
	public ResourceLocation getTextureLocation(BifrostEntities.Mark e) {
		return RUNES;
	}
}
