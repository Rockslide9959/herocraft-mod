package com.projecthero.mod.ultron.contagion.item;

import java.util.List;

import com.projecthero.mod.supersoldier.item.AdamantiumShieldItem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.16.1: the <b>Vibranium Shield</b> (the v0.15.12 TODO) -- a better tier of the Super Soldier's Adamantium Shield. It
 * is one (it extends {@link AdamantiumShieldItem}: a real, unbreakable shield, round, and the Super Soldier's C throw uses
 * it), and thrown it flies further (v0.17.7: the same 5 bounces and, by the user, the same 6 damage) ({@link #THROW_DAMAGE}, {@link #THROW_BOUNCES},
 * {@link #THROW_RANGE}; Adamantium: 6 / 5 / 25). Forged from Vibranium Ingots and an Adamantium Shield.
 */
public class VibraniumShieldItem extends AdamantiumShieldItem {
	public static final float THROW_DAMAGE = com.projecthero.mod.supersoldier.SuperSoldierConfig.VIBRANIUM_SHIELD_DAMAGE; // v0.17.7 (user): 6 like every shield (was 10)
	public static final int THROW_BOUNCES = 5; // v0.17.7: bounces after the first hit (was 5 hits in all)
	public static final double THROW_RANGE = 30.0;

	public VibraniumShieldItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.vibranium_shield.desc1").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.vibranium_shield.desc2").withStyle(ChatFormatting.LIGHT_PURPLE));
	}
}
