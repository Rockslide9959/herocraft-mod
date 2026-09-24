package com.projecthero.mod.client.wolverine;

import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.wolverine.WolverineConfig;
import com.projecthero.mod.wolverine.data.WolverineState;

import net.minecraft.world.entity.player.Player;

/**
 * The Wolverine flesh look, keyed off synced state so every viewer agrees: raw flesh while dying, and
 * after an emergency resurrection full flesh for {@code FLESH_HOLD_TICKS}, then the skin fades back
 * over the flesh across {@code FLESH_FADE_TICKS}.
 */
public final class WolverineFlesh {
	private WolverineFlesh() {
	}

	/** How much of the player's own skin shows: 0 = pure flesh, 1 = normal (no flesh at all). */
	public static float skinAlpha(Player player, float partialTick) {
		WolverineState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.WOLVERINE_STATE, null);
		if (s == null || !s.hasPower) {
			return 1.0f;
		}
		if (player.isDeadOrDying()) {
			return 0.0f;
		}
		if (s.fleshStartedAt <= 0L) {
			return 1.0f;
		}
		float t = player.level().getGameTime() + partialTick - s.fleshStartedAt;
		if (t < 0.0f) {
			return 1.0f;
		}
		if (t < WolverineConfig.FLESH_HOLD_TICKS) {
			return 0.0f;
		}
		return Math.min(1.0f, (t - WolverineConfig.FLESH_HOLD_TICKS) / WolverineConfig.FLESH_FADE_TICKS);
	}

	public static boolean active(Player player, float partialTick) {
		return skinAlpha(player, partialTick) < 1.0f;
	}
}
