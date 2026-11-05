package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltStow;

import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2 round 3 (user: "dont combine hitboxes"): a squad mate inside Cannonbolt's ball has no hitbox of their own: nothing can aim
 * at them, punch them or shoot them in there, and the ball stays the only thing to hit.
 */
@Mixin(LivingEntity.class)
public abstract class CannonboltStowMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void projecthero$noHitboxInside(CallbackInfoReturnable<Boolean> cir) {
		if (CannonboltStow.stowed((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void projecthero$noPushInside(CallbackInfoReturnable<Boolean> cir) {
		if (CannonboltStow.stowed((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
