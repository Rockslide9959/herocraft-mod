package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.frostgiant.item.FrostGear;

import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.1: the Casket of Ancient Winters in the off hand, or a full set of Rimed netherite, makes you immune to freezing
 * -- powder snow, the Frost Giant blizzard, a giant's freezing hits -- the same way leather armour does in vanilla
 * ({@code canFreeze} false).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFrostMixin {
	@Inject(method = "canFreeze", at = @At("HEAD"), cancellable = true)
	private void projecthero$frostImmune(CallbackInfoReturnable<Boolean> cir) {
		if (FrostGear.coldImmune((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
