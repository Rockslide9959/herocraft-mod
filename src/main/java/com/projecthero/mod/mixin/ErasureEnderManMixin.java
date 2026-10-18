package com.projecthero.mod.mixin;

import com.projecthero.mod.quirk.erasure.ErasureMobs;

import net.minecraft.world.entity.monster.EnderMan;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** v0.16.7 Erasure: an erased enderman cannot teleport (every teleport -- random, toward, dodging -- ends here). */
@Mixin(EnderMan.class)
public abstract class ErasureEnderManMixin {
	@Inject(method = "teleport(DDD)Z", at = @At("HEAD"), cancellable = true)
	private void projecthero$erasedNoTeleport(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (ErasureMobs.specialsHeld((EnderMan) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
