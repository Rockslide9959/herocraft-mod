package com.projecthero.mod.ultron.contagion.item;

import java.util.List;

import com.projecthero.mod.ultron.contagion.entity.PurgeChargeEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * v0.16.1: the <b>Purge Charge</b> -- a throwable blue EMP. Where it bursts ({@link PurgeChargeEntity}): every block a Nexus
 * corrupted within 6 blocks is put back, Ultron's robots are stunned for 3 s, Compromised machines come back, Assimilated
 * mobs lose their plating, Ultron Golems turn back and a hijacked Iron Legion suit powers down.
 */
public class PurgeChargeItem extends Item {
	public PurgeChargeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.6f);
		if (!level.isClientSide()) {
			PurgeChargeEntity charge = new PurgeChargeEntity(level, player);
			charge.setItem(stack);
			charge.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.3f, 0.8f);
			level.addFreshEntity(charge);
		}
		player.getCooldowns().addCooldown(this, 10);
		stack.consume(1, player);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.purge_charge.hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.purge_charge.hint2").withStyle(ChatFormatting.DARK_AQUA));
	}
}
