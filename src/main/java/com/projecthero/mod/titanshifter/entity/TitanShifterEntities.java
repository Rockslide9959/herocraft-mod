package com.projecthero.mod.titanshifter.entity;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.titanshifter.TitanShifterConfig;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Entity types of the Titan Shifter power. The hit-box baked in here comes from
 * {@link TitanShifterConfig} at start-up; per-type sizes are applied afterwards by
 * {@link TitanFormEntity#getDefaultDimensions}.
 */
public final class TitanShifterEntities {
	public static final EntityType<TitanFormEntity> TITAN_FORM = register("titan_form",
			EntityType.Builder.<TitanFormEntity>of(TitanFormEntity::new, MobCategory.MISC)
					.sized((float) TitanShifterConfig.stats().widthBlocks, (float) TitanShifterConfig.stats().heightBlocks)
					.eyeHeight((float) (TitanShifterConfig.stats().heightBlocks * 0.9))
					.clientTrackingRange(16)
					.updateInterval(2)
					.noSave()
					.noSummon()
					.build("titan_form"));

	private TitanShifterEntities() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(TITAN_FORM, TitanFormEntity.createAttributes());
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
