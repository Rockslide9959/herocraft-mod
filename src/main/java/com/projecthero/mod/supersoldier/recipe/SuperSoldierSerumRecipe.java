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
 * v0.14.8: the Unrefined Super Soldier Serum -- a shapeless crafting-table recipe of exactly three drinkable potions, one
 * of Strength, one of Swiftness and one of Leaping (the long and strong versions count too), in any slots. Vanilla
 * potions are all one item told apart by the {@code potion_contents} component, which a plain JSON recipe cannot check,
 * so this is a special recipe with its own serializer ({@code projecthero:super_soldier_serum}); like vanilla's special
 * recipes it does not appear in the recipe book.
 */
public class SuperSoldierSerumRecipe extends CustomRecipe {
	public static RecipeSerializer<SuperSoldierSerumRecipe> SERIALIZER;

	private static final List<Holder<Potion>> STRENGTH = List.of(Potions.STRENGTH, Potions.LONG_STRENGTH, Potions.STRONG_STRENGTH);
	private static final List<Holder<Potion>> SWIFTNESS = List.of(Potions.SWIFTNESS, Potions.LONG_SWIFTNESS, Potions.STRONG_SWIFTNESS);
	private static final List<Holder<Potion>> LEAPING = List.of(Potions.LEAPING, Potions.LONG_LEAPING, Potions.STRONG_LEAPING);

	public SuperSoldierSerumRecipe(CraftingBookCategory category) {
		super(category);
	}

	/** 0 = Strength, 1 = Swiftness, 2 = Leaping, -1 = anything else. */
	static int kind(ItemStack stack) {
		if (!stack.is(Items.POTION)) {
			return -1;
		}
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null || contents.potion().isEmpty() || !contents.customEffects().isEmpty()) {
			return -1;
		}
		Holder<Potion> potion = contents.potion().get();
		if (STRENGTH.stream().anyMatch(h -> h.value() == potion.value())) {
			return 0;
		}
		if (SWIFTNESS.stream().anyMatch(h -> h.value() == potion.value())) {
			return 1;
		}
		if (LEAPING.stream().anyMatch(h -> h.value() == potion.value())) {
			return 2;
		}
		return -1;
	}

	/** Exactly one of each of the three potions and nothing else. Public so tests can check any grid. */
	public static boolean matchesItems(List<ItemStack> items) {
		int[] count = new int[3];
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
		return total == 3 && count[0] == 1 && count[1] == 1 && count[2] == 1;
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
		return width * height >= 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}
}
