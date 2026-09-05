package com.herocraft.mod.maxsteel;

import com.herocraft.mod.maxsteel.data.MaxSteelState;

import net.minecraft.server.level.ServerPlayer;

/**
 * Per-tick upkeep for whichever specialised Turbo Mode is active: bleed its energy drain and drop hard
 * to Base Mode (with the overload lock-out) the instant energy reaches 0. Ticked from
 * {@link MaxSteelAbilityManager#serverTick}.
 *
 * <p>v0.6.20: modes are no longer time-capped -- the only things that end one are the player toggling
 * it off, switching modes, powering down, or running the pool dry here.
 */
public final class MaxSteelModeRuntime {
	private MaxSteelModeRuntime() {
	}

	public static void tick(ServerPlayer player) {
		MaxSteelState s = MaxSteel.state(player);
		if (!s.transformed) {
			return;
		}
		MaxSteelMode mode = s.modeEnum();
		if (!mode.isSpecialised()) {
			return;
		}

		float perTick = drainPerTick(player, mode);
		if (perTick > 0f) {
			MaxSteelEnergy.drain(player, perTick);
		}
		if (MaxSteelEnergy.get(player) <= 0f) {
			MaxSteelModes.overload(player);
		}
	}

	private static float drainPerTick(ServerPlayer player, MaxSteelMode mode) {
		return switch (mode) {
			case STRENGTH -> MaxSteelConfig.STRENGTH_DRAIN_PER_SEC / 20f;
			case SPEED -> MaxSteelConfig.SPEED_DRAIN_PER_SEC / 20f;
			case STEALTH -> MaxSteelConfig.STEALTH_DRAIN_PER_SEC / 20f;
			case FLIGHT -> MaxSteelFlight.drainPerTick(player);
			default -> 0f;
		};
	}
}
