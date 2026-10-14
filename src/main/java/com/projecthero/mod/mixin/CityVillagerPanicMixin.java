package com.projecthero.mod.mixin;

import com.projecthero.mod.city.CityCops;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.16.2 City part B: a City Cop doesn't panic at a nearby monster or a wound while it has more than a quarter of its
 * health left -- it fights instead ({@code CityCops}). Below that it flees like any villager.
 */
@Mixin(VillagerPanicTrigger.class)
public abstract class CityVillagerPanicMixin {
	@Inject(method = "hasHostile", at = @At("HEAD"), cancellable = true)
	private static void projecthero$copHostile(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (CityCops.bravesIt(entity)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isHurt", at = @At("HEAD"), cancellable = true)
	private static void projecthero$copHurt(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (CityCops.bravesIt(entity)) {
			cir.setReturnValue(false);
		}
	}
}
