package com.projecthero.mod.ironman.sorter;

import java.util.Set;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * v0.14.16: the item categories the Stark Sorter Bot files things into.
 *
 * <p>There are two levels. The thirteen <b>fine</b> categories are what a well-stocked storage room gets (one
 * chest each). Each belongs to one of four <b>coarse</b> {@link Group}s -- Blocks / Tools &amp; Combat / Food &amp;
 * Farming / Misc -- which is what the planner falls back to when only one to three containers are in range, and
 * which it uses as the "sibling" hint when it has to merge fine categories because there are fewer chests than
 * categories (Wood merges into Stone &amp; Building before it ever merges into Food). See {@link SortPlan}.
 *
 * <p>Classification uses item classes, vanilla tags and Fabric's conventional {@code c:} tags, so other mods'
 * ingots, ores, foods and tools land in the right chest too. The order of the checks matters (rotten flesh is
 * food, but it is filed as a mob drop; a wooden button is wood, but it is filed as redstone).
 */
public enum SortCategory {
	BUILDING(Group.BLOCKS),
	WOOD(Group.BLOCKS),
	DECORATION(Group.BLOCKS),
	COMBAT(Group.GEAR),
	TOOLS(Group.GEAR),
	FOOD(Group.FOOD_FARMING),
	FARMING(Group.FOOD_FARMING),
	ORES(Group.MISC),
	REDSTONE(Group.MISC),
	MOB_DROPS(Group.MISC),
	BREWING(Group.MISC),
	MOD_ITEMS(Group.MISC),
	MISC(Group.MISC);

	/** The four coarse groups used when only a handful of containers are available. */
	public enum Group {
		BLOCKS, GEAR, FOOD_FARMING, MISC;

		public Component displayName() {
			return Component.translatable("sort_group.projecthero." + name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private final Group group;

	SortCategory(Group group) {
		this.group = group;
	}

	public Group group() {
		return group;
	}

	public Component displayName() {
		return Component.translatable("sort_category.projecthero." + name().toLowerCase(java.util.Locale.ROOT));
	}

	private static final Set<Item> BREWING_ITEMS = Set.of(Items.GLASS_BOTTLE, Items.BLAZE_POWDER, Items.NETHER_WART,
			Items.FERMENTED_SPIDER_EYE, Items.GLISTERING_MELON_SLICE, Items.MAGMA_CREAM, Items.GHAST_TEAR,
			Items.BREWING_STAND, Items.CAULDRON, Items.DRAGON_BREATH, Items.RABBIT_FOOT, Items.EXPERIENCE_BOTTLE,
			Items.PHANTOM_MEMBRANE);

	private static final Set<Item> TOOL_ITEMS = Set.of(Items.CLOCK, Items.LEAD, Items.NAME_TAG, Items.SADDLE,
			Items.MAP, Items.FILLED_MAP, Items.CARROT_ON_A_STICK, Items.WARPED_FUNGUS_ON_A_STICK, Items.BUNDLE,
			Items.WRITABLE_BOOK, Items.BOOK);

	private static final Set<Item> COMBAT_ITEMS = Set.of(Items.TOTEM_OF_UNDYING, Items.FIREWORK_ROCKET,
			Items.WIND_CHARGE, Items.FIRE_CHARGE, Items.SNOWBALL, Items.EGG);

	private static final Set<Item> MOB_DROP_ITEMS = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.INK_SAC,
			Items.GLOW_INK_SAC, Items.PRISMARINE_SHARD, Items.PRISMARINE_CRYSTALS, Items.RABBIT_HIDE,
			Items.SHULKER_SHELL, Items.HONEYCOMB, Items.ARMADILLO_SCUTE, Items.TURTLE_SCUTE, Items.NAUTILUS_SHELL,
			Items.ECHO_SHARD, Items.HEART_OF_THE_SEA, Items.WITHER_SKELETON_SKULL, Items.DRAGON_EGG);

	private static final Set<Item> REDSTONE_ITEMS = Set.of(Items.REDSTONE, Items.REDSTONE_BLOCK,
			Items.REDSTONE_TORCH, Items.REPEATER, Items.COMPARATOR, Items.PISTON, Items.STICKY_PISTON,
			Items.OBSERVER, Items.HOPPER, Items.DISPENSER, Items.DROPPER, Items.LEVER, Items.REDSTONE_LAMP,
			Items.TRIPWIRE_HOOK, Items.DAYLIGHT_DETECTOR, Items.TARGET, Items.NOTE_BLOCK, Items.TNT,
			Items.SCULK_SENSOR, Items.CALIBRATED_SCULK_SENSOR, Items.CRAFTER, Items.LIGHTNING_ROD, Items.SLIME_BLOCK,
			Items.HONEY_BLOCK, Items.TRAPPED_CHEST);

	private static final Set<Item> FARMING_ITEMS = Set.of(Items.WHEAT, Items.SUGAR_CANE, Items.BAMBOO, Items.CACTUS,
			Items.KELP, Items.VINE, Items.SHORT_GRASS, Items.TALL_GRASS, Items.FERN, Items.LARGE_FERN,
			Items.HAY_BLOCK, Items.PUMPKIN, Items.MELON, Items.LILY_PAD, Items.MOSS_BLOCK, Items.MOSS_CARPET,
			Items.COCOA_BEANS, Items.BONE_MEAL, Items.SEAGRASS, Items.SEA_PICKLE, Items.DEAD_BUSH, Items.COMPOSTER,
			Items.FARMLAND, Items.CARVED_PUMPKIN, Items.TURTLE_EGG,
			Items.SNIFFER_EGG, Items.PITCHER_POD, Items.TORCHFLOWER_SEEDS);

	private static final Set<Item> ORE_ITEMS = Set.of(Items.FLINT, Items.AMETHYST_SHARD, Items.NETHERITE_SCRAP,
			Items.CHARCOAL, Items.COAL, Items.QUARTZ, Items.LAPIS_LAZULI, Items.DIAMOND, Items.EMERALD,
			Items.ANCIENT_DEBRIS, Items.GLOWSTONE_DUST, Items.CLAY_BALL, Items.BRICK, Items.NETHER_BRICK);

	private static final Set<Item> WOOD_ITEMS = Set.of(Items.STICK, Items.BAMBOO_BLOCK, Items.STRIPPED_BAMBOO_BLOCK,
			Items.BAMBOO_MOSAIC, Items.CRAFTING_TABLE, Items.CHEST, Items.BARREL, Items.BOWL, Items.LADDER);

	private static final Set<Item> DECORATION_ITEMS = Set.of(Items.PAINTING, Items.ITEM_FRAME, Items.GLOW_ITEM_FRAME,
			Items.FLOWER_POT, Items.LANTERN, Items.SOUL_LANTERN, Items.TORCH, Items.SOUL_TORCH, Items.ARMOR_STAND,
			Items.END_ROD, Items.CHAIN, Items.BOOKSHELF, Items.CHISELED_BOOKSHELF, Items.DECORATED_POT,
			Items.SEA_LANTERN, Items.GLOWSTONE, Items.SHROOMLIGHT, Items.CAMPFIRE, Items.SOUL_CAMPFIRE,
			Items.BELL, Items.LECTERN);

	/** The fine category of {@code stack}. Never null; anything unrecognised is {@link #MISC}. */
	public static SortCategory of(ItemStack stack) {
		Item item = stack.getItem();
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		if (ProjectHeroMod.MOD_ID.equals(id.getNamespace())) {
			return MOD_ITEMS;
		}
		if (item instanceof PotionItem || BREWING_ITEMS.contains(item) || stack.is(ConventionalItemTags.POTIONS)) {
			return BREWING;
		}
		if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem
				|| item instanceof TridentItem || item instanceof ShieldItem || item instanceof ArmorItem
				|| item instanceof AnimalArmorItem || item instanceof MaceItem || stack.is(ItemTags.ARROWS)
				|| stack.is(ConventionalItemTags.ARMORS) || COMBAT_ITEMS.contains(item)
				// c:tools/melee_weapon also lists axes -- those stay with the tools
				|| (stack.is(ConventionalItemTags.MELEE_WEAPON_TOOLS) && !(item instanceof DiggerItem))
				|| stack.is(ConventionalItemTags.RANGED_WEAPON_TOOLS)) {
			return COMBAT;
		}
		if (item instanceof DiggerItem || item instanceof ShearsItem || item instanceof FishingRodItem
				|| item instanceof FlintAndSteelItem || item instanceof BrushItem || item instanceof SpyglassItem
				|| item instanceof BucketItem || item instanceof CompassItem || item == Items.MILK_BUCKET
				|| TOOL_ITEMS.contains(item) || stack.is(ConventionalItemTags.TOOLS)
				|| stack.is(ConventionalItemTags.BUCKETS)) {
			return TOOLS;
		}
		if (MOB_DROP_ITEMS.contains(item) || stack.is(ConventionalItemTags.STRINGS)
				|| stack.is(ConventionalItemTags.LEATHERS) || stack.is(ConventionalItemTags.BONES)
				|| stack.is(ConventionalItemTags.FEATHERS) || stack.is(ConventionalItemTags.GUNPOWDERS)
				|| stack.is(ConventionalItemTags.ENDER_PEARLS) || stack.is(ConventionalItemTags.SLIME_BALLS)
				|| stack.is(ConventionalItemTags.BLAZE_RODS) || stack.is(ConventionalItemTags.BREEZE_RODS)
				|| stack.is(ConventionalItemTags.NETHER_STARS) || item == Items.ENDER_EYE) {
			return MOB_DROPS;
		}
		if (stack.has(DataComponents.FOOD) || stack.is(ConventionalItemTags.FOODS)) {
			return FOOD;
		}
		if (FARMING_ITEMS.contains(item) || stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.LEAVES)
				|| stack.is(ItemTags.FLOWERS) || stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)
				|| stack.is(ConventionalItemTags.SEEDS) || stack.is(ConventionalItemTags.MUSHROOMS)
				|| stack.is(ConventionalItemTags.FERTILIZERS)) {
			return FARMING;
		}
		if (REDSTONE_ITEMS.contains(item) || item instanceof MinecartItem || stack.is(ItemTags.RAILS)
				|| stack.is(ItemTags.BUTTONS) || stack.is(ConventionalItemTags.REDSTONE_DUSTS)
				|| (item instanceof BlockItem bi && bi.getBlock().defaultBlockState().is(BlockTags.PRESSURE_PLATES))) {
			return REDSTONE;
		}
		if (ORE_ITEMS.contains(item) || stack.is(ConventionalItemTags.ORES) || stack.is(ConventionalItemTags.INGOTS)
				|| stack.is(ConventionalItemTags.GEMS) || stack.is(ConventionalItemTags.RAW_MATERIALS)
				|| stack.is(ConventionalItemTags.NUGGETS) || stack.is(ConventionalItemTags.DUSTS)
				|| stack.is(ConventionalItemTags.STORAGE_BLOCKS) || stack.is(ConventionalItemTags.RAW_BLOCKS)
				|| stack.is(ItemTags.COALS)) {
			return ORES;
		}
		if (WOOD_ITEMS.contains(item) || stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)
				|| stack.is(ItemTags.WOODEN_SLABS) || stack.is(ItemTags.WOODEN_STAIRS)
				|| stack.is(ItemTags.WOODEN_FENCES) || stack.is(ItemTags.FENCE_GATES)
				|| stack.is(ItemTags.WOODEN_DOORS) || stack.is(ItemTags.WOODEN_TRAPDOORS)
				|| stack.is(ConventionalItemTags.STRIPPED_LOGS) || stack.is(ConventionalItemTags.STRIPPED_WOODS)) {
			return WOOD;
		}
		if (item instanceof DyeItem || DECORATION_ITEMS.contains(item) || stack.is(ItemTags.BEDS)
				|| stack.is(ItemTags.BANNERS) || stack.is(ItemTags.CANDLES) || stack.is(ItemTags.WOOL)
				|| stack.is(ItemTags.WOOL_CARPETS) || stack.is(ItemTags.TERRACOTTA) || stack.is(ItemTags.SIGNS)
				|| stack.is(ItemTags.HANGING_SIGNS) || stack.is(ItemTags.SKULLS)
				|| stack.is(ConventionalItemTags.GLAZED_TERRACOTTAS) || stack.is(ConventionalItemTags.CONCRETES)
				|| stack.is(ConventionalItemTags.CONCRETE_POWDERS) || stack.is(ConventionalItemTags.GLASS_BLOCKS)
				|| stack.is(ConventionalItemTags.GLASS_PANES) || stack.is(ConventionalItemTags.DYES)
				|| stack.is(ConventionalItemTags.SHULKER_BOXES)) {
			return DECORATION;
		}
		if (item instanceof BlockItem) {
			return BUILDING;
		}
		return MISC;
	}
}
