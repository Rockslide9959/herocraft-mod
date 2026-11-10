package com.projecthero.mod.undead;

import java.util.UUID;

import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user): Undead's flight, Blood Propulsion. The whole API the key routing needs:
 * <ul>
 *   <li>{@link #start(ServerPlayer)} -- take off (the hands and feet burst off, blood jets out of the four stumps);</li>
 *   <li>{@link #stop(ServerPlayer)} -- land / let go (the hands and feet grow back);</li>
 *   <li>{@link #isFlying(Player)} -- client-safe;</li>
 *   <li>{@link #setBoost(ServerPlayer, boolean)} -- right click held while flying (from {@code UndeadBoostPayload}).</li>
 * </ul>
 * Speeds ({@link #speed}): 25 b/s; 40 b/s boosting, and the body burns away (legs, arms, body: {@link UndeadConfig#BURN_TICKS});
 * 32 b/s as just a head. The movement itself is the owner's client's ({@code UndeadFlightTravelMixin} moves the local player
 * along the look at exactly {@link #speed} a tick); the server sets the same velocity, keeps the fall distance at nothing and
 * the "flying is not enabled" kick away ({@code UndeadFlightKickMixin}).
 *
 * <p>v0.17.3: the hands and feet are segments of their own ({@link UndeadState#RIGHT_HAND} ...): taking off breaks them (no burst:
 * the jets are the look) and they grow back by the regrowth once he lands. What the boost burns is really broken (kind
 * {@link UndeadState#KIND_BURNT}), the stage in progress drawn shortened by {@link UndeadState#X_BURN_TICKS}; a stage only part
 * burnt when he lands is lost whole (it regrows in about a second).
 */
public final class UndeadFlight {
	private UndeadFlight() {
	}

	static void clearSessionState() {
	}

	static void forget(UUID id) {
	}

	/** Flying right now (client-safe). */
	public static boolean isFlying(Player player) {
		UndeadState s = Undead.peek(player);
		return s != null && s.hasPower && s.flag(UndeadState.FLAG_PROPELLING);
	}

	/** Blocks a tick along the look for this state: 1.6 just a head, 2.0 boosting, else 1.25. */
	public static double speed(UndeadState s, float now) {
		if (s.headOnly(now)) {
			return UndeadConfig.HEAD_FLIGHT_SPEED;
		}
		return s.flag(UndeadState.FLAG_BOOST) ? UndeadConfig.BOOST_SPEED : UndeadConfig.PROPULSION_SPEED;
	}

	/** Take off (the hold starts). False if he can't right now. */
	public static boolean start(ServerPlayer p) {
		if (!Undead.canAct(p) || isFlying(p)) {
			return false;
		}
		int cd = Undead.cooldownRemaining(p, UndeadAbilities.PROPULSION);
		if (cd > 0) {
			Undead.say(p, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + UndeadAbilities.PROPULSION),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadState.FLAG_PROPELLING, true);
		n.setFlag(UndeadState.FLAG_BOOST, false);
		n.holdStart = p.level().getGameTime();
		// a fresh flight: whatever was still growing back from the last one is whole again
		n.setEx(UndeadState.X_CUT_REGROW, 0L);
		n.setEx(UndeadState.X_BURN_TICKS, 0L);
		n.setEx(UndeadState.X_BURN_SEG, 0L);
		Undead.save(p, n);
		for (int end = UndeadState.RIGHT_HAND; end <= UndeadState.LEFT_FOOT; end++) {
			Undead.breakPart(p, end, UndeadState.KIND_SPENT); // the hands and feet burst off (the jets are the look)
		}
		// the jets' start / loop / stop are client-side (UndeadSounds.jet, from the synced flag); the hands and feet bursting off:
		UndeadSfx.bloodBurst(p.level(), p.position().add(0, 0.6, 0), 1.2f);
		return true;
	}

	/** Let go / land: the cooldown starts and the hands, feet and any half-burnt stage grow back. */
	public static void stop(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		if (!s.flag(UndeadState.FLAG_PROPELLING)) {
			return;
		}
		long now = p.level().getGameTime();
		UndeadState n = s.copy();
		n.setFlag(UndeadState.FLAG_PROPELLING, false);
		n.setFlag(UndeadState.FLAG_BOOST, false);
		n.setEx(UndeadState.X_CUT_REGROW, 0L);
		n.abilityReadyAt.put(UndeadAbilities.PROPULSION, now + UndeadConfig.PROPULSION_COOLDOWN);
		// a stage only part burnt is lost whole: it grows back with the hands and feet
		int[] half = new int[UndeadState.PARTS];
		int halves = 0;
		for (int part = UndeadState.BODY; part < UndeadState.PARTS; part++) {
			if (burnt(s, part) > 0f) {
				half[halves++] = part;
			}
		}
		n.setEx(UndeadState.X_BURN_TICKS, 0L);
		n.setEx(UndeadState.X_BURN_SEG, 0L);
		Undead.save(p, n);
		for (int k = 0; k < halves; k++) {
			Undead.breakPart(p, half[k], UndeadState.KIND_BURNT);
		}
		p.resetFallDistance();
	}

	/** Right click held / let go while flying. */
	public static void setBoost(ServerPlayer p, boolean on) {
		UndeadState s = Undead.state(p);
		if (!s.hasPower || (on && !s.flag(UndeadState.FLAG_PROPELLING)) || s.flag(UndeadState.FLAG_BOOST) == on) {
			return;
		}
		UndeadState n = s.copy();
		n.setFlag(UndeadState.FLAG_BOOST, on);
		Undead.save(p, n);

	}

	// ---------------------------------------------------------------- the burn

	/** The parts each burn stage takes: both legs, both arms, the body. */
	public static final int[][] BURN_STAGES = { { UndeadState.RIGHT_LEG, UndeadState.LEFT_LEG }, { UndeadState.RIGHT_ARM, UndeadState.LEFT_ARM }, { UndeadState.BODY } };

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

	/**
	 * How far the current burn stage has eaten {@code part} (0..1): 0 for a part not in it. Read by the renderer (with the
	 * hands / feet cut, whichever is shorter wins).
	 */
	public static float burnt(UndeadState s, int part) {
		int k = (int) s.ex(UndeadState.X_BURN_SEG);
		long ticks = s.ex(UndeadState.X_BURN_TICKS);
		if (ticks <= 0L || k < 0 || k >= BURN_STAGES.length || s.broken(part)) {
			return 0f;
		}
		for (int q : BURN_STAGES[k]) {
			if (q == part) {
				return Math.min(1f, ticks / (float) UndeadConfig.BURN_TICKS[k]);
			}
		}
		return 0f;
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		if (!s.flag(UndeadState.FLAG_PROPELLING)) {
			return;
		}
		long now = p.level().getGameTime();
		if (!p.isAlive() || p.isSpectator() || s.broken(UndeadState.HEAD)) {
			stop(p);
			return;
		}
		Vec3 v = p.getLookAngle().scale(speed(s, now));
		// the owner's client moves him (UndeadFlightTravelMixin); this keeps the server's idea of his velocity in step
		p.setDeltaMovement(v);
		p.resetFallDistance();
		// (v0.17.3, user: flight no longer costs hunger -- it emptied the bar in under a minute)
		if (s.flag(UndeadState.FLAG_BOOST) && !s.headOnly(now)) {
			burn(p, s, now);
		}
	}

	/** One tick of the boost burn: the current stage burns on; when it is through, its parts break (no lethal damage). */
	private static void burn(ServerPlayer p, UndeadState s, long now) {
		int k = burnStage(s);
		if (k < 0) {
			return;
		}
		UndeadState n = s.copy();
		long ticks = n.ex(UndeadState.X_BURN_SEG) == k ? n.ex(UndeadState.X_BURN_TICKS) + 1 : 1;
		n.setEx(UndeadState.X_BURN_SEG, k);
		n.setEx(UndeadState.X_BURN_TICKS, ticks);
		Undead.save(p, n);
		if (ticks >= UndeadConfig.BURN_TICKS[k]) {
			UndeadState m = Undead.state(p).copy();
			m.setEx(UndeadState.X_BURN_TICKS, 0L);
			m.setEx(UndeadState.X_BURN_SEG, Math.min(BURN_STAGES.length - 1, k + 1));
			Undead.save(p, m);
			for (int part : BURN_STAGES[k]) {
				Undead.breakPart(p, part, UndeadState.KIND_BURNT);
			}
			UndeadSfx.spray(p.level(), p.position().add(0, 1, 0)); // the burn loop itself is client-side (UndeadSounds.burn)
		}
	}
}
