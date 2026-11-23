package com.projecthero.mod.vaultdungeon;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.vaultdungeon.entity.RiotGunner;
import com.projecthero.mod.vaultdungeon.entity.RiotWarden;
import com.projecthero.mod.vaultdungeon.entity.VaultInmate;
import com.projecthero.mod.vaultdungeon.entity.WardenKrossBoss;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * v0.17.5: The Vault's people -- escaped inmates, riot wardens, riot gunners (all player-model humans in detailed
 * skins, drawn by the Syndicate renderer) and Warden Kross (the same, scaled x1.7). None spawns naturally: the
 * dungeon places them (and Kross's Lockdown / the laser alarms call more).
 */
public final class VaultEntityTypes {
	public static final EntityType<VaultInmate> INMATE = register("vault_inmate",
			EntityType.Builder.<VaultInmate>of(VaultInmate::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("vault_inmate"));

	public static final EntityType<RiotWarden> RIOT_WARDEN = register("riot_warden",
			EntityType.Builder.<RiotWarden>of(RiotWarden::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("riot_warden"));

	public static final EntityType<RiotGunner> RIOT_GUNNER = register("riot_gunner",
			EntityType.Builder.<RiotGunner>of(RiotGunner::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("riot_gunner"));

	/** Warden Kross: player-sized, scaled x1.7 by his SCALE attribute. */
	public static final EntityType<WardenKrossBoss> WARDEN_KROSS = register("warden_kross",
			EntityType.Builder.<WardenKrossBoss>of(WardenKrossBoss::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("warden_kross"));

	/** v0.17.7: the Riot Captain, the Maximum Security mid-boss -- player-sized, scaled x1.25. */
	public static final EntityType<com.projecthero.mod.vaultdungeon.entity.RiotCaptainBoss> RIOT_CAPTAIN = register("riot_captain",
			EntityType.Builder.<com.projecthero.mod.vaultdungeon.entity.RiotCaptainBoss>of(com.projecthero.mod.vaultdungeon.entity.RiotCaptainBoss::new,
					MobCategory.MONSTER).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("riot_captain"));

	private VaultEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(RIOT_CAPTAIN, com.projecthero.mod.vaultdungeon.entity.RiotCaptainBoss.createAttributes());
		FabricDefaultAttributeRegistry.register(INMATE, VaultInmate.createAttributes());
		FabricDefaultAttributeRegistry.register(RIOT_WARDEN, RiotWarden.createAttributes());
		FabricDefaultAttributeRegistry.register(RIOT_GUNNER, RiotGunner.createAttributes());
		FabricDefaultAttributeRegistry.register(WARDEN_KROSS, WardenKrossBoss.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
