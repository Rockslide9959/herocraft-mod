package com.projecthero.mod.ironman.item;

import java.util.List;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.crate.HulkbusterCrate;

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
 * v0.16.7 (user) Iron Man Era II stage 2: the <b>Hulkbuster Uplink</b> -- the beacon you carry while the Hulkbuster I
 * (mark_8) waits in orbit in its drop crate. It holds the suit's four real armour stacks ({@link SuitcaseContents}, stamped
 * with integrity) exactly like the Mk VI briefcase does; while the suit is down (crate on the ground, or worn) it is kept,
 * EMPTY, and the next fold puts the suit back into it. A never-used uplink (creative tab) stands for a fresh suit.
 *
 * <p>C (no suit on) or right-click: call the crate down where you look ({@link HulkbusterCrate#call}); with the crate
 * already down and the suit still in it, C / right-click sends it back up.
 */
public class HulkbusterUplinkItem extends Item {
	public HulkbusterUplinkItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.pass(stack);
		}
		if (!TonyStark.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.ironman.armor_rejects").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}
		if (IronManArmor.wearingAnyIronMan(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.ironman.portable_fold_hint").withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(stack);
		}
		boolean ok = HulkbusterCrate.recall(sp) || HulkbusterCrate.call(sp);
		return ok ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		if (SuitcaseContents.isLegacyEmpty(stack)) {
			tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.fresh").withStyle(ChatFormatting.AQUA));
		} else if (SuitcaseContents.isEmpty(stack)) {
			tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.deployed").withStyle(ChatFormatting.GOLD));
			tooltip.add(Component.translatable("item.projecthero.portable.empty").withStyle(ChatFormatting.GRAY)); // v0.16.7 (user)
		} else {
			int pct = Math.round(100f * IronManEnergy.stackIntegrity(stack, HulkbusterCrate.SUIT_ID)
					/ Math.max(1f, IronManEnergy.maxIntegrity(HulkbusterCrate.SUIT_ID)));
			tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.in_orbit", pct).withStyle(ChatFormatting.AQUA));
		}
		tooltip.add(Component.translatable("item.projecthero.ironman.requires_tony_stark").withStyle(ChatFormatting.DARK_AQUA));
	}
}
