package com.projecthero.mod.hulk.item;

import java.util.List;

import com.projecthero.mod.hulk.GammaOverload;
import com.projecthero.mod.hulk.Hulk;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * v0.13.12 (Hulk Phase 4): the Gamma Serum. Loot only -- it is found in the chest of a rare, ruined Gamma Lab
 * ({@code hulk/worldgen/GammaLabPiece}); never crafted.
 *
 * <p>v0.13.21: drinking it no longer hands the power over. It doses the drinker ({@link GammaOverload#setDosed});
 * he then has to right-click a Gamma Reactor, which overloads and explodes -- and the Gamma in his blood is what lets
 * him survive it and become the Hulk ({@link GammaOverload}).
 */
public class GammaSerumItem extends Item {
	private static final int DRINK_TICKS = 40;

	public GammaSerumItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (Hulk.hasPower(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.gamma_serum.already")
						.withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		if (player instanceof ServerPlayer sp && GammaOverload.isDosed(sp)) {
			player.displayClientMessage(Component.translatable("message.projecthero.gamma_serum.already_dosed")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
			return stack;
		}
		if (Hulk.hasPower(player) || GammaOverload.isDosed(player)) {
			return stack;
		}
		GammaOverload.setDosed(player, true);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 160, 0));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 200, 0, false, false));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT,
				net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.7f);
		player.displayClientMessage(Component.translatable("message.projecthero.gamma_serum.dosed")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.gamma_serum.dosed_hint")
				.withStyle(ChatFormatting.DARK_GREEN), false);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			if (stack.isEmpty()) {
				return bottle;
			}
			if (!player.getInventory().add(bottle)) {
				player.drop(bottle, false);
			}
		}
		return stack;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return DRINK_TICKS;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.DRINK;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.gamma_serum.desc1").withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.translatable("item.projecthero.gamma_serum.desc2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
