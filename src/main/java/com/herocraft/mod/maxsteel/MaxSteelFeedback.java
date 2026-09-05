package com.herocraft.mod.maxsteel;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Small shared player-message / cue helpers for Max Steel abilities. */
public final class MaxSteelFeedback {
	private MaxSteelFeedback() {
	}

	public static void noEnergy(ServerPlayer player, float required) {
		player.displayClientMessage(Component.translatable("message.herocraft.max_steel.no_energy",
				String.format(java.util.Locale.ROOT, "%.0f", required)), true);
	}

	public static void onCooldown(ServerPlayer player, String abilityKey, int ticksRemaining) {
		player.displayClientMessage(Component.translatable("message.herocraft.ability.on_cooldown",
				Component.translatable("herocraft.max_steel.ability." + abilityKey),
				String.format(java.util.Locale.ROOT, "%.1f", ticksRemaining / 20.0f)), true);
	}

	public static void notTransformed(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.herocraft.max_steel.not_transformed"), true);
	}
}
