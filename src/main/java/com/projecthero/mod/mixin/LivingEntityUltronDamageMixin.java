package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.ultron.UltronCombat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.1: the harder Ultron Uprising -- every hit an Ultron robot deals (melee, bolts, beams, missiles, the Fist
 * Rocket) is multiplied by {@code damage.globalMultiplier} (x1.3). Scales the incoming amount at the top of
 * {@code LivingEntity#hurt}, server side only, so armour and resistances apply to the raised number like any hit.
 * One flat multiplier: there is no per-damage-type scaling and no adaptation.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityUltronDamageMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float projecthero$ultronGlobalDamage(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			return amount;
		}
		return UltronCombat.scaleOutgoing(source, amount);
	}
}
