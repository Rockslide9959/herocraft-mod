package com.projecthero.mod.hulk.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.13.14: the Hulk's entities -- the chunk of earth he throws. */
public final class HulkEntities {
	public static final EntityType<HulkBoulderEntity> BOULDER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("hulk_boulder")),
			EntityType.Builder.<HulkBoulderEntity>of(HulkBoulderEntity::new, MobCategory.MISC)
					.sized(2.0f, 1.8f)
					.clientTrackingRange(10)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("hulk_boulder"));

	private HulkEntities() {
	}

	public static void initialize() {
		// registration happens in the static initialiser
	}
}
