package com.herocraft.mod.entity;

import com.herocraft.mod.HeroCraftMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntityTypes {
	public static final EntityType<MjolnirEntity> MJOLNIR = register("mjolnir",
			FabricEntityTypeBuilder.<MjolnirEntity>create(MobCategory.MISC, MjolnirEntity::new)
					// Proportional to (not doubled with) the render-scale bump in mjolnir_thrown.json's
					// display transform -- big enough that a thrown hammer still reliably hits what it
					// visibly touches, deliberately not blown out to the model's full ~1-block span so
					// it can never register a hit noticeably outside the visible silhouette.
					.dimensions(EntityDimensions.scalable(0.6f, 0.6f))
					// Wide enough that a long throw stays visible all the way out and back rather
					// than the hammer vanishing at the edge of the old range and reappearing.
					.trackRangeBlocks(96)
					// Matches vanilla's arrows/tridents rather than the old every-other-tick rate.
					// MjolnirEntity runs the same simulation on both sides (see its class javadoc),
					// so frequent position packets bought nothing except a hard snap backwards on
					// every one of them -- which *was* the stutter. Corrections are now rare and,
					// when they do arrive, blended in over several ticks.
					.trackedUpdateRate(20)
					// THOR_DESIGN.md section 3: a lost Mjolnir must never be destroyed by the world
					// it lands in -- most obviously, it shouldn't sit in lava catching fire.
					.fireImmune()
					.build());

	private ModEntityTypes() {
	}

	public static void initialize() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, HeroCraftMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
