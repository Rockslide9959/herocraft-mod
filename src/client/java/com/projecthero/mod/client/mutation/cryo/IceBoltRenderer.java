package com.projecthero.mod.client.mutation.cryo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p09.IceBoltEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Cryokinesis projectiles: the Ice Bolt is a long faceted lance of clear ice with a glowing pale-blue core and
 * three trailing fins, spinning along its flight; an Ice Shard is a small jagged bipyramid tumbling as it flies; Shatter
 * debris is a chip of packed ice spinning off.
 */
public class IceBoltRenderer extends EntityRenderer<IceBoltEntity> {
	public IceBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(IceBoltEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		Vec3 v = e.getDeltaMovement();
		float yaw = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG) : e.getYRot();
		float pitch = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG) : 0.0f;
		float t = e.tickCount + partial;
		int kind = e.kind();
		pose.pushPose();
		pose.translate(0.0, 0.15, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(-pitch));
		// lay the crystal's +Y axis along the flight (+Z)
		pose.mulPose(Axis.XP.rotationDegrees(90));
		if (kind == IceBoltEntity.BOLT) {
			pose.mulPose(Axis.YP.rotationDegrees(t * 24));
			pose.translate(0, -0.35, 0);
			// the lance: a long tapered crystal, the tail a short reversed point
			IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.24f, 1.15f, 0.35f, 0xE8E6F8FF, LightTexture.FULL_BRIGHT);
			pose.pushPose();
			pose.mulPose(Axis.XP.rotationDegrees(180));
			IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.24f, 0.28f, 0, 0xE8E6F8FF, LightTexture.FULL_BRIGHT);
			pose.popPose();
			// the glowing core
			IceDraw.crystal(pose, buffers.getBuffer(RenderType.eyes(IceDraw.WHITE)), 0.09f, 1.05f, 0.4f, 0xFFB8ECFF, LightTexture.FULL_BRIGHT);
			// three fins at the tail
			for (int i = 0; i < 3; i++) {
				pose.pushPose();
				pose.mulPose(Axis.YP.rotationDegrees(i * 120));
				pose.translate(0.1, 0.05, 0);
				pose.mulPose(Axis.ZP.rotationDegrees(-28));
				IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.07f, 0.34f, 0, 0xD0D8F4FF, LightTexture.FULL_BRIGHT);
				pose.popPose();
			}
		} else if (kind == IceBoltEntity.SHARD) {
			pose.mulPose(Axis.YP.rotationDegrees(t * 40 + e.getId() * 37));
			pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.7f + e.getId()) * 25));
			IceDraw.shard(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.2f, 0.9f, 0xE6E0F4FF, LightTexture.FULL_BRIGHT);
			IceDraw.shard(pose, buffers.getBuffer(RenderType.eyes(IceDraw.WHITE)), 0.08f, 0.72f, 0xC09FE4FF, LightTexture.FULL_BRIGHT);
		} else {
			pose.mulPose(Axis.YP.rotationDegrees(t * 30 + e.getId() * 51));
			pose.mulPose(Axis.ZP.rotationDegrees(t * 22));
			float s = 0.08f + (e.getId() % 4) * 0.025f;
			IceDraw.box(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), -s, -s, -s, s, s * 1.4f, s, 0.5f,
					0xF0FFFFFF, light);
		}
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(IceBoltEntity entity) {
		return IceDraw.ICE;
	}
}
