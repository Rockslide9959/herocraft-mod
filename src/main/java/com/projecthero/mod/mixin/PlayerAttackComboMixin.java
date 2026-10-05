package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.power.WeaponCombo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.20: brackets every melee attack so {@link WeaponCombo} can read the attack strength before vanilla resets it
 * (HEAD) and resolve the Mjolnir / Stormbreaker combo step once the hit has landed (RETURN). Server side only.
 */
@Mixin(Player.class)
public abstract class PlayerAttackComboMixin {
	@Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
	private void projecthero$comboBefore(Entity target, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			WeaponCombo.beforeAttack(player, target);
			com.projecthero.mod.ironman.IronManCombo.beforeMelee(player, target); // v0.14.29 agent F
		}
	}

	@Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
	private void projecthero$comboAfter(Entity target, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			WeaponCombo.afterAttack(player);
			com.projecthero.mod.ironman.IronManCombo.afterMelee(player); // v0.14.29 agent F
		}
	}
}
