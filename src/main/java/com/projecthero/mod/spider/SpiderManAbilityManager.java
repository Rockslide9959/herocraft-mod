package com.projecthero.mod.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to Spider-Man's kit.
 *
 * <p>{@link com.projecthero.mod.hero.AbilityRouter} hands Spider-Man the slots when {@link #hasContext}
 * is true: the player has the power and has not deliberately switched to one of their experimental
 * mutations instead. Selecting an owned mutation from the power wheel takes the six slots the way it
 * always has; selecting "none" gives them straight back to Spider-Man. The passives -- wall crawling,
 * Spider Sense, the double jump, the physical enhancements -- are never affected by that choice, so a
 * Spider-Man running a mutation in the slots still crawls, still dodges and still double-jumps.
 *
 * <p>Deliberately sits <em>after</em> Thor and Iron Man in the router's priority list, for the same
 * reason Iron Man sits after Thor: those contexts are explicit (a hammer in hand, a suit on your
 * back), and an explicit context should always win over a permanently-on one.
 */
public final class SpiderManAbilityManager {
	private SpiderManAbilityManager() {
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!SpiderMan.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	/**
	 * v0.16.1: the 18-move kit -- tap / Shift / hold on R G X Z V C, resolved by {@link SpiderMoves#handle} (which also
	 * routes the Black Suit's Symbiote extras on Shift+X / Shift+Z / airborne Shift+C). Swinging is right-click with an
	 * empty hand, not a key; the N Traversal / Combat toggle and the C wall-crawl toggle are gone.
	 */
	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		SpiderMoves.handle(player, slot, pressed);
	}

	/** Per-player server tick for everything Spider-Man keeps running. */
	public static void serverTick(ServerPlayer player) {
		if (!SpiderMan.hasPower(player)) {
			return;
		}
		SpiderWebReserve.tick(player);
		SpiderSwing.serverTick(player);
		SpiderClimb.serverTick(player);
		SpiderSense.serverTick(player);
		SpiderPassives.tick(player);
		SpiderAbilities.tickWebBlossom(player);
		SpiderCombat.tick(player);
		SpiderMoves.tick(player);
		com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.serverTick(player);
	}
}
