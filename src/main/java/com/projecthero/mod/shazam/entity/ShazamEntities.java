package com.projecthero.mod.shazam.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.3 Shazam: the Wizard Shazam NPC (a player-shaped mob in his own skin). */
public final class ShazamEntities {
	public static final EntityType<WizardShazamEntity> WIZARD = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ProjectHeroMod.id("wizard_shazam"),
			EntityType.Builder.<WizardShazamEntity>of(WizardShazamEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.noSave()
					.build("wizard_shazam"));

	private ShazamEntities() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(WIZARD, WizardShazamEntity.createAttributes());
	}
}
