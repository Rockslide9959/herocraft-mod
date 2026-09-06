package com.projecthero.mod.ironman;

import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.server.level.ServerPlayer;

/**
 * "changes 17": the built-in air tank some Iron Man suits carry ({@link IronManSuit#airTankSeconds()}).
 *
 * <p>While the wearer's head is underwater and the tank still has charge, the suit keeps the player's
 * air supply topped up so they never start drowning. The tank drains over its rated number of seconds;
 * once it is empty the player drowns normally. Out of the water it refills at <b>3&times;</b> the drain
 * rate. The charge (0..1) is stored on the synced {@link TonyStarkState} so the HUD can draw an air
 * bar.
 *
 * <p>A suit with no air tank ({@code airTankSeconds() <= 0}) just resets the stored charge to full, so
 * switching back to a tank suit later starts from a full tank.
 */
public final class IronManAirTank {
	private IronManAirTank() {
	}

	public static void tick(ServerPlayer player, IronManSuit suit) {
		TonyStarkState s = TonyStark.state(player);
		int rated = suit == null ? 0 : suit.airTankSeconds();
		if (rated <= 0) {
			if (s.suitAir < 1.0f) {
				TonyStark.setSuitAir(player, 1.0f);
			}
			return;
		}
		float drainPerTick = 1.0f / (rated * 20f);
		float refillPerTick = drainPerTick * 3f;

		// isUnderWater() = the head is submerged (the same check vanilla uses to decrement air).
		if (player.isUnderWater()) {
			float remaining = s.suitAir - drainPerTick;
			if (remaining > 0f) {
				// Keep the vanilla air meter full so drowning never starts while the tank holds.
				player.setAirSupply(player.getMaxAirSupply());
				TonyStark.setSuitAir(player, remaining);
			} else {
				// Tank empty -> let the player drown normally.
				TonyStark.setSuitAir(player, 0f);
			}
		} else if (s.suitAir < 1.0f) {
			TonyStark.setSuitAir(player, s.suitAir + refillPerTick);
		}
	}
}
