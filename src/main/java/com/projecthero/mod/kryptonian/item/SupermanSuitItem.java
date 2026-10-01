package com.projecthero.mod.kryptonian.item;

import java.util.List;

import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.kryptonian.SupermanSuit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.14.9: a piece of the craftable Superman Suit -- the user's {@code superman.bbmodel} on the shared GeckoLib armour
 * path ({@code geo/superman.geo.json} over {@code textures/armor/superman.png}); the chestplate also brings the cloth
 * cape ({@code SupermanCapeLayer}). Only a Kryptonian can wear it: see {@link SupermanSuit}.
 */
public class SupermanSuitItem extends SuperheroArmorItem {
	public SupermanSuitItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return SupermanSuit.SET_ID;
	}

	/** Right-click to wear it: only a Kryptonian (anyone else is told why). */
	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (!SupermanSuit.mayWear(player)) {
			if (!level.isClientSide()) {
				SupermanSuit.refuse(player);
			}
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		return super.use(level, player, hand);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.superman_suit.tooltip").withStyle(ChatFormatting.GOLD));
		if (getType() == Type.HELMET) {
			tooltip.add(Component.translatable("item.projecthero.superman_suit_helmet.tooltip").withStyle(ChatFormatting.GRAY));
		} else if (getType() == Type.CHESTPLATE) {
			tooltip.add(Component.translatable("item.projecthero.superman_suit_chestplate.tooltip").withStyle(ChatFormatting.GRAY));
		}
	}
}
