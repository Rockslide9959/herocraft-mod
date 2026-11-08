package com.projecthero.mod.andy;

import java.util.UUID;

import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user): Andy's flight, Blood Propulsion. The whole API the key routing needs:
 * <ul>
 *   <li>{@link #start(ServerPlayer)} -- take off (the hands and feet burst off, blood jets out of the four stumps);</li>
 *   <li>{@link #stop(ServerPlayer)} -- land / let go (the hands and feet grow back);</li>
 *   <li>{@link #isFlying(Player)} -- client-safe;</li>
 *   <li>{@link #setBoost(ServerPlayer, boolean)} -- right click held while flying (from {@code AndyBoostPayload}).</li>
 * </ul>
 * Speeds ({@link #speed}): 25 b/s; 40 b/s boosting, and the body burns away (legs, arms, body: {@link AndyConfig#BURN_TICKS});
 * 32 b/s as just a head. The movement itself is the owner's client's ({@code AndyFlightTravelMixin} moves the local player
 * along the look at exactly {@link #speed} a tick); the server sets the same velocity, keeps the fall distance at nothing and
 * the "flying is not enabled" kick away ({@code AndyFlightKickMixin}).
 *
 * <p>The hands and feet are a look only (the arm and leg parts stay whole); what the boost burns is really broken (kind
 * {@link AndyState#KIND_BURNT}), the stage in progress drawn shortened by {@link AndyState#X_BURN_TICKS}.
 */
public final class AndyFlight {
	private AndyFlight() {
	}

	static void clearSessionState() {
	}

	static void forget(UUID id) {
	}

	/** Flying right now (client-safe). */
	public static boolean isFlying(Player player) {
		AndyState s = Andy.peek(player);
		return s != null && s.hasPower && s.flag(AndyState.FLAG_PROPELLING);
	}

	/** Blocks a tick along the look for this state: 1.6 just a head, 2.0 boosting, else 1.25. */
	public static double speed(AndyState s, float now) {
		if (s.headOnly(now)) {
			return AndyConfig.HEAD_FLIGHT_SPEED;
		}
		return s.flag(AndyState.FLAG_BOOST) ? AndyConfig.BOOST_SPEED : AndyConfig.PROPULSION_SPEED;
	}

	/** Take off (the hold starts). False if he can't right now. */
	public static boolean start(ServerPlayer p) {
		if (!Andy.canAct(p) || isFlying(p)) {
			return false;
		}
		int cd = Andy.cooldownRemaining(p, AndyAbilities.PROPULSION);
		if (cd > 0) {
			Andy.say(p, "message.projecthero.andy.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.andy.ability." + AndyAbilities.PROPULSION),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		AndyState n = Andy.state(p).copy();
		n.setFlag(AndyState.FLAG_PROPELLING, true);
		n.setFlag(AndyState.FLAG_BOOST, false);
		n.holdStart = p.level().getGameTime();
		// a fresh flight: whatever was still growing back from the last one is whole again
		n.setEx(AndyState.X_CUT_REGROW, 0L);
		n.setEx(AndyState.X_BURN_TICKS, 0L);
		n.setEx(AndyState.X_BURN_SEG, 0L);
		Andy.save(p, n);
		// the jets' start / loop / stop are client-side (AndySounds.jet, from the synced flag); the hands and feet bursting off:
		AndySfx.bloodBurst(p.level(), p.position().add(0, 0.6, 0), 1.2f);
		return true;
	}

	/** Let go / land: the cooldown starts and the hands, feet and any half-burnt stage grow back. */
	public static void stop(ServerPlayer p) {
		AndyState s = Andy.state(p);
		if (!s.flag(AndyState.FLAG_PROPELLING)) {
			return;
		}
		long now = p.level().getGameTime();
		AndyState n = s.copy();
		n.setFlag(AndyState.FLAG_PROPELLING, false);
		n.setFlag(AndyState.FLAG_BOOST, false);
		n.setEx(AndyState.X_CUT_REGROW, now);
		n.abilityReadyAt.put(AndyAbilities.PROPULSION, now + AndyConfig.PROPULSION_COOLDOWN);
		Andy.save(p, n);
		p.resetFallDistance();
	}

	/** Right click held / let go while flying. */
	public static void setBoost(ServerPlayer p, boolean on) {
		AndyState s = Andy.state(p);
		if (!s.hasPower || (on && !s.flag(AndyState.FLAG_PROPELLING)) || s.flag(AndyState.FLAG_BOOST) == on) {
			return;
		}
		AndyState n = s.copy();
		n.setFlag(AndyState.FLAG_BOOST, on);
		Andy.save(p, n);

	}

	// ---------------------------------------------------------------- the burn

	/** The parts each burn stage takes: both legs, both arms, the body. */
	public static final int[][] BURN_STAGES = { { AndyState.RIGHT_LEG, AndyState.LEFT_LEG }, { AndyState.RIGHT_ARM, AndyState.LEFT_ARM }, { AndyState.BODY } };

	/** The first burn stage with anything left to burn, or -1 (just a head). */
	public static int burnStage(AndyState s) {
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
	public static float burnt(AndyState s, int part) {
		int k = (int) s.ex(AndyState.X_BURN_SEG);
		long ticks = s.ex(AndyState.X_BURN_TICKS);
		if (ticks <= 0L || k < 0 || k >= BURN_STAGES.length || s.broken(part)) {
			return 0f;
		}
		for (int q : BURN_STAGES[k]) {
			if (q == part) {
				return Math.min(1f, ticks / (float) AndyConfig.BURN_TICKS[k]);
			}
		}
		return 0f;
	}

	/** How long the hands, feet and any half-burnt stage take to grow back after a landing. */
	public static float cutRegrowTicks(AndyState s) {
		float missing = AndyConfig.HAND_ROWS / 12f;
		for (int part = AndyState.BODY; part < AndyState.PARTS; part++) {
			missing = Math.max(missing, burnt(s, part));
		}
		return AndyConfig.cutRegrowTicks(s.regenSpeed, missing);
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		AndyState s = Andy.state(p);
		if (!s.flag(AndyState.FLAG_PROPELLING)) {
			return;
		}
		long now = p.level().getGameTime();
		if (now - s.holdStart > AndyConfig.PROPULSION_MAX || !p.isAlive() || p.isSpectator() || s.broken(AndyState.HEAD)) {
			stop(p);
			return;
		}
		Vec3 v = p.getLookAngle().scale(speed(s, now));
		// the owner's client moves him (AndyFlightTravelMixin); this keeps the server's idea of his velocity in step
		p.setDeltaMovement(v);
		p.resetFallDistance();
		if (now % 4 == 0) {
			p.causeFoodExhaustion(0.4f);
		}
		if (s.flag(AndyState.FLAG_BOOST) && !s.headOnly(now)) {
			burn(p, s, now);
		}
	}

	/** One tick of the boost burn: the current stage burns on; when it is through, its parts break (no lethal damage). */
	private static void burn(ServerPlayer p, AndyState s, long now) {
		int k = burnStage(s);
		if (k < 0) {
			return;
		}
		AndyState n = s.copy();
		long ticks = n.ex(AndyState.X_BURN_SEG) == k ? n.ex(AndyState.X_BURN_TICKS) + 1 : 1;
		n.setEx(AndyState.X_BURN_SEG, k);
		n.setEx(AndyState.X_BURN_TICKS, ticks);
		Andy.save(p, n);
		if (ticks >= AndyConfig.BURN_TICKS[k]) {
			AndyState m = Andy.state(p).copy();
			m.setEx(AndyState.X_BURN_TICKS, 0L);
			m.setEx(AndyState.X_BURN_SEG, Math.min(BURN_STAGES.length - 1, k + 1));
			Andy.save(p, m);
			for (int part : BURN_STAGES[k]) {
				Andy.breakPart(p, part, AndyState.KIND_BURNT);
			}
			AndySfx.spray(p.level(), p.position().add(0, 1, 0)); // the burn loop itself is client-side (AndySounds.burn)
		}
	}
}
