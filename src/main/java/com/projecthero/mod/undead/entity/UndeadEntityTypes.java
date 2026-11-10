package com.projecthero.mod.undead.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.3: Undead's flying pieces and his split-off selves. Neither is ever saved. */
public final class UndeadEntityTypes {
	public static final EntityType<UndeadPartEntity> PART = register("undead_part",
			EntityType.Builder.<UndeadPartEntity>of(UndeadPartEntity::new, MobCategory.MISC)
					.sized(0.35f, 0.35f).noSave().noSummon().fireImmune().clientTrackingRange(8).updateInterval(1).build("undead_part"));
	public static final EntityType<UndeadCloneEntity> CLONE = register("undead_clone",
			EntityType.Builder.<UndeadCloneEntity>of(UndeadCloneEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f).noSave().noSummon().clientTrackingRange(8).build("undead_clone"));

	private UndeadEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(CLONE, UndeadCloneEntity.attributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
