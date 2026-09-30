package com.projecthero.mod.hero.power.p04;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.network.TimeSlowStatePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Super Speed Z -- Time Slow. v0.14.7: <b>game-wide</b>. For 45 s everything in the game except the caster runs at 5%
 * -- every entity and player, block entities, scheduled and random block ticks, fluids, redstone, the day / night
 * cycle, weather, particles, clouds -- built on vanilla's own tick-rate system ({@code /tick rate}): the server's rate
 * drops to {@link #SLOW_RATE} (1 tick a second instead of 20) and vanilla syncs it to every client, which then runs
 * its own simulation, animations and interpolation at that rate.
 *
 * <p>The caster is the one exception, and keeps living at 20 ticks a second:
 * <ul>
 *   <li>Server: between the slowed ticks, {@link #pulse} (driven from {@code MinecraftServer#pollTask}, which the
 *       server thread spins on while it waits for the next tick) gives the caster the 19 ticks a second the loop no
 *       longer runs -- their connection tick (movement, physics, food, attack cooldown), their entity tick (mining,
 *       i-frames, carried passengers) and every per-player system of the mod ({@code ProjectHeroMod#tickPlayerSystems}:
 *       ability ticks, passives ...). Their cooldowns, running Super Speed timers and move animation clocks are
 *       absolute game times, so each extra tick also pulls those in by one tick.</li>
 *   <li>Client: the caster's own client keeps its 20 tick/s timer (the rest of the world is stepped once every 20
 *       of its ticks -- see {@code SuperSpeedClientV0145} / {@code TimeSlowClient}).</li>
 *   <li>Damage: slowed victims take hits as usual -- damage, death, loot and XP land at once -- and a victim the
 *       caster hit at least {@link #CASTER_HIT_COOLDOWN} caster ticks ago has its (slowed) hit-invulnerability
 *       window cleared, so the caster can keep hitting at the normal cadence ({@link #onIncomingDamage}).</li>
 * </ul>
 *
 * <p>Only one Time Slow runs at a time (a second speedster's Z is refused). It ends when Z is pressed again, after 45
 * of the caster's seconds, or when the caster dies, logs out or loses the power; the rate that was there before is
 * always put back (also on server stop, and on a player join if a stale slowed rate is ever found with no caster).
 * Press Z again to end it early; the 150 s cooldown starts when it ends.
 */
public final class SuperSpeedTimeSlow {
	public static final int DURATION_TICKS = 45 * 20; // v0.14.7: 45 s (was 30)
	public static final int COOLDOWN_TICKS = 150 * 20;
	/** The world's tick rate while slowed: 1 tick a second, 5% of 20. */
	public static final float SLOW_RATE = 1.0f;
	/** 20 ticks a second / {@link #SLOW_RATE}. */
	public static final int TICK_DIVISOR = 20;
	/** HUD countdown resource (the caster's own ticks left). */
	public static final String LEFT = "time_slow_ticks";
	/** A victim the caster hit this many caster ticks ago can take a full hit from them again (vanilla: 10). */
	public static final int CASTER_HIT_COOLDOWN = 10;
	private static final long PULSE_NANOS = 50_000_000L;

	private static ServerPlayer caster;
	private static float savedRate = 20.0f;
	/** True between our setTickRate(SLOW_RATE) and the restore, so a stale slowed rate can be spotted. */
	private static boolean weSlowed;
	private static int left;
	/** Ticks the caster has lived through since the slow began (the slowed ticks plus the extra ones). */
	private static long casterTicks;
	private static int extraThisTick;
	private static long nextPulse;
	private static boolean inServerTick;
	private static boolean pulsing;
	/** Victim entity id -> {@link #casterTicks} when the caster last hit it. */
	private static final Map<Integer, Long> LAST_CASTER_HIT = new HashMap<>();

	private SuperSpeedTimeSlow() {
	}

	public static boolean isCasting(ServerPlayer p) {
		return caster != null && caster.getUUID().equals(p.getUUID());
	}

	public static boolean anyActive() {
		return caster != null;
	}

	/** Whether {@code e} is the running Time Slow's caster (server side). */
	public static boolean isCaster(Entity e) {
		return caster != null && e != null && caster.getUUID().equals(e.getUUID());
	}

	public static void start(ServerPlayer p) {
		MinecraftServer server = p.getServer();
		if (server == null || isCasting(p)) {
			return;
		}
		if (caster != null) {
			p.displayClientMessage(Component.translatable("message.projecthero.speed.time_taken"), true);
			return;
		}
		float rate = server.tickRateManager().tickrate();
		savedRate = rate > SLOW_RATE ? rate : 20.0f;
		caster = p;
		left = DURATION_TICKS;
		casterTicks = 0;
		extraThisTick = 0;
		LAST_CASTER_HIT.clear();
		nextPulse = System.nanoTime() + PULSE_NANOS;
		server.tickRateManager().setTickRate(SLOW_RATE);
		weSlowed = true;
		BatchA.set(p, SuperSpeedHandlers.KEY, LEFT, DURATION_TICKS, 1e9f);
		BatchA.play(p, SuperSpeedHandlers.KEY, "power_up", 16);
		AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f);
		broadcast(server, new TimeSlowStatePayload(true, p.getUUID()));
	}

	/** Ends {@code p}'s Time Slow (no-op if they are not the caster); {@code cooldown} starts the 150 s cooldown. */
	public static void end(ServerPlayer p, boolean cooldown) {
		if (isCasting(p)) {
			finish(cooldown);
		}
	}

	private static void finish(boolean cooldown) {
		ServerPlayer p = caster;
		caster = null;
		LAST_CASTER_HIT.clear();
		MinecraftServer server = p.getServer();
		if (server != null) {
			restoreRate(server);
			broadcast(server, TimeSlowStatePayload.off());
		}
		BatchA.set(p, SuperSpeedHandlers.KEY, LEFT, 0, 1e9f);
		if (cooldown) {
			Power power = Powers.byKey(SuperSpeedHandlers.KEY);
			if (power != null && power.ability(AbilitySlot.SLOT_4) != null) {
				ExperimentalPowers.triggerCooldown(p, power, power.ability(AbilitySlot.SLOT_4),
						HeroConfig.get().scaledCooldown(COOLDOWN_TICKS));
			}
		}
		AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.6f);
	}

	private static void restoreRate(MinecraftServer server) {
		if (weSlowed) {
			server.tickRateManager().setTickRate(savedRate);
			weSlowed = false;
		}
	}

	private static void broadcast(MinecraftServer server, TimeSlowStatePayload payload) {
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(sp, TimeSlowStatePayload.TYPE)) {
				ServerPlayNetworking.send(sp, payload);
			}
		}
	}

	// ---- ticking -----------------------------------------------------------------------------------

	/** One tick of the caster's own clock (a slowed server tick or an extra one). */
	private static void casterTick() {
		casterTicks++;
		left--;
		if (caster != null && left % 5 == 0) {
			BatchA.set(caster, SuperSpeedHandlers.KEY, LEFT, Math.max(0, left), 1e9f);
		}
		if (left <= 0 && caster != null) {
			finish(true);
		}
	}

	/** End of every (slowed) server tick. */
	public static void serverTick(MinecraftServer server) {
		inServerTick = false;
		extraThisTick = 0;
		if (caster == null) {
			return;
		}
		if (caster.isRemoved() || !caster.isAlive() || !SuperSpeedHandlers.owns(caster) || caster.getServer() != server) {
			finish(true);
			return;
		}
		casterTick();
	}

	/** {@code MinecraftServer#tickServer} head / return (the pulses only run between ticks). */
	public static void markServerTick(boolean ticking) {
		inServerTick = ticking;
	}

	/**
	 * Called from {@code MinecraftServer#pollTask}, which the server thread spins on while it waits for the next
	 * (slowed) tick: every 50 ms of real time, one extra tick for the caster -- at most 19 between two server ticks.
	 * Returns at the first check whenever no Time Slow is running.
	 */
	public static void pulse(MinecraftServer server) {
		if (caster == null || inServerTick || pulsing) {
			return;
		}
		long now = System.nanoTime();
		if (now < nextPulse) {
			return;
		}
		nextPulse = now - nextPulse > 5 * PULSE_NANOS ? now + PULSE_NANOS : nextPulse + PULSE_NANOS;
		if (extraThisTick >= TICK_DIVISOR - 1 || caster.getServer() != server) {
			return;
		}
		extraThisTick++;
		pulsing = true;
		try {
			extraCasterTick(caster);
		} finally {
			pulsing = false;
		}
	}

	/** One extra, full-speed tick for the caster (public for the gametests). */
	public static void extraCasterTick(ServerPlayer p) {
		if (!isCasting(p) || p.isRemoved() || !p.isAlive() || !(p.level() instanceof ServerLevel sl)) {
			return;
		}
		p.connection.tick();
		if (p.isRemoved() || !isCasting(p)) {
			return;
		}
		sl.tickNonPassenger(p);
		com.projecthero.mod.ProjectHeroMod.tickPlayerSystems(p);
		if (!isCasting(p)) {
			return;
		}
		ExperimentalPowers.advanceCooldowns(p, 1);
		MutationVisuals.advanceAnimation(p, 1);
		SuperSpeedHandlers.advanceClocks(p, 1);
		casterTick();
	}

	// ---- damage --------------------------------------------------------------------------------------

	/**
	 * {@code ALLOW_DAMAGE} hook: a slowed victim's hit-invulnerability only counts down on its own (slowed) ticks,
	 * so on a hit from the caster the window is measured in the caster's ticks instead -- {@link #CASTER_HIT_COOLDOWN}
	 * caster ticks after their last hit on it, it can take a full hit from them again.
	 */
	public static void onIncomingDamage(LivingEntity victim, DamageSource source) {
		if (caster == null || victim == caster || !isCaster(source.getEntity())) {
			return;
		}
		Long last = LAST_CASTER_HIT.get(victim.getId());
		if (victim.invulnerableTime > 0 && (last == null || casterTicks - last >= CASTER_HIT_COOLDOWN)) {
			victim.invulnerableTime = 0;
		}
		LAST_CASTER_HIT.put(victim.getId(), casterTicks);
	}

	// ---- joins, logouts, safety ----------------------------------------------------------------------

	public static void onJoin(ServerPlayer p) {
		MinecraftServer server = p.getServer();
		if (server == null) {
			return;
		}
		if (caster == null && weSlowed) {
			restoreRate(server); // a slow that lost its caster without ending -- never leave the world stuck at 5%
		}
		if (caster != null && ServerPlayNetworking.canSend(p, TimeSlowStatePayload.TYPE)) {
			ServerPlayNetworking.send(p, new TimeSlowStatePayload(true, caster.getUUID()));
		}
	}

	public static void onDisconnect(ServerPlayer p) {
		if (isCasting(p)) {
			finish(true);
		}
	}

	/** Server stopping: put the rate back and forget everything. */
	public static void onServerStopping(MinecraftServer server) {
		if (caster != null) {
			caster = null;
		}
		restoreRate(server);
		LAST_CASTER_HIT.clear();
	}

	/** Server stopped: drop everything (the entities these hold belong to a dead world). */
	public static void clearSessionState() {
		caster = null;
		weSlowed = false;
		LAST_CASTER_HIT.clear();
		inServerTick = false;
		pulsing = false;
	}
}
