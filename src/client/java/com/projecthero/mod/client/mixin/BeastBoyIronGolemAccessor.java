package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.animal.IronGolem;

/** v0.17.3 Beast Boy: the gorilla's arm slam on its (never ticked) stand-in iron golem's model. */
@Mixin(IronGolem.class)
public interface BeastBoyIronGolemAccessor {
	@Accessor("attackAnimationTick")
	void projecthero$setAttackAnimationTick(int v);
}
