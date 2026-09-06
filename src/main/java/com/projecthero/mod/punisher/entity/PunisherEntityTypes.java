package com.projecthero.mod.punisher.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** The Punisher's two projectile/placed entities: the Frag Grenade and the Explosive Charge (C4). */
public final class PunisherEntityTypes {
	public static final EntityType<FragGrenadeEntity> FRAG_GRENADE = register("frag_grenade",
			EntityType.Builder.<FragGrenadeEntity>of(FragGrenadeEntity::new, MobCategory.MISC)
					.sized(0.3f, 0.3f)
					.clientTrackingRange(6)
					.updateInterval(5)
					.build("frag_grenade"));

	public static final EntityType<C4ChargeEntity> C4_CHARGE = register("c4_charge",
			EntityType.Builder.<C4ChargeEntity>of(C4ChargeEntity::new, MobCategory.MISC)
					.sized(0.4f, 0.28f)
					.clientTrackingRange(8)
					.updateInterval(20)
					.build("c4_charge"));

	private PunisherEntityTypes() {
	}

	public static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
