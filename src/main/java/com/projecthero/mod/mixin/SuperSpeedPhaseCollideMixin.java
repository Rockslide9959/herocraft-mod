package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Super Speed Shift+C Phase (v0.14.5): a phasing speedster's horizontal movement ignores blocks, but vertical
 * collision is kept -- you walk through walls on your own level, never down through floors or up through ceilings.
 * The vertical part is collided on its own (no horizontal component, so no step-up either) and the un-collided
 * X / Z are kept. Runs wherever the player's movement is simulated -- the owner's client (the server has
 * {@code noPhysics} on while phasing and accepts the client's positions).
 */
@Mixin(Entity.class)
public abstract class SuperSpeedPhaseCollideMixin {
	@Shadow
	private Vec3 collide(Vec3 movement) {
		throw new AssertionError();
	}

	@Inject(method = "collide", at = @At("HEAD"), cancellable = true)
	private void projecthero$speedPhase(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
		if ((movement.x != 0.0 || movement.z != 0.0) && (Object) this instanceof Player player
				&& SuperSpeedHandlers.phasing(player)) {
			Vec3 vertical = this.collide(new Vec3(0.0, movement.y, 0.0));
			cir.setReturnValue(new Vec3(movement.x, vertical.y, movement.z));
		}
	}
}
