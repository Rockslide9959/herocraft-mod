package com.projecthero.mod.gyro.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.3 Gyro Zeppeli: the thrown Steel Ball. */
public final class GyroEntities {
	public static final EntityType<SteelBallEntity> STEEL_BALL = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ProjectHeroMod.id("gyro_steel_ball"),
			EntityType.Builder.<SteelBallEntity>of(SteelBallEntity::new, MobCategory.MISC)
					.sized(0.3f, 0.3f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.build("gyro_steel_ball"));

	private GyroEntities() {
	}

	/** Class-load hook (registration happens in the static initialiser). */
	public static void initialize() {
	}
}
