package com.projecthero.mod.falcon.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.1: the Falcon's entity -- Redwing, his drone (never saved; it follows its owner while he is the Falcon). */
public final class FalconEntityTypes {
	public static final EntityType<RedwingEntity> REDWING = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("redwing")),
			EntityType.Builder.<RedwingEntity>of(RedwingEntity::new, MobCategory.MISC)
					.sized(0.6f, 0.3f).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(1).build("redwing"));

	private FalconEntityTypes() {
	}

	public static void initialize() {
	}
}
