package com.projecthero.mod.titanshifter;

import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the Titan kit and runs the power's per-player server tick.
 * {@link com.projecthero.mod.hero.AbilityRouter} hands the Titan Shifter the slots the whole time they are
 * inside a Titan form (whatever other powers they hold) -- Ability 1-6 keep their normal keys:
 *
 * <pre>
 *   R (1)  Titan Punch  (Shift+R = Titan Kick, third swing of a combo = Heavy Punch)
 *   G (2)  Heavy Smash
 *   X (3)  Titan Leap   (Shift+X = Titan Roar)
 *   Z (4)  Titan Stomp
 *   V (5)  Titan Roar
 *   C (6)  Titan Regeneration  (Shift+C = Titan Hardening)
 *   H      Titan Shift -- transform / revert (handled by {@link TitanShifter#requestToggle}, not a slot)
 * </pre>
 */
public final class TitanShifterAbilityManager {
	private TitanShifterAbilityManager() {
	}

	public static void clearSessionState() {
		TitanShifter.clearSessionState();
		TitanAbilities.clearSessionState();
	}

	/** True while the player is inside an active Titan (the abilities are usable). */
	public static boolean hasContext(ServerPlayer player) {
		return TitanShifter.inTitan(player) && TitanShifter.formOf(player) != null;
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!pressed) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> TitanAbilities.punch(player);
			case SLOT_2 -> TitanAbilities.heavySmash(player);
			case SLOT_3 -> {
				if (player.isShiftKeyDown()) {
					TitanAbilities.roar(player);
				} else {
					TitanAbilities.leap(player);
				}
			}
			case SLOT_4 -> TitanAbilities.stomp(player);
			case SLOT_5 -> TitanAbilities.roar(player);
			case SLOT_6 -> {
				if (player.isShiftKeyDown()) {
					TitanAbilities.hardening(player);
				} else {
					TitanAbilities.regeneration(player);
				}
			}
		}
	}

	/** Runs for every player every server tick. */
	public static void serverTick(ServerPlayer player) {
		TitanShifter.tick(player);
	}

	/** Cooldown-map id for a slot -- the HUD reads it client-side from the synced state. */
	public static String abilityIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> TitanAbilities.PUNCH;
			case SLOT_2 -> TitanAbilities.SMASH;
			case SLOT_3 -> TitanAbilities.LEAP;
			case SLOT_4 -> TitanAbilities.STOMP;
			case SLOT_5 -> TitanAbilities.ROAR;
			case SLOT_6 -> TitanAbilities.REGEN;
		};
	}

	public static int maxCooldown(String abilityId) {
		var a = TitanShifterConfig.abilities();
		return switch (abilityId) {
			case TitanAbilities.PUNCH -> a.punchCooldown;
			case TitanAbilities.SMASH -> a.smashCooldown;
			case TitanAbilities.STOMP -> a.stompCooldown;
			case TitanAbilities.LEAP -> a.leapCooldown;
			case TitanAbilities.ROAR -> a.roarCooldown;
			case TitanAbilities.REGEN -> a.regenCooldown;
			case TitanAbilities.HARDEN -> a.hardenCooldown;
			default -> 0;
		};
	}
}
