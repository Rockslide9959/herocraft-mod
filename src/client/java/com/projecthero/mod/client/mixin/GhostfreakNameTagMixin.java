package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2 Ghostfreak: no name tag on him while faded or inside a host (whatever the team rules say), and none on anything inside
 * his Lights Out (the darkness swallows them).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class GhostfreakNameTagMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void projecthero$ghostfreakNoNameTag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (GhostfreakKit.unseen(entity) || GhostfreakKit.inLightsOut(entity)) {
			cir.setReturnValue(false);
		}
	}
}
