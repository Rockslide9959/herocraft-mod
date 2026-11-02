package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.projectile.AbstractArrow;

/** v0.17.2 Upgrade revamp (Overclock's weapon boost): an overclocked bow's arrows pierce one target ({@code setPierceLevel} is private). */
@Mixin(AbstractArrow.class)
public interface OmnitrixUpgradeArrowAccessor {
	@Invoker("setPierceLevel")
	void projecthero$setPierceLevel(byte level);
}
