package com.projecthero.mod.event.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The Zombie Raid's own entities. Kept out of {@code ModEntityTypes} (Thor) and
 * {@code IronManEntityTypes} so each feature owns its registrations, matching how the rest of this
 * mod is organised.
 *
 * <p>All the mobs are {@link MobCategory#MONSTER} but none of them appear in any biome's spawn list:
 * they exist only because a raid, the Cursed-Zombie conversion, or a command created them. That is
 * why there is no {@code SpawnPlacements} registration here -- nothing in vanilla's natural spawner
 * ever considers them, which is also what makes "Cursed Zombies should not spawn excessively during
 * raid waves" true by construction.
 */
public final class RaidEntityTypes {
	public static final EntityType<CursedZombie> CURSED_ZOMBIE = register("cursed_zombie",
			EntityType.Builder.<CursedZombie>of(CursedZombie::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.74f)
					.clientTrackingRange(10)
					.build("cursed_zombie"));

	public static final EntityType<RaidZombie> RAID_ZOMBIE = register("raid_zombie",
			EntityType.Builder.<RaidZombie>of(RaidZombie::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.74f)
					.clientTrackingRange(8)
					.build("raid_zombie"));

	public static final EntityType<AcidZombie> ACID_ZOMBIE = register("acid_zombie",
			EntityType.Builder.<AcidZombie>of(AcidZombie::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.74f)
					.clientTrackingRange(8)
					.build("acid_zombie"));

	public static final EntityType<SwordSkeleton> SWORD_SKELETON = register("sword_skeleton",
			EntityType.Builder.<SwordSkeleton>of(SwordSkeleton::new, MobCategory.MONSTER)
					.sized(0.6f, 1.99f)
					.eyeHeight(1.74f)
					.clientTrackingRange(8)
					.build("sword_skeleton"));

	public static final EntityType<JuggernautZombie> JUGGERNAUT_ZOMBIE = register("juggernaut_zombie",
			EntityType.Builder.<JuggernautZombie>of(JuggernautZombie::new, MobCategory.MONSTER)
					.sized(0.6f * JuggernautZombie.SCALE, 1.95f * JuggernautZombie.SCALE)
					.eyeHeight(1.74f * JuggernautZombie.SCALE)
					.clientTrackingRange(10)
					.build("juggernaut_zombie"));

	public static final EntityType<EmpoweredZombie> EMPOWERED_ZOMBIE = register("empowered_zombie",
			EntityType.Builder.<EmpoweredZombie>of(EmpoweredZombie::new, MobCategory.MONSTER)
					.sized(0.6f * EmpoweredZombie.SCALE, 1.95f * EmpoweredZombie.SCALE)
					.eyeHeight(1.74f * EmpoweredZombie.SCALE)
					// A boss has to stay visible (and its boss bar accurate) from across the arena.
					.clientTrackingRange(16)
					.build("empowered_zombie"));

	public static final EntityType<PillagerSpy> PILLAGER_SPY = register("pillager_spy",
			EntityType.Builder.<PillagerSpy>of(PillagerSpy::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.7f)
					.clientTrackingRange(8)
					.build("pillager_spy"));

	public static final EntityType<AcidGlobEntity> ACID_GLOB = register("acid_glob",
			EntityType.Builder.<AcidGlobEntity>of(AcidGlobEntity::new, MobCategory.MISC)
					.sized(0.3f, 0.3f)
					.clientTrackingRange(6)
					.updateInterval(2)
					// A projectile in flight when its chunk unloads is gone; saving it would leave
					// stale globs in the world long after the raid that fired them ended.
					.noSave()
					.build("acid_glob"));

	private RaidEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(CURSED_ZOMBIE, CursedZombie.createAttributes());
		FabricDefaultAttributeRegistry.register(RAID_ZOMBIE, RaidZombie.createAttributes());
		FabricDefaultAttributeRegistry.register(ACID_ZOMBIE, AcidZombie.createAttributes());
		FabricDefaultAttributeRegistry.register(SWORD_SKELETON, SwordSkeleton.createAttributes());
		FabricDefaultAttributeRegistry.register(JUGGERNAUT_ZOMBIE, JuggernautZombie.createAttributes());
		FabricDefaultAttributeRegistry.register(EMPOWERED_ZOMBIE, EmpoweredZombie.createAttributes());
		FabricDefaultAttributeRegistry.register(PILLAGER_SPY, PillagerSpy.createAttributes());
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
