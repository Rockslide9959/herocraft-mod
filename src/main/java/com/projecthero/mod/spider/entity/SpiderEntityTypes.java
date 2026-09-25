package com.projecthero.mod.spider.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity types belonging to the Spider-Man power. */
public final class SpiderEntityTypes {
	/** v0.12.20: the Combat Mode heavy web shot (Z). */
	public static final EntityType<ImpactWebEntity> IMPACT_WEB = register("impact_web",
			FabricEntityTypeBuilder.<ImpactWebEntity>create(MobCategory.MISC, ImpactWebEntity::new)
					.dimensions(EntityDimensions.fixed(0.5f, 0.5f))
					.trackRangeBlocks(64)
					.trackedUpdateRate(2)
					.fireImmune()
					.build());

	private SpiderEntityTypes() {
	}

	public static void initialize() {
		// Referencing this class loads it and registers IMPACT_WEB.
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
