package com.projecthero.mod.galactus.entity;

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
 * v0.16.1: Galactus: The Devourer's entities. Galactus' body hit-box is 10 x 40 (his model is drawn ~60 blocks tall: render
 * scale only); his four weak points and the grabbing hand are separate small entities he moves. The Worldship is
 * render-only.
 */
public final class GalactusEntityTypes {
	public static final EntityType<GalactusEntity> GALACTUS = register("galactus",
			EntityType.Builder.<GalactusEntity>of(GalactusEntity::new, MobCategory.MONSTER)
					.sized(10.0f, 40.0f).eyeHeight(38.0f).fireImmune().clientTrackingRange(20).updateInterval(2).build("galactus"));

	public static final EntityType<GalactusWeakPointEntity> WEAK_POINT = register("galactus_weak_point",
			EntityType.Builder.<GalactusWeakPointEntity>of(GalactusWeakPointEntity::new, MobCategory.MISC)
					.sized(4.0f, 4.0f).eyeHeight(2.0f).fireImmune().noSummon().clientTrackingRange(20).updateInterval(1).build("galactus_weak_point"));

	public static final EntityType<GalactusHandEntity> HAND = register("galactus_hand",
			EntityType.Builder.<GalactusHandEntity>of(GalactusHandEntity::new, MobCategory.MISC)
					.sized(6.0f, 5.0f).eyeHeight(2.5f).fireImmune().noSummon().clientTrackingRange(20).updateInterval(1).build("galactus_hand"));

	public static final EntityType<SilverSurferEntity> SILVER_SURFER = register("silver_surfer",
			EntityType.Builder.<SilverSurferEntity>of(SilverSurferEntity::new, MobCategory.MONSTER)
					.sized(0.8f, 2.2f).eyeHeight(1.9f).fireImmune().clientTrackingRange(12).updateInterval(1).build("silver_surfer"));

	public static final EntityType<ElementConverterEntity> ELEMENT_CONVERTER = register("element_converter",
			EntityType.Builder.<ElementConverterEntity>of(ElementConverterEntity::new, MobCategory.MONSTER)
					.sized(3.0f, 10.0f).eyeHeight(8.0f).fireImmune().clientTrackingRange(16).updateInterval(2).build("element_converter"));

	public static final EntityType<PunisherRobotEntity> PUNISHER = register("punisher",
			EntityType.Builder.<PunisherRobotEntity>of(PunisherRobotEntity::new, MobCategory.MONSTER)
					.sized(2.6f, 8.0f).eyeHeight(7.0f).fireImmune().clientTrackingRange(14).updateInterval(2).build("punisher"));

	public static final EntityType<PowerPylonEntity> POWER_PYLON = register("power_pylon",
			EntityType.Builder.<PowerPylonEntity>of(PowerPylonEntity::new, MobCategory.MONSTER)
					.sized(1.6f, 6.0f).eyeHeight(5.0f).fireImmune().clientTrackingRange(16).build("power_pylon"));

	public static final EntityType<WorldshipEntity> WORLDSHIP = register("worldship",
			EntityType.Builder.<WorldshipEntity>of(WorldshipEntity::new, MobCategory.MISC)
					.sized(4.0f, 4.0f).fireImmune().noSummon().clientTrackingRange(24).updateInterval(4).build("worldship"));

	public static final EntityType<SurferBoardEntity> SURFER_BOARD = register("silver_surfer_board",
			EntityType.Builder.<SurferBoardEntity>of(SurferBoardEntity::new, MobCategory.MISC)
					.sized(1.1f, 0.3f).eyeHeight(0.2f).fireImmune().clientTrackingRange(10).updateInterval(1).build("silver_surfer_board"));

	private GalactusEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(GALACTUS, GalactusEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WEAK_POINT, GalactusWeakPointEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(HAND, GalactusHandEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SILVER_SURFER, SilverSurferEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ELEMENT_CONVERTER, ElementConverterEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(PUNISHER, PunisherRobotEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(POWER_PYLON, PowerPylonEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SURFER_BOARD, SurferBoardEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
