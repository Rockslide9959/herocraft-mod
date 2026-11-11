package com.projecthero.mod.gyro;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Gyro Zeppeli: bridges the universal ability slots to the Spin. {@link com.projecthero.mod.hero.AbilityRouter} hands him
 * the slots when {@link #hasContext} is true. Shift is read server-side; a key's release goes to the same move its press did (so
 * letting go of Shift before G still fires the Golden Spin).
 *
 * <pre>
 *   R  Steel Ball        Shift+R  Twin Balls
 *   G  Spin Touch        Shift+G  Golden Spin (hold: the Golden Rectangle)
 *   Z  Scan              Shift+Z  Ball Breaker (hold: needs a Perfect Spin)
 *   X  Spin Leap         Shift+X  Valkyrie
 *   C  Spin Quake        Shift+C  Spin Shield
 *   V  Healing Spin      Shift+V  Face Spin
 * </pre>
 * H stays the power selector and N does nothing for him.
 */
public final class GyroAbilityManager {
	public static final String STEEL_BALL = "steel_ball";
	public static final String TWIN_BALLS = "twin_balls";
	public static final String SPIN_TOUCH = "spin_touch";
	public static final String GOLDEN_SPIN = "golden_spin";
	public static final String SCAN = "scan";
	public static final String BALL_BREAKER = "ball_breaker";
	public static final String SPIN_LEAP = "spin_leap";
	public static final String VALKYRIE = "valkyrie";
	public static final String SPIN_QUAKE = "spin_quake";
	public static final String SPIN_SHIELD = "spin_shield";
	public static final String HEALING_SPIN = "healing_spin";
	public static final String FACE_SPIN = "face_spin";

	/** The move id each held slot's press went to (so its release reaches the same move). */
	private static final Map<UUID, String[]> HELD = new ConcurrentHashMap<>();

	private GyroAbilityManager() {
	}

	static void clearSessionState() {
		HELD.clear();
	}

	static void forget(UUID id) {
		HELD.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Gyro.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	/** The id the slot's current press went to (null when the key is up). For the tests. */
	public static String heldId(ServerPlayer player, AbilitySlot slot) {
		String[] held = HELD.get(player.getUUID());
		return held == null ? null : held[slot.ordinal()];
	}

	static void tick(ServerPlayer player) {
		// nothing per tick: the holds time out in GyroMoves
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		int i = slot.ordinal();
		if (i >= AbilitySlot.CORE_COUNT) {
			return; // H and N are never moves
		}
		String[] held = HELD.computeIfAbsent(player.getUUID(), k -> new String[AbilitySlot.values().length]);
		if (!pressed) {
			String id = held[i];
			held[i] = null;
			if (id != null && isHold(id)) {
				GyroMoves.releaseCharge(player, id);
			}
			return;
		}
		held[i] = null;
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		String id = moveFor(slot, player.isShiftKeyDown());
		if (id == null) {
			return;
		}
		if (isHold(id)) {
			if (GyroMoves.startCharge(player, id)) {
				held[i] = id;
			}
			return;
		}
		GyroMoves.use(player, id);
	}

	/** Shift+G and Shift+Z are holds (the Golden Rectangle). */
	public static boolean isHold(String id) {
		return GOLDEN_SPIN.equals(id) || BALL_BREAKER.equals(id);
	}

	/** The move a key sends. */
	public static String moveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? TWIN_BALLS : STEEL_BALL;
			case SLOT_2 -> shift ? GOLDEN_SPIN : SPIN_TOUCH;
			case SLOT_3 -> shift ? VALKYRIE : SPIN_LEAP;
			case SLOT_4 -> shift ? BALL_BREAKER : SCAN;
			case SLOT_5 -> shift ? FACE_SPIN : HEALING_SPIN;
			case SLOT_6 -> shift ? SPIN_SHIELD : SPIN_QUAKE;
			default -> null;
		};
	}

	/** The HUD's ids per slot: plain, then Shift. */
	public static String[] idsOf(AbilitySlot slot) {
		String a = moveFor(slot, false);
		return a == null ? new String[0] : new String[] { a, moveFor(slot, true) };
	}

	/** How many balls in hand a move needs (it is greyed out with fewer). */
	public static int ballsNeeded(String id) {
		return switch (id) {
			case TWIN_BALLS, SPIN_SHIELD -> 2;
			case SPIN_LEAP, VALKYRIE -> 0;
			default -> 1;
		};
	}

	/** Spin a move costs (the HUD greys a move he can't pay for). */
	public static float cost(String id) {
		return switch (id) {
			case STEEL_BALL -> GyroConfig.BALL_COST;
			case TWIN_BALLS -> GyroConfig.TWIN_COST;
			case SPIN_TOUCH -> GyroConfig.TOUCH_COST;
			case GOLDEN_SPIN -> GyroConfig.GOLDEN_COST;
			case SCAN -> GyroConfig.SCAN_COST;
			case BALL_BREAKER -> GyroConfig.BREAKER_COST;
			case SPIN_LEAP -> GyroConfig.LEAP_COST;
			case SPIN_QUAKE -> GyroConfig.QUAKE_COST;
			case SPIN_SHIELD -> GyroConfig.SHIELD_COST;
			case HEALING_SPIN -> GyroConfig.HEAL_COST;
			case FACE_SPIN -> GyroConfig.FACE_COST;
			default -> 0f;
		};
	}

	/** Usable right now as far as the balls go (the HUD's grey-out). Client-safe. */
	public static boolean ballsAllow(Player player, String id) {
		return Gyro.ballsReady(player) >= ballsNeeded(id);
	}

	/** Ticks left on {@code id}'s cooldown. Client-safe. */
	public static int cooldownRemaining(Player player, String id) {
		if (VALKYRIE.equals(id)) {
			return Math.max(Gyro.valkyrieCooldown(player), Gyro.cooldownRemaining(player, id));
		}
		return Gyro.cooldownRemaining(player, id);
	}

	public static int maxCooldown(Player player, String id) {
		return switch (id) {
			case STEEL_BALL -> GyroConfig.BALL_COOLDOWN;
			case TWIN_BALLS -> GyroConfig.TWIN_COOLDOWN;
			case SPIN_TOUCH -> GyroConfig.TOUCH_COOLDOWN;
			case GOLDEN_SPIN -> GyroConfig.GOLDEN_COOLDOWN;
			case SCAN -> GyroConfig.SCAN_COOLDOWN;
			case BALL_BREAKER -> GyroConfig.BREAKER_COOLDOWN;
			case SPIN_LEAP -> GyroConfig.LEAP_COOLDOWN;
			case VALKYRIE -> Gyro.valkyrieCooldown(player) > 0 ? GyroConfig.VALKYRIE_DEAD_COOLDOWN : GyroConfig.VALKYRIE_WHISTLE_COOLDOWN;
			case SPIN_QUAKE -> GyroConfig.QUAKE_COOLDOWN;
			case SPIN_SHIELD -> GyroConfig.SHIELD_COOLDOWN;
			case HEALING_SPIN -> GyroConfig.HEAL_COOLDOWN;
			case FACE_SPIN -> GyroConfig.FACE_COOLDOWN;
			default -> 1;
		};
	}
}
