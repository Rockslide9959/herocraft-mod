package com.projecthero.mod.greenlantern;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to Green Lantern's kit, and runs the power's per-player
 * server tick. Hand-rolled {@code hasContext}/{@code handle(slot)} switch, exactly like
 * {@code MaxSteelAbilityManager} -- Hero-Tier powers do not use the generic {@code AbilityHandler}
 * framework (that is reserved for the 27 experimental powers).
 *
 * <p><b>Slot mapping</b> (adapted from the build brief's default R/G/H/Z/X/C onto this mod's actual six
 * universal slots -- R/G/X/Z/V/C; {@code H} is reserved mod-wide for the experimental power-select
 * wheel and is not one of the six ability slots, so it is not available to a Hero-Tier power):
 * <pre>
 *   R (slot 1)  Ring Bolt / Shift+R Continuous Beam
 *   G (slot 2)  Construct Fist / Shift+G War Hammer Slam
 *   X (slot 3)  Flight Toggle (boost is automatic while sprint-holding, read live each tick)
 *   Z (slot 4)  Directional Shield (held) / Shift+Z Protective Dome
 *   V (slot 5)  Suit Up/Down / Shift+V Ring Scan
 *   C (slot 6)  tap: deploy selected construct; hold >=0.35s (release to confirm): cycle selection;
 *               Shift+C: dismiss all owned constructs
 * </pre>
 */
public final class GreenLanternAbilityManager {
	/** Ability-6 (C) press game-time per player -- tap-vs-hold gesture for deploy/cycle-select. */
	private static final Map<UUID, Long> ABILITY6_PRESSED = new ConcurrentHashMap<>();
	/** Ring Charge as of the last once-per-second check -- lets low-charge warnings fire on a real
	 *  crossing instead of a fabricated "value + one second of regen" estimate. */
	private static final Map<UUID, Float> LAST_CHARGE_CHECK = new ConcurrentHashMap<>();

	private GreenLanternAbilityManager() {
	}

	public static void clearSessionState() {
		ABILITY6_PRESSED.clear();
		LAST_CHARGE_CHECK.clear();
	}

	public static void onCleanup(UUID playerId) {
		ABILITY6_PRESSED.remove(playerId);
		LAST_CHARGE_CHECK.remove(playerId);
		GreenLanternCombat.onCleanup(playerId);
	}

	public static boolean hasContext(ServerPlayer player) {
		if (!GreenLantern.hasPower(player)) {
			return false;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null || st.activePower.isEmpty();
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		// The ring's powers work whether or not the suit is on -- only the suit's own armour/Emergency
		// Catch passives (GreenLanternDamage) require actually wearing it. Charge cost and cooldowns
		// remain the real gate on every ability below; nothing here becomes free by being unsuited.
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					if (player.isShiftKeyDown()) {
						GreenLanternCombat.beamStart(player);
					} else {
						GreenLanternCombat.ringBolt(player);
					}
				} else if (GreenLanternCombat.isChannellingBeam(player)) {
					GreenLanternCombat.beamStop(player);
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (player.isShiftKeyDown()) {
						GreenLanternCombat.warHammerSlam(player);
					} else {
						GreenLanternCombat.constructFist(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					toggleFlight(player);
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (player.isShiftKeyDown()) {
						// v0.11.2: Shift+Z dismisses an already-up dome instead of trying (and failing) to
						// deploy a second one -- lets the player drop it on their own timing rather than
						// only ever losing it to a break or the max-duration timeout.
						if (GreenLanternShield.isActive(player) && GreenLanternShield.isDome(player)) {
							GreenLanternShield.dismissDomeVoluntarily(player);
						} else {
							GreenLanternShield.deployDome(player);
						}
					} else {
						GreenLanternShield.startShield(player);
					}
				} else {
					GreenLanternShield.stopShield(player);
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (player.isShiftKeyDown()) {
						GreenLanternScan.scan(player);
					} else {
						GreenLanternSuit.toggle(player);
					}
				}
			}
			case SLOT_6 -> handleAbilitySix(player, pressed);
		}
	}

	private static void toggleFlight(ServerPlayer player) {
		if (GreenLanternFlight.isFlying(player)) {
			GreenLanternFlight.forceStop(player, false);
			return;
		}
		if (!GreenLanternEnergy.canSpend(player, GreenLanternConfig.FLIGHT_HOVER_COST_PER_SEC / 20f)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternEnergy.markAbilityUsed(player);
		GreenLanternBattery.onAbilityUsed(player);
		GreenLanternFlight.onEnter(player);
	}

	// ---------------- Ability 6 (C): deploy / hold-for-wheel / dismiss ----------------

	/**
	 * A tap (release before {@link GreenLanternConfig#CONSTRUCT_WHEEL_HOLD_TICKS}) deploys the currently
	 * selected construct. A hold that reaches the threshold is the construct-wheel gesture (v0.11.2):
	 * the client (see {@code ProjectHeroModClient}'s ability-key handling) opens
	 * {@code GreenLanternConstructWheelScreen} itself once it crosses that same threshold and, because
	 * opening any {@link net.minecraft.client.gui.screens.Screen} makes the client release every held
	 * ability key, the release this method sees for a long hold is intentionally a no-op here -- the
	 * player's actual pick arrives separately via {@link #selectConstruct}.
	 */
	private static void handleAbilitySix(ServerPlayer player, boolean pressed) {
		if (pressed) {
			if (player.isShiftKeyDown()) {
				GreenLanternConstructs.dismissRequested(player);
				return;
			}
			ABILITY6_PRESSED.put(player.getUUID(), player.level().getGameTime());
			return;
		}
		Long since = ABILITY6_PRESSED.remove(player.getUUID());
		if (since == null) {
			return;
		}
		long held = player.level().getGameTime() - since;
		if (held < GreenLanternConfig.CONSTRUCT_WHEEL_HOLD_TICKS) {
			GreenLanternConstructs.deploy(player, GreenLantern.state(player).selectedConstructType());
		}
	}

	/**
	 * Server-side landing spot for {@link com.projecthero.mod.network.GreenLanternConstructSelectPayload}
	 * (the construct wheel). Re-validates the ordinal and Mastery unlock -- the client only ever shows
	 * unlocked wedges, but the payload is not trusted just because the screen was built correctly.
	 */
	public static void selectConstruct(ServerPlayer player, int ordinal) {
		if (!hasContext(player) || ordinal < 0 || ordinal >= ConstructType.values().length) {
			return;
		}
		GreenLanternState s = GreenLantern.state(player);
		ConstructType type = ConstructType.values()[ordinal];
		if (!type.unlockedFor(s)) {
			return;
		}
		applySelection(player, ordinal);
	}

	private static void applySelection(ServerPlayer player, int ordinal) {
		GreenLanternState c = GreenLantern.state(player).copy();
		c.selectedConstruct = ordinal;
		GreenLantern.save(player, c);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.construct_selected",
				Component.translatable(ConstructType.byOrdinal(ordinal).translationKey())), true);
	}

	// ---------------- per-player server tick ----------------

	public static void serverTick(ServerPlayer player) {
		if (!GreenLantern.hasPower(player)) {
			return;
		}
		GreenLanternSuit.tick(player);
		GreenLanternMastery.tickNightWatch(player);
		GreenLanternBattery.tick(player);

		if (GreenLantern.isSuited(player)) {
			com.projecthero.mod.greenlantern.item.GreenLanternSuitArmor.reequipMissing(player);
			com.projecthero.mod.greenlantern.item.GreenLanternSuitArmor.deleteLoose(player);
		}

		boolean beamChannelling = GreenLanternCombat.isChannellingBeam(player);
		if (beamChannelling) {
			GreenLanternCombat.beamTick(player);
		}
		GreenLanternShield.tickShieldUpkeep(player);
		GreenLanternShield.tickDomeUpkeep(player);

		if (GreenLanternFlight.isFlying(player)) {
			boolean boosting = player.isShiftKeyDown() && player.isSprinting();
			float drain = GreenLanternFlight.tick(player, boosting);
			if (!GreenLanternEnergy.drainTick(player, drain)) {
				emergencyDescend(player);
			}
		}

		GreenLanternEnergy.tickRegen(player, beamChannelling || GreenLanternBattery.isChannelling(player));

		if (player.tickCount % 20 == 0) {
			float now = GreenLanternEnergy.get(player);
			Float previous = LAST_CHARGE_CHECK.put(player.getUUID(), now);
			if (previous != null) {
				GreenLanternEnergy.triggerLowChargeFeedback(player, previous, now);
			}
		}
	}

	private static void emergencyDescend(ServerPlayer player) {
		if (GreenLanternEnergy.get(player) > 0f) {
			GreenLanternEnergy.spendEmergency(player, Math.min(GreenLanternConfig.EMERGENCY_RESERVE, 10f));
		}
		GreenLanternFlight.forceStop(player, true);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.ring_depleted"), true);
	}
}
