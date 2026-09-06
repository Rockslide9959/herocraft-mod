package com.projecthero.mod.symbiote.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity types for the Symbiote upgrade. Registered separately from every other set. */
public final class SymbioteEntityTypes {
	/** The free-floating Symbiote blob a Spider-Man bonds with. Particle-only, no mesh. */
	public static final EntityType<SymbioteEntity> SYMBIOTE = register("symbiote",
			FabricEntityTypeBuilder.<SymbioteEntity>create(MobCategory.MISC, SymbioteEntity::new)
					.dimensions(EntityDimensions.fixed(0.9f, 0.9f))
					.trackRangeBlocks(64)
					.trackedUpdateRate(3)
					.fireImmune()
					.build());

	private SymbioteEntityTypes() {
	}

	public static void initialize() {
		// Referencing this class loads it and registers SYMBIOTE.
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
