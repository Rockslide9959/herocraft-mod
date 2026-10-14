package com.projecthero.mod.city;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;

/**
 * v0.16.2 City part B: the City's systems -- the self-repairing City (Repair Workers), City Cops, Doctors,
 * Firefighters, the bank's Safe Deposit and The Daily Bulletin. The one entry point ({@link #initialize}, called from
 * {@code ProjectHeroMod} after the config is loaded). See {@code docs/CITY_REFERENCE.md} (part B).
 *
 * <p>City part A (the worldgen) builds the {@code projecthero:city} structure; everything here finds Cities through
 * {@link CityLocator}, so it works on any City made of jigsaw pieces (or a test City registered by hand).
 */
public final class City {
	public static final String REPAIR_RULE_NAME = "projectheroCityRepair";
	public static GameRules.Key<GameRules.BooleanValue> REPAIR_RULE;

	private City() {
	}

	public static void initialize() {
		CityBlocks.initialize();
		CityTrades.initialize();
		CityLocator.initialize();
		REPAIR_RULE = GameRuleRegistry.register(REPAIR_RULE_NAME, GameRules.Category.UPDATES, GameRuleFactory.createBooleanRule(true));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			CityDamageTracker.tick(server);
			CityRepair.tick(server);
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				CityRepair.onVillagerLoad(v, level);
			}
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && entity instanceof Villager v) {
				return CityServices.onUseVillager(player, v, hand);
			}
			return InteractionResult.PASS;
		});
		// a cop that dies stops being "on a call"
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Villager v && CityCops.isCop(v)) {
				CityCops.forget(v);
			}
		});
		com.projecthero.mod.crime.Crimes.addListener(CityCops::onCrimeBegin);
	}

	/** Repairs on: the config switch and the {@code projectheroCityRepair} gamerule. */
	public static boolean repairActive(ServerLevel level) {
		return CityConfig.repair().enabled && (REPAIR_RULE == null || level.getGameRules().getBoolean(REPAIR_RULE));
	}

	/** Every static City cache ({@code ServerStateReset}). */
	public static void clearSessionState() {
		CityLocator.clearSessionState();
		CityBlueprint.clearSessionState();
		CityDamageTracker.clearSessionState();
		CityRepair.clearSessionState();
		CityCops.clearSessionState();
		CityServices.clearSessionState();
	}
}
