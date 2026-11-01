package com.projecthero.mod.stand.item;

import java.util.List;

import com.projecthero.mod.stand.StandType;
import com.projecthero.mod.stand.Stands;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.2: the <b>Stand Arrow</b> -- found in desert pyramids, trail ruins and ancient cities ({@link StandItems}).
 * Right-click stabs you (4 hearts) and awakens a Stand from the pool (only Star Platinum for now): the Hero-Tier power
 * {@code stand}, replacing whatever Primary you held (the one-Primary rule, {@code HeroTiers#claimPrimary}); the unique
 * powers gamerule can refuse it. The arrow is spent. Never from random serums.
 */
public class StandArrowItem extends Item {
	public static final float STAB_DAMAGE = 8f;

	public StandArrowItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (Stands.isStandUser(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.stand_arrow.already").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(stack);
		}
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		ServerPlayer sp = (ServerPlayer) player;
		if (!stab(sp, stack)) {
			return InteractionResultHolder.fail(stack);
		}
		return InteractionResultHolder.consume(stack);
	}

	/** The stab and the awakening. True if a Stand awoke. */
	public static boolean stab(ServerPlayer player, ItemStack stack) {
		if (Stands.isStandUser(player) || com.projecthero.mod.hero.UniquePowers.refuse(player, Stands.KEY)) {
			return false;
		}
		player.hurt(player.damageSources().generic(), STAB_DAMAGE);
		if (!player.isAlive()) {
			return false;
		}
		var pool = StandType.pool();
		StandType type = pool.get(player.getRandom().nextInt(pool.size()));
		if (!Stands.grant(player, type)) {
			return false;
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.stand_arrow.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.projecthero.stand_arrow.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
