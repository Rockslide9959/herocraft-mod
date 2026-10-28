package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;

/**
 * v0.17.1 Omnitrix framework: an alien kit's movement hooks on whichever side moves the player --
 * {@link AlienKit#standsOnFluid} (water running: the collision side of {@code canStandOnFluid}, as striders walk on lava) and
 * {@link AlienKit#climbs} (wall running: "on a ladder" while pressed into a wall).
 */
@Mixin(LivingEntity.class)
public abstract class OmnitrixAlienLivingMixin {
	@Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienFluidRun(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player p && !fluid.isEmpty()) {
			AlienKit kit = AlienKit.activeKit(p);
			if (kit != null && kit.standsOnFluid(p, fluid)) {
				cir.setReturnValue(true);
			}
		}
	}

	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienWallRun(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player p && !p.isSpectator()) {
			AlienKit kit = AlienKit.activeKit(p);
			if (kit != null && kit.climbs(p)) {
				cir.setReturnValue(true);
			}
		}
	}
}
