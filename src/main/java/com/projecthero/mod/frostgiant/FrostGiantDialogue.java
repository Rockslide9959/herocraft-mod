package com.projecthero.mod.frostgiant;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.1: King Laufey speaks -- a line in chat to everyone within 80 blocks, rate-limited by the caller (he keeps his
 * own last-line time) so it never spams. Original lines ({@code boss.projecthero.frost_giants.laufey.say.*}). Heimdall's
 * warning (the natural trigger) is here too.
 */
public final class FrostGiantDialogue {
	/** Minimum gap (ticks) between two non-forced lines. */
	public static final int GAP = 200;

	private FrostGiantDialogue() {
	}

	/** Says {@code key} from {@code speaker} unless the last line was under {@link #GAP} ticks ago ({@code force} skips that). */
	public static long say(ServerLevel level, Entity speaker, String key, boolean force, long lastLineTick) {
		long now = level.getGameTime();
		if (!force && now - lastLineTick < GAP) {
			return lastLineTick;
		}
		Component line = Component.translatable("boss.projecthero.frost_giants.chat",
				Component.translatable("entity.projecthero.king_laufey").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
				Component.translatable("boss.projecthero.frost_giants.laufey.say." + key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(speaker) < 80 * 80) {
				p.sendSystemMessage(line);
			}
		}
		return now;
	}

	/** Heimdall, from the Bifrost: "The Jotnar march on Midgard." (gold). */
	public static void heimdall(ServerPlayer player, String key) {
		player.sendSystemMessage(Component.translatable("boss.projecthero.frost_giants.chat",
				Component.translatable("boss.projecthero.frost_giants.heimdall").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("boss.projecthero.frost_giants.heimdall.say." + key).withStyle(ChatFormatting.GOLD)));
	}
}
