package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.greenlantern.GreenLanternHandRenderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.13.21, first person: the Green Lantern's Power Ring (and a switched-on Energy Blade / Mining Drill) on the right
 * hand the player sees. Injected at the TAIL of {@code renderHand} like {@link PlayerRendererClawsMixin}, so it rides
 * every swing / bob animation of the arm vanilla just drew; other viewers see it through {@code PowerRingLayer}.
 */
@Mixin(PlayerRenderer.class)
public abstract class GreenLanternRingHandMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$greenLanternRing(PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player || !GreenLanternHandRenderer.hasAnything(player)) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		if (arm != model.rightArm) {
			return;
		}
		GreenLanternHandRenderer.render(pose, buffers, light, player, arm,
				player.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
	}
}
