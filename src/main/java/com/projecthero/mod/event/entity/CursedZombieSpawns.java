package com.projecthero.mod.event.entity;

import java.util.ArrayDeque;
import java.util.Deque;

import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.worldgen.GraveyardTracker;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;

/**
 * Turning a rare fraction of naturally spawning vanilla zombies into {@link CursedZombie}s.
 *
 * <h2>Where the roll happens, and why</h2>
 * The roll is made from a mixin on {@code Zombie#finalizeSpawn}, which is the one place that knows a
 * zombie is being created <em>and</em> why. That matters: only {@link MobSpawnType#NATURAL} and
 * {@link MobSpawnType#CHUNK_GENERATION} are eligible, so spawners, eggs, zombie reinforcements,
 * conversions and raid waves are all excluded automatically. A hook that could not see the spawn
 * reason -- an entity-load listener, say -- would happily turn a spawner farm into a curse dispenser.
 *
 * <h2>Why the conversion is deferred a tick</h2>
 * {@code finalizeSpawn} runs <em>before</em> the zombie is added to the world, so swapping it out
 * there means discarding an entity mid-construction and adding another from inside the spawner's own
 * call stack. The same deferred-queue pattern {@code CraterAmbience} uses for crater hammers avoids
 * all of that: the mixin only records a candidate, and the swap happens on the next ordinary server
 * tick, when the world is in a normal state.
 *
 * <h2>Cost</h2>
 * On the spawn path: one enum comparison, one {@code nextDouble}, and -- only when that rare roll
 * passes -- one distance check against {@link GraveyardTracker}'s cached list. No structure search, no
 * chunk access, no entity query. The queue is normally empty, so the tick hook is a single
 * {@code isEmpty} check.
 */
public final class CursedZombieSpawns {
	private record Candidate(ServerLevel level, Zombie original) {
	}

	private static final Deque<Candidate> PENDING = new ArrayDeque<>();
	/** Safety valve: never convert more than this many per tick, whatever happened. */
	private static final int MAX_PER_TICK = 4;

	private CursedZombieSpawns() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(CursedZombieSpawns::tick);
	}

	/**
	 * Called from {@code ZombieSpawnMixin}. Decides whether this zombie should become a Cursed Zombie
	 * and, if so, queues the swap.
	 */
	public static void considerNaturalSpawn(Zombie zombie, ServerLevel level, MobSpawnType reason) {
		if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
			return;
		}
		// Only a plain vanilla zombie -- never a husk, drowned, villager zombie, or any of this mod's
		// own raid zombies (which all extend Zombie and would otherwise be eligible).
		if (zombie.getType() != net.minecraft.world.entity.EntityType.ZOMBIE) {
			return;
		}
		if (zombie.isBaby()) {
			return;
		}

		EventConfig.ZombieRaid cfg = EventConfig.raid();
		double baseChance = cfg.cursedZombieChance;
		double nearChance = cfg.cursedZombieChanceNearGraveyard;
		double bestChance = Math.max(baseChance, nearChance);
		if (bestChance <= 0.0) {
			return;
		}

		// Two-stage roll, so the proximity test is only reached by the ~2% of zombies that could
		// possibly convert at all. Stage one rejects against the most generous rate in the config;
		// stage two re-rolls against the rate that actually applies here. Multiplying the two gives
		// exactly the configured probability (bestChance * applicable/bestChance == applicable), so
		// this is a pure optimisation, not an approximation.
		if (level.random.nextDouble() >= bestChance) {
			return;
		}
		double applicable = GraveyardTracker.nearGraveyard(level, zombie.blockPosition(), cfg.graveyardProximityBlocks)
				? nearChance : baseChance;
		if (level.random.nextDouble() >= applicable / bestChance) {
			return;
		}

		PENDING.add(new Candidate(level, zombie));
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		int converted = 0;
		Candidate candidate;
		while (converted < MAX_PER_TICK && (candidate = PENDING.poll()) != null) {
			convert(candidate);
			converted++;
		}
		// Anything left over waits for the next tick; the queue is bounded by how many zombies could
		// possibly have spawned in one tick, which is small.
	}

	private static void convert(Candidate candidate) {
		Zombie original = candidate.original();
		ServerLevel level = candidate.level();
		if (original.isRemoved() || original.level() != level) {
			return; // the spawn was rejected after finalizeSpawn -- nothing to replace
		}
		BlockPos pos = original.blockPosition();
		if (!level.isLoaded(pos)) {
			return;
		}
		CursedZombie cursed = RaidEntityTypes.CURSED_ZOMBIE.create(level);
		if (cursed == null) {
			return;
		}
		cursed.moveTo(original.getX(), original.getY(), original.getZ(), original.getYRot(), original.getXRot());
		cursed.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
		original.discard();
		level.addFreshEntity(cursed);
	}

	/** Drop any queued conversions. Called from {@code ServerStateReset} when a server stops. */
	public static void clearSessionState() {
		PENDING.clear();
	}
}
