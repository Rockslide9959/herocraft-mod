package com.projecthero.mod.speedforce;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * v0.17.1: a Speed Force Shard -- the one thing the Black Flash leaves when he falls. Rare (epic, glinting); crafted with a
 * Tachyon Device it makes the Speed Force Tachyon Device (+10 Speed Force while held). Tooltip lines word-wrapped
 * (TooltipWrap).
 */
public class SpeedForceShardItem extends Item {
	public SpeedForceShardItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	/** v0.17.1 (user, race ghosts): used on a block -- a race start, then the finish; sneaking clears the course. */
	@Override
	public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
		if (ctx.getPlayer() == null || !SpeedForceSpeedster.hasPower(ctx.getPlayer())) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		if (ctx.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) {
			SpeedForceRaces.useShard(sp, ctx.getClickedPos());
		}
		return net.minecraft.world.InteractionResult.sidedSuccess(ctx.getLevel().isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.speed_force_shard.hint").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.speed_force_shard.hint2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.speed_force_shard.race").withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("item.projecthero.speed_force_shard.hint3").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
