package com.projecthero.mod.ultron.contagion.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.1: the Ultron Contagion's entities. */
public final class ContagionEntityTypes {
	public static final EntityType<UltronNexusEntity> ULTRON_NEXUS = register("ultron_nexus",
			EntityType.Builder.<UltronNexusEntity>of(UltronNexusEntity::new, MobCategory.MONSTER)
					.sized(2.2f, 9.0f).eyeHeight(7.6f).fireImmune().clientTrackingRange(16).build("ultron_nexus"));

	public static final EntityType<LiftEngineEntity> LIFT_ENGINE = register("ultron_lift_engine",
			EntityType.Builder.<LiftEngineEntity>of(LiftEngineEntity::new, MobCategory.MONSTER)
					.sized(2.0f, 6.0f).eyeHeight(5.0f).fireImmune().clientTrackingRange(16).build("ultron_lift_engine"));

	public static final EntityType<UplinkAntennaEntity> UPLINK_ANTENNA = register("ultron_uplink_antenna",
			EntityType.Builder.<UplinkAntennaEntity>of(UplinkAntennaEntity::new, MobCategory.MONSTER)
					.sized(1.4f, 7.0f).eyeHeight(6.4f).fireImmune().clientTrackingRange(16).build("ultron_uplink_antenna"));

	public static final EntityType<VibraniumCoreEntity> VIBRANIUM_CORE = register("vibranium_core",
			EntityType.Builder.<VibraniumCoreEntity>of(VibraniumCoreEntity::new, MobCategory.MISC)
					.sized(3.0f, 6.0f).fireImmune().clientTrackingRange(16).updateInterval(20).build("vibranium_core"));

	public static final EntityType<UltronOmegaEntity> ULTRON_OMEGA = register("ultron_omega",
			EntityType.Builder.<UltronOmegaEntity>of(UltronOmegaEntity::new, MobCategory.MONSTER)
					.sized(1.2f, 4.0f).eyeHeight(3.55f).fireImmune().clientTrackingRange(16).updateInterval(2).build("ultron_omega"));

	public static final EntityType<HijackedMarkEntity> HIJACKED_MARK = register("hijacked_mark",
			EntityType.Builder.<HijackedMarkEntity>of(HijackedMarkEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.9f).eyeHeight(1.62f).fireImmune().clientTrackingRange(12).build("hijacked_mark"));

	public static final EntityType<CitadelMarkerEntity> CITADEL_MARKER = register("citadel_marker",
			EntityType.Builder.<CitadelMarkerEntity>of(CitadelMarkerEntity::new, MobCategory.MISC)
					.sized(1.6f, 3.0f).fireImmune().clientTrackingRange(16).updateInterval(40).build("citadel_marker"));

	public static final EntityType<PurgeChargeEntity> PURGE_CHARGE = register("purge_charge",
			EntityType.Builder.<PurgeChargeEntity>of(PurgeChargeEntity::new, MobCategory.MISC)
					.sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10).build("purge_charge"));

	private ContagionEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(ULTRON_NEXUS, UltronNexusEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(LIFT_ENGINE, LiftEngineEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(UPLINK_ANTENNA, UplinkAntennaEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ULTRON_OMEGA, UltronOmegaEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(HIJACKED_MARK, HijackedMarkEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
