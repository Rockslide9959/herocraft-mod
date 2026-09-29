package com.projecthero.mod.hero.revamp.batcha;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.13.22 revamp batch A entities: the chunk of block / boulder Super Strength holds and hurls. */
public final class BatchAEntities {
	public static EntityType<ThrownChunkEntity> THROWN_CHUNK;

	private BatchAEntities() {
	}

	/** Registers the entity types (once, from {@code RevampBatchA.init}). */
	public static void register() {
		if (THROWN_CHUNK != null) {
			return;
		}
		THROWN_CHUNK = Registry.register(BuiltInRegistries.ENTITY_TYPE,
				ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("thrown_chunk")),
				EntityType.Builder.<ThrownChunkEntity>of(ThrownChunkEntity::new, MobCategory.MISC)
						.sized(0.9f, 0.9f)
						.clientTrackingRange(8)
						.updateInterval(1)
						.noSave()
						.noSummon()
						.fireImmune()
						.build("thrown_chunk"));
	}
}
