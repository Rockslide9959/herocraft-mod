package com.projecthero.mod.titan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Block destruction for the Titan's attacks -- "terrain is an inconvenience, not a hard barrier"
 * (spec section 14), but bounded and safe. Modelled on
 * {@link com.projecthero.mod.ironman.ability.IronManBlockBreak} (same blast-resistance filter idea),
 * with what that class doesn't need for a player weapon: an explicit blacklist of
 * indestructible/technical blocks, and gating on {@link TitanConfig.World}'s destruction toggles.
 */
public final class TitanTerrain {
	/** Blocks that must NEVER be destroyed, whatever the config's hardness/radius limits say. */
	private static boolean isProtected(BlockState state) {
		Block block = state.getBlock();
		if (block == Blocks.BEDROCK || block == Blocks.BARRIER || block == Blocks.END_PORTAL
				|| block == Blocks.END_PORTAL_FRAME || block == Blocks.END_GATEWAY
				|| block == Blocks.COMMAND_BLOCK || block == Blocks.CHAIN_COMMAND_BLOCK
				|| block == Blocks.REPEATING_COMMAND_BLOCK || block == Blocks.STRUCTURE_BLOCK
				|| block == Blocks.STRUCTURE_VOID || block == Blocks.JIGSAW
				|| block == Blocks.NETHER_PORTAL || block == Blocks.LIGHT) {
			return true;
		}
		var key = BuiltInRegistries.BLOCK.getKey(block);
		return TitanConfig.world().blockBlacklist.contains(key.toString())
				|| TitanConfig.world().blockBlacklist.contains(key.getPath());
	}

	private static boolean isFragile(BlockState state) {
		return state.is(BlockTags.LEAVES) || state.is(BlockTags.FENCES) || state.is(BlockTags.SAPLINGS)
				|| state.is(BlockTags.FLOWERS) || state.is(BlockTags.SMALL_FLOWERS)
				|| state.getBlock() == Blocks.SHORT_GRASS || state.getBlock() == Blocks.TALL_GRASS
				|| state.getBlock() == Blocks.GLASS || state.getBlock() == Blocks.GLASS_PANE
				|| BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass");
	}

	private static boolean isBreakable(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.liquid()) {
			return false;
		}
		if (state.getDestroySpeed(level, pos) < 0.0f || isProtected(state)) {
			return false;
		}
		return state.getBlock().getExplosionResistance() < TitanConfig.world().maxDestructionHardness;
	}

	private TitanTerrain() {
	}

	/**
	 * v0.9.22: destroy one block, but only spend an item drop on {@link TitanConfig.World#blockDropChance}
	 * of them -- breaking hundreds of blocks per fight used to spawn hundreds of {@code ItemEntity}s and
	 * lag the server. Returns true if the block was actually removed (for per-call caps).
	 */
	private static boolean titanBreak(ServerLevel level, BlockPos pos) {
		boolean drop = level.getRandom().nextFloat() < TitanConfig.world().blockDropChance;
		return level.destroyBlock(pos, drop);
	}

	/** Passive walking destruction: only genuinely fragile blocks the Titan's body intersects. */
	public static void breakFragileAt(ServerLevel level, BlockPos pos) {
		if (!TitanConfig.world().blockDestructionEnabled || !TitanConfig.world().passiveWalkingDestructionEnabled) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (isFragile(state) && !isProtected(state)) {
			titanBreak(level, pos.immutable());
		}
	}

	/**
	 * v0.9.22: carve a corridor toward a point so the Titan is never hard-stopped by terrain
	 * ("terrain is an inconvenience, not a hard barrier"). Breaks a box {@code width} wide and
	 * {@code height} tall along the segment, from the Titan's own feet upward (never below).
	 */
	public static void carveCorridor(ServerLevel level, Vec3 from, Vec3 to, int width, int height) {
		if (!TitanConfig.world().blockDestructionEnabled) {
			return;
		}
		int broken = 0;
		int cap = TitanConfig.world().maxBlocksPerDestruction;
		double dist = from.distanceTo(to);
		int steps = Math.max(1, (int) (dist * 1.5));
		int r = Math.max(1, width / 2);
		for (int i = 0; i <= steps && broken < cap; i++) {
			Vec3 p = from.lerp(to, (double) i / steps);
			BlockPos c = BlockPos.containing(p);
			for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, 0, -r), c.offset(r, height, r))) {
				if (broken >= cap) {
					break;
				}
				BlockState s = level.getBlockState(bp);
				if (isBreakable(level, bp, s) && titanBreak(level, bp.immutable())) {
					broken++;
				}
			}
		}
	}

	/** A radial cluster of breakable blocks, e.g. a boulder's impact crater -- may dig below {@code center}. */
	public static void breakCluster(ServerLevel level, BlockPos center, double radius) {
		breakCluster(level, center, radius, true);
	}

	/**
	 * A radial cluster of breakable blocks -- stomp / ground slam / boulder impact.
	 *
	 * @param includeBelow whether blocks below {@code center}'s Y level may be destroyed. Stomp and
	 *                     Ground Slam always centre this on the Titan's OWN feet
	 *                     ({@code TitanEntity#blockPosition()}) -- with this {@code true}, every use
	 *                     hollowed out the very ground the Titan was standing on, dropping it into the
	 *                     crater it had just made. Landing there and immediately using the same attack
	 *                     again (both are common picks in {@code TitanEntity#chooseAttack}) repeated the
	 *                     hollow-out one level down each time, so the Titan dug itself progressively
	 *                     deeper into an unreachable pit -- "the boss keeps digging himself into a hole."
	 *                     Those two attacks pass {@code false} so they only ever carve sideways and
	 *                     upward from the Titan's own standing level, never the floor under it. A
	 *                     boulder's impact crater (a different entity's landing spot, not the Titan's own
	 *                     feet) keeps the old {@code true} behaviour via the overload above.
	 */
	public static void breakCluster(ServerLevel level, BlockPos center, double radius, boolean includeBelow) {
		if (!TitanConfig.world().blockDestructionEnabled) {
			return;
		}
		double r = Math.min(radius, TitanConfig.world().maxDestructionRadius);
		int ir = (int) Math.ceil(r);
		int minY = includeBelow ? -ir : 0;
		int broken = 0;
		int cap = TitanConfig.world().maxBlocksPerDestruction;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-ir, minY, -ir), center.offset(ir, ir, ir))) {
			if (broken >= cap) {
				break;
			}
			if (p.distSqr(center) > r * r + 1) {
				continue;
			}
			BlockState s = level.getBlockState(p);
			if (isBreakable(level, p, s) && titanBreak(level, p.immutable())) {
				broken++;
			}
		}
	}

	/** Blocks directly in the Titan's charge path -- a short segment, not a full-body sweep. Never digs
	 *  below the Titan's own feet, for the same "would dig itself into a hole" reason as {@link
	 *  #breakCluster(ServerLevel, BlockPos, double, boolean)}'s {@code includeBelow=false} case. */
	public static void breakAlongPath(ServerLevel level, Vec3 from, Vec3 to, double width) {
		if (!TitanConfig.world().blockDestructionEnabled || !TitanConfig.world().chargeDestructionEnabled) {
			return;
		}
		double dist = from.distanceTo(to);
		int steps = Math.max(1, (int) (dist * 2));
		int r = Math.max(1, (int) Math.round(width / 2));
		int broken = 0;
		int cap = TitanConfig.world().maxBlocksPerDestruction;
		for (int i = 0; i <= steps && broken < cap; i++) {
			Vec3 p = from.lerp(to, (double) i / steps);
			BlockPos center = BlockPos.containing(p);
			for (BlockPos bp : BlockPos.betweenClosed(center.offset(-r, 0, -r), center.offset(r, r, r))) {
				if (broken >= cap) {
					break;
				}
				BlockState s = level.getBlockState(bp);
				if (isBreakable(level, bp, s) && titanBreak(level, bp.immutable())) {
					broken++;
				}
			}
		}
	}
}
