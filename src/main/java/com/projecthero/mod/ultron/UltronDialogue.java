package com.projecthero.mod.ultron;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.15.12: Ultron talks -- a line in chat to everyone within 64 blocks of the speaking body, rate-limited by the caller
 * (each body keeps its own last-line time) so it never spams. Lines are original ({@code boss.projecthero.ultron.say.*}).
 */
public final class UltronDialogue {
	/** Minimum gap (ticks) between two non-forced lines from one body. */
	public static final int GAP = 160;

	private UltronDialogue() {
	}

	/**
	 * Says {@code key} from {@code speaker} unless the last line was under {@link #GAP} ticks ago ({@code force} skips
	 * that). Returns the new last-line time (unchanged when nothing was said).
	 */
	public static long say(ServerLevel level, Entity speaker, String key, boolean force, long lastLineTick) {
		long now = level.getGameTime();
		if (!force && now - lastLineTick < GAP) {
			return lastLineTick;
		}
		Component line = Component.translatable("boss.projecthero.ultron.chat",
				Component.translatable("boss.projecthero.ultron.name").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.translatable("boss.projecthero.ultron.say." + key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(speaker) < 64 * 64) {
				p.sendSystemMessage(line);
			}
		}
		return now;
	}
}
