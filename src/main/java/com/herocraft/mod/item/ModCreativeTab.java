package com.herocraft.mod.item;

import com.herocraft.mod.HeroCraftMod;

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
			HeroCraftMod.id("superheroes"));

	/**
	 * v0.6.19: a dedicated tab for <em>everything</em> Iron Man — the Arc Reactor, the Stark Fabricator
	 * and Suit Platform blocks, every component, every blueprint and all the armour pieces (listed
	 * Mark I → Mark II → …). None of it appears in the main Superheroes tab any more; this is the one
	 * place to find it. (Superseded the old "Iron Man Armour" tab, which held only the armour pieces.)
	 */
	public static final ResourceKey<CreativeModeTab> IRON_MAN_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			HeroCraftMod.id("iron_man"));

	public static final CreativeModeTab IRON_MAN = FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.herocraft.iron_man"))
			.icon(() -> new ItemStack(
					com.herocraft.mod.ironman.item.IronManItems.armor("mark_iii", net.minecraft.world.item.ArmorItem.Type.HELMET)))
			.displayItems((parameters, output) -> {
				// The Arc Reactor, the machines, the reactor core + suitcase, then every blueprint, then
				// the components (basic → advanced), then all the armour ordered by mark. See
				// IronManItems.addToCreativeTab for the exact order.
				com.herocraft.mod.ironman.item.IronManItems.addToCreativeTab(output);
			})
			.build();

	/**
	 * The Punisher tab: every firearm, all four ammunition types, the crafting components, the
	 * tactical armour set and the Vigilante Training Manual. Nothing Punisher goes in the Superheroes
	 * tab (spec section 41 -- do not clutter unrelated tabs).
	 */
	public static final ResourceKey<CreativeModeTab> PUNISHER_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			HeroCraftMod.id("punisher"));

	public static final CreativeModeTab PUNISHER = FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.herocraft.punisher"))
			.icon(() -> new ItemStack(com.herocraft.mod.firearm.item.FirearmItems.PUNISHER_PISTOL))
			.displayItems((parameters, output) -> {
				com.herocraft.mod.firearm.item.FirearmItems.addToCreativeTab(output);
				com.herocraft.mod.punisher.item.PunisherItems.addToCreativeTab(output);
				// The tactical armour + Vigilante Training Manual are appended here once Phase 4 adds them.
			})
			.build();

	public static final CreativeModeTab SUPERHEROES = FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.herocraft.superheroes"))
			.icon(() -> new ItemStack(ModItems.MJOLNIR))
			.displayItems((parameters, output) -> {
				output.accept(ModItems.MJOLNIR);
				output.accept(ModItems.POWER_SUPPRESSOR);
				// HeroPack experimental-power items (research notes, reagents, serums, guide).
				com.herocraft.mod.hero.item.HeroPackItems.addToCreativeTab(output);
				com.herocraft.mod.hero.device.ModDevices.addToCreativeTab(output);
				// Iron Man has its own dedicated tab now (v0.6.19) — nothing Iron Man goes in here.
				// Zombie Raid: Grave Essence, artifacts, raid weapons, trophies, the Cursed Grave block.
				com.herocraft.mod.grave.item.GraveItems.addToCreativeTab(output);
				// Supervillain Village Raid: Villain Cache, Supervillain Token, Power Fragment, trophies.
				com.herocraft.mod.event.raid.SupervillainRaidItems.addToCreativeTab(output);
				// Spider-Man: the Arachnid Mutagen that evolves Spider Adhesion into the Hero Class.
				com.herocraft.mod.spider.item.SpiderItems.addToCreativeTab(output);
				// Max Steel: the T.U.R.B.O. Stabilizer for bonding with Steel below Level 30.
				com.herocraft.mod.maxsteel.item.MaxSteelItems.addToCreativeTab(output);
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
