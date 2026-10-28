package com.projecthero.mod.flight.landing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1, explicit user request ("add a superhero landing to green lantern ... and a cool landing animation and pose to
 * all the flight landings that don't have one"): the one shared <b>Hero Landing</b> system. Each power registers a
 * {@link HeroLandingStyle} with a "this player is flying under me" test ({@link #register}); this class does the rest:
 *
 * <ul>
 *   <li><b>Detection</b> (server, every player, every tick): while airborne it remembers the last style that claimed the
 *       player and the peak descent / speed of the last few ticks. Touching the ground within {@link #CLAIM_WINDOW} ticks of
 *       a claim, after at least {@link #MIN_AIR_TICKS} in the air, fast enough -- a descent of {@link #FAST_DESCENT}
 *       blocks/tick, or a dive of {@link #DIVE_SPEED} with at least {@link #DIVE_DESCENT} of it downward -- is a hero
 *       landing. A gentle touchdown just lands. A few fall-immune heroes also land this way off a long drop without flying
 *       ({@link #registerDrop}, {@link #DROP_DESCENT}).</li>
 *   <li><b>No fall damage</b>: a fall within {@link #FALL_SAFE_TICKS} of a claim never hurts (most flights already reset
 *       the fall distance; this covers the tick the flight ends on).</li>
 *   <li><b>The hold</b>: the lander holds the pose for the style's {@code holdTicks}; walking, jumping, riding or water cut
 *       it short after the style's {@code lockTicks}. Start and end go to everyone tracking the lander
 *       ({@link HeroLandingPayload}), so the pose, the first-person dip and the board are seen by all.</li>
 *   <li><b>The effects</b> ({@link HeroLandingFx}): shockwave ring, crack decal (cosmetic only, no block is touched), dust
 *       and debris of the block underneath, the impact sound plus an element layer, and a camera shake scaled by speed.</li>
 * </ul>
 * Never while riding, swimming / in water or lava, on an elytra, or in spectator. Falcon, Iron Man, Super Strength and
 * Spider-Man keep their own landings and do not register here. Static per-player state: cleared by
 * {@code diagnostics.ServerStateReset}, and per player on logout, death and dimension change.
 */
public final class HeroLanding {
	/** A claim (flying under a registered style) counts for this many ticks after it was last seen. */
	public static final int CLAIM_WINDOW = 10;
	/** Ticks airborne before a touchdown can be a hero landing (a hop never is). */
	public static final int MIN_AIR_TICKS = 3;
	/** Downward speed (blocks / tick) that is always a hero landing: 11 blocks/s. */
	public static final double FAST_DESCENT = 0.55;
	/** A diving touchdown: this total speed (18 blocks/s) ... */
	public static final double DIVE_SPEED = 0.9;
	/** ... with at least this much of it downward (5 blocks/s). */
	public static final double DIVE_DESCENT = 0.25;
	/** A drop without flying (fall-immune heroes only): about an 8-block fall. */
	public static final double DROP_DESCENT = 0.95;
	/** Fall damage is cancelled this long after a claim. */
	public static final int FALL_SAFE_TICKS = 12;
	/** Horizontal speed (blocks / tick) during the hold that counts as walking off. */
	public static final double MOVE_CANCEL = 0.1;

	private record Provider(HeroLandingStyle style, Predicate<ServerPlayer> test) {
	}

	private static final List<Provider> FLIGHTS = new ArrayList<>();
	private static final List<Provider> DROPS = new ArrayList<>();

	/** Per-player airborne bookkeeping. */
	static final class Track {
		HeroLandingStyle style = HeroLandingStyle.NONE;
		long claimedAt = Long.MIN_VALUE / 2;
		HeroLandingStyle dropStyle = HeroLandingStyle.NONE;
		int airTicks;
		long lastAirTick = Long.MIN_VALUE;
		double peakDescent;
		double peakSpeed;
		double lastY = Double.NaN;
		boolean wasOnGround = true;
	}

	/** A landing being held. */
	public record Active(HeroLandingStyle style, long start, float strength) {
	}

	private static final Map<UUID, Track> TRACK = new ConcurrentHashMap<>();
	private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

	private HeroLanding() {
	}

	// ---------------------------------------------------------------- registration

	/** {@code style} owns a flight: {@code flying} is true while the player is flying under it. */
	public static void register(HeroLandingStyle style, Predicate<ServerPlayer> flying) {
		FLIGHTS.add(new Provider(style, flying));
	}

	/** A fall-immune power that also hero-lands off a long drop without flying ({@link #DROP_DESCENT}). */
	public static void registerDrop(HeroLandingStyle style, Predicate<ServerPlayer> owns) {
		DROPS.add(new Provider(style, owns));
	}

	public static void initialize() {
		HeroLandingSounds.initialize();
		PayloadTypeRegistry.playS2C().register(HeroLandingPayload.TYPE, HeroLandingPayload.CODEC);
		HeroLandingStyles.registerAll();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				tick(p);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && source.is(DamageTypeTags.IS_FALL) && fallSafe(p)) {
				p.resetFallDistance();
				return false;
			}
			return true;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			forget(oldPlayer);
			forget(newPlayer);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				end(p);
				forget(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			end(player);
			forget(player);
		});
	}

	// ---------------------------------------------------------------- queries

	/** The landing {@code p} is holding, or null. */
	public static @Nullable Active active(Player p) {
		return ACTIVE.get(p.getUUID());
	}

	public static boolean landing(Player p) {
		return ACTIVE.containsKey(p.getUUID());
	}

	/** The style that would claim {@code p} right now (flying under it), or {@link HeroLandingStyle#NONE}. */
	public static HeroLandingStyle claim(ServerPlayer p) {
		for (Provider pr : FLIGHTS) {
			try {
				if (pr.test.test(p)) {
					return pr.style;
				}
			} catch (RuntimeException e) {
				// a power's state mid-change must never break the tick
			}
		}
		return HeroLandingStyle.NONE;
	}

	private static HeroLandingStyle dropClaim(ServerPlayer p) {
		for (Provider pr : DROPS) {
			try {
				if (pr.test.test(p)) {
					return pr.style;
				}
			} catch (RuntimeException e) {
				// as above
			}
		}
		return HeroLandingStyle.NONE;
	}

	/** Was {@code p} flying under a hero style within {@link #FALL_SAFE_TICKS} (or is landing now)? */
	public static boolean fallSafe(ServerPlayer p) {
		if (ACTIVE.containsKey(p.getUUID())) {
			return true;
		}
		Track t = TRACK.get(p.getUUID());
		return t != null && t.style != HeroLandingStyle.NONE && p.level().getGameTime() - t.claimedAt <= FALL_SAFE_TICKS;
	}

	/** Riding, in water / lava, on an elytra, in spectator or dead: never a hero landing. */
	public static boolean excluded(Player p) {
		return p.isPassenger() || p.isInWater() || p.isInLava() || p.isUnderWater() || p.isFallFlying() || p.isSpectator() || !p.isAlive()
				|| p.isSleeping() || p.isSwimming();
	}

	// ---------------------------------------------------------------- the tick

	/** Every player, every tick (also callable directly; repeating it within one tick is harmless). */
	public static void tick(ServerPlayer p) {
		long now = p.level().getGameTime();
		Track t = TRACK.computeIfAbsent(p.getUUID(), k -> new Track());
		Active a = ACTIVE.get(p.getUUID());
		if (a != null) {
			holdTick(p, a, now);
		}
		if (excluded(p)) {
			t.airTicks = 0;
			t.peakDescent = 0;
			t.peakSpeed = 0;
			t.style = HeroLandingStyle.NONE;
			t.wasOnGround = true;
			t.lastY = p.getY();
			return;
		}
		Vec3 known = p.getKnownMovement();
		double dy = Double.isNaN(t.lastY) ? 0.0 : p.getY() - t.lastY;
		t.lastY = p.getY();
		double descent = Math.max(0.0, Math.max(-known.y, -dy));
		double speed = Math.max(known.length(), Math.abs(dy));
		boolean ground = p.onGround();
		if (!ground) {
			HeroLandingStyle s = claim(p);
			if (s != HeroLandingStyle.NONE) {
				t.style = s;
				t.claimedAt = now;
			}
			t.dropStyle = dropClaim(p);
			if (now != t.lastAirTick) {
				t.airTicks++;
				t.lastAirTick = now;
				// a peak that fades: a dive that flattened out a second ago no longer counts
				t.peakDescent = Math.max(descent, t.peakDescent - 0.12);
				t.peakSpeed = Math.max(speed, t.peakSpeed - 0.15);
			} else {
				t.peakDescent = Math.max(descent, t.peakDescent);
				t.peakSpeed = Math.max(speed, t.peakSpeed);
			}
			t.wasOnGround = false;
			return;
		}
		if (!t.wasOnGround) {
			// touched down this tick
			t.peakDescent = Math.max(descent, t.peakDescent);
			t.peakSpeed = Math.max(speed, t.peakSpeed);
			HeroLandingStyle s = t.style != HeroLandingStyle.NONE && now - t.claimedAt <= CLAIM_WINDOW ? t.style : HeroLandingStyle.NONE;
			if (s == HeroLandingStyle.NONE && t.dropStyle != HeroLandingStyle.NONE && t.peakDescent >= DROP_DESCENT) {
				s = t.dropStyle;
			}
			if (s != HeroLandingStyle.NONE && t.airTicks >= MIN_AIR_TICKS && fast(t.peakDescent, t.peakSpeed) && !ACTIVE.containsKey(p.getUUID())) {
				trigger(p, s, strength(t.peakDescent, t.peakSpeed));
			}
		}
		t.wasOnGround = true;
		t.airTicks = 0;
		t.peakDescent = 0;
		t.peakSpeed = 0;
		t.dropStyle = HeroLandingStyle.NONE;
	}

	/** Is a touchdown at this peak descent / speed (blocks / tick) a hero landing? */
	public static boolean fast(double descent, double speed) {
		return descent >= FAST_DESCENT || (speed >= DIVE_SPEED && descent >= DIVE_DESCENT);
	}

	/** 0..1: how hard the landing was (scales the shockwave, the shake and the sound). */
	public static float strength(double descent, double speed) {
		double v = Math.max(descent, speed * 0.7);
		return (float) Math.max(0.15, Math.min(1.0, (v - 0.4) / 1.4));
	}

	private static void holdTick(ServerPlayer p, Active a, long now) {
		long age = now - a.start;
		if (excluded(p)) {
			end(p);
			return;
		}
		if (age >= a.style.holdTicks) {
			end(p);
			return;
		}
		if (a.style == HeroLandingStyle.SILVER_SURFER) {
			HeroLandingFx.skid(p, (int) age);
		}
		if (age < a.style.lockTicks) {
			p.resetFallDistance();
			return;
		}
		Vec3 m = p.getKnownMovement();
		boolean walking = m.horizontalDistance() > MOVE_CANCEL;
		boolean jumped = !p.onGround() || m.y > 0.08;
		if (walking || jumped) {
			end(p);
		}
	}

	/** Starts a hero landing for {@code p} in {@code style} now (the detector's path; also usable by a power directly). */
	public static void trigger(ServerPlayer p, HeroLandingStyle style, float strength) {
		if (style == HeroLandingStyle.NONE || excluded(p)) {
			return;
		}
		float s = Math.max(0.15f, Math.min(1f, strength));
		ACTIVE.put(p.getUUID(), new Active(style, p.level().getGameTime(), s));
		p.resetFallDistance();
		broadcast(p, new HeroLandingPayload(p.getId(), style.id, s));
		HeroLandingFx.play(p, style, s);
	}

	/** Ends {@code p}'s landing hold now (they blend back to standing). */
	public static void end(ServerPlayer p) {
		if (ACTIVE.remove(p.getUUID()) != null) {
			broadcast(p, new HeroLandingPayload(p.getId(), 0, 0f));
		}
	}

	private static void broadcast(ServerPlayer p, HeroLandingPayload payload) {
		if (ServerPlayNetworking.canSend(p, HeroLandingPayload.TYPE)) {
			ServerPlayNetworking.send(p, payload);
		}
		for (ServerPlayer viewer : PlayerLookup.tracking(p)) {
			if (viewer != p && ServerPlayNetworking.canSend(viewer, HeroLandingPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	/** Drops {@code p}'s per-player state (logout, death, dimension change). */
	public static void forget(ServerPlayer p) {
		TRACK.remove(p.getUUID());
		ACTIVE.remove(p.getUUID());
	}

	/** Server stopped. */
	public static void clearSessionState() {
		TRACK.clear();
		ACTIVE.clear();
	}
}
