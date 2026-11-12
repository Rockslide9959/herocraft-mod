package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.vision.Vision;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * v0.17.3 Vision: arrows, fireballs and every projectile that asks {@code canHitEntity} fly straight through him while he's phased
 * (Intangibility, a Phase Dash) instead of stopping on him; anything that still reaches him does no damage ({@code VisionDamage}).
 * An intangible Vision's own projectiles can't hit anything either.
 */
@Mixin(Projectile.class)
public abstract class VisionProjectilePassMixin {
	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void projecthero$visionPhasedPass(Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (target instanceof Player player && Vision.phased(player)) {
			cir.setReturnValue(false);
			return;
		}
		if (((Projectile) (Object) this).getOwner() instanceof Player owner && Vision.intangible(owner)) {
			cir.setReturnValue(false);
		}
	}
}
