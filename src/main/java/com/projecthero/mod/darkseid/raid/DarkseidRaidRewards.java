package com.projecthero.mod.darkseid.raid;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.item.DarkseidItems;
import com.projecthero.mod.grave.GraveboundCurse;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

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
}
