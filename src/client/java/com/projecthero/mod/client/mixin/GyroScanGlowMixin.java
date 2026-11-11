package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.gyro.GyroFx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3 Gyro Zeppeli, Z Scan: the outlines through walls. Hooked on {@code Minecraft#shouldEntityAppearGlowing}, which only this
 * client's renderer asks -- the outlines are the scanning Gyro's alone (the payload goes to him only).
 */
@Mixin(Minecraft.class)
public abstract class GyroScanGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$gyroScan(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (GyroFx.scanned(entity)) {
			cir.setReturnValue(true);
		}
	}
}
