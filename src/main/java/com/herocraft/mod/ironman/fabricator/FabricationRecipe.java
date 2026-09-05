package com.herocraft.mod.ironman.fabricator;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One Stark Fabricator recipe. Deliberately a lightweight in-code definition (spec section 10:
 * "create a lightweight internal implementation instead of bringing in a huge dependency") rather
 * than a datapack {@code RecipeType} -- the Fabricator is not a vanilla crafting surface and its
 * inputs are order-independent stacks, not a shaped/shapeless grid.
 *
 * @param id                 stable id, also the lang key suffix
 * @param inputs             required input stacks (item + count); matched against the 9 input slots
 * @param energyCost         Stark-energy consumed on completion
 * @param timeTicks          fabrication time (spec section 11: higher tier = longer)
 * @param result             the produced stack
 * @param requiredTechLevel  minimum {@link com.herocraft.mod.ironman.TonyStark} tech level; also the
 *                           minimum blueprint tier that must sit in the blueprint slot
 * @param marksBuilt         non-null suit id if completing this recipe counts as "developing" that
 *                           suit (advances the technology tree)
 */
public record FabricationRecipe(
		String id,
		List<Input> inputs,
		int energyCost,
		int timeTicks,
		ItemStack result,
		int requiredTechLevel,
		String marksBuilt) {

	public record Input(Item item, int count) {
	}

	public static FabricationRecipe of(String id, ItemStack result, int energyCost, int timeTicks,
			int requiredTechLevel, Input... inputs) {
		return new FabricationRecipe(id, List.of(inputs), energyCost, timeTicks, result, requiredTechLevel, null);
	}

	public FabricationRecipe developing(String suitId) {
		return new FabricationRecipe(id, inputs, energyCost, timeTicks, result, requiredTechLevel, suitId);
	}

	public static Input in(Item item, int count) {
		return new Input(item, count);
	}

	/** Does the container's input region (slots 0..8) hold enough of every required input? */
	public boolean matches(Container container) {
		for (Input input : inputs) {
			int found = 0;
			for (int slot = 0; slot < 9; slot++) {
				ItemStack stack = container.getItem(slot);
				if (stack.is(input.item())) {
					found += stack.getCount();
				}
			}
			if (found < input.count()) {
				return false;
			}
		}
		return true;
	}

	/** Consume the inputs from slots 0..8. Assumes {@link #matches} already returned true. */
	public void consume(Container container) {
		for (Input input : inputs) {
			int remaining = input.count();
			for (int slot = 0; slot < 9 && remaining > 0; slot++) {
				ItemStack stack = container.getItem(slot);
				if (stack.is(input.item())) {
					int take = Math.min(remaining, stack.getCount());
					stack.shrink(take);
					remaining -= take;
				}
			}
		}
	}
}
