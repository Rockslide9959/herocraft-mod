package com.projecthero.mod.ironman;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.16.1 comic rework: integrity as THE suit resource (there is no energy any more).
 * <ul>
 *   <li><b>Combat clock</b> -- the last tick each player took or dealt damage ({@link #markCombat}, fed from the shared
 *       {@code AFTER_DAMAGE} hook in {@link IronManDamage}). Out of combat = {@link #OUT_OF_COMBAT_TICKS} (10 s) without
 *       either.</li>
 *   <li><b>Self-repair</b> -- a worn Mark 6 / Mark 7 (the only suits with {@link IronManSuit#selfRepairPerSecond()})
 *       repairs its integrity once out of combat: the Centurion slowly, the Space armour faster. The Hulkbuster never
 *       does (its Replacement Pod move is the field repair).</li>
 *   <li><b>Disabled</b> -- at 0 integrity the suit is offline: no flight, no abilities, heavy Slowness and a dark visor,
 *       until a Suit Platform / Stark Gantry repairs it ({@link #disabled}).</li>
 *   <li><b>Lockout</b> -- a brief forced shutdown (Ultron's Hijacker latch, a Contagion EMP): the synced
 *       {@code overloadUntil} timer the old Mark 4 overload used, read by {@link TonyStark#overloaded}.</li>
 * </ul>
 * The combat clock is static server state keyed by UUID, cleared in {@code ServerStateReset}.
 */
public final class IronManIntegrity {
	/** Ten seconds without taking or dealing damage. */
	public static final int OUT_OF_COMBAT_TICKS = 200;

	private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();

	private IronManIntegrity() {
	}

	public static void clearSessionState() {
		LAST_COMBAT.clear();
	}

	public static void forget(UUID player) {
		LAST_COMBAT.remove(player);
	}

	/** This player just took or dealt damage. */
	public static void markCombat(ServerPlayer player) {
		LAST_COMBAT.put(player.getUUID(), player.level().getGameTime());
	}

	/** Gametest hook: pretend the last hit was {@code ticksAgo} ticks ago. */
	public static void setLastCombat(ServerPlayer player, long ticksAgo) {
		LAST_COMBAT.put(player.getUUID(), player.level().getGameTime() - ticksAgo);
	}

	public static boolean outOfCombat(ServerPlayer player) {
		Long last = LAST_COMBAT.get(player.getUUID());
		return last == null || player.level().getGameTime() - last >= OUT_OF_COMBAT_TICKS;
	}

	/** True when the worn suit {@code suitId} has no integrity left -- it is offline until repaired. */
	public static boolean disabled(ServerPlayer player, String suitId) {
		return suitId != null && IronManEnergy.integrity(player, suitId) <= 0f;
	}

	/** A brief forced suit shutdown of {@code ticks} (never shortens one already running). */
	public static void lockout(ServerPlayer player, int ticks) {
		if (ticks <= 0) {
			return;
		}
		long until = player.level().getGameTime() + ticks;
		if (TonyStark.state(player).overloadUntil < until) {
			TonyStark.setOverloadUntil(player, until);
		}
	}

	/** Per tick for a worn, powered suit: the Mark 6 / Mark 7 out-of-combat self-repair. */
	public static void tick(ServerPlayer player, IronManSuit suit) {
		if (suit == null || suit.selfRepairPerSecond() <= 0f || !IronManArmor.hasChestplate(player, suit.id())) {
			return;
		}
		// once a second (the state is synced to every player -- no per-tick churn)
		if (player.level().getGameTime() % 20L != 0L || !outOfCombat(player)) {
			return;
		}
		float cur = IronManEnergy.integrity(player, suit.id());
		float max = suit.maxIntegrity();
		if (cur < max) {
			IronManEnergy.setIntegrity(player, suit.id(), Math.min(max, cur + suit.selfRepairPerSecond()));
		}
	}
}
