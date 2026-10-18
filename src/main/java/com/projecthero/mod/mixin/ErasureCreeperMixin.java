package com.projecthero.mod.mixin;

import com.projecthero.mod.quirk.erasure.ErasureMobs;

import net.minecraft.world.entity.monster.Creeper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** v0.16.7 Erasure: an erased creeper does not swell, and never explodes (not even one lit with flint and steel). */
@Mixin(Creeper.class)
public abstract class ErasureCreeperMixin {
	@Shadow
	private int swell;

	@Shadow
	public abstract void setSwellDir(int dir);

	@Inject(method = "tick", at = @At("HEAD"))
	private void projecthero$erasedNoSwell(CallbackInfo ci) {
		if (ErasureMobs.specialsHeld((Creeper) (Object) this)) {
			setSwellDir(-1);
		}
	}

	@Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
	private void projecthero$erasedNoBoom(CallbackInfo ci) {
		if (ErasureMobs.specialsHeld((Creeper) (Object) this)) {
			swell = 0;
			ci.cancel();
		}
	}
}
