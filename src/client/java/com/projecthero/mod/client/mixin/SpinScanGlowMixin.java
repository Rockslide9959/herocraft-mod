package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.spin.SpinFx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3 the Spin, Z Scan: the outlines through walls. Hooked on {@code Minecraft#shouldEntityAppearGlowing}, which only this
 * client's renderer asks -- the outlines are the scanning Spin's alone (the payload goes to him only).
 */
@Mixin(Minecraft.class)
public abstract class SpinScanGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$spinScan(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (SpinFx.scanned(entity)) {
			cir.setReturnValue(true);
		}
	}
}
