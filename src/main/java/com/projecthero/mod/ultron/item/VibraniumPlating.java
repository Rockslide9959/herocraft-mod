package com.projecthero.mod.ultron.item;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/**
 * v0.15.12: <b>Vibranium Plating</b> on Iron Man armour. In a smithing table -- an Iron Man armour piece in the base slot,
 * a Vibranium Plating in the addition slot, no template -- the piece comes out <em>plated</em>: +2 armour and +1 toughness
 * for that slot, stored on the stack as the {@link #PLATED} component (which the tooltip and the I-key spec sheet read) and
 * as extra attribute modifiers on top of the piece's own. A piece can be plated once. Iron Man armour only (user call).
 *
 * <p>TODO (spec): the plating will also craft a Vibranium Shield variant later.
 */
public final class VibraniumPlating {
	public static final double ARMOR_BONUS = 2.0;
	public static final double TOUGHNESS_BONUS = 1.0;

	/** On an Iron Man armour piece: it has been plated. */
	public static final DataComponentType<Boolean> PLATED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("vibranium_plated"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	public static RecipeSerializer<Recipe> SERIALIZER;

	private VibraniumPlating() {
	}

	public static void initialize() {
		SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ProjectHeroMod.id("vibranium_plating"), new Serializer());
	}

	public static boolean isPlated(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(PLATED));
	}

	/** Can {@code stack} take a plating? An unplated Iron Man armour piece. */
	public static boolean canPlate(ItemStack stack) {
		return stack.getItem() instanceof IronManArmorItem && !isPlated(stack);
	}

	/** A plated copy of {@code base} (one item). */
	public static ItemStack plate(ItemStack base, HolderLookup.Provider registries) {
		ItemStack out = base.copyWithCount(1);
		if (!(out.getItem() instanceof ArmorItem armor)) {
			return out;
		}
		ItemAttributeModifiers current = modifiers(out, armor);
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(armor.getEquipmentSlot());
		String slot = armor.getType().getName();
		ResourceLocation armorId = ProjectHeroMod.id("vibranium_armor_" + slot);
		ResourceLocation toughId = ProjectHeroMod.id("vibranium_toughness_" + slot);
		ItemAttributeModifiers plated = current
				.withModifierAdded(Attributes.ARMOR, new AttributeModifier(armorId, ARMOR_BONUS, AttributeModifier.Operation.ADD_VALUE), group)
				.withModifierAdded(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(toughId, TOUGHNESS_BONUS, AttributeModifier.Operation.ADD_VALUE),
						group);
		out.set(DataComponents.ATTRIBUTE_MODIFIERS, plated);
		out.set(PLATED, true);
		return out;
	}

	/** The total ARMOR modifier on {@code stack} for its own slot (tests / spec sheet). */
	public static double armorValue(ItemStack stack) {
		if (!(stack.getItem() instanceof ArmorItem armor)) {
			return 0;
		}
		double[] sum = { 0 };
		modifiers(stack, armor).forEach(armor.getEquipmentSlot(),
				(attr, mod) -> {
					if (attr.is(Attributes.ARMOR) && mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
						sum[0] += mod.amount();
					}
				});
		return sum[0];
	}

	/** The stack's own modifiers -- or, like vanilla, the item's defaults when the component is empty. */
	private static ItemAttributeModifiers modifiers(ItemStack stack, ArmorItem armor) {
		ItemAttributeModifiers m = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return m.modifiers().isEmpty() ? armor.getDefaultAttributeModifiers() : m;
	}

	/** The tooltip line on a plated piece. */
	public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
		if (isPlated(stack)) {
			tooltip.add(Component.translatable("item.projecthero.vibranium_plated").withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	// ---------------------------------------------------------------- the smithing recipe

	/** Base: an unplated Iron Man piece; addition: Vibranium Plating; template: none. */
	public static final class Recipe implements SmithingRecipe {
		@Override
		public boolean isTemplateIngredient(ItemStack stack) {
			return false; // no template: the slot stays empty
		}

		@Override
		public boolean isBaseIngredient(ItemStack stack) {
			return canPlate(stack);
		}

		@Override
		public boolean isAdditionIngredient(ItemStack stack) {
			return UltronItems.VIBRANIUM_PLATING != null && stack.is(UltronItems.VIBRANIUM_PLATING);
		}

		@Override
		public boolean matches(SmithingRecipeInput input, Level level) {
			return input.template().isEmpty() && canPlate(input.base()) && isAdditionIngredient(input.addition());
		}

		@Override
		public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
			return plate(input.base(), registries);
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
