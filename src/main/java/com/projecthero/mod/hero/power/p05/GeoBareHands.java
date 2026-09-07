package com.projecthero.mod.hero.power.p05;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Geokinesis: the mutant's bare hands tear through stone and earth like iron tools -- for mining
 * speed and for whether a block actually drops -- and every earth-family block breaks 50% faster on
 * top of that. Kept out of the {@code Player} mixin itself so the {@link Items} references resolve
 * lazily, well after vanilla's item bootstrap (same reason as {@code StrengthBareHands}).
 */
public final class GeoBareHands {
	private static final String KEY = "power_05_geokinesis";
	private static ItemStack pick;
	private static ItemStack axe;
	private static ItemStack shovel;

	private GeoBareHands() {
	}

	private static void ensure() {
		if (pick == null) {
			pick = new ItemStack(Items.IRON_PICKAXE);
			axe = new ItemStack(Items.IRON_AXE);
			shovel = new ItemStack(Items.IRON_SHOVEL);
		}
	}

	public static boolean applies(Player player) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
	}

	/** An iron-tool floor whatever is held, then +50% for earth-family blocks. */
	public static float miningSpeed(BlockState state, float current) {
		ensure();
		float best = current;
		best = Math.max(best, pick.getDestroySpeed(state));
		best = Math.max(best, axe.getDestroySpeed(state));
		best = Math.max(best, shovel.getDestroySpeed(state));
		if (isEarth(state)) {
			best *= 1.5f;
		}
		return best;
	}

	public static boolean correctToolForDrops(BlockState state) {
		ensure();
		return pick.isCorrectToolForDrops(state)
				|| axe.isCorrectToolForDrops(state)
				|| shovel.isCorrectToolForDrops(state);
	}

	/** Stone, deepslate, dirt, sand, gravel and their families -- the blocks Geokinesis commands. */
	public static boolean isEarth(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
				|| state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
				|| state.is(Blocks.GRAVEL) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.CLAY)
				|| state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.is(BlockTags.NEEDS_STONE_TOOL);
	}
}
