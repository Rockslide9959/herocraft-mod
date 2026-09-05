package com.herocraft.mod.ironman.item;

import java.util.List;

import com.herocraft.mod.ironman.TonyStark;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Arc Reactor: the item used to obtain the permanent Tony Stark Hero-Tier power.
 *
 * <p>Right-click, server-authoritative:
 * <ol>
 *   <li>if the player does not have {@code tony_stark}: grant it, consume one Arc Reactor, play a
 *       technological activation cue, spawn blue-white particles, briefly illuminate the player, and
 *       display "Tony Stark power acquired.";</li>
 *   <li>if they already have it: do <em>not</em> consume the reactor, and display "You already possess
 *       the Tony Stark power."</li>
 * </ol>
 *
 * <p>This is deliberately the ONLY consumer of a real power-granting Arc Reactor. Machines (the Stark
 * Fabricator, the Suit Platform) take a {@code reactor_core} instead, or accept a plain Arc Reactor
 * only through an explicit "insert energy" interaction -- so a player's original Arc Reactor is
 * specifically associated with unlocking Tony Stark and is never quietly eaten by a machine.
 */
public class ArcReactorItem extends Item {
	public ArcReactorItem(Properties properties) {
		super(properties.stacksTo(16));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.pass(stack);
		}

		// Experimental mutations and Hero-Tier powers cannot be mixed: a mutated player must give up
		// their experimental powers before the Arc Reactor will bond (use a Power Suppressor, or the
		// operator command). Commands replace freely; this natural route refuses.
		if (com.herocraft.mod.hero.HeroTiers.hasExperimental(serverPlayer)) {
			serverPlayer.displayClientMessage(
					Component.translatable("message.herocraft.tony_stark.blocked_experimental").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}

		if (!TonyStark.grant(serverPlayer)) {
			serverPlayer.displayClientMessage(
					Component.translatable("message.herocraft.tony_stark.already_have").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}

		if (!serverPlayer.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.herocraft.arc_reactor.line1").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		tooltip.add(Component.empty());
		tooltip.add(Component.translatable("item.herocraft.arc_reactor.line2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.herocraft.arc_reactor.line3").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.herocraft.arc_reactor.line4").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.empty());
		tooltip.add(Component.translatable("item.herocraft.arc_reactor.unlock").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("herocraft.ironman.power.tony_stark.name").withStyle(ChatFormatting.GOLD));
	}
}
