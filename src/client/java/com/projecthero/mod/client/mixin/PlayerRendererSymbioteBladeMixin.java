package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.symbiote.SymbioteBladeRenderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.13.19 first person: grow the Symbiote Blade out of the hand the player sees. Injected at the TAIL of
 * {@code renderHand}, after vanilla drew the arm, so the blade follows every swing and bob -- same approach as
 * {@link PlayerRendererClawsMixin}. Other viewers see it through {@code SymbioteBladeRenderer.Layer}.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererSymbioteBladeMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$symbioteBlade(PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player) {
			return;
		}
		PlayerModel<AbstractClientPlayer> playerModel = ((PlayerRenderer) (Object) this).getModel();
		// v0.15.15: Symbiote Spikes bristling out of the arm while the spikes are out
		com.projecthero.mod.client.symbiote.SymbioteBodySpikes.renderFirstPerson(pose, buffers, light, player, arm,
				arm == playerModel.rightArm);
		if (!SymbioteBladeRenderer.visible(player)) {
			return;
		}
		SymbioteBladeRenderer.renderFirstPerson(pose, buffers, light, player, arm, arm == playerModel.rightArm);
	}
}
