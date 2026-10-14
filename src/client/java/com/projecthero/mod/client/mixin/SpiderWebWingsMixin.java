package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;

/**
 * v0.16.2: the Suit v2's Web Wings glide -- run at the top of the local player's {@code aiStep}, before vanilla moves him
 * (the same spot as the swing physics), so the glide velocity is what he moves by this tick.
 */
@Mixin(LocalPlayer.class)
public abstract class SpiderWebWingsMixin {
	@Inject(method = "aiStep", at = @At("HEAD"))
	private void projecthero$webWings(CallbackInfo ci) {
		com.projecthero.mod.client.spider.SpiderWebWingsClient.tick((LocalPlayer) (Object) this);
	}
}
