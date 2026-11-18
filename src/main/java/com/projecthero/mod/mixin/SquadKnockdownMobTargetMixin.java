package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.squad.SquadKnockdown;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * v0.17.5 squad knockdown: no mob can be pointed at a knocked player, whoever does the pointing (a target goal, a revenge
 * goal, a boss's own code). The target becomes "none" and the mob picks someone else on its next look round.
 */
@Mixin(Mob.class)
public abstract class SquadKnockdownMobTargetMixin {
	@ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
	private LivingEntity projecthero$neverTargetKnocked(LivingEntity target) {
		return target != null && SquadKnockdown.isKnocked(target) ? null : target;
	}
}
