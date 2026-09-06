package com.projecthero.mod.maxsteel.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity types belonging to the Max Steel power. Registered separately from every other set. */
public final class MaxSteelEntityTypes {
	public static final EntityType<SteelEntity> STEEL = register("steel",
			FabricEntityTypeBuilder.<SteelEntity>create(MobCategory.MISC, SteelEntity::new)
					.dimensions(EntityDimensions.fixed(0.8f, 1.8f))
					.trackRangeBlocks(64)
					.trackedUpdateRate(3)
					.fireImmune()
					.build());

	/** v0.9.2: the Turbo Blast bolt -- a fast straight energy projectile (was an instant hitscan). */
	public static final EntityType<TurboBoltEntity> TURBO_BOLT = register("turbo_bolt",
			FabricEntityTypeBuilder.<TurboBoltEntity>create(MobCategory.MISC, TurboBoltEntity::new)
					.dimensions(EntityDimensions.fixed(
							com.projecthero.mod.maxsteel.MaxSteelConfig.BLAST_PROJECTILE_SIZE,
							com.projecthero.mod.maxsteel.MaxSteelConfig.BLAST_PROJECTILE_SIZE))
					.trackRangeBlocks(64)
					.trackedUpdateRate(2)
					.fireImmune()
					.build());

	private MaxSteelEntityTypes() {
	}

	public static void initialize() {
		// Referencing this class loads it and registers STEEL.
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
