package com.herocraft.mod.diagnostics;

import com.herocraft.mod.hero.power.ConjuredStructures;
import com.herocraft.mod.hero.power.TempBlocks;
import com.herocraft.mod.hero.power.p07.ElectrokinesisHandlers;
import com.herocraft.mod.hero.power.p19.ShadowManipulationHandlers;
import com.herocraft.mod.hero.power.p26.MagneticHandlers;
import com.herocraft.mod.ironman.suit.IronManSuitCall;
import com.herocraft.mod.worldgen.CraterAmbience;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;

/**
 * One place that drops every piece of <em>static</em> server-side scratch state when a server stops,
 * plus a slow sweep that prunes the couple of caches which would otherwise only be pruned while some
 * player happens to have the owning power selected.
 *
 * <h2>Why this exists (the "long session gets laggy" report)</h2>
 * Several systems keep their live bookkeeping in {@code static} maps/queues rather than in
 * {@link net.minecraft.world.level.saveddata.SavedData} -- correctly, since none of it should be
 * persisted. But a static field outlives the server that filled it, and a singleplayer client keeps
 * one JVM for every world it opens in a session. Those entries hold hard references to
 * {@link net.minecraft.server.level.ServerLevel}, {@link net.minecraft.world.entity.Entity} and
 * {@link net.minecraft.world.level.block.state.BlockState} objects, so quitting to the title screen
 * and opening another world used to pin the <em>entire previous world</em> in memory: every level, its
 * chunk map and its entities stayed reachable and uncollectable. Do that a few times in one sitting
 * and the heap fills with dead worlds, the GC starts working much harder for the same allocation
 * rate, and the game gets progressively choppier the longer the session runs -- which is exactly the
 * shape of "it lags after playing for a long time".
 *
 * <p>Two of those maps ({@code ElectrokinesisHandlers.SHOCKED}, {@code ShadowManipulationHandlers.BOUND})
 * had a second, smaller version of the same problem while a single world was running: entries are
 * added whenever those abilities hit something, but the code that expires them only runs inside the
 * owning power's passive tick. Switch away from the power (or log out) with entries still in the map
 * and nothing ever removes them again. {@link #sweep} gives them a cheap, unconditional owner, and
 * Spider-Man's cocoon markers ({@code SpiderWebs}) are swept from there for the same reason.
 *
 * <p>{@link TempBlocks} deserves its own note: its queue expires entries by comparing against
 * {@code server.overworld().getGameTime()}. A new world starts near game time 0, so entries left over
 * from a previous world have an {@code expiresAt} far in the future and would never be reached --
 * they would sit at the head of the deque holding a dead {@code ServerLevel} until the 4096-entry cap
 * eventually pushed them out.
 */
public final class ServerStateReset {
	/** Ticks between sweeps. Deliberately slow -- everything it touches is tiny and expiry-stamped. */
	private static final int SWEEP_INTERVAL_TICKS = 200; // 10s

	private ServerStateReset() {
	}

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
		ServerTickEvents.END_SERVER_TICK.register(ServerStateReset::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) {
			return;
		}
		sweep(server);
	}

	/** Expire anything time-stamped that nothing else is currently responsible for expiring. */
	private static void sweep(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		ElectrokinesisHandlers.pruneExpired(now);
		ShadowManipulationHandlers.pruneExpired(now);
		com.herocraft.mod.spider.SpiderWebs.pruneExpired(now);
		com.herocraft.mod.combat.SonicVulnerability.pruneExpired(now);
	}

	/**
	 * Drop every static scratch collection. Safe to call at any point after a server has stopped --
	 * none of it is persisted state, and everything that owns it re-populates on demand.
	 */
	public static void clearAll() {
		CraterAmbience.clearSessionState();
		TempBlocks.clearSessionState();
		ConjuredStructures.clearSessionState();
		MagneticHandlers.clearSessionState();
		ElectrokinesisHandlers.clearSessionState();
		ShadowManipulationHandlers.clearSessionState();
		IronManSuitCall.clearPending();
		com.herocraft.mod.event.entity.CursedZombieSpawns.clearSessionState();
		com.herocraft.mod.event.raid.ZombieRaidNetworking.clearSessionState();
		com.herocraft.mod.worldgen.GraveyardTracker.clearSessionState();
		com.herocraft.mod.spider.SpiderWebs.clearSessionState();
		com.herocraft.mod.maxsteel.MaxSteelAbilityManager.clearSessionState();
		com.herocraft.mod.maxsteel.MaxSteelCannon.clearSessionState();
		com.herocraft.mod.maxsteel.worldgen.SteelCrashAmbience.clearSessionState();
		com.herocraft.mod.symbiote.worldgen.SymbioteWorldgen.clearSessionState();
		com.herocraft.mod.firearm.FirearmManager.clearSessionState();
		com.herocraft.mod.punisher.ability.PunisherC4.clearSessionState();
		com.herocraft.mod.punisher.PunisherAbilityManager.clearSessionState();
		com.herocraft.mod.punisher.PunisherArmorGate.clearSessionState();
		com.herocraft.mod.combat.SonicVulnerability.clearSessionState();
		com.herocraft.mod.symbiote.SymbioteAbilityManager.clearSessionState();
		com.herocraft.mod.symbiote.Symbiote.clearSessionState();
	}
}
