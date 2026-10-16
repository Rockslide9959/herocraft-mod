package com.projecthero.mod.hero.power.p05;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Geokinesis' own entities (v0.16.5 rework): the Rock Shot stone (and boulder shards), the Boulder Toss boulder and the
 * raised earth forms (grip hands, pillars, spikes, the quake ripple, the sinkhole cave-in). None is ever saved. The old
 * v0.10 Colossal Rock is gone.
 */
public final class GeoEntityTypes {
	/** R: a stone ripped out of the ground; also a boulder's shards. Updated every tick for the rise out of the ground. */
	public static final EntityType<GeoRockEntity> GEO_ROCK = register("geo_rock",
			EntityType.Builder.<GeoRockEntity>of(GeoRockEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.build("geo_rock"));

	/** Shift+R: the lifted / thrown boulder. */
	public static final EntityType<GeoBoulderEntity> GEO_BOULDER = register("geo_boulder",
			EntityType.Builder.<GeoBoulderEntity>of(GeoBoulderEntity::new, MobCategory.MISC)
					.sized(1.6f, 1.6f)
					.clientTrackingRange(10)
					.updateInterval(1)
					.noSave()
					.build("geo_boulder"));

	/** The raised earth shapes ({@link GeoFormEntity}). */
	public static final EntityType<GeoFormEntity> GEO_FORM = register("geo_form",
			EntityType.Builder.<GeoFormEntity>of(GeoFormEntity::new, MobCategory.MISC)
					.sized(1.0f, 1.0f)
					.clientTrackingRange(10)
					.updateInterval(2)
					.noSave()
					.noSummon()
					.build("geo_form"));

	private GeoEntityTypes() {
	}

	public static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
