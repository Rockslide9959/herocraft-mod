package com.projecthero.mod.mixin;

import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * v0.16.2: the melee goal's swing timer -- Spider-Sense reads it to warn of a blow about 0.6 s before it lands, and a
 * perfect dodge pushes it back so the attacker cannot swing again while dazed ({@code SpiderPerfectDodge}).
 */
@Mixin(MeleeAttackGoal.class)
public interface MeleeAttackGoalAccessor {
	@Accessor("ticksUntilNextAttack")
	int projecthero$ticksUntilNextAttack();

	@Accessor("ticksUntilNextAttack")
	void projecthero$setTicksUntilNextAttack(int ticks);
}
