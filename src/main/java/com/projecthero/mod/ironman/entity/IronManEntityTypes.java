package com.projecthero.mod.ironman.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class IronManEntityTypes {
	public static final EntityType<IronManSuitPartEntity> SUIT_PART = register("iron_man_suit_part",
			EntityType.Builder.<IronManSuitPartEntity>of(IronManSuitPartEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					// wide enough that a piece flown in from across a Hall of Armor is visible the whole way
					.clientTrackingRange(6)
					.updateInterval(1)
					.noSave()
					.build("iron_man_suit_part"));

	public static final EntityType<IronManMissileEntity> MISSILE = register("iron_man_missile",
			EntityType.Builder.<IronManMissileEntity>of(IronManMissileEntity::new, MobCategory.MISC)
					.sized(0.31f, 0.31f)
					.clientTrackingRange(6)
					.updateInterval(2)
					.noSave()
					.build("iron_man_missile"));

	private IronManEntityTypes() {
	}

	public static void initialize() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
