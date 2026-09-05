package com.herocraft.mod.hero.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.herocraft.mod.hero.HeroConfig;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Player-conjured earth/crystal/ice constructs (walls, domes, bridges). Unlike {@link TempBlocks}
 * these are owned by the caster: each caster has at most one active construct, it restores itself
 * after a fixed TTL, and the "dome" variant can be dismissed early by its owner left-clicking any of
 * its blocks. Gated on {@link HeroConfig#abilityTerrainDamage} the same way {@link TempBlocks} is.
 */
public final class ConjuredStructures {
	/** Every construct in this batch lasts this long unless dismissed. */
	public static final int TTL_TICKS = 25 * 20;

	private record Cell(BlockPos pos, BlockState previous, BlockState placed) {
	}

	private static final class Construct {
		final ServerLevel level;
		boolean dome;
		long expiresAt;
		final List<Cell> cells = new ArrayList<>();

		Construct(ServerLevel level, long expiresAt) {
			this.level = level;
			this.expiresAt = expiresAt;
		}
	}

	private static final Map<UUID, Construct> BY_OWNER = new HashMap<>();

	private ConjuredStructures() {
	}

	public static void initialize() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (player instanceof ServerPlayer sp) {
				Construct c = BY_OWNER.get(sp.getUUID());
				if (c != null && c.dome) {
					for (Cell cell : c.cells) {
						if (cell.pos.equals(pos)) {
							dismiss(sp);
							return InteractionResult.SUCCESS;
						}
					}
				}
			}
			return InteractionResult.PASS;
		});

		// Conjured blocks are not a resource: mining one removes it and drops nothing.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			for (Construct c : BY_OWNER.values()) {
				if (c.level != level) {
					continue;
				}
				for (int i = 0; i < c.cells.size(); i++) {
					Cell cell = c.cells.get(i);
					if (cell.pos.equals(pos) && level.getBlockState(pos) == cell.placed) {
						level.removeBlock(pos, false);
						c.cells.remove(i);
						return false;
					}
				}
			}
			return true;
		});
	}

	/**
	 * Forget every tracked construct without restoring it -- only ever called once the owning server
	 * has stopped ({@code ServerStateReset}); the cells hold a {@link ServerLevel} that must not be
	 * kept alive into the next world opened in the same JVM.
	 */
	public static void clearSessionState() {
		BY_OWNER.clear();
	}

	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		Iterator<Map.Entry<UUID, Construct>> it = BY_OWNER.entrySet().iterator();
		while (it.hasNext()) {
			Construct c = it.next().getValue();
			if (now >= c.expiresAt) {
				restore(c);
				it.remove();
			}
		}
	}

	public static void dismiss(ServerPlayer owner) {
		Construct c = BY_OWNER.remove(owner.getUUID());
		if (c != null) {
			restore(c);
		}
	}

	private static void restore(Construct c) {
		for (Cell cell : c.cells) {
			if (c.level.hasChunkAt(cell.pos) && c.level.getBlockState(cell.pos) == cell.placed) {
				c.level.setBlock(cell.pos, cell.previous, Block.UPDATE_ALL);
			}
		}
	}

	// ---------------- shapes ----------------

	/** A 4-wide, 4-tall slab two blocks in front of the player, facing them. */
	public static boolean wall(ServerPlayer p, ServerLevel level, BlockState state) {
		Direction facing = p.getDirection();
		Direction side = facing.getClockWise();
		BlockPos front = p.blockPosition().relative(facing, 2);
		Construct c = fresh(level, false);
		for (int w = -1; w <= 2; w++) {
			for (int h = 0; h < 4; h++) {
				add(c, level, front.relative(side, w).above(h), state);
			}
		}
		return commit(p, c);
	}

	/** A hollow hemisphere centred on the player, radius 4, dismissible by its owner. */
	public static boolean dome(ServerPlayer p, ServerLevel level, BlockState state) {
		Construct c = fresh(level, true);
		int r = 4;
		BlockPos center = p.blockPosition();
		for (int x = -r; x <= r; x++) {
			for (int y = 0; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					double d = Math.sqrt(x * x + y * y + z * z);
					if (d > r - 0.5 && d < r + 0.5) {
						add(c, level, center.offset(x, y, z), state);
					}
				}
			}
		}
		// Floor: seal any gap under the dome so nothing comes up from below.
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z <= (r - 0.5) * (r - 0.5)) {
					add(c, level, center.offset(x, -1, z), state);
				}
			}
		}
		return commit(p, c);
	}

	/** A 3-wide, 20-long flat bridge extending ahead of the player at foot level. */
	public static boolean bridge(ServerPlayer p, ServerLevel level, BlockState state) {
		Direction facing = p.getDirection();
		Direction side = facing.getClockWise();
		BlockPos start = p.blockPosition().below().relative(facing);
		Construct c = fresh(level, false);
		for (int f = 0; f < 20; f++) {
			for (int w = -1; w <= 1; w++) {
				add(c, level, start.relative(facing, f).relative(side, w), state);
			}
		}
		return commit(p, c);
	}

	// ---------------- internals ----------------

	private static Construct fresh(ServerLevel level, boolean dome) {
		Construct c = new Construct(level, level.getGameTime() + TTL_TICKS);
		c.dome = dome;
		return c;
	}

	private static void add(Construct c, ServerLevel level, BlockPos pos, BlockState state) {
		BlockState current = level.getBlockState(pos);
		if (!current.canBeReplaced() && !current.isAir()) {
			return;
		}
		c.cells.add(new Cell(pos.immutable(), current, state));
	}

	private static boolean commit(ServerPlayer p, Construct c) {
		if (!HeroConfig.get().abilityTerrainDamage || c.cells.isEmpty()) {
			return false;
		}
		dismiss(p); // one construct per caster
		for (Cell cell : c.cells) {
			c.level.setBlock(cell.pos, cell.placed, Block.UPDATE_ALL);
		}
		BY_OWNER.put(p.getUUID(), c);
		return true;
	}
}
