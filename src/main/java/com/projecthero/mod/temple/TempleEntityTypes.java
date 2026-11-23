package com.projecthero.mod.temple;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.temple.entity.AnkhatepBoss;
import com.projecthero.mod.temple.entity.CultAcolyte;
import com.projecthero.mod.temple.entity.TempleGuardian;
import com.projecthero.mod.temple.entity.TombSentinelBoss;
import com.projecthero.mod.temple.entity.WrappedRevenant;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * v0.17.7: the Ancient Temple's people. The guardians, revenants, acolytes and the Tomb Sentinel (x1.6 by SCALE) are
 * player-model humans in detailed skins; Ankhatep, the boss, has his own GeckoLib body. None spawns naturally.
 */
public final class TempleEntityTypes {
	public static final EntityType<TempleGuardian> GUARDIAN = register("temple_guardian",
			EntityType.Builder.<TempleGuardian>of(TempleGuardian::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("temple_guardian"));

	public static final EntityType<WrappedRevenant> REVENANT = register("wrapped_revenant",
			EntityType.Builder.<WrappedRevenant>of(WrappedRevenant::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("wrapped_revenant"));

	public static final EntityType<CultAcolyte> ACOLYTE = register("cult_acolyte",
			EntityType.Builder.<CultAcolyte>of(CultAcolyte::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("cult_acolyte"));

	public static final EntityType<TombSentinelBoss> TOMB_SENTINEL = register("tomb_sentinel",
			EntityType.Builder.<TombSentinelBoss>of(TombSentinelBoss::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("tomb_sentinel"));

	/** Ankhatep: a 3.3-block sorcerer-king (the geo model is authored at that size). */
	public static final EntityType<AnkhatepBoss> ANKHATEP = register("ankhatep",
			EntityType.Builder.<AnkhatepBoss>of(AnkhatepBoss::new, MobCategory.MONSTER)
					.sized(1.1f, 3.3f).eyeHeight(2.7f).fireImmune().clientTrackingRange(12).build("ankhatep"));

	private TempleEntityTypes() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(GUARDIAN, TempleGuardian.createAttributes());
		FabricDefaultAttributeRegistry.register(REVENANT, WrappedRevenant.createAttributes());
		FabricDefaultAttributeRegistry.register(ACOLYTE, CultAcolyte.createAttributes());
		FabricDefaultAttributeRegistry.register(TOMB_SENTINEL, TombSentinelBoss.createAttributes());
		FabricDefaultAttributeRegistry.register(ANKHATEP, AnkhatepBoss.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
