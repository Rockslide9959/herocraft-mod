package com.herocraft.mod.hammer;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Everything the server needs to know about one specific Mjolnir <em>without its chunk being
 * loaded</em>: who it belongs to, where it last was, what it was doing, and which physical copy of
 * it is the real one.
 *
 * <p>Immutable -- {@link MjolnirRegistry} replaces records wholesale rather than mutating them, so
 * "did anything actually change?" (and therefore whether the SavedData needs marking dirty) is a
 * single equality check.
 */
public record HammerRecord(
		UUID hammerId,
		int generation,
		Optional<UUID> owner,
		String ownerName,
		Placement placement,
		/** Whoever is carrying it, when {@link Placement#CARRIED}. Absent otherwise. */
		Optional<UUID> holder,
		ResourceKey<Level> dimension,
		BlockPos lastPos,
		/** The in-world entity's UUID while {@link Placement#ENTITY}. Absent otherwise. */
		Optional<UUID> entityId,
		MjolnirStatus status) {

	/** Where the hammer physically is. Deliberately coarse -- the fine detail lives on the entity. */
	public enum Placement {
		/** In some player's inventory or hands. */
		CARRIED,
		/** A {@code MjolnirEntity} in the world: flying, impacted, returning, or lying on the ground. */
		ENTITY,
		/** Last seen somewhere we can't cheaply re-find (a chest, a hopper, another mod's storage). */
		UNKNOWN
	}

	public static final Codec<HammerRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("hammer_id").forGetter(HammerRecord::hammerId),
			Codec.INT.fieldOf("generation").forGetter(HammerRecord::generation),
			UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(HammerRecord::owner),
			Codec.STRING.optionalFieldOf("owner_name", "").forGetter(HammerRecord::ownerName),
			Codec.STRING.xmap(HammerRecord::placementOf, Placement::name)
					.optionalFieldOf("placement", Placement.UNKNOWN).forGetter(HammerRecord::placement),
			UUIDUtil.CODEC.optionalFieldOf("holder").forGetter(HammerRecord::holder),
			ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION)
					.optionalFieldOf("dimension", Level.OVERWORLD).forGetter(HammerRecord::dimension),
			BlockPos.CODEC.optionalFieldOf("last_pos", BlockPos.ZERO).forGetter(HammerRecord::lastPos),
			UUIDUtil.CODEC.optionalFieldOf("entity_id").forGetter(HammerRecord::entityId),
			Codec.STRING.xmap(HammerRecord::statusOf, MjolnirStatus::name)
					.optionalFieldOf("status", MjolnirStatus.STORED).forGetter(HammerRecord::status)
	).apply(instance, HammerRecord::new));

	/** Anything unrecognised (an older save, a hand-edited file) degrades to the inert default. */
	private static Placement placementOf(String name) {
		try {
			return Placement.valueOf(name);
		} catch (IllegalArgumentException e) {
			return Placement.UNKNOWN;
		}
	}

	private static MjolnirStatus statusOf(String name) {
		try {
			return MjolnirStatus.valueOf(name);
		} catch (IllegalArgumentException e) {
			return MjolnirStatus.STORED;
		}
	}

	public HammerRecord withPlacement(Placement newPlacement, Optional<UUID> newHolder, Optional<UUID> newEntityId,
			ResourceKey<Level> newDimension, BlockPos newPos, MjolnirStatus newStatus) {
		return new HammerRecord(hammerId, generation, owner, ownerName, newPlacement, newHolder,
				newDimension, newPos, newEntityId, newStatus);
	}

	public HammerRecord withOwner(Optional<UUID> newOwner, String newOwnerName) {
		return new HammerRecord(hammerId, generation, newOwner, newOwnerName, placement, holder,
				dimension, lastPos, entityId, status);
	}

	public HammerRecord withGeneration(int newGeneration) {
		return new HammerRecord(hammerId, newGeneration, owner, ownerName, placement, holder,
				dimension, lastPos, entityId, status);
	}

	public boolean isOwnedBy(UUID player) {
		return owner.isPresent() && owner.get().equals(player);
	}
}
