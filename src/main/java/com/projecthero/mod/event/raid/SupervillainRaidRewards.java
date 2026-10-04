package com.projecthero.mod.event.raid;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.SupervillainVariant;
import com.projecthero.mod.event.reward.Valuables;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Supervillain Village Raid rewards (spec sections 36-40).
 *
 * <h2>No duplication paths</h2>
 * The boss can die only once, so {@link #onEntityDied} marking the raid's boss as defeated is
 * idempotent. The victory bundle is handed out from {@link SupervillainRaid#onFinished} guarded by a
 * persisted {@code rewardsGranted} flag, so a restart between the boss dying and the drops landing
 * cannot produce them twice.
 */
public final class SupervillainRaidRewards {
	/** Split across a few orbs so the pickup animation reads as a big reward. */
	private static final int VICTORY_XP = 600;

	private SupervillainRaidRewards() {
	}

	/**
	 * Death hook, called for every entity from the shared raid death listener. Fast-returns for
	 * anything that is not a Supervillain-Raid mob.
	 */
	public static void onEntityDied(LivingEntity entity, ServerLevel level) {
		EventInstance event = EventManager.owning(entity);
		if (!(event instanceof SupervillainRaid raid)) {
			return;
		}
		raid.disown(entity.getUUID());
		if (entity instanceof EmpoweredZombie boss && boss.variant() != null) {
			raid.onBossDied(boss.blockPosition());
			level.playSound(null, boss.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 1.2f, 0.6f);
		}
	}

	/**
	 * The victory bundle: dropped where the boss fell, so it is the participants who were there who
	 * collect it (spec section 41 -- rewards go to who fought, not to someone thousands of blocks away).
	 */
	public static void grantVictory(ServerLevel level, SupervillainRaid raid, BlockPos at) {
		RandomSource random = level.random;
		EventConfig.SupervillainRaid cfg = EventConfig.supervillain();
		SupervillainVariant variant = raid.variant();
		String power = raid.powerKey();

		// Guaranteed.
		drop(level, at, new ItemStack(SupervillainRaidItems.VILLAIN_CACHE, 1 + random.nextInt(2)));
		drop(level, at, new ItemStack(Items.EMERALD, 12 + random.nextInt(13)));
		spawnXp(level, at, VICTORY_XP);
		// v0.14.21: ores and gems, more for a bigger party
		for (ItemStack stack : victoryValuables(random, level.registryAccess(), raid.participants().eligibleCount())) {
			drop(level, at, stack);
		}

		// Chance drops.
		if (random.nextDouble() < cfg.supervillainTokenDropChance) {
			drop(level, at, new ItemStack(SupervillainRaidItems.SUPERVILLAIN_TOKEN));
		}
		if (!power.isEmpty() && random.nextDouble() < cfg.powerFragmentDropChance) {
			drop(level, at, SupervillainRaidItems.PowerFragmentItem.of(power));
		}
		if (variant != null && random.nextDouble() < cfg.bossTrophyChance) {
			drop(level, at, new ItemStack(switch (variant) {
				case CHIMERA -> SupervillainRaidItems.CHIMERA_CORE;
				case ARSENAL -> SupervillainRaidItems.ARSENAL_REACTOR;
				case OMEGA_MAGE -> SupervillainRaidItems.OMEGA_CRYSTAL;
			}));
		}

		// Champion of the Village -- a stronger, time-limited Hero of the Village. Re-applied fresh (not
		// stacked) so it cannot be farmed into a permanent max discount.
		for (ServerPlayer player : raid.participants().onlineEligible(level)) {
			player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
			player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE,
					cfg.championOfTheVillageTicks, 2, false, false, true));
			player.sendSystemMessage(Component.translatable("event.projecthero.supervillain_raid.champion")
					.withStyle(ChatFormatting.GOLD));
		}
		ProjectHeroMod.LOGGER.info("[SupervillainRaid] Victory rewards granted at {}", at);
	}

	/**
	 * v0.14.21: the ores and gems in the victory bundle -- a mid-sized event, so less than a Cursed Grave Chest but a
	 * proper haul, a third more per extra fighter (up to double, the Horde chests' rule). A 30% lapis block and a 5%
	 * enchanted golden apple are the rare extras.
	 */
	public static java.util.List<ItemStack> victoryValuables(RandomSource r, net.minecraft.core.RegistryAccess registries,
			int participants) {
		double more = Valuables.partyScale(participants);
		java.util.List<ItemStack> out = new java.util.ArrayList<>();
		Valuables.add(out, r, Items.LAPIS_LAZULI, 16, 28, more);
		Valuables.add(out, r, Items.REDSTONE, 12, 20, more);
		Valuables.add(out, r, Items.IRON_INGOT, 10, 18, more);
		Valuables.add(out, r, Items.GOLD_INGOT, 6, 12, more);
		Valuables.add(out, r, Items.DIAMOND, 2, 5, more);
		Valuables.add(out, r, Items.AMETHYST_SHARD, 6, 12, more);
		Valuables.add(out, r, Items.GOLDEN_APPLE, 1, 3, more);
		out.add(Valuables.book(r, registries, 25));
		Valuables.chance(out, r, 0.30, Items.LAPIS_BLOCK, 1, 1);
		Valuables.chance(out, r, 0.05, Items.ENCHANTED_GOLDEN_APPLE, 1, 1);
		return out;
	}

	private static void spawnXp(ServerLevel level, BlockPos at, int total) {
		int remaining = total;
		while (remaining > 0) {
			int orb = Math.min(remaining, 60 + level.random.nextInt(40));
			ExperienceOrb.award(level, net.minecraft.world.phys.Vec3.atCenterOf(at), orb);
			remaining -= orb;
		}
	}

	private static void drop(ServerLevel level, BlockPos at, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}
}
