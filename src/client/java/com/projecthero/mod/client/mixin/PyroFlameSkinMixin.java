package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.mutation.pyro.PyroFireSkin;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.5 Pyrokinesis Flame Body: the player is drawn in the user's flame skin ({@link PyroFireSkin#SKIN}) -- the body
 * through {@code getTextureLocation}, and the first-person arm redrawn in it (with the live fire layer over it) after
 * vanilla's own arm, since {@code renderHand} reads the real skin directly.
 */
@Mixin(PlayerRenderer.class)
public abstract class PyroFlameSkinMixin {
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
			at = @At("RETURN"), cancellable = true)
	private void projecthero$pyroFlameSkin(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
		if (PyroFireSkin.wearing(player)) {
			cir.setReturnValue(PyroFireSkin.SKIN);
		}
	}

	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$pyroFlameArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (!PyroFireSkin.wearing(player) || player.isInvisible()) {
			return;
		}
		arm.render(pose, buffers.getBuffer(RenderType.entitySolid(PyroFireSkin.SKIN)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(PyroFireSkin.SKIN)), light, OverlayTexture.NO_OVERLAY);
		if (PyroFireSkin.ready()) {
			arm.render(pose, buffers.getBuffer(RenderType.eyes(PyroFireSkin.FIRE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		}
	}
}
