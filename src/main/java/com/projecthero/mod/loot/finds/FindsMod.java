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

	// v0.17.7 small world places (docs/WORLD_PLACES_REFERENCE.md)
	public static final StructureType<FindStructure> RUINED_HERO_BASE_TYPE = structureType(FindStructure.Kind.RUINED_HERO_BASE);
	public static final StructureType<FindStructure> PARADEMON_POD_TYPE = structureType(FindStructure.Kind.PARADEMON_POD);
	public static final StructureType<FindStructure> ABANDONED_WORKSHOP_TYPE = structureType(FindStructure.Kind.ABANDONED_WORKSHOP);
	public static final StructureType<FindStructure> SMUGGLERS_DEN_TYPE = structureType(FindStructure.Kind.SMUGGLERS_DEN);
	public static final StructureType<FindStructure> TECH_METEOR_TYPE = structureType(FindStructure.Kind.TECH_METEOR);
	public static final StructurePieceType RUINED_HERO_BASE_PIECE = pieceType("ruined_hero_base", RuinedHeroBasePiece::new);
	public static final StructurePieceType PARADEMON_POD_PIECE = pieceType("crashed_parademon_pod", CrashedParademonPodPiece::new);
	public static final StructurePieceType ABANDONED_WORKSHOP_PIECE = pieceType("abandoned_workshop", AbandonedWorkshopPiece::new);
	public static final StructurePieceType SMUGGLERS_DEN_PIECE = pieceType("smugglers_den", SmugglersDenPiece::new);
	public static final StructurePieceType TECH_METEOR_PIECE = pieceType("tech_meteor", TechMeteorPiece::new);
	/** Only ever part of a City ({@code CityStructure} adds it after the jigsaw). */
	public static final StructurePieceType ROOFTOP_STASH_PIECE = pieceType("rooftop_stash", RooftopStashPiece::new);

	public static final EntityType<SupplyDropEntity> SUPPLY_DROP = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("supply_drop")),
			EntityType.Builder.<SupplyDropEntity>of(SupplyDropEntity::new, MobCategory.MISC)
					.sized(0.98f, 0.98f).fireImmune().clientTrackingRange(10).updateInterval(1).build("supply_drop"));

	/** Structure keys, for tests and the Locator-style lookups. */
	public static final ResourceKey<Structure> CRASHED_DRONE = key("crashed_drone");
	public static final ResourceKey<Structure> HIDDEN_CACHE = key("hidden_cache");
	public static final ResourceKey<Structure> HERO_OUTPOST = key("hero_outpost");
	public static final ResourceKey<Structure> RUINED_HERO_BASE = key("ruined_hero_base");
	public static final ResourceKey<Structure> PARADEMON_POD = key("crashed_parademon_pod");
	public static final ResourceKey<Structure> ABANDONED_WORKSHOP = key("abandoned_workshop");
	public static final ResourceKey<Structure> SMUGGLERS_DEN = key("smugglers_den");
	public static final ResourceKey<Structure> TECH_METEOR = key("tech_meteor");

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
