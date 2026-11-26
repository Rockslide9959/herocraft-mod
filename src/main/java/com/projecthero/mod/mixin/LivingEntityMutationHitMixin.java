package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.hero.power.MutationDamage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.7 City tier: a melee swing's mutation attack-damage bonuses (stacked passives, Maximum Effort, Overdrive ...)
 * never lift the hit past the mutation cap ({@link MutationDamage#onHurt}). Server side only; only acts inside a swing
 * bracketed by {@code PlayerAttackComboMixin}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMutationHitMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float projecthero$mutationMeleeCap(float amount, DamageSource source) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return amount;
		}
		return MutationDamage.onHurt(source, amount);
	}
}
