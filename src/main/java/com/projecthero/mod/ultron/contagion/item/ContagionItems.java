package com.projecthero.mod.ultron.contagion.item;

import java.util.List;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ultron.contagion.block.ContagionBlocks;
import com.projecthero.mod.ultron.contagion.block.FirewallBeaconBlock;
import com.projecthero.mod.ultron.contagion.entity.ContagionEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * v0.16.1: the Ultron Contagion's items -- materials (Ultron Scrap, Nanite Sample, Vibranium Ingot), the tools to fight
 * back (Purge Charge, Override Spike, Firewall Chip, Firewall Beacon), the Vibranium upgrades (Vibranium Shield, Vibranium
 * Plating II), the Omega Core trophy, the corruption / citadel blocks and three spawn eggs. Plus the
 * {@link #MIND_STONE_EMPOWERED} component (the first Omega win's Empowered Mind Stone).
 */
public final class ContagionItems {
	/** On a Mind Stone: empowered (Sneak + hold right-click fires the Mind Stone Beam). */
	public static final DataComponentType<Boolean> MIND_STONE_EMPOWERED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("mind_stone_empowered"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	public static Item ULTRON_SCRAP;
	public static Item NANITE_SAMPLE;
	public static Item VIBRANIUM_INGOT;
	public static Item PURGE_CHARGE;
	public static Item OVERRIDE_SPIKE;
	public static Item FIREWALL_CHIP;
	public static Item VIBRANIUM_PLATING_2;
	public static Item VIBRANIUM_SHIELD;
	public static Item FIREWALL_BEACON;
	public static Item OMEGA_CORE;
	public static Item CORRUPTED_CIRCUITRY;
	public static Item CORRUPTED_STONE;
	public static Item CORRUPTED_LOG;
	public static Item CORRUPTED_LEAVES;
	public static Item CITADEL_PLATING;
	public static Item CITADEL_ROCK;
	public static Item RISING_ROCK;
	public static Item OMEGA_SPAWN_EGG;
	public static Item NEXUS_SPAWN_EGG;
	public static Item HIJACKED_MARK_SPAWN_EGG;

	private ContagionItems() {
	}

	public static void initialize() {
		ULTRON_SCRAP = register("ultron_scrap", hinted(new Item.Properties().stacksTo(64), "item.projecthero.ultron_scrap.hint"));
		NANITE_SAMPLE = register("nanite_sample", hinted(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), "item.projecthero.nanite_sample.hint"));
		VIBRANIUM_INGOT = register("vibranium_ingot", hinted(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC).fireResistant(),
				"item.projecthero.vibranium_ingot.hint"));
		PURGE_CHARGE = register("purge_charge", new PurgeChargeItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
		OVERRIDE_SPIKE = register("override_spike", new OverrideSpikeItem(new Item.Properties().stacksTo(8).rarity(Rarity.RARE)));
		FIREWALL_CHIP = register("firewall_chip", hinted(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), "item.projecthero.firewall_chip.hint"));
		VIBRANIUM_PLATING_2 = register("vibranium_plating_2", hinted(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC).fireResistant(),
				"item.projecthero.vibranium_plating_2.hint"));
		VIBRANIUM_SHIELD = register("vibranium_shield", new VibraniumShieldItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
		FIREWALL_BEACON = blockItem("firewall_beacon", new BlockItem(ContagionBlocks.FIREWALL_BEACON, new Item.Properties().stacksTo(16)
				.rarity(Rarity.EPIC)) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				FirewallBeaconBlock.appendTooltip(stack, context, tooltip, flag);
			}
		});
		OMEGA_CORE = blockItem("ultron_omega_core", new BlockItem(ContagionBlocks.OMEGA_CORE, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)
				.fireResistant()) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("block.projecthero.ultron_omega_core.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		});
		CORRUPTED_CIRCUITRY = plainBlockItem("corrupted_circuitry", ContagionBlocks.CORRUPTED_CIRCUITRY);
		CORRUPTED_STONE = plainBlockItem("corrupted_circuitry_stone", ContagionBlocks.CORRUPTED_STONE);
		CORRUPTED_LOG = plainBlockItem("corrupted_circuitry_log", ContagionBlocks.CORRUPTED_LOG);
		CORRUPTED_LEAVES = plainBlockItem("corrupted_circuitry_leaves", ContagionBlocks.CORRUPTED_LEAVES);
		CITADEL_PLATING = plainBlockItem("citadel_plating", ContagionBlocks.CITADEL_PLATING);
		CITADEL_ROCK = plainBlockItem("citadel_rock", ContagionBlocks.CITADEL_ROCK);
		RISING_ROCK = plainBlockItem("rising_rock", ContagionBlocks.RISING_ROCK);
		OMEGA_SPAWN_EGG = register("ultron_omega_spawn_egg", new SpawnEggItem(ContagionEntityTypes.ULTRON_OMEGA, 0xD8DCE4, 0xE01010, new Item.Properties()));
		NEXUS_SPAWN_EGG = register("ultron_nexus_spawn_egg", new SpawnEggItem(ContagionEntityTypes.ULTRON_NEXUS, 0x15161A, 0xC00808, new Item.Properties()));
		HIJACKED_MARK_SPAWN_EGG = register("hijacked_mark_spawn_egg", new SpawnEggItem(ContagionEntityTypes.HIJACKED_MARK, 0xA01818, 0xE8C040,
				new Item.Properties()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		for (Item i : new Item[] { ULTRON_SCRAP, NANITE_SAMPLE, VIBRANIUM_INGOT, PURGE_CHARGE, OVERRIDE_SPIKE, FIREWALL_CHIP, FIREWALL_BEACON,
				VIBRANIUM_PLATING_2, VIBRANIUM_SHIELD, OMEGA_CORE, CORRUPTED_CIRCUITRY, CORRUPTED_STONE, CORRUPTED_LOG, CORRUPTED_LEAVES,
				CITADEL_PLATING, CITADEL_ROCK, RISING_ROCK, NEXUS_SPAWN_EGG, HIJACKED_MARK_SPAWN_EGG, OMEGA_SPAWN_EGG }) {
			output.accept(i);
		}
	}

	/** Is this Mind Stone empowered? */
	public static boolean empowered(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(MIND_STONE_EMPOWERED));
	}

	private static Item hinted(Item.Properties props, String hintKey) {
		return new Item(props) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable(hintKey).withStyle(ChatFormatting.GRAY));
			}
		};
	}

	private static Item plainBlockItem(String path, Block block) {
		return blockItem(path, new BlockItem(block, new Item.Properties()));
	}

	private static Item blockItem(String path, BlockItem item) {
		Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
		item.registerBlocks(Item.BY_BLOCK, item);
		return item;
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}
}
