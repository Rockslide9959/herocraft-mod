package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.miraculous.viperion.ViperionFirstPerson;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.7: Viperion in first person -- the suit sleeve over the bare arm vanilla just rendered, the Snake Bracelet on
 * the right wrist and (main arm, during a move) the Lyre in the fist. See {@link ViperionFirstPerson}.
 */
@Mixin(PlayerRenderer.class)
public abstract class ViperionFirstPersonArmMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$viperionArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player || player.isInvisible()) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		ViperionFirstPerson.renderArm(pose, buffers, light, player, arm, arm == model.rightArm, player.tickCount + partial, partial);
	}
}
