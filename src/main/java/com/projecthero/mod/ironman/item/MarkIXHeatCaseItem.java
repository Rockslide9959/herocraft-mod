package com.projecthero.mod.ironman.item;

import java.util.List;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.PortableForms;

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
 * v0.16.7 (user) Iron Man Era II stage 2: the <b>Mk IX Heat Case</b> -- the Mk IX Inferno packed away
 * ({@link PortableForms#MK9_HEAT_CASE}). A squat charcoal heat-proof case with bronze bands and glowing vents, holding the
 * four real armour stacks ({@link SuitcaseContents}, the Mk VI briefcase's format) with their integrity and charge.
 *
 * <p>C (or right-click it) with no suit on: the case is set down, both leaves swing open on a glow of heat and the black
 * plate climbs on. Hold Shift+C in the full Mk IX to fold it back in. It works in fire and lava (the pieces never leave
 * their slots until the fold's last tick, so the full-set immunity holds; see {@code PortableDeploy#heatGrace}). The
 * item itself never burns (fire resistant, like the suit's pieces).
 *
 * <p>Obtained like the Mk VI briefcase: right-click any loose Mk IX piece with all four in your inventory
 * ({@link IronManArmorItem#packLoose}), or fold a worn Mk IX.
 */
public class MarkIXHeatCaseItem extends Item {
	public MarkIXHeatCaseItem(Properties properties) {
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
		String suitId = PortableForms.MK9_HEAT_CASE.suitId();
		if (IronManArmor.wearingAnyIronMan(serverPlayer)) {
			String worn = IronManArmor.wornSuitId(serverPlayer);
			if (suitId.equals(worn) && IronManSuitUpManager.beginSuitDownToCase(serverPlayer, worn)) {
				return InteractionResultHolder.success(stack);
			}
			serverPlayer.displayClientMessage(Component.translatable("message.projecthero.ironman.portable_take_off_first")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(stack);
		}
		if (IronManSuitUpManager.beginSuitUpFromCase(serverPlayer, suitId)) {
			return InteractionResultHolder.success(stack);
		}
		return InteractionResultHolder.fail(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.mark_9_heat_case.hint").withStyle(ChatFormatting.DARK_GRAY,
				ChatFormatting.ITALIC));
		if (!SuitcaseContents.isLegacyEmpty(stack)) {
			tooltip.add(Component.translatable("item.projecthero.mark_9_heat_case.contents",
					SuitcaseContents.nonEmpty(stack).size()).withStyle(ChatFormatting.GRAY));
			if (SuitcaseContents.isEmpty(stack)) { // v0.16.7 (user): a crafted / emptied case -- how to fill it
				tooltip.add(Component.translatable("item.projecthero.portable.empty").withStyle(ChatFormatting.GRAY));
			}
		}
		tooltip.add(Component.translatable("item.projecthero.mark_9_heat_case.lava").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.ironman.requires_tony_stark").withStyle(ChatFormatting.DARK_AQUA));
	}
}
