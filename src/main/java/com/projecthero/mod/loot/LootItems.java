package com.projecthero.mod.loot;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.17.5 loot framework: the Hero Vault block, the gear-tier materials, the hero supplies and the Locator, plus the
 * {@link #LOCATOR_TARGET} component. (The rarity component lives on {@link HeroRarity#COMPONENT}.)
 */
public final class LootItems {
	public static final DataComponentType<LocatorTarget> LOCATOR_TARGET = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("locator_target"),
			DataComponentType.<LocatorTarget>builder().persistent(LocatorTarget.CODEC).networkSynchronized(LocatorTarget.STREAM_CODEC).build());

	public static HeroVaultBlock HERO_VAULT;
	public static BlockItem HERO_VAULT_ITEM;
	public static BlockEntityType<HeroVaultBlockEntity> HERO_VAULT_BLOCK_ENTITY;

	// gear-tier materials (I-IV)
	public static Item SALVAGED_TECH;
	public static Item HERO_ALLOY_SCRAP;
	public static Item HERO_ALLOY_INGOT;
	public static Item VIBRANIUM_SHARD;
	public static Item CELESTIAL_CORE;

	// hero supplies
	public static Item ENERGY_CELL;
	public static Item COOLDOWN_STIM;
	public static Item SUIT_REPAIR_KIT;
	public static Item MED_KIT;

	public static Item LOCATOR;

	private LootItems() {
	}

	static void initialize() {
		HERO_VAULT = Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("hero_vault")),
				new HeroVaultBlock(BlockBehaviour.Properties.of()
						.mapColor(MapColor.METAL)
						.strength(50.0f, 1200.0f)
						.sound(SoundType.VAULT)
						.lightLevel(s -> 3 + s.getValue(HeroVaultBlock.TIER))
						.pushReaction(PushReaction.BLOCK)
));
		HERO_VAULT_ITEM = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id("hero_vault")),
				new BlockItem(HERO_VAULT, HeroRarity.props(HeroRarity.COMMON)) {
					@Override
					public Component getName(ItemStack stack) {
						return HeroVault.tierName(vaultTier(stack));
					}

					@Override
					public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
						tooltip.add(Component.translatable("block.projecthero.hero_vault.hint").withStyle(ChatFormatting.GRAY));
					}
				});
		HERO_VAULT_ITEM.registerBlocks(Item.BY_BLOCK, HERO_VAULT_ITEM);
		HERO_VAULT_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("hero_vault"),
				FabricBlockEntityTypeBuilder.create(HeroVaultBlockEntity::new, HERO_VAULT).build());

		SALVAGED_TECH = item("salvaged_tech", new LootMaterialItem(1, HeroRarity.props(HeroRarity.COMMON)));
		HERO_ALLOY_SCRAP = item("hero_alloy_scrap", new LootMaterialItem(2, HeroRarity.props(HeroRarity.UNCOMMON)));
		HERO_ALLOY_INGOT = item("hero_alloy_ingot", new LootMaterialItem(2, HeroRarity.props(HeroRarity.UNCOMMON)));
		VIBRANIUM_SHARD = item("vibranium_shard", new LootMaterialItem(3, HeroRarity.props(HeroRarity.RARE).fireResistant()));
		CELESTIAL_CORE = item("celestial_core", new LootMaterialItem(4, HeroRarity.props(HeroRarity.EPIC).fireResistant().stacksTo(16)
				.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

		ENERGY_CELL = item("energy_cell", new SupplyItem(SupplyItem.Kind.ENERGY_CELL, HeroRarity.props(HeroRarity.UNCOMMON).stacksTo(16)));
		COOLDOWN_STIM = item("cooldown_stim", new SupplyItem(SupplyItem.Kind.COOLDOWN_STIM, HeroRarity.props(HeroRarity.RARE).stacksTo(16)));
		SUIT_REPAIR_KIT = item("suit_repair_kit", new SupplyItem(SupplyItem.Kind.SUIT_REPAIR_KIT, HeroRarity.props(HeroRarity.UNCOMMON).stacksTo(16)));
		MED_KIT = item("med_kit", new SupplyItem(SupplyItem.Kind.MED_KIT, HeroRarity.props(HeroRarity.COMMON).stacksTo(16)));

		LOCATOR = item("locator", new LocatorItem(HeroRarity.props(HeroRarity.UNCOMMON).stacksTo(1)
				.component(LOCATOR_TARGET, LocatorTarget.unbound(LocatorTarget.MIN_TIER))));
	}

	/** A Hero Vault item that places a vault of this tier (creative / operators). */
	public static ItemStack vaultStack(int tier) {
		int t = Math.max(HeroVault.MIN_TIER, Math.min(HeroVault.MAX_TIER, tier));
		ItemStack stack = new ItemStack(HERO_VAULT_ITEM);
		stack.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of(HeroVaultBlock.TIER.getName(), Integer.toString(t))));
		stack.set(HeroRarity.COMPONENT, switch (t) {
			case 2 -> HeroRarity.UNCOMMON;
			case 3 -> HeroRarity.RARE;
			case 4 -> HeroRarity.EPIC;
			default -> HeroRarity.COMMON;
		});
		return stack;
	}

	/** The tier a Hero Vault item places (its block_state component), 1 when unset. */
	public static int vaultTier(ItemStack stack) {
		BlockItemStateProperties props = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
		Integer t = props.get(HeroVaultBlock.TIER);
		return t == null ? 1 : t;
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		for (int t = 1; t <= 4; t++) {
			output.accept(vaultStack(t));
		}
		for (Item i : new Item[] { SALVAGED_TECH, HERO_ALLOY_SCRAP, HERO_ALLOY_INGOT, VIBRANIUM_SHARD, CELESTIAL_CORE,
				ENERGY_CELL, COOLDOWN_STIM, SUIT_REPAIR_KIT, MED_KIT }) {
			output.accept(i);
		}
		for (int t = LocatorTarget.MIN_TIER; t <= LocatorTarget.MAX_TIER; t++) {
			output.accept(LocatorItem.forTier(t));
		}
	}

	private static Item item(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}
}
