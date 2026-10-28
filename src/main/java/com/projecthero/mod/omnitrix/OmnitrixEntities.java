package com.projecthero.mod.omnitrix;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.omnitrix.entity.OmnitrixMeteorEntity;
import com.projecthero.mod.omnitrix.entity.OmnitrixPodEntity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.1: the Omnitrix meteor, the fallen pod and the shared alien projectile. */
public final class OmnitrixEntities {
	public static final EntityType<OmnitrixMeteorEntity> OMNITRIX_METEOR = register("omnitrix_meteor",
			EntityType.Builder.<OmnitrixMeteorEntity>of(OmnitrixMeteorEntity::new, MobCategory.MISC)
					.sized(1.0f, 1.0f).fireImmune().clientTrackingRange(16).updateInterval(1).build("omnitrix_meteor"));

	public static final EntityType<OmnitrixPodEntity> OMNITRIX_POD = register("omnitrix_pod",
			EntityType.Builder.<OmnitrixPodEntity>of(OmnitrixPodEntity::new, MobCategory.MISC)
					.sized(1.1f, 1.3f).fireImmune().clientTrackingRange(10).updateInterval(5).build("omnitrix_pod"));

	public static final EntityType<AlienProjectileEntity> ALIEN_PROJECTILE = register("alien_projectile",
			EntityType.Builder.<AlienProjectileEntity>of(AlienProjectileEntity::new, MobCategory.MISC)
					.sized(0.4f, 0.4f).fireImmune().noSave().clientTrackingRange(8).updateInterval(1).build("alien_projectile"));

	private OmnitrixEntities() {
	}

	static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
