package com.projecthero.mod.ironman.item;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.network.IronManBlueprintPickerPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

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
 * "changes 21": a blank Stark blueprint. Right-click (with or without sneak) to open a picker that
 * stamps it into a specific mark's blueprint. A mark is only offered once the entire previous mark's
 * suit has been built, which is the whole Mark 1 -> 2 -> III -> 4 -> V -> 6 -> VII progression gate --
 * see {@link com.projecthero.mod.network.ModNetworking} for the server-side re-validation and stamping.
 */
public class BlankBlueprintItem extends Item {
	public BlankBlueprintItem(Properties properties) {
		super(properties.stacksTo(16));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResultHolder.pass(stack);
		}
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.pass(stack);
		}
		if (!TonyStark.hasPower(serverPlayer)) {
			serverPlayer.displayClientMessage(Component
					.translatable("message.projecthero.ironman.craft_requires_tony_stark").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}

		List<IronManBlueprintPickerPayload.Entry> entries = new ArrayList<>();
		for (String suitId : IronManItems.MARK_ORDER) {
			if (IronManItems.blueprintFor(suitId) == null) {
				continue;
			}
			String prereq = IronManItems.prerequisiteSuit(suitId);
			boolean unlocked = prereq == null || TonyStark.hasFabricatedFullSuit(serverPlayer, prereq);
			entries.add(new IronManBlueprintPickerPayload.Entry(suitId, unlocked, unlocked ? null : prereq));
		}
		ServerPlayNetworking.send(serverPlayer, new IronManBlueprintPickerPayload(entries));
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7f, 1.1f);
		return InteractionResultHolder.sidedSuccess(stack, false);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.blank_blueprint.hint")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
