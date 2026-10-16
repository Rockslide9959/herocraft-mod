package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.mutation.CryokinesisClient;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.5 Cryokinesis: the Ice Blade (and Frozen Armor's bracer) on the arm you see in first person. Injected at the TAIL
 * of {@code renderHand}, after vanilla drew the arm, so they follow every swing and bob.
 */
@Mixin(PlayerRenderer.class)
public abstract class CryoFirstPersonArmMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$cryoArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		CryokinesisClient.renderFirstPersonArm(pose, buffers, light, player, arm,
				arm == ((PlayerRenderer) (Object) this).getModel().rightArm);
	}
}
