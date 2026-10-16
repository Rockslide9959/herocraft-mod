package com.projecthero.mod.ghostrider.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.5: Ghost Rider's entities -- the Hell Cycle and the Hellfire Blast bolt. */
public final class GhostRiderEntityTypes {
	public static final EntityType<HellCycleEntity> HELL_CYCLE = register("hell_cycle",
			EntityType.Builder.<HellCycleEntity>of(HellCycleEntity::new, MobCategory.MISC)
					.sized(1.1f, 1.1f).eyeHeight(0.9f).fireImmune().noSave().noSummon().clientTrackingRange(10).updateInterval(1)
					.build("hell_cycle"));
	/** v0.16.5 rework: the Hellfire Blast bolt as a real projectile. */
	public static final EntityType<HellfireBlastEntity> HELLFIRE_BLAST = register("hellfire_blast",
			EntityType.Builder.<HellfireBlastEntity>of(HellfireBlastEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f).fireImmune().noSave().noSummon().clientTrackingRange(8).updateInterval(1)
					.build("hellfire_blast"));

	private GhostRiderEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(HELL_CYCLE, HellCycleEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
