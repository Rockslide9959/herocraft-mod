package com.projecthero.mod.ironman;

import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.14.26: an Iron Man suit feeds its wearer. Every two seconds, if you're hungry enough that a meal won't be wasted
 * (or you're down to three drumsticks), the suit takes the most filling <b>cooked</b> food in your inventory and feeds it
 * to you -- bowls come back. Raw, rotten and risky food is never touched, and neither are golden apples (those are for
 * emergencies you choose).
 */
public final class IronManAutoFeed {
	static final int INTERVAL = 40;

	/** Cooked / prepared vanilla food the suit is allowed to use. */
	private static final Set<Item> COOKED = Set.of(Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN, Items.COOKED_MUTTON,
			Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.BAKED_POTATO, Items.BREAD, Items.PUMPKIN_PIE,
			Items.MUSHROOM_STEW, Items.RABBIT_STEW, Items.BEETROOT_SOUP, Items.COOKIE, Items.DRIED_KELP, Items.GOLDEN_CARROT);

	private IronManAutoFeed() {
	}

	public static boolean isCooked(ItemStack stack) {
		return !stack.isEmpty() && COOKED.contains(stack.getItem()) && stack.get(DataComponents.FOOD) != null;
	}

	/** Called every tick for an Iron Man wearer (any piece). */
	public static void tick(ServerPlayer player) {
		if (player.tickCount % INTERVAL != 0 || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		int food = player.getFoodData().getFoodLevel();
		if (food >= 20) {
			return;
		}
		ItemStack best = ItemStack.EMPTY;
		int bestNutrition = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!isCooked(s)) {
				continue;
			}
			FoodProperties p = s.get(DataComponents.FOOD);
			int n = p.nutrition();
			// a meal is eaten once it fits without waste, or straight away when the bar is nearly empty
			if ((food + n <= 20 || food <= 6) && n > bestNutrition) {
				best = s;
				bestNutrition = n;
			}
		}
		if (best.isEmpty()) {
			return;
		}
		FoodProperties p = best.get(DataComponents.FOOD);
		Component name = best.getHoverName();
		player.getFoodData().eat(p);
		boolean bowl = best.is(Items.MUSHROOM_STEW) || best.is(Items.RABBIT_STEW) || best.is(Items.BEETROOT_SOUP);
		best.shrink(1);
		if (bowl && !player.getInventory().add(new ItemStack(Items.BOWL))) {
			player.drop(new ItemStack(Items.BOWL), false);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.5f, 1.2f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.auto_feed", name).withStyle(ChatFormatting.AQUA), true);
	}
}
