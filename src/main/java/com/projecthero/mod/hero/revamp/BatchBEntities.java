package com.projecthero.mod.hero.revamp;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.p05.GeoRockEntity;
import com.projecthero.mod.hero.power.p06.CrystalNodeEntity;
import com.projecthero.mod.hero.power.p06.CrystalShardEntity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.13.22 batch B entity types (registered by referencing this class from {@link RevampBatchB#init}). */
public final class BatchBEntities {
	/** Geokinesis R: a chunk of the ground you stand on. */
	public static final EntityType<GeoRockEntity> GEO_ROCK = register("geo_rock",
			EntityType.Builder.<GeoRockEntity>of(GeoRockEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.clientTrackingRange(6)
					.updateInterval(10)
					.noSave()
					.build("geo_rock"));

	/** Crystalkinesis R (and the Resonance Spire's ammunition): a flying amethyst shard. */
	public static final EntityType<CrystalShardEntity> CRYSTAL_SHARD = register("crystal_shard",
			EntityType.Builder.<CrystalShardEntity>of(CrystalShardEntity::new, MobCategory.MISC)
					.sized(0.3f, 0.3f)
					.clientTrackingRange(6)
					.updateInterval(10)
					.noSave()
					.build("crystal_shard"));

	/** Crystalkinesis: a planted crystal node / Resonance Spire. Never saved. */
	public static final EntityType<CrystalNodeEntity> CRYSTAL_NODE = register("crystal_node",
			EntityType.Builder.<CrystalNodeEntity>of(CrystalNodeEntity::new, MobCategory.MISC)
					.sized(0.6f, 0.7f)
					.clientTrackingRange(8)
					.updateInterval(20)
					.noSave()
					.fireImmune()
					.build("crystal_node"));

	private BatchBEntities() {
	}

	static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
