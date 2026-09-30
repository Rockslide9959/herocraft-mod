package com.projecthero.mod.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * v0.14.4: reads a mob's protected goal selectors so a Symbiote can rewrite a taken-over animal's AI at
 * runtime -- an infested cow hunts players, a Symbiote cat fights for its owner (see
 * {@code com.projecthero.mod.symbiote.SymbioteMobGoals}).
 */
@Mixin(Mob.class)
public interface MobGoalsAccessor {
	@Accessor("goalSelector")
	GoalSelector projecthero$goalSelector();

	@Accessor("targetSelector")
	GoalSelector projecthero$targetSelector();
}
