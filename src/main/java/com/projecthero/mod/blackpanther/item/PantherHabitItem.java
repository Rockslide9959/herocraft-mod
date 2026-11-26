package com.projecthero.mod.blackpanther.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Panther Habit: v0.17.4-0.17.6's folded suit, bound into the necklace. v0.17.7 made the suit four wearable armour pieces
 * ({@link PantherSuitItem}), so the habit has no recipe any more and is only kept so old stacks do not vanish: using it unfolds it
 * into the four pieces (anyone may). Not in the creative tab.
 */
public class PantherHabitItem extends Item {
	public PantherHabitItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(stack);
		}
		stack.consume(1, player);
		for (Item piece : new Item[] { BlackPantherItems.SUIT_HELMET, BlackPantherItems.SUIT_CHESTPLATE, BlackPantherItems.SUIT_LEGGINGS,
				BlackPantherItems.SUIT_BOOTS }) {
			ItemStack out = new ItemStack(piece);
			if (!sp.getInventory().add(out)) {
				sp.drop(out, false);
			}
		}
		level.playSound(null, sp.getX(), sp.getY() + 1.0, sp.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
		sp.displayClientMessage(Component.translatable("message.projecthero.black_panther.habit_unfolded").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		return InteractionResultHolder.success(stack);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.panther_habit.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.projecthero.panther_habit.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
