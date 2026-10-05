package com.projecthero.mod.ironman;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.server.level.ServerPlayer;

/**
 * Iron Man repulsor flight. Same input as Thor's flight -- a double-tap of the vanilla jump key --
 * but only while wearing a valid, powered Iron Man suit with boots, and it drains suit energy rather
 * than Storm Energy. Ends on landing or at zero energy. Server-authoritative; the client only
 * requests the toggle.
 */
public final class IronManFlight {
	private IronManFlight() {
	}

	public static boolean isFlying(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false);
	}

	/** Called on the double-tap-jump request. Validates suit + power + energy before taking off. */
	public static void toggle(ServerPlayer player) {
		if (isFlying(player)) {
			// landing early cancels a Mark 1 timed burst too -- and puts it on its 13 s cooldown
			com.projecthero.mod.ironman.ability.IronManAbilities.endTimedFlight(player, IronManArmor.wornSuitId(player));
			setFlying(player, false);
			return;
		}
		if (!IronManArmor.canOperate(player)) {
			return;
		}
		String suitId = IronManArmor.wornSuitId(player);
		if (suitId == null || !IronManArmor.isPieceWorn(player, net.minecraft.world.entity.EquipmentSlot.FEET, suitId)) {
			return;
		}
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit != null && !suit.manualFlight()) {
			// Mark 1 ("changes 13"): no double-tap-jump repulsor flight -- it flies only via its own
			// timed-flight ability, which calls setFlying() directly and bypasses this path.
			player.displayClientMessage(net.minecraft.network.chat.Component
					.translatable("message.projecthero.ironman.no_manual_flight"), true);
			return;
		}
		if (suit != null && suit.altitudeCeiling() > 0.0 && player.getY() >= suit.altitudeCeiling()) {
			return; // Mark 2's altitude ceiling -- systems frozen, no taking off from up here either
		}
		if (!IronManEnergy.has(player, suitId, 1.0f)) {
			player.displayClientMessage(net.minecraft.network.chat.Component
					.translatable("message.projecthero.ironman.no_energy"), true);
			return;
		}
		setFlying(player, true);
	}

	public static void setFlying(ServerPlayer player, boolean flying) {
		player.setAttached(ModAttachments.IRON_MAN_FLYING, flying);
		if (player.getAbilities().instabuild) {
			return;
		}
		player.getAbilities().mayfly = flying;
		player.getAbilities().flying = flying;
		player.onUpdateAbilities();
		player.resetFallDistance();
		// v0.14.21: no server sound here any more -- every client plays the take-off burst / landing / power-down itself
		// (client.IronManFlightFxClient) off the synced IRON_MAN_FLYING flag, so it would only double up.
	}

	/** Per-tick while flying: drain energy and end conditions (the thruster FX are client-side since v0.14.21). */
	public static void tick(ServerPlayer player) {
		if (!isFlying(player)) {
			return;
		}
		if (player.getAbilities().instabuild) {
			setFlying(player, false);
			return;
		}
		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		if (suit == null || !IronManArmor.canOperate(player)
				|| !IronManArmor.isPieceWorn(player, net.minecraft.world.entity.EquipmentSlot.FEET, suitId)) {
			setFlying(player, false);
			return;
		}
		// Mark 1's "20 seconds of flight" (X) is paid for up front (an activation cost) and guaranteed to
		// stay airborne for its whole duration regardless of energy -- no landing on ground contact (so
		// it can launch you straight up off the floor, like Pyrokinesis/Geokinesis timed self-flight),
		// and it force-lands the instant the timer runs out. v0.11.12, explicit user request: it now ALSO
		// drains TIMED_FLIGHT_DRAIN_PER_SECOND on top of that activation cost -- unlike the guarantee
		// above, running dry mid-burst does force an early landing (see below), it just never cuts the
		// burst short from ground contact. This check has to run BEFORE the ordinary "touched the ground
		// = land" rule, otherwise activating it while standing on the ground ended it again the very next
		// tick.
		// "changes 17": a supersonic burst ends after 20 s (or an early re-press) -> start its cooldown.
		long supersonicUntil = TonyStark.state(player).supersonicUntil;
		if (supersonicUntil != 0L && player.level().getGameTime() >= supersonicUntil) {
			com.projecthero.mod.ironman.ability.IronManAbilities.endSupersonic(player, suitId);
		}
		double supersonicCap = com.projecthero.mod.ironman.ability.IronManAbilities.supersonicSpeedCap(player);
		boolean supersonic = supersonicCap > 0.0;

		long timedFlightUntil = TonyStark.state(player).timedFlightUntil;
		boolean timedBurst = timedFlightUntil > 0L;
		if (timedBurst) {
			if (player.level().getGameTime() >= timedFlightUntil) {
				com.projecthero.mod.ironman.ability.IronManAbilities.endTimedFlight(player, suitId);
				setFlying(player, false);
				return;
			}
			if (!IronManEnergy.spend(player, suitId,
					com.projecthero.mod.ironman.ability.IronManAbilities.TIMED_FLIGHT_DRAIN_PER_SECOND / 20f)) {
				com.projecthero.mod.ironman.ability.IronManAbilities.endTimedFlight(player, suitId);
				setFlying(player, false);
				player.displayClientMessage(net.minecraft.network.chat.Component
						.translatable("message.projecthero.ironman.no_energy"), true);
				return;
			}
		} else {
			if (player.onGround()) {
				setFlying(player, false);
				return;
			}
			// "changes 18": tiered flight energy cost -- hover 10/s, walk-flight 20/s, sprint-flight 30/s,
			// supersonic 45/s -- each scaled by this mark's flightDrainMultiplier.
			if (!IronManEnergy.spend(player, suitId, flightCostPerTick(player, suit, supersonic))) {
				if (supersonic) {
					com.projecthero.mod.ironman.ability.IronManAbilities.endSupersonic(player, suitId);
				}
				setFlying(player, false);
				player.displayClientMessage(net.minecraft.network.chat.Component
						.translatable("message.projecthero.ironman.no_energy"), true);
				return;
			}
		}
		player.getAbilities().flying = true;
		// v0.14.16: the movement itself -- the mark's speed / pick-up, the "changes 16" speed ceiling and the supersonic
		// drive along the look -- is the client's directional flight now (client.flight.DirectionalFlight, numbers in
		// flight.DirectionalFlightModel#ironMan), W / S forward / back along the look like every other flight. The old
		// server-side forward assist and clamp pushed motion packets every tick, which would only fight it.
		// v0.14.21 flight revamp: no server particles. The old feet-centre FLAME / END_ROD spam (and the supersonic CLOUD
		// puff) is replaced by client-side thruster jets at the real palm / boot positions, the vapour cone and the
		// shockwave, drawn by every client for every flier it can see (client.IronManFlightFxClient, numbers in
		// IronManFlightLook) -- they track the rendered limbs, which server particles never could.
	}

	// "changes 18": tiered per-second flight energy costs (converted to per-tick), before the per-mark
	// flightDrainMultiplier. Hovering = holding position with no movement input; walk-flight = moving;
	// sprint-flight = sprinting; supersonic = the Mark 7 burst.
	private static final float HOVER_COST_PER_TICK = 10.0f / 20f;
	private static final float WALK_FLIGHT_COST_PER_TICK = 20.0f / 20f;
	private static final float SPRINT_FLIGHT_COST_PER_TICK = 30.0f / 20f;
	private static final float SUPERSONIC_COST_PER_TICK = 45.0f / 20f;

	private static float flightCostPerTick(ServerPlayer player, IronManSuit suit, boolean supersonic) {
		float base;
		if (supersonic) {
			base = SUPERSONIC_COST_PER_TICK;
		} else if (player.isSprinting() && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()
				instanceof com.projecthero.mod.ironman.item.IronManArmorItem) { // v0.14.26: boots alone never sprint-fly
			base = SPRINT_FLIGHT_COST_PER_TICK;
		} else if (player.zza != 0f || player.xxa != 0f) {
			base = WALK_FLIGHT_COST_PER_TICK;
		} else {
			base = HOVER_COST_PER_TICK;
		}
		return base * suit.flightDrainMultiplier();
	}

	public static void clearStale(ServerPlayer player) {
		if (isFlying(player) || player.getAbilities().instabuild || !player.getAbilities().mayfly) {
			return;
		}
		// a leftover mayfly with our flag off (relog mid-flight) -- resync, but only if no other
		// flight system (Thor / experimental) is currently the owner of that ability bit.
		if (!player.getAttachedOrElse(ModAttachments.FLYING, false)
				&& !player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
	}
}
