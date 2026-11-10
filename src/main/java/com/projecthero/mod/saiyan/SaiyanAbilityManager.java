package com.projecthero.mod.saiyan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.saiyan.moves.SaiyanMoves;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Bridges the universal ability slots to the Saiyan (v0.17.3). {@link com.projecthero.mod.hero.AbilityRouter} hands him the
 * slots when {@link #hasContext} is true. Shift is read server-side; a key's release goes to the same move its press did.
 *
 * <pre>
 *   R (1)  Feint Strike                         (feint)
 *   G (2)  Ki Beam           Shift+G  Spirit Orb       (ki_beam / spirit_orb)
 *   X (3)  Ki Dash           Shift+X  Flight           (the core: SaiyanFlight)
 *   Z (4)  Barrage           Shift+Z  Sky Combo        (barrage / sky_combo)
 *   V (5)  Instant Step      Shift+V  Power Down       (instant_step / the core)
 *   C (6)  Dragon Rush                                 (dragon_rush)
 *   Left click on a creature: the Saiyan combo (combo)   H: the form wheel   Hold Q: charge Ki
 * </pre>
 * The moves are {@link SaiyanMoves}'; nothing fires while charging Ki.
 */
public final class SaiyanAbilityManager {
	public static final String FEINT = "feint";
	public static final String KI_BEAM = "ki_beam";
	public static final String SPIRIT_ORB = "spirit_orb";
	public static final String BARRAGE = "barrage";
	public static final String SKY_COMBO = "sky_combo";
	public static final String DRAGON_RUSH = "dragon_rush";
	public static final String INSTANT_STEP = "instant_step";
	public static final String COMBO = "combo";
	public static final String POWER_DOWN = "power_down";

	/** The move id each held slot's press went to (so its release reaches the same move). */
	private static final Map<UUID, String[]> HELD = new ConcurrentHashMap<>();

	private SaiyanAbilityManager() {
	}

	static void clearSessionState() {
		HELD.clear();
	}

	static void forget(UUID id) {
		HELD.remove(id);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!Saiyan.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	/** May the Saiyan use a move right now (alive, not charging Ki). Says why not. */
	public static boolean canAct(ServerPlayer player) {
		if (!Saiyan.hasPower(player) || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (Saiyan.charging(player)) {
			Saiyan.say(player, "message.projecthero.saiyan.charging", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		String[] held = HELD.computeIfAbsent(player.getUUID(), k -> new String[AbilitySlot.values().length]);
		int i = slot.ordinal();
		if (!pressed) {
			String id = held[i];
			held[i] = null;
			if (id != null) {
				SaiyanMoves.press(player, id, false);
			}
			return;
		}
		boolean shift = player.isShiftKeyDown();
		switch (slot) {
			case SLOT_3 -> {
				if (shift) {
					SaiyanFlight.toggle(player);
				} else {
					SaiyanFlight.dash(player);
				}
				return;
			}
			case SLOT_5 -> {
				if (shift) {
					Saiyan.powerDown(player);
					return;
				}
			}
			default -> {
			}
		}
		String id = moveFor(slot, shift);
		if (id == null || !canAct(player)) {
			return;
		}
		held[i] = id;
		SaiyanMoves.press(player, id, true);
	}

	/** Left click on a creature: the combo's next hit. */
	public static void attack(ServerPlayer player) {
		if (hasContext(player) && player.isAlive() && !Saiyan.charging(player)) {
			SaiyanMoves.press(player, COMBO, true);
		}
	}

	private static String moveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> FEINT;
			case SLOT_2 -> shift ? SPIRIT_ORB : KI_BEAM;
			case SLOT_4 -> shift ? SKY_COMBO : BARRAGE;
			case SLOT_5 -> INSTANT_STEP;
			case SLOT_6 -> DRAGON_RUSH;
			default -> null;
		};
	}

	/** The HUD's ids per slot: plain, then Shift. */
	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { FEINT };
			case SLOT_2 -> new String[] { KI_BEAM, SPIRIT_ORB };
			case SLOT_3 -> new String[] { SaiyanFlight.DASH, SaiyanFlight.FLIGHT };
			case SLOT_4 -> new String[] { BARRAGE, SKY_COMBO };
			case SLOT_5 -> new String[] { INSTANT_STEP, POWER_DOWN };
			case SLOT_6 -> new String[] { DRAGON_RUSH };
			default -> new String[0];
		};
	}

	/** Ticks left on {@code id}'s cooldown, the core's or the moves'. Client-safe. */
	public static int cooldownRemaining(Player player, String id) {
		return switch (id) {
			case "ki_dash", "flight" -> Saiyan.cooldownRemaining(player, id);
			case POWER_DOWN -> 0;
			default -> SaiyanMoves.cooldownRemaining(player, id);
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case "ki_dash" -> SaiyanConfig.DASH_COOLDOWN;
			case "flight" -> SaiyanConfig.FLIGHT_TOGGLE_COOLDOWN;
			case POWER_DOWN -> 0;
			default -> SaiyanMoves.maxCooldown(id);
		};
	}
}
