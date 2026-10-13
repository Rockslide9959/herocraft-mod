package com.projecthero.mod.galactus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.galactus.block.GalactusBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.16.1: <b>every block Galactus changes, and how each is put back</b> -- one ledger, persisted as server-wide
 * {@link SavedData} (on the overworld), so it is both the event's saved record and the orphan guard.
 *
 * <h2>What may change (the safety rules)</h2>
 * <ul>
 *   <li>Only natural blocks: the {@code projecthero:galactus_consumable} tag (grass, dirt, stone, sand, gravel, snow,
 *       clay...) becomes Consumed Ground / Cosmic Hazard; the plants on top of them (short grass, flowers, ferns) are
 *       cleared to air with them. Never a block entity; nothing within
 *       {@link GalactusConfig.Converters#blockEntityClearance} of one (a base's chests and furnaces keep the whole base
 *       clean); a column stops at its first block that is neither -- a player's floor or wall shields what is below.</li>
 *   <li>Loaded chunks only (never force-loads); set with UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE (no neighbour updates, so
 *       nothing pops off, falls or decays).</li>
 *   <li>Every original state is recorded <em>before</em> it changes, under the event's id and a group (a converter's index,
 *       or {@link #GROUP_HAZARD} with an expiry time).</li>
 * </ul>
 *
 * <h2>Revert</h2>
 * A group (a destroyed converter) or the whole ledger (win / loss / abort / an event that no longer exists -- swept every
 * 10 s) is marked, and {@link #tickRevert} puts it back {@link GalactusConfig.Converters#revertPerTick} blocks a tick
 * (nearest first, the order they were taken), only where the block there is still the one Galactus put down (a block a
 * player broke or replaced since is left as it is), and only in loaded chunks -- the rest wait in the ledger until their
 * chunk loads. Lives in saved data, not a static: nothing for {@code ServerStateReset} to clear.
 */
public final class GalactusTerrain extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_galactus_terrain";
	public static final int GROUP_HAZARD = -1;
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** One changed block: palette ids of what was put there and what was there, its group, and when it expires (-1 never). */
	private record Entry(int placed, int original, int group, long expiresAt) {
	}

	private static final class Ledger {
		ResourceKey<Level> dim = Level.OVERWORLD;
		final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();
		final Set<Integer> reverting = new HashSet<>();
		boolean all;
	}

	private final Map<UUID, Ledger> byEvent = new LinkedHashMap<>();
	private final List<BlockState> palette = new ArrayList<>();
	private final Map<BlockState, Integer> paletteIndex = new HashMap<>();
	private transient boolean work;
	private transient long nextExpiry = Long.MAX_VALUE;

	private static final SavedData.Factory<GalactusTerrain> FACTORY = new SavedData.Factory<>(GalactusTerrain::new, GalactusTerrain::load,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static GalactusTerrain get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static GalactusTerrain get(ServerLevel level) {
		return get(level.getServer());
	}

	private int id(BlockState s) {
		Integer i = paletteIndex.get(s);
		if (i != null) {
			return i;
		}
		palette.add(s);
		paletteIndex.put(s, palette.size() - 1);
		return palette.size() - 1;
	}

	private BlockState state(int id) {
		return id >= 0 && id < palette.size() ? palette.get(id) : null;
	}

	// ---------------------------------------------------------------- the rules

	/** May the event turn {@code s} into its own block? */
	public static boolean consumable(BlockState s) {
		return s.is(GalactusBlocks.CONSUMABLE) && !s.hasBlockEntity();
	}

	/** A plant / tuft standing on consumable ground (cleared with it, put back with it). */
	public static boolean plant(BlockState s) {
		if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty()) {
			return false;
		}
		return s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.SWEET_BERRY_BUSH)
				|| s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS);
	}

	/** Is there a block entity within {@code r} blocks of {@code pos}? (An unloaded chunk counts as yes.) */
	public static boolean nearBlockEntity(ServerLevel level, BlockPos pos, int r) {
		if (r <= 0) {
			return false;
		}
		int r2 = r * r;
		for (int cx = SectionPos.blockToSectionCoord(pos.getX() - r); cx <= SectionPos.blockToSectionCoord(pos.getX() + r); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(pos.getZ() - r); cz <= SectionPos.blockToSectionCoord(pos.getZ() + r); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					return true;
				}
				for (BlockPos be : chunk.getBlockEntitiesPos()) {
					if (Math.abs(be.getY() - pos.getY()) <= r + 4 && (be.getX() - pos.getX()) * (be.getX() - pos.getX())
							+ (be.getZ() - pos.getZ()) * (be.getZ() - pos.getZ()) <= r2) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private Ledger ledger(UUID event, ServerLevel level) {
		Ledger l = byEvent.computeIfAbsent(event, u -> new Ledger());
		l.dim = level.dimension();
		return l;
	}

	// ---------------------------------------------------------------- changing blocks

	/**
	 * Puts {@code placed} at {@code pos} for {@code event}, recording what was there first. Refuses a block entity, an
	 * unloaded chunk, and anything already recorded by another event. ({@code ttl} &lt; 0: until the group is reverted.)
	 */
	public boolean place(ServerLevel level, UUID event, int group, BlockPos pos, BlockState placed, int ttl) {
		if (event == null || level.isOutsideBuildHeight(pos) || !level.isLoaded(pos) || level.getBlockEntity(pos) != null) {
			return false;
		}
		long key = pos.asLong();
		for (Map.Entry<UUID, Ledger> e : byEvent.entrySet()) {
			if (!e.getKey().equals(event) && e.getValue().entries.containsKey(key)) {
				return false;
			}
		}
		Ledger l = ledger(event, level);
		BlockState now = level.getBlockState(pos);
		Entry mine = l.entries.get(key);
		BlockState original = mine != null ? state(mine.original()) : now;
		if (mine != null && !now.is(state(mine.placed()).getBlock())) {
			l.entries.remove(key); // someone changed it since -- it is theirs now
			return false;
		}
		long expires = ttl < 0 ? -1L : level.getGameTime() + ttl;
		int g = mine != null && mine.group() != GROUP_HAZARD ? mine.group() : group;
		if (mine != null && mine.group() != GROUP_HAZARD) {
			expires = -1L; // a converter's block stays a converter's block
		}
		level.setBlock(pos, placed, FLAGS);
		l.entries.put(key, new Entry(id(placed), id(original), g, expires));
		if (expires >= 0) {
			nextExpiry = Math.min(nextExpiry, expires);
		}
		setDirty();
		return true;
	}

	/**
	 * Consumes the top of column (x, z) for {@code event}: plants cleared to air, up to {@code depth} consumable ground
	 * blocks turned into {@code into}; stops at the first block that is neither. Returns the blocks changed.
	 */
	public int consumeColumn(ServerLevel level, UUID event, int group, int x, int z, int depth, BlockState into, int budget) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		int ground = 0;
		int changed = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = top; y > top - 12 && y > level.getMinBuildHeight() && changed < budget && ground < depth; y--) {
			p.set(x, y, z);
			BlockState st = level.getBlockState(p);
			if (st.isAir() || GalactusBlocks.isEventBlock(st.getBlock())) {
				if (GalactusBlocks.isEventBlock(st.getBlock())) {
					ground++;
				}
				continue;
			}
			if (plant(st)) {
				if (place(level, event, group, p.immutable(), Blocks.AIR.defaultBlockState(), -1)) {
					changed++;
				}
				continue;
			}
			if (!consumable(st)) {
				break; // a floor, a wall, water, an ore: it shields what is under it
			}
			if (place(level, event, group, p.immutable(), into, -1)) {
				changed++;
			}
			ground++;
		}
		return changed;
	}

	// ---------------------------------------------------------------- putting it back

	public void markGroup(UUID event, int group) {
		Ledger l = byEvent.get(event);
		if (l != null) {
			l.reverting.add(group);
			work = true;
			setDirty();
		}
	}

	public void markAll(UUID event) {
		Ledger l = byEvent.get(event);
		if (l != null) {
			l.all = true;
			work = true;
			setDirty();
		}
	}

	private boolean revertOne(ServerLevel level, long key, Entry e) {
		BlockPos pos = BlockPos.of(key);
		BlockState placed = state(e.placed());
		BlockState original = state(e.original());
		if (placed == null || original == null) {
			return true;
		}
		BlockState now = level.getBlockState(pos);
		boolean ours = placed.isAir() ? now.isAir() : now.is(placed.getBlock());
		if (ours) {
			level.setBlock(pos, original, FLAGS);
		}
		return true;
	}

	/** Puts back up to {@code budget} marked or expired blocks (loaded chunks only). Returns how many. Once a server tick. */
	public int tickRevert(MinecraftServer server, int budget) {
		long now = server.overworld().getGameTime();
		if (now % 200 == 61) {
			sweepOrphans(server);
		}
		boolean expiring = now >= nextExpiry;
		if (!work && !expiring && now % 100 != 7) {
			return 0;
		}
		int done = 0;
		boolean left = false;
		long soonest = Long.MAX_VALUE;
		for (Iterator<Map.Entry<UUID, Ledger>> it = byEvent.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Ledger> le = it.next();
			Ledger l = le.getValue();
			ServerLevel level = server.getLevel(l.dim);
			if (level == null) {
				continue;
			}
			for (Iterator<Map.Entry<Long, Entry>> bt = l.entries.entrySet().iterator(); bt.hasNext();) {
				Map.Entry<Long, Entry> b = bt.next();
				Entry e = b.getValue();
				boolean due = l.all || l.reverting.contains(e.group()) || e.expiresAt() >= 0 && now >= e.expiresAt();
				if (!due) {
					if (e.expiresAt() >= 0) {
						soonest = Math.min(soonest, e.expiresAt());
					}
					continue;
				}
				if (done >= budget) {
					left = true;
					break;
				}
				if (!level.isLoaded(BlockPos.of(b.getKey()))) {
					left = true;
					continue;
				}
				revertOne(level, b.getKey(), e);
				bt.remove();
				done++;
				if (done % 32 == 0) {
					BlockPos p = BlockPos.of(b.getKey());
					level.sendParticles(GalactusFx.BLUE, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 1, 0.3, 0.1, 0.3, 0.0);
				}
			}
			if (l.entries.isEmpty() && (l.all || !isActive(server, le.getKey()))) {
				it.remove();
			}
		}
		nextExpiry = soonest;
		work = left && done >= budget; // more ready to go next tick; unloaded leftovers wait for the 5 s retry
		if (done > 0) {
			setDirty();
		}
		return done;
	}

	/** Puts back everything of {@code event} in loaded chunks right now (tests, and the end of a test). */
	public int revertAllNow(ServerLevel level, UUID event) {
		Ledger l = byEvent.get(event);
		if (l == null) {
			return 0;
		}
		int n = 0;
		for (Iterator<Map.Entry<Long, Entry>> bt = l.entries.entrySet().iterator(); bt.hasNext();) {
			Map.Entry<Long, Entry> b = bt.next();
			if (level.isLoaded(BlockPos.of(b.getKey()))) {
				revertOne(level, b.getKey(), b.getValue());
				bt.remove();
				n++;
			}
		}
		l.all = true;
		if (l.entries.isEmpty()) {
			byEvent.remove(event);
		}
		setDirty();
		return n;
	}

	private static boolean isActive(MinecraftServer server, UUID event) {
		var e = EventSavedData.get(server).byId(event);
		return e != null && e.state().active();
	}

	/** Any ledger whose event no longer exists (crashed, dropped, ended while unloaded) is put back. */
	public void sweepOrphans(MinecraftServer server) {
		for (Map.Entry<UUID, Ledger> e : byEvent.entrySet()) {
			if (!e.getValue().all && !isActive(server, e.getKey())) {
				e.getValue().all = true;
				work = true;
				setDirty();
			}
		}
	}

	// ---------------------------------------------------------------- queries

	public int count(UUID event) {
		Ledger l = byEvent.get(event);
		return l == null ? 0 : l.entries.size();
	}

	public int count(UUID event, int group) {
		Ledger l = byEvent.get(event);
		if (l == null) {
			return 0;
		}
		int n = 0;
		for (Entry e : l.entries.values()) {
			if (e.group() == group) {
				n++;
			}
		}
		return n;
	}

	public boolean tracks(UUID event, BlockPos pos) {
		Ledger l = byEvent.get(event);
		return l != null && l.entries.containsKey(pos.asLong());
	}

	/** The recorded original at {@code pos}, or null (tests). */
	public BlockState originalAt(UUID event, BlockPos pos) {
		Ledger l = byEvent.get(event);
		Entry e = l == null ? null : l.entries.get(pos.asLong());
		return e == null ? null : state(e.original());
	}

	// ---------------------------------------------------------------- save

	private static GalactusTerrain load(CompoundTag tag, HolderLookup.Provider registries) {
		GalactusTerrain data = new GalactusTerrain();
		var blocks = registries.lookup(Registries.BLOCK).map(l -> (net.minecraft.core.HolderGetter<Block>) l)
				.orElse(BuiltInRegistries.BLOCK.asLookup());
		ListTag pal = tag.getList("Palette", Tag.TAG_COMPOUND);
		for (int i = 0; i < pal.size(); i++) {
			BlockState s = NbtUtils.readBlockState(blocks, pal.getCompound(i));
			data.palette.add(s);
			data.paletteIndex.putIfAbsent(s, i);
		}
		ListTag events = tag.getList("Events", Tag.TAG_COMPOUND);
		for (int i = 0; i < events.size(); i++) {
			CompoundTag ev = events.getCompound(i);
			if (!ev.hasUUID("Id")) {
				continue;
			}
			Ledger l = new Ledger();
			ResourceLocation dim = ResourceLocation.tryParse(ev.getString("Dim"));
			l.dim = dim == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, dim);
			l.all = ev.getBoolean("All");
			for (int g : ev.getIntArray("Reverting")) {
				l.reverting.add(g);
			}
			long[] pos = ev.getLongArray("Pos");
			int[] placed = ev.getIntArray("Placed");
			int[] original = ev.getIntArray("Original");
			int[] group = ev.getIntArray("Group");
			long[] exp = ev.getLongArray("Expires");
			int n = Math.min(pos.length, Math.min(placed.length, Math.min(original.length, Math.min(group.length, exp.length))));
			for (int j = 0; j < n; j++) {
				l.entries.put(pos[j], new Entry(placed[j], original[j], group[j], exp[j]));
			}
			if (!l.entries.isEmpty()) {
				data.byEvent.put(ev.getUUID("Id"), l);
				data.work = data.work || l.all || !l.reverting.isEmpty();
			}
		}
		data.nextExpiry = Long.MIN_VALUE; // re-derived on the first pass
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag pal = new ListTag();
		for (BlockState s : palette) {
			pal.add(NbtUtils.writeBlockState(s));
		}
		tag.put("Palette", pal);
		ListTag events = new ListTag();
		for (Map.Entry<UUID, Ledger> e : byEvent.entrySet()) {
			Ledger l = e.getValue();
			CompoundTag t = new CompoundTag();
			t.putUUID("Id", e.getKey());
			t.putString("Dim", l.dim.location().toString());
			t.putBoolean("All", l.all);
			t.putIntArray("Reverting", new ArrayList<>(l.reverting));
			int n = l.entries.size();
			long[] pos = new long[n];
			int[] placed = new int[n];
			int[] original = new int[n];
			int[] group = new int[n];
			long[] exp = new long[n];
			int j = 0;
			for (Map.Entry<Long, Entry> b : l.entries.entrySet()) {
				pos[j] = b.getKey();
				placed[j] = b.getValue().placed();
				original[j] = b.getValue().original();
				group[j] = b.getValue().group();
				exp[j] = b.getValue().expiresAt();
				j++;
			}
			t.putLongArray("Pos", pos);
			t.putIntArray("Placed", placed);
			t.putIntArray("Original", original);
			t.putIntArray("Group", group);
			t.putLongArray("Expires", exp);
			events.add(t);
		}
		tag.put("Events", events);
		return tag;
	}
}
