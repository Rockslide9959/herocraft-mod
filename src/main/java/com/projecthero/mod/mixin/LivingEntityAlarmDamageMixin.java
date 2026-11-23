package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.alarm.AlarmRoles;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.7 guard roles: the Shield-bearer's 70% frontal block and the Captain's +20% aura, applied to the incoming amount
 * at the top of {@code LivingEntity#hurt} (server side), so armour and resistances apply to the result like any hit.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAlarmDamageMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float projecthero$alarmRoles(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			return amount;
		}
		return AlarmRoles.modifyIncoming(self, source, amount);
	}
}
