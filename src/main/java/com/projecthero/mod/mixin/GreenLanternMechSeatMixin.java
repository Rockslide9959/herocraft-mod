package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;

import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: Sneak does not climb out of the Hard-Light Mech -- Shift is the Lantern's Shift+key modifier there (Shift+R =
 * Rocket Fist); N is the way out.
 */
@Mixin(Player.class)
public abstract class GreenLanternMechSeatMixin {
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void projecthero$mechStaysSeated(CallbackInfoReturnable<Boolean> cir) {
		if (HardLightMechEntity.isPilot((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
