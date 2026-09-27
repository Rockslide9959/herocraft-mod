package com.projecthero.mod.behemoth.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The Abyssal Behemoth's own entities. Kept out of {@code ModEntityTypes}, matching how every other
 * major boss/power in this mod owns its registrations ({@code TitanEntityTypes}, {@code MaxSteelEntityTypes}, ...).
 */
public final class BehemothEntityTypes {
	public static final EntityType<AbyssalBehemothEntity> ABYSSAL_BEHEMOTH = register("abyssal_behemoth",
			EntityType.Builder.<AbyssalBehemothEntity>of(AbyssalBehemothEntity::new, MobCategory.MONSTER)
					.sized(AbyssalBehemothEntity.SIZE, AbyssalBehemothEntity.SIZE)
					.eyeHeight(AbyssalBehemothEntity.SIZE * 0.6f)
					.fireImmune()
					.clientTrackingRange(20)
					.updateInterval(2)
					.build("abyssal_behemoth"));

	public static final EntityType<BehemothFireballEntity> BEHEMOTH_FIREBALL = register("behemoth_fireball",
			EntityType.Builder.<BehemothFireballEntity>of(BehemothFireballEntity::new, MobCategory.MISC)
					.sized(1.0f, 1.0f)
					.clientTrackingRange(20)
					.updateInterval(4)
					.fireImmune()
					.noSave()
					.build("behemoth_fireball"));

	private BehemothEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(ABYSSAL_BEHEMOTH, AbyssalBehemothEntity.createAttributes());
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
