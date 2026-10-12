package com.projecthero.mod.ultron.contagion.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ultron.item.VibraniumPlating;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
 * v0.16.1: <b>Vibranium Plating II</b> on Iron Man armour -- +3 armour and +2 toughness on that piece's slot, and it
 * <em>replaces</em> Vibranium Plating I (the plating I modifiers and component come off). Smithing table, no template:
 * base = an Iron Man piece without plating II, addition = Vibranium Plating II. Stored as the {@link #PLATED_2} component.
 */
public final class VibraniumPlatingII {
	public static final double ARMOR_BONUS = 3.0;
	public static final double TOUGHNESS_BONUS = 2.0;

	public static final DataComponentType<Boolean> PLATED_2 = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("vibranium_plated_2"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	public static RecipeSerializer<Recipe> SERIALIZER;

	private VibraniumPlatingII() {
	}

	public static void initialize() {
		SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ProjectHeroMod.id("vibranium_plating_2"), new Serializer());
	}

	public static boolean isPlated(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(PLATED_2));
	}

	public static boolean canPlate(ItemStack stack) {
		return stack.getItem() instanceof IronManArmorItem && !isPlated(stack);
	}

	/** A plating II copy of {@code base}: the plating I bonus (if any) is replaced, not stacked. */
	public static ItemStack plate(ItemStack base) {
		ItemStack out = base.copyWithCount(1);
		if (!(out.getItem() instanceof ArmorItem armor)) {
			return out;
		}
		ItemAttributeModifiers current = out.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		if (current.modifiers().isEmpty()) {
			current = armor.getDefaultAttributeModifiers();
		}
		String slot = armor.getType().getName();
		ResourceLocation oldArmor = ProjectHeroMod.id("vibranium_armor_" + slot);
		ResourceLocation oldTough = ProjectHeroMod.id("vibranium_toughness_" + slot);
		ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
		for (ItemAttributeModifiers.Entry e : current.modifiers()) {
			if (!e.modifier().id().equals(oldArmor) && !e.modifier().id().equals(oldTough)) {
				b.add(e.attribute(), e.modifier(), e.slot());
			}
		}
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(armor.getEquipmentSlot());
		b.add(Attributes.ARMOR, new AttributeModifier(ProjectHeroMod.id("vibranium2_armor_" + slot), ARMOR_BONUS, AttributeModifier.Operation.ADD_VALUE),
				group);
		b.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(ProjectHeroMod.id("vibranium2_toughness_" + slot), TOUGHNESS_BONUS,
				AttributeModifier.Operation.ADD_VALUE), group);
		out.set(DataComponents.ATTRIBUTE_MODIFIERS, b.build());
		out.remove(VibraniumPlating.PLATED);
		out.set(PLATED_2, true);
		return out;
	}

	public static final class Recipe implements SmithingRecipe {
		@Override
		public boolean isTemplateIngredient(ItemStack stack) {
			return false;
		}

		@Override
		public boolean isBaseIngredient(ItemStack stack) {
			return canPlate(stack);
		}

		@Override
		public boolean isAdditionIngredient(ItemStack stack) {
			return ContagionItems.VIBRANIUM_PLATING_2 != null && stack.is(ContagionItems.VIBRANIUM_PLATING_2);
		}

		@Override
		public boolean matches(SmithingRecipeInput input, Level level) {
			return input.template().isEmpty() && canPlate(input.base()) && isAdditionIngredient(input.addition());
		}

		@Override
		public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
			return plate(input.base());
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
