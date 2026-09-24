package com.projecthero.mod.wolverine;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.12.14: deployed adamantium claws on an empty hand break blocks the way a sword does -- cobwebs
 * fall almost instantly (and drop their string), and the "sword efficient" blocks (plants, leaves,
 * bamboo, ...) break 1.5x faster -- for both mining speed and whether the block drops.
 *
 * <p>Kept out of the {@code Player} mixin for the same reason as {@code SymbioteBareHands}: the
 * {@link Items} references must resolve lazily, long after vanilla's item bootstrap.
 */
public final class WolverineBareHands {
	private static ItemStack sword;

	private WolverineBareHands() {
	}

	private static ItemStack sword() {
		if (sword == null) {
			sword = new ItemStack(Items.NETHERITE_SWORD);
		}
		return sword;
	}

	/** Claws out on an empty main hand, for a Wolverine. */
	public static boolean applies(Player player) {
		return Wolverine.hasPower(player) && Wolverine.clawsOut(player) && player.getMainHandItem().isEmpty();
	}

	/** What a sword would mine {@code state} at, if better than {@code current}. */
	public static float miningSpeed(BlockState state, float current) {
		return Math.max(current, sword().getDestroySpeed(state));
	}

	public static boolean correctToolForDrops(BlockState state) {
		return sword().isCorrectToolForDrops(state);
	}
}
