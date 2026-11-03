package com.projecthero.mod.kryptonian.item;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * v0.17.2 (user: "make it so that you can craft tools from kryptonite, make them have iron level durability but diamond level
 * efficiency and power and stuff, make it deal 2 times damage to kryptonian beings(this includes doomsday please), should
 * completely bypass their defences, should stop their healing and powers for 20 seconds after getting hit 3 times"): the
 * kryptonite sword, pickaxe, axe, shovel and hoe. Iron's durability, diamond's everything else; what they do to Kryptonians is
 * {@link com.projecthero.mod.kryptonian.KryptoniteStrikes}.
 */
public final class KryptoniteTools {
	/** Iron uses (250); diamond speed, damage bonus, enchantability and mining level. Repaired with kryptonite shards. */
	public static final Tier TIER = new Tier() {
		@Override
		public int getUses() {
			return 250;
		}

		@Override
		public float getSpeed() {
			return 8.0f;
		}

		@Override
		public float getAttackDamageBonus() {
			return 3.0f;
		}

		@Override
		public TagKey<Block> getIncorrectBlocksForDrops() {
			return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
		}

		@Override
		public int getEnchantmentValue() {
			return 10;
		}

		@Override
		public Ingredient getRepairIngredient() {
			return Ingredient.of(KryptonianItems.KRYPTONITE_SHARD);
		}
	};

	public static Item SWORD;
	public static Item PICKAXE;
	public static Item AXE;
	public static Item SHOVEL;
	public static Item HOE;

	private KryptoniteTools() {
	}

	/** After {@link KryptonianItems#KRYPTONITE_SHARD} is registered (the repair ingredient). */
	public static void initialize() {
		SWORD = register("kryptonite_sword", new SwordItem(TIER, props().attributes(SwordItem.createAttributes(TIER, 3, -2.4f))));
		PICKAXE = register("kryptonite_pickaxe", new PickaxeItem(TIER, props().attributes(DiggerItem.createAttributes(TIER, 1.0f, -2.8f))));
		AXE = register("kryptonite_axe", new AxeItem(TIER, props().attributes(DiggerItem.createAttributes(TIER, 5.0f, -3.0f))));
		SHOVEL = register("kryptonite_shovel", new ShovelItem(TIER, props().attributes(DiggerItem.createAttributes(TIER, 1.5f, -3.0f))));
		HOE = register("kryptonite_hoe", new HoeItem(TIER, props().attributes(DiggerItem.createAttributes(TIER, -3.0f, 0.0f))));
	}

	private static Item.Properties props() {
		return new Item.Properties().rarity(Rarity.UNCOMMON);
	}

	private static Item register(String name, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(name), item);
	}

	/** One of the five kryptonite tools. */
	public static boolean isTool(ItemStack stack) {
		if (stack.isEmpty() || SWORD == null) {
			return false;
		}
		Item i = stack.getItem();
		return i == SWORD || i == PICKAXE || i == AXE || i == SHOVEL || i == HOE;
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(SWORD);
		output.accept(PICKAXE);
		output.accept(AXE);
		output.accept(SHOVEL);
		output.accept(HOE);
	}
}
