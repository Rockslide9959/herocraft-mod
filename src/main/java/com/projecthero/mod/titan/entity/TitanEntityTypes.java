package com.projecthero.mod.titan.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The Titan boss's own entities. Kept out of {@code ModEntityTypes}/{@code RaidEntityTypes} so this
 * feature owns its registrations, matching how every other major system in this mod is organised.
 *
 * <p>None of these appear in any biome's spawn list -- {@link DisguisedTitanEntity} is placed
 * explicitly by {@link com.projecthero.mod.titan.TitanSpawner}, {@link TitanEntity} only ever by
 * {@link DisguisedTitanEntity#completeTransformation}, and the boulder only by
 * {@link TitanEntity}'s own boulder-throw attack.
 */
public final class TitanEntityTypes {
	public static final EntityType<DisguisedTitanEntity> DISGUISED_TITAN = register("titan_disguised",
			EntityType.Builder.<DisguisedTitanEntity>of(DisguisedTitanEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.74f)
					.clientTrackingRange(10)
					.build("titan_disguised"));

	public static final EntityType<TitanEntity> TITAN = register("titan",
			EntityType.Builder.<TitanEntity>of(TitanEntity::new, MobCategory.MONSTER)
					.sized(0.6f * TitanEntity.SCALE, 1.95f * TitanEntity.SCALE)
					.eyeHeight(1.74f * TitanEntity.SCALE)
					// A giant boss has to stay visible (and its boss bar accurate) from far across a field.
					.clientTrackingRange(24)
					.fireImmune()
					.build("titan"));

	public static final EntityType<TitanBoulderEntity> TITAN_BOULDER = register("titan_boulder",
			EntityType.Builder.<TitanBoulderEntity>of(TitanBoulderEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.clientTrackingRange(10)
					.updateInterval(2)
					.noSave()
					.build("titan_boulder"));

	private TitanEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(DISGUISED_TITAN, DisguisedTitanEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(TITAN, TitanEntity.createAttributes());
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
