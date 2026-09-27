package com.projecthero.mod.titan;

import com.projecthero.mod.mixin.RangedAttributeAccessor;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Widens vanilla's shared {@code Attributes.MAX_HEALTH} ceiling (1024, hard-clamped) so the Titan's
 * 1500 HP is a genuine health pool rather than the "dump the overflow into armour" workaround this
 * mod's Zombie Raid boss already uses (see project memory / {@code EmpoweredZombie.VANILLA_MAX_HEALTH}).
 * Applied once via {@link RangedAttributeAccessor} at mod init -- see that class's javadoc for why a
 * mixin accessor is the mechanism.
 *
 * <p>v0.13.1: also covers The Abyssal Behemoth's 3,000 HP -- {@code Attributes.MAX_HEALTH} is one
 * shared vanilla attribute, so raising the ceiling here is enough for every boss in the mod; there is
 * no need for a second, near-identical cap class.
 */
public final class TitanHealthCap {
	/** Comfortably above the Titan's 1500 HP and the Abyssal Behemoth's 3,000, with headroom to spare. */
	public static final double NEW_MAX_HEALTH_CEILING = 40000.0;

	private TitanHealthCap() {
	}

	public static void initialize() {
		if (Attributes.MAX_HEALTH.value() instanceof RangedAttribute ranged) {
			((RangedAttributeAccessor) ranged).projecthero$setMaxValue(NEW_MAX_HEALTH_CEILING);
		}
	}
}
