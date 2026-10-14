package com.projecthero.mod.spider.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity types belonging to the Spider-Man power. */
public final class SpiderEntityTypes {
	/** v0.12.20: the Combat Mode heavy web shot (Z). */
	public static final EntityType<ImpactWebEntity> IMPACT_WEB = register("impact_web",
			FabricEntityTypeBuilder.<ImpactWebEntity>create(MobCategory.MISC, ImpactWebEntity::new)
					.dimensions(EntityDimensions.fixed(0.5f, 0.5f))
					.trackRangeBlocks(64)
					.trackedUpdateRate(2)
					.fireImmune()
					.build());

	/** v0.16.1: a tightrope / web wall / woven web (never saved; see the class). */
	public static final EntityType<com.projecthero.mod.spider.web.WebStructureEntity> WEB_STRUCTURE = register("web_structure",
			FabricEntityTypeBuilder.<com.projecthero.mod.spider.web.WebStructureEntity>create(MobCategory.MISC,
							com.projecthero.mod.spider.web.WebStructureEntity::new)
					.dimensions(EntityDimensions.fixed(0.5f, 0.5f))
					.trackRangeBlocks(160)
					.trackedUpdateRate(20)
					.fireImmune()
					.disableSummon()
					.build());

	/** v0.16.1: a block ripped out and thrown by Grab &amp; Hurl. */
	public static final EntityType<ThrownBlockEntity> THROWN_BLOCK = register("thrown_block",
			FabricEntityTypeBuilder.<ThrownBlockEntity>create(MobCategory.MISC, ThrownBlockEntity::new)
					.dimensions(EntityDimensions.fixed(0.9f, 0.9f))
					.trackRangeBlocks(80)
					.trackedUpdateRate(1)
					.fireImmune()
					.build());

	/**
	 * v0.16.1: the Web Shot (R tap) glob. v0.16.3 FIX: was "web_shot" -- the same id as the horde spiders' web shot
	 * (HordeEntityTypes), so a joining player's client lost one of the two and was disconnected (then crashed in Fabric's
	 * registry unmap) the moment one spawned near them. Every registry id must be unique (RegistryIdGameTest).
	 */
	public static final EntityType<SpiderWebShotEntity> WEB_SHOT = register("spider_web_shot",
			FabricEntityTypeBuilder.<SpiderWebShotEntity>create(MobCategory.MISC, SpiderWebShotEntity::new)
					.dimensions(EntityDimensions.fixed(0.3f, 0.3f))
					.trackRangeBlocks(64)
					.trackedUpdateRate(1)
					.fireImmune()
					.build());

	/** v0.16.2: the Suit v2's Trip Mine, shot out like a Web Shot; it becomes a mine on the surface it hits. */
	public static final EntityType<TripMineShotEntity> TRIP_MINE_SHOT = register("trip_mine_shot",
			FabricEntityTypeBuilder.<TripMineShotEntity>create(MobCategory.MISC, TripMineShotEntity::new)
					.dimensions(EntityDimensions.fixed(0.3f, 0.3f))
					.trackRangeBlocks(64)
					.trackedUpdateRate(1)
					.fireImmune()
					.build());

	/** v0.16.1: the Web Bomb (Shift+R) glob, lobbed in an arc; bursts into the splash where it lands. */
	public static final EntityType<SpiderWebBombEntity> WEB_BOMB = register("web_bomb",
			FabricEntityTypeBuilder.<SpiderWebBombEntity>create(MobCategory.MISC, SpiderWebBombEntity::new)
					.dimensions(EntityDimensions.fixed(0.6f, 0.6f))
					.trackRangeBlocks(64)
					.trackedUpdateRate(1)
					.fireImmune()
					.build());

	/** v0.16.1: the Web Cocoon (Shift+V) ball, homing in on its target. */
	public static final EntityType<WebCocoonBallEntity> WEB_COCOON = register("web_cocoon",
			FabricEntityTypeBuilder.<WebCocoonBallEntity>create(MobCategory.MISC, WebCocoonBallEntity::new)
					.dimensions(EntityDimensions.fixed(0.4f, 0.4f))
					.trackRangeBlocks(64)
					.trackedUpdateRate(1)
					.fireImmune()
					.build());

	private SpiderEntityTypes() {
	}

	public static void initialize() {
		// Referencing this class loads it and registers IMPACT_WEB.
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
