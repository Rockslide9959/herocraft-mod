package com.projecthero.mod.hulk.gladiator;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.15.3: the Gladiator Hulk's thrown weapons. Never saved, never summoned by hand. */
public final class GladiatorEntities {
	public static final EntityType<GladiatorAxeEntity> THROWN_AXE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("thrown_gladiator_axe")),
			EntityType.Builder.<GladiatorAxeEntity>of(GladiatorAxeEntity::new, MobCategory.MISC)
					.sized(0.9f, 0.9f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("thrown_gladiator_axe"));

	public static final EntityType<GladiatorHammerEntity> THROWN_HAMMER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("thrown_gladiator_hammer")),
			EntityType.Builder.<GladiatorHammerEntity>of(GladiatorHammerEntity::new, MobCategory.MISC)
					.sized(1.0f, 1.0f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("thrown_gladiator_hammer"));

	private GladiatorEntities() {
	}

	public static void initialize() {
		// registration happens in the static initialiser
	}
}
