package com.projecthero.mod.time;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 (user: "can you change all the time stops and time slows so that they still work worldwide but not across
 * dimensions"): a <b>per-dimension</b> time scale. Vanilla's tick rate ({@code /tick rate}) is server-wide, so the Super
 * Speed Time Slow used to slow every dimension at once. Instead the server keeps running at its normal rate and a scaled
 * dimension only <i>runs</i> one server tick in every {@link Scale#divisor}; on the ticks in between it is <b>held</b>:
 * <ul>
 *   <li>{@code LevelTimeScaleServerLevelMixin}: the level's own {@code runsNormally} is false (weather, world border,
 *       scheduled block and fluid ticks, random ticks and mob spawning, raids, block events, the day / night cycle -- the
 *       same switches vanilla's {@code /tick freeze} uses), every entity but the exempt ones skips its tick, and the
 *       Overworld still counts the shared game clock (cooldowns, other dimensions' scheduled ticks) while its day time
 *       holds.</li>
 *   <li>{@code LevelTimeScaleLevelMixin}: block entities hold; {@code LevelTimeScaleMobMixin}: mobs don't roll despawn
 *       checks; {@code LevelTimeScaleChunkCacheMixin}: no spawning, random ticks or lightning; {@code LevelTimeScalePlayerMixin}:
 *       a held player's connection tick (living tick, effects, food, mining, i-frames, movement bookkeeping) holds and
 *       {@code ProjectHeroMod} skips their per-player mod systems. Chunk loading and entity tracking carry on.</li>
 *   <li>Clients: every player in the scaled dimension is sent vanilla's ticking-state packet at the scaled rate, so their
 *       game runs its simulation, animations and interpolation at that rate (the old behaviour, now per player); everyone
 *       else gets the server's real state. It is re-sent whenever a player joins, changes dimension or respawns
 *       (a new client level would otherwise start at 20), and after vanilla's {@code /tick} commands broadcast theirs.</li>
 * </ul>
 * The <b>anchor</b> (the caster) and whatever shares its root vehicle (its mount, what it carries) are never held. Other
 * dimensions are never touched. Only dimension keys and ids are stored (no worlds or entities), and
 * {@code ServerStateReset} / server stop clear it all.
 */
public final class LevelTimeScale {
	/** One scaled dimension. */
	public static final class Scale {
		final ResourceKey<Level> dimension;
		/** The dimension runs one server tick in this many. */
		final int divisor;
		/** The anchor (never held), or null. */
		final UUID anchor;
		/** Gametests only: hold just the entities within this radius of {@link #centre} (and leave the level-wide clock alone). */
		final double radius;
		final Vec3 centre;
		long phase;
		boolean held;
		/** The anchor's root vehicle's entity id this tick (-1 = the anchor isn't in this dimension). */
		int anchorRoot = -1;

		Scale(ResourceKey<Level> dimension, int divisor, UUID anchor, double radius, Vec3 centre) {
			this.dimension = dimension;
			this.divisor = Math.max(1, divisor);
			this.anchor = anchor;
			this.radius = radius;
			this.centre = centre == null ? Vec3.ZERO : centre;
		}

		public int divisor() {
			return divisor;
		}

		public boolean held() {
			return held;
		}
	}

	private static final Map<ResourceKey<Level>, Scale> SCALES = new LinkedHashMap<>();
	/** True while a power deliberately ticks an entity on a held tick (the Black Flash keeping pace with the caster). */
	private static boolean forcing;

	private LevelTimeScale() {
	}

	public static void register() {
		ServerTickEvents.START_SERVER_TICK.register(LevelTimeScale::startServerTick);
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> syncClient(player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncClient(newPlayer));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
	}

	// ---------------------------------------------------------------- the registry

	/**
	 * Scales {@code dimension} to one tick in {@code divisor}, never holding {@code anchor} (and its mount / passengers).
	 * {@code testRadius} &gt; 0 (gametests only) holds just the entities near {@code centre}.
	 */
	public static void set(MinecraftServer server, ResourceKey<Level> dimension, int divisor, UUID anchor, double testRadius, Vec3 centre) {
		Scale s = new Scale(dimension, divisor, anchor, testRadius, centre);
		SCALES.put(dimension, s);
		resolveAnchor(server, s);
		syncDimension(server, dimension);
	}

	/** The dimension runs at the normal rate again (its players' clients are put back on the server's rate). */
	public static void clear(MinecraftServer server, ResourceKey<Level> dimension) {
		if (SCALES.remove(dimension) != null && server != null) {
			syncDimension(server, dimension);
		}
	}

	/** Server stopped / {@code ServerStateReset}: forget everything. */
	public static void clearAll() {
		SCALES.clear();
		forcing = false;
	}

	public static Scale scale(ResourceKey<Level> dimension) {
		return SCALES.get(dimension);
	}

	public static boolean scaled(Level level) {
		return level != null && !SCALES.isEmpty() && SCALES.containsKey(level.dimension());
	}

	/** How many dimensions are scaled right now (tests, diagnostics). */
	public static int count() {
		return SCALES.size();
	}

	// ---------------------------------------------------------------- per tick

	/** Start of every server tick: each scaled dimension steps its phase -- it runs on one tick in {@code divisor}. */
	public static void startServerTick(MinecraftServer server) {
		if (SCALES.isEmpty()) {
			return;
		}
		for (Scale s : SCALES.values()) {
			s.phase++;
			s.held = !phaseRuns(s.phase, s.divisor);
			resolveAnchor(server, s);
		}
	}

	/** Pure rule (tests): with phase {@code phase}, does a dimension scaled to one tick in {@code divisor} run? */
	public static boolean phaseRuns(long phase, int divisor) {
		return divisor <= 1 || phase % divisor == 0;
	}

	private static void resolveAnchor(MinecraftServer server, Scale s) {
		s.anchorRoot = -1;
		if (s.anchor == null || server == null) {
			return;
		}
		ServerPlayer a = server.getPlayerList().getPlayer(s.anchor);
		if (a != null && a.level().dimension() == s.dimension) {
			s.anchorRoot = a.getRootVehicle().getId();
		}
	}

	/**
	 * The whole level sits this server tick out (weather, clock, block ticks, block entities ...). False for a test-radius
	 * scale, which only holds the entities near its centre. Server side only.
	 */
	public static boolean heldNow(Level level) {
		if (SCALES.isEmpty() || level == null || level.isClientSide()) {
			return false;
		}
		Scale s = SCALES.get(level.dimension());
		return s != null && s.held && s.radius <= 0.0;
	}

	/** This dimension's scale is on a held tick (also a test-radius scale, unlike {@link #heldNow}). Server side only. */
	public static boolean phaseHeld(Level level) {
		if (SCALES.isEmpty() || level == null || level.isClientSide()) {
			return false;
		}
		Scale s = SCALES.get(level.dimension());
		return s != null && s.held;
	}

	/** This entity sits this server tick out: its dimension is held and it isn't the anchor's group. Server side only. */
	public static boolean holds(Entity e) {
		if (SCALES.isEmpty() || forcing || e == null || e.level().isClientSide()) {
			return false;
		}
		Scale s = SCALES.get(e.level().dimension());
		if (s == null || !s.held) {
			return false;
		}
		if (s.anchorRoot != -1 && e.getRootVehicle().getId() == s.anchorRoot) {
			return false;
		}
		if (s.anchor != null && s.anchor.equals(e.getUUID())) {
			return false;
		}
		return !(s.radius > 0.0 && e.position().distanceToSqr(s.centre) > s.radius * s.radius);
	}

	/** Pure rule (tests): is {@code e} exempt from the scale in its dimension (the anchor's group, or outside a test radius)? */
	public static boolean exempt(Entity e) {
		Scale s = e == null ? null : SCALES.get(e.level().dimension());
		if (s == null) {
			return true;
		}
		boolean wasHeld = s.held;
		s.held = true;
		try {
			return !holds(e);
		} finally {
			s.held = wasHeld;
		}
	}

	/** Ticks {@code entity} now even though its dimension is held (a power keeping something at the anchor's pace). */
	public static void forceTick(ServerLevel level, Entity entity) {
		boolean was = forcing;
		forcing = true;
		try {
			level.tickNonPassenger(entity);
		} finally {
			forcing = was;
		}
	}

	// ---------------------------------------------------------------- clients

	/** The tick rate a client in {@code dimension} should run at: the server's, divided by the dimension's scale. */
	public static float clientRate(MinecraftServer server, ResourceKey<Level> dimension) {
		float rate = server.tickRateManager().tickrate();
		Scale s = SCALES.get(dimension);
		if (s == null || s.radius > 0.0) {
			return rate;
		}
		return Math.max(TickRateManager.MIN_TICKRATE, rate / s.divisor);
	}

	/** Sends this player the ticking state of the dimension they are in now. */
	public static void syncClient(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if (server == null || player.connection == null) {
			return;
		}
		ServerTickRateManager trm = server.tickRateManager();
		Scale s = SCALES.get(player.level().dimension());
		if (s == null || s.radius > 0.0) {
			player.connection.send(ClientboundTickingStatePacket.from(trm));
		} else {
			player.connection.send(new ClientboundTickingStatePacket(clientRate(server, s.dimension), trm.isFrozen()));
		}
	}

	private static void syncDimension(MinecraftServer server, ResourceKey<Level> dimension) {
		if (server == null) {
			return;
		}
		ServerLevel level = server.getLevel(dimension);
		if (level == null) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			syncClient(p);
		}
	}

	/** After vanilla broadcasts its tick state to everyone ({@code /tick}): players in scaled dimensions get theirs back. */
	public static void afterVanillaBroadcast(MinecraftServer server) {
		if (SCALES.isEmpty() || server == null) {
			return;
		}
		for (ResourceKey<Level> dim : new java.util.ArrayList<>(SCALES.keySet())) {
			syncDimension(server, dim);
		}
	}
}
