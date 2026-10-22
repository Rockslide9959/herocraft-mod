package com.projecthero.mod.titanshifter;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the Titan kits and runs the power's per-player server tick.
 * {@link com.projecthero.mod.hero.AbilityRouter} hands the Titan Shifter the slots the whole time they are inside a Titan
 * form (whatever other powers they hold), and -- v0.16.7 -- in human form too (Partial Shifting), unless a mutation is
 * selected on the power wheel. Every key has a tap and a Shift move; the table is {@link TitanMove#of}:
 *
 * <pre>
 *   R (1)  combo (Punch / Jab / Swipe)          Shift: Kick / Elbow / Roundhouse / Pitcher's Fastball
 *   G (2)  Heavy Smash / Axe Kick                Shift: Uppercut / Ground Pound / Sweep Kick / Rock Scatter
 *   X (3)  Titan Leap                            Shift: the SIGNATURE -- Rage / Shoulder Charge / Crystal Fists / Boulder Throw
 *   Z (4)  Titan Stomp                           Shift: Dropkick / Brace / Scream / Boulder Volley
 *   V (5)  Titan Roar                            Shift: Nape Guard
 *   C (6)  Titan Regeneration                    Shift: Titan Hardening
 *   N      grab / bite (Shift+N: set down)       H: Titan Shift -- transform / revert (TitanShifter#requestToggle)
 *   human: R Titan Arm (Shift: Crystal Blade), G Steam Burst (Shift: Bite Shift), X Crystal Wall (Shift: Crystal Cocoon)
 * </pre>
 */
public final class TitanShifterAbilityManager {
	private TitanShifterAbilityManager() {
	}

	public static void clearSessionState() {
		TitanShifter.clearSessionState();
		TitanAbilities.clearSessionState();
		TitanBoulders.clearSessionState();
		com.projecthero.mod.titanshifter.entity.TitanLimbChunkEntity.clearSessionState(); // v0.16.7 (user, Titan fixes)
		TitanHumanKit.clearSessionState();
	}

	/** True while the player is inside an active Titan (the abilities are usable). */
	public static boolean hasContext(ServerPlayer player) {
		return TitanShifter.inTitan(player) && TitanShifter.formOf(player) != null;
	}

	/** v0.16.7: a shifter in human form with no mutation selected -- the R / G / X keys are Partial Shifting. */
	public static boolean hasHumanContext(ServerPlayer player) {
		if (!TitanShifter.isShifter(player) || TitanShifter.phase(player) != TitanPhase.HUMAN) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!pressed) {
			return;
		}
		TitanKit.handle(player, slot.ordinal() + 1, player.isShiftKeyDown());
	}

	public static void handleHuman(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!pressed) {
			return;
		}
		TitanHumanKit.handle(player, slot.ordinal() + 1, player.isShiftKeyDown());
	}

	/** Runs for every player every server tick. */
	public static void serverTick(ServerPlayer player) {
		TitanShifter.tick(player);
	}

	/** Cooldown-map id for a slot (the tapped move of the Attack Titan's kit). */
	public static String abilityIdOf(AbilitySlot slot) {
		TitanMove m = TitanMove.of(TitanType.GENERIC_TITAN, Math.min(6, slot.ordinal() + 1), false);
		return m == null ? TitanMove.REGEN.id : m.id;
	}

	public static int maxCooldown(String abilityId) {
		TitanMove m = TitanMove.byId(abilityId);
		return m == null ? 0 : Math.max(0, m.cooldown());
	}
}
