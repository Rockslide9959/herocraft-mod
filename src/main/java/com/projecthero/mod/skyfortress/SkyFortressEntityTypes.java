package com.projecthero.mod.skyfortress;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.skyfortress.entity.AdmiralKestrelBoss;
import com.projecthero.mod.skyfortress.entity.DeckOfficerBoss;
import com.projecthero.mod.skyfortress.entity.KestrelMissileEntity;
import com.projecthero.mod.skyfortress.entity.SkyGunner;
import com.projecthero.mod.skyfortress.entity.SkyPrisoner;
import com.projecthero.mod.skyfortress.entity.SkyTrooper;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * v0.17.7: the Sky Fortress's people -- troopers (and the Pad Sergeant), gunners, prisoners and the Deck Officer
 * (all player-model humans in detailed skins), Admiral Kestrel (GeckoLib) and her micro-missiles. None spawns
 * naturally: the fortress places them, the alarm and the Deck Officer call more.
 */
public final class SkyFortressEntityTypes {
	public static final EntityType<SkyTrooper> TROOPER = register("sky_trooper",
			EntityType.Builder.<SkyTrooper>of(SkyTrooper::new, MobCategory.MONSTER).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("sky_trooper"));

	public static final EntityType<SkyGunner> GUNNER = register("sky_gunner",
			EntityType.Builder.<SkyGunner>of(SkyGunner::new, MobCategory.MONSTER).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("sky_gunner"));

	public static final EntityType<SkyPrisoner> PRISONER = register("sky_prisoner",
			EntityType.Builder.<SkyPrisoner>of(SkyPrisoner::new, MobCategory.CREATURE).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("sky_prisoner"));

	public static final EntityType<DeckOfficerBoss> DECK_OFFICER = register("deck_officer",
			EntityType.Builder.<DeckOfficerBoss>of(DeckOfficerBoss::new, MobCategory.MONSTER).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("deck_officer"));

	public static final EntityType<AdmiralKestrelBoss> ADMIRAL_KESTREL = register("admiral_kestrel",
			EntityType.Builder.<AdmiralKestrelBoss>of(AdmiralKestrelBoss::new, MobCategory.MONSTER).sized(0.9f, 2.6f).eyeHeight(2.3f)
					.fireImmune().clientTrackingRange(12).build("admiral_kestrel"));

	public static final EntityType<KestrelMissileEntity> KESTREL_MISSILE = register("kestrel_missile",
			EntityType.Builder.<KestrelMissileEntity>of(KestrelMissileEntity::new, MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(8)
					.updateInterval(2).build("kestrel_missile"));

	private SkyFortressEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(TROOPER, SkyTrooper.createAttributes());
		FabricDefaultAttributeRegistry.register(GUNNER, SkyGunner.createAttributes());
		FabricDefaultAttributeRegistry.register(PRISONER, SkyPrisoner.createAttributes());
		FabricDefaultAttributeRegistry.register(DECK_OFFICER, DeckOfficerBoss.createAttributes());
		FabricDefaultAttributeRegistry.register(ADMIRAL_KESTREL, AdmiralKestrelBoss.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
