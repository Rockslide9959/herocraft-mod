package com.projecthero.mod.syndicate.item;

import java.util.List;

import com.projecthero.mod.event.raid.SupervillainMark;

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
 * v0.14.25: the Kingpin's file on the next supervillain he's selling a town to. Read it and you are given the
 * Supervillain's Mark ({@link SupervillainMark#mark}) -- walk into a village and a Supervillain Raid follows, no
 * Pillager Spy needed. One use. Refused on Peaceful and while you already carry the Mark.
 */
public class VillainDossierItem extends Item {
	public VillainDossierItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(stack);
		}
		if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			sp.displayClientMessage(Component.translatable("item.projecthero.villain_dossier.peaceful").withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(stack);
		}
		if (SupervillainMark.isMarked(sp) || !SupervillainMark.mark(sp)) {
			sp.displayClientMessage(Component.translatable("item.projecthero.villain_dossier.already").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
		sp.displayClientMessage(Component.translatable("item.projecthero.villain_dossier.read").withStyle(ChatFormatting.DARK_RED), false);
		if (!sp.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.villain_dossier.tooltip").withStyle(ChatFormatting.GRAY));
	}
}
