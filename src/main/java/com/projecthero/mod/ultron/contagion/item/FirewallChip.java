package com.projecthero.mod.ultron.contagion.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/**
 * v0.16.1: the <b>Firewall Chip</b> -- an Iron Man helmet upgrade. Smithing table: an Iron Man helmet in the base slot, a
 * Firewall Chip in the addition slot, no template. The helmet gets the {@link #INSTALLED} component: that suit is immune to
 * Ultron's hijack attempts ({@code SuitHijack}). Shown in the tooltip and on the I-key spec sheet.
 */
public final class FirewallChip {
	public static final DataComponentType<Boolean> INSTALLED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("firewall_chip"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	public static RecipeSerializer<Recipe> SERIALIZER;

	private FirewallChip() {
	}

	public static void initialize() {
		SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ProjectHeroMod.id("firewall_chip"), new Serializer());
	}

	public static boolean hasChip(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(INSTALLED));
	}

	/** An Iron Man helmet without a chip. */
	public static boolean canInstall(ItemStack stack) {
		return stack.getItem() instanceof IronManArmorItem armor && armor.getType() == ArmorItem.Type.HELMET && !hasChip(stack);
	}

	public static ItemStack install(ItemStack helmet) {
		ItemStack out = helmet.copyWithCount(1);
		out.set(INSTALLED, true);
		return out;
	}

	public static final class Recipe implements SmithingRecipe {
		@Override
		public boolean isTemplateIngredient(ItemStack stack) {
			return false;
		}

		@Override
		public boolean isBaseIngredient(ItemStack stack) {
			return canInstall(stack);
		}

		@Override
		public boolean isAdditionIngredient(ItemStack stack) {
			return ContagionItems.FIREWALL_CHIP != null && stack.is(ContagionItems.FIREWALL_CHIP);
		}

		@Override
		public boolean matches(SmithingRecipeInput input, Level level) {
			return input.template().isEmpty() && canInstall(input.base()) && isAdditionIngredient(input.addition());
		}

		@Override
		public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
			return install(input.base());
		}

		@Override
		public ItemStack getResultItem(HolderLookup.Provider registries) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean isSpecial() {
			return true;
		}

		@Override
		public RecipeSerializer<?> getSerializer() {
			return SERIALIZER;
		}
	}

	static final class Serializer implements RecipeSerializer<Recipe> {
		private static final Recipe INSTANCE = new Recipe();
		private static final MapCodec<Recipe> CODEC = MapCodec.unit(INSTANCE);
		private static final StreamCodec<RegistryFriendlyByteBuf, Recipe> STREAM = StreamCodec.unit(INSTANCE);

		@Override
		public MapCodec<Recipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, Recipe> streamCodec() {
			return STREAM;
		}
	}
}
