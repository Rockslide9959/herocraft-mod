package com.herocraft.mod.ironman.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Block-breaking helpers for Iron Man weapons (spec "changes 8": repulsors break glass, charged
 * repulsors break normal blocks, the Unibeam carves a path). Deliberately skips anything with a high
 * blast resistance (obsidian, ancient debris, reinforced deepslate, bedrock) so a stray shot cannot
 * chew through a base wall or the world floor.
 */
public final class IronManBlockBreak {
	/** Above this blast resistance a block is left alone. */
	private static final float MAX_RESISTANCE = 50.0f;

	private IronManBlockBreak() {
	}

	public static boolean isGlass(BlockState state) {
		if (state.isAir()) {
			return false;
		}
		var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return key.getPath().contains("glass");
	}

	public static boolean isBreakable(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.liquid()) {
			return false;
		}
		if (state.getDestroySpeed(level, pos) < 0.0f) {
			return false; // unbreakable
		}
		return state.getBlock().getExplosionResistance() < MAX_RESISTANCE;
	}

	/** Break every glass block the segment {@code from -> to} passes through. */
	public static void breakGlassAlong(ServerLevel level, Player owner, Vec3 from, Vec3 to) {
		walk(level, from, to, pos -> {
			BlockState s = level.getBlockState(pos);
			if (isGlass(s)) {
				level.destroyBlock(pos, true, owner);
			}
		});
	}

	/** Break any (soft) block the segment passes through -- the Unibeam carving a tunnel. */
	public static void breakSoftAlong(ServerLevel level, Player owner, Vec3 from, Vec3 to, int radius) {
		walk(level, from, to, pos -> {
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
				BlockState s = level.getBlockState(p);
				if (isBreakable(level, p, s)) {
					level.destroyBlock(p.immutable(), true, owner);
				}
			}
		});
	}

	/**
	 * Break up to {@code count} breakable blocks in a straight line, starting at {@code start} and
	 * stepping along {@code dir} -- the Charged Repulsor punching a short hole through a wall (spec
	 * "changes 9": "around 3 in a row", blocks only, never a crater).
	 */
	public static void breakLine(ServerLevel level, Player owner, BlockPos start, Vec3 dir, int count) {
		Vec3 unit = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
		Vec3 from = Vec3.atCenterOf(start);
		int broken = 0;
		BlockPos last = null;
		for (double d = 0; d <= count + 1.5 && broken < count; d += 0.34) {
			BlockPos bp = BlockPos.containing(from.add(unit.scale(d)));
			if (bp.equals(last)) {
				continue;
			}
			last = bp;
			BlockState s = level.getBlockState(bp);
			if (isBreakable(level, bp, s)) {
				level.destroyBlock(bp, true, owner);
				broken++;
			}
		}
	}

	/** A small crater of soft blocks around a point -- the charged repulsor impact. */
	public static void breakCluster(ServerLevel level, Player owner, BlockPos center, int radius) {
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (p.distSqr(center) > (double) radius * radius + 1) {
				continue;
			}
			BlockState s = level.getBlockState(p);
			if (isBreakable(level, p, s)) {
				level.destroyBlock(p.immutable(), true, owner);
			}
		}
	}

	private interface PosConsumer {
		void accept(BlockPos pos);
	}

	private static void walk(ServerLevel level, Vec3 from, Vec3 to, PosConsumer fn) {
		double dist = from.distanceTo(to);
		int steps = Math.max(1, (int) (dist * 2));
		BlockPos last = null;
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.lerp(to, (double) i / steps);
			BlockPos bp = BlockPos.containing(p);
			if (!bp.equals(last)) {
				fn.accept(bp);
				last = bp;
			}
		}
	}
}
