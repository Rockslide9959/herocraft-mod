package com.projecthero.mod.city;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerLevel;
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
 * v0.16.2 City part B: The Daily Bulletin, sold by the librarians at the City's newspaper office. Reading it opens a
 * book screen with today's front page: every crime, world event and raid within {@code bulletin.reportRange} blocks,
 * nearest first, with directions from where you stand. It is printed fresh each time it is opened.
 */
public class DailyBulletinItem extends Item {
	public DailyBulletinItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			stack.set(DataComponents.WRITTEN_BOOK_CONTENT, DailyBulletin.content(sl, sp.blockPosition()));
			sp.containerMenu.broadcastChanges();
			sp.connection.send(new ClientboundOpenBookPacket(hand));
			level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.daily_bulletin.desc").withStyle(ChatFormatting.GRAY));
	}
}
