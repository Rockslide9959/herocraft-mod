package com.herocraft.mod.maxsteel;

import com.herocraft.mod.maxsteel.data.MaxSteelState;

import net.minecraft.server.level.ServerPlayer;

/**
 * The T.U.R.B.O. Energy resource. Max generates it naturally -- it is "safe channelable energy", not a
 * finite battery -- so it always trends back toward full. Regeneration is 18/sec out of combat, 9/sec
 * in combat, paused for {@link MaxSteelConfig#HIGH_COST_REGEN_DELAY_TICKS} after a high-cost ability
 * and entirely while a specialised mode's own drain is running (that drain is applied by
 * {@link MaxSteelModes}). At 0 energy the suit drops to Base Mode and every ability locks until natural
 * regen brings the pool back up to {@link MaxSteelConfig#OVERLOAD_RECOVER_ENERGY}.
 *
 * <p>Server-authoritative in every respect: only a server-side {@link #spend} moves the pool down and
 * only {@link #tickRegen} moves it up. A client packet is a request; the client's own idea of its
 * energy is never trusted.
 */
public final class MaxSteelEnergy {
	private MaxSteelEnergy() {
	}

	public static float get(ServerPlayer player) {
		return MaxSteel.state(player).turboEnergy;
	}

	public static boolean has(ServerPlayer player, float cost) {
		return MaxSteel.state(player).turboEnergy >= cost;
	}

	/**
	 * Spend {@code cost} energy. Returns false without spending anything when the player is short.
	 * A spend at or above {@link MaxSteelConfig#HIGH_COST_FRACTION} of the pool trips the regen delay.
	 */
	public static boolean spend(ServerPlayer player, float cost) {
		if (cost <= 0f) {
			return true;
		}
		MaxSteelState s = MaxSteel.state(player);
		if (s.turboEnergy < cost) {
			return false;
		}
		MaxSteelState c = s.copy();
		c.turboEnergy = Math.max(0f, c.turboEnergy - cost);
		if (cost >= MaxSteelConfig.MAX_TURBO_ENERGY * MaxSteelConfig.HIGH_COST_FRACTION) {
			c.lastHighCostTick = player.level().getGameTime();
		}
		MaxSteel.save(player, c);
		return true;
	}

	/** Continuous drain (per-tick fraction of a per-second figure) for an active mode. Never trips the regen delay. */
	public static void drain(ServerPlayer player, float perTick) {
		MaxSteelState s = MaxSteel.state(player);
		if (s.turboEnergy <= 0f) {
			return;
		}
		MaxSteelState c = s.copy();
		c.turboEnergy = Math.max(0f, c.turboEnergy - perTick);
		MaxSteel.saveEnergy(player, c, s);
	}

	/** Mark the player as "in combat" for regen-rate purposes. Call on any hit given or taken. */
	public static void markCombat(ServerPlayer player) {
		MaxSteelState s = MaxSteel.state(player);
		long until = player.level().getGameTime() + MaxSteelConfig.COMBAT_WINDOW_TICKS;
		if (s.combatUntil >= until - 5) {
			return;
		}
		MaxSteelState c = s.copy();
		c.combatUntil = until;
		MaxSteel.save(player, c);
	}

	/**
	 * v0.9.2: an overload holds until natural regen brings the pool back up to
	 * {@link MaxSteelConfig#OVERLOAD_RECOVER_ENERGY}, not for a fixed number of ticks. {@code lockoutUntil}
	 * is now just a nonzero "overloaded" marker; {@link #tickRegen} clears it once the pool recovers.
	 */
	public static boolean isLockedOut(ServerPlayer player) {
		MaxSteelState s = MaxSteel.state(player);
		return s.lockoutUntil != 0L && s.turboEnergy < MaxSteelConfig.OVERLOAD_RECOVER_ENERGY;
	}

	/**
	 * Begin the overload lock-out: the pool is spent (an overload only fires at ~0 anyway) and every
	 * ability locks until natural regen brings it back to {@link MaxSteelConfig#OVERLOAD_RECOVER_ENERGY}.
	 */
	public static void triggerOverloadLockout(ServerPlayer player) {
		MaxSteelState c = MaxSteel.state(player).copy();
		c.turboEnergy = 0f;
		c.lockoutUntil = player.level().getGameTime() + 1L; // nonzero marker
		MaxSteel.save(player, c);
	}

	/**
	 * Natural regeneration, once per server tick. Does nothing while a specialised mode is draining
	 * (the mode owns the pool then), during the post-high-cost delay, during the overload lock-out, or
	 * while charging the Turbo Cannon.
	 */
	public static void tickRegen(ServerPlayer player, boolean modeDraining, boolean cannonCharging) {
		MaxSteelState s = MaxSteel.state(player);
		if (!s.hasPower || !s.transformed) {
			return;
		}
		if (s.turboEnergy >= MaxSteelConfig.MAX_TURBO_ENERGY) {
			return;
		}
		long now = player.level().getGameTime();
		if (modeDraining || cannonCharging) {
			return;
		}
		if (now - s.lastHighCostTick < MaxSteelConfig.HIGH_COST_REGEN_DELAY_TICKS) {
			return;
		}
		boolean inCombat = now < s.combatUntil;
		float perSec = inCombat ? MaxSteelConfig.COMBAT_REGEN_PER_SEC : MaxSteelConfig.OUT_OF_COMBAT_REGEN_PER_SEC;
		MaxSteelState c = s.copy();
		c.turboEnergy = Math.min(MaxSteelConfig.MAX_TURBO_ENERGY, c.turboEnergy + perSec / 20f);
		// v0.9.2: an overload clears the instant the pool has recovered to the threshold.
		if (c.lockoutUntil != 0L && c.turboEnergy >= MaxSteelConfig.OVERLOAD_RECOVER_ENERGY) {
			c.lockoutUntil = 0L;
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.herocraft.max_steel.systems_online"), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), net.minecraft.sounds.SoundSource.PLAYERS,
					0.4f, 1.6f);
			MaxSteel.save(player, c);
			return;
		}
		MaxSteel.saveEnergy(player, c, s);
	}
}
