package com.projecthero.mod.client.miraculous.catnoir;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.miraculous.catnoir.entity.CatBatonEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** v0.16.6: the thrown baton -- extended to staff length, whirling end over end round its line of flight. */
public class CatBatonRenderer extends EntityRenderer<CatBatonEntity> {
	private static final float SIDE = 0.8f;

	public CatBatonRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.1f;
	}

	@Override
	public void render(CatBatonEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		Vec3 v = e.getDeltaMovement();
		float heading = v.horizontalDistanceSqr() < 1.0e-6 ? 0f : (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
		pose.pushPose();
		pose.translate(0, 0.25, 0);
		pose.mulPose(Axis.YP.rotationDegrees(heading));
		// end over end: turning about the axis across the line of flight
		pose.mulPose(Axis.XP.rotationDegrees((e.tickCount + partial) * 52f));
		CatNoirBaton.render(pose, buffers, light, SIDE, SIDE, SIDE, SIDE, e.rotted());
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(CatBatonEntity e) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
