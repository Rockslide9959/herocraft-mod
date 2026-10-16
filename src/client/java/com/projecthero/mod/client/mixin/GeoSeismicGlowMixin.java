package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.mutation.geo.GeokinesisClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.5 Geokinesis: Seismic Sense's outlines. Hooked on {@code Minecraft#shouldEntityAppearGlowing}, which only this
 * client's renderer asks -- the outlines are the sensing geokinetic's alone (the v0.15.4 mob-highlight rule: never the
 * server glowing flag).
 */
@Mixin(Minecraft.class)
public abstract class GeoSeismicGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$geoSeismicSense(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (GeokinesisClient.sensed(entity)) {
			cir.setReturnValue(true);
		}
	}
}
