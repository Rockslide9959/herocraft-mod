package com.projecthero.mod.hideout;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hideout.boss.KingpinEnforcerBoss;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** v0.17.5: the five hideout mini-bosses (the guards are every faction's existing mobs). */
public final class HideoutEntityTypes {
	/** Kingpin's Enforcer: player-sized, scaled x1.6 by his SCALE attribute. */
	public static final EntityType<KingpinEnforcerBoss> KINGPIN_ENFORCER = register("kingpins_enforcer",
			EntityType.Builder.<KingpinEnforcerBoss>of(KingpinEnforcerBoss::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("kingpins_enforcer"));

	/** The Forge Overseer: player-sized, scaled x1.7 by its SCALE attribute. */
	public static final EntityType<com.projecthero.mod.hideout.boss.ForgeOverseerBoss> FORGE_OVERSEER = register("forge_overseer",
			EntityType.Builder.<com.projecthero.mod.hideout.boss.ForgeOverseerBoss>of(com.projecthero.mod.hideout.boss.ForgeOverseerBoss::new,
					MobCategory.MONSTER).sized(0.6f, 1.8f).eyeHeight(1.62f).fireImmune().clientTrackingRange(10).build("forge_overseer"));

	/** The Parademon Commander: a Parademon's box, x1.5 (getDefaultDimensions scales it). */
	public static final EntityType<com.projecthero.mod.hideout.boss.ParademonCommanderBoss> PARADEMON_COMMANDER = register("parademon_commander",
			EntityType.Builder.<com.projecthero.mod.hideout.boss.ParademonCommanderBoss>of(com.projecthero.mod.hideout.boss.ParademonCommanderBoss::new,
					MobCategory.MONSTER).sized(0.7f, 2.0f).eyeHeight(1.75f).fireImmune().clientTrackingRange(10).build("parademon_commander"));

	/** The Hive Mother: player-sized, scaled x1.8 by her SCALE attribute. */
	public static final EntityType<com.projecthero.mod.hideout.boss.HiveMotherBoss> HIVE_MOTHER = register("hive_mother",
			EntityType.Builder.<com.projecthero.mod.hideout.boss.HiveMotherBoss>of(com.projecthero.mod.hideout.boss.HiveMotherBoss::new,
					MobCategory.MONSTER).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("hive_mother"));

	/** The Frost Giant Jarl: the Warlord's body at 6.5 blocks (the renderer fits the model to the box). */
	public static final EntityType<com.projecthero.mod.hideout.boss.FrostGiantJarlBoss> FROST_GIANT_JARL = register("frost_giant_jarl",
			EntityType.Builder.<com.projecthero.mod.hideout.boss.FrostGiantJarlBoss>of(com.projecthero.mod.hideout.boss.FrostGiantJarlBoss::new,
					MobCategory.MONSTER).sized(1.6f, 6.5f).eyeHeight(5.8f).clientTrackingRange(12).updateInterval(2).build("frost_giant_jarl"));

	private HideoutEntityTypes() {
	}

	/** Every mini-boss type (tests, the guide). */
	public static java.util.List<EntityType<? extends net.minecraft.world.entity.Mob>> bosses() {
		return java.util.List.of(KINGPIN_ENFORCER, FORGE_OVERSEER, PARADEMON_COMMANDER, HIVE_MOTHER, FROST_GIANT_JARL);
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(KINGPIN_ENFORCER, KingpinEnforcerBoss.createAttributes());
		FabricDefaultAttributeRegistry.register(FORGE_OVERSEER, com.projecthero.mod.hideout.boss.ForgeOverseerBoss.createAttributes());
		FabricDefaultAttributeRegistry.register(PARADEMON_COMMANDER, com.projecthero.mod.hideout.boss.ParademonCommanderBoss.createAttributes());
		FabricDefaultAttributeRegistry.register(HIVE_MOTHER, com.projecthero.mod.hideout.boss.HiveMotherBoss.createAttributes());
		FabricDefaultAttributeRegistry.register(FROST_GIANT_JARL, com.projecthero.mod.hideout.boss.FrostGiantJarlBoss.createAttributes());
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path)), type);
	}
}
