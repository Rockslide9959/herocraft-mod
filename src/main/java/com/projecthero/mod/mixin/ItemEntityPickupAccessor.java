package com.projecthero.mod.mixin;

import net.minecraft.world.entity.item.ItemEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * v0.14.8: reads an item's pickup delay, so Super Speed's Time Slow can count it down on the caster's own ticks
 * (see {@code SuperSpeedTimeSlow#tickLootPickupDelays}).
 */
@Mixin(ItemEntity.class)
public interface ItemEntityPickupAccessor {
	@Accessor("pickupDelay")
	int projecthero$getPickupDelay();
}
