package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.alienx.AlienXKit;

import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2 round 4 (user: "change the alien x council thing to be the mind palace thing that the hulk has"): the player in Alien X's
 * hall stands inside his frozen body, and only the body is there to the world: he has no hitbox, cannot be pushed, and mobs lose
 * him (the targeting rules ask {@code canBeSeenAsEnemy}, and a mob already hunting him drops him at its next check).
 */
@Mixin(LivingEntity.class)
public abstract class AlienXHallHiddenMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void projecthero$noHitboxInHall(CallbackInfoReturnable<Boolean> cir) {
		if (AlienXKit.inHall((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void projecthero$noPushInHall(CallbackInfoReturnable<Boolean> cir) {
		if (AlienXKit.inHall((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
	private void projecthero$noEnemyInHall(CallbackInfoReturnable<Boolean> cir) {
		if (AlienXKit.inHall((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
