package com.projecthero.mod.greenlantern;

import java.util.UUID;

import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.minecraft.ChatFormatting;
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
	 * Spend {@code amount} from the spendable pool (never touching the emergency reserve), doubled
	 * (v0.11.7) whenever {@link GreenLanternOath}'s "Green Lantern's Light!" empowerment mode is active
	 * -- "drains 2x energy for everything" applies here, the single choke point every ability/construct/
	 * flight/suit cost already routes through, rather than at each call site individually. Returns false
	 * and spends nothing if the player is short. Use {@link #spendRaw} for a cost that must NOT be
	 * doubled by the mode (its own flat upkeep drain).
	 */
	public static boolean spend(ServerPlayer player, float amount) {
		return spendRaw(player, amount * GreenLanternOath.multiplier(player));
	}

	/** Like {@link #spend}, but never doubled by the Oath empowerment mode. */
	public static boolean spendRaw(ServerPlayer player, float amount) {
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

	/**
	 * How many of {@link GreenLanternConfig#LOW_CHARGE_WARN_THRESHOLDS} {@code frac} is at or below --
	 * 0 means above the highest (40%) threshold entirely. Pure function of the charge fraction, safe to
	 * call from either side (the client-side HUD uses it too, to scale how urgently the charge bar
	 * pulses -- see {@code GreenLanternHud}).
	 */
	public static int severityTier(float frac) {
		int tier = 0;
		for (float threshold : GreenLanternConfig.LOW_CHARGE_WARN_THRESHOLDS) {
			if (frac <= threshold) {
				tier++;
			}
		}
		return tier;
	}

	/**
	 * v0.11.7: eight escalating warnings (was three) -- a real notification sound plus a flashing
	 * action-bar message every time charge crosses one of {@link GreenLanternConfig#LOW_CHARGE_WARN_THRESHOLDS}
	 * (40/35/30/25/20/15/10/5%), each more intense than the last (louder, higher-pitched, more urgently
	 * coloured). If a single tick's drain jumps past more than one threshold at once, only the most
	 * severe (lowest) one crossed fires -- the thresholds array is sorted descending, so the last match
	 * found while scanning it is the most severe. The HUD's own charge-bar pulse (see
	 * {@code GreenLanternHud}) is the continuous "flash above the hotbar" -- it reacts to
	 * {@link #severityTier} directly and speeds up as charge drops further, rather than this method
	 * trying to schedule a multi-tick blink itself.
	 */
	public static void triggerLowChargeFeedback(ServerPlayer player, float before, float after) {
		float pctBefore = before / GreenLanternConfig.MAX_RING_CHARGE;
		float pctAfter = after / GreenLanternConfig.MAX_RING_CHARGE;
		int severity = -1;
		float crossed = 0f;
		float[] thresholds = GreenLanternConfig.LOW_CHARGE_WARN_THRESHOLDS;
		for (int i = 0; i < thresholds.length; i++) {
			if (pctBefore > thresholds[i] && pctAfter <= thresholds[i]) {
				severity = i;
				crossed = thresholds[i];
			}
		}
		if (severity < 0) {
			return;
		}
		ChatFormatting color = severity >= 6 ? ChatFormatting.RED : severity >= 3 ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
		net.minecraft.network.chat.Style style = severity >= 6
				? net.minecraft.network.chat.Style.EMPTY.withColor(color).withBold(true)
				: net.minecraft.network.chat.Style.EMPTY.withColor(color);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.charge_warning",
				Math.round(crossed * 100f)).setStyle(style), true);
		float pitch = 0.6f + severity * 0.15f;
		float volume = 0.5f + severity * 0.05f;
		net.minecraft.sounds.SoundEvent sound = severity >= 6
				? net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value()
				: net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				sound, net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch);
	}

	public static void feedback(ServerPlayer player, String reasonKey) {
		player.displayClientMessage(Component.translatable(reasonKey), true);
	}
}
