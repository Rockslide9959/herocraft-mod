package com.projecthero.mod.doomsday.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.2: Doomsday (player-sized; the SCALE attribute does the rest), his casket and his moves. */
public final class DoomsdayEntityTypes {
	public static final EntityType<DoomsdayEntity> DOOMSDAY = register("doomsday",
			EntityType.Builder.<DoomsdayEntity>of(DoomsdayEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(16).build("doomsday"));

	public static final EntityType<CasketEntity> CASKET = register("calatonian_casket",
			EntityType.Builder.<CasketEntity>of(CasketEntity::new, MobCategory.MISC)
					.sized(1.6f, 1.2f).fireImmune().clientTrackingRange(16).updateInterval(1).build("calatonian_casket"));

	public static final EntityType<DoomsdayAttackEntity> ATTACK = register("doomsday_attack",
			EntityType.Builder.<DoomsdayAttackEntity>of(DoomsdayAttackEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(1).noSave().build("doomsday_attack"));

	private DoomsdayEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(DOOMSDAY, DoomsdayEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
