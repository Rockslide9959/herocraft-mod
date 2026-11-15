package com.projecthero.mod.loot.finds;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * v0.17.5 Tier 1 Finds: the three common world-flavour structures (Crashed Drone, Hidden Cache, Abandoned Hero
 * Outpost) and the Supply Drop event. Registers its own structure types, piece types, the crate entity, the two game
 * rules and the operator command, so the shared worldgen registries stay untouched. See
 * {@code docs/LOOT_PROGRESSION_REFERENCE.md} (Tier 1 Finds).
 */
public final class FindsMod {
	public static final StructureType<FindStructure> CRASHED_DRONE_TYPE = structureType(FindStructure.Kind.CRASHED_DRONE);
	public static final StructureType<FindStructure> HIDDEN_CACHE_TYPE = structureType(FindStructure.Kind.HIDDEN_CACHE);
	public static final StructureType<FindStructure> HERO_OUTPOST_TYPE = structureType(FindStructure.Kind.HERO_OUTPOST);

	public static final StructurePieceType CRASHED_DRONE_PIECE = pieceType("crashed_drone", CrashedDronePiece::new);
	public static final StructurePieceType HIDDEN_CACHE_PIECE = pieceType("hidden_cache", HiddenCachePiece::new);
	public static final StructurePieceType HERO_OUTPOST_PIECE = pieceType("hero_outpost", HeroOutpostPiece::new);

	public static final EntityType<SupplyDropEntity> SUPPLY_DROP = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("supply_drop")),
			EntityType.Builder.<SupplyDropEntity>of(SupplyDropEntity::new, MobCategory.MISC)
					.sized(0.98f, 0.98f).fireImmune().clientTrackingRange(10).updateInterval(1).build("supply_drop"));

	/** Structure keys, for tests and the Locator-style lookups. */
	public static final ResourceKey<Structure> CRASHED_DRONE = key("crashed_drone");
	public static final ResourceKey<Structure> HIDDEN_CACHE = key("hidden_cache");
	public static final ResourceKey<Structure> HERO_OUTPOST = key("hero_outpost");

	private FindsMod() {
	}

	public static void initialize() {
		SupplyDrops.initialize();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SupplyDrops.registerCommand(dispatcher));
	}

	private static ResourceKey<Structure> key(String path) {
		return ResourceKey.create(Registries.STRUCTURE, ProjectHeroMod.id(path));
	}

	private static StructureType<FindStructure> structureType(FindStructure.Kind kind) {
		MapCodec<FindStructure> codec = FindStructure.codec(kind);
		ResourceKey<StructureType<?>> key = ResourceKey.create(Registries.STRUCTURE_TYPE, ProjectHeroMod.id(kind.id));
		return Registry.register(BuiltInRegistries.STRUCTURE_TYPE, key, () -> codec);
	}

	private static StructurePieceType pieceType(String path, StructurePieceType type) {
		return Registry.register(BuiltInRegistries.STRUCTURE_PIECE, ResourceKey.create(Registries.STRUCTURE_PIECE, ProjectHeroMod.id(path)), type);
	}
}
