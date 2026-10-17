package com.projecthero.mod.client.miraculous.hawkmoth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.miraculous.hawkmoth.entity.AkumaEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** v0.16.6: an akuma in flight -- the dark-purple {@link ButterflyModel}, wings beating fast (its trail: the client tick). */
public class AkumaRenderer extends EntityRenderer<AkumaEntity> {
	public AkumaRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public void render(AkumaEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
		float pitch = Mth.lerp(partial, e.xRotO, e.getXRot());
		float beat = (e.tickCount + partial) * 1.6f + e.phase;
		pose.pushPose();
		pose.translate(0, 0.1, 0);
		ButterflyModel.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(ButterflyModel.AKUMA)), light, yaw, pitch, beat, 0.9f, 0xFFFFFFFF);
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(AkumaEntity e) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
