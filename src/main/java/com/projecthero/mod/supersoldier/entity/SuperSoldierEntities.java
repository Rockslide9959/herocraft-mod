package com.projecthero.mod.supersoldier.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.14.8 Super Soldier entity types: the thrown Soldier's Shield (G). */
public final class SuperSoldierEntities {
	public static EntityType<SoldierShieldEntity> SOLDIER_SHIELD;

	private SuperSoldierEntities() {
	}

	public static void initialize() {
		SOLDIER_SHIELD = Registry.register(BuiltInRegistries.ENTITY_TYPE,
				ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("soldier_shield")),
				FabricEntityTypeBuilder.<SoldierShieldEntity>create(MobCategory.MISC, SoldierShieldEntity::new)
						.dimensions(EntityDimensions.fixed(0.7f, 0.25f))
						.trackRangeBlocks(80)
						.trackedUpdateRate(1)
						.disableSummon()
						.build());
	}
}
