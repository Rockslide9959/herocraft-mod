package com.projecthero.mod.syndicate;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.syndicate.entity.KingpinEntity;
import com.projecthero.mod.syndicate.entity.SyndicateEnforcer;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;
import com.projecthero.mod.syndicate.entity.SyndicateThug;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.14.25: the Syndicate's people. None spawns naturally: a bust (or a spawn egg / command) brings them. */
public final class SyndicateEntityTypes {
	public static final EntityType<SyndicateThug> THUG = register("syndicate_thug",
			EntityType.Builder.<SyndicateThug>of(SyndicateThug::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("syndicate_thug"));

	public static final EntityType<SyndicateGunman> GUNMAN = register("syndicate_gunman",
			EntityType.Builder.<SyndicateGunman>of(SyndicateGunman::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("syndicate_gunman"));

	/** Scaled up 1.25x by his SCALE attribute (the hit-box follows). */
	public static final EntityType<SyndicateEnforcer> ENFORCER = register("syndicate_enforcer",
			EntityType.Builder.<SyndicateEnforcer>of(SyndicateEnforcer::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("syndicate_enforcer"));

	/** A broad man (0.7 wide before his 1.35x SCALE). */
	public static final EntityType<KingpinEntity> KINGPIN = register("kingpin",
			EntityType.Builder.<KingpinEntity>of(KingpinEntity::new, MobCategory.MONSTER)
					.sized(0.7f, 1.8f).eyeHeight(1.62f).clientTrackingRange(12).build("kingpin"));

	private SyndicateEntityTypes() {
	}

	static void initialize() {
		FabricDefaultAttributeRegistry.register(THUG, SyndicateThug.createAttributes());
		FabricDefaultAttributeRegistry.register(GUNMAN, SyndicateGunman.createAttributes());
		FabricDefaultAttributeRegistry.register(ENFORCER, SyndicateEnforcer.createAttributes());
		FabricDefaultAttributeRegistry.register(KINGPIN, KingpinEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
