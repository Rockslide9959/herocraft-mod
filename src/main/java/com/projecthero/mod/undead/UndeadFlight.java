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
 *
 * <p>v0.17.3 flight speeds + boost burn (user): 10 b/s cruising, 20 sprinting ({@link #bps}). Right click held while flying is
 * the BOOST ({@link #setBoost}, from {@code UndeadBoostPayload}; the client's {@code UndeadBoostInputMixin} keeps that click from
 * using the held item or anything else): 32 b/s along the look while the body burns away -- both legs, then both arms, then the
 * body ({@link #BURN_STAGES}, {@link UndeadEjectConfig#BURN_STAGE_TICKS}), 10 s of boosting in all, added up over every boost of
 * the flight. What burns through is really lost ({@link UndeadState#KIND_BURNT}); the stage in progress is drawn shortened from
 * {@link UndeadState#X_BURN_SEG} / {@link UndeadState#X_BURN_TICKS}. Just a head he flies at 25 b/s, boosting or not. On landing a
 * stage only part burnt is lost whole, and everything burnt grows back by the normal regrowth.
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

	/** Boosting right now: flying with right click held (client-safe, from the synced flag). */
	public static boolean boosting(Player player) {
		UndeadState s = Undead.peek(player);
		return s != null && s.hasPower && s.flag(UndeadState.FLAG_PROPELLING) && s.flag(UndeadState.FLAG_BOOST);
	}

	/**
	 * Blocks a second on the directional flight for this state: just a head 25 (boosting or not), boosting 32, sprinting 20,
	 * else 10. {@code boost}: right click held (the local client passes its own key so the boost starts without a round trip).
	 */
	public static double bps(UndeadState s, float now, boolean sprint, boolean boost) {
		if (s != null && s.headOnly(now)) {
			return UndeadEjectConfig.FLIGHT_HEAD_BPS;
		}
		if (boost) {
			return UndeadEjectConfig.FLIGHT_BOOST_BPS;
		}
		return sprint ? UndeadEjectConfig.FLIGHT_SPRINT_BPS : UndeadEjectConfig.FLIGHT_CRUISE_BPS;
	}

	/** Right click held / let go while flying (from {@code UndeadBoostPayload}; the server re-checks that he flies). */
	public static void setBoost(ServerPlayer p, boolean on) {
		UndeadState s = Undead.state(p);
		if (!s.hasPower || (on && !s.flag(UndeadState.FLAG_PROPELLING)) || s.flag(UndeadState.FLAG_BOOST) == on) {
			return;
		}
		UndeadState n = s.copy();
		n.setFlag(UndeadState.FLAG_BOOST, on);
		Undead.save(p, n);
	}

	// ---------------------------------------------------------------- the boost burn

	/** The parts each burn stage takes: both legs, both arms, the body. */
	public static final int[][] BURN_STAGES = { { UndeadState.RIGHT_LEG, UndeadState.LEFT_LEG }, { UndeadState.RIGHT_ARM, UndeadState.LEFT_ARM },
			{ UndeadState.BODY } };

	/** The first burn stage with anything left to burn, or -1 (just a head). */
	public static int burnStage(UndeadState s) {
		for (int k = 0; k < BURN_STAGES.length; k++) {
			for (int part : BURN_STAGES[k]) {
				if (!s.broken(part)) {
					return k;
				}
			}
		}
		return -1;
	}

	/** Ticks of boosting stage {@code k} takes to burn through. */
	public static int stageTicks(int k) {
		return UndeadEjectConfig.BURN_STAGE_TICKS[Math.max(0, Math.min(UndeadEjectConfig.BURN_STAGE_TICKS.length - 1, k))];
	}

	/**
	 * How far the current burn stage has eaten {@code part} (0..1): 0 for a part not in it (or gone). Read by the renderer and
	 * kept between boosts (a part half burnt stays half burnt until he boosts on or lands).
	 */
	public static float burnt(UndeadState s, int part) {
		if (s == null) {
			return 0f;
		}
		int k = (int) s.ex(UndeadState.X_BURN_SEG);
		long ticks = s.ex(UndeadState.X_BURN_TICKS);
		if (ticks <= 0L || k < 0 || k >= BURN_STAGES.length || s.broken(part)) {
			return 0f;
		}
		for (int q : BURN_STAGES[k]) {
			if (q == part) {
				return Math.min(1f, ticks / (float) stageTicks(k));
			}
		}
		return 0f;
	}

	/** The whole burn, 0 (whole body) .. 1 (just a head): for the HUD. */
	public static float burnProgress(UndeadState s) {
		int k = burnStage(s);
		if (k < 0) {
			return 1f;
		}
		int done = 0;
		for (int q = 0; q < k; q++) {
			done += stageTicks(q);
		}
		long ticks = s.ex(UndeadState.X_BURN_SEG) == k ? Math.min(stageTicks(k), s.ex(UndeadState.X_BURN_TICKS)) : 0L;
		return Math.min(1f, (done + ticks) / (float) UndeadEjectConfig.BURN_TOTAL_TICKS);
	}

	/** One tick of the boost burn: the current stage burns on; when it is through, its parts are lost (no damage). */
	public static void burnTick(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		int k = burnStage(s);
		if (k < 0) {
			return;
		}
		UndeadState n = s.copy();
		long ticks = n.ex(UndeadState.X_BURN_SEG) == k ? n.ex(UndeadState.X_BURN_TICKS) + 1 : 1;
		n.setEx(UndeadState.X_BURN_SEG, k);
		n.setEx(UndeadState.X_BURN_TICKS, ticks);
		Undead.save(p, n);
		if (ticks >= stageTicks(k)) {
			UndeadState m = Undead.state(p).copy();
			m.setEx(UndeadState.X_BURN_TICKS, 0L);
			m.setEx(UndeadState.X_BURN_SEG, Math.min(BURN_STAGES.length - 1, k + 1));
			Undead.save(p, m);
			for (int part : BURN_STAGES[k]) {
				Undead.breakPart(p, part, UndeadState.KIND_BURNT);
			}
			// the last of the stage crumbles to ash: a soft hiss (the burn loop and the embers are client-side)
			p.level().playSound(null, p.getX(), p.getY() + 0.8, p.getZ(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
					net.minecraft.sounds.SoundSource.PLAYERS, 0.18f, 1.6f + p.getRandom().nextFloat() * 0.3f);
		}
	}

	/** Landing: a stage only part burnt is lost whole (it grows back with the rest), and the burn starts over. */
	private static void endBurn(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		if (s.ex(UndeadState.X_BURN_TICKS) <= 0L && s.ex(UndeadState.X_BURN_SEG) == 0L) {
			return;
		}
		int[] half = new int[UndeadState.PARTS];
		int halves = 0;
		for (int part = UndeadState.BODY; part < UndeadState.PARTS; part++) {
			if (burnt(s, part) > 0f) {
				half[halves++] = part;
			}
		}
		UndeadState n = s.copy();
		n.setEx(UndeadState.X_BURN_TICKS, 0L);
		n.setEx(UndeadState.X_BURN_SEG, 0L);
		Undead.save(p, n);
		for (int k = 0; k < halves; k++) {
			Undead.breakPart(p, half[k], UndeadState.KIND_BURNT);
		}
	}

	/** The body is gone some way other than the boost burn (the flight can't go on without it). */
	private static boolean bodyLostOtherwise(UndeadState s) {
		return s.broken(UndeadState.BODY) && s.kind(UndeadState.BODY) != UndeadState.KIND_BURNT;
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
		// a fresh flight burns from the start (whatever an old one burnt is lost or regrowing)
		n.setEx(UndeadState.X_BURN_TICKS, 0L);
		n.setEx(UndeadState.X_BURN_SEG, 0L);
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
		STARTED.remove(p.getUUID());
		endBurn(p);
		UndeadState s = Undead.state(p);
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
		if (!p.isAlive() || p.isSpectator() || p.isPassenger() || p.isFallFlying() || s.broken(UndeadState.HEAD) || bodyLostOtherwise(s)) {
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
		// right click held: the body burns away (just a head: nothing left to burn, he flies at the head's speed)
		if (s.flag(UndeadState.FLAG_BOOST) && !s.headOnly(now)) {
			burnTick(p);
		}
	}
}
