package com.projecthero.mod.armor;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.21: armour Night Vision is automatic -- the Iron Man helmet optic and the Stark Glasses only switch it on while
 * the wearer actually needs it: somewhere dark (light {@value #ON_AT_OR_BELOW} or less at the eyes, i.e. night, caves,
 * anywhere mobs can spawn) or with their head under water. It goes off again once it is properly lit
 * ({@value #OFF_AT_OR_ABOVE}+), and the gap between the two keeps it from flickering at a torch's edge.
 *
 * <p>Stateless: "is it on right now" is read off the wearer's own optic effect, which the caller passes in.
 */
public final class ArmorNightVision {
	public static final int ON_AT_OR_BELOW = 7;
	public static final int OFF_AT_OR_ABOVE = 10;

	/** Gametest hook only, set and cleared inside one synchronous test body: forces "dark" (true) / "lit" (false). */
	public static Boolean testOverride;

	private ArmorNightVision() {
	}

	/** Should an armour optic be giving Night Vision this tick? {@code opticOn} = it is on at the moment. */
	public static boolean wanted(Player player, boolean opticOn) {
		if (testOverride != null) {
			return testOverride;
		}
		if (player.isEyeInFluid(FluidTags.WATER)) {
			return true;
		}
		int light = player.level().getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition()));
		return opticOn ? light < OFF_AT_OR_ABOVE : light <= ON_AT_OR_BELOW;
	}
}
