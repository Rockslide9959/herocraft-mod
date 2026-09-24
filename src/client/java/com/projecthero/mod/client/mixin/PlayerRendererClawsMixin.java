package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.wolverine.WolverineClawsModel;
import com.projecthero.mod.client.wolverine.WolverineClawsRenderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * First person: draw Wolverine's claws on the hand the player sees. Injected at the TAIL of
 * {@code renderHand}, after vanilla (and {@link PlayerRendererHandMixin}) drew the arm, so the blades
 * follow every swing / bob animation for free. Only the local player is ever drawn here; other
 * viewers see the claws through {@code WolverineClawsLayer}.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererClawsMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$wolverineClaws(PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player || !WolverineClawsModel.visible(player, 1.0f)) {
			return;
		}
		PlayerModel<AbstractClientPlayer> playerModel = ((PlayerRenderer) (Object) this).getModel();
		WolverineClawsRenderer.renderFirstPerson(pose, buffers, light, player, arm == playerModel.rightArm, arm);
	}
}
