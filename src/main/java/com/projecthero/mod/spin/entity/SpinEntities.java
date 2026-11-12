package com.projecthero.mod.spin.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.3 the Spin: the thrown Steel Ball. */
public final class SpinEntities {
	public static final EntityType<SteelBallEntity> STEEL_BALL = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ProjectHeroMod.id("spin_steel_ball"),
			EntityType.Builder.<SteelBallEntity>of(SteelBallEntity::new, MobCategory.MISC)
					.sized(0.3f, 0.3f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.build("spin_steel_ball"));

	private SpinEntities() {
	}

	/** Class-load hook (registration happens in the static initialiser). */
	public static void initialize() {
	}
}
