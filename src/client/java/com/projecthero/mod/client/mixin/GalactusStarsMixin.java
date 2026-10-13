package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.galactus.GalactusSkyClient;

import net.minecraft.client.multiplayer.ClientLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** v0.16.1: Galactus' sky -- the stars stay out in daylight inside his arena ({@link GalactusSkyClient#stars()}). */
@Mixin(ClientLevel.class)
public class GalactusStarsMixin {
	@Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
	private void projecthero$galactusStars(float partialTick, CallbackInfoReturnable<Float> cir) {
		float s = GalactusSkyClient.stars();
		if (s > cir.getReturnValueF()) {
			cir.setReturnValue(s);
		}
	}
}
