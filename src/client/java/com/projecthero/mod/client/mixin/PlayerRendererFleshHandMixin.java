package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.wolverine.WolverineFlesh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.12.17: the first-person hand follows the Wolverine flesh look. While the flesh is showing, vanilla's
 * {@code renderHand} is fed the flesh texture in place of the player's skin; while the skin is fading
 * back, the arm is drawn a second time with the real skin at rising alpha.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererFleshHandMixin {
	private static final ResourceLocation FLESH = ProjectHeroMod.id("textures/entity/wolverine_flesh.png");

	@Redirect(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSkin()Lnet/minecraft/client/resources/PlayerSkin;"))
	private PlayerSkin projecthero$fleshHandSkin(AbstractClientPlayer player) {
		PlayerSkin skin = player.getSkin();
		if (player == Minecraft.getInstance().player && WolverineFlesh.active(player, 1.0f)) {
			return new PlayerSkin(FLESH, skin.textureUrl(), skin.capeTexture(), skin.elytraTexture(), skin.model(), skin.secure());
		}
		return skin;
	}

	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$skinFadeOverHand(PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player) {
			return;
		}
		float alpha = WolverineFlesh.skinAlpha(player, 1.0f);
		if (alpha <= 0.01f || alpha >= 1.0f) {
			return;
		}
		var buffer = buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
		int argb = (Math.round(alpha * 255.0f) << 24) | 0xFFFFFF;
		arm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, argb);
		sleeve.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, argb);
	}
}
