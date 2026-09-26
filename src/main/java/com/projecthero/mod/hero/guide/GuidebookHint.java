package com.projecthero.mod.hero.guide;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.12.36 -- the first time a player ever joins a world they see a message at the bottom of the screen telling them to craft
 * the Guidebook. {@code GUIDEBOOK_HINT_SEEN} (persistent) makes it once per player per world; {@code GUIDEBOOK_HINT_LEFT}
 * (transient) counts down the few seconds the message is repeated on the action bar so it cannot be missed.
 */
public final class GuidebookHint {
	private static final int TOTAL_TICKS = 20 * 14;
	private static final int FIRST_AT = TOTAL_TICKS - 40; // a two-second beat before the first message

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
		if (left <= FIRST_AT && left % 40 == 0) {
			player.displayClientMessage(Component.translatable("message.projecthero.guidebook_hint")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
		}
	}
}
