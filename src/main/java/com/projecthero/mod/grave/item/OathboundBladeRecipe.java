package com.projecthero.mod.grave.item;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/**
 * v0.14.23: Broken Oath (template) + Necrotic Blade (base) + Gravebound Ingot (addition) at a smithing table gives an
 * Oathbound Necrotic Blade -- the same stack (enchantments, name, durability all kept) with
 * {@link NecroticBladeItem#makeOathbound} applied. A special recipe rather than a JSON smithing_transform because a plain
 * one cannot refuse a blade that is already Oathbound, which would just eat a Broken Oath for nothing.
 */
public class OathboundBladeRecipe implements SmithingRecipe {
	public static RecipeSerializer<OathboundBladeRecipe> SERIALIZER;
	public static final OathboundBladeRecipe INSTANCE = new OathboundBladeRecipe();

	@Override
	public boolean isTemplateIngredient(ItemStack stack) {
		return stack.is(GraveItems.BROKEN_OATH);
	}

	@Override
	public boolean isBaseIngredient(ItemStack stack) {
		return stack.is(GraveItems.NECROTIC_BLADE) && !NecroticBladeItem.isOathbound(stack);
	}

	@Override
	public boolean isAdditionIngredient(ItemStack stack) {
		return stack.is(GraveItems.GRAVEBOUND_INGOT);
	}

	@Override
	public boolean matches(SmithingRecipeInput input, Level level) {
		return isTemplateIngredient(input.template()) && isBaseIngredient(input.base())
				&& isAdditionIngredient(input.addition());
	}

	@Override
	public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
		ItemStack out = input.base().copyWithCount(1);
		NecroticBladeItem.makeOathbound(out);
		return out;
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		ItemStack out = new ItemStack(GraveItems.NECROTIC_BLADE);
		NecroticBladeItem.makeOathbound(out);
		return out;
	}

	@Override
	public boolean isIncomplete() {
		return false;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}

	/** No fields: the recipe JSON is just {@code {"type": "projecthero:oathbound_blade"}}. */
	public static final class Serializer implements RecipeSerializer<OathboundBladeRecipe> {
		private static final MapCodec<OathboundBladeRecipe> CODEC = MapCodec.unit(INSTANCE);
		private static final StreamCodec<RegistryFriendlyByteBuf, OathboundBladeRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public MapCodec<OathboundBladeRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, OathboundBladeRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
