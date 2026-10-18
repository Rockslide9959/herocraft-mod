package com.projecthero.mod.miraculous.carapace.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.6: Carapace's one entity -- the thrown Shield. Never saved ({@code noSave}). */
public final class CarapaceEntityTypes {
	public static final EntityType<ShieldThrowEntity> SHIELD = register("carapace_shield",
			EntityType.Builder.<ShieldThrowEntity>of(ShieldThrowEntity::new, MobCategory.MISC)
					.sized(0.8f, 0.3f).noSave().noSummon().clientTrackingRange(8).updateInterval(1)
					.build("carapace_shield"));

	private CarapaceEntityTypes() {
	}

	public static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
