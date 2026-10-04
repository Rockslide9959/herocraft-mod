package com.projecthero.mod.darkseid.raid;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.item.DarkseidItems;
import com.projecthero.mod.event.reward.Valuables;
import com.projecthero.mod.grave.GraveboundCurse;

import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Victory loot, rolled separately for every rewarded participant: an official roster member (so not a late arrival
 * after the seal) who is online when Apokolips falls. Given straight into the inventory (dropped at their feet if
 * full) -- a raid that ends mid-air or over lava must not throw its rewards into it.
 *
 * <ul>
 *   <li>Darkseid's Omega Core -- guaranteed.</li>
 *   <li>Omega Shards -- likely (1-3).</li>
 *   <li>Mother Box -- very rare.</li>
 *   <li>Darkseid's Omega Relic -- extremely rare.</li>
 *   <li>v0.14.21: Apokolips plunder -- ores, gems, golden apples and books ({@link #valuables}).</li>
 * </ul>
 * Nobody is given Darkseid's powers. Advancements: {@code darkseid/anti_life} for everyone rewarded, and
 * {@code darkseid/apokolips_falls} too if no Mother Box overloaded during the whole raid.
 */
public final class DarkseidRaidRewards {
	private DarkseidRaidRewards() {
	}

	public static void grant(ServerLevel level, DarkseidRaid raid) {
		DarkseidConfig.Rewards cfg = DarkseidConfig.rewards();
		RandomSource random = level.random;
		for (DarkseidRoster.Member m : raid.roster().all()) {
			if (!m.rewardEligible) {
				continue;
			}
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(m.id);
			if (p == null) {
				continue;
			}
			List<ItemStack> loot = new ArrayList<>();
			loot.add(new ItemStack(DarkseidItems.OMEGA_CORE, Math.max(1, cfg.omegaCoreCount)));
			if (random.nextDouble() < cfg.omegaShardChance) {
				int n = cfg.omegaShardMin + random.nextInt(Math.max(1, cfg.omegaShardMax - cfg.omegaShardMin + 1));
				loot.add(new ItemStack(DarkseidItems.OMEGA_SHARD, n));
			}
			boolean box = random.nextDouble() < cfg.motherBoxChance;
			if (box) {
				loot.add(new ItemStack(DarkseidItems.MOTHER_BOX));
			}
			boolean relic = random.nextDouble() < cfg.omegaRelicChance;
			if (relic) {
				loot.add(new ItemStack(DarkseidItems.OMEGA_RELIC));
			}
			for (ItemStack stack : loot) {
				p.sendSystemMessage(Component.translatable("message.projecthero.darkseid_raid.reward", stack.getCount(), stack.getHoverName())
						.withStyle(ChatFormatting.GOLD));
				if (!p.getInventory().add(stack)) {
					p.drop(stack, false);
				}
			}
			// v0.14.21: Apokolips plunder -- ores and gems, one chat line for the lot
			List<ItemStack> plunder = valuables(random, level.registryAccess(), DarkseidRaid.waveCount(), cfg.valuablesMultiplier);
			int items = 0;
			for (ItemStack stack : plunder) {
				items += stack.getCount();
				if (!p.getInventory().add(stack)) {
					p.drop(stack, false);
				}
			}
			if (items > 0) {
				p.sendSystemMessage(Component.translatable("message.projecthero.darkseid_raid.valuables", items)
						.withStyle(ChatFormatting.GOLD));
			}
			if (relic || box) {
				p.sendSystemMessage(Component.translatable(relic ? "message.projecthero.darkseid_raid.relic" : "message.projecthero.darkseid_raid.mother_box_drop")
						.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
				level.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.3f);
			}
			p.giveExperiencePoints(cfg.experiencePoints);
			GraveboundCurse.award(p, "darkseid/anti_life");
			if (!raid.overloadHappened()) {
				GraveboundCurse.award(p, "darkseid/apokolips_falls");
			}
		}
	}

	/**
	 * v0.14.21: one participant's ores and gems -- the top-tier event, so the biggest haul: lapis and lapis blocks,
	 * diamonds, gold, iron, redstone, amethyst, emeralds, golden apples and two books, plus a 25% chance of netherite
	 * scrap, 10% of ancient debris and 10% of an enchanted golden apple. The common part is scaled by the configured
	 * number of invasion waves (60% for one wave, 100% for all five) and by {@code multiplier}; 0 gives nothing.
	 */
	public static List<ItemStack> valuables(RandomSource r, RegistryAccess registries, int waves, double multiplier) {
		List<ItemStack> out = new ArrayList<>();
		if (multiplier <= 0) {
			return out;
		}
		double scale = multiplier * (0.6 + 0.4 * Math.max(1, Math.min(DarkseidRaid.Stage.MAX_WAVES, waves))
				/ DarkseidRaid.Stage.MAX_WAVES);
		Valuables.add(out, r, Items.LAPIS_LAZULI, 32, 48, scale);
		Valuables.add(out, r, Items.LAPIS_BLOCK, 2, 4, scale);
		Valuables.add(out, r, Items.DIAMOND, 6, 10, scale);
		Valuables.add(out, r, Items.GOLD_INGOT, 12, 24, scale);
		Valuables.add(out, r, Items.IRON_INGOT, 24, 40, scale);
		Valuables.add(out, r, Items.REDSTONE, 24, 40, scale);
		Valuables.add(out, r, Items.AMETHYST_SHARD, 12, 20, scale);
		Valuables.add(out, r, Items.EMERALD, 10, 20, scale);
		Valuables.add(out, r, Items.GOLDEN_APPLE, 3, 5, scale);
		out.add(Valuables.book(r, registries, 30));
		out.add(Valuables.book(r, registries, 30));
		Valuables.chance(out, r, 0.25, Items.NETHERITE_SCRAP, 1, 2);
		Valuables.chance(out, r, 0.10, Items.ANCIENT_DEBRIS, 1, 1);
		Valuables.chance(out, r, 0.10, Items.ENCHANTED_GOLDEN_APPLE, 1, 1);
		return out;
	}
}
