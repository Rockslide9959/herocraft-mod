package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.gyro.GyroFx;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3 Gyro Zeppeli, Z Scan: scanned creatures outline gold. Only answered for client-side entities
 * ({@code IronManHighlight.mayDecide}), so the integrated server never sees it.
 */
@Mixin(Entity.class)
public abstract class GyroScanColorMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void projecthero$gyroScanColour(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LocalPlayer || !com.projecthero.mod.ironman.IronManHighlight.mayDecide(self)) {
			return;
		}
		if (GyroFx.scanned(self)) {
			cir.setReturnValue(GyroFx.GOLD);
		}
	}
}
