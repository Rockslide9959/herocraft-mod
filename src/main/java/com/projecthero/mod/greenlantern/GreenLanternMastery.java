package com.projecthero.mod.greenlantern;

import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Willpower Mastery progression: Bonded -> I -> II -> III -> IV, each requiring a cumulative-energy
 * threshold plus one situational requirement (see {@code GreenLanternConfig}). Self-contained on
 * {@link GreenLanternState} -- there is no existing progression system to hook into (per the build
 * brief's explicit allowance for a standalone system).
 */
public final class GreenLanternMastery {
	private GreenLanternMastery() {
	}

	/** Re-checked after every energy spend -- cheap, and catches the "energy" half of every tier instantly. */
	public static void onEnergySpent(ServerPlayer player) {
		checkAdvance(player);
	}

	public static void onDamageBlocked(ServerPlayer player, float amount) {
		GreenLanternState c = GreenLantern.state(player).copy();
		c.totalDamageBlocked += amount;
		GreenLantern.save(player, c);
		checkAdvance(player);
	}

	public static void onFlightDistance(ServerPlayer player, double blocks) {
		if (blocks <= 0) {
			return;
		}
		GreenLanternState c = GreenLantern.state(player).copy();
		c.totalFlightDistance += blocks;
		GreenLantern.save(player, c);
		checkAdvance(player);
	}

	public static void onBossDefeated(ServerPlayer player) {
		GreenLanternState s = GreenLantern.state(player);
		if (s.bossDefeatedWhileBonded) {
			return;
		}
		GreenLanternState c = s.copy();
		c.bossDefeatedWhileBonded = true;
		GreenLantern.save(player, c);
		checkAdvance(player);
	}

	/** Called from the per-player server tick while bonded -- tracks "survive one full night". */
	public static void tickNightWatch(ServerPlayer player) {
		GreenLanternState s = GreenLantern.state(player);
		if (s.nightSurvived) {
			return;
		}
		ServerLevel level = player.serverLevel();
		boolean isNight = !level.isDay();
		if (isNight && s.nightStartTick == 0L) {
			GreenLanternState c = s.copy();
			c.nightStartTick = level.getDayTime();
			GreenLantern.save(player, c);
			return;
		}
		if (!isNight && s.nightStartTick != 0L) {
			GreenLanternState c = s.copy();
			c.nightSurvived = true;
			c.nightStartTick = 0L;
			GreenLantern.save(player, c);
			checkAdvance(player);
		}
	}

	private static void checkAdvance(ServerPlayer player) {
		GreenLanternState s = GreenLantern.state(player);
		if (!s.hasPower) {
			return;
		}
		int next = -1;
		if (s.masteryLevel == GreenLanternState.MASTERY_BONDED
				&& s.totalEnergySpent >= GreenLanternConfig.MASTERY_I_ENERGY && s.nightSurvived) {
			next = GreenLanternState.MASTERY_I;
		} else if (s.masteryLevel == GreenLanternState.MASTERY_I
				&& s.totalEnergySpent >= GreenLanternConfig.MASTERY_II_ENERGY
				&& s.totalDamageBlocked >= GreenLanternConfig.MASTERY_II_DAMAGE_BLOCKED) {
			next = GreenLanternState.MASTERY_II;
		} else if (s.masteryLevel == GreenLanternState.MASTERY_II
				&& s.totalEnergySpent >= GreenLanternConfig.MASTERY_III_ENERGY
				&& s.totalFlightDistance >= GreenLanternConfig.MASTERY_III_FLIGHT_DISTANCE) {
			next = GreenLanternState.MASTERY_III;
		} else if (s.masteryLevel == GreenLanternState.MASTERY_III
				&& s.totalEnergySpent >= GreenLanternConfig.MASTERY_IV_ENERGY && s.bossDefeatedWhileBonded) {
			next = GreenLanternState.MASTERY_IV;
		}
		if (next == -1) {
			return;
		}
		GreenLanternState c = s.copy();
		c.masteryLevel = next;
		GreenLantern.save(player, c);
		announce(player, next);
	}

	private static void announce(ServerPlayer player, int level) {
		String name = switch (level) {
			case GreenLanternState.MASTERY_I -> "I";
			case GreenLanternState.MASTERY_II -> "II";
			case GreenLanternState.MASTERY_III -> "III";
			case GreenLanternState.MASTERY_IV -> "IV";
			default -> "?";
		};
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.mastery_unlocked",
				name).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.3f);
	}
}
