package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.5 Water Manipulation rework: the water bodies the power makes (see {@link WaterFxEntity}). */
public final class WaterEntities {
	public static EntityType<WaterBoltEntity> WATER_BOLT;
	public static EntityType<WaterWhipEntity> WATER_WHIP;
	public static EntityType<WaterPrisonEntity> WATER_PRISON;
	public static EntityType<TidalWaveEntity> TIDAL_WAVE;
	public static EntityType<GeyserEntity> GEYSER;
	public static EntityType<HealingWaterEntity> HEALING_WATER;

	private WaterEntities() {
	}

	/** Once, from {@link WaterHandlers#register}. */
	public static void register() {
		if (WATER_BOLT != null) {
			return;
		}
		WATER_BOLT = type("water_bolt", WaterBoltEntity::new, 0.4f, 0.4f);
		WATER_WHIP = type("water_whip", WaterWhipEntity::new, 0.5f, 0.5f);
		WATER_PRISON = type("water_prison", WaterPrisonEntity::new, 2.0f, 2.0f);
		TIDAL_WAVE = type("tidal_wave", TidalWaveEntity::new, 7.0f, 3.0f);
		GEYSER = type("geyser", GeyserEntity::new, 1.6f, 11.0f);
		HEALING_WATER = type("healing_water", HealingWaterEntity::new, 1.2f, 2.2f);
	}

	private static <T extends WaterFxEntity> EntityType<T> type(String name, EntityType.EntityFactory<T> factory, float w, float h) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(name)),
				EntityType.Builder.of(factory, MobCategory.MISC)
						.sized(w, h)
						.clientTrackingRange(8)
						.updateInterval(1)
						.noSave()
						.noSummon()
						.fireImmune()
						.build(name));
	}
}
