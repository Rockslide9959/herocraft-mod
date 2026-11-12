package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.vision.Vision;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Vision's Intangibility (Shift+V): he walks through walls sideways while vertical collision is kept -- floors and ceilings
 * stay solid, so he never sinks out of the world. The Ghostfreak / Silver Surfer approach ({@code OmnitrixAlienCollideMixin}); runs on
 * both sides (the owner's client simulates, the server replays). {@code VisionPhaseMoveCheckMixin} waves the server's "moved wrongly"
 * check through, {@code Vision} refuses to re-solidify inside a block and pushes him out if the bar empties in one.
 */
@Mixin(Entity.class)
public abstract class VisionPhaseCollideMixin {
	@Shadow
	private Vec3 collide(Vec3 movement) {
		throw new AssertionError();
	}

	@Inject(method = "collide", at = @At("HEAD"), cancellable = true)
	private void projecthero$visionPhase(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
		if ((Object) this instanceof Player player && (movement.x != 0.0 || movement.z != 0.0) && Vision.wallPhasing(player)) {
			Vec3 vertical = this.collide(new Vec3(0.0, movement.y, 0.0));
			cir.setReturnValue(new Vec3(movement.x, vertical.y, movement.z));
		}
	}
}
