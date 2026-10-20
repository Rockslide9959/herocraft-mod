package com.projecthero.mod.ironman.ability.era1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Era I (user): everything the Mk III Classic and Mk IV Hydro moves leave RUNNING -- a charging Unibeam, a held
 * Tractor Beam, the Targeting HUD, the Barrel Roll's i-frames, the Flight Boost trail, Ballast, the Bubble Shield, the
 * Headlamps (and the light blocks they place), thrown Depth Charges and laid Mines.
 *
 * <p>Static server state keyed by player UUID: cleared by {@link #clearSessionState} ({@code ServerStateReset}), per player
 * on disconnect, and ticked once per server tick ({@link #serverTick}). Every light block the Headlamps place is taken
 * down again on toggle-off, suit-off, death, logout, dimension change and {@code SERVER_STOPPING}, so none is ever saved.
 */
public final class EraOneMk34State {
	private EraOneMk34State() {
	}

	/** One placed headlamp light block (only ever put into air / a water source; only removed while still ours). */
	record Light(ResourceKey<Level> dim, BlockPos pos, boolean waterlogged) {
	}

	/** A thrown Depth Charge (a simulated point: arcs in air, sinks in water, goes off on contact / landing / fuse). */
	static final class Charge {
		final UUID owner;
		final ResourceKey<Level> dim;
		Vec3 pos;
		Vec3 vel;
		final long born;
		final float damage;

		Charge(UUID owner, ResourceKey<Level> dim, Vec3 pos, Vec3 vel, long born, float damage) {
			this.owner = owner;
			this.dim = dim;
			this.pos = pos;
			this.vel = vel;
			this.born = born;
			this.damage = damage;
		}
	}

	/** A laid proximity Mine. */
	static final class Mine {
		final UUID owner;
		final ResourceKey<Level> dim;
		final Vec3 pos;
		final long until;
		final float damage;

		Mine(UUID owner, ResourceKey<Level> dim, Vec3 pos, long until, float damage) {
			this.owner = owner;
			this.dim = dim;
			this.pos = pos;
			this.until = until;
			this.damage = damage;
		}
	}

	/** Per-player runtime. */
	static final class P {
		// ---- Mk III
		long unibeamChargeStart = -1L;
		int tractorTarget = -1;
		long tractorStart;
		boolean hudOn;
		long hudNextMark;
		int hudMarked;
		long rollIframesUntil;
		boolean rollRight;
		long boostUntil;
		// ---- Mk IV
		long ballastSinkUntil;
		boolean ballastStompPending;
		long ballastStompUntil;
		long bubbleUntil;
		boolean headlamps;
		final List<Light> lights = new ArrayList<>();
		int lastPing;
	}

	private static final Map<UUID, P> STATE = new HashMap<>();
	static final List<Charge> CHARGES = new ArrayList<>();
	static final List<Mine> MINES = new ArrayList<>();

	private static boolean initialized;

	/** Registers the server tick, the logout cleanup and the light take-down on stop (from the moves' {@code init}). */
	static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(EraOneMk34State::serverTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(EraOneMk34State::removeAllLights);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUUID(), server));
	}

	public static void clearSessionState() {
		STATE.clear();
		CHARGES.clear();
		MINES.clear();
	}

	static P of(ServerPlayer p) {
		return STATE.computeIfAbsent(p.getUUID(), k -> new P());
	}

	static P peek(ServerPlayer p) {
		return STATE.get(p.getUUID());
	}

	/** Logout: every light block comes down, the mines / charges go with their owner. */
	static void forget(UUID id, MinecraftServer server) {
		P s = STATE.remove(id);
		if (s != null && server != null) {
			EraOneMk4Moves.removeLights(server, s);
		}
		CHARGES.removeIf(c -> c.owner.equals(id));
		MINES.removeIf(m -> m.owner.equals(id));
	}

	private static void removeAllLights(MinecraftServer server) {
		for (P s : STATE.values()) {
			EraOneMk4Moves.removeLights(server, s);
		}
	}

	private static void serverTick(MinecraftServer server) {
		if (!STATE.isEmpty()) {
			for (Map.Entry<UUID, P> e : new ArrayList<>(STATE.entrySet())) {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p == null) {
					forget(e.getKey(), server);
					continue;
				}
				tickPlayer(p, e.getValue());
			}
		}
		if (!CHARGES.isEmpty() || !MINES.isEmpty()) {
			EraOneMk4Moves.tickWorld(server);
		}
	}

	private static void tickPlayer(ServerPlayer p, P s) {
		String worn = com.projecthero.mod.ironman.IronManArmor.wornSuitId(p);
		boolean up = p.isAlive() && worn != null && com.projecthero.mod.ironman.IronManArmor.hasChestplate(p, worn)
				&& !com.projecthero.mod.ironman.IronManIntegrity.disabled(p, worn);
		EraOneMk3Moves.tick(p, s, up && EraOneMk3Moves.SUIT.equals(worn));
		EraOneMk4Moves.tick(p, s, up && EraOneMk4Moves.SUIT.equals(worn));
	}

	/** Gametests: run one server tick of this state right now. */
	public static void tickForTests(MinecraftServer server) {
		serverTick(server);
	}
}
