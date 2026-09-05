package com.herocraft.mod.titan;

import com.herocraft.mod.mixin.RangedAttributeAccessor;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Widens vanilla's shared {@code Attributes.MAX_HEALTH} ceiling (1024, hard-clamped) so the Titan's
 * 1500 HP is a genuine health pool rather than the "dump the overflow into armour" workaround this
 * mod's Zombie Raid boss already uses (see project memory / {@code EmpoweredZombie.VANILLA_MAX_HEALTH}).
 * Applied once via {@link RangedAttributeAccessor} at mod init -- see that class's javadoc for why a
 * mixin accessor is the mechanism.
 */
public final class TitanHealthCap {
	/** Comfortably above the Titan's own 1500 HP, with headroom for any future boss. */
	public static final double NEW_MAX_HEALTH_CEILING = 2048.0;

	private TitanHealthCap() {
	}

	public static void initialize() {
		if (Attributes.MAX_HEALTH.value() instanceof RangedAttribute ranged) {
			((RangedAttributeAccessor) ranged).herocraft$setMaxValue(NEW_MAX_HEALTH_CEILING);
		}
	}
}
