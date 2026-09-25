package com.projecthero.mod.mixin;

import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the protected {@code jumping} flag (the rider's Space key, delivered by the vanilla player-input
 * packet) so a Titan can jump when its rider does -- see {@code TitanFormEntity#tickRidden}.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("jumping")
	boolean projecthero$isJumping();
}
