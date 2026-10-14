package com.projecthero.mod.spider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.spider.data.SpiderMoveState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: swing tricks. Tap Jump within about a second of letting go of a swing (in the air -- not gliding, not hanging,
 * not on a wall) and Spider-Man throws a trick: a front flip (W), a backflip (S), a corkscrew (A), a twist (D) or, with no
 * direction held, any of those or the split at random. Each trick finishing reopens the window, so up to
 * {@link #MAX_PER_AIRTIME} chain off one release; the count resets when he lands, grabs a wall or hangs.
 *
 * <p>The reward is a forward kick of {@code BOOST / n} (10 %, 5 %, 3.3 % for the 1st, 2nd and 3rd trick), never past the
 * swing's top speed. In the Black Suit a trick also feeds the symbiote a little Hunger.
 *
 * <p>The owning client times the tap, picks the trick and applies the kick to its own (client-simulated) motion; the
 * server re-checks every request, keeps the authoritative count and starts the synced animation every viewer plays.
 */
public final class SpiderTricks {
	public static final int FRONT_FLIP = 0;
	public static final int BACKFLIP = 1;
	public static final int CORKSCREW = 2;
	public static final int SPLIT = 3;
	public static final int TWIST = 4;
	public static final int COUNT = 5;

	/** Tricks that count (and pay) per airtime. */
	public static final int MAX_PER_AIRTIME = 3;
	/** After a release (or a trick finishing) the tap must come within this many ticks. */
	public static final int WINDOW_TICKS = 20;
	/** Jump released within this many ticks of being pressed is a tap (held longer it belongs to gliding). */
	public static final int TAP_TICKS = 5;
	/** Length of every trick animation. */
	public static final int LENGTH = 12;
	/** A new trick may start this many ticks before the last one has finished. */
	public static final int CHAIN_EARLY = 2;
	/** The first trick's forward kick (fraction of the current horizontal speed). */
	public static final double BOOST = 0.10;
	/** Black Suit: Hunger fed per trick. */
	public static final float HUNGER = 2.0f;
	/** Server slack on the window for packet latency. */
	private static final int LATENCY_SLACK = 8;

	/** Per player: the server's combo bookkeeping. */
	private static final Map<UUID, Combo> COMBOS = new ConcurrentHashMap<>();

	private static final class Combo {
		boolean wasSwinging;
		long releaseAt = Long.MIN_VALUE / 2;
		long lastTrickAt = Long.MIN_VALUE / 2;
		int count;
	}

	private SpiderTricks() {
	}

	// ------------------------------------------------------------------ pure rules (both sides, gametested)

	/** The {@link SpiderAnim} id of a trick. */
	public static int anim(int kind) {
		return SpiderAnim.TRICK_FRONT_FLIP + Math.floorMod(kind, COUNT);
	}

	/** The trick for the held direction keys; with none (or opposing ones) {@code random} (any int) picks one. */
	public static int kindForKeys(boolean forward, boolean back, boolean left, boolean right, int random) {
		if (forward && !back) {
			return FRONT_FLIP;
		}
		if (back && !forward) {
			return BACKFLIP;
		}
		if (left && !right) {
			return CORKSCREW;
		}
		if (right && !left) {
			return TWIST;
		}
		return Math.floorMod(random, COUNT);
	}

	/** Speed multiplier for the {@code n}-th trick of an airtime (1-based): 1 + BOOST / n, nothing past the cap. */
	public static double rewardFactor(int n) {
		return n >= 1 && n <= MAX_PER_AIRTIME ? 1.0 + BOOST / n : 1.0;
	}

	/**
	 * The velocity after the {@code n}-th trick: the horizontal part scaled by {@link #rewardFactor}, the whole never
	 * faster than {@code maxSpeed} -- and never slower than it was (a trick at or over the cap changes nothing).
	 */
	public static Vec3 reward(Vec3 v, int n, double maxSpeed) {
		double f = rewardFactor(n);
		double before = v.length();
		if (f <= 1.0 || before >= maxSpeed) {
			return v;
		}
		Vec3 out = new Vec3(v.x * f, v.y, v.z * f);
		double len = out.length();
		if (len > maxSpeed) {
			out = out.scale(maxSpeed / len);
		}
		return out;
	}

	/**
	 * Whether a trick may start: fewer than {@link #MAX_PER_AIRTIME} so far, the previous one (nearly) finished and the
	 * tap inside the window opened by the release / the last trick's end ({@code slack} extra ticks for latency).
	 */
	public static boolean allowed(int count, long now, long releaseAt, long lastTrickAt, int slack) {
		if (count >= MAX_PER_AIRTIME) {
			return false;
		}
		long trickEnd = lastTrickAt + LENGTH;
		if (now < trickEnd - CHAIN_EARLY) {
			return false; // still mid-trick
		}
		long open = Math.max(releaseAt, trickEnd);
		return now >= releaseAt && now - open <= WINDOW_TICKS + slack;
	}

	// ------------------------------------------------------------------ server

	/** Every tick, for every Spider-Man: note swing releases, reset the count once he is back on something. */
	static void serverTick(ServerPlayer player) {
		boolean swinging = SpiderSwing.isSwinging(player);
		Combo c = COMBOS.get(player.getUUID());
		boolean grounded = player.onGround() || player.isInWater() || SpiderClimb.attached(player)
				|| SpiderMoves.state(player).has(SpiderMoveState.FLAG_HANGING);
		if (c == null) {
			if (!swinging) {
				return;
			}
			c = new Combo();
			COMBOS.put(player.getUUID(), c);
		}
		long now = player.level().getGameTime();
		if (c.wasSwinging && !swinging && !grounded) {
			c.releaseAt = now;
		}
		c.wasSwinging = swinging;
		if (grounded) {
			c.count = 0;
			if (!swinging) {
				COMBOS.remove(player.getUUID());
			}
		}
	}

	/** The client asks for a trick. Returns the combo count it became (1..3), or 0 when refused. */
	public static int tryTrick(ServerPlayer player, int kind) {
		if (!SpiderMan.hasPower(player) || kind < 0 || kind >= COUNT || player.onGround() || player.isInWater()
				|| player.isFallFlying() || player.getAbilities().flying || player.isPassenger()
				|| SpiderSwing.isSwinging(player) || SpiderClimb.attached(player)
				|| SpiderMoves.state(player).has(SpiderMoveState.FLAG_HANGING)) {
			return 0;
		}
		Combo c = COMBOS.get(player.getUUID());
		long now = player.level().getGameTime();
		if (c == null || !allowed(c.count, now, c.releaseAt, c.lastTrickAt, LATENCY_SLACK)) {
			return 0;
		}
		c.count++;
		c.lastTrickAt = now;
		SpiderMoves.anim(player, anim(kind));
		if (com.projecthero.mod.symbiote.SymbioteHunger.blackSuit(player)) {
			com.projecthero.mod.symbiote.SymbioteHunger.add(player, HUNGER);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
				player.getSoundSource(), 0.35f, 1.5f + 0.15f * c.count);
		player.resetFallDistance();
		return c.count;
	}

	/** The server's count this airtime (tests / debugging). */
	public static int count(ServerPlayer player) {
		Combo c = COMBOS.get(player.getUUID());
		return c == null ? 0 : c.count;
	}

	/** Gametests: pretend a swing was just let go of. */
	public static void markReleased(ServerPlayer player) {
		Combo c = COMBOS.computeIfAbsent(player.getUUID(), k -> new Combo());
		c.releaseAt = player.level().getGameTime();
		c.wasSwinging = false;
	}

	static void remove(UUID id) {
		COMBOS.remove(id);
	}

	static void clearSessionState() {
		COMBOS.clear();
	}
}
