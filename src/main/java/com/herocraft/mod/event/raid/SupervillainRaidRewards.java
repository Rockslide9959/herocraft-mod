package com.herocraft.mod.event.raid;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.event.EventConfig;
import com.herocraft.mod.event.EventInstance;
import com.herocraft.mod.event.EventManager;
import com.herocraft.mod.event.entity.EmpoweredZombie;
import com.herocraft.mod.event.entity.SupervillainVariant;

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
			player.sendSystemMessage(Component.translatable("event.herocraft.supervillain_raid.champion")
					.withStyle(ChatFormatting.GOLD));
		}
		HeroCraftMod.LOGGER.info("[SupervillainRaid] Victory rewards granted at {}", at);
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
