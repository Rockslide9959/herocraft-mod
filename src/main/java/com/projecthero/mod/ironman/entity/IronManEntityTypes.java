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
					// v0.14.21: saved -- the courier carries the real armour stack, which must survive a chunk unload / restart
					.build("iron_man_suit_part"));

	/** v0.14.21: the Mark VII delivery pod (carries the real stacks; saved for the same reason). */
	public static final EntityType<IronManDeliveryPodEntity> DELIVERY_POD = register("iron_man_delivery_pod",
			EntityType.Builder.<IronManDeliveryPodEntity>of(IronManDeliveryPodEntity::new, MobCategory.MISC)
					.sized(1.1f, 2.2f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.build("iron_man_delivery_pod"));

	public static final EntityType<IronManMissileEntity> MISSILE = register("iron_man_missile",
			EntityType.Builder.<IronManMissileEntity>of(IronManMissileEntity::new, MobCategory.MISC)
					.sized(0.31f, 0.31f)
					.clientTrackingRange(6)
					.updateInterval(2)
					.noSave()
					.build("iron_man_missile"));

	/** v0.15.9: Sentry Mode -- the empty suit standing on its own (carries the real stacks; saved). */
	public static final EntityType<IronManSentryEntity> SENTRY = register("iron_man_sentry",
			EntityType.Builder.<IronManSentryEntity>of(IronManSentryEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.9f)
					.eyeHeight(1.62f)
					.fireImmune()
					.clientTrackingRange(10)
					.updateInterval(1)
					.build("iron_man_sentry"));

	private IronManEntityTypes() {
	}

	public static void initialize() {
		com.projecthero.mod.ironman.IronManSounds.initialize(); // v0.14.21
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
