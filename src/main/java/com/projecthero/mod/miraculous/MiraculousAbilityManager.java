package com.projecthero.mod.miraculous;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.6: routes the universal ability slots to the holder's {@link KwamiKit} ({@link com.projecthero.mod.hero.AbilityRouter}
 * hands them over while {@link #hasContext}: holds a Kwami, no mutation selected). Key auto-repeat is filtered here (a
 * press within {@link #REPEAT_WINDOW} of the last on the same key, with no release between). Shift is read off the
 * player's sneak state. Detransformed, the keys only say so.
 */
public final class MiraculousAbilityManager {
	static final int REPEAT_WINDOW = 25;

	private static final Map<UUID, long[]> LAST_PRESS = new ConcurrentHashMap<>();

	private MiraculousAbilityManager() {
	}

	static void clearSessionState() {
		LAST_PRESS.clear();
	}

	static void forget(UUID id) {
		LAST_PRESS.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Miraculous.hasPower(player)) {
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
		boolean repeat = last[i] >= 0L && now - last[i] <= REPEAT_WINDOW && now >= last[i];
		last[i] = now;
		return !repeat;
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!freshPress(player, slot, pressed)) {
			return;
		}
		Kwami kwami = Miraculous.kwami(player);
		if (kwami == null) {
			return;
		}
		if (!Miraculous.canAct(player)) {
			if (pressed && !Miraculous.transformed(player)) {
				Miraculous.say(player, "message.projecthero.miraculous.not_transformed", ChatFormatting.GOLD);
			}
			return;
		}
		MiraculousKits.of(kwami).handle(player, slot, pressed, player.isShiftKeyDown());
	}

	/** The two moves on one key: [plain, shift]. */
	public static String[] idsOf(Kwami kwami, AbilitySlot slot) {
		return MiraculousKits.of(kwami).idsOf(slot);
	}

	public static int maxCooldown(Kwami kwami, String id) {
		return MiraculousKits.of(kwami).maxCooldown(id);
	}
}
