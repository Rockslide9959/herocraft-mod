package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.hulk.GladiatorFirstPerson;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.15.5, first person: Gladiator Hulk's hammer (right fist) and axe (left fist), drawn at the TAIL of
 * {@code renderHand} -- after vanilla drew the arm, so the weapon follows every swing / bob. Only the local player;
 * everyone else sees the weapons on the GeckoLib Hulk ({@code HulkGladiatorLayer}).
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererGladiatorWeaponMixin {
	@Inject(method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
			at = @At("TAIL"))
	private void projecthero$gladiatorWeapon(PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
		if (player != Minecraft.getInstance().player) {
			return;
		}
		GladiatorFirstPerson.renderWeapon(pose, buffers, light, player, arm,
				arm == ((PlayerRenderer) (Object) this).getModel().rightArm);
	}
}
