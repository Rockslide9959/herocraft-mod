package com.projecthero.mod.sentry;

import java.util.List;

import com.projecthero.mod.sentry.block.SentryBlocks;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * v0.17.5: the Void eats the world. While it has the body, a ring of black void matter spreads out from it (3 blocks wide at first,
 * a quarter block more every second, 16 at most), swallowing up to 8 blocks a tick: each turns to void matter, then to nothing a
 * moment later ({@code VoidMatterBlock}). Every original is recorded first in {@link VoidMatterData} (per dimension), and about a
 * minute after control returns ({@link SentryConfig#RESTORE_DELAY_TICKS}) the world grows back, 24 blocks a tick, into air only (a
 * block someone has built there since is left alone).
 *
 * <p>Never eaten: bedrock and every unbreakable block, anything with a block entity (chests, furnaces, signs, beds -- all skipped,
 * so no contents are ever at risk), portals and their frames, fluids, anything inside spawn protection or that the body may not touch
 * ({@code mayInteract}), and unloaded chunks. The gamerule {@code projectheroVoidGriefing} (default false) makes it permanent:
 * nothing is recorded and nothing comes back.
 */
public final class VoidMatter {
	public static final String RULE_NAME = "projectheroVoidGriefing";
	public static GameRules.Key<GameRules.BooleanValue> RULE;

	private VoidMatter() {
	}

	public static void initialize() {
		RULE = GameRuleRegistry.register(RULE_NAME, GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));
	}

	public static boolean permanent(ServerLevel level) {
		return RULE != null && level.getGameRules().getBoolean(RULE);
	}

	/** The ring's radius {@code ticks} into a takeover. */
	public static double radius(long ticks) {
		return Math.min(SentryConfig.MATTER_MAX_RADIUS, SentryConfig.MATTER_START_RADIUS + ticks / 20.0 * SentryConfig.MATTER_GROWTH_PER_SECOND);
	}

	/** Whether the Void may swallow the block at {@code pos} (for the body of {@code owner}). */
	public static boolean edible(ServerLevel level, ServerPlayer owner, BlockPos pos) {
		if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
			return false;
		}
		BlockState st = level.getBlockState(pos);
		if (!SentryMoves.unmakeable(level, pos, st)) {
			return false;
		}
		if (owner.getServer() != null && owner.getServer().isUnderSpawnProtection(level, pos, owner)) {
			return false;
		}
		return level.mayInteract(owner, pos);
	}

	/**
	 * Swallows one block (records it, then void matter). True if it went. Public for the tests.
	 */
	public static boolean eat(ServerLevel level, ServerPlayer owner, BlockPos pos) {
		if (!edible(level, owner, pos)) {
			return false;
		}
		BlockState st = level.getBlockState(pos);
		if (!permanent(level)) {
			VoidMatterData.get(level).record(pos, st, owner.getUUID());
		}
		level.setBlock(pos, SentryBlocks.VOID_MATTER.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		return true;
	}

	/** One tick of the ring around the body, {@code ticks} into the takeover. Returns how many blocks went. */
	public static int tickRing(ServerPlayer owner, long ticks) {
		ServerLevel level = owner.serverLevel();
		RandomSource rnd = owner.getRandom();
		double r = radius(ticks);
		int eaten = 0;
		int tries = SentryConfig.MATTER_BLOCKS_PER_TICK * 3;
		int x0 = owner.getBlockX();
		int z0 = owner.getBlockZ();
		for (int i = 0; i < tries && eaten < SentryConfig.MATTER_BLOCKS_PER_TICK; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double d = Math.max(0.0, r - rnd.nextDouble() * 2.0);
			int x = x0 + Mth.floor(Math.cos(a) * d);
			int z = z0 + Mth.floor(Math.sin(a) * d);
			BlockPos column = new BlockPos(x, owner.getBlockY(), z);
			if (!level.isLoaded(column)) {
				continue;
			}
			// the ground under the ring: the surface there, or under the body when it flies over a pit
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			int y = Math.min(top, owner.getBlockY() + 2) - rnd.nextInt(3);
			if (Math.abs(y - owner.getBlockY()) > 10) {
				continue;
			}
			BlockPos pos = new BlockPos(x, y, z);
			if (eat(level, owner, pos)) {
				eaten++;
				if (rnd.nextInt(3) == 0) {
					level.sendParticles(ParticleTypes.SQUID_INK, x + 0.5, y + 1.0, z + 0.5, 2, 0.3, 0.2, 0.3, 0.01);
				}
			}
		}
		return eaten;
	}

	/** The takeover is over: what this Sentry's Void ate comes back in about a minute (or never, under the gamerule). */
	public static int scheduleRestore(ServerLevel level, ServerPlayer owner) {
		VoidMatterData data = VoidMatterData.get(level);
		if (permanent(level)) {
			return -data.forget(owner.getUUID());
		}
		return data.schedule(owner.getUUID(), level.getGameTime() + SentryConfig.RESTORE_DELAY_TICKS);
	}

	/** Every world, every tick: due blocks come back (into air or void matter only, in loaded chunks only). */
	public static void tickLevel(ServerLevel level) {
		VoidMatterData data = VoidMatterData.get(level);
		long now = level.getGameTime();
		if (now % 100 == 0 && data.size() > 0) {
			// orphans: a takeover that ended without telling us (a crash) -- give them back too
			data.adoptOrphans(owner -> {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
				return p != null && Sentry.inTakeover(p);
			}, now + SentryConfig.RESTORE_DELAY_TICKS);
		}
		if (data.size() == 0) {
			return;
		}
		List<VoidMatterData.Entry> due = data.due(now, SentryConfig.RESTORE_PER_TICK * 4);
		int done = 0;
		for (VoidMatterData.Entry e : due) {
			if (done >= SentryConfig.RESTORE_PER_TICK) {
				break;
			}
			if (!level.isLoaded(e.pos)) {
				continue; // waits until someone loads it
			}
			BlockState here = level.getBlockState(e.pos);
			if (here.isAir() || here.is(SentryBlocks.VOID_MATTER) || here.canBeReplaced()) {
				level.setBlock(e.pos, e.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				if (level.getRandom().nextInt(4) == 0) {
					level.sendParticles(ParticleTypes.END_ROD, e.pos.getX() + 0.5, e.pos.getY() + 0.8, e.pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.01);
				}
			}
			data.remove(e);
			done++;
		}
	}

	/** Gametests: brings back everything due by {@code now} in one go (loaded chunks only). Returns how many came back. */
	public static int restoreNow(ServerLevel level) {
		VoidMatterData data = VoidMatterData.get(level);
		int n = 0;
		for (VoidMatterData.Entry e : data.due(Long.MAX_VALUE - 1, Integer.MAX_VALUE)) {
			if (!level.isLoaded(e.pos)) {
				continue;
			}
			BlockState here = level.getBlockState(e.pos);
			if (here.isAir() || here.is(SentryBlocks.VOID_MATTER) || here.canBeReplaced()) {
				level.setBlock(e.pos, e.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				n++;
			}
			data.remove(e);
		}
		return n;
	}
}
