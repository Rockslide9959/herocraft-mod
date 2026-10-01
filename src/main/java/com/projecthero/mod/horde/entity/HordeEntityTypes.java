package com.projecthero.mod.horde.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * v0.14.12: the Horde blocks' own entities. None has a natural spawn: only a horde (or a command) makes them. The two
 * bosses are vanilla-sized here and grow through the {@code SCALE} attribute, which also scales their hitbox.
 */
public final class HordeEntityTypes {
	public static final EntityType<HordeSpider> HORDE_SPIDER = register("horde_spider",
			EntityType.Builder.<HordeSpider>of(HordeSpider::new, MobCategory.MONSTER)
					.sized(1.4f, 0.9f)
					.eyeHeight(0.65f)
					.clientTrackingRange(8)
					.build("horde_spider"));

	public static final EntityType<BoneTyrant> BONE_TYRANT = register("bone_tyrant",
			EntityType.Builder.<BoneTyrant>of(BoneTyrant::new, MobCategory.MONSTER)
					.sized(0.6f, 1.99f)
					.eyeHeight(1.74f)
					.clientTrackingRange(16)
					.build("bone_tyrant"));

	public static final EntityType<BroodQueen> BROOD_QUEEN = register("brood_queen",
			EntityType.Builder.<BroodQueen>of(BroodQueen::new, MobCategory.MONSTER)
					.sized(1.4f, 0.9f)
					.eyeHeight(0.65f)
					.clientTrackingRange(16)
					.build("brood_queen"));

	public static final EntityType<WebShotEntity> WEB_SHOT = register("web_shot",
			EntityType.Builder.<WebShotEntity>of(WebShotEntity::new, MobCategory.MISC)
					.sized(0.35f, 0.35f)
					.clientTrackingRange(6)
					.updateInterval(2)
					.noSave()
					.build("web_shot"));

	private HordeEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(HORDE_SPIDER, HordeSpider.createAttributes());
		FabricDefaultAttributeRegistry.register(BONE_TYRANT, BoneTyrant.createAttributes());
		FabricDefaultAttributeRegistry.register(BROOD_QUEEN, BroodQueen.createAttributes());
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
