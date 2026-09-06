package com.projecthero.mod.spider.item;

import java.util.List;

import com.projecthero.mod.spider.SpiderMan;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * The Arachnid Mutagen: the one and only way to become Spider-Man.
 *
 * <p>It does not create arachnid powers, it <em>refines</em> them. Using one without already carrying
 * the Spider Climbing / Adhesion mutation does nothing at all and, critically, consumes nothing --
 * the failure costs the player only the click. Using one as a Spider Adhesion player evolves that
 * power into the full Hero Class and consumes the mutagen.
 *
 * <p>All of that is decided in {@link SpiderMan#evolveFromAdhesion}; this class only owns the item
 * behaviour, so the same rule holds however the evolution is reached.
 */
public class ArachnidMutagenItem extends Item {
	public ArachnidMutagenItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}

		if (SpiderMan.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.arachnid_mutagen.already")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(held);
		}
		if (!SpiderMan.hasSpiderAdhesion(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.arachnid_mutagen.no_adhesion")
					.withStyle(ChatFormatting.RED), true);
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BREWING_STAND_BREW,
					SoundSource.PLAYERS, 0.6f, 0.6f);
			return InteractionResultHolder.fail(held);
		}

		if (!SpiderMan.evolveFromAdhesion(sp)) {
			return InteractionResultHolder.fail(held);
		}
		held.shrink(1);
		return InteractionResultHolder.success(held);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.arachnid_mutagen.desc1")
				.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.projecthero.arachnid_mutagen.desc2")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
