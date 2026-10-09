package com.projecthero.mod.client.wolverine;

import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.wolverine.WolverineConfig;
import com.projecthero.mod.wolverine.data.WolverineState;

import net.minecraft.world.entity.player.Player;

/**
 * The Wolverine flesh look, keyed off synced state so every viewer agrees: raw flesh while dying, and
 * after a Death Surge the skin fades back over the flesh as {@code WolverineState.skinRecovery} climbs
 * from 0 to 1 (v0.15.18: it only climbs while he is at full HP -- see {@code WolverinePassives}).
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
		// v0.15.18: no timer -- the server-synced skin recovery, which only advances while he is at full HP
		return Math.max(0.0f, Math.min(1.0f, s.skinRecovery));
	}

	/** v0.12.43: how much raw flesh shows on his LEGS after a survived lethal fall: 1 for 20 s, then fading to 0 over 20 s (0 = none). */
	public static float legFleshAlpha(Player player, float partialTick) {
		WolverineState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.WOLVERINE_STATE, null);
		if (s == null || !s.hasPower || s.legFleshStartedAt <= 0L) {
			return 0.0f;
		}
		float t = player.level().getGameTime() + partialTick - s.legFleshStartedAt;
		if (t < 0.0f) {
			return 0.0f;
		}
		if (t < WolverineConfig.LEG_FLESH_HOLD_TICKS) {
			return 1.0f;
		}
		return Math.max(0.0f, 1.0f - (t - WolverineConfig.LEG_FLESH_HOLD_TICKS) / WolverineConfig.LEG_FLESH_FADE_TICKS);
	}

	public static boolean active(Player player, float partialTick) {
		return skinAlpha(player, partialTick) < 1.0f;
	}
}
