package com.projecthero.mod.frostgiant;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.16.1: the <b>orphan guard</b> for Frost Giant Assault blocks. Every snow layer and ice block an assault places is
 * mirrored here (server-wide {@link SavedData} on the overworld) under the assault's id; the assault removes its entries as
 * it takes them back. Every 10 s {@link #tick} looks for entries whose assault no longer exists (it ended while a chunk
 * was unloaded, its record was dropped, the server crashed mid-fight) and puts those blocks back as soon as their chunks are
 * loaded -- only if the block there is still the one that was placed. Lives in saved data, not a static, so there is
 * nothing for {@code ServerStateReset} to clear.
 */
public final class FrostCleanup extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_frost_cleanup";

	private record Pending(ResourceKey<Level> dim, BlockState placed, BlockState previous) {
	}

	private final Map<UUID, Map<Long, Pending>> byEvent = new LinkedHashMap<>();

	private static final SavedData.Factory<FrostCleanup> FACTORY = new SavedData.Factory<>(FrostCleanup::new, FrostCleanup::load,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static FrostCleanup get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static FrostCleanup get(ServerLevel level) {
		return get(level.getServer());
	}

	public void add(UUID owner, ServerLevel level, BlockPos pos, BlockState placed, BlockState previous) {
		if (owner == null) {
			return;
		}
		byEvent.computeIfAbsent(owner, u -> new HashMap<>()).put(pos.asLong(), new Pending(level.dimension(), placed, previous));
		setDirty();
	}

	public void remove(UUID owner, BlockPos pos) {
		Map<Long, Pending> m = owner == null ? null : byEvent.get(owner);
		if (m != null && m.remove(pos.asLong()) != null) {
			if (m.isEmpty()) {
				byEvent.remove(owner);
			}
			setDirty();
		}
	}

	/** How many blocks are still owed back for {@code owner} (tests). */
	public int pending(UUID owner) {
		Map<Long, Pending> m = byEvent.get(owner);
		return m == null ? 0 : m.size();
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 200 != 37) {
			return;
		}
		get(server).sweep(server);
	}

	/** Puts back every block owed by an assault that no longer exists, where its chunk is loaded. */
	public void sweep(MinecraftServer server) {
		if (byEvent.isEmpty()) {
			return;
		}
		EventSavedData events = EventSavedData.get(server);
		for (Iterator<Map.Entry<UUID, Map<Long, Pending>>> it = byEvent.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Map<Long, Pending>> ev = it.next();
			var live = events.byId(ev.getKey());
			if (live != null && live.state().active()) {
				continue;
			}
			for (Iterator<Map.Entry<Long, Pending>> bt = ev.getValue().entrySet().iterator(); bt.hasNext();) {
				Map.Entry<Long, Pending> b = bt.next();
				ServerLevel level = server.getLevel(b.getValue().dim());
				BlockPos pos = BlockPos.of(b.getKey());
				if (level == null || !level.isLoaded(pos)) {
					continue;
				}
				if (level.getBlockState(pos).is(b.getValue().placed().getBlock())) {
					level.setBlock(pos, b.getValue().previous(), Block.UPDATE_ALL);
				}
				bt.remove();
			}
			if (ev.getValue().isEmpty()) {
				it.remove();
			}
			setDirty();
		}
	}

	// ---------------------------------------------------------------- save

	private static FrostCleanup load(CompoundTag tag, HolderLookup.Provider registries) {
		FrostCleanup data = new FrostCleanup();
		var blocks = registries.lookup(Registries.BLOCK).map(l -> (net.minecraft.core.HolderGetter<Block>) l)
				.orElse(BuiltInRegistries.BLOCK.asLookup());
		ListTag events = tag.getList("Events", Tag.TAG_COMPOUND);
		for (int i = 0; i < events.size(); i++) {
			CompoundTag ev = events.getCompound(i);
			if (!ev.hasUUID("Id")) {
				continue;
			}
			Map<Long, Pending> m = new HashMap<>();
			ListTag list = ev.getList("Blocks", Tag.TAG_COMPOUND);
			for (int j = 0; j < list.size(); j++) {
				CompoundTag b = list.getCompound(j);
				ResourceLocation dim = ResourceLocation.tryParse(b.getString("D"));
				ResourceKey<Level> key = dim == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, dim);
				m.put(b.getLong("P"), new Pending(key, NbtUtils.readBlockState(blocks, b.getCompound("S")),
						NbtUtils.readBlockState(blocks, b.getCompound("O"))));
			}
			if (!m.isEmpty()) {
				data.byEvent.put(ev.getUUID("Id"), m);
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag events = new ListTag();
		for (Map.Entry<UUID, Map<Long, Pending>> ev : byEvent.entrySet()) {
			CompoundTag t = new CompoundTag();
			t.putUUID("Id", ev.getKey());
			ListTag list = new ListTag();
			for (Map.Entry<Long, Pending> b : ev.getValue().entrySet()) {
				CompoundTag bt = new CompoundTag();
				bt.putLong("P", b.getKey());
				bt.putString("D", b.getValue().dim().location().toString());
				bt.put("S", NbtUtils.writeBlockState(b.getValue().placed()));
				bt.put("O", NbtUtils.writeBlockState(b.getValue().previous()));
				list.add(bt);
			}
			t.put("Blocks", list);
			events.add(t);
		}
		tag.put("Events", events);
		return tag;
	}
}
