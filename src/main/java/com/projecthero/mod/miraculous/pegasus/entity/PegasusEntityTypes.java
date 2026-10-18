package com.projecthero.mod.miraculous.pegasus.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.6: Pegasus's entities -- the portals and the thrown horseshoes. Never saved ({@code noSave}). */
public final class PegasusEntityTypes {
	public static final EntityType<PegasusPortalEntity> PORTAL = register("pegasus_portal",
			EntityType.Builder.<PegasusPortalEntity>of(PegasusPortalEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(1)
					.build("pegasus_portal"));
	public static final EntityType<HorseshoeEntity> HORSESHOE = register("pegasus_horseshoe",
			EntityType.Builder.<HorseshoeEntity>of(HorseshoeEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.25f).noSave().noSummon().fireImmune().clientTrackingRange(8).updateInterval(1)
					.build("pegasus_horseshoe"));

	private PegasusEntityTypes() {
	}

	public static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
