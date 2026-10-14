package com.projecthero.mod.hulk.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.13.14: the Hulk's entities -- the chunk of earth he throws. v0.16.4: the mirror Hulk and the kneeling body of the Gamma origin. */
public final class HulkEntities {
	public static final EntityType<HulkBoulderEntity> BOULDER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("hulk_boulder")),
			EntityType.Builder.<HulkBoulderEntity>of(HulkBoulderEntity::new, MobCategory.MISC)
					.sized(2.0f, 1.8f)
					.clientTrackingRange(10)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("hulk_boulder"));
	/** v0.16.4 (user): the Hulk in the mirror of a player's mind (HulkMind). Never saved. */
	public static final EntityType<MirrorHulkEntity> MIRROR_HULK = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("mirror_hulk")),
			EntityType.Builder.<MirrorHulkEntity>of(MirrorHulkEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("mirror_hulk"));

	/** v0.16.4 (user): the body a player leaves kneeling in the world while he is in his mind (HulkMind). Never saved. */
	public static final EntityType<HulkMindBodyEntity> MIND_BODY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("hulk_mind_body")),
			EntityType.Builder.<HulkMindBodyEntity>of(HulkMindBodyEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f)
					.clientTrackingRange(10)
					.updateInterval(2)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("hulk_mind_body"));

	private HulkEntities() {
	}

	public static void initialize() {
		// registration happens in the static initialiser
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(MIRROR_HULK, MirrorHulkEntity.createAttributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(MIND_BODY, HulkMindBodyEntity.createAttributes());
		com.projecthero.mod.hulk.gladiator.GladiatorEntities.initialize(); // v0.15.3: the thrown axe and hammer
	}
}
