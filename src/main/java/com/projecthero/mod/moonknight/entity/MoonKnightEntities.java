package com.projecthero.mod.moonknight.entity;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.moonknight.ability.MoonKnightCombat;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Moon Knight's entity types (Phase 3+): the Crescent Dart. */
public final class MoonKnightEntities {
	/** R: the thrown crescent dart -- boomerangs home on a miss, homes at night, sticks in for a Moon Mark. */
	public static final EntityType<CrescentDartEntity> CRESCENT_DART = register("crescent_dart",
			FabricEntityTypeBuilder.<CrescentDartEntity>create(MobCategory.MISC, CrescentDartEntity::new)
					.dimensions(EntityDimensions.fixed(0.4f, 0.2f))
					.trackRangeBlocks(80)
					.trackedUpdateRate(2)
					.disableSummon()
					.build());

	private MoonKnightEntities() {
	}

	/** Referencing this class registers the types; also hooks the Truncheon's "never lies in the world" rule. */
	public static void initialize() {
		MoonKnightCombat.initializeEvents();
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
