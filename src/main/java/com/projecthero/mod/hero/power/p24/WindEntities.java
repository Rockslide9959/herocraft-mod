package com.projecthero.mod.hero.power.p24;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Wind Manipulation's entities (v0.13.22): the rideable tornado (H). Registered from {@code RevampBatchC.init}. */
public final class WindEntities {
	public static final EntityType<WindTornadoEntity> TORNADO = register("wind_tornado",
			EntityType.Builder.<WindTornadoEntity>of(WindTornadoEntity::new, MobCategory.MISC)
					.sized(2.4f, 4.6f)
					.clientTrackingRange(10)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("wind_tornado"));

	private WindEntities() {
	}

	/** Referencing this class registers the types. */
	public static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
