package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.cyborg.CyborgSkins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.5: Cyborg in first person. v0.17.7 round 2 (user: Cyborg at the player's normal size): a Cyborg player is drawn with
 * his Cyborg skin ({@link CyborgSkins}: his own skin with Cyborg's parts on it) in place of his own -- the body in third person
 * ({@code getTextureLocation}) and the arm and sleeve in first person ({@code renderHand}), both by vanilla on the normal model.
 */
@Mixin(PlayerRenderer.class)
public abstract class CyborgFirstPersonArmMixin {
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$cyborgSkin(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
		ResourceLocation skin = CyborgSkins.texture(player);
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}

	@ModifyExpressionValue(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/PlayerSkin;texture()Lnet/minecraft/resources/ResourceLocation;"))
	private ResourceLocation projecthero$cyborgArmSkin(ResourceLocation original, @Local(argsOnly = true) AbstractClientPlayer player) {
		ResourceLocation skin = CyborgSkins.texture(player);
		return skin != null ? skin : original;
	}

	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$cyborgArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player || player.isInvisible()) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
		// v0.16.5 (user): the blaster building round the forearm for the Sonic Cannon / Sonic Burst, and the Arm Saw
		if (com.projecthero.mod.cyborg.Cyborg.hasPower(player)) {
			com.projecthero.mod.client.cyborg.CyborgTech.renderArmTechFirstPerson(pose, buffers, light, player, arm, arm == model.rightArm,
					player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM);
		}
	}
}
