package com.projecthero.mod.bluebeetle.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.5: Blue Beetle's entities -- the Scarab Swarm drone. */
public final class BlueBeetleEntityTypes {
	public static final EntityType<ScarabDroneEntity> SCARAB_DRONE = register("scarab_drone",
			EntityType.Builder.<ScarabDroneEntity>of(ScarabDroneEntity::new, MobCategory.MISC)
					.sized(0.4f, 0.3f).noSave().noSummon().fireImmune().clientTrackingRange(8).updateInterval(1).build("scarab_drone"));

	private BlueBeetleEntityTypes() {
	}

	public static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
