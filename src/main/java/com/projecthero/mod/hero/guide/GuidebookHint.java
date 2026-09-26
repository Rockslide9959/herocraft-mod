package com.projecthero.mod.hero.guide;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.12.36 -- the first time a player ever joins a world they see a message at the bottom of the screen telling them to craft
 * the Guidebook. {@code GUIDEBOOK_HINT_SEEN} (persistent) makes it once per player per world; {@code GUIDEBOOK_HINT_LEFT}
 * (transient) counts down the few seconds the message is sent (once, in chat).
 */
public final class GuidebookHint {
	private static final int TOTAL_TICKS = 20 * 5;
	private static final int FIRST_AT = TOTAL_TICKS - 60; // a three-second beat so it lands after the join messages

	private GuidebookHint() {
	}

	public static void onJoin(ServerPlayer player) {
		if (Boolean.TRUE.equals(player.getAttached(ModAttachments.GUIDEBOOK_HINT_SEEN))) {
			return;
		}
		player.setAttached(ModAttachments.GUIDEBOOK_HINT_SEEN, true);
		player.setAttached(ModAttachments.GUIDEBOOK_HINT_LEFT, TOTAL_TICKS);
	}

	public static void tick(ServerPlayer player) {
		Integer left = player.getAttached(ModAttachments.GUIDEBOOK_HINT_LEFT);
		if (left == null) {
			return;
		}
		if (left <= 0) {
			player.removeAttached(ModAttachments.GUIDEBOOK_HINT_LEFT);
			return;
		}
		player.setAttached(ModAttachments.GUIDEBOOK_HINT_LEFT, left - 1);
		if (left == FIRST_AT) {
			// v0.12.37: one chat line, not a permanent action-bar message
			player.displayClientMessage(Component.translatable("message.projecthero.guidebook_hint")
					.withStyle(ChatFormatting.GOLD), false);
		}
	}
}
