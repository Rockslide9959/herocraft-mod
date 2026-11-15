package com.projecthero.mod.vaultdungeon.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.loot.HeroRarity;
import com.projecthero.mod.vaultdungeon.block.VaultBlocks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/** v0.17.5: The Vault's three artifacts (rolled per player from its Dungeon Vaults only). */
public final class VaultItems {
	public static Item WARDENS_KEYRING;
	public static Item MJOLNIR_SHARD;
	public static Item RIOT_CAPACITOR;

	private VaultItems() {
	}

	public static void initialize() {
		WARDENS_KEYRING = item("wardens_keyring", new WardensKeyringItem(HeroRarity.props(HeroRarity.EPIC).stacksTo(1)));
		MJOLNIR_SHARD = item("cracked_mjolnir_shard", new MjolnirShardItem(HeroRarity.props(HeroRarity.LEGENDARY).stacksTo(1).fireResistant()));
		RIOT_CAPACITOR = item("riot_capacitor", new RiotCapacitorItem(HeroRarity.props(HeroRarity.EPIC).stacksTo(1)));
	}

	/** The Riot Capacitor's trigger: a hit you take. */
	public static void registerEvents() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0f && !blocked) {
				RiotCapacitorItem.onHurt(player, source);
			}
		});
	}

	public static Item[] all() {
		return new Item[] { WARDENS_KEYRING, MJOLNIR_SHARD, RIOT_CAPACITOR };
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		for (Item i : all()) {
			output.accept(i);
		}
		output.accept(VaultBlocks.TURRET_ITEM);
		output.accept(VaultBlocks.ALARM_ITEM);
		output.accept(VaultBlocks.CONSOLE_ITEM);
		output.accept(VaultBlocks.TRACK_ITEM);
	}

	private static Item item(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}
}
