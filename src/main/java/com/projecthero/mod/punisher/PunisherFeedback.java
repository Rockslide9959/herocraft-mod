package com.projecthero.mod.punisher;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Shared short action-bar messages for the Punisher abilities. */
public final class PunisherFeedback {
	private PunisherFeedback() {
	}

	public static void message(ServerPlayer player, String key) {
		player.displayClientMessage(
				Component.translatable("message.projecthero.punisher." + key).withStyle(ChatFormatting.GRAY), true);
	}
}
