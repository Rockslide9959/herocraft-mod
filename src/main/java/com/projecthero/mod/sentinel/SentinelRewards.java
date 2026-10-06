package com.projecthero.mod.sentinel;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.event.reward.Valuables;
import com.projecthero.mod.sentinel.item.SentinelItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.15.1: what beating a Sentinel Purge pays -- rolled separately for every fighter still standing when Master Mold
 * falls, given straight into the inventory (dropped at their feet if it's full), like the Apokolips Invasion's rewards.
 * <ul>
 *   <li>the <b>Master Mold Core</b> -- guaranteed;</li>
 *   <li><b>Sentinel Circuitry</b> -- 2 to 5 (crafts a new Trask Signal for a rematch);</li>
 *   <li>robot salvage: iron, redstone, gold, diamonds,
 *       emeralds, lapis, an enchanted book, and a chance of netherite scrap -- a haul between the hordes and Apokolips;</li>
 *   <li>a <b>Mutagenic Serum</b> (a random mutation) -- rare, and a fitting irony;</li>
 *   <li>600 experience points.</li>
 * </ul>
 */
public final class SentinelRewards {
	private SentinelRewards() {
	}

	public static void grant(ServerLevel level, List<ServerPlayer> winners) {
		SentinelConfig.Rewards cfg = SentinelConfig.rewards();
		for (ServerPlayer p : winners) {
			List<ItemStack> loot = roll(level.random, level.registryAccess(), winners.size());
			int items = 0;
			for (ItemStack stack : loot) {
				if (stack.is(SentinelItems.MASTER_MOLD_CORE) || stack.is(SentinelItems.SENTINEL_CIRCUITRY) || isSerum(stack)) {
					p.sendSystemMessage(Component.translatable("message.projecthero.sentinel_purge.reward", stack.getCount(), stack.getHoverName())
							.withStyle(isSerum(stack) ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
				} else {
					items += stack.getCount();
				}
				if (!p.getInventory().add(stack)) {
					p.drop(stack, false);
				}
			}
			if (items > 0) {
				p.sendSystemMessage(Component.translatable("message.projecthero.sentinel_purge.valuables", items).withStyle(ChatFormatting.GOLD));
			}
			p.giveExperiencePoints(cfg.experiencePoints);
			level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
		}
	}

	private static boolean isSerum(ItemStack stack) {
		Item serum = com.projecthero.mod.hero.item.RandomPowerSerumItem.EXPERIMENTAL_SERUM;
		return serum != null && stack.is(serum);
	}

	/** One fighter's loot, for a party of {@code party}. */
	public static List<ItemStack> roll(RandomSource r, RegistryAccess registries, int party) {
		SentinelConfig.Rewards cfg = SentinelConfig.rewards();
		List<ItemStack> out = new ArrayList<>();
		if (SentinelItems.MASTER_MOLD_CORE != null && cfg.coresPerFighter > 0) {
			out.add(new ItemStack(SentinelItems.MASTER_MOLD_CORE, cfg.coresPerFighter));
		}
		if (SentinelItems.SENTINEL_CIRCUITRY != null) {
			int lo = Math.max(0, cfg.circuitryMin);
			int hi = Math.max(lo, cfg.circuitryMax);
			int n = lo + (hi > lo ? r.nextInt(hi - lo + 1) : 0);
			if (n > 0) {
				out.add(new ItemStack(SentinelItems.SENTINEL_CIRCUITRY, n));
			}
		}
		double scale = Math.max(0.0, cfg.valuablesMultiplier);
		if (scale > 0) {
			Valuables.add(out, r, Items.IRON_INGOT, 24, 40, scale);
			Valuables.add(out, r, Items.REDSTONE, 24, 40, scale);
			Valuables.add(out, r, Items.GOLD_INGOT, 8, 16, scale);
			Valuables.add(out, r, Items.DIAMOND, 4, 7, scale);
			Valuables.add(out, r, Items.EMERALD, 8, 14, scale);
			Valuables.add(out, r, Items.LAPIS_LAZULI, 16, 24, scale);
			Valuables.add(out, r, Items.GOLDEN_APPLE, 2, 3, scale);
			out.add(Valuables.book(r, registries, 30));
			Valuables.chance(out, r, 0.25, Items.NETHERITE_SCRAP, 1, 2);
		}
		Item serum = com.projecthero.mod.hero.item.RandomPowerSerumItem.EXPERIMENTAL_SERUM;
		if (serum != null && r.nextDouble() < cfg.mutagenicSerumChance) {
			out.add(new ItemStack(serum));
		}
		return out;
	}
}
