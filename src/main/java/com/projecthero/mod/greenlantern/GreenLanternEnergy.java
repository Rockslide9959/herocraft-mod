package com.projecthero.mod.greenlantern;

import java.util.UUID;

import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Ring Charge resource. 0..{@link GreenLanternConfig#MAX_RING_CHARGE}; the last
 * {@link GreenLanternConfig#EMERGENCY_RESERVE} points are reserved for an emergency flight descent
 * and never spendable by {@link #spend}. There is no passive regeneration (v0.11.4) -- the
 * only way to gain charge is {@link #addCharge}, called once, instantly, when a Power Battery Oath
 * completes (see {@link GreenLanternBattery}).
 *
 * <p>Server-authoritative in every respect: only a server-side {@link #spend}/{@link #spendEmergency}
 * moves the pool down and only {@link #addCharge} moves it up.
 */
public final class GreenLanternEnergy {
	private GreenLanternEnergy() {
	}

	/** No per-player session state of its own any more (the Mastery XP carry it used to track is gone). */
	public static void clearSessionState() {
	}

	public static void onCleanup(UUID playerId) {
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
	 * false and spends nothing if the player is short.
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
		GreenLantern.save(player, c);
		return true;
	}

	/** Continuous per-tick drain for an upkeep (constructs, channels). Same accounting as {@link #spend}. */
	public static boolean drainTick(ServerPlayer player, float perTick) {
		return spend(player, perTick);
	}

	/**
	 * Spend from the emergency reserve only (emergency flight descent) -- may dip the pool all the way
	 * to 0, unlike {@link #spend}.
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

	/**
	 * Undo a {@link #spend} that turned out not to earn anything (a construct that failed to place, an
	 * ability refused after the cost was already taken).
	 */
	public static void refund(ServerPlayer player, float amount) {
		if (amount <= 0f) {
			return;
		}
		GreenLanternState c = GreenLantern.state(player).copy();
		c.ringCharge = Math.min(GreenLanternConfig.MAX_RING_CHARGE, c.ringCharge + amount);
		GreenLantern.save(player, c);
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
