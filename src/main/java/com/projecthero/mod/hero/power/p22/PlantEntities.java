package com.projecthero.mod.hero.power.p22;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.13.22 (batch E): Plant Manipulation's entities -- the Thorn Sentry turret and the thorns it fires. */
public final class PlantEntities {
	public static final EntityType<ThornSentryEntity> THORN_SENTRY = register("thorn_sentry",
			EntityType.Builder.<ThornSentryEntity>of(ThornSentryEntity::new, MobCategory.MISC)
					.sized(0.8f, 1.3f)
					.clientTrackingRange(10)
					.updateInterval(2)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("thorn_sentry"));

	public static final EntityType<ThornProjectile> THORN = register("thorn",
			EntityType.Builder.<ThornProjectile>of(ThornProjectile::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.build("thorn"));

	/** v0.17.1: the code-drawn plant effects (vines, roots, spores, blooms, the Overgrowth wave, the Root Sense ring). */
	public static final EntityType<ChloroFxEntity> FX = register("chloro_fx",
			EntityType.Builder.<ChloroFxEntity>of(ChloroFxEntity::new, MobCategory.MISC)
					.sized(0.4f, 0.4f)
					.clientTrackingRange(10)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("chloro_fx"));

	/** v0.17.1 Shift+Z: the walking tree ally. */
	public static final EntityType<TreantEntity> TREANT = register("treant",
			EntityType.Builder.<TreantEntity>of(TreantEntity::new, MobCategory.MISC)
					.sized(0.85f, 2.45f)
					.clientTrackingRange(10)
					.noSave()
					.noSummon()
					.build("treant"));

	private PlantEntities() {
	}

	/** Class-load hook (registration happens in the static initialisers); v0.17.1 also the Treant's attributes. */
	public static void initialize() {
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(TREANT, TreantEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
