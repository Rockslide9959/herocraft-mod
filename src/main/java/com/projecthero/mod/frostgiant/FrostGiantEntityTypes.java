package com.projecthero.mod.frostgiant;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.frostgiant.entity.FrostGiantWarlordEntity;
import com.projecthero.mod.frostgiant.entity.FrostShamanEntity;
import com.projecthero.mod.frostgiant.entity.IceBruteEntity;
import com.projecthero.mod.frostgiant.entity.IceCasketEntity;
import com.projecthero.mod.frostgiant.entity.IceJavelinEntity;
import com.projecthero.mod.frostgiant.entity.IcePrisonEntity;
import com.projecthero.mod.frostgiant.entity.JotunRaiderEntity;
import com.projecthero.mod.frostgiant.entity.KingLaufeyEntity;
import com.projecthero.mod.frostgiant.entity.RimeHoundEntity;
import com.projecthero.mod.frostgiant.entity.RuneBrazierEntity;
import com.projecthero.mod.frostgiant.entity.YmirArmEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * v0.16.1: the Frost Giant Assault's entities, sized to the spec: hound 1.6 tall, Raider 4.5, Shaman 5, Brute 7, Warlord
 * 11, Laufey 14 (kept narrower than they look so they can still path between trees and houses; their models are wider).
 * The models are scaled by the renderer so each rest pose fills its hit-box height.
 */
public final class FrostGiantEntityTypes {
	public static final EntityType<RimeHoundEntity> HOUND = register("rime_hound",
			EntityType.Builder.<RimeHoundEntity>of(RimeHoundEntity::new, MobCategory.MONSTER)
					.sized(1.0f, 1.6f).eyeHeight(1.35f).clientTrackingRange(10).build("rime_hound"));

	public static final EntityType<JotunRaiderEntity> RAIDER = register("jotun_raider",
			EntityType.Builder.<JotunRaiderEntity>of(JotunRaiderEntity::new, MobCategory.MONSTER)
					.sized(1.3f, 4.5f).eyeHeight(4.0f).clientTrackingRange(12).build("jotun_raider"));

	public static final EntityType<FrostShamanEntity> SHAMAN = register("frost_shaman",
			EntityType.Builder.<FrostShamanEntity>of(FrostShamanEntity::new, MobCategory.MONSTER)
					.sized(1.3f, 5.0f).eyeHeight(4.45f).clientTrackingRange(12).build("frost_shaman"));

	public static final EntityType<IceBruteEntity> BRUTE = register("ice_brute",
			EntityType.Builder.<IceBruteEntity>of(IceBruteEntity::new, MobCategory.MONSTER)
					.sized(2.2f, 7.0f).eyeHeight(6.1f).clientTrackingRange(14).build("ice_brute"));

	public static final EntityType<FrostGiantWarlordEntity> WARLORD = register("frost_giant_warlord",
			EntityType.Builder.<FrostGiantWarlordEntity>of(FrostGiantWarlordEntity::new, MobCategory.MONSTER)
					.sized(2.6f, 11.0f).eyeHeight(9.8f).clientTrackingRange(16).updateInterval(2).build("frost_giant_warlord"));

	public static final EntityType<KingLaufeyEntity> LAUFEY = register("king_laufey",
			EntityType.Builder.<KingLaufeyEntity>of(KingLaufeyEntity::new, MobCategory.MONSTER)
					.sized(2.8f, 14.0f).eyeHeight(12.6f).clientTrackingRange(16).updateInterval(2).build("king_laufey"));

	public static final EntityType<RuneBrazierEntity> RUNE_BRAZIER = register("rune_brazier",
			EntityType.Builder.<RuneBrazierEntity>of(RuneBrazierEntity::new, MobCategory.MISC)
					.sized(1.0f, 2.0f).eyeHeight(1.6f).fireImmune().clientTrackingRange(12).build("rune_brazier"));

	public static final EntityType<IceCasketEntity> ICE_CASKET = register("ice_casket",
			EntityType.Builder.<IceCasketEntity>of(IceCasketEntity::new, MobCategory.MONSTER)
					.sized(2.4f, 1.8f).eyeHeight(0.9f).fireImmune().clientTrackingRange(16).updateInterval(1).build("ice_casket"));

	public static final EntityType<IcePrisonEntity> ICE_PRISON = register("ice_prison",
			EntityType.Builder.<IcePrisonEntity>of(IcePrisonEntity::new, MobCategory.MISC)
					.sized(1.4f, 2.4f).fireImmune().clientTrackingRange(10).updateInterval(1).build("ice_prison"));

	public static final EntityType<YmirArmEntity> YMIR_ARM = register("ymir_arm",
			EntityType.Builder.<YmirArmEntity>of(YmirArmEntity::new, MobCategory.MISC)
					.sized(1.0f, 1.0f).fireImmune().noSummon().clientTrackingRange(20).updateInterval(1).build("ymir_arm"));

	public static final EntityType<IceJavelinEntity> ICE_JAVELIN = register("ice_javelin",
			EntityType.Builder.<IceJavelinEntity>of(IceJavelinEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1).build("ice_javelin"));

	private FrostGiantEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(HOUND, RimeHoundEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(RAIDER, JotunRaiderEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SHAMAN, FrostShamanEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(BRUTE, IceBruteEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WARLORD, FrostGiantWarlordEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(LAUFEY, KingLaufeyEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(RUNE_BRAZIER, RuneBrazierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ICE_CASKET, IceCasketEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
