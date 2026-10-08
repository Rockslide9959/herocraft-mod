package com.projecthero.mod.greenlantern.item;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternBattery;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * v0.15.15: the Personal Power Battery as an item. Held in the OFF hand by a Green Lantern, Sneak + use (right-click,
 * at a block or at the air) starts charging the ring ({@link GreenLanternBattery#beginCharge}) instead of placing it;
 * anything else is the ordinary block item (place it as decoration). The held / swaying look is client-side
 * ({@code PowerBatteryHeldRenderer}).
 */
public class PowerBatteryItem extends BlockItem {
	public PowerBatteryItem(Block block, Properties properties) {
		super(block, properties);
	}

	/** Whether this use is the charging gesture: a bonded Lantern, sneaking, the battery in the off hand. */
	private static boolean chargeGesture(Player player, InteractionHand hand) {
		return player != null && hand == InteractionHand.OFF_HAND && player.isSecondaryUseActive() && GreenLantern.hasPower(player);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (chargeGesture(player, context.getHand())) {
			if (player instanceof ServerPlayer sp) {
				GreenLanternBattery.beginCharge(sp);
			}
			return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
		}
		return super.useOn(context);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (chargeGesture(player, hand)) {
			if (player instanceof ServerPlayer sp) {
				GreenLanternBattery.beginCharge(sp);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		return super.use(level, player, hand);
	}
}
