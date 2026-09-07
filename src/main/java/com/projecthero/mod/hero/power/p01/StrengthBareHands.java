package com.projecthero.mod.hero.power.p01;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Super Strength: the mutant's hands break and harvest blocks as though holding stone tools --
 * for both mining speed and whether the block actually drops its items. This applies whatever they
 * are carrying: a stone-tier floor is added on top of whatever the held item can do (a better tool
 * still wins; a block, torch or sword in hand no longer stops you punching through stone).
 *
 * <p>Kept out of the {@code Player} mixin itself for the same reason as
 * {@link com.projecthero.mod.symbiote.SymbioteBareHands}: the {@link Items} references must resolve
 * from a class that initialises lazily, well after vanilla's item bootstrap.
 */
public final class StrengthBareHands {
	private static final String KEY = "power_01_super_strength";
	private static ItemStack pick;
	private static ItemStack axe;
	private static ItemStack shovel;

	private StrengthBareHands() {
	}

	private static void ensure() {
		if (pick == null) {
			pick = new ItemStack(Items.STONE_PICKAXE);
			axe = new ItemStack(Items.STONE_AXE);
			shovel = new ItemStack(Items.STONE_SHOVEL);
		}
	}

	/** Whether the stone-hand rule applies to {@code player} right now -- simply: they own the power. */
	public static boolean applies(Player player) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
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
		return pick.isCorrectToolForDrops(state)
				|| axe.isCorrectToolForDrops(state)
				|| shovel.isCorrectToolForDrops(state);
	}
}
