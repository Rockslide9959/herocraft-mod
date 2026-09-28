package com.projecthero.mod.oathbreaker.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** The Oathbreaker's own entity type -- kept out of {@code ModEntityTypes}, matching how every other
 * boss in this mod owns its registrations ({@code BehemothEntityTypes}, {@code TitanEntityTypes}, ...). */
public final class OathbreakerEntityTypes {
	public static final EntityType<OathbreakerEntity> OATHBREAKER = register("oathbreaker",
			EntityType.Builder.<OathbreakerEntity>of(OathbreakerEntity::new, MobCategory.MONSTER)
					.sized(OathbreakerEntity.WIDTH, OathbreakerEntity.HEIGHT)
					.eyeHeight(OathbreakerEntity.HEIGHT * 0.9f)
					.clientTrackingRange(12)
					.updateInterval(3)
					.build("oathbreaker"));

	private OathbreakerEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(OATHBREAKER, OathbreakerEntity.createAttributes());
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
