package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2 (user: "make sure he also has an invisibility move"): a faded Ghostfreak (or one inside a host) leaves NOTHING to see
 * -- vanilla invisibility still draws what an invisible player holds, wears on the head, the elytra and stuck arrows. (His
 * armour is already hidden for every alien: {@code OmnitrixHideGearMixin}; the name tag: {@code GhostfreakNameTagMixin}.)
 */
@Mixin({ ItemInHandLayer.class, CustomHeadLayer.class, ElytraLayer.class, StuckInBodyLayer.class })
public abstract class GhostfreakFadeLayersMixin {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$ghostfreakFadedNothingShows(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float a, float b, float c,
			float d, float e, float f, CallbackInfo ci) {
		if (GhostfreakKit.unseen(entity)) {
			ci.cancel();
		}
	}
}
