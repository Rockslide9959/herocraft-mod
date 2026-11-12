package com.projecthero.mod.starfire.item;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.3 Starfire: one piece of her classic Teen Titans outfit (the purple crop top with the green gem collar and silver arm
 * bands, the purple skirt and belt, the purple thigh-high boots). Craftable, anyone may wear it, iron-level. Drawn from a 64x64
 * costume skin ({@link #texture}, wide or slim by the wearer's model; transparent where the wearer's own skin shows) by the
 * shared skin-costume path ({@code client.quirk.QuirkCostume}); the vanilla armour model is never drawn. No powers of its own --
 * the power itself never changes the skin.
 */
public class StarfireSuitItem extends ArmorItem {
	public static final ResourceLocation WIDE = ProjectHeroMod.id("textures/entity/starfire/starfire_suit_wide.png");
	public static final ResourceLocation SLIM = ProjectHeroMod.id("textures/entity/starfire/starfire_suit_slim.png");

	public StarfireSuitItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	public static ResourceLocation texture(boolean slim) {
		return slim ? SLIM : WIDE;
	}

	public static boolean wornIn(LivingEntity e, EquipmentSlot slot) {
		return e.getItemBySlot(slot).getItem() instanceof StarfireSuitItem;
	}

	public static boolean anyWorn(LivingEntity e) {
		return wornIn(e, EquipmentSlot.CHEST) || wornIn(e, EquipmentSlot.LEGS) || wornIn(e, EquipmentSlot.FEET);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.starfire_suit.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
	}
}
