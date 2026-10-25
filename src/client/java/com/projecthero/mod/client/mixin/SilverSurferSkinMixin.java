package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.silversurfer.SilverSurferClientState;
import com.projecthero.mod.client.silversurfer.SilverSurferRender;
import com.projecthero.mod.silversurfer.SilverSurfer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1 Silver Surfer: the player is drawn in the Herald's skin ({@link SilverSurferRender#SKIN}) through
 * {@code getTextureLocation}; the first-person arm is redrawn in it ({@code renderHand} reads the real skin directly, and
 * the model's own arm is hidden while he has the power); and on the board the whole body leans forward with his speed and
 * banks into turns.
 */
@Mixin(PlayerRenderer.class)
public abstract class SilverSurferSkinMixin {
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
			at = @At("RETURN"), cancellable = true)
	private void projecthero$surferSkin(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
		if (SilverSurferRender.shows(player)) {
			cir.setReturnValue(SilverSurferRender.SKIN);
		}
	}

	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$surferArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (!SilverSurferRender.shows(player) || player.isInvisible()) {
			return;
		}
		PlayerRenderer self = (PlayerRenderer) (Object) this;
		SilverSurferRender.renderFirstPersonArm(pose, buffers, light, arm, arm == self.getModel().rightArm);
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$surferBank(AbstractClientPlayer player, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale,
			CallbackInfo ci) {
		if (!SilverSurfer.surfing(player)) {
			return;
		}
		double dx = player.getX() - player.xo;
		double dz = player.getZ() - player.zo;
		double speed = Math.sqrt(dx * dx + dz * dz);
		float lean = SilverSurfer.lightSpeed(player) ? 30f : (float) Mth.clamp(speed * 6.0, 0.0, 20.0);
		float bank = SilverSurferClientState.bank(player, partialTick);
		// forward is -z and his right is +x in this frame (before the model's flip)
		pose.mulPose(Axis.XP.rotationDegrees(-lean));
		pose.mulPose(Axis.ZP.rotationDegrees(-bank));
	}
}
