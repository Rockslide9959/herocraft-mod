package com.projecthero.mod.hammer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.item.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The persistent, server-wide index of every Mjolnir the server has ever seen -- one
 * {@link HammerRecord} per hammer, keyed by the hammer's {@link ModDataComponents#HAMMER_ID}.
 *
 * <p>This exists for one reason: <b>a bound Mjolnir has to answer its owner's call even when its
 * entity isn't loaded</b> (different chunk, different dimension, or the owner died three thousand
 * blocks away and respawned). {@code level.getEntity(uuid)} cannot see an unloaded entity, and
 * force-loading every chunk that happens to contain a hammer is not an option, so the hammer's
 * whereabouts are mirrored here instead and recall works off the record.
 *
 * <h2>How duplication is prevented</h2>
 * Recall only ever <em>reconstructs</em> a hammer when it cannot find the real one loaded. That is
 * the dangerous case: the original is still sitting in an unloaded chunk and will come back when
 * that chunk loads. Every physical copy therefore carries a
 * {@link ModDataComponents#HAMMER_GENERATION} alongside its id, and reconstruction
 * {@linkplain #reconstruct bumps} the authoritative generation here. From that moment, any stack or
 * entity carrying an older generation is by definition a ghost of a hammer that has already been
 * recovered, and {@link #isStale} tells its holder to delete it the next time it ticks. There is
 * exactly one valid copy of a given hammer id at any instant, and it is always the newest
 * generation.
 *
 * <p>Stored on the overworld's {@code DimensionDataStorage} (the conventional place for
 * server-global SavedData), and written only when something actually changes -- never per tick.
 */
public final class MjolnirRegistry extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_mjolnir";
	private static final String TAG_HAMMERS = "Hammers";

	private final Map<UUID, HammerRecord> records = new HashMap<>();

	/**
	 * Hoisted to a constant because {@link #get} is called on the hot path -- every ticking hammer,
	 * item and entity alike, checks {@link #isStale} against it -- and a fresh factory record per
	 * call would be pure allocation churn. Resolving the registry itself is a map lookup.
	 *
	 * <p>The {@link DataFixTypes} must not be null even though this data needs no fixing:
	 * {@code DimensionDataStorage.readTagFromDisk} calls {@code update} on it unguarded, and the
	 * resulting NPE is swallowed by the caller's blanket {@code catch} -- so a null here would not
	 * crash, it would silently return "no saved data" on <em>every</em> load and quietly throw the
	 * whole hammer index away each time the world reopened. {@code SAVED_DATA_RANDOM_SEQUENCES} is
	 * the newest saved-data type and carries no fixers that could touch this tag; the update is a
	 * no-op whenever the stored data version matches, which is every load except a Minecraft upgrade.
	 */
	private static final SavedData.Factory<MjolnirRegistry> FACTORY = new SavedData.Factory<>(
			MjolnirRegistry::new, MjolnirRegistry::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static MjolnirRegistry get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	/** Convenience for the common "I have a level, not a server" call sites. */
	public static MjolnirRegistry get(ServerLevel level) {
		return get(level.getServer());
	}

	private static MjolnirRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
		MjolnirRegistry registry = new MjolnirRegistry();
		ListTag list = tag.getList(TAG_HAMMERS, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			HammerRecord.CODEC.parse(NbtOps.INSTANCE, list.getCompound(i))
					.resultOrPartial(error -> ProjectHeroMod.LOGGER.warn("Dropping unreadable Mjolnir record: {}", error))
					.ifPresent(record -> registry.records.put(record.hammerId(), record));
		}
		return registry;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (HammerRecord record : records.values()) {
			HammerRecord.CODEC.encodeStart(NbtOps.INSTANCE, record)
					.resultOrPartial(error -> ProjectHeroMod.LOGGER.warn("Failed to save Mjolnir record: {}", error))
					.ifPresent(list::add);
		}
		tag.put(TAG_HAMMERS, list);
		return tag;
	}

	// ---------------- identity ----------------

	/**
	 * Gives a Mjolnir stack its identity if it does not have one yet, and returns that id. Called
	 * from the item's inventory tick and from the entity's first server tick, so every hammer that
	 * exists anywhere ends up identified without needing a hook on every possible way one can be
	 * created (crafting, {@code /give}, creative tab, loot, another mod).
	 *
	 * <p>A creative-mode copy of an existing hammer arrives here already carrying an id, so it is
	 * <em>not</em> re-identified; instead it is treated as another copy of the same hammer, and the
	 * generation check retires whichever copy is older. That is the behaviour we want: creative
	 * copying should not silently mint a second hammer sharing one bound owner.
	 */
	public UUID identify(ItemStack stack) {
		UUID existing = stack.get(ModDataComponents.HAMMER_ID);
		if (existing != null) {
			return existing;
		}
		UUID fresh = UUID.randomUUID();
		stack.set(ModDataComponents.HAMMER_ID, fresh);
		stack.set(ModDataComponents.HAMMER_GENERATION, generationOf(fresh));
		return fresh;
	}

	public int generationOf(UUID hammerId) {
		HammerRecord record = records.get(hammerId);
		return record == null ? 0 : record.generation();
	}

	/**
	 * Whether this physical copy has been superseded by a reconstructed one and should delete
	 * itself. A stack with no id at all is never stale (it just has not been identified yet).
	 */
	public boolean isStale(ItemStack stack) {
		UUID id = stack.get(ModDataComponents.HAMMER_ID);
		if (id == null) {
			return false;
		}
		HammerRecord record = records.get(id);
		if (record == null) {
			return false;
		}
		Integer generation = stack.get(ModDataComponents.HAMMER_GENERATION);
		return (generation == null ? 0 : generation) < record.generation();
	}

	// ---------------- record lookup ----------------

	public Optional<HammerRecord> record(UUID hammerId) {
		return Optional.ofNullable(hammerId == null ? null : records.get(hammerId));
	}

	/** Every hammer lying loose in the world (an entity, no owner) -- what the villager Seer can point at. */
	public java.util.List<HammerRecord> freeHammers() {
		java.util.List<HammerRecord> out = new java.util.ArrayList<>();
		for (HammerRecord record : records.values()) {
			if (record.placement() == HammerRecord.Placement.ENTITY && record.owner().isEmpty()) {
				out.add(record);
			}
		}
		return out;
	}

	// ---------------- record updates ----------------

	/** Records that {@code holder} has the hammer in an inventory. */
	public void noteCarried(ItemStack stack, Entity holder, boolean inHand) {
		UUID id = identify(stack);
		update(id, stack, existing -> existing.withPlacement(
				HammerRecord.Placement.CARRIED,
				Optional.of(holder.getUUID()),
				Optional.empty(),
				holder.level().dimension(),
				holder.blockPosition(),
				inHand ? MjolnirStatus.HELD : MjolnirStatus.STORED));
	}

	/** Records that the hammer is an entity in the world, in the given state. */
	public void noteEntity(ItemStack stack, Entity hammer, MjolnirStatus status) {
		UUID id = identify(stack);
		update(id, stack, existing -> existing.withPlacement(
				HammerRecord.Placement.ENTITY,
				Optional.empty(),
				Optional.of(hammer.getUUID()),
				hammer.level().dimension(),
				hammer.blockPosition(),
				status));
	}

	/** Binds (or rebinds) a hammer to a player. Passing an empty owner unbinds it. */
	public void setOwner(ItemStack stack, Optional<UUID> owner, String ownerName) {
		UUID id = identify(stack);
		update(id, stack, existing -> existing.withOwner(owner, ownerName));
	}

	/**
	 * Retires every existing physical copy of this hammer and hands back the generation the
	 * replacement must carry. The caller is responsible for actually creating that replacement --
	 * see {@link MjolnirRecall}.
	 */
	public int reconstruct(UUID hammerId) {
		HammerRecord existing = records.get(hammerId);
		int next = (existing == null ? 0 : existing.generation()) + 1;
		if (existing != null) {
			// LOST, not RETURNING: what this record now describes is the *retired* copy, whose
			// whereabouts no longer matter. The replacement writes its own ENTITY/RETURNING record on
			// its first tick, which overwrites this one.
			put(existing.withGeneration(next).withPlacement(HammerRecord.Placement.UNKNOWN,
					Optional.empty(), Optional.empty(), existing.dimension(), existing.lastPos(),
					MjolnirStatus.LOST));
		}
		return next;
	}


	// ---------------- internals ----------------

	private void update(UUID id, ItemStack stack, UnaryOperator<HammerRecord> mutator) {
		HammerRecord existing = records.get(id);
		if (existing == null) {
			existing = blank(id, stack);
		}
		put(mutator.apply(existing));

		// Stamp a generation onto a hammer that predates this system, so it is not mistaken for a
		// retired ghost of itself. Deliberately only when the component is *absent*: a copy carrying
		// an older generation is exactly what a ghost looks like, and silently promoting it here
		// would undo the duplication guarantee. Callers must therefore check {@link #isStale} before
		// recording a hammer, not after.
		if (stack.get(ModDataComponents.HAMMER_GENERATION) == null) {
			stack.set(ModDataComponents.HAMMER_GENERATION, records.get(id).generation());
		}
	}

	private HammerRecord blank(UUID id, ItemStack stack) {
		UUID boundOwner = stack.get(ModDataComponents.BOUND_OWNER);
		String boundName = stack.get(ModDataComponents.BOUND_OWNER_NAME);
		Integer generation = stack.get(ModDataComponents.HAMMER_GENERATION);
		return new HammerRecord(id, generation == null ? 0 : generation,
				Optional.ofNullable(boundOwner), boundName == null ? "" : boundName,
				HammerRecord.Placement.UNKNOWN, Optional.empty(), Level.OVERWORLD, BlockPos.ZERO,
				Optional.empty(), MjolnirStatus.STORED);
	}

	/** The single write path -- nothing is marked dirty unless the record genuinely changed. */
	private void put(HammerRecord record) {
		HammerRecord previous = records.put(record.hammerId(), record);
		if (!Objects.equals(previous, record)) {
			setDirty();
		}
	}
}
