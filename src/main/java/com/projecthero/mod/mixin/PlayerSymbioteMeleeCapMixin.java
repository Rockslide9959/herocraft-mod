package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.projecthero.mod.symbiote.SymbiotePassives;

import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7 City tier: the Symbiote is a Secondary stacked on a Primary, so its melee bonuses (the suit's +10, a weapon's, the Monster's
 * claws) never lift a real melee swing past 30 ({@link SymbiotePassives#capMelee}). Only the swing's own hit (the {@code target.hurt}
 * in {@code Player#attack}); ability hits never pass here.
 */
@Mixin(Player.class)
public abstract class PlayerSymbioteMeleeCapMixin {
	@ModifyArg(method = "attack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
	private float projecthero$symbioteMeleeCap(float amount) {
		return SymbiotePassives.capMelee((Player) (Object) this, amount);
	}
}
