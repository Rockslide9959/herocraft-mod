package com.projecthero.mod.grave.item;

import java.util.List;

import com.projecthero.mod.oathbreaker.OathbreakerSummon;
import com.projecthero.mod.oathbreaker.entity.OathbreakerEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Knight's Soul -- crafted from an Abyssal Core surrounded by Gravebound Essence. Right-clicked on a
 * Respawn Anchor, it consumes itself immediately and calls forth The Oathbreaker 5 seconds later (the
 * buildup -- rumble, a slight camera zoom, particles -- is {@link OathbreakerSummon}'s job; this class
 * is only the gate-checks and the hand-off).
 *
 * <p>Every gate is server-side, same shape as {@link GraveRitualTotemItem}: the block really has to be
 * a Respawn Anchor, there has to be open ground to actually place the boss on, and one already summoned
 * (or already queued) nearby refuses a second so a player can't flood the area with them.
 */
public class KnightsSoulItem extends Item {
	/** How far away an already-summoned Oathbreaker blocks a new one. */
	private static final double DEDUP_RADIUS = 48.0;

	public KnightsSoulItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!level.getBlockState(context.getClickedPos()).is(Blocks.RESPAWN_ANCHOR)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)
				|| !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		BlockPos anchor = context.getClickedPos();
		if (activeNearby(serverLevel, anchor)) {
			fail(player, "message.projecthero.knights_soul.already_active");
			return InteractionResult.FAIL;
		}
		if (OathbreakerSummon.findSpot(serverLevel, anchor) == null) {
			fail(player, "message.projecthero.knights_soul.no_room");
			return InteractionResult.FAIL;
		}

		context.getItemInHand().shrink(1);
		OathbreakerSummon.begin(serverLevel, anchor);
		player.sendSystemMessage(Component.translatable("message.projecthero.knights_soul.summoned")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		return InteractionResult.CONSUME;
	}

	private boolean activeNearby(ServerLevel level, BlockPos center) {
		AABB box = new AABB(center).inflate(DEDUP_RADIUS);
		return !level.getEntitiesOfClass(OathbreakerEntity.class, box).isEmpty();
	}

	private static void fail(ServerPlayer player, String messageKey) {
		player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), true);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.knights_soul.hint").withStyle(ChatFormatting.GRAY));
	}
}
