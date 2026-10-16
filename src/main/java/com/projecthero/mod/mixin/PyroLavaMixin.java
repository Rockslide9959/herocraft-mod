package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Pyrokinesis passives, on whichever side moves the player (the client for its own player):
 * <ul>
 * <li>lava doesn't slow a pyrokinetic -- {@code travel} skips its lava branch (the second {@code canStandOnFluid}
 * check) for them, so they walk and swim through lava at normal speed;</li>
 * <li>Flame Body walks ON lava: lava counts as a fluid they can stand on (the collision side of
 * {@code canStandOnFluid}), unless they hold Shift to sink in.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class PyroLavaMixin {
	@Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
	private void projecthero$pyroLavaWalk(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player p && fluid.is(FluidTags.LAVA) && !p.isShiftKeyDown()
				&& PyrokinesisHandlers.flameBodyActive(p)) {
			cir.setReturnValue(true);
		}
	}

	@Redirect(method = "travel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;canStandOnFluid(Lnet/minecraft/world/level/material/FluidState;)Z",
			ordinal = 1))
	private boolean projecthero$pyroLavaNoSlow(LivingEntity self, FluidState fluid, Vec3 travel) {
		if (self instanceof Player p && fluid.is(FluidTags.LAVA) && PyrokinesisHandlers.owns(p)) {
			return true;
		}
		return self.canStandOnFluid(fluid);
	}
}
