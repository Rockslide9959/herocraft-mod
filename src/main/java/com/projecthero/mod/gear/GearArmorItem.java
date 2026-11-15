package com.projecthero.mod.gear;

import java.util.List;

import com.google.common.base.Suppliers;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * v0.17.5: a Hero Alloy / Vibranium / Celestial armour piece. Vibranium and Celestial pieces each cut fall damage by
 * {@link #FALL_CUT_PER_PIECE} (additive on the base: a full set takes half), through the vanilla
 * {@code fall_damage_multiplier} attribute -- no damage hook needed.
 */
public class GearArmorItem extends ArmorItem {
	public static final double FALL_CUT_PER_PIECE = 0.125;

	private final int gearTier;
	private final String set;
	private final com.google.common.base.Supplier<ItemAttributeModifiers> modifiers;

	public GearArmorItem(Holder<ArmorMaterial> material, Type type, int gearTier, String set, Properties properties) {
		super(material, type, properties);
		this.gearTier = gearTier;
		this.set = set;
		this.modifiers = Suppliers.memoize(() -> {
			ItemAttributeModifiers base = super.getDefaultAttributeModifiers();
			if (!absorbsFalls()) {
				return base;
			}
			ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
			for (ItemAttributeModifiers.Entry e : base.modifiers()) {
				b.add(e.attribute(), e.modifier(), e.slot());
			}
			b.add(Attributes.FALL_DAMAGE_MULTIPLIER, new AttributeModifier(ProjectHeroMod.id("gear_fall_cut." + type.getName()),
					-FALL_CUT_PER_PIECE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.bySlot(type.getSlot()));
			return b.build();
		});
	}

	public int gearTier() {
		return gearTier;
	}

	public String set() {
		return set;
	}

	/** Vibranium soaks impacts, and Celestial plate is forged on Vibranium. */
	public boolean absorbsFalls() {
		return gearTier >= 3;
	}

	@Override
	public ItemAttributeModifiers getDefaultAttributeModifiers() {
		return modifiers.get();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		GearTooltip.add(tooltip, gearTier, "projecthero.gear.set." + set + ".hint");
		if (gearTier >= 4) {
			tooltip.add(Component.translatable("projecthero.gear.set.celestial.bonus").withStyle(ChatFormatting.GOLD));
		}
	}
}
