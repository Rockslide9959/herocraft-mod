package com.projecthero.mod.hulk;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.13.14: the Hulk's hands are as good as stone tools -- a stone pickaxe / axe / shovel's mining speed, and they
 * harvest what those would (iron ore, lapis, copper...). He can't hold real tools, so this is how he mines.
 *
 * <p>Kept out of the {@code Player} mixin for the same reason as {@code SymbioteBareHands}: the {@link Items} references
 * must resolve lazily, long after vanilla's item bootstrap.
 */
public final class HulkBareHands {
	private static ItemStack pick;
	private static ItemStack axe;
	private static ItemStack shovel;

	private HulkBareHands() {
	}

	private static void ensure() {
		if (pick == null) {
			pick = new ItemStack(Items.STONE_PICKAXE);
			axe = new ItemStack(Items.STONE_AXE);
			shovel = new ItemStack(Items.STONE_SHOVEL);
		}
	}

	/** Whenever he is the Hulk (tools are refused anyway, so whatever is in hand does not matter). */
	public static boolean applies(Player player) {
		return Hulk.isHulk(player);
	}

	public static float miningSpeed(BlockState state, float current) {
		ensure();
		float best = current;
		best = Math.max(best, pick.getDestroySpeed(state));
		best = Math.max(best, axe.getDestroySpeed(state));
		best = Math.max(best, shovel.getDestroySpeed(state));
		return best;
	}

	public static boolean correctToolForDrops(BlockState state) {
		ensure();
		return pick.isCorrectToolForDrops(state) || axe.isCorrectToolForDrops(state) || shovel.isCorrectToolForDrops(state);
	}
}
