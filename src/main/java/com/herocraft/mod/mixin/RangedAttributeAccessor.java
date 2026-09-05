package com.herocraft.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Lets the mod widen a {@link RangedAttribute}'s otherwise-{@code private final} upper bound.
 *
 * <p>Why this is needed: vanilla registers {@code Attributes.MAX_HEALTH} with a hard ceiling of
 * 1024 ({@code new RangedAttribute("attribute.name.max_health", 20.0, 1.0, 1024.0)}), and
 * {@link RangedAttribute#sanitizeValue} silently clamps anything set above that -- this project's
 * Zombie Raid boss already hit it once and worked around it by dumping the "overflow" into armour
 * stats instead of a real HP pool (see project memory). The Titan needs a genuine 1500 HP bar, so
 * {@code com.herocraft.mod.titan.TitanHealthCap} uses this accessor to raise
 * {@code Attributes.MAX_HEALTH}'s own {@code maxValue} field once at mod init, exactly the same
 * {@code @Mutable @Accessor} technique {@link BossEventAccessor} already uses on a different
 * vanilla {@code private final} field.
 *
 * <p>This changes the ceiling for every entity in the mod (players included), not just the Titan --
 * confirmed acceptable since it only raises what health values CAN reach, it does not grant anyone
 * extra health on its own.
 */
@Mixin(RangedAttribute.class)
public interface RangedAttributeAccessor {
	@Mutable
	@Accessor("maxValue")
	void herocraft$setMaxValue(double maxValue);
}
