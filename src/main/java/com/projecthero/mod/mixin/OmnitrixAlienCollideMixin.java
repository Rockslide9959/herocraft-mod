package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Omnitrix framework ({@link AlienKit#phasing}): an intangible alien (Ghostfreak's Phase) moves horizontally through
 * blocks while vertical collision is kept -- through walls, never down through floors. The Silver Surfer's Molecular Phase
 * approach ({@code SilverSurferPhaseCollideMixin}); runs on both sides (the owner's client simulates, the server replays).
 */
@Mixin(Entity.class)
public abstract class OmnitrixAlienCollideMixin {
	@Shadow
	private Vec3 collide(Vec3 movement) {
		throw new AssertionError();
	}

	@Inject(method = "collide", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienPhase(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
		if ((movement.x != 0.0 || movement.z != 0.0) && (Object) this instanceof Player player) {
			AlienKit kit = AlienKit.activeKit(player);
			if (kit != null && kit.phasing(player)) {
				Vec3 vertical = this.collide(new Vec3(0.0, movement.y, 0.0));
				cir.setReturnValue(new Vec3(movement.x, vertical.y, movement.z));
			}
		}
	}
}
