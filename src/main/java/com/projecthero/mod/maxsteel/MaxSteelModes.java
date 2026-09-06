package com.projecthero.mod.maxsteel;

import com.projecthero.mod.maxsteel.data.MaxSteelState;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The specialised-mode framework: entering, switching and exiting Turbo Strength / Speed / Flight /
 * Stealth. Only one is ever active; Base Mode is the fallback. Each switch ends the previous mode's
 * effects, checks the overload lock-out, pays the new activation cost, plays a short reconfiguration
 * flourish and sets the max-duration clock.
 *
 * <p>{@link #clearAll} is the single teardown hub for every mode side effect -- called on suit-down,
 * energy depletion, death, respawn, logout, dimension change and power removal.
 */
public final class MaxSteelModes {
	private MaxSteelModes() {
	}

	/**
	 * Enter (or toggle off) a specialised mode. Returns false -- doing nothing -- if the player is not
	 * suited, is locked out, or cannot afford the activation cost. Re-pressing the ability for the mode
	 * you are already in drops you back to Base.
	 *
	 * <p>v0.6.20: a mode has no time limit any more -- once on it stays on until you re-press its key,
	 * switch to another mode, power down, or run the T.U.R.B.O. pool dry.
	 */
	public static boolean toggle(ServerPlayer player, MaxSteelMode mode, float activationCost) {
		MaxSteelState s = MaxSteel.state(player);
		if (!s.transformed || s.transformDir != MaxSteelState.DIR_IDLE) {
			player.displayClientMessage(Component.translatable("message.projecthero.max_steel.not_transformed"), true);
			return false;
		}
		if (s.modeEnum() == mode) {
			exitToBase(player, true);
			return true;
		}
		if (MaxSteelEnergy.isLockedOut(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.max_steel.locked_out"), true);
			return false;
		}
		if (mode == MaxSteelMode.STEALTH && MaxSteelStealth.onCooldown(player)) {
			MaxSteelFeedback.onCooldown(player, MaxSteelStealth.ABILITY,
					MaxSteel.cooldownRemaining(player, MaxSteelStealth.ABILITY));
			return false;
		}
		if (!MaxSteelEnergy.spend(player, activationCost)) {
			MaxSteelFeedback.noEnergy(player, activationCost);
			return false;
		}

		// end whatever was running
		clearModeEffects(player);

		MaxSteelState c = MaxSteel.state(player).copy();
		c.mode = mode.ordinal();
		c.modeEndsAt = 0L; // v0.6.20: no hard duration -- energy drain is the only clock
		MaxSteel.save(player, c);

		MaxSteelAttributes.reconcile(player);
		switch (mode) {
			case FLIGHT -> MaxSteelFlight.onEnter(player);
			case STEALTH -> MaxSteelStealth.onEnter(player);
			case SPEED -> MaxSteelSpeed.applyBoost(player);
			default -> {
			}
		}
		reconfigureFx(player, mode);
		return true;
	}

	/** Drop back to Base Mode, ending the current mode's effects. */
	public static void exitToBase(ServerPlayer player, boolean flourish) {
		MaxSteelMode was = MaxSteel.mode(player);
		clearModeEffects(player);
		MaxSteelState c = MaxSteel.state(player).copy();
		c.mode = MaxSteelMode.BASE.ordinal();
		c.modeEndsAt = 0L;
		MaxSteel.save(player, c);
		MaxSteelAttributes.reconcile(player);
		if (flourish && was != MaxSteelMode.BASE) {
			reconfigureFx(player, MaxSteelMode.BASE);
		}
	}

	/** Energy ran out mid-mode: hard drop to Base + a 4s lock-out + a harmless overload flourish. */
	public static void overload(ServerPlayer player) {
		clearModeEffects(player);
		MaxSteelState c = MaxSteel.state(player).copy();
		c.mode = MaxSteelMode.BASE.ordinal();
		c.modeEndsAt = 0L;
		MaxSteel.save(player, c);
		MaxSteelEnergy.triggerOverloadLockout(player);
		MaxSteelAttributes.reconcile(player);

		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
				30, 0.4, 0.7, 0.4, 0.15);
		// v0.6.20: a single low beep instead of the beacon power-down groan.
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.4f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.max_steel.overloaded"), true);
	}

	private static void clearModeEffects(ServerPlayer player) {
		MaxSteelFlight.forceStop(player, true);
		MaxSteelStealth.forceStop(player);
		MaxSteelSpeed.forceStop(player);
	}

	/** Remove every mode side effect -- suit-down, energy depletion, death, respawn, logout, power loss. */
	public static void clearAll(ServerPlayer player) {
		MaxSteelAttributes.clearAll(player);
		MaxSteelFlight.forceStop(player, false);
		MaxSteelStealth.forceStop(player);
		MaxSteelSpeed.forceStop(player);
		MaxSteelCannon.endFlight(player);
	}

	private static void reconfigureFx(ServerPlayer player, MaxSteelMode mode) {
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
				14, 0.35, 0.6, 0.35, 0.03);
		// v0.6.20: a single beep on a mode change, not a metallic slam.
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.4f, mode == MaxSteelMode.BASE ? 0.9f : 1.4f);
	}
}
