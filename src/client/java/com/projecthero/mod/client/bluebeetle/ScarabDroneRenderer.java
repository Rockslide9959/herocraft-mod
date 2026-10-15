package com.projecthero.mod.client.bluebeetle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.bluebeetle.entity.ScarabDroneEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** v0.16.5: a Scarab Swarm drone -- a small copy of the voxel Scarab ({@link BlueBeetleTech}), nose-first and buzzing. */
public class ScarabDroneRenderer extends EntityRenderer<ScarabDroneEntity> {
	public ScarabDroneRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public void render(ScarabDroneEntity drone, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		float age = drone.tickCount + partial;
		pose.translate(0, 0.15 + 0.05 * Mth.sin(age * 0.9f), 0);
		pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partial, drone.yRotO, drone.getYRot())));
		pose.mulPose(Axis.XP.rotationDegrees(-90f)); // lying flat: carapace up, head first
		pose.mulPose(Axis.ZP.rotationDegrees(8f * Mth.sin(age * 2.2f)));
		pose.scale(0.9f, 0.9f, 0.9f);
		BlueBeetleTech.renderScarabCentred(pose, buffers, light);
		pose.popPose();
		super.render(drone, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(ScarabDroneEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
