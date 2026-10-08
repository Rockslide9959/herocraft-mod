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
 *   (v0.14.3: Shift+X hold = Emerald Gatling, Shift+C = Missile Barrage, H = Giant Hand, N = dismiss constructs,
 *    Shift + hold N 5 s = take the ring off -- see the class javadoc of GreenLanternConstructAttacks)
 *   X (slot 3)  hold: recite the Oath -- "Green Lantern's Light!" empowerment mode (v0.11.7,
 *               replaces Ring Grapple). Ring Flight is a double-tap of the vanilla jump key
 *               (boost is automatic while sprint-holding while flying, read live each tick)
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
	/** v0.14.3: Shift + N press game-time per player, while the ring is being taken off. */
	private static final Map<UUID, Long> RING_REMOVE = new ConcurrentHashMap<>();

	private GreenLanternAbilityManager() {
	}

	public static void clearSessionState() {
		ABILITY6_PRESSED.clear();
		LAST_CHARGE_CHECK.clear();
		RING_REMOVE.clear();
		GreenLanternConstructAttacks.clearSessionState();
	}

	public static void onCleanup(UUID playerId) {
		ABILITY6_PRESSED.remove(playerId);
		LAST_CHARGE_CHECK.remove(playerId);
		RING_REMOVE.remove(playerId);
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
					// v0.14.3: Shift+X (hold) spins up the Emerald Gatling; plain X still recites the Oath
					if (player.isShiftKeyDown()) {
						GreenLanternConstructAttacks.gatlingStart(player);
					} else {
						GreenLanternOath.onPress(player);
					}
				} else {
					GreenLanternConstructAttacks.gatlingStop(player, true);
					GreenLanternOath.onRelease(player);
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
							if (GreenLanternShield.isActive(player) && GreenLanternShield.isDome(player)) {
								GreenLanternVisuals.anim(player, com.projecthero.mod.greenlantern.data.GreenLanternFx.ANIM_DOME);
							}
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
						GreenLanternVisuals.anim(player, com.projecthero.mod.greenlantern.data.GreenLanternFx.ANIM_SCAN);
					} else {
						// v0.15.15: V is the Giant Hand now (it was on H); the suit moved to H
						giantHand(player);
					}
				}
			}
			case SLOT_6 -> handleAbilitySix(player, pressed);
		}
	}

	/**
	 * Toggles Ring Flight. No longer wired to any of the six ability slots (v0.11.5 moved it to a
	 * double-tap of the vanilla jump key, mirroring Thor/Iron Man) -- called from the
	 * {@code GreenLanternActionPayload} network handler instead.
	 */
	public static void toggleFlight(ServerPlayer player) {
		if (!hasContext(player)) {
			return;
		}
		if (GreenLanternFlight.isFlying(player)) {
			GreenLanternFlight.forceStop(player, false);
			return;
		}
		if (!GreenLanternEnergy.canSpend(player, GreenLanternConfig.FLIGHT_COST_PER_SEC / 20f)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternBattery.onAbilityUsed(player);
		GreenLanternFlight.onEnter(player);
	}

	// ---------------- v0.14.3: H / N ----------------

	/** v0.15.15: H -- Suit Up / Suit Down (it was V; the Giant Hand moved to V). */
	public static void suitToggle(ServerPlayer player) {
		if (hasContext(player)) {
			GreenLanternSuit.toggle(player);
		}
	}

	/**
	 * v0.15.15: the Remove Ring button on the Shift+N suit screen (the screen asks for a second click to confirm) --
	 * replaces Shift + hold N. Same result as before: the power goes and the Power Ring comes back as an item.
	 */
	public static void removeRingFromMenu(ServerPlayer player) {
		if (hasContext(player)) {
			RING_REMOVE.remove(player.getUUID());
			GreenLantern.removeRing(player);
		}
	}

	/** V (v0.15.15; H before): the Giant Hand -- grab what you aim at, or hurl what it is holding. */
	public static void giantHand(ServerPlayer player) {
		if (hasContext(player)) {
			GreenLanternConstructAttacks.giantHand(player);
		}
	}

	/**
	 * Shift + tap N (v0.15.15; plain N before that, Shift+C before v0.14.3): dismiss every construct. A Rescue Tether hold is set down safely first, and anything in the
	 * Giant Hand is let go; a second press then dismisses the rest.
	 */
	public static void clearConstructs(ServerPlayer player) {
		if (!hasContext(player)) {
			return;
		}
		if (GreenLanternConstructs.releaseRescueHeldSafely(player)) {
			return;
		}
		GreenLanternConstructAttacks.dismissAll(player.getUUID());
		GreenLanternConstructs.dismissRequested(player);
	}

	/** Shift + N pressed: start taking the ring off (it comes off after {@link GreenLanternConfig#RING_REMOVE_HOLD_TICKS}). */
	public static void ringRemoveStart(ServerPlayer player) {
		if (!hasContext(player) || !player.isShiftKeyDown() || RING_REMOVE.containsKey(player.getUUID())) {
			return;
		}
		long now = player.level().getGameTime();
		RING_REMOVE.put(player.getUUID(), now);
		GreenLanternVisuals.ringRemove(player, now);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.ring_remove_hold")
				.withStyle(net.minecraft.ChatFormatting.YELLOW), true);
	}

	/** N released (or Sneak let go): the ring stays on. */
	public static void ringRemoveStop(ServerPlayer player) {
		if (RING_REMOVE.remove(player.getUUID()) != null) {
			GreenLanternVisuals.ringRemove(player, 0L);
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.ring_remove_cancel")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
		}
	}

	private static void tickRingRemove(ServerPlayer player) {
		Long since = RING_REMOVE.get(player.getUUID());
		if (since == null) {
			return;
		}
		if (!player.isShiftKeyDown()) {
			ringRemoveStop(player);
			return;
		}
		long held = player.level().getGameTime() - since;
		if (held % 10 == 0) {
			player.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(
					new org.joml.Vector3f(0.208f, 0.941f, 0.459f), 1.0f), player.getX(), player.getY() + 1.0, player.getZ(),
					4 + (int) (held / 10), 0.3, 0.4, 0.3, 0.02);
		}
		if (held >= GreenLanternConfig.RING_REMOVE_HOLD_TICKS) {
			RING_REMOVE.remove(player.getUUID());
			GreenLantern.removeRing(player);
		}
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
				// v0.14.3: Shift+C is the Missile Barrage now -- dismissing constructs (and setting a tethered
				// creature down) moved to N, see #clearConstructs.
				GreenLanternConstructAttacks.missileBarrage(player);
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
	 * (the construct wheel). Re-validates the ordinal -- the client only ever shows real wedges, but the
	 * payload is not trusted just because the screen was built correctly.
	 */
	public static void selectConstruct(ServerPlayer player, int ordinal) {
		if (!hasContext(player) || ordinal < 0 || ordinal >= ConstructType.values().length) {
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
		// v0.11.7: a flat passive Resistance I for any bonded Green Lantern, suited or not (same
		// unsuited-still-works convention as the fall-damage immunity) -- reapplied every 5s on a short
		// effect so it never actually runs out, rather than tracked as its own persisted flag.
		if (player.tickCount % 100 == 0) {
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 140,
					GreenLanternConfig.RING_RESISTANCE_AMPLIFIER, true, false, false));
		}
		GreenLanternOath.tick(player);
		GreenLanternSuit.tick(player);
		GreenLanternBattery.tick(player);
		GreenLanternAirTank.tick(player);

		if (GreenLantern.isSuited(player)) {
			com.projecthero.mod.greenlantern.item.GreenLanternSuitArmor.reequipMissing(player);
			com.projecthero.mod.greenlantern.item.GreenLanternSuitArmor.deleteLoose(player);
			// v0.11.5: wearing the suit is no longer free -- 1 charge every 5 seconds. Depleting the ring
			// entirely while suited forces it back off rather than leaving an unpayable debt.
			if (player.tickCount % GreenLanternConfig.SUIT_UPKEEP_INTERVAL_TICKS == 0
					&& !GreenLanternEnergy.spend(player, GreenLanternConfig.SUIT_UPKEEP_COST)) {
				GreenLanternSuit.forceSuitDown(player);
			}
		}

		boolean beamChannelling = GreenLanternCombat.isChannellingBeam(player);
		if (beamChannelling) {
			GreenLanternCombat.beamTick(player);
		}
		GreenLanternConstructAttacks.gatlingTick(player);
		tickRingRemove(player);
		GreenLanternShield.tickShieldUpkeep(player);
		GreenLanternShield.tickDomeUpkeep(player);
		GreenLanternShield.tickMeterRegen(player);
		GreenLanternConstructs.tickRescueHeld(player);

		if (GreenLanternFlight.isFlying(player)) {
			boolean boosting = player.isShiftKeyDown() && player.isSprinting();
			float drain = GreenLanternFlight.tick(player, boosting);
			if (!GreenLanternEnergy.drainTick(player, drain)) {
				emergencyDescend(player);
			}
		}

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
