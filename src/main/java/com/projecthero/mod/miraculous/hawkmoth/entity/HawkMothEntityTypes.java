package com.projecthero.mod.miraculous.hawkmoth.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.6: Hawk Moth's entities -- the akuma butterfly. */
public final class HawkMothEntityTypes {
	public static final EntityType<AkumaEntity> AKUMA = register("akuma",
			EntityType.Builder.<AkumaEntity>of(AkumaEntity::new, MobCategory.MISC)
					.sized(0.3f, 0.2f).fireImmune().noSave().noSummon().clientTrackingRange(8).updateInterval(1)
					.build("akuma"));

	private HawkMothEntityTypes() {
	}

	/** Loads the class (the static registration above). */
	public static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
