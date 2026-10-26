package com.projecthero.mod.miraculous.multimouse.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.1: Multimouse's entities -- the mini copies of her (Multitude and Mouse Swarm). Never saved. */
public final class MultimouseEntityTypes {
	/** Full player size here; the copies are shrunk by their generic.scale attribute ({@code MultimouseConfig.MINI_SCALE}). */
	public static final EntityType<MiniMouseEntity> MINI = register("multimouse_mini",
			EntityType.Builder.<MiniMouseEntity>of(MiniMouseEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).noSave().noSummon().clientTrackingRange(10).updateInterval(2)
					.build("multimouse_mini"));

	private MultimouseEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(MINI, MiniMouseEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
