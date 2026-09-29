package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.moonknight.MoonKnightPose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.21: Moon Knight's Cape Glide pose, the body half (the limbs are set in {@link MoonKnightPose}). The glide is
 * held with Sneak, so vanilla would draw him crouched and upright; instead:
 * <ul>
 *   <li>the model's crouch is switched off for as long as he glides (and the crouch's 2 px render drop with it), so
 *       the legs stay straight and the GeckoLib suit, which copies the model, follows;</li>
 *   <li>the whole body tips forward, nearly flat -- {@link MoonKnightPose#glideLean} degrees, eased in and out and
 *       steepening a little as he looks down -- pivoting about the middle of the body so he stays inside his
 *       hitbox rather than swinging out from the feet.</li>
 * </ul>
 * Driven by the synced {@code FLAG_GLIDING}, so every viewer sees the same pose.
 */
@Mixin(PlayerRenderer.class)
public abstract class MoonKnightGlidePoseMixin {
	/** The first-person hand runs the same setupAnim: the glide's spread arms must not swing it out of view. */
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("HEAD"))
	private void projecthero$moonKnightHandStart(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
			int light, AbstractClientPlayer player, net.minecraft.client.model.geom.ModelPart arm,
			net.minecraft.client.model.geom.ModelPart sleeve, CallbackInfo ci) {
		MoonKnightPose.firstPersonHand = true;
	}

	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$moonKnightHandEnd(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
			int light, AbstractClientPlayer player, net.minecraft.client.model.geom.ModelPart arm,
			net.minecraft.client.model.geom.ModelPart sleeve, CallbackInfo ci) {
		MoonKnightPose.firstPersonHand = false;
	}

	@Inject(method = "setModelProperties(Lnet/minecraft/client/player/AbstractClientPlayer;)V", at = @At("TAIL"))
	private void projecthero$moonKnightGlideNoCrouch(AbstractClientPlayer player, CallbackInfo ci) {
		if (MoonKnightPose.isGliding(player)) {
			((PlayerRenderer) (Object) this).getModel().crouching = false;
		}
	}

	@Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",
			at = @At("RETURN"), cancellable = true)
	private void projecthero$moonKnightGlideNoCrouchOffset(AbstractClientPlayer player, float partialTicks,
			CallbackInfoReturnable<Vec3> cir) {
		if (MoonKnightPose.isGliding(player)) {
			cir.setReturnValue(Vec3.ZERO);
		}
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$moonKnightGlideLean(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks,
			float rotationYaw, float partialTicks, float scale, CallbackInfo ci) {
		float lean = MoonKnightPose.glideLean(player, partialTicks);
		if (lean < 0.5f) {
			return;
		}
		// pivot about the middle of the body; negative X tips the head forward (see PlayerRendererMixin)
		float pivot = 0.9f * scale;
		poseStack.translate(0.0f, pivot, 0.0f);
		poseStack.mulPose(Axis.XP.rotationDegrees(-lean));
		poseStack.translate(0.0f, -pivot, 0.0f);
	}
}
