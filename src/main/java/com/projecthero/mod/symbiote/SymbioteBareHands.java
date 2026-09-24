package com.projecthero.mod.symbiote;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A bonded Symbiote host's empty hand works like a wooden tool: the alien hardens the host's claws to
 * match a wooden pickaxe / axe / shovel, for both mining speed and whether the block actually drops.
 *
 * <p>Kept out of the {@code Player} mixin itself because the {@link Items} references would otherwise
 * be resolved from a class merged into {@code Player} -- this ordinary class initialises lazily, long
 * after vanilla's item bootstrap.
 */
public final class SymbioteBareHands {
	private static ItemStack pick;
	private static ItemStack axe;
	private static ItemStack shovel;

	private SymbioteBareHands() {
	}

	private static void ensure() {
		if (pick == null) {
			pick = new ItemStack(Items.WOODEN_PICKAXE);
			axe = new ItemStack(Items.WOODEN_AXE);
			shovel = new ItemStack(Items.WOODEN_SHOVEL);
		}
	}

	/** Whether the wooden-hand rule applies to {@code player} this instant (bonded, main hand empty). */
	public static boolean applies(Player player) {
		return Symbiote.isNormalHost(player) && player.getMainHandItem().isEmpty();
	}

	/** The best mining speed a wooden pickaxe / axe / shovel would get on {@code state}. */
	public static float miningSpeed(BlockState state, float current) {
		ensure();
		float best = current;
		best = Math.max(best, pick.getDestroySpeed(state));
		best = Math.max(best, axe.getDestroySpeed(state));
		best = Math.max(best, shovel.getDestroySpeed(state));
		return best;
	}

	/** Whether a wooden pickaxe / axe / shovel would let {@code state} drop its items. */
	public static boolean correctToolForDrops(BlockState state) {
		ensure();
		return pick.isCorrectToolForDrops(state)
				|| axe.isCorrectToolForDrops(state)
				|| shovel.isCorrectToolForDrops(state);
	}
}
