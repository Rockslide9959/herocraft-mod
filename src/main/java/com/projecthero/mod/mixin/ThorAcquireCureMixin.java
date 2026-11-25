package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.thoracquire.Valor;

import net.minecraft.advancements.critereon.CuredZombieVillagerTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;

/** v0.17.7 Valor: curing a zombie villager (the vanilla advancement trigger names the player who started the cure). */
@Mixin(CuredZombieVillagerTrigger.class)
public abstract class ThorAcquireCureMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void projecthero$valorCure(ServerPlayer player, Zombie zombie, Villager villager, CallbackInfo ci) {
		if (player != null) {
			Valor.villagerCured(player);
		}
	}
}
