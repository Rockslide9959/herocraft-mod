package com.projecthero.mod.client.mutation.cryo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p09.IceJavelinEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Cryokinesis hold-R: the Ice Javelin -- a 2.6-block spear: a long faceted head of clear ice with a glowing
 * pale-blue core, a packed-ice haft bound with three blue-ice rings, a crown of swept fins behind the head and a short
 * reversed point at the butt. While it forms it rides over the caster's shoulder along his look, growing out of a
 * sliver with frost motes orbiting it; in flight it spins slowly along its axis.
 */
public class IceJavelinRenderer extends EntityRenderer<IceJavelinEntity> {
	public IceJavelinRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public boolean shouldRender(IceJavelinEntity e, Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(IceJavelinEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = e.tickCount + partial;
		float formed = e.formed(partial);
		pose.pushPose();
		float yaw;
		float pitch;
		Entity owner = e.getOwner();
		if (e.charge() > 0 && owner != null) {
			// held: over the caster's shoulder, along his look
			Vec3 here = new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()));
			Vec3 hand = IceJavelinEntity.handPoint(owner, partial);
			Vec3 off = hand.subtract(here);
			pose.translate(off.x, off.y, off.z);
			Vec3 look = owner.getViewVector(partial);
			yaw = (float) (Mth.atan2(look.x, look.z) * Mth.RAD_TO_DEG);
			pitch = (float) (Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG) + 8.0f;
		} else {
			Vec3 v = e.getDeltaMovement();
			yaw = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG) : e.getYRot();
			pitch = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG) : 0.0f;
		}
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(-pitch));
		// the crystal's +Y along the flight (+Z)
		pose.mulPose(Axis.XP.rotationDegrees(90));
		float s = 0.25f + 0.75f * formed;
		pose.scale(s, s, s);
		pose.mulPose(Axis.YP.rotationDegrees(t * (e.charge() > 0 ? 6 : 14)));
		int full = LightTexture.FULL_BRIGHT;
		pose.translate(0, -1.3, 0);
		// the butt point
		pose.pushPose();
		pose.mulPose(Axis.XP.rotationDegrees(180));
		IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), 0.16f, 0.3f, 0, 0xF0FFFFFF, full);
		pose.popPose();
		// the haft
		IceDraw.box(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), -0.07f, 0.0f, -0.07f, 0.07f, 1.55f, 0.07f, 0.5f, 0xF2FFFFFF, full);
		for (int i = 0; i < 3; i++) {
			float y = 0.2f + i * 0.55f;
			IceDraw.box(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.BLUE)), -0.1f, y, -0.1f, 0.1f, y + 0.09f, 0.1f, 0.5f, 0xF8FFFFFF, full);
		}
		// the head: a long faceted crystal with a glowing core
		pose.pushPose();
		pose.translate(0, 1.5, 0);
		IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.38f, 1.15f, 0.25f, 0xE8E6F8FF, full);
		IceDraw.crystal(pose, buffers.getBuffer(RenderType.eyes(IceDraw.WHITE)), 0.14f, 1.05f, 0.3f, 0xFFB8ECFF, full);
		// the crown of swept fins where the head meets the haft
		for (int i = 0; i < 4; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 90 + 45));
			pose.translate(0.14, 0.12, 0);
			pose.mulPose(Axis.ZP.rotationDegrees(150));
			IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.1f, 0.5f, 0, 0xD8DCF4FF, full);
			pose.popPose();
		}
		pose.popPose();
		// forming: frost motes circling the spear
		if (e.charge() > 0) {
			for (int i = 0; i < 6; i++) {
				float a = t * 0.5f + i * Mth.TWO_PI / 6;
				pose.pushPose();
				pose.translate(Mth.cos(a) * 0.45f, 0.4f + i * 0.28f, Mth.sin(a) * 0.45f);
				pose.mulPose(Axis.ZP.rotation(a));
				IceDraw.shard(pose, buffers.getBuffer(RenderType.eyes(IceDraw.WHITE)), 0.06f, 0.2f, 0xC0CFF2FF, full);
				pose.popPose();
			}
		}
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(IceJavelinEntity entity) {
		return IceDraw.ICE;
	}
}
