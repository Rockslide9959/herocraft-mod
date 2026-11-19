package com.projecthero.mod.backpack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * v0.17.5 backpacks: a shaped recipe that carries the backpack in the grid over to the result -- contents, dye, anvil name and
 * all -- so crafting a ring of ingots around a backpack upgrades it without emptying it. JSON type
 * {@code projecthero:backpack_upgrade}, same fields as {@code minecraft:crafting_shaped}.
 */
public class BackpackUpgradeRecipe extends ShapedRecipe {
	private final ShapedRecipePattern pattern;
	private final ItemStack result;

	public BackpackUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification) {
		super(group, category, pattern, result, showNotification);
		this.pattern = pattern;
		this.result = result;
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		ItemStack out = super.assemble(input, registries);
		for (int i = 0; i < input.size(); i++) {
			ItemStack in = input.getItem(i);
			if (BackpackItem.isBackpack(in)) {
				out.applyComponents(in.getComponentsPatch());
				break;
			}
		}
		return out;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return BackpackMod.UPGRADE_SERIALIZER;
	}

	public static final class Serializer implements RecipeSerializer<BackpackUpgradeRecipe> {
		private static final MapCodec<BackpackUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
				CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ShapedRecipe::category),
				ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
				ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
				Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification))
				.apply(i, BackpackUpgradeRecipe::new));

		private static final StreamCodec<RegistryFriendlyByteBuf, BackpackUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ShapedRecipe::getGroup,
				CraftingBookCategory.STREAM_CODEC, ShapedRecipe::category,
				ShapedRecipePattern.STREAM_CODEC, r -> r.pattern,
				ItemStack.STREAM_CODEC, r -> r.result,
				ByteBufCodecs.BOOL, ShapedRecipe::showNotification,
				BackpackUpgradeRecipe::new);

		@Override
		public MapCodec<BackpackUpgradeRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, BackpackUpgradeRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
