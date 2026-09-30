package com.projecthero.mod.supersoldier.item;

import java.util.List;

import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierSerum;

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
 * v0.14.8: the Super Soldier Serum, unrefined or refined. Drunk like a potion (32 ticks), it leaves a glass bottle.
 * Unrefined: 10% it takes, 90% the body rejects it and the drinker dies (creative included). Refined (the unrefined
 * serum after ten minutes in a Blast Furnace): it always takes. See {@link SuperSoldierSerum}.
 */
public class SuperSoldierSerumItem extends Item {
	private static final int DRINK_TICKS = 32;
	private final boolean refined;

	public SuperSoldierSerumItem(Properties properties, boolean refined) {
		super(properties.stacksTo(16));
		this.refined = refined;
	}

	public boolean refined() {
		return refined;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (SuperSoldier.hasPower(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.super_soldier.already")
						.withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer player) || SuperSoldier.hasPower(player)) {
			return stack;
		}
		float roll = level.getRandom().nextFloat();
		stack.consume(1, player);
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
		ItemStack result = stack;
		if (!player.getAbilities().instabuild) {
			if (stack.isEmpty()) {
				result = bottle;
			} else if (!player.getInventory().add(bottle)) {
				player.drop(bottle, false);
			}
		}
		SuperSoldierSerum.drink(player, refined, roll);
		return result;
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
		return refined;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		String base = refined ? "item.projecthero.refined_super_soldier_serum" : "item.projecthero.unrefined_super_soldier_serum";
		tooltip.add(Component.translatable(base + ".desc1").withStyle(refined ? ChatFormatting.AQUA : ChatFormatting.RED));
		tooltip.add(Component.translatable(base + ".desc2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
