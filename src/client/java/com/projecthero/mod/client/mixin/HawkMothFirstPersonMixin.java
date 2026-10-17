package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.miraculous.hawkmoth.CaneRender;
import com.projecthero.mod.client.miraculous.hawkmoth.HawkMothPose;
import com.projecthero.mod.client.miraculous.hawkmoth.HawkMothSuitRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.16.6: Hawk Moth in first person -- the suit's sleeve over the bare arm vanilla just rendered (his own sleeve layer is
 * hidden by {@code PlayerModelMixin} while the suit shows) and the cane in the fist, so both ride every swing of the hand.
 */
@Mixin(PlayerRenderer.class)
public abstract class HawkMothFirstPersonMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$hawkMothArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player || player.isInvisible()) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float cover = HawkMothSuitRender.progress(player, partial);
		if (cover <= 0f) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		boolean right = arm == model.rightArm;
		HawkMothSuitRender.renderArm(pose, buffers, light, player, arm, right, cover);
		if (HawkMothPose.caneShown(player, partial)) {
			CaneRender.renderFirstPerson(pose, buffers, light, model, player, right, partial);
		}
	}
}
