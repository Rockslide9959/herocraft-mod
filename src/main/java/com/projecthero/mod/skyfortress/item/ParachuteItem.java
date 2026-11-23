package com.projecthero.mod.skyfortress.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.7: a <b>Parachute</b> (from the Sky Fortress's Parachute Crate). Right-click in the air to open it, or let it
 * open by itself: carried anywhere in your inventory, it deploys once you have fallen {@link #AUTO_FALL} blocks with no
 * slow fall already on you. Opening it uses it up and gives {@link #SLOW_FALL_TICKS} of Slow Falling.
 */
public class ParachuteItem extends Item {
	public static final float AUTO_FALL = 10.0f;
	public static final int SLOW_FALL_TICKS = 400;

	public ParachuteItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.onGround()) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("item.projecthero.parachute.ground").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(stack);
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			deploy(server, sp, stack);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/** Opens the chute: Slow Falling, the fall forgiven, one parachute used. */
	public static void deploy(ServerLevel level, ServerPlayer player, ItemStack stack) {
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, true, true));
		player.resetFallDistance();
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 1.2f, 0.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.0f, 0.6f);
		player.displayClientMessage(Component.translatable("item.projecthero.parachute.open").withStyle(ChatFormatting.GOLD), true);
	}

	/** The auto-deploy check, once a tick per player. True when a chute opened. */
	public static boolean autoDeploy(ServerLevel level, ServerPlayer player) {
		if (player.onGround() || player.fallDistance < AUTO_FALL || player.getDeltaMovement().y > -0.4 || player.isFallFlying()
				|| player.getAbilities().flying || player.hasEffect(MobEffects.SLOW_FALLING) || player.isInWater()) {
			return false;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.getItem() instanceof ParachuteItem) {
				deploy(level, player, s);
				return true;
			}
		}
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
		lines.add(Component.translatable("item.projecthero.parachute.hint").withStyle(ChatFormatting.GRAY));
	}
}
