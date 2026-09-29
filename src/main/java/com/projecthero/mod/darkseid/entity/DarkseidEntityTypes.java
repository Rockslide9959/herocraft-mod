package com.projecthero.mod.darkseid.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Every entity the Darkseid Raid adds -- kept in its own registration class, like {@code OathbreakerEntityTypes}
 * / {@code BehemothEntityTypes}. Only {@link #DARKSEID} and {@link #PARADEMON} are mobs; the Mother Box and the
 * Boom Tube are light, attribute-less world objects, and the two projectiles are the only things that move fast
 * enough to need a tracked entity (see their class docs for why they are not just particles).
 */
public final class DarkseidEntityTypes {
	public static final EntityType<DarkseidEntity> DARKSEID = register("darkseid",
			EntityType.Builder.<DarkseidEntity>of(DarkseidEntity::new, MobCategory.MONSTER)
					.sized(DarkseidEntity.BASE_WIDTH, DarkseidEntity.BASE_HEIGHT)
					.eyeHeight(DarkseidEntity.BASE_HEIGHT * 0.9f)
					.fireImmune()
					.clientTrackingRange(16)
					.updateInterval(2)
					.build("darkseid"));

	public static final EntityType<ParademonEntity> PARADEMON = register("parademon",
			EntityType.Builder.<ParademonEntity>of(ParademonEntity::new, MobCategory.MONSTER)
					.sized(0.7f, 2.0f)
					.eyeHeight(1.75f)
					.clientTrackingRange(10)
					.build("parademon"));

	public static final EntityType<MotherBoxEntity> MOTHER_BOX = register("mother_box",
			EntityType.Builder.<MotherBoxEntity>of(MotherBoxEntity::new, MobCategory.MISC)
					.sized(1.0f, 1.0f)
					.fireImmune()
					.clientTrackingRange(12)
					.updateInterval(10)
					.build("mother_box"));

	public static final EntityType<BoomTubeEntity> BOOM_TUBE = register("boom_tube",
			EntityType.Builder.<BoomTubeEntity>of(BoomTubeEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(12)
					.updateInterval(20)
					.build("boom_tube"));

	public static final EntityType<OmegaBeamEntity> OMEGA_BEAM = register("omega_beam",
			EntityType.Builder.<OmegaBeamEntity>of(OmegaBeamEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(10)
					.updateInterval(1)
					.build("omega_beam"));

	public static final EntityType<ParademonBoltEntity> PARADEMON_BOLT = register("parademon_bolt",
			EntityType.Builder.<ParademonBoltEntity>of(ParademonBoltEntity::new, MobCategory.MISC)
					.sized(0.3f, 0.3f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(8)
					.updateInterval(1)
					.build("parademon_bolt"));

	private DarkseidEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(DARKSEID, DarkseidEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(PARADEMON, ParademonEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
