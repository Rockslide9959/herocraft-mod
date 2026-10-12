package com.projecthero.mod.wolverine;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Deployed claws on an empty hand are tools. v0.12.14: they cut like a sword (cobwebs fall almost instantly and drop
 * their string; plants, leaves, bamboo ... 1.5x faster). v0.16.1: they also dig like an <b>iron shovel</b> and chop like
 * an <b>iron axe</b> -- the speed of each on its own blocks, and the iron tier for whether a block drops. Only mining
 * changes: the claws are not items, so right-click on a block does nothing a tool would (no dirt paths, no stripped
 * logs, no scraped copper) -- an empty hand's ordinary right-click is all there is.
 *
 * <p>The speed is applied as a multiplier on what vanilla computed for the bare hand (tool speed 1), so haste, the
 * underwater / airborne penalties and the {@code BLOCK_BREAK_SPEED} bonuses all still apply on top.
 *
 * <p>Kept out of the {@code Player} mixin for the same reason as {@code SymbioteBareHands}: the {@link Items}
 * references must resolve lazily, long after vanilla's item bootstrap.
 */
public final class WolverineBareHands {
	private static ItemStack[] tools;

	private WolverineBareHands() {
	}

	private static ItemStack[] tools() {
		if (tools == null) {
			tools = new ItemStack[] {new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.IRON_SHOVEL),
					new ItemStack(Items.IRON_AXE)};
		}
		return tools;
	}

	/** Claws out on an empty main hand, for a Wolverine. */
	public static boolean applies(Player player) {
		return Wolverine.hasPower(player) && Wolverine.clawsOut(player) && player.getMainHandItem().isEmpty();
	}

	/** The best tool speed the claws have against {@code state} (1 = no better than a fist). */
	public static float toolSpeed(BlockState state) {
		float best = 1.0f;
		for (ItemStack tool : tools()) {
			best = Math.max(best, tool.getDestroySpeed(state));
		}
		return best;
	}

	/** {@code current} is vanilla's bare-hand mining speed; the claws scale it by their tool speed. */
	public static float miningSpeed(BlockState state, float current) {
		return current * toolSpeed(state);
	}

	/** True if a sword, an iron shovel or an iron axe would get a drop from {@code state}. */
	public static boolean correctToolForDrops(BlockState state) {
		for (ItemStack tool : tools()) {
			if (tool.isCorrectToolForDrops(state)) {
				return true;
			}
		}
		return false;
	}
}
