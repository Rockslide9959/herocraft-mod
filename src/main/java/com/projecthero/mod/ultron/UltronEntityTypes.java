package com.projecthero.mod.ultron;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ultron.entity.UltronDroneEntity;
import com.projecthero.mod.ultron.entity.UltronHeavyEntity;
import com.projecthero.mod.ultron.entity.UltronPrimeEntity;
import com.projecthero.mod.ultron.entity.UltronPylonEntity;
import com.projecthero.mod.ultron.entity.UltronSentinelDroneEntity;
import com.projecthero.mod.ultron.entity.UltronSentryEntity;
import com.projecthero.mod.ultron.entity.UltronSniperEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * v0.15.12: the Ultron Uprising's entities. The robots are sized here at a player's height; the SCALE attribute grows
 * the hit-box and the model together (Heavy 1.3, Prime 1.4, Sentry 3.4 -- about 6 blocks), so the constructors call
 * {@code refreshDimensions()}.
 */
public final class UltronEntityTypes {
	public static final EntityType<UltronDroneEntity> DRONE = register("ultron_drone",
			EntityType.Builder.<UltronDroneEntity>of(UltronDroneEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("ultron_drone"));

	public static final EntityType<UltronSentinelDroneEntity> SENTINEL_DRONE = register("ultron_sentinel_drone",
			EntityType.Builder.<UltronSentinelDroneEntity>of(UltronSentinelDroneEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("ultron_sentinel_drone"));

	public static final EntityType<UltronHeavyEntity> HEAVY = register("ultron_heavy",
			EntityType.Builder.<UltronHeavyEntity>of(UltronHeavyEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("ultron_heavy"));

	public static final EntityType<UltronSniperEntity> SNIPER = register("ultron_sniper",
			EntityType.Builder.<UltronSniperEntity>of(UltronSniperEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(12).build("ultron_sniper"));

	public static final EntityType<UltronPrimeEntity> PRIME = register("ultron_prime",
			EntityType.Builder.<UltronPrimeEntity>of(UltronPrimeEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).fireImmune().clientTrackingRange(16).updateInterval(2).build("ultron_prime"));

	public static final EntityType<UltronSentryEntity> SENTRY = register("ultron_sentry",
			EntityType.Builder.<UltronSentryEntity>of(UltronSentryEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).fireImmune().clientTrackingRange(16).updateInterval(2).build("ultron_sentry"));

	public static final EntityType<UltronPylonEntity> PYLON = register("ultron_pylon",
			EntityType.Builder.<UltronPylonEntity>of(UltronPylonEntity::new, MobCategory.MONSTER)
					.sized(1.4f, 4.2f).eyeHeight(3.75f).fireImmune().clientTrackingRange(16).build("ultron_pylon"));

	private UltronEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(DRONE, UltronDroneEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SENTINEL_DRONE, UltronSentinelDroneEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(HEAVY, UltronHeavyEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SNIPER, UltronSniperEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(PRIME, UltronPrimeEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SENTRY, UltronSentryEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(PYLON, UltronPylonEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
