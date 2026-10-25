package com.projecthero.mod.client.falcon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.falcon.entity.RedwingEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** v0.17.1: Redwing -- the box-built drone ({@link FalconGeometry#redwing}), nose-first, bobbing, banking as it turns. */
public class RedwingRenderer extends EntityRenderer<RedwingEntity> {
	public RedwingRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.15f;
	}

	@Override
	public void render(RedwingEntity drone, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		float age = drone.tickCount + partial;
		pose.translate(0, 0.15 + 0.04 * Mth.sin(age * 0.25f), 0);
		float bodyYaw = Mth.rotLerp(partial, drone.yRotO, drone.getYRot());
		pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
		float turn = Mth.wrapDegrees(drone.getYRot() - drone.yRotO);
		pose.mulPose(Axis.ZP.rotationDegrees(Mth.clamp(turn * 3f, -35f, 35f)));
		if (drone.mode() == RedwingEntity.MODE_BOMB) {
			pose.mulPose(Axis.XP.rotationDegrees(12f)); // nose down on the run
		}
		pose.scale(0.9f, 0.9f, 0.9f);
		FalconGeometry.redwing(pose, buffers, light, age, drone.busy());
		pose.popPose();
		super.render(drone, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(RedwingEntity entity) {
		return FalconGeometry.WHITE;
	}
}
