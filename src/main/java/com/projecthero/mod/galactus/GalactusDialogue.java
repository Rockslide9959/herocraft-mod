package com.projecthero.mod.galactus;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.1: the Silver Surfer and Galactus speak -- a line in chat to everyone within 140 blocks of the arena. Original lines
 * ({@code boss.projecthero.galactus.surfer.say.*}, {@code boss.projecthero.galactus.galactus.say.*}); the callers
 * rate-limit (the event keeps the last line's time).
 */
public final class GalactusDialogue {
	/** Minimum gap (ticks) between two non-forced lines. */
	public static final int GAP = 160;

	private GalactusDialogue() {
	}

	public static void surfer(ServerLevel level, BlockPos near, String key) {
		say(level, near, Component.translatable("entity.projecthero.silver_surfer").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
				Component.translatable("boss.projecthero.galactus.surfer.say." + key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	public static void galactus(ServerLevel level, BlockPos near, String key) {
		say(level, near, Component.translatable("entity.projecthero.galactus").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
				Component.translatable("boss.projecthero.galactus.galactus.say." + key).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	private static void say(ServerLevel level, BlockPos near, Component who, Component what) {
		Component line = Component.translatable("boss.projecthero.galactus.chat", who, what);
		for (ServerPlayer p : level.players()) {
			if (near == null || p.distanceToSqr(near.getX() + 0.5, p.getY(), near.getZ() + 0.5) < 140 * 140) {
				p.sendSystemMessage(line);
			}
		}
	}
}
