package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.punisher.ability.PunisherMark;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.15.18: Target Designation -- an entity the Punisher marked takes +30% from <em>every</em> hit he deals it (guns,
 * melee, grenades, abilities). Scales the incoming amount at the top of {@code LivingEntity#hurt}, server side only, so
 * armour, resistances and the rest still apply to the raised number exactly as they would to any hit.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPunisherMarkMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float projecthero$punisherMarkBonus(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			return amount;
		}
		return PunisherMark.scale(self, source, amount);
	}
}
