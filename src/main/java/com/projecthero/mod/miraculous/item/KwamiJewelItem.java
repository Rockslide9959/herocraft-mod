package com.projecthero.mod.miraculous.item;

import java.util.List;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;

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
 * v0.16.6: a Miraculous jewel (the Ladybug Earrings, the Cat Ring, the Fox Necklace). Right-click: the Kwami pops out,
 * introduces itself, and its hero becomes your Primary power ({@link Miraculous#grant}, replacing any other). Refused --
 * and the jewel kept -- when you already hold this Kwami, or the {@code projectheroUniquePowers} gamerule says someone
 * else does. Used up when it takes. (Revoking the power by command gives no jewel back.)
 */
public class KwamiJewelItem extends Item {
	public final Kwami kwami;

	public KwamiJewelItem(Kwami kwami, Properties properties) {
		super(properties);
		this.kwami = kwami;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (Miraculous.holds(player, kwami)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.miraculous.already",
						Component.translatable("entity.projecthero.kwami." + kwami.id)).withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(stack);
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(stack);
		}
		// projectheroUniquePowers: refused BEFORE the jewel is used up
		if (com.projecthero.mod.hero.UniquePowers.refuse(sp, kwami.heroKey)) {
			return InteractionResultHolder.fail(stack);
		}
		if (!Miraculous.grant(sp, kwami)) {
			return InteractionResultHolder.fail(stack);
		}
		if (!sp.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.miraculous_jewel.tooltip",
				Component.translatable("entity.projecthero.kwami." + kwami.id),
				Component.translatable("projecthero.hero_tier." + kwami.heroKey)).withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.miraculous_jewel.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
