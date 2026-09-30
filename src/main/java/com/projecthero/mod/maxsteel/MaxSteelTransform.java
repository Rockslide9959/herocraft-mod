package com.projecthero.mod.maxsteel;

import com.projecthero.mod.maxsteel.data.MaxSteelState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The suit-up / suit-down state machine, server side. The visual pixel-by-pixel reveal is a client
 * concern (a later phase) that reads {@link MaxSteelState#transformDir},
 * {@link MaxSteelState#transformStartTick} and {@link MaxSteelState#transformDurationTicks} off the
 * synced state and interpolates {@code revealProgress}; the server only owns <em>when</em> the suit is
 * on and drives the timed hand-off between "forming", "on" and "retracting".
 *
 * <ul>
 *   <li>{@link #beginSuitUp} -- {@code DIR_SUITING_UP} for the duration, then settle to on + idle.</li>
 *   <li>{@link #beginSuitDown} -- {@code DIR_SUITING_DOWN} for the duration, then off + idle.</li>
 *   <li>{@link #tick} -- advances whichever animation is running and settles it when the clock runs out.</li>
 * </ul>
 */
public final class MaxSteelTransform {
	private MaxSteelTransform() {
	}

	public static boolean isAnimating(MaxSteelState s) {
		return s.transformDir != MaxSteelState.DIR_IDLE;
	}

	/** Fraction 0..1 of the current transform animation, for the HUD / reveal shader on the server side. */
	public static float progress(ServerPlayer player) {
		MaxSteelState s = MaxSteel.state(player);
		if (s.transformDir == MaxSteelState.DIR_IDLE) {
			return s.transformed ? 1f : 0f;
		}
		long elapsed = player.level().getGameTime() - s.transformStartTick;
		return Math.max(0f, Math.min(1f, (float) elapsed / Math.max(1, s.transformDurationTicks)));
	}

	/**
	 * v0.14.2: the H key -- the one key that transforms and powers down. Unsuited: Go Turbo. Suited: power down
	 * (refused in combat, see {@link #powerDown}). Ignored while the suit is still forming / retracting.
	 */
	public static void toggle(ServerPlayer player) {
		MaxSteelState s = MaxSteel.state(player);
		if (isAnimating(s)) {
			return;
		}
		if (s.transformed) {
			powerDown(player);
		} else {
			goTurbo(player);
		}
	}

	/** Go Turbo. A no-op if already suited or mid-animation. */
	public static void goTurbo(ServerPlayer player) {
		MaxSteelState s = MaxSteel.state(player);
		if (isAnimating(s) || s.transformed) {
			return;
		}
		beginSuitUp(player, false);
	}

	/** Power down (H while suited, v0.14.2), unless in combat. */
	public static void powerDown(ServerPlayer player) {
		MaxSteelState s = MaxSteel.state(player);
		if (isAnimating(s) || !s.transformed) {
			return;
		}
		if (player.level().getGameTime() >= s.combatUntil) {
			beginSuitDown(player);
		} else {
			player.displayClientMessage(net.minecraft.network.chat.Component
					.translatable("message.projecthero.max_steel.cannot_power_down"), true);
		}
	}

	/**
	 * Start suiting up straight into a specialised mode (v0.6.17; restored in v0.14.4): a mode key (G / X / Z / V) pressed
	 * while unsuited armours up and drops into {@code mode} the instant the suit settles, in one step. The nanotech
	 * builds that mode's form directly ({@link MaxSteel#formMode}), so there is no Base-then-swap double animation.
	 *
	 * <p>Checked up front, before anything forms: a mode that could not be entered at the end (overload lock-out,
	 * Stealth on cooldown, not enough T.U.R.B.O. Energy for the activation cost) says why and does not transform at all.
	 */
	public static boolean beginSuitUpIntoMode(ServerPlayer player, MaxSteelMode mode) {
		MaxSteelState s = MaxSteel.state(player);
		if (!s.hasPower || s.transformed || isAnimating(s) || !mode.isSpecialised()) {
			return false;
		}
		if (MaxSteelEnergy.isLockedOut(player)) {
			player.displayClientMessage(net.minecraft.network.chat.Component
					.translatable("message.projecthero.max_steel.locked_out"), true);
			return false;
		}
		if (mode == MaxSteelMode.STEALTH && MaxSteelStealth.onCooldown(player)) {
			MaxSteelFeedback.onCooldown(player, MaxSteelStealth.ABILITY,
					MaxSteel.cooldownRemaining(player, MaxSteelStealth.ABILITY));
			return false;
		}
		float cost = activationCost(mode);
		if (!MaxSteelEnergy.has(player, cost)) {
			MaxSteelFeedback.noEnergy(player, cost);
			return false;
		}
		return beginSuitUp(player, false, mode.ordinal());
	}

	public static boolean beginSuitUp(ServerPlayer player, boolean firstBond) {
		return beginSuitUp(player, firstBond, -1);
	}

	private static boolean beginSuitUp(ServerPlayer player, boolean firstBond, int pendingMode) {
		MaxSteelState s = MaxSteel.state(player);
		if (!s.hasPower || s.transformed || isAnimating(s)) {
			return false;
		}
		MaxSteelState c = s.copy();
		c.pendingMode = pendingMode; // v0.14.4: set in the same save, so viewers never see a Base form first
		c.transformed = true; // "on or coming on"
		c.transformDir = MaxSteelState.DIR_SUITING_UP;
		c.transformStartTick = player.level().getGameTime();
		c.transformDurationTicks = firstBond ? MaxSteelConfig.FIRST_BOND_TICKS : MaxSteelConfig.TRANSFORM_TICKS;
		c.mode = MaxSteelMode.BASE.ordinal();
		MaxSteel.save(player, c);
		// Equip the (synthetic) suit pieces now, so the model is present for the whole reveal; the
		// renderer hides its bones progressively based on the reveal clock.
		MaxSteelSuitArmor.equip(player);
		fx(player, true);
		return true;
	}

	public static boolean beginSuitDown(ServerPlayer player) {
		MaxSteelState s = MaxSteel.state(player);
		if (!s.transformed || s.transformDir == MaxSteelState.DIR_SUITING_DOWN) {
			return false;
		}
		MaxSteelModes.clearAll(player);
		MaxSteelVisuals.clear(player);
		MaxSteelState c = MaxSteel.state(player).copy();
		c.transformDir = MaxSteelState.DIR_SUITING_DOWN;
		c.transformStartTick = player.level().getGameTime();
		c.transformDurationTicks = MaxSteelConfig.TRANSFORM_TICKS;
		c.mode = MaxSteelMode.BASE.ordinal();
		c.modeEndsAt = 0L;
		MaxSteel.save(player, c);
		fx(player, false);
		return true;
	}

	/** Advance a running suit-up / suit-down and settle it when the clock runs out. Call every tick. */
	public static void tick(ServerPlayer player) {
		MaxSteelState s = MaxSteel.state(player);
		if (s.transformDir == MaxSteelState.DIR_IDLE) {
			return;
		}
		long elapsed = player.level().getGameTime() - s.transformStartTick;
		if (elapsed < s.transformDurationTicks) {
			return;
		}
		MaxSteelState c = s.copy();
		if (s.transformDir == MaxSteelState.DIR_SUITING_UP) {
			c.transformDir = MaxSteelState.DIR_IDLE;
			c.transformed = true;
			int pending = c.pendingMode;
			c.pendingMode = -1;
			MaxSteel.save(player, c);
			MaxSteelAttributes.reconcile(player);
			// v0.6.17: a mode key pressed while unsuited armours up straight into that mode.
			if (pending >= 0) {
				MaxSteelMode m = MaxSteelMode.byOrdinal(pending);
				if (m.isSpecialised()) {
					if (MaxSteelModes.toggle(player, m, activationCost(m))) {
						// v0.14.4: the suit already formed as this mode -- no Base -> mode swap animation on top
						MaxSteelVisuals.clearSwap(player);
					} else {
						// could not enter it after all: the form it was built as rematerialises back to Base
						MaxSteelVisuals.swap(player, m);
					}
				}
			}
		} else {
			c.transformDir = MaxSteelState.DIR_IDLE;
			c.transformed = false;
			c.mode = MaxSteelMode.BASE.ordinal();
			c.pendingMode = -1;
			MaxSteel.save(player, c);
			MaxSteelModes.clearAll(player);
			MaxSteelSuitArmor.strip(player);
			MaxSteelFaceplate.reconcile(player);
			MaxSteelAttributes.reconcile(player);
		}
	}

	/** Cooldown-map key for the emergency totem revive. */
	public static final String EMERGENCY_REVIVE = "emergency_revive";

	/**
	 * Death while unsuited pops an emergency totem (v0.6.17): the suit forms around the downed pilot
	 * and the death is cancelled. Costs {@link MaxSteelConfig#TOTEM_REVIVE_COST} energy and goes on a
	 * {@link MaxSteelConfig#TOTEM_REVIVE_COOLDOWN_TICKS} cooldown. Returns true if it fired.
	 */
	public static boolean tryEmergencyRevive(ServerPlayer player) {
		MaxSteelState s = MaxSteel.state(player);
		if (!s.hasPower || s.transformed || isAnimating(s)) {
			return false;
		}
		if (!MaxSteel.abilityReady(player, EMERGENCY_REVIVE)) {
			return false;
		}
		if (s.turboEnergy < MaxSteelConfig.TOTEM_REVIVE_COST) {
			return false;
		}

		MaxSteelState c = s.copy();
		c.turboEnergy = Math.max(0f, c.turboEnergy - MaxSteelConfig.TOTEM_REVIVE_COST);
		c.abilityReadyAt.put(EMERGENCY_REVIVE,
				player.level().getGameTime() + MaxSteelConfig.TOTEM_REVIVE_COOLDOWN_TICKS);
		MaxSteel.save(player, c);

		player.setHealth(player.getMaxHealth());
		player.removeAllEffects();
		player.clearFire();
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.REGENERATION, 200, 1));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.ABSORPTION, 200, 1));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 2));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 200, 0));

		player.level().broadcastEntityEvent(player, (byte) 35); // totem-of-undying particle burst
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.4f);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
				player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.9, 0.5, 0.1);
		player.displayClientMessage(net.minecraft.network.chat.Component
				.translatable("message.projecthero.max_steel.emergency_revive")
				.withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD), false);

		beginSuitUp(player, false);
		return true;
	}

	static float activationCost(MaxSteelMode mode) {
		return switch (mode) {
			case STRENGTH -> MaxSteelConfig.STRENGTH_ACTIVATION_COST;
			case SPEED -> MaxSteelConfig.SPEED_ACTIVATION_COST;
			case FLIGHT -> MaxSteelConfig.FLIGHT_ACTIVATION_COST;
			case STEALTH -> MaxSteelConfig.STEALTH_ACTIVATION_COST;
			default -> 0f;
		};
	}

	private static void fx(ServerPlayer player, boolean up) {
		ServerLevel level = player.serverLevel();
		// v0.6.20: a single soft beep, not a dramatic block-place slam.
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.4f, up ? 1.6f : 0.8f);
	}
}
