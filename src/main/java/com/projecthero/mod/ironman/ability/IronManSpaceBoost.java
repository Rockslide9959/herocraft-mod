package com.projecthero.mod.ironman.ability;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManIntegrity;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.TonyStarkState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (user): the Mk VIII Space armour's ({@code mark_vii}) <b>Flight Boost</b> -- X tap, replacing the old Booster
 * Rockets ("give the space armour a flight boost ability ... which enables doubled flight speed while player is sprint
 * flying, it automatically disables itself after players stop sprint flying, it makes flight cost 2.5x as much energy").
 * <ul>
 *   <li><b>X</b> arms it. While it is armed and you sprint-fly it is <b>engaged</b>: flight speed x{@value #SPEED_MULTIPLIER}
 *       (the client's directional flight reads {@link #speedMultiplier}), and the flight drain is x{@value #DRAIN_MULTIPLIER}
 *       the normal sprint-flight drain ({@code IronManEnergy.flightDrainTick}). Pressed while not sprint-flying it waits,
 *       armed, and engages the moment you sprint-fly.</li>
 *   <li>Once it has engaged, stopping sprint flight (letting go of sprint, landing, the flight ending) switches it off.
 *       X again switches it off by hand (free, no cooldown). A brownout, a disabled suit or taking the suit off switches
 *       it off too.</li>
 * </ul>
 * The armed flag rides in the synced {@code TonyStarkState.abilityReadyAt} map under {@code mark_vii/}{@value #ARMED_KEY}
 * (so every client can draw the boost: the longer, brighter jets, the sonic-boom ring and the HUD BOOST tag); whether it
 * has engaged yet is server scratch state, cleared by {@link #clearSessionState} (ServerStateReset).
 */
public final class IronManSpaceBoost {
	public static final String SUIT = "mark_vii";
	public static final String MOVE = "mk7_flight_boost";
	/** The synced armed flag's key (value {@link Long#MAX_VALUE} while armed). */
	public static final String ARMED_KEY = "mk7_flight_boost_on";
	public static final double SPEED_MULTIPLIER = 2.0;
	public static final float DRAIN_MULTIPLIER = 2.5f;

	private static final Set<UUID> ENGAGED = new HashSet<>();

	private IronManSpaceBoost() {
	}

	// ------------------------------------------------------------------ either side

	/** Is the Flight Boost armed on {@code suitId}? (synced -- either side) */
	public static boolean armed(TonyStarkState state, String suitId) {
		return state != null && SUIT.equals(suitId) && state.abilityReadyAt.getOrDefault(SUIT + "/" + ARMED_KEY, 0L) == Long.MAX_VALUE;
	}

	/** Armed and sprint-flying: the boost is running. */
	public static boolean engaged(TonyStarkState state, String suitId, boolean flying, boolean sprinting) {
		return flying && sprinting && armed(state, suitId);
	}

	/** The flight speed multiplier ({@value #SPEED_MULTIPLIER} while engaged, else 1). */
	public static double speedMultiplier(TonyStarkState state, String suitId, boolean flying, boolean sprinting) {
		return engaged(state, suitId, flying, sprinting) ? SPEED_MULTIPLIER : 1.0;
	}

	/** Is {@code player} boosting right now? (either side; reads the synced flags, the worn suit and the sprint flag) */
	public static boolean boosting(Player player) {
		if (player == null) {
			return false;
		}
		TonyStarkState s = player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		return engaged(s, IronManArmor.wornSuitId(player), player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false),
				player.isSprinting());
	}

	// ------------------------------------------------------------------ server

	/** X: arm (true -- the router starts the short cooldown) or switch it off (false: free). */
	static boolean toggle(ServerPlayer p) {
		if (armed(TonyStark.state(p), SUIT)) {
			disarm(p, true);
			return false;
		}
		setArmed(p, true);
		boolean now = IronManFlight.isFlying(p) && p.isSprinting();
		p.displayClientMessage(Component.translatable(now ? "message.projecthero.ironman.flight_boost.on"
				: "message.projecthero.ironman.flight_boost.armed").withStyle(ChatFormatting.AQUA), true);
		IronManSounds.play(p, IronManSounds.REPULSOR_CHARGE, 0.5f, 1.6f);
		if (now) {
			engage(p);
		}
		return true;
	}

	/** Is the boost engaged on the server (armed and running this flight)? */
	public static boolean engagedServer(ServerPlayer p) {
		return ENGAGED.contains(p.getUUID()) && armed(TonyStark.state(p), IronManArmor.wornSuitId(p)) && IronManFlight.isFlying(p)
				&& p.isSprinting();
	}

	/** Every server tick (from the suit ticker, whatever the suit's state): engage / switch off. */
	public static void tick(ServerPlayer p) {
		TonyStarkState s = TonyStark.state(p);
		if (!armed(s, SUIT)) {
			ENGAGED.remove(p.getUUID());
			return;
		}
		String worn = IronManArmor.wornSuitId(p);
		if (!SUIT.equals(worn) || !TonyStark.hasPower(p) || IronManEnergy.brownedOut(p, SUIT) || IronManIntegrity.disabled(p, SUIT)
				|| IronManEnergy.real(SUIT) && IronManEnergy.energy(p, SUIT) <= 0f) {
			disarm(p, SUIT.equals(worn)); // brownout / disabled / suit off: it shuts off
			return;
		}
		boolean sprintFlying = IronManFlight.isFlying(p) && p.isSprinting();
		if (sprintFlying) {
			if (!ENGAGED.contains(p.getUUID())) {
				engage(p);
			}
		} else if (ENGAGED.contains(p.getUUID())) {
			disarm(p, true); // stopped sprint-flying: it switches itself off
		}
	}

	private static void engage(ServerPlayer p) {
		ENGAGED.add(p.getUUID());
		// the speed, the sonic-boom ring, the longer jets and the FOV kick are every client's own (IronManSpaceBoostClient)
		IronManSounds.play(p, IronManSounds.SUPERSONIC, 1.1f, 1.15f);
	}

	/** Switch it off (announce = the action-bar note + power-down sound). */
	public static void disarm(ServerPlayer p, boolean announce) {
		boolean was = armed(TonyStark.state(p), SUIT);
		ENGAGED.remove(p.getUUID());
		if (!was) {
			return;
		}
		setArmed(p, false);
		if (announce) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.flight_boost.off").withStyle(ChatFormatting.GRAY), true);
			IronManSounds.play(p, IronManSounds.POWER_FAIL, 0.35f, 1.5f);
		}
	}

	private static void setArmed(ServerPlayer p, boolean on) {
		TonyStarkState c = TonyStark.state(p).copy();
		if (on) {
			c.abilityReadyAt.put(SUIT + "/" + ARMED_KEY, Long.MAX_VALUE);
		} else {
			c.abilityReadyAt.remove(SUIT + "/" + ARMED_KEY);
		}
		p.setAttached(ModAttachments.TONY_STARK_STATE, c);
	}

	/** Server stopped: drop the scratch set (ServerStateReset). */
	public static void clearSessionState() {
		ENGAGED.clear();
	}
}
