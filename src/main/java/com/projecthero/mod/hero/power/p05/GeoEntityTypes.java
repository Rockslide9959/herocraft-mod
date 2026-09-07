package com.projecthero.mod.hero.power.p05;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Geokinesis' own entities. Currently just the Colossal Rock ultimate projectile. */
public final class GeoEntityTypes {
	public static final EntityType<ColossalRockEntity> COLOSSAL_ROCK = register("colossal_rock",
			EntityType.Builder.<ColossalRockEntity>of(ColossalRockEntity::new, MobCategory.MISC)
					// A genuinely large hitbox -- the boulder is huge and should barely need to be aimed.
					.sized(3.0f, 3.0f)
					.clientTrackingRange(12)
					.updateInterval(1)
					.noSave()
					.build("colossal_rock"));

	private GeoEntityTypes() {
	}

	public static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
