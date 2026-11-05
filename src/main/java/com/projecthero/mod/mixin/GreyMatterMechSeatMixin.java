package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterMechEntity;

import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2 round 3: Sneak does not climb out of Grey Matter's Block Mech -- Shift is the Shift+key modifier there (Shift+R = the
 * Ground Slam); Shift+Z is the way out.
 */
@Mixin(Player.class)
public abstract class GreyMatterMechSeatMixin {
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void projecthero$mechStaysSeated(CallbackInfoReturnable<Boolean> cir) {
		if (GreyMatterMechEntity.mechOf((Player) (Object) this) != null) {
			cir.setReturnValue(false);
		}
	}
}
