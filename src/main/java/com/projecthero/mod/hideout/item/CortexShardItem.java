package com.projecthero.mod.hideout.item;

import java.util.List;

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
 * v0.17.5 artifact (Ultron Forge): <b>Ultron Cortex Shard</b> -- a sliver of Ultron's processor. Use it (either hand):
 * for {@link #REVEAL_TICKS} every hostile mob within {@link #RANGE} blocks is outlined through walls -- in <em>your</em>
 * view only (the client draws it; nothing is set on the mobs, the v0.15.4 privacy rule). 45 s cooldown.
 */
public class CortexShardItem extends Item {
	public static final int REVEAL_TICKS = 160;
	public static final double RANGE = 32.0;
	public static final int COOLDOWN = 900;

	/** Client hook (set by the client initialiser): starts the local reveal. Never touches the server. */
	public static Runnable clientReveal = () -> {
	};

	public CortexShardItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			clientReveal.run();
		} else {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.8f);
		}
		player.getCooldowns().addCooldown(this, COOLDOWN);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.ultron_cortex_shard.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.hideout.artifact.from", Component.translatable("structure.projecthero.ultron_forge"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
