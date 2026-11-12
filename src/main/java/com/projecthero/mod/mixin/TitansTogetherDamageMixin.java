package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.titans.TitansTogether;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** v0.17.3 Titans Together: +10% on every hit a Titan deals while a Titan squadmate is near (server side). */
@Mixin(LivingEntity.class)
public abstract class TitansTogetherDamageMixin {
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float projecthero$titansTogether(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			return amount;
		}
		return TitansTogether.scaleOutgoing(self, source, amount);
	}
}
