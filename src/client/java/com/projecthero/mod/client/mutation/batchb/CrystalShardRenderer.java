package com.projecthero.mod.client.mutation.batchb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.hero.power.p06.CrystalShardEntity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 Crystalkinesis: the flying shard -- a slim, diamond-section amethyst spike (vanilla amethyst texture),
 * full-bright, pointing along its flight and spinning about it, with a tighter bright core.
 */
public class CrystalShardRenderer extends EntityRenderer<CrystalShardEntity> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/amethyst_block.png");
	private static ModelPart shard;
	private static ModelPart core;

	public CrystalShardRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(CrystalShardEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int light) {
		if (shard == null) {
			shard = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -1.5f, -5.0f, 3, 3, 10), 32, 32);
			core = MutationRender.bake(CubeListBuilder.create().texOffs(4, 4).addBox(-0.6f, -0.6f, -6.5f, 1.2f, 1.2f, 13), 32, 32);
		}
		Vec3 v = e.getDeltaMovement();
		float yaw = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG) : e.getYRot();
		float pitch = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG) : 0.0f;
		pose.pushPose();
		pose.translate(0.0, 0.15, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(-pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(45.0f + (e.tickCount + partialTick) * 30.0f));
		shard.render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, 0xE6FFFFFF);
		core.render(pose, buffers.getBuffer(RenderType.eyes(TEXTURE)), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, 0xFFE8D0FF);
		pose.popPose();
		super.render(e, entityYaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(CrystalShardEntity entity) {
		return TEXTURE;
	}
}
