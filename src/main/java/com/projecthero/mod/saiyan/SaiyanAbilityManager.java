package com.projecthero.mod.saiyan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.saiyan.moves.SaiyanMoves;
import com.projecthero.mod.saiyan.network.SaiyanActionPayload;

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
 *   X (3)  Ki Dash           Shift+X  Instant Step     (the core: SaiyanFlight / instant_step)
 *   Z (4)  Barrage           Shift+Z  Sky Combo        (barrage / sky_combo)
 *   V (5)  Charge Ki         Shift+V  Power Down       (the core: tap = charge until full / tapped again, hold = while held)
 *   C (6)  Dragon Rush                                 (dragon_rush)
 *   Left click on a creature: the Saiyan combo (combo)   H: the form wheel   Double-tap jump: flight
 * </pre>
 * The moves are {@link SaiyanMoves}'. Pressing any move while charging Ki stops the charge and fires the move.
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
	public static final String CHARGE = "charge";
	/** A V press let go within this many ticks is a tap: the charge keeps going until Ki is full or V is tapped again. */
	public static final int CHARGE_TAP_TICKS = 6;

	/** Players charging from a tap of V (not held), and when V went down. */
	private static final Map<UUID, Boolean> TAP_CHARGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> CHARGE_PRESSED = new ConcurrentHashMap<>();

	/** The move id each held slot's press went to (so its release reaches the same move). */
	private static final Map<UUID, String[]> HELD = new ConcurrentHashMap<>();

	private SaiyanAbilityManager() {
	}

	static void clearSessionState() {
		HELD.clear();
		TAP_CHARGE.clear();
		CHARGE_PRESSED.clear();
	}

	static void forget(UUID id) {
		HELD.remove(id);
		TAP_CHARGE.remove(id);
		CHARGE_PRESSED.remove(id);
	}

	/** True while a tap of V keeps the charge going on its own. */
	public static boolean tapCharging(Player player) {
		return TAP_CHARGE.getOrDefault(player.getUUID(), false);
	}

	/** Per tick for every Saiyan: a tapped charge ends when Ki is full; a charge that stopped elsewhere forgets its tap. */
	static void tick(ServerPlayer player) {
		if (!TAP_CHARGE.containsKey(player.getUUID())) {
			return;
		}
		if (!Saiyan.charging(player)) {
			TAP_CHARGE.remove(player.getUUID());
		} else if (Saiyan.ki(player) >= SaiyanConfig.KI_MAX) {
			stopCharge(player);
		}
	}

	private static void stopCharge(ServerPlayer player) {
		TAP_CHARGE.remove(player.getUUID());
		CHARGE_PRESSED.remove(player.getUUID());
		Saiyan.setCharging(player, false);
	}

	/** V (not sneaking): a press starts the charge, or ends a tapped one. */
	private static void chargePress(ServerPlayer player, String[] held, int i) {
		if (Saiyan.charging(player)) {
			stopCharge(player);
			held[i] = null; // this press only stopped it
			return;
		}
		if (com.projecthero.mod.hero.PowerSuppression.refuse(player, SaiyanActionPayload.Action.CHARGE_START)) {
			return;
		}
		Saiyan.setCharging(player, true);
		if (Saiyan.charging(player)) {
			CHARGE_PRESSED.put(player.getUUID(), player.level().getGameTime());
			held[i] = CHARGE;
		}
	}

	/** V let go: a quick tap keeps charging (until full or tapped again); a hold stops here. */
	private static void chargeRelease(ServerPlayer player) {
		Long at = CHARGE_PRESSED.remove(player.getUUID());
		if (!Saiyan.charging(player)) {
			return;
		}
		if (at != null && player.level().getGameTime() - at <= CHARGE_TAP_TICKS && Saiyan.ki(player) < SaiyanConfig.KI_MAX) {
			TAP_CHARGE.put(player.getUUID(), true);
		} else {
			stopCharge(player);
		}
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
			if (CHARGE.equals(id)) {
				chargeRelease(player);
			} else if (id != null) {
				SaiyanMoves.press(player, id, false);
			}
			return;
		}
		boolean shift = player.isShiftKeyDown();
		if (slot == AbilitySlot.SLOT_5) {
			if (shift) {
				Saiyan.powerDown(player);
			} else {
				chargePress(player, held, i);
			}
			return;
		}
		if (Saiyan.charging(player) && (slot == AbilitySlot.SLOT_3 || moveFor(slot, shift) != null)) {
			stopCharge(player); // a move breaks off the charge
		}
		if (slot == AbilitySlot.SLOT_3 && !shift) {
			SaiyanFlight.dash(player);
			return;
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
			case SLOT_3 -> shift ? INSTANT_STEP : null;
			case SLOT_4 -> shift ? SKY_COMBO : BARRAGE;
			case SLOT_6 -> DRAGON_RUSH;
			default -> null;
		};
	}

	/** The HUD's ids per slot: plain, then Shift. */
	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { FEINT };
			case SLOT_2 -> new String[] { KI_BEAM, SPIRIT_ORB };
			case SLOT_3 -> new String[] { SaiyanFlight.DASH, INSTANT_STEP };
			case SLOT_4 -> new String[] { BARRAGE, SKY_COMBO };
			case SLOT_5 -> new String[] { CHARGE, POWER_DOWN };
			case SLOT_6 -> new String[] { DRAGON_RUSH };
			default -> new String[0];
		};
	}

	/** Ticks left on {@code id}'s cooldown, the core's or the moves'. Client-safe. */
	public static int cooldownRemaining(Player player, String id) {
		return switch (id) {
			case "ki_dash", "flight" -> Saiyan.cooldownRemaining(player, id);
			case POWER_DOWN, CHARGE -> 0;
			default -> SaiyanMoves.cooldownRemaining(player, id);
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case "ki_dash" -> SaiyanConfig.DASH_COOLDOWN;
			case "flight" -> SaiyanConfig.FLIGHT_TOGGLE_COOLDOWN;
			case POWER_DOWN, CHARGE -> 0;
			default -> SaiyanMoves.maxCooldown(id);
		};
	}
}
