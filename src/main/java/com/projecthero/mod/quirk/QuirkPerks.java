package com.projecthero.mod.quirk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.quirk.item.QuirkSuits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 Quirk framework: the costume set perk hook. "The suit should matter in play": each full set of a Quirk's own
 * hero suit gives that Quirk one kit perk. The framework tracks when the set goes on and off and tells the kit
 * ({@link QuirkKit#onSuitChanged}); a kit can also just ask {@link #fullSet} when a move runs. A numeric meter perk is one
 * line in the meter spec ({@code QuirkMeterSpec.Builder#suitBuild}: Uravity's -25% Nausea).
 */
public final class QuirkPerks {
	private static final Map<UUID, Boolean> WORN = new ConcurrentHashMap<>();

	private QuirkPerks() {
	}

	/** Client-safe: holds a kit Quirk and wears that Quirk's own full suit set. */
	public static boolean fullSet(Player p) {
		Quirk q = Quirks.quirk(p);
		if (q == null || q.standalone) {
			return false;
		}
		QuirkSuits.Suit suit = QuirkSuits.Suit.of(q);
		return suit != null && QuirkSuits.fullSetWorn(p, suit);
	}

	static void tick(ServerPlayer p, Quirk q) {
		if (p.tickCount % 10 != 0) {
			return;
		}
		boolean now = fullSet(p);
		Boolean was = WORN.put(p.getUUID(), now);
		if (was == null ? now : was != now) {
			QuirkKits.of(q).onSuitChanged(p, now);
		}
	}

	static void clearSessionState() {
		WORN.clear();
	}

	static void forget(UUID id) {
		WORN.remove(id);
	}
}
