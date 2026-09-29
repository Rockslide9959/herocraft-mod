package com.projecthero.mod.maxsteel;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.maxsteel.data.MaxSteelState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to Max Steel's kit, and runs the power's per-player server
 * tick.
 *
 * <p>{@link com.projecthero.mod.hero.AbilityRouter} hands Max Steel the slots when {@link #hasContext}
 * is true -- the player has the power and has not deliberately selected an experimental mutation.
 * Sits after Thor, Iron Man and Spider-Man in the router priority.
 *
 * <p>Slot mapping (the mod's existing Ability 1-6 keys, in spec order). v0.14.2: H is the only key that transforms
 * and powers down ({@link MaxSteelTransform#toggle}); every ability key used while unsuited just says so.
 * <pre>
 *   R (slot 1)  Turbo Blast                 G (slot 2)  Turbo Strength
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
			case SLOT_6, SLOT_7, SLOT_8 -> handleAbilitySix(player, pressed); // H / N are mutation-only (v0.13.22); never routed here
		}
	}

	/**
	 * Run {@code action} if suited. v0.14.2: unsuited, the key no longer armours up on its own (only H transforms) --
	 * it just tells the pilot to press H.
	 */
	private static void requireSuit(ServerPlayer player, MaxSteelMode mode, Runnable action) {
		MaxSteelState s = MaxSteel.state(player);
		if (!s.transformed && !MaxSteelTransform.isAnimating(s)) {
			pressHHint(player);
			return;
		}
		if (s.transformDir != MaxSteelState.DIR_IDLE) {
			return; // mid-animation
		}
		action.run();
	}

	/** v0.14.2: an ability key pressed while unsuited -- only H transforms now. */
	private static void pressHHint(ServerPlayer player) {
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.max_steel.press_h")
				.withStyle(net.minecraft.ChatFormatting.AQUA), true);
	}

	// ---------------- Ability 1 (R): Turbo Blast (tap / hold to charge) ----------------

	private static void handleAbilityOne(ServerPlayer player, boolean pressed) {
		long now = player.level().getGameTime();
		MaxSteelState s = MaxSteel.state(player);
		if (pressed) {
			if (!s.transformed && !MaxSteelTransform.isAnimating(s)) {
				pressHHint(player);
				return;
			}
			if (s.transformDir != MaxSteelState.DIR_IDLE) {
				return;
			}
			ABILITY1_PRESSED.put(player.getUUID(), now);
			// the orb only starts forming in the hand for a shot that can actually go off
			if (MaxSteel.abilityReady(player, MaxSteelBlast.ABILITY) && MaxSteelEnergy.get(player) >= MaxSteelConfig.BLAST_COST) {
				MaxSteelVisuals.blastCharge(player, now);
			}
			return;
		}
		Long since = ABILITY1_PRESSED.remove(player.getUUID());
		MaxSteelVisuals.blastCharge(player, 0L);
		if (since == null || !s.transformed || s.transformDir != MaxSteelState.DIR_IDLE) {
			return;
		}
		long held = now - since;
		if (held < MaxSteelConfig.BLAST_TAP_TICKS) {
			MaxSteelBlast.tap(player);
		} else {
			float frac = Math.min(1f, (float) held / MaxSteelConfig.BLAST_MAX_CHARGE_TICKS);
			MaxSteelBlast.charged(player, frac);
		}
	}

	/** v0.14.2: the charge-stage cues while R is held -- a rising chime at each third, a surge at full. */
	private static void tickBlastCharge(ServerPlayer player) {
		Long since = ABILITY1_PRESSED.get(player.getUUID());
		if (since == null || MaxSteelVisuals.get(player).blastChargeStart() == 0L) {
			return;
		}
		long held = player.level().getGameTime() - since;
		for (int i = 0; i < MaxSteelConfig.BLAST_STAGES.length; i++) {
			if (held == Math.round(MaxSteelConfig.BLAST_STAGES[i] * MaxSteelConfig.BLAST_MAX_CHARGE_TICKS)) {
				boolean full = i == MaxSteelConfig.BLAST_STAGES.length - 1;
				com.projecthero.mod.hero.power.AbilityHelpers.sound(player, full
						? net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE : net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
						full ? 0.6f : 0.9f, full ? 2.0f : 1.2f + 0.3f * i);
				net.minecraft.world.phys.Vec3 hand = com.projecthero.mod.hero.power.AbilityHelpers.handPosition(player);
				for (ServerPlayer viewer : player.serverLevel().players()) {
					if (viewer != player && viewer.distanceToSqr(player) < 64 * 64) { // not in the pilot's own face
						player.serverLevel().sendParticles(viewer, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
								false, hand.x, hand.y, hand.z, 6 + 6 * i, 0.15, 0.15, 0.15, 0.08);
					}
				}
			}
		}
	}

	// ---------------- Ability 6 (C): Turbo Cannon (charge + release) ----------------

	private static void handleAbilitySix(ServerPlayer player, boolean pressed) {
		MaxSteelState s = MaxSteel.state(player);
		if (!s.transformed && !MaxSteelTransform.isAnimating(s)) {
			if (pressed) {
				pressHHint(player);
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
			ABILITY1_PRESSED.remove(player.getUUID());
			return;
		}
		MaxSteelSuitArmor.reequipMissing(player);
		MaxSteelSuitArmor.deleteLoose(player);
		MaxSteelModeRuntime.tick(player);
		MaxSteelStealth.tick(player);
		MaxSteelCannon.tick(player);
		tickBlastCharge(player);
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
			case SLOT_6, SLOT_7, SLOT_8 -> "turbo_cannon"; // H / N are mutation-only (v0.13.22); never routed here
		};
	}
}
