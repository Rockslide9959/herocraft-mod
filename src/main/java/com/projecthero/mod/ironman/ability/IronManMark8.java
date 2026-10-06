package com.projecthero.mod.ironman.ability;

import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.15.9: the Mark 8's own C ability -- <b>Sentry Mode</b> ({@link IronManSentryEntity}). Put {@link #SENTRY} in a
 * suit's slot 6 ({@code IronManSuit.Builder.abilities(..., IronManMark8.SENTRY)}); {@link IronManAbilities#trigger}
 * routes it here. Nothing in it is specific to one mark: any full worn Iron Man suit can stand as a sentry.
 * <pre>
 *   C        step out of the suit -- the back opens, you step forward out of it, it closes and stands there
 *   Shift+C  the Call Armour picker (send the worn suit home), as Sneak+C does on the other marks
 * </pre>
 * The standing suit: right-click opens / closes its back (walk into the open back to put it on again), Sneak +
 * right-click cycles Regular / Defensive / Follow.
 */
public final class IronManMark8 {
	public static final String SUIT_ID = "mark_8";

	/** Slot 6 (C): Sentry Mode. */
	public static final String SENTRY = "mk8_sentry";

	private IronManMark8() {
	}

	/** Called from {@link IronManAbilities#trigger} for {@link #SENTRY}. Works for any suit that has it in a slot. */
	public static void trigger(ServerPlayer player, IronManSuit suit, String ability, boolean pressed) {
		if (!pressed || !SENTRY.equals(ability)) {
			return;
		}
		if (player.isShiftKeyDown()) {
			com.projecthero.mod.ironman.suit.IronManSuitCall.openMenu(player);
			return;
		}
		IronManSentryEntity.deploy(player);
	}
}
