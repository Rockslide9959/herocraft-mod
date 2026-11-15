package com.projecthero.mod.sentry.item;

import java.util.List;

import com.projecthero.mod.sentry.Sentry;

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
 * v0.17.5: the Sentry Serum, a golden vial from the O.X.E. vat in The Vault. Drunk like a potion (32 ticks), it leaves a glass bottle
 * and makes the drinker the Sentry, replacing their Primary power like every hero grant (the unique-powers gamerule applies).
 */
public class SentrySerumItem extends Item {
	private static final int DRINK_TICKS = 32;

	public SentrySerumItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (Sentry.hasPower(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.sentry.already").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		if (player instanceof ServerPlayer sp && com.projecthero.mod.hero.UniquePowers.refuse(sp, Sentry.KEY)) {
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer player) || Sentry.hasPower(player)) {
			return stack;
		}
		if (!Sentry.grant(player)) {
			return stack;
		}
		stack.consume(1, player);
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
		if (player.getAbilities().instabuild) {
			return stack;
		}
		if (stack.isEmpty()) {
			return bottle;
		}
		if (!player.getInventory().add(bottle)) {
			player.drop(bottle, false);
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
		tooltip.add(Component.translatable("item.projecthero.sentry_serum.desc1").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.sentry_serum.desc2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
