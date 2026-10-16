package com.projecthero.mod.hero.power.p05;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Geokinesis mining passive. v0.16.5 rework: stone and dirt (natural ground, {@link GeoTerrain#natural}) break
 * <b>twice as fast</b> with whatever you hold. The old iron-tool hands, vein mining and Haste are gone, so
 * {@link #correctToolForDrops} no longer grants drops (the mixin hook stays, inert).
 */
public final class GeoBareHands {
	private static final String KEY = "power_05_geokinesis";
	public static final float MINING_FACTOR = 2.0f;

	private GeoBareHands() {
	}

	public static boolean applies(Player player) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
	}

	/** Natural ground breaks twice as fast. */
	public static float miningSpeed(BlockState state, float current) {
		return GeoTerrain.natural(state) ? current * MINING_FACTOR : current;
	}

	public static boolean correctToolForDrops(BlockState state) {
		return false;
	}

	/** Stone, deepslate, dirt, sand, gravel and their families (used by {@link GroundType}). */
	public static boolean isEarth(BlockState state) {
		return GeoTerrain.natural(state) || state.is(Blocks.DEEPSLATE)
				|| state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.is(BlockTags.NEEDS_STONE_TOOL);
	}
}
