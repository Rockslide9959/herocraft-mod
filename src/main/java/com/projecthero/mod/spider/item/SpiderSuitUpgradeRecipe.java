package com.projecthero.mod.spider.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * v0.16.2: a shaped crafting recipe that keeps the original Spider-Man Suit piece. Crafting a Suit v2 piece needs the
 * matching original piece in the grid, but the original stays there afterwards (its damage and enchantments untouched)
 * -- {@link #getRemainingItems} hands it back, the way a bucket of milk leaves its bucket. Everything else (the
 * pattern, the recipe book, JSON loading) is vanilla's shaped recipe: {@code "type": "projecthero:spider_suit_upgrade"}
 * with the usual {@code pattern} / {@code key} / {@code result}.
 */
public class SpiderSuitUpgradeRecipe extends ShapedRecipe {
	private final ShapedRecipePattern keptPattern;
	private final ItemStack keptResult;

	public SpiderSuitUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result,
			boolean showNotification) {
		super(group, category, pattern, result, showNotification);
		this.keptPattern = pattern;
		this.keptResult = result;
	}

	/** An original (red) Spider-Man Suit piece -- not the v2 one, not the Symbiote's. */
	public static boolean isOriginalSuitPiece(ItemStack stack) {
		return stack.getItem() instanceof SpiderManArmorItem && !(stack.getItem() instanceof SpiderManV2ArmorItem)
				&& !(stack.getItem() instanceof SymbioteArmorItem) && !(stack.getItem() instanceof SpiderManSkinSuitArmorItem);
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> remaining = super.getRemainingItems(input);
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (isOriginalSuitPiece(stack)) {
				remaining.set(i, stack.copyWithCount(1)); // the old suit stays in the grid
			}
		}
		return remaining;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}

	public static final MapCodec<SpiderSuitUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
			CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.EQUIPMENT).forGetter(ShapedRecipe::category),
			ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.keptPattern),
			ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.keptResult),
			Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification))
			.apply(i, SpiderSuitUpgradeRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpiderSuitUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
			(buf, r) -> {
				ByteBufCodecs.STRING_UTF8.encode(buf, r.getGroup());
				buf.writeEnum(r.category());
				ShapedRecipePattern.STREAM_CODEC.encode(buf, r.keptPattern);
				ItemStack.STREAM_CODEC.encode(buf, r.keptResult);
				buf.writeBoolean(r.showNotification());
			},
			buf -> new SpiderSuitUpgradeRecipe(ByteBufCodecs.STRING_UTF8.decode(buf), buf.readEnum(CraftingBookCategory.class),
					ShapedRecipePattern.STREAM_CODEC.decode(buf), ItemStack.STREAM_CODEC.decode(buf), buf.readBoolean()));

	public static final RecipeSerializer<SpiderSuitUpgradeRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			ProjectHeroMod.id("spider_suit_upgrade"), new RecipeSerializer<SpiderSuitUpgradeRecipe>() {
				@Override
				public MapCodec<SpiderSuitUpgradeRecipe> codec() {
					return CODEC;
				}

				@Override
				public StreamCodec<RegistryFriendlyByteBuf, SpiderSuitUpgradeRecipe> streamCodec() {
					return STREAM_CODEC;
				}
			});

	public static void initialize() {
		// loading the class registers the serializer
	}
}
