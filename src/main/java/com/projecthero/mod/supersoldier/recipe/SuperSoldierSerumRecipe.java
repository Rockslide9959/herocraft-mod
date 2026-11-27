package com.projecthero.mod.supersoldier.recipe;

import java.util.List;

import com.projecthero.mod.supersoldier.item.SuperSoldierItems;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * v0.14.8: the Unrefined Super Soldier Serum -- a shapeless crafting-table recipe. v0.17.7 (user: "combine a strength potion,
 * swiftness potion, regeneration potion, a golden apple and an empty glass bottle"): exactly one drinkable Potion of Strength, one
 * of Swiftness and one of Regeneration (the long and strong versions count too), one Golden Apple (a plain one -- not the
 * enchanted apple) and one empty Glass Bottle, in any slots (five items, so the crafting table's 3x3 grid only). Leaping is no
 * longer part of it. Vanilla potions are all one item told apart by the {@code potion_contents} component, which a plain JSON
 * recipe cannot check, so this is a special recipe with its own serializer ({@code projecthero:super_soldier_serum}); like
 * vanilla's special recipes it does not appear in the recipe book (JEI shows it -- {@link #displayIngredients}).
 */
public class SuperSoldierSerumRecipe extends CustomRecipe {
	public static RecipeSerializer<SuperSoldierSerumRecipe> SERIALIZER;

	public static final List<Holder<Potion>> STRENGTH = List.of(Potions.STRENGTH, Potions.LONG_STRENGTH, Potions.STRONG_STRENGTH);
	public static final List<Holder<Potion>> SWIFTNESS = List.of(Potions.SWIFTNESS, Potions.LONG_SWIFTNESS, Potions.STRONG_SWIFTNESS);
	public static final List<Holder<Potion>> REGENERATION = List.of(Potions.REGENERATION, Potions.LONG_REGENERATION,
			Potions.STRONG_REGENERATION);

	/** Kinds: one of each, nothing else. */
	static final int K_STRENGTH = 0, K_SWIFTNESS = 1, K_REGENERATION = 2, K_APPLE = 3, K_BOTTLE = 4, KINDS = 5;

	public SuperSoldierSerumRecipe(CraftingBookCategory category) {
		super(category);
	}

	/** Which of the five ingredients {@code stack} is, -1 = anything else. */
	static int kind(ItemStack stack) {
		if (stack.is(Items.GOLDEN_APPLE)) {
			return K_APPLE;
		}
		if (stack.is(Items.GLASS_BOTTLE)) {
			return K_BOTTLE;
		}
		if (!stack.is(Items.POTION)) {
			return -1; // splash / lingering potions, the enchanted golden apple, anything else
		}
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null || contents.potion().isEmpty() || !contents.customEffects().isEmpty()) {
			return -1;
		}
		Holder<Potion> potion = contents.potion().get();
		if (STRENGTH.stream().anyMatch(h -> h.value() == potion.value())) {
			return K_STRENGTH;
		}
		if (SWIFTNESS.stream().anyMatch(h -> h.value() == potion.value())) {
			return K_SWIFTNESS;
		}
		if (REGENERATION.stream().anyMatch(h -> h.value() == potion.value())) {
			return K_REGENERATION;
		}
		return -1;
	}

	/** Exactly one of each of the five ingredients and nothing else. Public so tests can check any grid. */
	public static boolean matchesItems(List<ItemStack> items) {
		int[] count = new int[KINDS];
		int total = 0;
		for (ItemStack stack : items) {
			if (stack.isEmpty()) {
				continue;
			}
			int k = kind(stack);
			if (k < 0) {
				return false;
			}
			count[k]++;
			total++;
		}
		if (total != KINDS) {
			return false;
		}
		for (int c : count) {
			if (c != 1) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The five ingredient slots for a recipe viewer (JEI): every Strength / Swiftness / Regeneration potion it accepts, the Golden
	 * Apple and the Glass Bottle.
	 */
	public static List<List<ItemStack>> displayIngredients() {
		return List.of(potions(STRENGTH), potions(SWIFTNESS), potions(REGENERATION), List.of(new ItemStack(Items.GOLDEN_APPLE)),
				List.of(new ItemStack(Items.GLASS_BOTTLE)));
	}

	private static List<ItemStack> potions(List<Holder<Potion>> kinds) {
		return kinds.stream().map(h -> PotionContents.createItemStack(Items.POTION, h)).toList();
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return matchesItems(input.items());
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		return new ItemStack(SuperSoldierItems.UNREFINED_SERUM);
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= KINDS;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}
}
