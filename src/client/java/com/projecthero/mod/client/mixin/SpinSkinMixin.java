package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.spin.SpinLayer;
import com.projecthero.mod.spin.Spin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3 the Spin: a Steel Ball in the empty right hand in first person ({@link SpinLayer#renderFirstPersonBall}). The Spin never
 * changes the player's skin (user, 2026-11-12).
 */
@Mixin(PlayerRenderer.class)
public abstract class SpinSkinMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$spinHandBall(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (!Spin.hasPower(player) || player.isInvisible() || arm != ((PlayerRenderer) (Object) this).getModel().rightArm) {
			return;
		}
		float age = player.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		SpinLayer.renderFirstPersonBall(pose, buffers, light, player, arm, age);
	}
}
