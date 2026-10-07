package com.projecthero.mod.hero.item;

import java.util.List;

import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.mutation.ModSerums;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * v0.15.11: piecing the notes back together. Four Damaged Research Notes about the same power plus a Glass Bottle, in
 * any slots of a crafting grid, make that power's serum (the same potion the brewing route makes). Four blank notes
 * plus a bottle make a Mutagenic Serum (a random enabled Experimental power the drinker does not have yet). A note for
 * a disabled power reads as blank ({@link ResearchNoteItem#notePower}), so it counts with the blank notes.
 *
 * <p>Which notes go together lives in the {@code research_power} data component, which a plain JSON recipe cannot
 * check, so this is a special recipe with its own serializer ({@code projecthero:research_note_serum}). Like vanilla's
 * special recipes it is not in the recipe book: the note's tooltip and the guidebook's mutation chapter say how.
 */
public class ResearchNoteSerumRecipe extends CustomRecipe {
	public static RecipeSerializer<ResearchNoteSerumRecipe> SERIALIZER;

	/** How many notes one serum takes. */
	public static final int NOTES = 4;

	public ResearchNoteSerumRecipe(CraftingBookCategory category) {
		super(category);
	}

	/**
	 * What {@code items} (a crafting grid, empties allowed) would make, or {@link ItemStack#EMPTY} when it is not this
	 * recipe: exactly four research notes that all share one power (or are all blank) and exactly one glass bottle,
	 * nothing else. Public so tests can check any grid.
	 */
	public static ItemStack result(List<ItemStack> items) {
		int notes = 0;
		int bottles = 0;
		Power power = null;
		boolean first = true;
		for (ItemStack stack : items) {
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(Items.GLASS_BOTTLE)) {
				bottles++;
				continue;
			}
			if (HeroPackItems.RESEARCH_NOTE == null || !stack.is(HeroPackItems.RESEARCH_NOTE)) {
				return ItemStack.EMPTY;
			}
			Power p = ResearchNoteItem.notePower(stack);
			if (first) {
				power = p;
				first = false;
			} else if (p != power) {
				return ItemStack.EMPTY; // notes about different powers (or blank mixed with written) do not go together
			}
			notes++;
		}
		if (notes != NOTES || bottles != 1) {
			return ItemStack.EMPTY;
		}
		if (power == null) {
			return RandomPowerSerumItem.EXPERIMENTAL_SERUM == null ? ItemStack.EMPTY
					: new ItemStack(RandomPowerSerumItem.EXPERIMENTAL_SERUM);
		}
		var serum = ModSerums.serum(power);
		return serum == null ? ItemStack.EMPTY : PotionContents.createItemStack(Items.POTION, serum);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return !result(input.items()).isEmpty();
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		return result(input.items());
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= NOTES + 1;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}
}
