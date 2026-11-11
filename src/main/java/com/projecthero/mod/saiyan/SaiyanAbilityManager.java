package com.projecthero.mod.saiyan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.saiyan.data.SaiyanState;
import com.projecthero.mod.saiyan.kit.SaiyanKit;
import com.projecthero.mod.saiyan.network.SaiyanActionPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Saiyan revamp: bridges the universal ability slots to the Saiyan. {@link com.projecthero.mod.hero.AbilityRouter} hands
 * him the slots when {@link #hasContext} is true. Shift is read server-side; a key's release goes to the same move its press did.
 *
 * <pre>
 *   R  combo         Shift+R  martial
 *   G  ki_blast      Shift+G  beam
 *   Z  strong        Shift+Z  spirit_bomb
 *   X  dash          Shift+X  rush
 *   C  aoe           Shift+C  aoe2
 *   V  Charge Ki (the core: a tap charges until full / tapped again, a hold charges while held)
 *      Shift+V tap: utility2      Shift + hold V 3 s: Kaioken on / off (the core)
 *   H  Super Saiyan at full Rage (the client's H, {@code SaiyanActionPayload.TRANSFORM})    Double-tap jump: flight
 * </pre>
 * Every move is {@link SaiyanKit}'s. Pressing any move while charging Ki stops the charge. While {@link SaiyanKit#busy} no new
 * press goes anywhere (releases still reach the kit).
 */
public final class SaiyanAbilityManager {
	public static final String CHARGE = "charge";
	public static final String KAIOKEN = "kaioken";
	/** Held-slot markers for Shift+V: the wind-up is running / it already toggled Kaioken. */
	private static final String KAIOKEN_HOLD = "kaioken_hold";
	private static final String KAIOKEN_DONE = "kaioken_done";

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

	public static boolean hasContext(ServerPlayer player) {
		if (!Saiyan.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	/** True while a tap of V keeps the charge going on its own. */
	public static boolean tapCharging(Player player) {
		return TAP_CHARGE.getOrDefault(player.getUUID(), false);
	}

	/** The id the slot's current press went to (null when the key is up or the press went nowhere). For the tests. */
	public static String heldId(ServerPlayer player, AbilitySlot slot) {
		String[] held = HELD.get(player.getUUID());
		return held == null ? null : held[slot.ordinal()];
	}

	/** May the Saiyan start a move right now (alive, not held by a move). */
	public static boolean canAct(ServerPlayer player) {
		return Saiyan.hasPower(player) && player.isAlive() && !player.isSpectator() && !SaiyanKit.busy(player);
	}

	/** Per tick: a tapped charge ends when Ki is full; the Kaioken wind-up completes after 3 s. */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		if (TAP_CHARGE.containsKey(id)) {
			if (!Saiyan.charging(player)) {
				TAP_CHARGE.remove(id);
			} else if (Saiyan.ki(player) >= SaiyanConfig.KI_MAX) {
				stopCharge(player);
			}
		}
		SaiyanState s = Saiyan.state(player);
		if (s.kaiokenHoldAt > 0L && player.level().getGameTime() - s.kaiokenHoldAt >= SaiyanConfig.KAIOKEN_WINDUP) {
			setHold(player, 0L);
			String[] held = HELD.get(id);
			if (held != null && KAIOKEN_HOLD.equals(held[AbilitySlot.SLOT_5.ordinal()])) {
				held[AbilitySlot.SLOT_5.ordinal()] = KAIOKEN_DONE;
			}
			if (Saiyan.kaioken(player)) {
				Saiyan.stopKaioken(player, true);
			} else {
				Saiyan.startKaioken(player);
			}
		}
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
			if (CHARGE.equals(id)) {
				chargeRelease(player);
			} else if (KAIOKEN_HOLD.equals(id)) {
				kaiokenRelease(player);
			} else if (id != null && !KAIOKEN_DONE.equals(id)) {
				SaiyanKit.press(player, id, false);
			}
			return;
		}
		held[i] = null;
		if (!canAct(player)) {
			return;
		}
		boolean shift = player.isShiftKeyDown();
		if (slot == AbilitySlot.SLOT_5) {
			if (shift) {
				kaiokenPress(player, held, i);
			} else {
				chargePress(player, held, i);
			}
			return;
		}
		String id = moveFor(slot, shift);
		if (id == null) {
			return;
		}
		if (Saiyan.charging(player)) {
			stopCharge(player); // a move breaks off the charge
		}
		held[i] = id;
		SaiyanKit.press(player, id, true);
	}

	/** Left click on a creature: the kit's melee hook. */
	public static void attack(ServerPlayer player) {
		if (hasContext(player) && player.isAlive() && !Saiyan.charging(player)) {
			SaiyanKit.attack(player);
		}
	}

	/** The kit id a key sends ({@code null} for V, which is the core's). */
	public static String moveFor(AbilitySlot slot, boolean shift) {
		return switch (slot) {
			case SLOT_1 -> shift ? SaiyanKit.MARTIAL : SaiyanKit.COMBO;
			case SLOT_2 -> shift ? SaiyanKit.BEAM : SaiyanKit.KI_BLAST;
			case SLOT_3 -> shift ? SaiyanKit.RUSH : SaiyanKit.DASH;
			case SLOT_4 -> shift ? SaiyanKit.SPIRIT_BOMB : SaiyanKit.STRONG;
			case SLOT_6 -> shift ? SaiyanKit.AOE2 : SaiyanKit.AOE;
			default -> null;
		};
	}

	/** The HUD's ids per slot: plain, then Shift. */
	public static String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { SaiyanKit.COMBO, SaiyanKit.MARTIAL };
			case SLOT_2 -> new String[] { SaiyanKit.KI_BLAST, SaiyanKit.BEAM };
			case SLOT_3 -> new String[] { SaiyanKit.DASH, SaiyanKit.RUSH };
			case SLOT_4 -> new String[] { SaiyanKit.STRONG, SaiyanKit.SPIRIT_BOMB };
			case SLOT_5 -> new String[] { CHARGE, SaiyanKit.UTILITY2 };
			case SLOT_6 -> new String[] { SaiyanKit.AOE, SaiyanKit.AOE2 };
			default -> new String[0];
		};
	}

	/** Ticks left on {@code id}'s cooldown. Client-safe. */
	public static int cooldownRemaining(Player player, String id) {
		return switch (id) {
			case CHARGE -> 0;
			case KAIOKEN -> Saiyan.kaiokenCooldown(player);
			default -> SaiyanKit.cooldownRemaining(player, id);
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case CHARGE -> 1;
			case KAIOKEN -> SaiyanConfig.KAIOKEN_COOLDOWN;
			default -> Math.max(1, SaiyanKit.maxCooldown(id));
		};
	}

	// ---------------------------------------------------------------- V: charge Ki

	private static void stopCharge(ServerPlayer player) {
		TAP_CHARGE.remove(player.getUUID());
		CHARGE_PRESSED.remove(player.getUUID());
		Saiyan.setCharging(player, false);
	}

	/** V (not sneaking): a press starts the charge, or ends a tapped one. */
	private static void chargePress(ServerPlayer player, String[] held, int i) {
		if (Saiyan.charging(player)) {
			stopCharge(player);
			return; // this press only stopped it
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
		if (at != null && player.level().getGameTime() - at <= SaiyanConfig.TAP_TICKS && Saiyan.ki(player) < SaiyanConfig.KI_MAX) {
			TAP_CHARGE.put(player.getUUID(), true);
		} else {
			stopCharge(player);
		}
	}

	// ---------------------------------------------------------------- Shift+V: utility2 (tap) / Kaioken (hold 3 s)

	private static void setHold(ServerPlayer player, long at) {
		SaiyanState n = Saiyan.state(player).copy();
		n.kaiokenHoldAt = at;
		Saiyan.save(player, n);
	}

	private static void kaiokenPress(ServerPlayer player, String[] held, int i) {
		if (Saiyan.charging(player)) {
			stopCharge(player);
		}
		setHold(player, player.level().getGameTime());
		held[i] = KAIOKEN_HOLD;
	}

	/** Shift+V let go before the 3 s: a tap is utility2 (or ends a running Kaioken); a longer hold just cancels the wind-up. */
	private static void kaiokenRelease(ServerPlayer player) {
		long at = Saiyan.state(player).kaiokenHoldAt;
		setHold(player, 0L);
		if (at <= 0L) {
			return;
		}
		long d = player.level().getGameTime() - at;
		if (d > SaiyanConfig.TAP_TICKS) {
			return;
		}
		if (Saiyan.kaioken(player)) {
			Saiyan.stopKaioken(player, true);
			return;
		}
		if (com.projecthero.mod.hero.PowerSuppression.refuse(player)) {
			return;
		}
		SaiyanKit.press(player, SaiyanKit.UTILITY2, true);
		SaiyanKit.press(player, SaiyanKit.UTILITY2, false);
	}

}
