package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.silversurfer.SilverSurfer;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Silver Surfer Shift+Z Molecular Phase: like Super Speed's Phase, horizontal movement ignores blocks while
 * vertical collision is kept -- he walks through walls on his own level, never down through floors. Runs on both sides
 * (the owner's client simulates, the server replays the same move and so accepts it).
 */
@Mixin(Entity.class)
public abstract class SilverSurferPhaseCollideMixin {
	@Shadow
	private Vec3 collide(Vec3 movement) {
		throw new AssertionError();
	}

	@Inject(method = "collide", at = @At("HEAD"), cancellable = true)
	private void projecthero$surferPhase(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
		if ((movement.x != 0.0 || movement.z != 0.0) && (Object) this instanceof Player player && SilverSurfer.phasing(player)) {
			Vec3 vertical = this.collide(new Vec3(0.0, movement.y, 0.0));
			cir.setReturnValue(new Vec3(movement.x, vertical.y, movement.z));
		}
	}
}
