package com.projecthero.mod.miraculous.ladybug.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.16.6: Ladybug's entities -- the thrown Lucky Charm TNT. */
public final class LadybugEntityTypes {
	public static final EntityType<LuckyTntEntity> LUCKY_TNT = register("ladybug_lucky_tnt",
			EntityType.Builder.<LuckyTntEntity>of(LuckyTntEntity::new, MobCategory.MISC)
					.sized(0.4f, 0.4f).noSave().noSummon().clientTrackingRange(6).updateInterval(5).build("ladybug_lucky_tnt"));

	private LadybugEntityTypes() {
	}

	public static void initialize() {
		// class load registers the types
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
