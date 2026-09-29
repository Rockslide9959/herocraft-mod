package com.projecthero.mod.darkseid.item;

import java.util.List;

import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.raid.DarkseidRaid;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Boom Tube Beacon: use it to open the way for Apokolips. The raid begins where you stand and everyone within
 * {@link DarkseidConfig.Raid#registrationRadius} becomes a participant. Consumed on use (not in creative); a server
 * stop mid-raid gives it back. Refused if another invasion is already running nearby.
 */
public class BoomTubeBeaconItem extends Item {
	public BoomTubeBeaconItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}
		if (EventManager.anyActiveNear(server, sp.blockPosition(), DarkseidRaid.TYPE_ID, EventConfig.framework().minDistanceBetweenEvents)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.boom_tube_beacon.busy").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(held);
		}
		DarkseidRaid raid = DarkseidRaid.startAt(server, sp.blockPosition(), sp);
		if (raid == null) {
			sp.displayClientMessage(Component.translatable("message.projecthero.boom_tube_beacon.busy").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(held);
		}
		if (!sp.getAbilities().instabuild) {
			held.shrink(1);
		}
		return InteractionResultHolder.consume(held);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.boom_tube_beacon.hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.boom_tube_beacon.warning", DarkseidConfig.raid().maxParticipants)
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}
}
