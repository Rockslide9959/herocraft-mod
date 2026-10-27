package com.projecthero.mod.hero.power.p05;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.16.5 Geokinesis rework: every terrain change the power makes, and the rules for making one.
 *
 * <ul>
 * <li>{@link #natural} -- what counts as "natural ground" (stone, dirt, sand, gravel and their families): the Earth meter
 * refills fast on it, falls onto it do nothing, and only it can be sunk or tunnelled.</li>
 * <li>{@link #canEdit} -- build permission: the terrain-damage config, the player's own build rights (adventure /
 * spectator cannot), spawn protection and claims ({@code mayInteract}) and the world border.</li>
 * <li>A ledger of <b>temporary</b> blocks (the Stone Wall, the Sinkhole's lid and its air pocket), each on its own clock.
 * The Sinkhole pocket waits for its prisoner: it is refilled once the buried creature is dead or has dug out (or after a
 * long safety timeout). Everything left is put back on {@code SERVER_STOPPING}, while the levels still exist, and the
 * ledger is dropped on {@code SERVER_STOPPED} ({@code ServerStateReset}) so no dead level is ever pinned.</li>
 * </ul>
 */
public final class GeoTerrain {
	private static final class Entry {
		final ServerLevel level;
		final BlockPos pos;
		final BlockState previous;
		final BlockState placed;
		long restoreAt;
		/** A Sinkhole pocket waits until this entity (id) is no longer inside it; 0 = a plain timed block. */
		final int holder;
		/** A Sinkhole lid: refilled over whoever is inside (they are buried, not lifted out). */
		boolean bury;
		/** A pocket starts watching for its holder to leave only from this game time (it is still falling in before). */
		long watchFrom;

		Entry(ServerLevel level, BlockPos pos, BlockState previous, BlockState placed, long restoreAt, int holder) {
			this.level = level;
			this.pos = pos;
			this.previous = previous;
			this.placed = placed;
			this.restoreAt = restoreAt;
			this.holder = holder;
		}
	}

	private static final int MAX_ENTRIES = 4096;
	private static final Map<Long, List<Entry>> ENTRIES = new HashMap<>();
	private static int count;

	private GeoTerrain() {
	}

	// ================================================================ rules

	/** Stone, deepslate, tuff, dirt, grass, mud, sand, gravel, clay, sandstone and the ores set in them. */
	public static boolean natural(BlockState s) {
		return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL)
				|| s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.DIRT_PATH)
				|| s.is(Blocks.FARMLAND) || s.is(Blocks.SUSPICIOUS_SAND) || s.is(Blocks.SUSPICIOUS_GRAVEL)
				|| s.is(BlockTags.TERRACOTTA) || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK)
				|| s.is(BlockTags.STONE_ORE_REPLACEABLES) || s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
				|| s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.GOLD_ORES)
				|| s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES) || s.is(BlockTags.DIAMOND_ORES)
				|| s.is(BlockTags.EMERALD_ORES);
	}

	/** Standing on natural ground (on the ground, the block under the feet is natural). */
	public static boolean onNaturalGround(LivingEntity e) {
		if (!e.onGround()) {
			return false;
		}
		BlockPos below = BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ());
		return natural(e.level().getBlockState(below));
	}

	/** The natural block under {@code e}, else stone (for the look of rocks, slabs and armour plates). */
	public static BlockState groundBlock(LivingEntity e) {
		for (int dy = 1; dy <= 3; dy++) {
			BlockState s = e.level().getBlockState(BlockPos.containing(e.getX(), e.getY() - 0.2 - (dy - 1), e.getZ()));
			if (natural(s)) {
				return s;
			}
			if (!s.isAir()) {
				break;
			}
		}
		return Blocks.STONE.defaultBlockState();
	}

	/** Whether {@code p} may change the block at {@code pos} with the power. */
	public static boolean canEdit(ServerPlayer p, ServerLevel level, BlockPos pos) {
		return AbilityHelpers.canGrief() && p.mayBuild() && level.mayInteract(p, pos) && level.getWorldBorder().isWithinBounds(pos)
				&& level.isInWorldBounds(pos) && level.hasChunkAt(pos);
	}

	/** A block the power may lift out of / sink into the ground: natural, breakable, no block entity, no fluid. */
	public static boolean diggable(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return natural(s) && !s.hasBlockEntity() && s.getDestroySpeed(level, pos) >= 0 && s.getFluidState().isEmpty();
	}

	// ================================================================ the ledger

	private static Entry find(ServerLevel level, BlockPos pos) {
		List<Entry> list = ENTRIES.get(pos.asLong());
		if (list != null) {
			for (Entry e : list) {
				if (e.level == level) {
					return e;
				}
			}
		}
		return null;
	}

	private static void track(Entry e) {
		ENTRIES.computeIfAbsent(e.pos.asLong(), k -> new ArrayList<>(1)).add(e);
		count++;
	}

	/**
	 * Puts {@code state} at {@code pos} for {@code ttl} ticks, where the cell is air or a replaceable plant / snow layer
	 * (no fluid). Remembers what was there and puts it back on its own clock.
	 *
	 * @return true if the block is there now
	 */
	public static boolean placeTemp(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, int ttl) {
		if (!canEdit(p, level, pos) || count >= MAX_ENTRIES || find(level, pos) != null) {
			return false;
		}
		BlockState now = level.getBlockState(pos);
		if (!(now.isAir() || now.canBeReplaced()) || !now.getFluidState().isEmpty() || now.hasBlockEntity()) {
			return false;
		}
		level.setBlock(pos, state, Block.UPDATE_ALL);
		track(new Entry(level, pos.immutable(), now, state, level.getGameTime() + ttl, 0));
		return true;
	}

	/**
	 * Sinkhole: empties the (natural) cell at {@code pos} until {@code holder} is no longer inside it -- dead, gone or dug
	 * out -- or {@code maxTicks} have passed. The ground is put back exactly as it was.
	 */
	public static boolean pocket(ServerLevel level, BlockPos pos, Entity holder, int maxTicks, int graceTicks) {
		if (count >= MAX_ENTRIES || find(level, pos) != null || !diggable(level, pos)) {
			return false;
		}
		BlockState was = level.getBlockState(pos);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		Entry e = new Entry(level, pos.immutable(), was, Blocks.AIR.defaultBlockState(), level.getGameTime() + maxTicks,
				holder == null ? 0 : holder.getId());
		e.watchFrom = level.getGameTime() + graceTicks;
		track(e);
		return true;
	}

	/**
	 * v0.16.7 (Zero Gravity's touch): empties ANY plain cell at {@code pos} -- a full, breakable block with no block entity
	 * and no fluid, the caller having checked {@link #canEdit} -- for at most {@code maxTicks}; put back exactly as it was by
	 * {@link #restoreNow} or on its clock (or on server stop).
	 */
	public static boolean lift(ServerLevel level, BlockPos pos, int maxTicks) {
		BlockState was = level.getBlockState(pos);
		if (count >= MAX_ENTRIES || find(level, pos) != null || was.isAir() || was.hasBlockEntity() || !was.getFluidState().isEmpty()
				|| was.getDestroySpeed(level, pos) < 0) {
			return false;
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		Entry e = new Entry(level, pos.immutable(), was, Blocks.AIR.defaultBlockState(), level.getGameTime() + maxTicks, 0);
		e.watchFrom = level.getGameTime();
		track(e);
		return true;
	}

	/**
	 * Sinkhole: empties the (natural) cell at {@code pos} for {@code ticks}, then puts the ground back exactly as it was --
	 * over anything that has fallen into it (it is buried, not lifted out).
	 */
	public static boolean bury(ServerLevel level, BlockPos pos, int ticks) {
		if (count >= MAX_ENTRIES || find(level, pos) != null || !diggable(level, pos)) {
			return false;
		}
		BlockState was = level.getBlockState(pos);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		Entry e = new Entry(level, pos.immutable(), was, Blocks.AIR.defaultBlockState(), level.getGameTime() + ticks, 0);
		e.bury = true;
		track(e);
		return true;
	}

	/** True when {@code pos} holds one of this ledger's temporary blocks. */
	public static boolean isTemp(ServerLevel level, BlockPos pos) {
		return find(level, pos) != null;
	}

	/** Puts {@code pos} back now (if the ledger holds it). */
	public static void restoreNow(ServerLevel level, BlockPos pos) {
		List<Entry> list = ENTRIES.get(pos.asLong());
		if (list == null) {
			return;
		}
		Iterator<Entry> it = list.iterator();
		while (it.hasNext()) {
			Entry e = it.next();
			if (e.level == level) {
				it.remove();
				count--;
				restore(e);
			}
		}
		if (list.isEmpty()) {
			ENTRIES.remove(pos.asLong());
		}
	}

	/**
	 * v0.17.1 (Zero Gravity's Crane): the block lifted from {@code pos} was set down somewhere else for good -- forget the
	 * cell without putting anything back (the hole it left stays, like a moved block).
	 */
	public static void commit(ServerLevel level, BlockPos pos) {
		List<Entry> list = ENTRIES.get(pos.asLong());
		if (list == null) {
			return;
		}
		if (list.removeIf(e -> e.level == level)) {
			count = 0;
			for (List<Entry> l : ENTRIES.values()) {
				count += l.size();
			}
		}
		if (list.isEmpty()) {
			ENTRIES.remove(pos.asLong());
		}
	}

	public static int pending() {
		return count;
	}

	/** END_SERVER_TICK. */
	public static void tick(MinecraftServer server) {
		if (count == 0) {
			return;
		}
		List<Entry> due = new ArrayList<>();
		Iterator<Map.Entry<Long, List<Entry>>> it = ENTRIES.entrySet().iterator();
		while (it.hasNext()) {
			List<Entry> list = it.next().getValue();
			Iterator<Entry> li = list.iterator();
			while (li.hasNext()) {
				Entry e = li.next();
				long now = e.level.getGameTime();
				boolean ready = now >= e.restoreAt;
				if (!ready && e.holder != 0 && now >= e.watchFrom && now % 5 == 0) {
					Entity h = e.level.getEntity(e.holder);
					ready = h == null || !h.isAlive() || !h.getBoundingBox().intersects(new AABB(e.pos));
				}
				if (ready) {
					due.add(e);
					li.remove();
					count--;
				}
			}
			if (list.isEmpty()) {
				it.remove();
			}
		}
		// top-down for placed walls (they crumble from the top), bottom-up for refilled pockets
		due.sort((a, b) -> Integer.compare(b.pos.getY(), a.pos.getY()));
		for (Entry e : due) {
			restore(e);
		}
	}

	private static void restore(Entry e) {
		if (!e.level.hasChunkAt(e.pos)) {
			return;
		}
		BlockState now = e.level.getBlockState(e.pos);
		if (e.placed.isAir()) {
			// a pocket: refill only if nobody built there in the meantime
			if (!now.isAir() && !now.canBeReplaced()) {
				return;
			}
			for (Entity en : e.bury ? List.<Entity>of() : e.level.getEntitiesOfClass(Entity.class, new AABB(e.pos))) {
				en.teleportTo(en.getX(), e.pos.getY() + 1.05, en.getZ());
				en.hurtMarked = true;
			}
			e.level.setBlock(e.pos, e.previous, Block.UPDATE_ALL);
		} else if (now == e.placed) {
			e.level.levelEvent(2001, e.pos, Block.getId(e.placed)); // crumbles: break particles + sound
			e.level.setBlock(e.pos, e.previous, Block.UPDATE_ALL);
		}
	}

	/** SERVER_STOPPING: undo every change while the levels still exist. */
	public static void restoreAll() {
		List<Entry> all = new ArrayList<>();
		for (List<Entry> list : ENTRIES.values()) {
			all.addAll(list);
		}
		all.sort((a, b) -> Integer.compare(a.pos.getY(), b.pos.getY()));
		for (Entry e : all) {
			try {
				restore(e);
			} catch (RuntimeException ignored) {
				// the level may be half torn down
			}
		}
		ENTRIES.clear();
		count = 0;
	}

	/** SERVER_STOPPED ({@code ServerStateReset}): drop every reference to the dead levels. */
	public static void clearSessionState() {
		ENTRIES.clear();
		count = 0;
	}
}
