package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.miraculous.renarouge.RenaRougeRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;

/**
 * v0.16.6: Rena Rouge's sleeve and black glove in first person -- the suit arm drawn over the bare arm vanilla just
 * rendered (her own sleeve layer is hidden while the suit shows), so it follows every swing of the hand.
 */
@Mixin(PlayerRenderer.class)
public abstract class RenaRougeFirstPersonArmMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$renaRougeSleeve(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player || player.isInvisible()) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float cover = RenaRougeRender.cover(player, partial);
		if (cover <= 0f) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		RenaRougeRender.renderArm(pose, buffers, light, arm, arm == model.rightArm, player.getSkin().model() == PlayerSkin.Model.SLIM, cover);
	}
}
