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
 * (or you're down to three drumsticks), the suit takes the most filling safe food in your inventory and feeds it to
 * you -- bowls and bottles come back.
 *
 * <p>v0.14.29: "safe" is no longer a fixed list of vanilla cooked foods. Any food (modded included) qualifies unless it
 * carries a status effect (rotten flesh, raw chicken, spider eyes, pufferfish, golden apples...), is a raw meat / fish
 * / potato, or teleports you (chorus fruit). Suspicious stew is never touched either.
 */
public final class IronManAutoFeed {
	static final int INTERVAL = 40;

	/** Foods with no effect component that still shouldn't be eaten automatically. */
	private static final Set<Item> NEVER = Set.of(Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT,
			Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.POTATO, Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW,
			Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);

	private IronManAutoFeed() {
	}

	/** True for any food the suit is willing to feed you. (Kept the old name -- callers predate v0.14.29.) */
	public static boolean isCooked(ItemStack stack) {
		if (stack.isEmpty() || NEVER.contains(stack.getItem())) {
			return false;
		}
		FoodProperties p = stack.get(DataComponents.FOOD);
		return p != null && p.nutrition() > 0 && p.effects().isEmpty()
				&& stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS) == null;
	}

	/** Called every tick for an Iron Man wearer (any piece). */
	public static void tick(ServerPlayer player) {
		if (player.tickCount % INTERVAL != 0) {
			return;
		}
		feedNow(player);
	}

	/** One feeding check, ignoring the two-second cadence. Returns true if something was eaten. */
	public static boolean feedNow(ServerPlayer player) {
		if (player.getAbilities().invulnerable || player.isSpectator() || !player.isAlive()) { // creative (mock test players report isCreative() even in survival)
			return false;
		}
		int food = player.getFoodData().getFoodLevel();
		if (food >= 20) {
			return false;
		}
		ItemStack best = ItemStack.EMPTY;
		int bestNutrition = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!isCooked(s)) {
				continue;
			}
			int n = s.get(DataComponents.FOOD).nutrition();
			// a meal is eaten once it fits without waste, or straight away when the bar is nearly empty
			if ((food + n <= 20 || food <= 6) && n > bestNutrition) {
				best = s;
				bestNutrition = n;
			}
		}
		if (best.isEmpty()) {
			return false;
		}
		FoodProperties p = best.get(DataComponents.FOOD);
		Component name = best.getHoverName();
		player.getFoodData().eat(p);
		ItemStack leftover = p.usingConvertsTo().map(ItemStack::copy).orElse(ItemStack.EMPTY);
		if (leftover.isEmpty() && best.getItem().hasCraftingRemainingItem()) {
			leftover = new ItemStack(best.getItem().getCraftingRemainingItem());
		}
		best.shrink(1);
		if (!leftover.isEmpty() && !player.getInventory().add(leftover)) {
			player.drop(leftover, false);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.5f, 1.2f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.auto_feed", name).withStyle(ChatFormatting.AQUA), true);
		return true;
	}
}
