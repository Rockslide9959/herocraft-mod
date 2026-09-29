package com.projecthero.mod.moonknight.ability;

import java.util.Locale;

import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.data.MoonKnightState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared helpers for every Moon Knight ability: the lunar power, cooldowns (divided by the lunar power), Vengeance
 * costs and feedback. Cooldown ids follow the HUD's convention: {@code <key id>} for the TAP, {@code <key id>_hold},
 * {@code <key id>_sneak}, where the key ids are {@code darts} (R), {@code grapple} (G), {@code truncheon} (Z),
 * {@code cape} (X), {@code alter} (C) and {@code khonshu} (V).
 */
public final class MoonKnightAbilities {
	private MoonKnightAbilities() {
	}

	public static float power(ServerPlayer player) {
		return MoonKnightLunar.power(player);
	}

	public static boolean night(ServerPlayer player) {
		return MoonKnightLunar.isMoonNight(player.level());
	}

	public static boolean fullMoon(ServerPlayer player) {
		return MoonKnightLunar.isFullMoonNight(player.level());
	}

	/** Is {@code id} off cooldown? If not, say how long is left (action bar) and return false. */
	public static boolean ready(ServerPlayer player, String id) {
		int left = MoonKnight.cooldownRemaining(player, id);
		if (left > 0) {
			player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.cooldown",
					String.format(Locale.ROOT, "%.1f", left / 20.0f)).withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		return true;
	}

	/** Start {@code id}'s cooldown: {@code baseTicks} divided by the current lunar power. */
	public static void cooldown(ServerPlayer player, String id, int baseTicks) {
		int ticks = MoonKnightLunar.cooldown(baseTicks, power(player));
		MoonKnightState c = MoonKnight.state(player).copy();
		c.abilityReadyAt.put(id, player.level().getGameTime() + ticks);
		MoonKnight.saveState(player, c);
	}

	/** Pay {@code amount} Vengeance, or say there isn't enough and return false. */
	public static boolean spendVengeance(ServerPlayer player, float amount) {
		float have = MoonKnight.vengeance(player);
		if (have < amount) {
			player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.no_vengeance",
					Math.round(amount)).withStyle(ChatFormatting.RED), true);
			return false;
		}
		MoonKnight.setVengeance(player, have - amount);
		return true;
	}

	public static void say(ServerPlayer player, String key, Object... args) {
		player.displayClientMessage(Component.translatable(key, args), true);
	}
}
