package com.projecthero.mod.greenlantern.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.14.3: entity types belonging to the Green Lantern power. */
public final class GreenLanternEntityTypes {
	/**
	 * Every hard-light shape the ring throws around that is not made of blocks -- the flying fist, the war hammer,
	 * missiles, the buzzsaw, the anvil, the giant hand, chains, the launch pad, the Emerald Warrior and the bolt / beam
	 * streaks. One type, the shape in synced data; see {@link HardLightConstructEntity}.
	 */
	public static final EntityType<HardLightConstructEntity> HARD_LIGHT_CONSTRUCT = register("hard_light_construct",
			FabricEntityTypeBuilder.<HardLightConstructEntity>create(MobCategory.MISC, HardLightConstructEntity::new)
					.dimensions(EntityDimensions.fixed(0.6f, 0.6f))
					.trackRangeBlocks(96)
					.trackedUpdateRate(1)
					.fireImmune()
					.build());

	private GreenLanternEntityTypes() {
	}

	public static void initialize() {
		// Referencing this class loads it and registers the types.
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
