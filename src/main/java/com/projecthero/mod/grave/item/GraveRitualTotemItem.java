package com.projecthero.mod.grave.item;

import java.util.List;

import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.grave.CurseSource;
import com.projecthero.mod.grave.GraveboundCurse;
import com.projecthero.mod.worldgen.GraveyardTracker;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * Lets a player who has already earned it summon the Gravebound Curse on demand, so raids become
 * repeatable rather than something you have to wait to stumble into.
 *
 * <h2>Every gate is server-side</h2>
 * Using it does nothing on the client; the server checks, in order, that the player is near a real
 * Graveyard, is not already cursed, and has no Zombie Raid running nearby -- and only then consumes
 * the totem and applies the curse. That ordering matters: the item is spent only on a use that
 * actually did something, so a refused activation costs nothing, and a player cannot stack up dozens
 * of raids by spamming totems because the second one is refused while the first raid is still up.
 *
 * <p>It applies the <em>same</em> Gravebound Curse as the Graveyard and the Cursed Zombie -- same
 * timer, same removal rules, same raid -- rather than starting a raid directly, so a player who
 * changes their mind can still burn an Enchanted Golden Apple to call it off.
 */
public class GraveRitualTotemItem extends Item {
	public GraveRitualTotemItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
				|| !(level instanceof ServerLevel serverLevel)) {
			return InteractionResultHolder.consume(stack);
		}

		if (!GraveyardTracker.nearGraveyard(serverLevel, player.blockPosition(),
				EventConfig.raid().graveyardProximityBlocks)) {
			fail(serverPlayer, "message.projecthero.ritual.not_near_graveyard");
			return InteractionResultHolder.fail(stack);
		}
		if (GraveboundCurse.isCursed(serverPlayer)) {
			fail(serverPlayer, "message.projecthero.ritual.already_cursed");
			return InteractionResultHolder.fail(stack);
		}
		if (EventManager.anyActiveNear(serverLevel, player.blockPosition(), EventTypes.ZOMBIE_RAID,
				EventConfig.framework().minDistanceBetweenEvents)) {
			fail(serverPlayer, "message.projecthero.ritual.raid_nearby");
			return InteractionResultHolder.fail(stack);
		}

		if (!GraveboundCurse.apply(serverPlayer, CurseSource.RITUAL)) {
			fail(serverPlayer, "message.projecthero.ritual.already_cursed");
			return InteractionResultHolder.fail(stack);
		}

		stack.shrink(1);
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.0f, 0.6f);
		serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
				player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.6, 0.8, 0.6, 0.03);
		return InteractionResultHolder.consume(stack);
	}

	private static void fail(ServerPlayer player, String messageKey) {
		player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), true);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.grave_ritual_totem.hint").withStyle(ChatFormatting.GRAY));
	}

}
