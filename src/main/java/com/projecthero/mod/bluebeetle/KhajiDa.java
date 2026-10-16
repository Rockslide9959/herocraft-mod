package com.projecthero.mod.bluebeetle;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.5: Khaji-Da, the scarab, talking to its host in chat ({@code Khaji-Da: ...}, blue italics) -- the same idea as the
 * Symbiote's voice, much less chatty. Each topic has a few lines picked at random and its own cooldown, and nothing is
 * said more than once every 8 seconds overall.
 */
public final class KhajiDa {
	public enum Topic {
		BOND(3, 0),
		SUIT_UP(4, 600),
		LOW_HEALTH(3, 600),
		ADAPTED(3, 200),
		UNLEASHED(3, 0),
		UNLEASH_END(2, 0),
		WARN(3, 600),
		KILL(4, 900),
		/** v0.16.5: the takeover found nothing left to fight and hands the body back. */
		TAKEOVER_CLEAR(3, 0);

		final int lines;
		final int cooldown;

		Topic(int lines, int cooldown) {
			this.lines = lines;
			this.cooldown = cooldown;
		}
	}

	private static final Map<UUID, Map<Topic, Long>> LAST = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_ANY = new ConcurrentHashMap<>();

	private KhajiDa() {
	}

	static void clearSessionState() {
		LAST.clear();
		LAST_ANY.clear();
	}

	static void forget(UUID id) {
		LAST.remove(id);
		LAST_ANY.remove(id);
	}

	public static void speak(ServerPlayer player, Topic topic, Object... args) {
		long now = player.level().getGameTime();
		Map<Topic, Long> mine = LAST.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
		Long last = mine.get(topic);
		if (last != null && now - last < topic.cooldown && now >= last) {
			return;
		}
		Long any = LAST_ANY.get(player.getUUID());
		boolean urgent = topic == Topic.BOND || topic == Topic.UNLEASHED || topic == Topic.UNLEASH_END || topic == Topic.ADAPTED
				|| topic == Topic.TAKEOVER_CLEAR;
		if (!urgent && any != null && now - any < 160 && now >= any) {
			return;
		}
		mine.put(topic, now);
		LAST_ANY.put(player.getUUID(), now);
		int line = player.getRandom().nextInt(topic.lines);
		Component msg = Component.translatable("message.projecthero.khaji_da.prefix").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD)
				.append(Component.translatable("message.projecthero.khaji_da." + topic.name().toLowerCase(java.util.Locale.ROOT) + "." + line, args)
						.withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
		player.displayClientMessage(msg, false);
	}

	/** Unsuited: a hostile is close. */
	static void warn(ServerPlayer player, Entity hostile) {
		speak(player, Topic.WARN, hostile.getDisplayName());
	}
}
