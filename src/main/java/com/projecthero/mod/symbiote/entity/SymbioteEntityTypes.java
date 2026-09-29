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

	/** v0.13.19: the Symbiote Spike projectile (G / Shift+G). Modelled, never saved. */
	public static final EntityType<SymbioteSpikeEntity> SPIKE = register("symbiote_spike",
			FabricEntityTypeBuilder.<SymbioteSpikeEntity>create(MobCategory.MISC, SymbioteSpikeEntity::new)
					.dimensions(EntityDimensions.fixed(0.3f, 0.3f))
					.trackRangeBlocks(80)
					.trackedUpdateRate(10)
					.build());

	/** v0.13.19: the living tendril drawn for every tendril move. Visual only, never saved. */
	public static final EntityType<SymbioteTendrilEntity> TENDRIL = register("symbiote_tendril",
			FabricEntityTypeBuilder.<SymbioteTendrilEntity>create(MobCategory.MISC, SymbioteTendrilEntity::new)
					.dimensions(EntityDimensions.fixed(0.2f, 0.2f))
					.trackRangeBlocks(80)
					.trackedUpdateRate(20)
					.disableSummon()
					.build());

	private SymbioteEntityTypes() {
	}

	public static void initialize() {
		// Referencing this class loads it and registers SYMBIOTE, SPIKE and TENDRIL.
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
