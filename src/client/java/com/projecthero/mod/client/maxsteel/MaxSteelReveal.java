package com.projecthero.mod.client.maxsteel;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.maxsteel.data.MaxSteelState;

import net.minecraft.world.entity.player.Player;

/**
 * Small client-side reads of the synced Max Steel state for the renderers. v0.14.2: the per-bone reveal thresholds
 * that used to live here are gone -- the suit now forms texel by texel, see {@link MaxSteelNano}.
 */
public final class MaxSteelReveal {
	private MaxSteelReveal() {
	}

	private static MaxSteelState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.MAX_STEEL_STATE, null);
	}

	/** True while a suit-up or suit-down animation is running. */
	public static boolean isRevealing(Player player) {
		MaxSteelState s = state(player);
		return s != null && s.transformDir != MaxSteelState.DIR_IDLE;
	}

	/** True while the player is in Turbo Stealth -- the suit model should not render. */
	public static boolean isStealthed(Player player) {
		MaxSteelState s = state(player);
		return s != null && s.transformed
				&& s.modeEnum() == com.projecthero.mod.maxsteel.MaxSteelMode.STEALTH;
	}
}
