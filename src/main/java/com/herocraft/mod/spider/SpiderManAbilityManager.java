package com.herocraft.mod.spider;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.hero.AbilitySlot;
import com.herocraft.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to Spider-Man's kit.
 *
 * <p>{@link com.herocraft.mod.hero.AbilityRouter} hands Spider-Man the slots when {@link #hasContext}
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

	private static boolean blackSuit(ServerPlayer player) {
		return com.herocraft.mod.symbiote.Symbiote.isActive(player)
				&& com.herocraft.mod.symbiote.SymbioteHostType.of(player)
						== com.herocraft.mod.symbiote.SymbioteHostType.SPIDER_MAN;
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		switch (slot) {
			// R -- held: the line stays attached for as long as the key is down.
			case SLOT_1 -> {
				if (pressed) {
					SpiderSwing.fire(player);
				} else {
					SpiderSwing.detach(player, true);
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					SpiderAbilities.webZip(player);
				}
			}
			// X -- Black Suit: sneak = Symbiote Tendril Strike (a straight tendril melee hit, not a pull)
			case SLOT_3 -> {
				if (pressed) {
					if (blackSuit(player) && player.isShiftKeyDown()) {
						com.herocraft.mod.symbiote.SymbioteBlackSuitAbilities.tendrilStrike(player);
					} else {
						SpiderAbilities.webYank(player);
					}
				}
			}
			// Z -- Black Suit: sneak + hold = Symbiote Crush (release ends it)
			case SLOT_4 -> {
				if (pressed) {
					if (blackSuit(player) && player.isShiftKeyDown()) {
						com.herocraft.mod.symbiote.SymbioteBlackSuitAbilities.beginCrush(player);
					} else {
						SpiderAbilities.webShot(player);
					}
				} else {
					com.herocraft.mod.symbiote.SymbioteBlackSuitAbilities.releaseCrush(player);
				}
			}
			// V -- tap = Web Net; sneak + hold = Web Blossom (charge 3 s, releases on let-go)
			case SLOT_5 -> {
				if (pressed) {
					if (player.isShiftKeyDown()) {
						SpiderAbilities.beginWebBlossom(player);
					} else {
						SpiderAbilities.webNet(player);
					}
				} else {
					SpiderAbilities.releaseWebBlossom(player);
				}
			}
			// C -- toggle the automatic wall-crawl mode (v0.6.17, replaced Web Cocoon). Black Suit:
			// sneak while airborne = Symbiote Slam Enhancement instead (falls through to the ordinary
			// toggle if grounded or the slam is on cooldown).
			case SLOT_6 -> {
				if (pressed) {
					boolean handledBySlam = blackSuit(player) && player.isShiftKeyDown()
							&& com.herocraft.mod.symbiote.SymbioteBlackSuitAbilities.slam(player);
					if (!handledBySlam) {
						SpiderAbilities.toggleWallCrawl(player);
					}
				}
			}
		}
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
		com.herocraft.mod.symbiote.SymbioteBlackSuitAbilities.serverTick(player);
	}
}
