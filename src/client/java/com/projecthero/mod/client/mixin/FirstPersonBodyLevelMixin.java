package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.client.fpbody.CullingBufferSource;
import com.projecthero.mod.client.fpbody.FirstPersonBody;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15 full-body first person ({@link FirstPersonBody}): while a suit-up / suit-down runs, the world pass draws the
 * local player even though the camera is at their eyes -- set back a little along the body's facing and through the
 * head-culling buffer source ({@link CullingBufferSource}).
 */
@Mixin(LevelRenderer.class)
public abstract class FirstPersonBodyLevelMixin {
	/** The entity loop skips the camera's own entity unless the camera is detached: count it as detached for this. */
	@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
	private boolean projecthero$fpBodyDetached(boolean detached) {
		if (FirstPersonBody.active()) {
			return true;
		}
		FirstPersonBody.inactive();
		return detached;
	}

	@WrapOperation(method = "renderEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void projecthero$fpBodyRender(EntityRenderDispatcher dispatcher, Entity entity, double x, double y, double z, float yaw,
			float partialTick, PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
		Minecraft mc = Minecraft.getInstance();
		Entity self = mc.player;
		if (self == null || mc.getCameraEntity() != self || !mc.options.getCameraType().isFirstPerson()
				|| entity != self && !FirstPersonBody.companion(entity, self) || !FirstPersonBody.active()) {
			original.call(dispatcher, entity, x, y, z, yaw, partialTick, pose, buffers, light);
			return;
		}
		Vec3 off = FirstPersonBody.offset(self, partialTick);
		CullingBufferSource culled = new CullingBufferSource(buffers);
		FirstPersonBody.begin(entity, culled);
		pose.pushPose();
		try {
			FirstPersonBody.applySwing(pose, self, partialTick);
			original.call(dispatcher, entity, x + off.x, y + off.y, z + off.z, yaw, partialTick, pose, culled, light);
			culled.flush();
		} finally {
			pose.popPose();
			FirstPersonBody.end();
		}
	}
}
