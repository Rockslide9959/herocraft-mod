package com.projecthero.mod.temple;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.alarm.AlarmProfile;
import com.projecthero.mod.alarm.AlarmProfiles;
import com.projecthero.mod.temple.block.TempleBlocks;
import com.projecthero.mod.temple.item.TempleItems;
import com.projecthero.mod.temple.worldgen.TempleWorldgen;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * v0.17.7: the <b>Ancient Temple</b>, a Tier 4 dungeon (a 10-15 minute crawl) in jungle and desert -- a ziggurat over
 * a deep descent: the Antechamber (dart halls, a collapsing floor), the Hall of Trials (three seals: glyph plates, a sun
 * beam and mirrors, a weight altar), the Crushing Corridor, the Flooded Crypt (the Tomb Sentinel), the Hall of the Bound
 * (the Captain's seal key) and the Throne of the Bound King (<b>Ankhatep</b>), with a Tier 4 treasury. Its alarm is the
 * <b>Temple Wards</b> (the shared alarm framework, ancient style: ward gongs, ward plates, ward stones, braziers). See
 * {@code docs/ANCIENT_TEMPLE_REFERENCE.md}.
 *
 * <p>No static per-world state: cleared temples live in {@link TempleState}; the alarm zone in the framework's SavedData.
 */
public final class TempleMod {
	public static final String ALARM_PROFILE = "ancient_temple";
	/** The artifact sub-table the Bound King's Ankh pool is added to (in code, so other features' pools in the JSON stay theirs). */
	public static final ResourceKey<LootTable> TIER4_ARTIFACTS = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/vault_tier4_artifacts"));
	public static final float ANKH_CHANCE = 0.18f;

	private TempleMod() {
	}

	public static void initialize() {
		TempleBlocks.initialize();
		TempleItems.initialize();
		TempleEffects.initialize();
		TempleEntityTypes.initialize();
		TempleWorldgen.initialize();
		NearTempleCondition.initialize();
		TempleItems.registerEvents();
		AlarmProfiles.register(new AlarmProfile(ALARM_PROFILE, 4, 0xE8C060, Component.translatable("structure.projecthero.ancient_temple"),
				TempleGuards::reinforcement, SoundEvents.BELL_BLOCK, 0.5f));
		AlarmProfiles.registerStructure(ProjectHeroMod.id("ancient_temple"), ALARM_PROFILE);
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (key.equals(TIER4_ARTIFACTS)) {
				builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
						.when(() -> new NearTempleCondition(32.0))
						.when(LootItemRandomChanceCondition.randomChance(ANKH_CHANCE))
						.add(LootItem.lootTableItem(TempleItems.BOUND_KINGS_ANKH)));
			}
		});
	}
}
