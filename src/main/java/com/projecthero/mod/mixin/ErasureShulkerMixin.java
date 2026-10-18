package com.projecthero.mod.mixin;

import com.projecthero.mod.quirk.erasure.ErasureMobs;

import net.minecraft.world.entity.monster.Shulker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** v0.16.7 Erasure: an erased shulker cannot teleport away (its bullets are stopped by the spawn hook). */
@Mixin(Shulker.class)
public abstract class ErasureShulkerMixin {
	@Inject(method = "teleportSomewhere", at = @At("HEAD"), cancellable = true)
	private void projecthero$erasedNoTeleport(CallbackInfoReturnable<Boolean> cir) {
		if (ErasureMobs.specialsHeld((Shulker) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
