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
 * the ground it climbs to on a temporary scaffolding column; if it can't get within reach in {@code reachTimeoutTicks} it
 * repairs from where it stands. With no Repair Worker at all, due damage fills in on its own after
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
	private static final double REACH = 4.5;
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
		for (CitySavedData.CityRecord rec : data.allCities()) {
			if (job(level, rec.id) != null) {
				continue;
			}
			CityArea city = CityLocator.byId(level, rec.id);
			if (city == null) {
				continue;
			}
			List<Villager> workers = workers(level, city);
			hire(level, city, rec, workers, now);
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
			setPhase(job, Phase.DESCEND);
			return true;
		}
		BlockPos target = job.todo.get(job.index);
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
				if (--job.cooldown <= 0) {
					place(level, city, data, target, next, v);
					job.index++;
					job.placed++;
					job.cooldown = Math.max(1, cfg.ticksPerBlock);
					BlockPos after = job.index < job.todo.size() ? job.todo.get(job.index) : null;
					if (after != null && !job.remote && !inReach(v, after)) {
						if (high(v, after)) {
							setPhase(job, Phase.CLIMB);
						} else if (job.scaffold.isEmpty()) {
							unfreeze(v);
							setPhase(job, Phase.WALK);
						} else {
							job.remote = true; // already up a scaffold: finish this stretch from up here
						}
					}
				}
			}
			default -> {
			}
		}
		return true;
	}

	private static void setPhase(Job job, Phase phase) {
		job.phase = phase;
		job.phaseTicks = 0;
	}

	private static boolean inReach(Villager v, BlockPos p) {
		return v.getEyePosition().distanceTo(Vec3.atCenterOf(p)) <= REACH;
	}

	private static boolean high(Villager v, BlockPos p) {
		double dx = p.getX() + 0.5 - v.getX();
		double dz = p.getZ() + 0.5 - v.getZ();
		return p.getY() > v.getY() + 3.0 && dx * dx + dz * dz <= 3.0 * 3.0;
	}

	private static void walk(ServerLevel level, Job job, Villager v, BlockPos target, CityConfig.Repair cfg) {
		if (inReach(v, target)) {
			freeze(v, job);
			job.cooldown = 4;
			setPhase(job, Phase.BUILD);
			return;
		}
		if (high(v, target) && v.onGround()) {
			freeze(v, job);
			setPhase(job, Phase.CLIMB);
			return;
		}
		if (job.phaseTicks > cfg.reachTimeoutTicks) {
			job.remote = true; // can't get there: repair from here
			freeze(v, job);
			setPhase(job, Phase.BUILD);
			return;
		}
		if (job.phaseTicks % 10 == 1) {
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.65f, 2));
			v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		}
	}

	private static void climb(ServerLevel level, Job job, CitySavedData data, Villager v, BlockPos target) {
		hold(v, job);
		if (job.phaseTicks % 8 != 0) {
			return;
		}
		if (inReach(v, target)) {
			job.cooldown = 2;
			setPhase(job, Phase.BUILD);
			return;
		}
		BlockPos feet = v.blockPosition();
		boolean room = level.getBlockState(feet.above(2)).isAir() && CityDamageTracker.airish(level.getBlockState(feet));
		if (target.getY() <= v.getY() + 3.0 || job.scaffold.size() >= MAX_SCAFFOLD || !room) {
			job.remote = true;
			job.cooldown = 2;
			setPhase(job, Phase.BUILD);
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
