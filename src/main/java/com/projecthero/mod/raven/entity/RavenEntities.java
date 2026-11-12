package com.projecthero.mod.raven.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.3 Raven: the Dark Bolt, the Soul-Self raven and the black-energy debris (Telekinesis / Debris Storm). Never saved. */
public final class RavenEntities {
	public static final EntityType<DarkBoltEntity> DARK_BOLT = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ProjectHeroMod.id("raven_dark_bolt"),
			EntityType.Builder.<DarkBoltEntity>of(DarkBoltEntity::new, MobCategory.MISC)
					.sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1).noSave().noSummon().build("raven_dark_bolt"));

	public static final EntityType<SoulSelfEntity> SOUL_SELF = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ProjectHeroMod.id("raven_soul_self"),
			EntityType.Builder.<SoulSelfEntity>of(SoulSelfEntity::new, MobCategory.MISC)
					.sized(2.0f, 1.5f).clientTrackingRange(10).updateInterval(1).noSave().noSummon().build("raven_soul_self"));

	public static final EntityType<RavenDebrisEntity> DEBRIS = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ProjectHeroMod.id("raven_debris"),
			EntityType.Builder.<RavenDebrisEntity>of(RavenDebrisEntity::new, MobCategory.MISC)
					.sized(0.98f, 0.98f).clientTrackingRange(8).updateInterval(1).noSave().noSummon().build("raven_debris"));

	private RavenEntities() {
	}

	/** Class-load hook (registration happens in the static initialiser). */
	public static void initialize() {
	}
}
