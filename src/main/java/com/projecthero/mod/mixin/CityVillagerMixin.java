package com.projecthero.mod.mixin;

import com.projecthero.mod.city.CityVillagers;

import net.minecraft.world.entity.npc.Villager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** v0.16.2 City part B: City Cops, Doctors, Firefighters and the newspaper seller tick after the villager's brain. */
@Mixin(Villager.class)
public abstract class CityVillagerMixin {
	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void projecthero$cityJobs(CallbackInfo ci) {
		CityVillagers.tick((Villager) (Object) this);
	}
}
