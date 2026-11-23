package com.projecthero.mod.skyfortress;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.alarm.AlarmProfile;
import com.projecthero.mod.alarm.AlarmProfiles;
import com.projecthero.mod.skyfortress.block.SkyFortressBlocks;
import com.projecthero.mod.skyfortress.item.ParachuteItem;
import com.projecthero.mod.skyfortress.item.SkyFortressItems;
import com.projecthero.mod.skyfortress.worldgen.SkyFortressWorldgen;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * v0.17.7: the <b>Sky Fortress</b>, a Tier 4 dungeon -- Admiral Kestrel's flying fortress at y 186-222 over plains,
 * savanna and ocean, with a guarded Launch Pad post on the ground below it. Hangar deck -> engine room (four turbines to
 * sabotage) -> cell block (prisoners, the Captain's keycard) -> Command Bridge (the Deck Officer) -> flight deck
 * (Admiral Kestrel) -> the Fortress Vault in her cabin. The whole fortress is one alarm zone (profile {@link #PROFILE},
 * tier 4). See {@code docs/SKY_FORTRESS_REFERENCE.md}.
 *
 * <p>Static state: {@link SkyFling} (flings in progress) and {@link SkyTurbines} (sabotage progress), both keyed by
 * player UUID with numbers only, cleared from {@code ServerStateReset} via {@link #clearSessionState}.
 */
public final class SkyFortressMod {
	public static final String PROFILE = "sky_fortress";
	public static final int COLOUR = 0x6FA8DC;
	/** Kestrel's Thruster's chance per Fortress Vault open (per player). */
	public static final float THRUSTER_CHANCE = 0.2f;
	public static final ResourceKey<LootTable> TIER4_ARTIFACTS = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/vault_tier4_artifacts"));

	private SkyFortressMod() {
	}

	public static void initialize() {
		SkyFortressBlocks.initialize();
		SkyFortressItems.initialize();
		SkyFortressEntityTypes.initialize();
		SkyFortressWorldgen.initialize();
		InSkyFortressCondition.initialize();
		AlarmProfiles.register(new AlarmProfile(PROFILE, 4, COLOUR, Component.translatable("structure.projecthero.sky_fortress"),
				SkyFortressGuards::reinforcement, SoundEvents.NOTE_BLOCK_BELL.value(), 0.5f));
		AlarmProfiles.registerStructure(ProjectHeroMod.id("sky_fortress"), PROFILE);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SkyFling.tick(server);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.level() instanceof ServerLevel level && !p.onGround() && p.fallDistance >= ParachuteItem.AUTO_FALL) {
					ParachuteItem.autoDeploy(level, p);
				}
			}
		});
		// Kestrel's Thruster: one extra pool on the shared Tier 4 artifact sub-table, only inside a cleared Sky Fortress
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (source.isBuiltin() && key.equals(TIER4_ARTIFACTS)) {
				builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
						.when(InSkyFortressCondition::new)
						.when(LootItemRandomChanceCondition.randomChance(THRUSTER_CHANCE))
						.add(LootItem.lootTableItem(SkyFortressItems.KESTREL_THRUSTER)));
			}
		});
	}

	/** Server stopping / world switch: forget flings and sabotage progress. */
	public static void clearSessionState() {
		SkyFling.clear();
		SkyTurbines.clear();
	}
}
