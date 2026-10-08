package com.projecthero.mod.carnage;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.carnage.entity.CarnageAttackEntity;
import com.projecthero.mod.carnage.entity.CarnageEntity;
import com.projecthero.mod.carnage.entity.CrimsonMeteorEntity;
import com.projecthero.mod.carnage.entity.CrimsonSpawnEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.14.25: Carnage, his brood and his meteor. Sizes are the player's; the SCALE attribute does the rest. */
public final class CarnageEntityTypes {
	public static final EntityType<CarnageEntity> CARNAGE = register("carnage",
			EntityType.Builder.<CarnageEntity>of(CarnageEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(12).build("carnage"));

	public static final EntityType<CrimsonSpawnEntity> CRIMSON_SPAWN = register("crimson_spawn",
			EntityType.Builder.<CrimsonSpawnEntity>of(CrimsonSpawnEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("crimson_spawn"));

	public static final EntityType<CrimsonMeteorEntity> CRIMSON_METEOR = register("crimson_meteor",
			EntityType.Builder.<CrimsonMeteorEntity>of(CrimsonMeteorEntity::new, MobCategory.MISC)
					.sized(1.0f, 1.0f).clientTrackingRange(16).updateInterval(1).noSave().build("crimson_meteor"));

	/** v0.15.15: his newer moves out in the world (spikes, shockwave, goo glob, snare). */
	public static final EntityType<CarnageAttackEntity> CARNAGE_ATTACK = register("carnage_attack",
			EntityType.Builder.<CarnageAttackEntity>of(CarnageAttackEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(1).noSave().build("carnage_attack"));

	private CarnageEntityTypes() {
	}

	static void initialize() {
		FabricDefaultAttributeRegistry.register(CARNAGE, CarnageEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(CRIMSON_SPAWN, CrimsonSpawnEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
