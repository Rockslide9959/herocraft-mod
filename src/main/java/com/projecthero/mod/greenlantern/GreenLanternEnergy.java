package com.projecthero.mod.greenlantern;

import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Ring Charge resource. 0..{@link GreenLanternConfig#MAX_RING_CHARGE}; the last
 * {@link GreenLanternConfig#EMERGENCY_RESERVE} points are reserved for Emergency Catch/emergency
 * descent and never spendable by {@link #spend}. Passive regen is 2/sec, but only once 8 seconds have
 * passed since {@link #markAbilityUsed}, and never while a channel (beam, battery recharge) holds it
 * off via {@link #tickRegen}'s {@code suppressed} argument.
 *
 * <p>Server-authoritative in every respect: only a server-side {@link #spend}/{@link #spendEmergency}
 * moves the pool down and only {@link #tickRegen}/{@link #addCharge} moves it up.
 */
public final class GreenLanternEnergy {
	private GreenLanternEnergy() {
	}

	public static float get(ServerPlayer player) {
		return GreenLantern.state(player).ringCharge;
	}

	public static float getChargePercent(ServerPlayer player) {
		return get(player) / GreenLanternConfig.MAX_RING_CHARGE;
	}

	/** Charge available for abilities/constructs/flight -- excludes the emergency reserve. */
	public static float getSpendableCharge(ServerPlayer player) {
		return Math.max(0f, get(player) - GreenLanternConfig.EMERGENCY_RESERVE);
	}

	public static boolean canSpend(ServerPlayer player, float amount) {
		return getSpendableCharge(player) >= amount;
	}

	/**
	 * Spend {@code amount} from the spendable pool (never touching the emergency reserve). Returns
	 * false and spends nothing if the player is short. Tracks {@code totalEnergySpent} for Mastery.
	 */
	public static boolean spend(ServerPlayer player, float amount) {
		if (amount <= 0f) {
			return true;
		}
		if (!canSpend(player, amount)) {
			return false;
		}
		GreenLanternState c = GreenLantern.state(player).copy();
		c.ringCharge = Math.max(0f, c.ringCharge - amount);
		c.totalEnergySpent += Math.round(amount);
		GreenLantern.save(player, c);
		com.projecthero.mod.greenlantern.GreenLanternMastery.onEnergySpent(player);
		return true;
	}

	/** Continuous per-tick drain for an upkeep (constructs, channels). Same accounting as {@link #spend}. */
	public static boolean drainTick(ServerPlayer player, float perTick) {
		return spend(player, perTick);
	}

	/**
	 * Spend from the emergency reserve only (Emergency Catch, emergency flight descent) -- may dip the
	 * pool all the way to 0, unlike {@link #spend}.
	 */
	public static boolean spendEmergency(ServerPlayer player, float amount) {
		GreenLanternState s = GreenLantern.state(player);
		if (s.ringCharge < amount) {
			return false;
		}
		GreenLanternState c = s.copy();
		c.ringCharge = Math.max(0f, c.ringCharge - amount);
		GreenLantern.save(player, c);
		return true;
	}

	public static void addCharge(ServerPlayer player, float amount) {
		if (amount <= 0f) {
			return;
		}
		GreenLanternState s = GreenLantern.state(player);
		if (s.ringCharge >= GreenLanternConfig.MAX_RING_CHARGE) {
			return;
		}
		GreenLanternState c = s.copy();
		c.ringCharge = Math.min(GreenLanternConfig.MAX_RING_CHARGE, c.ringCharge + amount);
		GreenLantern.save(player, c);
	}

	/** Call whenever a ring ability is used -- resets the 8s passive-regen delay. */
	public static void markAbilityUsed(ServerPlayer player) {
		GreenLanternState c = GreenLantern.state(player).copy();
		c.lastAbilityUseTick = player.level().getGameTime();
		GreenLantern.save(player, c);
	}

	/** Once per server tick while suited. {@code suppressed} = a channel (beam/battery) owns the pool right now. */
	public static void tickRegen(ServerPlayer player, boolean suppressed) {
		GreenLanternState s = GreenLantern.state(player);
		if (!s.hasPower || s.ringCharge >= GreenLanternConfig.MAX_RING_CHARGE || suppressed) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - s.lastAbilityUseTick < GreenLanternConfig.PASSIVE_REGEN_DELAY_TICKS) {
			return;
		}
		GreenLanternState c = s.copy();
		c.ringCharge = Math.min(GreenLanternConfig.MAX_RING_CHARGE, c.ringCharge + GreenLanternConfig.PASSIVE_REGEN_PER_SEC / 20f);
		GreenLantern.save(player, c);
	}

	public static void triggerLowChargeFeedback(ServerPlayer player, float before, float after) {
		float pctBefore = before / GreenLanternConfig.MAX_RING_CHARGE;
		float pctAfter = after / GreenLanternConfig.MAX_RING_CHARGE;
		String key = null;
		if (pctBefore > GreenLanternConfig.LOW_CHARGE_WARN_5 && pctAfter <= GreenLanternConfig.LOW_CHARGE_WARN_5) {
			key = "message.projecthero.green_lantern.charge_critical";
		} else if (pctBefore > GreenLanternConfig.LOW_CHARGE_WARN_10 && pctAfter <= GreenLanternConfig.LOW_CHARGE_WARN_10) {
			key = "message.projecthero.green_lantern.charge_low";
		} else if (pctBefore > GreenLanternConfig.LOW_CHARGE_WARN_25 && pctAfter <= GreenLanternConfig.LOW_CHARGE_WARN_25) {
			key = "message.projecthero.green_lantern.charge_warning";
		}
		if (key != null) {
			player.displayClientMessage(Component.translatable(key), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), net.minecraft.sounds.SoundSource.PLAYERS,
					0.6f, 0.7f);
		}
	}

	public static void feedback(ServerPlayer player, String reasonKey) {
		player.displayClientMessage(Component.translatable(reasonKey), true);
	}
}
