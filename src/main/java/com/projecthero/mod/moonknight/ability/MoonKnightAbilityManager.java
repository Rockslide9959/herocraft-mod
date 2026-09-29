package com.projecthero.mod.moonknight.ability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightConfig;

import net.minecraft.server.level.ServerPlayer;

/**
 * Routes the six shared ability keys to Moon Knight's moves while he is transformed, turning raw press / release edges
 * into TAP / HOLD / SNEAK+KEY ({@link MoonKnightMove}). Everything is decided here, server-side: the client only says
 * "slot N went down / up"; the hold is timed on the server clock and sneak is read from the server's own player.
 *
 * <pre>
 *   R  Crescent Darts   G  Grapple Kick (Sneak: Shadow Step)   X  Dash (Sneak: Grappling Line)
 *   Z  Khonshu          C  Truncheon / Staff                   V  Alters
 * </pre>
 * v0.13.21 moved the keys (they were R Darts, G Grapple, X Cape, Z Truncheon, C Alters, V Khonshu). The Cape is no
 * longer on a key at all: the glide is jump + hold Sneak and the block is hold right click ({@link MoonKnightCape});
 * it still ticks here with the others.
 */
public final class MoonKnightAbilityManager {
	/** Per player: the game time each slot (index 0..5) went down, -1 if up / consumed, and whether its hold began. */
	private static final Map<Integer, long[]> PRESSED = new ConcurrentHashMap<>();
	private static final Map<Integer, boolean[]> HOLDING = new ConcurrentHashMap<>();

	private MoonKnightAbilityManager() {
	}

	/** The six keys are Moon Knight's while he is transformed. */
	public static boolean hasContext(ServerPlayer player) {
		return MoonKnight.isTransformed(player);
	}

	public static MoonKnightMove moveFor(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> MoonKnightDarts.INSTANCE;     // R
			case SLOT_2 -> MoonKnightGrapple.INSTANCE;   // G
			case SLOT_3 -> MoonKnightDash.INSTANCE;      // X
			case SLOT_4 -> MoonKnightKhonshu.INSTANCE;   // Z
			case SLOT_5 -> MoonKnightAlters.INSTANCE;    // V
			case SLOT_6 -> MoonKnightTruncheon.INSTANCE; // C
		};
	}

	private static final MoonKnightMove[] ALL = {
			MoonKnightDarts.INSTANCE, MoonKnightGrapple.INSTANCE, MoonKnightDash.INSTANCE, MoonKnightCape.INSTANCE,
			MoonKnightTruncheon.INSTANCE, MoonKnightKhonshu.INSTANCE, MoonKnightAlters.INSTANCE };

	private static long[] pressed(ServerPlayer player) {
		return PRESSED.computeIfAbsent(player.getId(), k -> new long[]{-1, -1, -1, -1, -1, -1});
	}

	private static boolean[] holding(ServerPlayer player) {
		return HOLDING.computeIfAbsent(player.getId(), k -> new boolean[6]);
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean down) {
		int i = slot.index();
		long now = player.level().getGameTime();
		long[] p = pressed(player);
		boolean[] h = holding(player);
		MoonKnightMove move = moveFor(slot);
		if (down) {
			if (p[i] >= 0) {
				return; // already down (a repeated press edge)
			}
			if (player.isShiftKeyDown()) {
				move.sneak(player);
				p[i] = -1;
				h[i] = false;
				return;
			}
			if (move.firesOnPress()) {
				move.tap(player); // no hold move on this key: no reason to wait for the release
				p[i] = -1;
				h[i] = false;
				return;
			}
			p[i] = now;
			h[i] = false;
			return;
		}
		if (p[i] < 0) {
			return; // a sneak press, or nothing
		}
		int held = (int) (now - p[i]);
		p[i] = -1;
		if (h[i]) {
			h[i] = false;
			move.holdRelease(player, held);
		} else {
			move.tap(player);
		}
	}

	/** Per tick for every player (from {@code AbilityRouter.serverTick}). */
	public static void serverTick(ServerPlayer player) {
		if (!hasContext(player)) {
			if (PRESSED.containsKey(player.getId())) {
				cancelAll(player);
			}
			return;
		}
		long now = player.level().getGameTime();
		long[] p = pressed(player);
		boolean[] h = holding(player);
		for (int i = 0; i < 6; i++) {
			if (p[i] < 0) {
				continue;
			}
			MoonKnightMove move = moveFor(AbilitySlot.values()[i]);
			int held = (int) (now - p[i]);
			if (!h[i] && held >= MoonKnightConfig.HOLD_THRESHOLD_TICKS) {
				h[i] = true;
				move.holdStart(player);
			}
			if (h[i]) {
				move.holdTick(player, held);
			}
		}
		for (MoonKnightMove move : ALL) {
			move.tick(player);
		}
	}

	/** Drop every held key (a hold in progress is cancelled, not released). */
	public static void cancelAll(ServerPlayer player) {
		long[] p = PRESSED.remove(player.getId());
		boolean[] h = HOLDING.remove(player.getId());
		if (p == null || h == null) {
			return;
		}
		for (int i = 0; i < 6; i++) {
			if (p[i] >= 0 && h[i]) {
				moveFor(AbilitySlot.values()[i]).cancelHold(player);
			}
		}
	}

	/** The suit is coming off (H, death, revoke): cancel holds and let every key drop what it keeps active. */
	public static void onUntransform(ServerPlayer player) {
		cancelAll(player);
		for (MoonKnightMove move : ALL) {
			move.onUntransform(player);
		}
	}

	public static void clearFor(ServerPlayer player) {
		PRESSED.remove(player.getId());
		HOLDING.remove(player.getId());
	}

	public static void clearSessionState() {
		PRESSED.clear();
		HOLDING.clear();
	}
}
