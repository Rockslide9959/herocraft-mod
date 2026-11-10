package com.projecthero.mod.stand.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.3: Star Platinum's entities -- the flicked ball bearing. */
public final class StandEntities {
	public static final EntityType<BallBearingEntity> BALL_BEARING = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ProjectHeroMod.id("ball_bearing"),
			EntityType.Builder.<BallBearingEntity>of(BallBearingEntity::new, MobCategory.MISC)
					.sized(0.15f, 0.15f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.build("ball_bearing"));

	private StandEntities() {
	}

	/** Class-load hook (registration happens in the static initialiser). */
	public static void initialize() {
	}
}
