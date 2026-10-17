package com.projecthero.mod.city;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2 City part B: the Repair Worker trips that rebuild City damage.
 *
 * <p>Every second each City with due damage (its latest damage at least {@code repairDelayTicks} old) gets one job at
 * most: an idle Repair Worker in work hours (no raid, no monster within 16 blocks) walks out with a hammer and rebuilds
 * the queued blocks bottom-up, one every {@code ticksPerBlock}, up to {@code blocksPerTrip}. Blocks it can't reach from
 * the ground it climbs to on a temporary scaffolding column. v0.16.2 (user: a worker fell into a road hole, sealed himself
 * and other villagers in it, and repaired the rest from where he was stuck): it only ever works from a <em>work spot</em>
 * -- solid ground outside the damage, never on or under a block still to be rebuilt -- walking there (or, stuck for
 * {@code reachTimeoutTicks}, 10 s at most, clambering over) instead of to the hole itself; and before any block goes in,
 * every villager or mob standing inside the damage it is about to close is lifted out onto safe ground (a player there
 * just makes the worker do that block later). With no Repair Worker at all, due damage fills in on its own after
 * {@code selfRepairAfterTicks}, and the depot hires a new worker every {@code hireDelayTicks} while it has a free Repair
 * Bench.
 *
 * <p>Safety: a position is only filled while it is air, fluid or fire (anything else -- a player's block -- drops it
 * and opts its cell out), never inside spawn protection, never with a block entity's contents (rebuilt empty), and
 * the bank's valuable blocks come back as stone-brick filler. A rebuilt block drops nothing if broken within
 * {@code noDropTicks}.
 */
public final class CityRepair {
	/** A worker frozen on a job (no AI while it builds). Cleared on load if its job is gone. */
	public static final String FROZEN_TAG = "projecthero_repair_frozen";
	/** Blocks that come back as {@link #FILLER} (the bank vault is "cleaned out"). */
	public static final Set<Block> VALUABLES = Set.of(Blocks.EMERALD_BLOCK, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.IRON_BLOCK,
			Blocks.NETHERITE_BLOCK, Blocks.LAPIS_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.COPPER_BLOCK, Blocks.RAW_COPPER_BLOCK,
			Blocks.ANCIENT_DEBRIS, Blocks.BEACON);
	public static final Block FILLER = Blocks.STONE_BRICKS;
	private static final double REACH = 5.5;
	/** v0.16.2: the deepest a worker reaches down into a crater from its rim (it never climbs in). */
	private static final double FAR_REACH = 8.0;
	/** How far above a spot a block still to be rebuilt makes it "inside" the damage (someone there would be roofed in). */
	private static final int ROOF_CHECK = 8;
	/** v0.16.2: a stuck walk to the work spot ends in a clamber this soon at most. */
	private static final int HOP_TICKS = 200;
	private static final int MAX_SCAFFOLD = 24;
	private static final int TRIP_RADIUS = 48;

	private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Job>> JOBS = new ConcurrentHashMap<>();

	private CityRepair() {
	}

	// ---------------------------------------------------------------- the job

	enum Phase {
		WALK, BUILD, CLIMB, DESCEND
	}

	/** One trip (or a self-repair run when {@code worker} is null). */
	public static final class Job {
		final long cityId;
		final UUID worker;
		final List<BlockPos> todo;
		int index;
		int placed;
		Phase phase = Phase.WALK;
		int phaseTicks;
		int cooldown;
		boolean remote;
		Vec3 hold;
		final List<BlockPos> scaffold = new ArrayList<>();
		/** v0.16.2: the work spot it is heading for / standing on, its reach from there, and how often blocks were put off. */
		BlockPos stand;
		double reach = REACH;
		boolean resume;
		int deferrals;

		Job(long cityId, UUID worker, List<BlockPos> todo) {
			this.cityId = cityId;
			this.worker = worker;
			this.todo = todo;
		}

		public UUID worker() {
			return worker;
		}

		public int placed() {
			return placed;
		}

		public int size() {
			return todo.size();
		}

		/** Scaffolding blocks put up on this trip so far (they are all taken down again). */
		public int scaffoldsPlaced() {
			return scaffoldsPlaced;
		}

		int scaffoldsPlaced;
	}

	static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			Long2ObjectOpenHashMap<Job> jobs = JOBS.get(level.dimension());
			if (jobs != null && !jobs.isEmpty()) {
				jobs.values().removeIf(job -> !tickJob(level, job));
			}
			if (server.getTickCount() % 20 == 0) {
				plan(level);
			}
			if (server.getTickCount() % 200 == 0) {
				CitySavedData data = CitySavedData.get(level);
				data.pruneNoDrop(level.getGameTime());
				cleanupScaffolds(level, data);
			}
		}
	}

	/** The running job of a City, or null. */
	public static Job job(ServerLevel level, long cityId) {
		Long2ObjectOpenHashMap<Job> jobs = JOBS.get(level.dimension());
		return jobs == null ? null : jobs.get(cityId);
	}

	public static boolean isWorkHours(ServerLevel level) {
		long t = level.getDayTime() % 24000L;
		return t >= 2000 && t < 9000;
	}

	private static boolean danger(ServerLevel level, BlockPos pos) {
		return level.isRaided(pos) || !level.getEntitiesOfClass(Monster.class, new AABB(pos).inflate(16.0), m -> m.isAlive()).isEmpty();
	}

	/** Repair Workers living in (or right next to) {@code city}. */
	public static List<Villager> workers(ServerLevel level, CityArea city) {
		return level.getEntitiesOfClass(Villager.class, AABB.of(city.bounds()).inflate(16.0),
				v -> v.isAlive() && !v.isBaby() && v.getVillagerData().getProfession() == CityBlocks.REPAIR_WORKER);
	}

	private static boolean busy(ServerLevel level, UUID worker) {
		Long2ObjectOpenHashMap<Job> jobs = JOBS.get(level.dimension());
		if (jobs != null) {
			for (Job j : jobs.values()) {
				if (worker.equals(j.worker)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Once a second: give each City with due damage a job, hire workers. */
	static void plan(ServerLevel level) {
		if (!City.repairActive(level)) {
			return;
		}
		CitySavedData data = CitySavedData.get(level);
		long now = level.getGameTime();
		CityConfig.Repair cfg = CityConfig.repair();
		boolean hireCheck = (now / 20) % 10 == 0; // the depot looks at its staff every 10 s
		for (CitySavedData.CityRecord rec : data.allCities()) {
			if (job(level, rec.id) != null || (rec.areas.isEmpty() && !hireCheck)) {
				continue;
			}
			CityArea city = CityLocator.byId(level, rec.id);
			if (city == null) {
				continue;
			}
			List<Villager> workers = workers(level, city);
			if (hireCheck) {
				hire(level, city, rec, workers, now);
			}
			if (rec.areas.isEmpty()) {
				continue;
			}
			List<CitySavedData.DamageArea> due = rec.dueAreas(now, cfg.repairDelayTicks);
			if (due.isEmpty()) {
				continue;
			}
			BlockPos focus = BlockPos.of(due.get(0).positions.iterator().nextLong());
			if (!level.isPositionEntityTicking(focus) || danger(level, focus)) {
				continue;
			}
			boolean hours = !cfg.workHoursOnly || isWorkHours(level);
			Villager worker = null;
			if (hours) {
				double best = Double.MAX_VALUE;
				for (Villager v : workers) {
					if (v.isSleeping() || busy(level, v.getUUID())) {
						continue;
					}
					double d = v.distanceToSqr(Vec3.atCenterOf(focus));
					if (d < best) {
						best = d;
						worker = v;
					}
				}
			}
			boolean selfRepair = worker == null && workers.isEmpty() && now - due.get(0).lastDamage >= (long) cfg.repairDelayTicks + cfg.selfRepairAfterTicks;
			if (worker == null && !selfRepair) {
				continue;
			}
			List<BlockPos> todo = collect(level, city, data, due, focus, cfg.blocksPerTrip);
			if (todo.isEmpty()) {
				continue;
			}
			startJob(level, new Job(rec.id, worker == null ? null : worker.getUUID(), todo), worker);
		}
	}

	/** Starts {@code job} now (tests use this to skip the planner). */
	public static Job startJob(ServerLevel level, Job job, Villager worker) {
		JOBS.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>()).put(job.cityId, job);
		if (worker != null) {
			if (worker.isSleeping()) {
				worker.stopSleeping();
			}
			worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MACE));
			worker.setDropChance(EquipmentSlot.MAINHAND, 0f);
		} else {
			job.remote = true;
			job.phase = Phase.BUILD;
		}
		return job;
	}

	/** Starts a trip for {@code worker} over everything due in {@code city} now (tests and {@code /projecthero city repair}). */
	public static Job startTripNow(ServerLevel level, CityArea city, Villager worker) {
		CitySavedData data = CitySavedData.get(level);
		CitySavedData.CityRecord rec = data.city(city.id());
		List<CitySavedData.DamageArea> due = rec.dueAreas(Long.MAX_VALUE / 2, 0);
		if (due.isEmpty()) {
			return null;
		}
		BlockPos focus = BlockPos.of(due.get(0).positions.iterator().nextLong());
		List<BlockPos> todo = collect(level, city, data, due, focus, CityConfig.repair().blocksPerTrip);
		return todo.isEmpty() ? null : startJob(level, new Job(city.id(), worker == null ? null : worker.getUUID(), todo), worker);
	}

	/** The positions of one trip: valid due damage near {@code focus}, bottom-up (full blocks before the rest). */
	private static List<BlockPos> collect(ServerLevel level, CityArea city, CitySavedData data, List<CitySavedData.DamageArea> due, BlockPos focus,
			int max) {
		List<BlockPos> out = new ArrayList<>();
		for (CitySavedData.DamageArea area : due) {
			for (LongIterator it = area.positions.clone().iterator(); it.hasNext();) {
				BlockPos p = BlockPos.of(it.nextLong());
				if (p.distManhattan(focus) > TRIP_RADIUS * 2 || !level.isLoaded(p)) {
					continue;
				}
				if (validate(level, city, data, p) != null) {
					out.add(p);
				}
			}
		}
		out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
				.thenComparingInt(p -> fullBlock(level, city, p) ? 0 : 1)
				.thenComparingInt(p -> p.distManhattan(focus)));
		return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
	}

	private static boolean fullBlock(ServerLevel level, CityArea city, BlockPos p) {
		BlockState s = CityBlueprint.expectedAt(level, city, p);
		return s != null && s.isCollisionShapeFullBlock(level, p);
	}

	/**
	 * The state to rebuild at {@code p}, or null if the position is no longer a repair (and it is dropped from the queue;
	 * a player's block there opts the cell out).
	 */
	static BlockState validate(ServerLevel level, CityArea city, CitySavedData data, BlockPos p) {
		BlockState expected = CityBlueprint.expectedAt(level, city, p);
		if (expected == null || data.isOptedOut(city.id(), p)) {
			data.removeDamage(city.id(), p);
			return null;
		}
		BlockState current = level.getBlockState(p);
		if (current.is(expected.getBlock())) {
			data.removeDamage(city.id(), p);
			return null;
		}
		if (!CityDamageTracker.airish(current)) {
			data.optOut(city.id(), p); // someone else's block is there now: never overwrite it
			return null;
		}
		if (spawnProtected(level, p)) {
			return null;
		}
		return restored(expected);
	}

	/** What a rebuilt block becomes: valuables (the bank vault) come back as {@link #FILLER}. */
	public static BlockState restored(BlockState expected) {
		return VALUABLES.contains(expected.getBlock()) ? FILLER.defaultBlockState() : expected;
	}

	static boolean spawnProtected(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		if (CityConfig.repair().repairInsideSpawnProtection || !server.isDedicatedServer() || level.dimension() != Level.OVERWORLD) {
			return false;
		}
		int r = server.getSpawnProtectionRadius();
		BlockPos spawn = level.getSharedSpawnPos();
		return r > 0 && Math.max(Math.abs(pos.getX() - spawn.getX()), Math.abs(pos.getZ() - spawn.getZ())) <= r;
	}

	/** One tick of a job. False when it is over. */
	private static boolean tickJob(ServerLevel level, Job job) {
		CityArea city = CityLocator.byId(level, job.cityId);
		CitySavedData data = CitySavedData.get(level);
		CityConfig.Repair cfg = CityConfig.repair();
		Villager v = null;
		if (job.worker != null) {
			if (!(level.getEntity(job.worker) instanceof Villager found) || !found.isAlive()) {
				removeScaffold(level, job, data, null);
				return false;
			}
			v = found;
		}
		if (city == null || !City.repairActive(level)) {
			return finish(level, job, data, v);
		}
		job.phaseTicks++;
		if (v != null && job.phase != Phase.DESCEND && job.phaseTicks % 20 == 0
				&& ((cfg.workHoursOnly && !isWorkHours(level)) || danger(level, v.blockPosition()))) {
			job.resume = false;
			setPhase(job, Phase.DESCEND);
		}
		if (job.phase == Phase.DESCEND) {
			return descend(level, job, data, v);
		}
		// skip what is no longer a repair
		BlockState next = null;
		for (int guard = 0; guard < 32 && job.index < job.todo.size(); guard++) {
			next = validate(level, city, data, job.todo.get(job.index));
			if (next != null) {
				break;
			}
			job.index++;
		}
		if (next == null || job.index >= job.todo.size() || job.placed >= cfg.blocksPerTrip) {
			if (v == null) {
				return false;
			}
			job.resume = false;
			setPhase(job, Phase.DESCEND);
			return true;
		}
		BlockPos target = job.todo.get(job.index);
		if ((job.phase == Phase.BUILD || v == null) && job.cooldown <= 1 && occupied(level, target, v)) {
			// a player (or the worker) is standing in the gap: do it last (or wait for them to move). Anyone else is lifted
			// out by clearHole below -- v0.16.5 fix: a villager standing in the hole used to put the block off forever
			if (v != null && v.getBoundingBox().intersects(new AABB(target))) {
				stepAside(level, job, v, target);
			}
			if (job.index < job.todo.size() - 1) {
				job.todo.add(job.todo.remove(job.index));
			}
			job.cooldown = 10;
			return true;
		}
		if ((job.phase == Phase.BUILD || v == null) && job.cooldown <= 1 && !clearHole(level, job, v, target)) {
			defer(job); // a player is down there (or there is nowhere to lift someone to): come back to it
			job.cooldown = 10;
			if (giveUp(job)) {
				if (v == null) {
					return false;
				}
				job.resume = false;
				setPhase(job, Phase.DESCEND);
			}
			return true;
		}
		if (v == null) {
			if (--job.cooldown <= 0) {
				place(level, city, data, target, next, null);
				job.index++;
				job.placed++;
				job.cooldown = Math.max(1, cfg.selfRepairTicksPerBlock);
			}
			return true;
		}
		switch (job.phase) {
			case WALK -> walk(level, job, v, target, cfg);
			case CLIMB -> climb(level, job, data, v, target);
			case BUILD -> {
				hold(v, job);
				lookAt(v, target);
				if (!job.remote && !inReach(v, target, job.reach)) {
					toNextStretch(job, v, target); // out of reach (e.g. just lifted out of the hole): find the next work spot
					return true;
				}
				if (--job.cooldown <= 0) {
					place(level, city, data, target, next, v);
					job.index++;
					job.placed++;
					job.cooldown = Math.max(1, cfg.ticksPerBlock);
					BlockPos after = job.index < job.todo.size() ? job.todo.get(job.index) : null;
					if (after != null && !job.remote && !inReach(v, after, job.reach)) {
						toNextStretch(job, v, after);
					}
				}
			}
			default -> {
			}
		}
		return true;
	}

	/** The next block is out of reach from here: climb for it, or come down off the scaffold and walk to its work spot. */
	private static void toNextStretch(Job job, Villager v, BlockPos next) {
		job.stand = null;
		job.reach = REACH;
		if (high(v, next)) {
			setPhase(job, Phase.CLIMB); // (a little higher up the same column if already on one)
		} else if (job.scaffold.isEmpty()) {
			unfreeze(v);
			setPhase(job, Phase.WALK);
		} else {
			job.resume = true; // down the scaffold, then on to the next work spot
			setPhase(job, Phase.DESCEND);
		}
	}

	/** The rest of this trip, as a set (what a work spot must not stand on or under). */
	private static LongOpenHashSet pending(Job job) {
		LongOpenHashSet out = new LongOpenHashSet();
		for (int i = job.index; i < job.todo.size(); i++) {
			out.add(job.todo.get(i).asLong());
		}
		return out;
	}

	/** True if a block still to be rebuilt is at {@code p}'s feet or head, or anywhere above it in its column. */
	private static boolean insideDamage(BlockPos p, LongOpenHashSet pending) {
		for (int dy = 0; dy <= ROOF_CHECK; dy++) {
			if (pending.contains(BlockPos.asLong(p.getX(), p.getY() + dy, p.getZ()))) {
				return true;
			}
		}
		return false;
	}

	/** Somewhere to stand that the repair can never close in: sturdy floor, room for a body, outside the damage. */
	private static boolean safeSpot(ServerLevel level, BlockPos p, LongOpenHashSet pending) {
		BlockPos below = p.below();
		return level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
				&& level.getBlockState(p).getCollisionShape(level, p).isEmpty()
				&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
				&& level.getFluidState(p).isEmpty() && !insideDamage(p, pending);
	}

	/** v0.16.2: the work spot for {@code target} -- a safe spot in reach, closest to {@code from}; else the nearest the rim gets. */
	private static BlockPos workSpot(ServerLevel level, Job job, Vec3 from, BlockPos target) {
		LongOpenHashSet pending = pending(job);
		Vec3 centre = Vec3.atCenterOf(target);
		BlockPos best = null, closest = null, base = null;
		double bestScore = Double.MAX_VALUE, closestDist = Double.MAX_VALUE, baseDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(target.offset(-7, -12, -7), target.offset(7, 5, 7))) {
			if (!safeSpot(level, p, pending) || occupiedByOther(level, p, job.worker)) {
				continue;
			}
			double eyeY = p.getY() + 1.62;
			double eye = new Vec3(p.getX() + 0.5, eyeY, p.getZ() + 0.5).distanceTo(centre);
			double dx = p.getX() - target.getX(), dz = p.getZ() - target.getZ();
			if (eye <= REACH - 0.2) {
				double rise = p.getY() - from.y; // climbing up to a spot is the last resort
				double score = Vec3.atBottomCenterOf(p).distanceToSqr(from) + eye * eye * 0.25 + rise * rise * 6.0;
				if (score < bestScore) {
					bestScore = score;
					best = p.immutable();
				}
			} else if (centre.y < eyeY) {
				if (eye < closestDist) { // below him: the bottom of a crater
					closestDist = eye;
					closest = p.immutable();
				}
			} else if (dx * dx + dz * dz <= 2.0 * 2.0 && dx * dx + dz * dz < baseDist) { // above him: the foot of a scaffold
				baseDist = dx * dx + dz * dz;
				base = p.immutable();
			}
		}
		// a scaffold beats clambering up onto something higher (a roof, a neighbour's wall) to reach it
		if (best != null && (base == null || best.getY() <= from.y + 2.0)) {
			job.reach = REACH;
			return best;
		}
		if (base != null) {
			job.reach = REACH;
			return base;
		}
		if (closest != null && closestDist <= FAR_REACH) {
			job.reach = closestDist + 0.3; // the bottom of a deep crater: hammered from its rim
			return closest;
		}
		return null;
	}

	private static boolean occupiedByOther(ServerLevel level, BlockPos p, UUID self) {
		return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(p),
				e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(self)).isEmpty();
	}

	/**
	 * v0.16.2 (user): before {@code x} goes in, everyone standing inside the damage around it gets out -- villagers and
	 * mobs (the worker too) are lifted onto the nearest safe spot; a player is never moved, so the block waits instead.
	 * False when the block must wait.
	 */
	private static boolean clearHole(ServerLevel level, Job job, Villager worker, BlockPos x) {
		LongOpenHashSet pending = pending(job);
		AABB around = new AABB(x.getX() - 2, x.getY() - ROOF_CHECK, x.getZ() - 2, x.getX() + 3, x.getY() + 2, x.getZ() + 3);
		for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, around,
				e -> e.isAlive() && !e.isSpectator() && !e.isPassenger())) {
			BlockPos feet = e.blockPosition();
			if (!insideDamage(feet, pending)) {
				continue;
			}
			if (e instanceof net.minecraft.world.entity.player.Player) {
				return false;
			}
			BlockPos out = liftSpot(level, feet, pending);
			if (out == null) {
				return false;
			}
			level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.3, e.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
			e.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
			e.setDeltaMovement(Vec3.ZERO);
			e.fallDistance = 0f;
			level.playSound(null, out, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.6f, 1.2f);
			if (e == worker) {
				job.hold = worker.position();
				job.stand = out;
			}
		}
		return true;
	}

	/** The nearest safe spot to climb out to from {@code feet}. */
	private static BlockPos liftSpot(ServerLevel level, BlockPos feet, LongOpenHashSet pending) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(feet.offset(-8, -2, -8), feet.offset(8, ROOF_CHECK, 8))) {
			if (!safeSpot(level, p, pending)) {
				continue;
			}
			double d = p.distSqr(feet);
			if (d < bestDist) {
				bestDist = d;
				best = p.immutable();
			}
		}
		return best;
	}

	/** Puts the current block off until the end of the trip (the last one is left for the next trip). */
	private static void defer(Job job) {
		if (job.index < job.todo.size() - 1) {
			job.todo.add(job.todo.remove(job.index));
		} else {
			job.index++;
		}
		job.deferrals++;
	}

	private static boolean giveUp(Job job) {
		return job.deferrals > job.todo.size() * 2 + 20;
	}

	/** The worker is standing in the gap it wants to fill: it steps to the nearest free spot next to it. */
	private static void stepAside(ServerLevel level, Job job, Villager v, BlockPos gap) {
		for (BlockPos p : BlockPos.withinManhattan(v.blockPosition(), 2, 1, 2)) {
			if (p.equals(gap) || job.todo.contains(p)) {
				continue;
			}
			if (level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
					&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
				job.hold = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
				v.teleportTo(job.hold.x, job.hold.y, job.hold.z);
				return;
			}
		}
	}

	/** True if a living thing is inside the block space at {@code p} (a block is never rebuilt into someone). */
	private static boolean occupied(ServerLevel level, BlockPos p, Villager worker) {
		return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(p),
				e -> e.isAlive() && !e.isSpectator() && (e instanceof net.minecraft.world.entity.player.Player || e == worker)).isEmpty();
	}

	private static void setPhase(Job job, Phase phase) {
		job.phase = phase;
		job.phaseTicks = 0;
	}

	private static boolean inReach(Villager v, BlockPos p, double reach) {
		return v.getEyePosition().distanceTo(Vec3.atCenterOf(p)) <= reach;
	}

	private static boolean high(Villager v, BlockPos p) {
		double dx = p.getX() + 0.5 - v.getX();
		double dz = p.getZ() + 0.5 - v.getZ();
		return p.getY() > v.getY() + 3.0 && dx * dx + dz * dz <= 3.0 * 3.0;
	}

	private static void walk(ServerLevel level, Job job, Villager v, BlockPos target, CityConfig.Repair cfg) {
		if (job.stand == null || job.phaseTicks % 40 == 0 && !safeSpot(level, job.stand, pending(job))) {
			job.stand = workSpot(level, job, v.position(), target);
			if (job.stand == null) {
				// nowhere safe to work it from (yet): later in the trip, or the next trip
				defer(job);
				setPhase(job, Phase.WALK);
				if (giveUp(job)) {
					job.resume = false;
					setPhase(job, Phase.DESCEND);
				}
				return;
			}
		}
		BlockPos stand = job.stand;
		Vec3 spot = Vec3.atBottomCenterOf(stand);
		boolean there = v.position().distanceToSqr(spot) < 0.8 * 0.8 && Math.abs(v.getY() - stand.getY()) < 0.6;
		boolean safeHere = v.onGround() && safeSpot(level, v.blockPosition(), pending(job));
		if (there || (safeHere && inReach(v, target, job.reach))) {
			freeze(v, job);
			job.cooldown = 4;
			setPhase(job, high(v, target) ? Phase.CLIMB : Phase.BUILD);
			return;
		}
		if (job.phaseTicks > Math.min(cfg.reachTimeoutTicks, HOP_TICKS)) {
			// stuck (or fallen somewhere awkward): clamber over to the work spot -- never build from afar
			level.sendParticles(ParticleTypes.CLOUD, v.getX(), v.getY() + 0.3, v.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
			v.teleportTo(spot.x, spot.y, spot.z);
			v.fallDistance = 0f;
			freeze(v, job);
			job.hold = spot;
			job.cooldown = 4;
			setPhase(job, high(v, target) ? Phase.CLIMB : Phase.BUILD);
			return;
		}
		if (job.phaseTicks % 10 == 1) {
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(stand, 0.65f, 0));
			v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		}
	}

	private static void climb(ServerLevel level, Job job, CitySavedData data, Villager v, BlockPos target) {
		hold(v, job);
		if (job.phaseTicks % 8 != 0) {
			return;
		}
		if (inReach(v, target, REACH)) {
			job.reach = REACH;
			job.cooldown = 2;
			setPhase(job, Phase.BUILD);
			return;
		}
		BlockPos feet = v.blockPosition();
		boolean room = level.getBlockState(feet.above(2)).isAir() && CityDamageTracker.airish(level.getBlockState(feet));
		if (target.getY() <= v.getY() + 3.0 || job.scaffold.size() >= MAX_SCAFFOLD || !room) {
			defer(job); // can't climb to it from here: try it from another spot later
			job.resume = !giveUp(job);
			setPhase(job, Phase.DESCEND);
			return;
		}
		job.hold = new Vec3(feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5);
		v.teleportTo(job.hold.x, job.hold.y, job.hold.z);
		BlockState s = Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0).setValue(ScaffoldingBlock.BOTTOM, false);
		CityDamageTracker.suppressed(() -> level.setBlock(feet, s, Block.UPDATE_ALL));
		job.scaffold.add(feet);
		job.scaffoldsPlaced++;
		data.scaffolds().add(feet.asLong());
		data.setDirty();
		v.swing(InteractionHand.MAIN_HAND, true);
		level.playSound(null, feet, s.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.9f, 1.0f);
	}

	private static boolean descend(ServerLevel level, Job job, CitySavedData data, Villager v) {
		if (v == null) {
			return false;
		}
		hold(v, job);
		if (job.scaffold.isEmpty()) {
			if (job.resume) {
				job.resume = false;
				job.stand = null;
				unfreeze(v);
				setPhase(job, Phase.WALK);
				return true;
			}
			return finish(level, job, data, v);
		}
		if (job.phaseTicks % 5 != 0) {
			return true;
		}
		BlockPos top = job.scaffold.remove(job.scaffold.size() - 1);
		if (level.getBlockState(top).is(Blocks.SCAFFOLDING)) {
			CityDamageTracker.suppressed(() -> level.setBlock(top, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
		}
		data.scaffolds().remove(top.asLong());
		data.setDirty();
		job.hold = new Vec3(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
		v.teleportTo(job.hold.x, job.hold.y, job.hold.z);
		return true;
	}

	private static boolean finish(ServerLevel level, Job job, CitySavedData data, Villager v) {
		removeScaffold(level, job, data, v);
		if (v != null) {
			unfreeze(v);
			if (v.getMainHandItem().is(Items.MACE)) {
				v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
			Optional<GlobalPos> site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
			site.ifPresent(g -> v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(g.pos(), 0.6f, 1)));
		}
		return false;
	}

	private static void removeScaffold(ServerLevel level, Job job, CitySavedData data, Villager v) {
		for (int i = job.scaffold.size() - 1; i >= 0; i--) {
			BlockPos p = job.scaffold.get(i);
			if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
				CityDamageTracker.suppressed(() -> level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
			}
			data.scaffolds().remove(p.asLong());
		}
		if (v != null && !job.scaffold.isEmpty()) {
			BlockPos base = job.scaffold.get(0);
			v.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
		}
		job.scaffold.clear();
		data.setDirty();
	}

	private static void cleanupScaffolds(ServerLevel level, CitySavedData data) {
		if (data.scaffolds().isEmpty()) {
			return;
		}
		Long2ObjectOpenHashMap<Job> jobs = JOBS.get(level.dimension());
		for (LongIterator it = data.scaffolds().clone().iterator(); it.hasNext();) {
			long l = it.nextLong();
			BlockPos p = BlockPos.of(l);
			boolean owned = false;
			if (jobs != null) {
				for (Job j : jobs.values()) {
					owned |= j.scaffold.contains(p);
				}
			}
			if (owned || !level.isLoaded(p)) {
				continue;
			}
			if (level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
				CityDamageTracker.suppressed(() -> level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
			}
			data.scaffolds().remove(l);
			data.setDirty();
		}
	}

	private static void freeze(Villager v, Job job) {
		v.getNavigation().stop();
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		v.setNoAi(true);
		v.addTag(FROZEN_TAG);
		job.hold = v.position();
	}

	private static void unfreeze(Villager v) {
		v.setNoAi(false);
		v.removeTag(FROZEN_TAG);
	}

	private static void hold(Villager v, Job job) {
		if (job.hold != null) {
			v.setPos(job.hold.x, job.hold.y, job.hold.z);
		}
		v.setDeltaMovement(Vec3.ZERO);
	}

	private static void lookAt(Villager v, BlockPos p) {
		double dx = p.getX() + 0.5 - v.getX();
		double dz = p.getZ() + 0.5 - v.getZ();
		double dy = p.getY() + 0.5 - v.getEyeY();
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		v.setYRot(yaw);
		v.setYHeadRot(yaw);
		v.setYBodyRot(yaw);
		v.setXRot(Math.max(-60f, Math.min(60f, pitch)));
	}

	/** Rebuilds one block (empty block entity, effects, no-drop window). {@code by} swings its hammer if present. */
	public static void place(ServerLevel level, CityArea city, CitySavedData data, BlockPos pos, BlockState state, Villager by) {
		CityDamageTracker.suppressed(() -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof Container c) {
			c.clearContent(); // containers always come back empty: no loot is ever re-rolled
		}
		data.removeDamage(city.id(), pos);
		data.markRepaired(pos, level.getGameTime() + CityConfig.repair().noDropTicks);
		level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.85f + level.random.nextFloat() * 0.2f);
		if (!state.isAir() && state.getFluidState().isEmpty()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14,
					0.3, 0.3, 0.3, 0.05);
		}
		level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0.5);
		if (by != null) {
			by.swing(InteractionHand.MAIN_HAND, true);
			level.playSound(null, by.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.15f, 1.6f);
		}
	}

	// ---------------------------------------------------------------- hiring

	private static void hire(ServerLevel level, CityArea city, CitySavedData.CityRecord rec, List<Villager> workers, long now) {
		CityConfig.Repair cfg = CityConfig.repair();
		if (workers.size() >= cfg.workersPerCity) {
			rec.lastHire = now; // the clock only runs while the City is short of workers
			return;
		}
		if (rec.lastHire < 0) {
			rec.lastHire = now;
			return;
		}
		if (now - rec.lastHire < cfg.hireDelayTicks) {
			return;
		}
		Villager hired = hireAt(level, city);
		rec.lastHire = now;
		if (hired != null) {
			com.projecthero.mod.ProjectHeroMod.LOGGER.info("[City] the depot hired a Repair Worker at {}", hired.blockPosition());
		}
	}

	/** Spawns a Repair Worker at a free Repair Bench in {@code city} and gives it that bench. Null if there is none. */
	public static Villager hireAt(ServerLevel level, CityArea city) {
		BlockPos centre = city.bounds().getCenter();
		int radius = Math.max(city.bounds().getXSpan(), city.bounds().getZSpan());
		PoiManager poi = level.getPoiManager();
		Optional<BlockPos> bench = poi.findAll(h -> h.is(CityBlocks.REPAIR_BENCH_POI), city::contains, centre, radius, PoiManager.Occupancy.HAS_SPACE)
				.filter(level::isLoaded).findFirst();
		if (bench.isEmpty()) {
			return null;
		}
		BlockPos b = bench.get();
		BlockPos spot = null;
		for (BlockPos p : BlockPos.betweenClosed(b.offset(-2, -1, -2), b.offset(2, 1, 2))) {
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(),
					net.minecraft.core.Direction.UP)) {
				spot = p.immutable();
				break;
			}
		}
		if (spot == null) {
			return null;
		}
		Villager v = EntityType.VILLAGER.create(level);
		if (v == null) {
			return null;
		}
		v.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
		VillagerData data = v.getVillagerData();
		v.setVillagerData(data.setProfession(CityBlocks.REPAIR_WORKER).setLevel(1));
		v.setVillagerXp(1);
		v.refreshBrain(level); // its job behaviours are built for the profession
		poi.take(h -> h.is(CityBlocks.REPAIR_BENCH_POI), (h, p) -> p.equals(b), b, 1);
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), b));
		level.addFreshEntity(v);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.0, v.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
		return v;
	}

	// ---------------------------------------------------------------- hooks

	/** {@code CityBlockDropMixin}: true if a block popping drops at {@code pos} was rebuilt recently (drops nothing). */
	public static boolean suppressDrop(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel sl) || !CityLocator.isCityChunk(sl, ChunkPos.asLong(pos))) {
			return false;
		}
		CitySavedData data = CitySavedData.get(sl);
		return data.hasNoDropEntries() && data.noDropAt(pos, sl.getGameTime());
	}

	/** Entity load: a worker frozen by a job that no longer exists (server restart) gets its AI back. */
	static void onVillagerLoad(Villager v, ServerLevel level) {
		if (v.getTags().contains(FROZEN_TAG) && !busy(level, v.getUUID())) {
			unfreeze(v);
			if (v.getMainHandItem().is(Items.MACE)) {
				v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
		}
	}

	/** The repair board line the Repair Bench shows. */
	public static Component statusLine(ServerLevel level, BlockPos pos) {
		CityArea city = CityLocator.cityAround(level, pos);
		if (city == null) {
			return Component.translatable("block.projecthero.repair_bench.no_city").withStyle(ChatFormatting.GRAY);
		}
		CitySavedData.CityRecord rec = CitySavedData.get(level).city(city.id());
		int pending = rec.pendingBlocks();
		Job job = job(level, city.id());
		if (job != null) {
			return Component.translatable("block.projecthero.repair_bench.working", job.placed, job.todo.size(), pending).withStyle(ChatFormatting.GOLD);
		}
		if (pending == 0) {
			return Component.translatable("block.projecthero.repair_bench.clear").withStyle(ChatFormatting.GREEN);
		}
		long latest = Long.MIN_VALUE;
		for (CitySavedData.DamageArea a : rec.areas.values()) {
			latest = Math.max(latest, a.lastDamage);
		}
		long wait = Math.max(0, CityConfig.repair().repairDelayTicks - (level.getGameTime() - latest));
		return Component.translatable("block.projecthero.repair_bench.pending", pending, (wait + 1199) / 1200).withStyle(ChatFormatting.YELLOW);
	}

	public static void clearSessionState() {
		JOBS.clear();
	}
}
