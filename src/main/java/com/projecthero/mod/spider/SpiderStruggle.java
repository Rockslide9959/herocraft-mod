package com.projecthero.mod.spider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.network.SpiderStruggleSyncPayload;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): "if players get trapped in a web coccoon give them a minigame to break out, dont make it too easy tho they
 * need to struggle to break free".
 *
 * <p>A cocooned PLAYER ({@link SpiderWrap}) mashes their two strafe keys -- A and D by default -- ALTERNATELY. Every press of
 * the other key than last time adds {@link SpiderManConfig.Struggle#gainPerPress}; the same key twice is a slip that adds
 * nothing and costs {@link SpiderManConfig.Struggle#slipPenalty}. Progress drains every tick (a flat part, a part that
 * grows with the progress, and a fast drain once they stop), so only quick, clean alternation ever fills it: about 8
 * presses a second breaks out in 6 s, 10 a second in under 4, 6 a second or slower never does. At 1 the cocoon tears open
 * ({@link SpiderWrap#release} with effects) and any web still holding them lets go too ({@code WebStructures.tearFree}).
 *
 * <p>The client only reports presses ({@code SpiderStrugglePayload}); the server counts them, ignores more than
 * {@link SpiderManConfig.Struggle#maxPressesPerSecond} a second, and sends the progress back for the bar
 * ({@link SpiderStruggleSyncPayload}). Mobs never struggle -- their cocoons are unchanged. Spider-Man's sneak +
 * right-click still frees anyone at once.
 */
public final class SpiderStruggle {
	public static final int LEFT = 0;
	public static final int RIGHT = 1;
	/** Presses that may arrive together (one network batch) before the rate limit bites. */
	private static final float BURST = 3.0f;

	private static final class State {
		final ServerPlayer player;
		float progress;
		int lastKey = -1;
		long lastGoodTick = Long.MIN_VALUE / 2;
		/** Rate limit: a token bucket refilled at maxPressesPerSecond, at most BURST deep. */
		float tokens = BURST;
		long lastRefill = Long.MIN_VALUE / 2;
		float sent;

		State(ServerPlayer player) {
			this.player = player;
		}
	}

	/** player -> their struggle (server; only while it has something to drain). */
	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private SpiderStruggle() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(SpiderStruggle::tick);
	}

	public static void clearSessionState() {
		STATES.clear();
	}

	private static SpiderManConfig.Struggle cfg() {
		return SpiderManConfig.get().struggle;
	}

	/** The struggle so far, 0..1 (0 when none). */
	public static float progress(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s == null || s.player != player ? 0.0f : s.progress;
	}

	/**
	 * One strafe-key press from a cocooned player ({@link #LEFT} / {@link #RIGHT}). Returns true when this press broke them
	 * out. Ignored when they are not cocooned, the minigame is off, or they are over the press rate.
	 */
	public static boolean press(ServerPlayer player, int key) {
		SpiderManConfig.Struggle c = cfg();
		if (!c.enabled || (key != LEFT && key != RIGHT) || !SpiderWrap.isWrapped(player) || !player.isAlive()) {
			return false;
		}
		State s = STATES.get(player.getUUID());
		if (s == null || s.player != player) {
			s = new State(player);
			STATES.put(player.getUUID(), s);
		}
		long now = player.level().getGameTime();
		long since = Math.min(100L, Math.max(0L, now - s.lastRefill));
		s.lastRefill = now;
		s.tokens = Math.min(BURST, s.tokens + since * Math.max(1, c.maxPressesPerSecond) / 20.0f);
		if (s.tokens < 1.0f) {
			return false; // faster than any hand: ignored
		}
		s.tokens -= 1.0f;
		int good;
		if (key == s.lastKey) {
			s.progress = Math.max(0.0f, s.progress - c.slipPenalty);
			good = -1;
		} else {
			s.progress += c.gainPerPress;
			s.lastGoodTick = now;
			good = 1;
		}
		s.lastKey = key;
		if (s.progress >= 1.0f) {
			breakOut(player);
			return true;
		}
		sync(s, good);
		return false;
	}

	/** Full bar: the cocoon tears open with a burst, and any web still holding them lets go. */
	private static void breakOut(ServerPlayer player) {
		STATES.remove(player.getUUID());
		SpiderWrap.release(player, true);
		com.projecthero.mod.spider.web.WebStructures.tearFree(player);
		if (player.level() instanceof ServerLevel level) {
			Vec3 c = player.position().add(0, player.getBbHeight() * 0.5, 0);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y, c.z, 40, 0.5, 0.7, 0.5, 0.18);
			level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 8, 0.4, 0.6, 0.4, 0.04);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
		}
		send(player, 0.0f, 2); // 2 = broke out
	}

	private static void tick(MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}
		SpiderManConfig.Struggle c = cfg();
		STATES.entrySet().removeIf(en -> {
			State s = en.getValue();
			ServerPlayer p = s.player;
			if (p.isRemoved() || !p.isAlive() || !SpiderWrap.isWrapped(p)) {
				if (!p.isRemoved()) {
					send(p, 0.0f, 0); // freed some other way: the bar goes
				}
				return true;
			}
			long now = p.level().getGameTime();
			float drain = c.decayPerTick + c.decayFractionPerTick * s.progress;
			if (now - s.lastGoodTick > c.idleGraceTicks) {
				drain += c.idleDecayPerTick;
			}
			s.progress = Math.max(0.0f, s.progress - drain);
			if (s.progress <= 0.0f && now - s.lastGoodTick > c.idleGraceTicks) {
				send(p, 0.0f, 0);
				return true; // drained: nothing left to track until the next press
			}
			if (now % 2L == 0L) {
				sync(s, 0);
			}
			return false;
		});
	}

	private static void sync(State s, int good) {
		if (good == 0 && Math.abs(s.progress - s.sent) < 0.004f) {
			return;
		}
		s.sent = s.progress;
		send(s.player, s.progress, good);
	}

	private static void send(ServerPlayer p, float progress, int good) {
		if (ServerPlayNetworking.canSend(p, SpiderStruggleSyncPayload.TYPE)) {
			ServerPlayNetworking.send(p, new SpiderStruggleSyncPayload(progress, good));
		}
	}
}
