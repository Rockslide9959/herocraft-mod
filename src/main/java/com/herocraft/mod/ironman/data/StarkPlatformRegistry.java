package com.herocraft.mod.ironman.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.herocraft.mod.HeroCraftMod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-wide index of every Iron Man Suit Platform and what it currently holds -- so a player can
 * call a suit off a platform that is in an <em>unloaded</em> chunk far away (spec "changes 9",
 * mirroring {@link com.herocraft.mod.hammer.MjolnirRegistry}'s reason to exist).
 *
 * <p>Each {@link Entry} records the platform's dimension + position, the player it is bound to (the
 * one who placed it -- this is what lets multiple Iron Man players coexist), which suit it holds and
 * which of the four pieces are actually present, and the suit's stored charge/integrity so the call
 * can restore them without the block being loaded.
 *
 * <p>The block entity is authoritative whenever it is loaded and re-pushes its state here on load /
 * change; this registry is only consulted for platforms that are <em>not</em> loaded, and a call that
 * reaches one force-loads its chunk (a one-off synchronous load in a packet-handler context, never
 * from a chunk-load callback) so the block entity itself performs the actual piece removal.
 */
public final class StarkPlatformRegistry extends SavedData {
	private static final String FILE_ID = HeroCraftMod.MOD_ID + "_stark_platforms";

	public record Entry(GlobalPos pos, Optional<UUID> owner, String suitId, int pieceMask,
			float suitEnergy, float suitIntegrity) {

		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Entry::owner),
				Codec.STRING.optionalFieldOf("suit", "").forGetter(Entry::suitId),
				Codec.INT.optionalFieldOf("pieces", 0).forGetter(Entry::pieceMask),
				Codec.FLOAT.optionalFieldOf("energy", 0f).forGetter(Entry::suitEnergy),
				Codec.FLOAT.optionalFieldOf("integrity", com.herocraft.mod.ironman.IronManEnergy.MAX_INTEGRITY)
						.forGetter(Entry::suitIntegrity)
		).apply(i, Entry::new));

		public boolean holdsAnything() {
			return !suitId.isEmpty() && pieceMask != 0;
		}

		/** Could this platform still accept pieces of {@code wantSuit}? (empty, or already that suit with a free slot) */
		public boolean hasRoomFor(String wantSuit) {
			return (suitId.isEmpty() || suitId.equals(wantSuit)) && Integer.bitCount(pieceMask) < 4;
		}

		public boolean ownedBy(UUID player) {
			return owner.isPresent() && owner.get().equals(player);
		}

		public ResourceKey<Level> dimension() {
			return pos.dimension();
		}

		public BlockPos blockPos() {
			return pos.pos();
		}
	}

	private final Map<GlobalPos, Entry> byPos = new HashMap<>();

	private static final SavedData.Factory<StarkPlatformRegistry> FACTORY = new SavedData.Factory<>(
			StarkPlatformRegistry::new, StarkPlatformRegistry::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static StarkPlatformRegistry get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static StarkPlatformRegistry get(ServerLevel level) {
		return get(level.getServer());
	}

	private static StarkPlatformRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
		StarkPlatformRegistry reg = new StarkPlatformRegistry();
		ListTag list = tag.getList("Platforms", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			Entry.CODEC.parse(NbtOps.INSTANCE, list.getCompound(i))
					.resultOrPartial(err -> HeroCraftMod.LOGGER.warn("Dropping unreadable Stark platform record: {}", err))
					.ifPresent(e -> reg.byPos.put(e.pos(), e));
		}
		return reg;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Entry e : byPos.values()) {
			Entry.CODEC.encodeStart(NbtOps.INSTANCE, e)
					.resultOrPartial(err -> HeroCraftMod.LOGGER.warn("Failed to save Stark platform record: {}", err))
					.ifPresent(list::add);
		}
		tag.put("Platforms", list);
		return tag;
	}

	// ---------------- updates (from the block entity) ----------------

	public void put(ServerLevel level, BlockPos pos, Optional<UUID> owner, String suitId, int pieceMask,
			float suitEnergy, float suitIntegrity) {
		GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
		Entry existing = byPos.get(key);
		Optional<UUID> keepOwner = owner.isPresent() ? owner : (existing != null ? existing.owner() : Optional.empty());
		Entry updated = new Entry(key, keepOwner, suitId == null ? "" : suitId, pieceMask,
				suitEnergy, suitIntegrity);
		if (!Objects.equals(existing, updated)) {
			byPos.put(key, updated);
			setDirty();
		}
	}

	public void remove(ServerLevel level, BlockPos pos) {
		if (byPos.remove(GlobalPos.of(level.dimension(), pos.immutable())) != null) {
			setDirty();
		}
	}

	// ---------------- queries (for the call menu) ----------------

	private static boolean callableBy(Entry e, UUID player) {
		// bound to you, or not bound to anyone yet (a command-placed / legacy platform -- calling it claims it)
		return e.owner().isEmpty() || e.owner().get().equals(player);
	}

	/** Every platform callable by {@code player} in {@code dimension} that currently holds a suit. */
	public List<Entry> platformsFor(UUID player, ResourceKey<Level> dimension) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : byPos.values()) {
			if (callableBy(e, player) && e.dimension() == dimension && e.holdsAnything()) {
				out.add(e);
			}
		}
		return out;
	}

	/** The nearest platform callable by {@code player} in {@code dimension} holding {@code suitId}, or empty. */
	public Optional<Entry> nearestHolding(UUID player, ResourceKey<Level> dimension, String suitId, BlockPos near) {
		Entry best = null;
		double bestSq = Double.MAX_VALUE;
		for (Entry e : byPos.values()) {
			if (!callableBy(e, player) || e.dimension() != dimension || !suitId.equals(e.suitId()) || e.pieceMask() == 0) {
				continue;
			}
			double d = e.blockPos().distSqr(near);
			if (d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return Optional.ofNullable(best);
	}

	public Optional<Entry> at(ServerLevel level, BlockPos pos) {
		return Optional.ofNullable(byPos.get(GlobalPos.of(level.dimension(), pos.immutable())));
	}

	/** The nearest platform callable by {@code player} in {@code dimension} that can still take a piece of {@code suitId}. */
	public Optional<Entry> nearestDockFor(UUID player, ResourceKey<Level> dimension, String suitId, BlockPos near) {
		Entry best = null;
		double bestSq = Double.MAX_VALUE;
		for (Entry e : byPos.values()) {
			if (!callableBy(e, player) || e.dimension() != dimension || !e.hasRoomFor(suitId)) {
				continue;
			}
			double d = e.blockPos().distSqr(near);
			if (d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return Optional.ofNullable(best);
	}
}
