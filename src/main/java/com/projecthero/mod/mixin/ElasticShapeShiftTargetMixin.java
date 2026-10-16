package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.hero.power.p17.ElasticityHandlers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * v0.16.5 Elasticity rework: a Shape-Shifted hero is just a barrel (or a chest, or a block) to a mob -- it cannot target
 * one, at the one choke point every target goal, retaliation and command goes through.
 */
@Mixin(Mob.class)
public abstract class ElasticShapeShiftTargetMixin {
	@ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
	private LivingEntity projecthero$cannotTargetAShapeShift(LivingEntity target) {
		return target != null && ElasticityHandlers.hiddenFromMobs(target) ? null : target;
	}
}
