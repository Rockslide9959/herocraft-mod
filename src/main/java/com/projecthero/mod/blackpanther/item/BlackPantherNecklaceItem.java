package com.projecthero.mod.blackpanther.item;

import java.util.List;

import com.projecthero.mod.blackpanther.BlackPanther;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.7 (user: "Make the suit just a necklace, player crafts the necklace, equips it and presses H to make it transform into the
 * full suit"): the Black Panther suit folded into a vibranium tooth necklace. Equipped like the Flash Ring holding the Flash Suit:
 * a wearable item in the CHESTPLATE slot (right-click it, shift-click it or drag it there), so death, keepInventory and graves
 * treat it like armour. Worn by the Black Panther, H lets the suit out of it and H again draws it back
 * ({@code BlackPanther.toggleSuit}); taking it off draws the suit back too. It is not drawn on the player unless the suit is on.
 */
public class BlackPantherNecklaceItem extends Item implements Equipable {
	public BlackPantherNecklaceItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (!BlackPanther.hasPower(player)) {
			// as the Flash Ring: only its hero puts it on (the client sees the synced state too, so both sides agree)
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.black_panther.necklace_refuse")
						.withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		return swapWithEquipmentSlot(this, level, player, hand);
	}

	@Override
	public EquipmentSlot getEquipmentSlot() {
		return EquipmentSlot.CHEST;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.black_panther_necklace.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.projecthero.black_panther_necklace.tooltip2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.black_panther_necklace.tooltip3").withStyle(ChatFormatting.DARK_GRAY));
	}
}
