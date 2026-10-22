package com.projecthero.mod.titanshifter.item;

import java.util.List;

import com.projecthero.mod.titanshifter.TitanShifter;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Titan Serum: using it opens the Titan picker, and the Titan picked unlocks Titan Shifting for good (v0.16.7: a
 * shifter drinking another re-forges into a different Titan). It never transforms the player by itself (the config can
 * turn that on); the Titan Shift key does that. All rules live in {@link TitanShifter#offerPick} / {@link TitanShifter#choose}.
 */
public class TitanSerumItem extends Item {
	public TitanSerumItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}
		// v0.16.1: projectheroUniquePowers -- refused, and the serum is kept
		if (!TitanShifter.isShifter(sp) && com.projecthero.mod.hero.UniquePowers.refuse(sp, TitanShifter.KEY)) {
			return InteractionResultHolder.fail(held);
		}
		// v0.16.7 (user, Titan revamp): the serum opens the Titan picker (Attack / Armoured / Female / Beast). A shifter who
		// drinks another re-forges into whichever Titan they pick. The serum is only used up once a Titan is picked.
		if (!TitanShifter.offerPick(sp)) {
			return InteractionResultHolder.fail(held);
		}
		return InteractionResultHolder.success(held);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.titan_serum.desc1").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.projecthero.titan_serum.desc2")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
