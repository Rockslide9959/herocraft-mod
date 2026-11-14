package com.projecthero.mod.doctorfate.helmet;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.17.4 the one authority on every player's Helmet of Fate: its current <b>generation</b> and the last place it was seen. Saved
 * with the world ({@code data/projecthero_fate_helmets.dat} on the overworld), so it knows even about helmets in unloaded chunks,
 * other dimensions and the inventories of players who are offline.
 *
 * <p><b>No duplicates:</b> summoning (H) always bumps the owner's generation ({@link #bump}) before the new helmet exists. Every
 * older copy -- a placed {@link FateHelmetEntity}, a dropped item, a stack in a chest, an ender chest, another player's or a mob's
 * inventory, in a chunk that isn't even loaded -- is from then on a ghost ({@link #isStale}) and {@link FateHelmetGuard} deletes it
 * the moment it loads, ticks or is looked at. The summon also removes the old copy at once when it's somewhere loaded, so the
 * helmet visibly flies out of it.
 *
 * <p>Not static state: per-world SavedData, so nothing here needs a {@code ServerStateReset} hook.
 */
public final class FateHelmetRegistry extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_fate_helmets";

	/** Where the helmet was last seen. */
	public enum Where {
		/** Unknown (never seen, or destroyed). */
		NONE,
		/** On its owner's head (the power's state; no physical copy exists). */
		WORN,
		/** In a player's inventory ({@code thing} = that player). */
		INVENTORY,
		/** A dropped item entity ({@code thing} = the entity). */
		ITEM_ENTITY,
		/** Placed on a block: a {@link FateHelmetEntity} ({@code thing} = the entity). */
		PLACED,
		/** In a container block ({@code pos}). */
		CONTAINER
	}

	/** One owner's helmet. {@code dim} is the dimension id ("" = unknown), {@code thing} an entity / player UUID or none. */
	public record Entry(int generation, Where where, String dim, long pos, Optional<UUID> thing) {
		static final Entry NEW = new Entry(0, Where.NONE, "", 0L, Optional.empty());

		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("generation", 0).forGetter(Entry::generation),
				Codec.STRING.optionalFieldOf("where", "NONE").xmap(FateHelmetRegistry::where, Enum::name).forGetter(Entry::where),
				Codec.STRING.optionalFieldOf("dim", "").forGetter(Entry::dim),
				Codec.LONG.optionalFieldOf("pos", 0L).forGetter(Entry::pos),
				UUIDUtil.CODEC.optionalFieldOf("thing").forGetter(Entry::thing)
		).apply(i, Entry::new));

		public BlockPos blockPos() {
			return BlockPos.of(pos);
		}
	}

	private static final SavedData.Factory<FateHelmetRegistry> FACTORY = new SavedData.Factory<>(FateHelmetRegistry::new,
			FateHelmetRegistry::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	private final Map<UUID, Entry> entries = new HashMap<>();

	static Where where(String name) {
		try {
			return Where.valueOf(name);
		} catch (IllegalArgumentException e) {
			return Where.NONE;
		}
	}

	public static FateHelmetRegistry get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static FateHelmetRegistry get(ServerLevel level) {
		return get(level.getServer());
	}

	public static FateHelmetRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
		FateHelmetRegistry r = new FateHelmetRegistry();
		ListTag list = tag.getList("Helmets", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag e = list.getCompound(i);
			if (!e.hasUUID("owner")) {
				continue;
			}
			UUID owner = e.getUUID("owner");
			Entry.CODEC.parse(NbtOps.INSTANCE, e).resultOrPartial(err -> ProjectHeroMod.LOGGER.warn("Dropping a Helmet of Fate record: {}", err))
					.ifPresent(entry -> r.entries.put(owner, entry));
		}
		return r;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
			Entry.CODEC.encodeStart(NbtOps.INSTANCE, e.getValue()).result().ifPresent(t -> {
				if (t instanceof CompoundTag c) {
					c.putUUID("owner", e.getKey());
					list.add(c);
				}
			});
		}
		tag.put("Helmets", list);
		return tag;
	}

	public Entry entry(UUID owner) {
		return entries.getOrDefault(owner, Entry.NEW);
	}

	public int generation(UUID owner) {
		return entry(owner).generation();
	}

	/** A new generation for {@code owner}: every copy that exists right now is a ghost from here on. Returns the new generation. */
	public int bump(UUID owner) {
		Entry old = entry(owner);
		int gen = old.generation() + 1;
		entries.put(owner, new Entry(gen, old.where(), old.dim(), old.pos(), old.thing()));
		setDirty();
		return gen;
	}

	/** A copy older than its owner's current generation: it was summoned away and must delete itself. */
	public boolean isStale(FateHelmetTag tag) {
		if (tag == null) {
			return false;
		}
		Entry e = entries.get(tag.owner());
		return e != null && tag.generation() < e.generation();
	}

	public boolean isStale(ItemStack stack) {
		return isStale(stack.get(FateHelmet.TAG));
	}

	/** Records where the live helmet is (written only when something changed). */
	public void note(UUID owner, Where where, ServerLevel level, BlockPos pos, UUID thing) {
		Entry old = entry(owner);
		String dim = level == null ? "" : level.dimension().location().toString();
		long p = pos == null ? 0L : pos.asLong();
		Optional<UUID> t = Optional.ofNullable(thing);
		if (old.where() == where && old.dim().equals(dim) && old.pos() == p && old.thing().equals(t)) {
			return;
		}
		entries.put(owner, new Entry(old.generation(), where, dim, p, t));
		setDirty();
	}

	/** The power was taken away: every copy becomes a ghost and the record is cleared. */
	public void retire(UUID owner) {
		Entry old = entry(owner);
		entries.put(owner, new Entry(old.generation() + 1, Where.NONE, "", 0L, Optional.empty()));
		setDirty();
	}
}
