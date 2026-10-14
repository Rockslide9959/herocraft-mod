package com.projecthero.mod.client.hulk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.hulk.entity.MirrorHulkEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * v0.16.4 (user): draws the Hulk in the mirror of a player's mind. For its owner, while it is still his reflection, it is
 * drawn at the exact reflection of where he is this frame -- his interpolated position mirrored across the plane
 * z = {@link MirrorHulkEntity#mirrorZ}, his body / head yaw mirrored (180 - yaw) and his pitch -- instead of at the entity's
 * synced position, so the reflection never trails him by the network lerp. Once the smash starts the server moves it and
 * it is drawn where it is. The Gladiator Hulk's gear bones are always hidden ({@link HulkGladiatorLayer#baseVisibility}).
 */
public class MirrorHulkRenderer extends GeoEntityRenderer<MirrorHulkEntity> {
	public MirrorHulkRenderer(EntityRendererProvider.Context context) {
		super(context, new MirrorHulkModel());
		this.shadowRadius = 0.8f;
		withScale(HulkRenderer.MODEL_FIT); // v0.16.4: drawn exactly as tall as his hitbox, like the player Hulk
	}

	@Override
	public boolean shouldRender(MirrorHulkEntity entity, Frustum frustum, double x, double y, double z) {
		return true; // drawn away from its synced position; there is only ever one, in a tiny room
	}

	@Override
	public void render(MirrorHulkEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
			int packedLight) {
		Player owner = entity.owner();
		Minecraft mc = Minecraft.getInstance();
		if (entity.smashing() || owner == null || owner != mc.player) {
			super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
			return;
		}
		double mz = entity.mirrorZ();
		Vec3 him = new Vec3(Mth.lerp(partialTick, owner.xo, owner.getX()), Mth.lerp(partialTick, owner.yo, owner.getY()),
				Mth.lerp(partialTick, owner.zo, owner.getZ()));
		Vec3 at = MirrorHulkEntity.reflect(him, mz);
		Vec3 drawn = new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()), Mth.lerp(partialTick, entity.yOld, entity.getY()),
				Mth.lerp(partialTick, entity.zOld, entity.getZ()));
		float body = MirrorHulkEntity.reflectYaw(Mth.rotLerp(partialTick, owner.yBodyRotO, owner.yBodyRot));
		// v0.16.4 (user): while he faces the mirror the Hulk looks straight at his eyes (not his pitch mirrored)
		float[] look = MirrorHulkEntity.headLook(owner, at, body, MirrorHulkEntity.reflectYaw(Mth.rotLerp(partialTick, owner.yHeadRotO, owner.yHeadRot)),
				Mth.lerp(partialTick, owner.xRotO, owner.getXRot()), owner.getEyePosition(partialTick), entity.getEyeHeight(), mz);
		float head = look[0];
		float pitch = look[1];
		entity.yBodyRotO = body;
		entity.yBodyRot = body;
		entity.yHeadRotO = head;
		entity.yHeadRot = head;
		entity.xRotO = pitch;
		entity.setXRot(pitch);
		poseStack.pushPose();
		poseStack.translate(at.x - drawn.x, at.y - drawn.y, at.z - drawn.z);
		super.render(entity, body, partialTick, poseStack, buffer, packedLight);
		poseStack.popPose();
	}

	@Override
	public void preRender(PoseStack poseStack, MirrorHulkEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		if (!isReRender) {
			HulkGladiatorLayer.baseVisibility(model);
		}
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
	}
}
