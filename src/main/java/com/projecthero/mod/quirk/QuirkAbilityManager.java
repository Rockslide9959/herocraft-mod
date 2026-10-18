package com.projecthero.mod.quirk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.7: routes the universal ability slots to the holder's {@link QuirkKit} ({@link com.projecthero.mod.hero.AbilityRouter}
 * hands them over while {@link #hasContext}: holds a Quirk, no mutation selected). Key auto-repeat is filtered here.
 * Shift is read off the player's sneak state. Erased or overloaded, the keys only say so (releases still go through so
 * held moves can end).
 */
public final class QuirkAbilityManager {
	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private QuirkAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Quirks.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	private static boolean freshPress(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		long[] last = LAST_PRESS.computeIfAbsent(player.getUUID(), k -> {
			long[] a = new long[AbilitySlot.values().length];
			java.util.Arrays.fill(a, -1L);
			return a;
		});
		int i = slot.ordinal();
		if (!pressed) {
			last[i] = -1L;
			return true;
		}
		long now = player.level().getGameTime();
		boolean repeat = last[i] >= 0L && now - last[i] <= QuirkConfig.REPEAT_WINDOW && now >= last[i];
		last[i] = now;
		return !repeat;
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!freshPress(player, slot, pressed)) {
			return;
		}
		Quirk quirk = Quirks.quirk(player);
		if (quirk == null || !player.isAlive() || player.isSpectator()) {
			return;
		}
		QuirkKit kit = QuirkKits.of(quirk);
		boolean shift = player.isShiftKeyDown();
		if (pressed) {
			if (PowerSuppression.isSuppressed(player)) {
				Quirks.say(player, "message.projecthero.power.erased", ChatFormatting.RED);
				return;
			}
			if (Quirks.overloaded(player) && !kit.usableWhileOverloaded(slot, shift)) {
				Quirks.say(player, "message.projecthero.quirk.overloaded." + quirk.id, ChatFormatting.RED);
				return;
			}
		}
		kit.handle(player, slot, pressed, shift);
	}

	/** The two moves on one key: [plain, shift]. */
	public static String[] idsOf(Quirk quirk, AbilitySlot slot) {
		return QuirkKits.of(quirk).idsOf(slot);
	}

	public static int maxCooldown(Quirk quirk, String id) {
		return QuirkKits.of(quirk).maxCooldown(id);
	}
}
