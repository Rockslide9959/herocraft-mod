package com.herocraft.mod.maxsteel;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.hero.AbilitySlot;
import com.herocraft.mod.hero.data.ExperimentalState;
import com.herocraft.mod.maxsteel.data.MaxSteelState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to Max Steel's kit, and runs the power's per-player server
 * tick.
 *
 * <p>{@link com.herocraft.mod.hero.AbilityRouter} hands Max Steel the slots when {@link #hasContext}
 * is true -- the player has the power and has not deliberately selected an experimental mutation.
 * Sits after Thor, Iron Man and Spider-Man in the router priority.
 *
 * <p>Slot mapping (the mod's existing Ability 1-6 keys, in spec order):
 * <pre>
 *   R (slot 1)  Turbo Blast / transform     G (slot 2)  Turbo Strength
 *   X (slot 3)  Turbo Speed                 Z (slot 4)  Turbo Flight
 *   V (slot 5)  Turbo Stealth               C (slot 6)  Turbo Cannon
 * </pre>
 */
public final class MaxSteelAbilityManager {
	/** Ability-1 press game-time per player (hold-to-transform / charged-blast gesture). */
	private static final java.util.Map<java.util.UUID, Long> ABILITY1_PRESSED = new java.util.concurrent.ConcurrentHashMap<>();
	/** Ability-6 press game-time per player (Turbo Cannon charge). */
	private static final java.util.Map<java.util.UUID, Long> ABILITY6_PRESSED = new java.util.concurrent.ConcurrentHashMap<>();

	private MaxSteelAbilityManager() {
	}

	public static void clearSessionState() {
		ABILITY1_PRESSED.clear();
		ABILITY6_PRESSED.clear();
	}

	/** Per-player scratch cleanup -- called from {@link MaxSteel#clearTransient} on death/logout/etc. */
	public static void onCleanup(java.util.UUID playerId) {
		ABILITY1_PRESSED.remove(playerId);
		ABILITY6_PRESSED.remove(playerId);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!MaxSteel.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		switch (slot) {
			case SLOT_1 -> handleAbilityOne(player, pressed);
			case SLOT_2 -> {
				if (pressed) {
					requireSuit(player, MaxSteelMode.STRENGTH, () -> {
						if (MaxSteel.mode(player) == MaxSteelMode.STRENGTH) {
							if (player.isShiftKeyDown()) {
								MaxSteelModes.exitToBase(player, true); // Shift+G: back to Base Turbo Mode
							} else {
								MaxSteelStrength.slam(player); // G again: Turbo Slam
							}
						} else {
							MaxSteelModes.toggle(player, MaxSteelMode.STRENGTH, MaxSteelConfig.STRENGTH_ACTIVATION_COST);
						}
					});
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					requireSuit(player, MaxSteelMode.SPEED, () -> {
						if (MaxSteel.mode(player) == MaxSteelMode.SPEED) {
							if (player.isShiftKeyDown()) {
								MaxSteelModes.exitToBase(player, true); // Shift+X: back to Base Turbo Mode
							} else {
								MaxSteelSpeed.dash(player);
							}
						} else {
							MaxSteelModes.toggle(player, MaxSteelMode.SPEED, MaxSteelConfig.SPEED_ACTIVATION_COST);
						}
					});
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					requireSuit(player, MaxSteelMode.FLIGHT, () -> MaxSteelModes.toggle(player,
							MaxSteelMode.FLIGHT, MaxSteelConfig.FLIGHT_ACTIVATION_COST));
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					requireSuit(player, MaxSteelMode.STEALTH, () -> MaxSteelModes.toggle(player,
							MaxSteelMode.STEALTH, MaxSteelConfig.STEALTH_ACTIVATION_COST));
				}
			}
			case SLOT_6 -> handleAbilitySix(player, pressed);
		}
	}

	/**
	 * Run {@code action} if suited; if unsuited, armour up straight into {@code mode} (v0.6.17) so the
	 * key does what it says instead of just standing the player up in Base.
	 */
	private static void requireSuit(ServerPlayer player, MaxSteelMode mode, Runnable action) {
		MaxSteelState s = MaxSteel.state(player);
		if (!s.transformed && !MaxSteelTransform.isAnimating(s)) {
			MaxSteelTransform.beginSuitUpIntoMode(player, mode);
			return;
		}
		if (s.transformDir != MaxSteelState.DIR_IDLE) {
			return; // mid-animation
		}
		action.run();
	}

	// ---------------- Ability 1 (R): transform / suit-down / Turbo Blast ----------------

	private static void handleAbilityOne(ServerPlayer player, boolean pressed) {
		long now = player.level().getGameTime();
		if (pressed) {
			ABILITY1_PRESSED.put(player.getUUID(), now);
			return;
		}
		Long since = ABILITY1_PRESSED.remove(player.getUUID());
		if (since == null) {
			return;
		}
		long held = now - since;
		MaxSteelState s = MaxSteel.state(player);

		if (!s.transformed && !MaxSteelTransform.isAnimating(s)) {
			MaxSteelTransform.beginSuitUp(player, false);
			return;
		}
		if (s.transformDir != MaxSteelState.DIR_IDLE) {
			return;
		}

		// v0.6.21: holding Ability 1 no longer suits the player down -- that gesture fought with the
		// charged Turbo Blast and surprised players mid-fight. Power down is Shift+H / the N key only.
		if (held < 7) {
			MaxSteelBlast.tap(player);
		} else {
			float frac = Math.min(1f, (float) held / MaxSteelConfig.BLAST_MAX_CHARGE_TICKS);
			MaxSteelBlast.charged(player, frac);
		}
	}

	// ---------------- Ability 6 (C): Turbo Cannon (charge + release) ----------------

	private static void handleAbilitySix(ServerPlayer player, boolean pressed) {
		MaxSteelState s = MaxSteel.state(player);
		if (!s.transformed && !MaxSteelTransform.isAnimating(s)) {
			if (pressed) {
				MaxSteelTransform.beginSuitUp(player, false);
			}
			return;
		}
		if (s.transformDir != MaxSteelState.DIR_IDLE) {
			return;
		}
		if (pressed) {
			if (MaxSteelCannon.beginCharge(player)) {
				ABILITY6_PRESSED.put(player.getUUID(), player.level().getGameTime());
			}
			return;
		}
		Long since = ABILITY6_PRESSED.remove(player.getUUID());
		if (since == null) {
			return;
		}
		long held = player.level().getGameTime() - since;
		float frac = Math.min(1f, (float) held / MaxSteelConfig.CANNON_MAX_CHARGE_TICKS);
		MaxSteelCannon.release(player, frac);
	}

	// ---------------- per-player server tick ----------------

	public static void serverTick(ServerPlayer player) {
		if (!MaxSteel.hasPower(player)) {
			return;
		}
		MaxSteelTransform.tick(player);
		MaxSteelState s = MaxSteel.state(player);
		if (!s.transformed) {
			ABILITY6_PRESSED.remove(player.getUUID());
			return;
		}
		MaxSteelSuitArmor.reequipMissing(player);
		MaxSteelSuitArmor.deleteLoose(player);
		MaxSteelModeRuntime.tick(player);
		MaxSteelStealth.tick(player);
		MaxSteelCannon.tick(player);
		boolean modeDraining = s.modeEnum().isSpecialised();
		MaxSteelEnergy.tickRegen(player, modeDraining, MaxSteelCannon.isCharging(player));
		if (player.tickCount % 40 == 0) {
			MaxSteelAttributes.reconcile(player);
		}
		MaxSteelSpeed.tickFx(player);
		MaxSteelPassives.tick(player);
		MaxSteelSense.tick(player);
	}

	public static String abilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> "turbo_blast";
			case SLOT_2 -> "turbo_strength";
			case SLOT_3 -> "turbo_speed";
			case SLOT_4 -> "turbo_flight";
			case SLOT_5 -> "turbo_stealth";
			case SLOT_6 -> "turbo_cannon";
		};
	}
}
