package com.projecthero.mod.hero.power.p24;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Wind Manipulation's entities (v0.16.5 rework): the travelling / rideable tornado, the Wind Blade crescent and the
 * short-lived wind effects (Vacuum vortex, Wind Push cone, Tailwind streaks, Updraft column). Registered from
 * {@code RevampBatchC.init}. None of them is ever saved.
 */
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

	public static final EntityType<WindBladeEntity> BLADE = register("wind_blade",
			EntityType.Builder.<WindBladeEntity>of(WindBladeEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("wind_blade"));

	public static final EntityType<WindFxEntity> FX = register("wind_fx",
			EntityType.Builder.<WindFxEntity>of(WindFxEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("wind_fx"));

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
