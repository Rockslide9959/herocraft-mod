package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

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
	// v0.16.6: the hold moves
	public static EntityType<WaterDragonEntity> WATER_DRAGON;
	public static EntityType<WhirlpoolEntity> WHIRLPOOL;
	public static EntityType<TsunamiEntity> TSUNAMI;
	public static EntityType<RainCallEntity> RAIN_CALL;
	public static EntityType<WaterCloneEntity> WATER_CLONE;

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
		WATER_DRAGON = type("water_dragon", WaterDragonEntity::new, 1.2f, 1.2f);
		WHIRLPOOL = type("whirlpool", WhirlpoolEntity::new, 8.0f, 1.0f);
		TSUNAMI = type("tsunami", TsunamiEntity::new, 12.0f, 6.0f);
		RAIN_CALL = type("rain_call", RainCallEntity::new, 2.0f, 2.0f);
		// the clone is a real (pathfinding, targetable) creature, drawn with its caster's skin
		WATER_CLONE = Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("water_clone")),
				EntityType.Builder.<WaterCloneEntity>of(WaterCloneEntity::new, MobCategory.MISC)
						.sized(0.6f, 1.8f)
						.eyeHeight(1.62f)
						.clientTrackingRange(8)
						.noSave()
						.noSummon()
						.fireImmune()
						.build("water_clone"));
		FabricDefaultAttributeRegistry.register(WATER_CLONE, WaterCloneEntity.createAttributes());
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
