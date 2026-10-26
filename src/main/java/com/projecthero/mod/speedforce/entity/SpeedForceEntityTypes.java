package com.projecthero.mod.speedforce.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.1: the Speed Force's entities -- the memory road's doorways and echoes, and the Black Flash. None is ever saved. */
public final class SpeedForceEntityTypes {
	public static final EntityType<SpeedForceDoorwayEntity> DOORWAY = register("speed_force_doorway",
			EntityType.Builder.<SpeedForceDoorwayEntity>of(SpeedForceDoorwayEntity::new, MobCategory.MISC)
					.sized(5.0f, 6.5f).clientTrackingRange(10).updateInterval(20).noSave().noSummon().build("speed_force_doorway"));

	public static final EntityType<SpeedForceEchoEntity> ECHO = register("speed_force_echo",
			EntityType.Builder.<SpeedForceEchoEntity>of(SpeedForceEchoEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f).clientTrackingRange(10).updateInterval(1).noSave().noSummon().build("speed_force_echo"));

	public static final EntityType<BlackFlashEntity> BLACK_FLASH = register("black_flash",
			EntityType.Builder.<BlackFlashEntity>of(BlackFlashEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(12).updateInterval(1).noSave().fireImmune().build("black_flash"));

	private SpeedForceEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(BLACK_FLASH, BlackFlashEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
