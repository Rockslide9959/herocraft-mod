package com.projecthero.mod.miraculous.catnoir.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.6: Cat Noir's entities -- the thrown baton. */
public final class CatNoirEntityTypes {
	public static final EntityType<CatBatonEntity> THROWN_BATON = register("cat_noir_baton",
			EntityType.Builder.<CatBatonEntity>of(CatBatonEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f).fireImmune().noSave().noSummon().clientTrackingRange(8).updateInterval(1)
					.build("cat_noir_baton"));

	private CatNoirEntityTypes() {
	}

	/** Loads the class (the registration above runs). */
	public static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
