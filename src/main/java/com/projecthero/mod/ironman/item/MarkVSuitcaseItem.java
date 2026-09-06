package com.projecthero.mod.ironman.item;

import java.util.List;

import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

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
 * The Mark V Suitcase -- an actual item (spec section 23). Right-click while holding it, and if you
 * have the Tony Stark power the Mark V unfolds around you from the case (quick mechanical suit-up).
 * The case item stays in your inventory and represents the stowed armour; suit-down folds the Mark V
 * back into it.
 *
 * <p>A player without the Tony Stark power gets "Stark armor rejects unauthorized user." and nothing
 * deploys -- the whole point is that only Tony Stark understands the technology.
 */
public class MarkVSuitcaseItem extends Item {
	public MarkVSuitcaseItem(Properties properties) {
		super(properties);
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
		if (!TonyStark.hasPower(serverPlayer)) {
			serverPlayer.displayClientMessage(
					Component.translatable("message.projecthero.ironman.armor_rejects").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}
		// "changes 15": the case IS the stowed Mark V. Right-click builds it around you from the case
		// (chest-first, ~4 s); right-click while already wearing it folds it back into the case.
		if (com.projecthero.mod.ironman.IronManArmor.wearingAnyIronMan(serverPlayer)) {
			String worn = com.projecthero.mod.ironman.IronManArmor.wornSuitId(serverPlayer);
			if (IronManSuitUpManager.beginSuitDownToCase(serverPlayer, worn != null ? worn : "mark_v")) {
				return InteractionResultHolder.success(stack);
			}
			return InteractionResultHolder.fail(stack);
		}
		if (IronManSuitUpManager.beginSuitUpFromCase(serverPlayer, "mark_v")) {
			return InteractionResultHolder.success(stack);
		}
		return InteractionResultHolder.fail(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.mark_v_suitcase.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		tooltip.add(Component.translatable("item.projecthero.ironman.requires_tony_stark").withStyle(ChatFormatting.DARK_AQUA));
	}
}
