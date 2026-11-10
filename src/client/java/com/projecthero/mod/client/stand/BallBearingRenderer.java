package com.projecthero.mod.client.stand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.stand.entity.BallBearingEntity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: the flicked ball bearing -- a small polished steel ball (a bright core inside a darker shell, so it reads round at a glance)
 * with a short silver streak behind it along its flight.
 */
public final class BallBearingRenderer extends EntityRenderer<BallBearingEntity> {
	private static ModelPart ball;

	public BallBearingRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(BallBearingEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		if (ball == null) {
			// a rounded ball: three crossed boxes, the corners cut away
			ball = MutationRender.bake(CubeListBuilder.create()
					.texOffs(0, 0).addBox(-1.2f, -0.8f, -0.8f, 2.4f, 1.6f, 1.6f)
					.texOffs(0, 0).addBox(-0.8f, -1.2f, -0.8f, 1.6f, 2.4f, 1.6f)
					.texOffs(0, 0).addBox(-0.8f, -0.8f, -1.2f, 1.6f, 1.6f, 2.4f), 4, 4);
		}
		int lit = Math.max(light, LightTexture.pack(12, 15));
		var vc = buffers.getBuffer(RenderType.entitySolid(MutationRender.WHITE));
		Vec3 v = e.getDeltaMovement();
		pose.pushPose();
		pose.translate(0f, e.getBbHeight() * 0.5f, 0f);
		float spin = (e.tickCount + partial) * 40f;
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(spin));
		pose.mulPose(Axis.XP.rotationDegrees(spin * 0.7f));
		ball.render(pose, vc, lit, OverlayTexture.NO_OVERLAY, MutationRender.argb(255, 196, 204, 214));
		pose.popPose();
		// the streak: shrinking steel beads strung back along the path
		double speed = v.length();
		if (speed > 0.2 && e.tickCount > 0) {
			Vec3 back = v.normalize().scale(-Math.min(1.6, speed * 0.4));
			for (int i = 1; i <= 4; i++) {
				pose.pushPose();
				float f = i / 5f;
				pose.translate(back.x * f, back.y * f, back.z * f);
				float s = 0.8f - 0.15f * i;
				pose.scale(s, s, s);
				int g = 200 - 18 * i;
				ball.render(pose, vc, lit, OverlayTexture.NO_OVERLAY, MutationRender.argb(255, g, g + 6, g + 14));
				pose.popPose();
			}
		}
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(BallBearingEntity e) {
		return MutationRender.WHITE;
	}
}
