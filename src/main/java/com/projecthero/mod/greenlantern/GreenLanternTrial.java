package com.projecthero.mod.greenlantern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.greenlantern.block.FallenLanternPedestalBlock;
import com.projecthero.mod.greenlantern.item.GreenLanternItems;
import com.projecthero.mod.hero.HeroTiers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Will Trial: right-clicking an unclaimed Fallen Lantern Site pedestal begins a 3-wave fight
 * within a 32-block radius. Success binds Green Lantern, awards the site's single Lantern Core, and
 * permanently flips the pedestal's {@link FallenLanternPedestalBlock#CLAIMED} blockstate so the same
 * site can never be farmed again. Failure (leaving the radius for 8s, dying, or logging out) drops a
 * 10-minute per-player cooldown for that pedestal.
 *
 * <p>First-pass enemies are vanilla mobs, boosted and marked Glowing for readability -- see the build
 * brief's explicit allowance not to block the whole feature on a bespoke trial mob.
 */
public final class GreenLanternTrial {
	private static final class Trial {
		final BlockPos pedestal;
		final Vec3 center;
		final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
		int wave = 1;
		final List<Integer> liveMobIds = new ArrayList<>();
		long outOfRadiusSince = -1L;

		Trial(BlockPos pedestal, Vec3 center, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
			this.pedestal = pedestal;
			this.center = center;
			this.dimension = dimension;
		}
	}

	private static final Map<UUID, Trial> ACTIVE = new ConcurrentHashMap<>();
	/** (player, pedestal) -> game-time the 10-minute failure cooldown ends. */
	private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();
	/** Pedestals with a trial currently running -- refuses a second player racing the same site. */
	private static final java.util.Set<Long> PEDESTALS_IN_PROGRESS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private GreenLanternTrial() {
	}

	public static void clearSessionState() {
		ACTIVE.clear();
		COOLDOWNS.clear();
		PEDESTALS_IN_PROGRESS.clear();
	}

	public static void attemptStart(ServerPlayer player, BlockPos pedestal, boolean claimed) {
		if (claimed) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.site_claimed"), true);
			return;
		}
		if (ACTIVE.containsKey(player.getUUID())) {
			return;
		}
		if (GreenLantern.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.already_bonded"), true);
			return;
		}
		if (HeroTiers.hasHeroTier(player) || HeroTiers.hasExperimental(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_ineligible"), true);
			return;
		}
		Long cooldownUntil = COOLDOWNS.get(cooldownKey(player, pedestal));
		if (cooldownUntil != null && player.level().getGameTime() < cooldownUntil) {
			int secs = (int) ((cooldownUntil - player.level().getGameTime()) / 20);
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_cooldown",
					secs), true);
			return;
		}
		// The last gate before committing: refuses a second player racing the same unclaimed pedestal.
		// Every earlier `return` above must NOT have reserved this, or a rejected attempt would leave
		// the pedestal permanently (falsely) marked in-progress.
		if (!PEDESTALS_IN_PROGRESS.add(pedestal.asLong())) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_in_progress"), true);
			return;
		}

		Trial trial = new Trial(pedestal, Vec3.atCenterOf(pedestal), player.level().dimension());
		ACTIVE.put(player.getUUID(), trial);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_begin")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		spawnWave(player, trial);
	}

	private static String cooldownKey(ServerPlayer player, BlockPos pedestal) {
		return player.getUUID() + "@" + pedestal.asLong();
	}

	private static void spawnWave(ServerPlayer player, Trial trial) {
		ServerLevel level = player.serverLevel();
		int ordinary = switch (trial.wave) {
			case 1 -> GreenLanternConfig.TRIAL_WAVE_1_COUNT;
			case 2 -> GreenLanternConfig.TRIAL_WAVE_2_COUNT;
			default -> GreenLanternConfig.TRIAL_WAVE_3_ORDINARY_COUNT;
		};
		int ranged = trial.wave == 2 ? 2 : 0;
		for (int i = 0; i < ordinary; i++) {
			MobFactory factory = i < ranged
					? lvl -> new Skeleton(net.minecraft.world.entity.EntityType.SKELETON, lvl)
					: lvl -> new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, lvl);
			Mob mob = spawnBoosted(level, trial.center, factory);
			if (mob != null) {
				trial.liveMobIds.add(mob.getId());
			}
		}
		if (trial.wave == 3) {
			Mob fearEcho = spawnBoosted(level, trial.center, lvl -> new Vex(net.minecraft.world.entity.EntityType.VEX, lvl));
			if (fearEcho != null) {
				fearEcho.setCustomName(Component.literal("Fear Echo").withStyle(ChatFormatting.DARK_GREEN));
				fearEcho.setCustomNameVisible(true);
				fearEcho.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60.0);
				fearEcho.setHealth(60.0f);
				fearEcho.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0);
				trial.liveMobIds.add(fearEcho.getId());
			}
		}
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_wave", trial.wave), true);
		level.playSound(null, trial.center.x, trial.center.y, trial.center.z, SoundEvents.RAVAGER_ROAR,
				SoundSource.HOSTILE, 1.0f, 1.0f);
	}

	private static Mob spawnBoosted(ServerLevel level, Vec3 center, MobFactory factory) {
		Vec3 pos = groundedPointNear(level, center, 6.0, 14.0);
		Mob mob = factory.create(level);
		if (mob == null) {
			return null;
		}
		mob.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
				net.minecraft.world.entity.MobSpawnType.EVENT, null);
		mob.getAttribute(Attributes.MAX_HEALTH).addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
				com.projecthero.mod.ProjectHeroMod.id("green_lantern_trial_hp"), 0.5, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		mob.setHealth(mob.getMaxHealth());
		mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20000, 0, false, false, false));
		level.addFreshEntity(mob);
		return mob;
	}

	private interface MobFactory {
		Mob create(ServerLevel level);
	}

	/**
	 * A random point in the ring [min, max] around {@code center}, snapped onto the real surface
	 * (the crater's walls mean a fixed {@code center.y} often lands a spawn inside solid ground). Tries
	 * a few times to avoid a column with no open headroom before falling back to whatever the last roll
	 * found -- never blocks spawning outright.
	 */
	private static Vec3 groundedPointNear(ServerLevel level, Vec3 center, double min, double max) {
		Vec3 best = null;
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = level.random.nextDouble() * Math.PI * 2;
			double dist = min + level.random.nextDouble() * (max - min);
			double x = center.x + Math.cos(angle) * dist;
			double z = center.z + Math.sin(angle) * dist;
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					(int) Math.floor(x), (int) Math.floor(z));
			Vec3 candidate = new Vec3(x, y, z);
			BlockPos feet = BlockPos.containing(candidate);
			if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
				return candidate;
			}
			best = candidate;
		}
		return best;
	}

	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (var it = ACTIVE.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Trial> entry = it.next();
			Trial trial = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				fail(entry.getKey(), trial, server, "message.projecthero.green_lantern.trial_failed_logout");
				it.remove();
				continue;
			}
			if (!player.isAlive()) {
				fail(entry.getKey(), trial, server, "message.projecthero.green_lantern.trial_failed_death");
				it.remove();
				continue;
			}
			if (player.level().dimension() != trial.dimension) {
				// Left the dimension entirely (portal, /execute in, command teleport) -- treat exactly
				// like leaving the 32-block radius rather than letting the trial silently follow them.
				fail(entry.getKey(), trial, server, "message.projecthero.green_lantern.trial_failed_left");
				it.remove();
				continue;
			}
			if (player.position().distanceTo(trial.center) > GreenLanternConfig.TRIAL_RADIUS) {
				if (trial.outOfRadiusSince < 0) {
					trial.outOfRadiusSince = now;
				} else if (now - trial.outOfRadiusSince > GreenLanternConfig.TRIAL_LEAVE_FAIL_TICKS) {
					fail(entry.getKey(), trial, server, "message.projecthero.green_lantern.trial_failed_left");
					it.remove();
					continue;
				}
			} else {
				trial.outOfRadiusSince = -1L;
			}

			trial.liveMobIds.removeIf(id -> !(player.serverLevel().getEntity(id) instanceof Mob m) || !m.isAlive());
			if (trial.liveMobIds.isEmpty()) {
				if (trial.wave < 3) {
					trial.wave++;
					spawnWave(player, trial);
				} else {
					succeed(player, trial);
					it.remove();
				}
			}
		}
	}

	private static void fail(UUID playerId, Trial trial, MinecraftServer server, String messageKey) {
		PEDESTALS_IN_PROGRESS.remove(trial.pedestal.asLong());
		for (int id : trial.liveMobIds) {
			if (server.overworld().getEntity(id) instanceof Mob m) {
				m.discard();
			}
		}
		COOLDOWNS.put(playerId + "@" + trial.pedestal.asLong(),
				server.overworld().getGameTime() + GreenLanternConfig.TRIAL_FAIL_COOLDOWN_TICKS);
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player != null) {
			player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), false);
		}
	}

	private static void succeed(ServerPlayer player, Trial trial) {
		PEDESTALS_IN_PROGRESS.remove(trial.pedestal.asLong());
		for (int id : trial.liveMobIds) {
			if (player.serverLevel().getEntity(id) instanceof Mob m) {
				m.discard();
			}
		}
		var level = player.serverLevel();
		var pedestalState = level.getBlockState(trial.pedestal);
		if (pedestalState.is(com.projecthero.mod.greenlantern.block.GreenLanternBlocks.FALLEN_LANTERN_PEDESTAL)) {
			level.setBlock(trial.pedestal, pedestalState.setValue(FallenLanternPedestalBlock.CLAIMED, true),
					net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
		GreenLantern.bond(player);
		ItemStack core = new ItemStack(GreenLanternItems.LANTERN_CORE);
		if (!player.getInventory().add(core)) {
			player.drop(core, false);
		}
		if (!player.getInventory().add(new ItemStack(GreenLanternItems.POWER_RING))) {
			player.drop(new ItemStack(GreenLanternItems.POWER_RING), false);
		}
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
				60, 0.5, 1.0, 0.5, 0.2);
	}
}
