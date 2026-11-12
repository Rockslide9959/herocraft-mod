package com.projecthero.mod.raven;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.3 Raven: blocks her telekinesis really tore out of the ground (mob griefing on) go back where they came from
 * {@link RavenConfig#RESTORE_TICKS} later. If something else filled the spot meanwhile, the block drops as an item instead, so nothing
 * is ever lost. Every pending block is put back at once when the server stops. The list holds levels and block states, so
 * {@code ServerStateReset} clears it ({@link #clearSessionState}).
 */
public final class RavenRestore {
	private record Pending(ServerLevel level, BlockPos pos, BlockState state, long at) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private RavenRestore() {
	}

	public static synchronized void clearSessionState() {
		PENDING.clear();
	}

	/** Can her power lift the real block at {@code pos}? (no block entities, nothing unbreakable, no fluids, not air) */
	public static boolean liftable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0f && hardness < 50f && state.isCollisionShapeFullBlock(level, pos);
	}

	/** Mob griefing on: blocks really move. Off: energy copies. */
	public static boolean griefing(ServerLevel level) {
		return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
	}

	/** Takes the real block out (no drops) and books it to come back. */
	public static synchronized void take(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		PENDING.add(new Pending(level, pos.immutable(), state, level.getGameTime() + RavenConfig.RESTORE_TICKS));
	}

	public static synchronized int pending() {
		return PENDING.size();
	}

	public static synchronized void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<Pending> it = PENDING.iterator();
		while (it.hasNext()) {
			Pending p = it.next();
			if (p.level.getGameTime() >= p.at || p.level.getGameTime() < p.at - RavenConfig.RESTORE_TICKS - 20) {
				put(p);
				it.remove();
			}
		}
	}

	/** Puts every block back now (the server is stopping, or a test). */
	public static synchronized void restoreAll() {
		for (Pending p : PENDING) {
			put(p);
		}
		PENDING.clear();
	}

	private static void put(Pending p) {
		BlockState there = p.level.getBlockState(p.pos);
		if (there.canBeReplaced()) {
			p.level.setBlock(p.pos, p.state, Block.UPDATE_ALL);
			p.level.sendParticles(Raven.dust(Raven.VIOLET, 1.0f), p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.0);
			p.level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
		} else {
			Block.popResource(p.level, p.pos.above(), new net.minecraft.world.item.ItemStack(p.state.getBlock()));
		}
	}
}
