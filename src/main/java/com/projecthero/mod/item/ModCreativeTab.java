package com.projecthero.mod.item;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {
	public static final ResourceKey<CreativeModeTab> SUPERHEROES_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			ProjectHeroMod.id("superheroes"));

	/**
	 * v0.6.19: a dedicated tab for <em>everything</em> Iron Man — the Arc Reactor, the Stark Fabricator
	 * and Suit Platform blocks, every component, every blueprint and all the armour pieces (listed
	 * Mark I → Mark II → …). None of it appears in the main Superheroes tab any more; this is the one
	 * place to find it. (Superseded the old "Iron Man Armour" tab, which held only the armour pieces.)
	 */
	public static final ResourceKey<CreativeModeTab> IRON_MAN_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			ProjectHeroMod.id("iron_man"));

	public static final CreativeModeTab IRON_MAN = FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.projecthero.iron_man"))
			.icon(() -> new ItemStack(
					com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", net.minecraft.world.item.ArmorItem.Type.HELMET)))
			.displayItems((parameters, output) -> {
				// The Arc Reactor, the machines, the reactor core + suitcase, then every blueprint, then
				// the components (basic → advanced), then all the armour ordered by mark. See
				// IronManItems.addToCreativeTab for the exact order.
				com.projecthero.mod.ironman.item.IronManItems.addToCreativeTab(output);
				com.projecthero.mod.ironman.furnace.StarkFurnaces.addToCreativeTab(output); // v0.14.26
			})
			.build();

	/**
	 * The Punisher tab: every firearm, all four ammunition types, the crafting components, the
	 * tactical armour set and the Vigilante Training Manual. Nothing Punisher goes in the Superheroes
	 * tab (spec section 41 -- do not clutter unrelated tabs).
	 */
	public static final ResourceKey<CreativeModeTab> PUNISHER_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			ProjectHeroMod.id("punisher"));

	public static final CreativeModeTab PUNISHER = FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.projecthero.punisher"))
			.icon(() -> new ItemStack(com.projecthero.mod.firearm.item.FirearmItems.PUNISHER_PISTOL))
			.displayItems((parameters, output) -> {
				com.projecthero.mod.firearm.item.FirearmItems.addToCreativeTab(output);
				com.projecthero.mod.punisher.item.PunisherItems.addToCreativeTab(output);
				// The tactical armour + Vigilante Training Manual are appended here once Phase 4 adds them.
			})
			.build();

	public static final CreativeModeTab SUPERHEROES = FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.projecthero.superheroes"))
			.icon(() -> new ItemStack(ModItems.MJOLNIR))
			.displayItems((parameters, output) -> {
				output.accept(ModItems.MJOLNIR);
				output.accept(ModItems.STORMBREAKER);
				output.accept(ModItems.UNFORGED_STORMBREAKER);
				output.accept(ModItems.POWER_SUPPRESSOR);
				output.accept(com.projecthero.mod.symbiote.item.SymbioteHostItems.SYMBIOTE_VIAL);
				output.accept(com.projecthero.mod.symbiote.item.SymbioteHostItems.SYMBIOTE_VIAL_FILLED);
				// v0.13.19: the Symbiote Meteorite (break it and a free Symbiote crawls out) -- for admins.
				output.accept(com.projecthero.mod.symbiote.block.SymbioteBlocks.SYMBIOTE_METEORITE_ITEM);
				// HeroPack experimental-power items (research notes, reagents, serums, guide).
				com.projecthero.mod.hero.item.HeroPackItems.addToCreativeTab(output);
				// v0.13.18: the three random-power serums.
				com.projecthero.mod.hero.item.RandomPowerSerumItem.addToCreativeTab(output);
				com.projecthero.mod.hero.device.ModDevices.addToCreativeTab(output);
				// Iron Man has its own dedicated tab now (v0.6.19) — nothing Iron Man goes in here.
				// Zombie Raid: Grave Essence, artifacts, raid weapons, trophies, the Cursed Grave block.
				com.projecthero.mod.grave.item.GraveItems.addToCreativeTab(output);
				// Supervillain Village Raid: Villain Cache, Supervillain Token, Power Fragment, trophies.
				com.projecthero.mod.event.raid.SupervillainRaidItems.addToCreativeTab(output);
				// v0.14.25: Syndicate Bust: Police Scanner, Villain Dossier, Kingpin's Cane, the stash, spawn eggs.
				com.projecthero.mod.syndicate.SyndicateItems.addToCreativeTab(output);
				// v0.14.25: Carnage: Crimson Biomass and the spawn eggs.
				com.projecthero.mod.carnage.CarnageItems.addToCreativeTab(output);
				// v0.15.1: Sentinel Purge: the Trask Signal, Sentinel Circuitry, the Master Mold Core, spawn eggs.
				com.projecthero.mod.sentinel.item.SentinelItems.addToCreativeTab(output);
				// v0.15.12: Ultron Uprising: the Ultron Beacon, Vibranium Plating, the Ultron Core, the Mind Stone, spawn eggs.
				com.projecthero.mod.ultron.item.UltronItems.addToCreativeTab(output);
				// Spider-Man: the Arachnid Mutagen that evolves Spider Adhesion into the Hero Class.
				com.projecthero.mod.spider.item.SpiderItems.addToCreativeTab(output);
				// Max Steel: the T.U.R.B.O. Stabilizer for bonding with Steel below Level 30.
				com.projecthero.mod.maxsteel.item.MaxSteelItems.addToCreativeTab(output);
				// Green Lantern: the Power Ring, Lantern Core, suit pieces and Power Battery block.
				com.projecthero.mod.greenlantern.item.GreenLanternItems.addToCreativeTab(output);
				// Wolverine: the Adamantium Serum that ascends Super Regeneration into the Hero Class.
				com.projecthero.mod.wolverine.item.WolverineItems.addToCreativeTab(output);
				// All Might: the Vestige of One For All and the costume pieces.
				com.projecthero.mod.allmight.item.AllMightItems.addToCreativeTab(output);
				// Hulk: the Gamma Serum (loot-only in survival) and the Gamma Reactor block.
				com.projecthero.mod.hulk.item.HulkItems.addToCreativeTab(output);
				// v0.14.8 Super Soldier: the unrefined and refined Super Soldier Serum.
				com.projecthero.mod.supersoldier.item.SuperSoldierItems.addToCreativeTab(output);
				// Kryptonian (v0.14.8): the Kryptonian Crystal, kryptonite and the Meteor Core.
				com.projecthero.mod.kryptonian.item.KryptonianItems.addToCreativeTab(output);
				// v0.15.13 Nova: the Nova Corps Helmet and the Centurion's spawn egg.
				com.projecthero.mod.nova.item.NovaItems.addToCreativeTab(output);
				// v0.14.11: the Flash Suit (the ring itself only exists holding a suit)
				com.projecthero.mod.flash.FlashSuit.addToCreativeTab(output);
				// v0.14.12: the Horde blocks and the new mobs' spawn eggs
				com.projecthero.mod.horde.HordeBlocks.addToCreativeTab(output);
				// Moon Knight: the Scarab of Khonshu (loot-only in survival) and the Altar of Khonshu.
				com.projecthero.mod.moonknight.temple.KhonshuTemple.addToCreativeTab(output);
				// Thor: the H-conjured armour (creative-only for testing; in survival it is summoned, not crafted).
				com.projecthero.mod.thorarmor.ThorArmorItems.addToCreativeTab(output);
				// Titan Shifter: the Titan Serum.
				com.projecthero.mod.titanshifter.item.TitanShifterItems.addToCreativeTab(output);
				// The Abyssal Behemoth: its unique boss material.
				com.projecthero.mod.behemoth.BehemothItems.addToCreativeTab(output);
				// v0.13.18 Darkseid Raid: Boom Tube Beacon, Omega Core/Shard, Mother Box, Omega Relic, spawn eggs.
				com.projecthero.mod.darkseid.item.DarkseidItems.addToCreativeTab(output);
			})
			.build();

	private ModCreativeTab() {
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SUPERHEROES_KEY, SUPERHEROES);
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, IRON_MAN_KEY, IRON_MAN);
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, PUNISHER_KEY, PUNISHER);
	}
}
