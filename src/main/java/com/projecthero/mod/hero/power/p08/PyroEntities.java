package com.projecthero.mod.hero.power.p08;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.5 Pyrokinesis entity types (registered when {@link PyrokinesisHandlers#register} first touches this class). */
public final class PyroEntities {
	/** R Fireball / Z Meteor. */
	public static final EntityType<PyroBoltEntity> BOLT = register("pyro_bolt",
			EntityType.Builder.<PyroBoltEntity>of(PyroBoltEntity::new, MobCategory.MISC)
					.sized(0.6f, 0.6f)
					.clientTrackingRange(8)
					.updateInterval(2)
					.noSave()
					.fireImmune()
					.build("pyro_bolt"));

	/** Flame Wall, the novas, the Heat Wave and the Meteor blast. */
	public static final EntityType<PyroFxEntity> FX = register("pyro_fx",
			EntityType.Builder.<PyroFxEntity>of(PyroFxEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.clientTrackingRange(8)
					.updateInterval(20)
					.noSave()
					.fireImmune()
					.build("pyro_fx"));

	private PyroEntities() {
	}

	static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
