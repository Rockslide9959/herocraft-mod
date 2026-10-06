package com.projecthero.mod.sentinel.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * v0.15.1: the Sentinel Purge's entities. The Sentinel and Master Mold are sized here at their 2-block model height; the
 * SCALE attribute (1.75 / 5.5) grows the hit-box and the model together, so the real sizes are ~3.5 and ~11 blocks.
 */
public final class SentinelEntityTypes {
	public static final EntityType<SentinelDroneEntity> SENTINEL_DRONE = register("sentinel_drone",
			EntityType.Builder.<SentinelDroneEntity>of(SentinelDroneEntity::new, MobCategory.MONSTER)
					.sized(0.9f, 0.9f)
					.eyeHeight(0.5f)
					.fireImmune()
					.clientTrackingRange(10)
					.build("sentinel_drone"));

	public static final EntityType<SentinelEntity> SENTINEL = register("sentinel",
			EntityType.Builder.<SentinelEntity>of(SentinelEntity::new, MobCategory.MONSTER)
					.sized(0.7f, 2.0f)
					.eyeHeight(1.8f)
					.fireImmune()
					.clientTrackingRange(12)
					.build("sentinel"));

	public static final EntityType<MasterMoldEntity> MASTER_MOLD = register("master_mold",
			EntityType.Builder.<MasterMoldEntity>of(MasterMoldEntity::new, MobCategory.MONSTER)
					.sized(0.9f, 2.0f)
					.eyeHeight(1.75f)
					.fireImmune()
					.clientTrackingRange(16)
					.updateInterval(2)
					.build("master_mold"));

	public static final EntityType<SentinelBeamEntity> SENTINEL_BEAM = register("sentinel_beam",
			EntityType.Builder.<SentinelBeamEntity>of(SentinelBeamEntity::new, MobCategory.MISC)
					.sized(0.3f, 0.3f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(8)
					.updateInterval(1)
					.build("sentinel_beam"));

	private SentinelEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(SENTINEL_DRONE, SentinelDroneEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SENTINEL, SentinelEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(MASTER_MOLD, MasterMoldEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
