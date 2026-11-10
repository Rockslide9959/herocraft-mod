package com.projecthero.mod.undead;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "when player double presses space bar they eject their hands and half their forearm aswell as their feet and
 * half their calf as blood starts spraying out ... allowing them to fly ... the same directional flight as the other flight
 * powers"): Undead's flight. Double-tap jump in the air takes off ({@link #toggle}, from {@code UndeadFlightPayload}): the
 * hands and feet blow off with half the forearms and calves ({@link UndeadEject}), blood jets stream out of the four stumps
 * (client-side, from {@link UndeadState#FLAG_PROPELLING}: {@code UndeadJets}) and he flies on the shared directional flight
 * ({@code client.flight.DirectionalFlight}: {@link UndeadEjectConfig#FLIGHT_CRUISE_BPS} cruising,
 * {@link UndeadEjectConfig#FLIGHT_SPRINT_BPS} sprinting, a dead hover with no input). Like the Saiyan's, vanilla
 * {@code mayfly} / {@code flying} are granted so nobody is kicked for floating. Landing or a second double-tap ends it; then the
 * ends grow back by the normal regrowth. No cost, no time limit.
 */
public final class UndeadFlight {
	public static final String FLIGHT = "flight";

	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();
	/** Players this flight gave vanilla flight to (taken back when it ends, however it ends). */
	private static final Set<UUID> GRANTED = ConcurrentHashMap.newKeySet();
	/** The last toggle (a double-tap is one toggle; the client's own drop of vanilla flight may race it). */
	private static final Map<UUID, Long> LAST_TOGGLE = new ConcurrentHashMap<>();

	private UndeadFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
		GRANTED.clear();
		LAST_TOGGLE.clear();
	}

	static void forget(UUID id) {
		STARTED.remove(id);
		GRANTED.remove(id);
		LAST_TOGGLE.remove(id);
	}

	/** Flying right now (client-safe). */
	public static boolean isFlying(Player player) {
		UndeadState s = Undead.peek(player);
		return s != null && s.hasPower && s.flag(UndeadState.FLAG_PROPELLING);
	}

	/** Double-tap jump in the air: take off, or drop out of the flight. */
	public static void toggle(ServerPlayer p) {
		long now = p.level().getGameTime();
		Long last = LAST_TOGGLE.get(p.getUUID());
		if (last != null && now - last < UndeadEjectConfig.FLIGHT_TOGGLE_COOLDOWN && now >= last) {
			return;
		}
		LAST_TOGGLE.put(p.getUUID(), now);
		if (isFlying(p)) {
			stop(p);
		} else {
			start(p);
		}
	}

	/** Take off. False if he can't right now. */
	public static boolean start(ServerPlayer p) {
		if (!Undead.canAct(p) || isFlying(p) || p.isPassenger() || p.isFallFlying() || p.isSpectator()) {
			return false;
		}
		UndeadState s = Undead.state(p);
		if (s.broken(UndeadState.HEAD) || s.broken(UndeadState.BODY)) {
			return false;
		}
		UndeadState n = s.copy();
		n.setFlag(UndeadState.FLAG_PROPELLING, true);
		n.setFlag(UndeadState.FLAG_BOOST, false);
		n.holdStart = p.level().getGameTime();
		Undead.save(p, n);
		UndeadEject.hold(p, UndeadEject.Hold.FLIGHT);
		UndeadEject.eject(p, UndeadEject.HANDS | UndeadEject.FEET, true);
		STARTED.put(p.getUUID(), p.level().getGameTime());
		grant(p);
		p.resetFallDistance();
		// a little hop up off the take-off as the stumps erupt
		Vec3 v = p.getDeltaMovement();
		com.projecthero.mod.hero.power.AbilityHelpers.launchSelf(p, new Vec3(v.x, Math.max(v.y, 0.25), v.z));
		// the jets' start / loop / stop are client-side (UndeadSounds.jet, from the synced flag)
		UndeadSfx.bloodBurst(p.level(), p.position().add(0, 0.6, 0), 0.6f);
		return true;
	}

	/** Land / drop out: the ends grow back by the regrowth from here. */
	public static void stop(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		STARTED.remove(p.getUUID());
		UndeadEject.release(p, UndeadEject.Hold.FLIGHT);
		if (s.flag(UndeadState.FLAG_PROPELLING) || s.flag(UndeadState.FLAG_BOOST)) {
			UndeadState n = s.copy();
			n.setFlag(UndeadState.FLAG_PROPELLING, false);
			n.setFlag(UndeadState.FLAG_BOOST, false);
			Undead.save(p, n);
		}
		ungrant(p);
		p.resetFallDistance();
	}

	private static void grant(ServerPlayer p) {
		if (!p.getAbilities().instabuild && !p.isSpectator()) {
			GRANTED.add(p.getUUID());
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
	}

	private static void ungrant(ServerPlayer p) {
		if (GRANTED.remove(p.getUUID()) && !p.getAbilities().instabuild && !p.isSpectator() && !anyOtherFlightWants(p)) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
	}

	private static boolean anyOtherFlightWants(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false);
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		if (!s.flag(UndeadState.FLAG_PROPELLING)) {
			if (GRANTED.contains(p.getUUID()) || UndeadEject.holding(p, UndeadEject.Hold.FLIGHT)) {
				stop(p); // the flag went some other way (a shatter, a clear): give vanilla flight back and let the ends regrow
			}
			return;
		}
		long now = p.level().getGameTime();
		if (!p.isAlive() || p.isSpectator() || p.isPassenger() || p.isFallFlying() || s.broken(UndeadState.HEAD) || s.broken(UndeadState.BODY)) {
			stop(p);
			return;
		}
		Long start = STARTED.computeIfAbsent(p.getUUID(), k -> now);
		if (com.projecthero.mod.flight.FlightLanding.landed(p, start)) {
			stop(p);
			return;
		}
		if (!p.getAbilities().flying || !p.getAbilities().mayfly) {
			if (now - start >= UndeadEjectConfig.FLIGHT_LIFTOFF_GRACE && !p.getAbilities().flying && p.getAbilities().mayfly) {
				// the client dropped vanilla flight on its own (its own double-tap): follow it
				stop(p);
				LAST_TOGGLE.put(p.getUUID(), now);
				return;
			}
			grant(p);
		}
		UndeadEject.hold(p, UndeadEject.Hold.FLIGHT);
		p.resetFallDistance();
	}
}
