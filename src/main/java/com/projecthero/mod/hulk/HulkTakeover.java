package com.projecthero.mod.hulk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): "if the player loses control of the hulk 3 times in a row i want the hulk to like completely take over, he
 * will go and smash things for a bit before jumping around and finding a mountain and carving out a cave and sitting in it,
 * make his rampage last 5 minutes and then he runs away". The takeover's phases ({@code HulkState.TAKEOVER_*}, synced in
 * {@code Wild.phase}):
 * <ol>
 *   <li><b>Smash</b> ({@code takeoverSmashTicks}, 75 s): he fights every creature near (the rampage AI) and, with nothing
 *       alive near, smashes the nearest built blocks (planks, bricks, glass, fences, wool...). Block rules hold: no bedrock /
 *       unbreakable blocks, nothing with a block entity (chests keep their contents), nothing harder than the config limit,
 *       and none at all where the server has switched Hulk block-breaking off.</li>
 *   <li><b>Seek</b>: one scan of the loaded terrain within {@code takeoverSearchRange} (300) for a peak well above its
 *       surroundings, and the steep face on his side of it; Super Leap hops toward it. Nothing found, or not reached within
 *       {@code takeoverSeekTicks}: he digs in where he is.</li>
 *   <li><b>Carve</b>: punches a tunnel into the face, slice by slice -- at least 3 wide x 4 tall (more if he is bigger)
 *       and {@code caveDepth} (6) solid slices deep -- and walks in. Carved blocks drop NOTHING (the break particles and
 *       sounds still play), so a cave never leaves a pile of loot. No face: he carves into the nearest wall around him, or
 *       digs a pit shelter straight down (never into bedrock or below the world's floor).</li>
 *   <li><b>Sit</b>: hunched at the back, facing out, breathing.</li>
 *   <li><b>Run</b> (the last {@code takeoverRunTicks}): out of the cave and a leap away -- then the clock ends it and the
 *       player wakes up as Banner wherever he came down ({@code HulkControl}).</li>
 * </ol>
 * The end never depends on any of this: the takeover's hard end time is set when it starts and is never moved.
 */
public final class HulkTakeover {
	/** The takeover's plan (server only, rebuilt on a relog). */
	public static final class Plan {
		/** The first slice's feet block (pit: the top layer's centre). Null = not planned yet. */
		public BlockPos mouth;
		/** Into the slope; {@link Direction#DOWN} for a pit. */
		public Direction dir = Direction.NORTH;
		public int width = 3;
		public int height = 4;
		public int depth = 6;
		public boolean pit;
		/** The next slice to carve, and how many held something solid. */
		public int slice;
		public int solidSlices;
		/** v0.16.4 (in-game check): how many times the mouth was moved forward to the real rock face. */
		public int replans;
		/** Where he sits when it is done. */
		public Vec3 sitSpot;
		long phaseStart;
		int phase = -1;
		BlockPos smashAt;
		long nextScan;
		long nextSmash;
		long nextCarve;
		Vec3 wander;
		long runStart = -1L;
		boolean ranLeap;
	}

	private HulkTakeover() {
	}

	private static HulkConfig.Control cfg() {
		return HulkConfig.control();
	}

	// ---------------------------------------------------------------- the tick

	static void tick(ServerPlayer p, HulkRampageAI.Brain brain, Plan plan, long now) {
		HulkState s = Hulk.state(p);
		long end = s.combat.rampageUntil;
		int phase = s.wild.phase;
		if (now >= end - Math.max(20, cfg().takeoverRunTicks) && phase != HulkState.TAKEOVER_RUN) {
			phase = setPhase(p, plan, HulkState.TAKEOVER_RUN, now);
		}
		if (plan.phase != phase) {
			plan.phase = phase; // a relog: the phase carries on with a fresh plan
			plan.phaseStart = now;
		}
		switch (phase) {
			case HulkState.TAKEOVER_SMASH -> tickSmash(p, brain, plan, s, now);
			case HulkState.TAKEOVER_SEEK -> tickSeek(p, brain, plan, now);
			case HulkState.TAKEOVER_CARVE -> tickCarve(p, brain, plan, now);
			case HulkState.TAKEOVER_SIT -> tickSit(p, plan, now);
			default -> tickRun(p, brain, plan, now);
		}
	}

	static int setPhase(ServerPlayer p, Plan plan, int phase, long now) {
		HulkState n = Hulk.state(p).copy();
		n.wild.phase = phase;
		n.wild.target = -1;
		Hulk.save(p, n);
		plan.phase = phase;
		plan.phaseStart = now;
		return phase;
	}

	private static void syncFace(ServerPlayer p, float yaw) {
		HulkState s = Hulk.state(p);
		if (Math.abs(Mth.wrapDegrees(s.wild.faceYaw - yaw)) > 1.0f || s.wild.target != -1) {
			HulkState n = s.copy();
			n.wild.faceYaw = yaw;
			n.wild.target = -1;
			Hulk.save(p, n);
		}
		HulkRampageAI.face(p, yaw, 10.0f);
	}

	private static float yawOf(Vec3 dir) {
		return (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0f;
	}

	private static Vec3 flatTo(ServerPlayer p, Vec3 at) {
		Vec3 d = new Vec3(at.x - p.getX(), 0, at.z - p.getZ());
		return d.lengthSqr() < 1.0e-6 ? HulkAbilities.flatLook(p) : d.normalize();
	}

	private static double flatDist(ServerPlayer p, Vec3 at) {
		double dx = at.x - p.getX();
		double dz = at.z - p.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static void stand(ServerPlayer p) {
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.3, Math.min(v.y, 0.0), v.z * 0.3));
	}

	// ---------------------------------------------------------------- 1: smash

	private static void tickSmash(ServerPlayer p, HulkRampageAI.Brain brain, Plan plan, HulkState s, long now) {
		if (now - s.wild.startedAt >= Math.max(20, cfg().takeoverSmashTicks)) {
			setPhase(p, plan, HulkState.TAKEOVER_SEEK, now);
			return;
		}
		LivingEntity target = HulkControl.findTarget(p, 32.0);
		if (target != null) {
			HulkControl.syncTarget(p, target.getId());
			HulkRampageAI.fight(p, brain, target, now, () -> HulkControl.findOther(p, target));
			return;
		}
		HulkControl.syncTarget(p, -1);
		ServerLevel level = (ServerLevel) p.level();
		if ((plan.smashAt == null || level.getBlockState(plan.smashAt).isAir()) && now >= plan.nextScan) {
			plan.smashAt = HulkCombat.canBreakBlocks(level) ? findBuilt(level, p, 20) : null;
			plan.nextScan = now + 40L;
		}
		if (HulkAbilities.busy(p) || Hulk.state(p).leaping) {
			return;
		}
		if (plan.smashAt == null) {
			// nothing alive, nothing built: he stomps about, roaring
			if (plan.wander == null || flatDist(p, plan.wander) < 2.0 || now % 80L == 0L) {
				double a = p.getRandom().nextDouble() * Math.PI * 2.0;
				plan.wander = p.position().add(Math.cos(a) * 12.0, 0, Math.sin(a) * 12.0);
			}
			Vec3 dir = flatTo(p, plan.wander);
			syncFace(p, yawOf(dir));
			if (now >= brain.nextMove && p.onGround()) {
				HulkAbilities.driven(p, () -> HulkAbilities.groundSmash(p));
				brain.nextMove = now + 70L + p.getRandom().nextInt(40);
			}
			HulkRampageAI.runAt(p, brain, dir, now);
			return;
		}
		Vec3 c = Vec3.atCenterOf(plan.smashAt);
		Vec3 dir = flatTo(p, c);
		syncFace(p, yawOf(dir));
		HulkRampageAI.aim(p, c, false, 30.0f);
		double reach = HulkRampageAI.reach(p) + 1.0;
		if (HulkAbilities.chest(p).distanceTo(c) <= reach) {
			stand(p);
			if (now >= plan.nextSmash) {
				plan.nextSmash = now + 25L;
				boolean punch = p.getRandom().nextBoolean();
				if (punch) {
					HulkAbilities.driven(p, () -> HulkAbilities.superPunch(p));
				} else {
					HulkAbilities.anim(p, HulkState.ANIM_GROUND_SMASH);
				}
				p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
				smashBlocks(p, c, 2.5, 24);
				plan.smashAt = null;
			}
		} else {
			HulkRampageAI.runAt(p, brain, dir, now);
		}
	}

	/** Blocks people build (not what the world grows). */
	public static boolean built(BlockState st) {
		return st.is(BlockTags.PLANKS) || st.is(BlockTags.STAIRS) || st.is(BlockTags.SLABS) || st.is(BlockTags.FENCES)
				|| st.is(BlockTags.FENCE_GATES) || st.is(BlockTags.WALLS) || st.is(BlockTags.DOORS) || st.is(BlockTags.TRAPDOORS)
				|| st.is(BlockTags.WOOL) || st.is(BlockTags.IMPERMEABLE) || st.is(BlockTags.TERRACOTTA) || st.is(Blocks.GLASS_PANE)
				|| st.is(Blocks.BRICKS) || st.is(Blocks.STONE_BRICKS) || st.is(Blocks.COBBLESTONE) || st.is(Blocks.MOSSY_COBBLESTONE)
				|| st.is(Blocks.BOOKSHELF) || st.is(Blocks.HAY_BLOCK) || st.is(Blocks.SMOOTH_STONE) || st.is(Blocks.POLISHED_ANDESITE)
				|| st.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock;
	}

	/** The nearest built block he may break within {@code r} (loaded chunks only), or null. */
	static BlockPos findBuilt(ServerLevel level, ServerPlayer p, int r) {
		BlockPos o = p.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (!level.hasChunk((o.getX() + dx) >> 4, (o.getZ() + dz) >> 4)) {
					continue;
				}
				for (int dy = -2; dy <= 10; dy++) {
					pos.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
					BlockState st = level.getBlockState(pos);
					if (st.isAir() || !built(st) || !HulkCombat.breakable(level, pos, st) || !level.mayInteract(p, pos)) {
						continue;
					}
					double d = dx * dx + dy * dy * 2 + dz * dz;
					if (d < bestD) {
						bestD = d;
						best = pos.immutable();
					}
				}
			}
		}
		return best;
	}

	/** Smashes up to {@code max} breakable blocks within {@code radius} of {@code c} (not under his feet). */
	static int smashBlocks(ServerPlayer p, Vec3 c, double radius, int max) {
		ServerLevel level = (ServerLevel) p.level();
		if (!HulkCombat.canBreakBlocks(level)) {
			return 0;
		}
		int r = (int) Math.ceil(radius);
		BlockPos o = BlockPos.containing(c);
		List<BlockPos> hits = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(o.offset(-r, -r, -r), o.offset(r, r, r))) {
			if (pos.getY() < p.getBlockY() || Vec3.atCenterOf(pos).distanceTo(c) > radius || !level.isLoaded(pos) || !level.mayInteract(p, pos)) {
				continue;
			}
			if (HulkCombat.breakable(level, pos, level.getBlockState(pos))) {
				hits.add(pos.immutable());
			}
		}
		hits.sort(Comparator.comparingDouble(b -> Vec3.atCenterOf(b).distanceToSqr(c)));
		int n = 0;
		for (BlockPos pos : hits) {
			if (n >= max) {
				break;
			}
			level.destroyBlock(pos, HulkConfig.world().dropBrokenBlocks && n % 3 == 0, p);
			n++;
		}
		if (n > 0) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.2f, 0.6f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			HulkCombat.shake(level, c, 0.4f, 8, 16.0);
		}
		return n;
	}

	// ---------------------------------------------------------------- 2: seek

	private static void tickSeek(ServerPlayer p, HulkRampageAI.Brain brain, Plan plan, long now) {
		if (plan.mouth == null) {
			if (!planMountain(p, plan)) {
				planHere(p, plan); // nothing tall near: dig in where he is
				setPhase(p, plan, HulkState.TAKEOVER_CARVE, now);
				return;
			}
			ServerLevel level = (ServerLevel) p.level();
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.5f, 0.5f);
		}
		Vec3 goal = Vec3.atBottomCenterOf(plan.mouth).subtract(Vec3.atLowerCornerOf(plan.dir.getNormal()).scale(1.0));
		double dist = flatDist(p, goal);
		double dy = goal.y - p.getY();
		if (dist <= 2.5 && Math.abs(dy) <= 3.0) {
			setPhase(p, plan, HulkState.TAKEOVER_CARVE, now);
			return;
		}
		if (now - plan.phaseStart > Math.max(100, cfg().takeoverSeekTicks)) {
			planHere(p, plan); // never got there: dig in where he stands
			setPhase(p, plan, HulkState.TAKEOVER_CARVE, now);
			return;
		}
		Vec3 dir = flatTo(p, goal);
		syncFace(p, yawOf(dir));
		HulkState s = Hulk.state(p);
		if (s.leaping || HulkAbilities.busy(p)) {
			return;
		}
		if (p.onGround() && (dist > 12.0 || dy > 2.5) && now >= brain.nextLeap) {
			// v0.16.4 (user): "jumping around and finding a mountain" -- Super Leap hops
			HulkRampageAI.leapAt(p, dir, Math.min(dist - 1.0, 90.0), dy);
			brain.nextLeap = now + 30L;
			return;
		}
		HulkRampageAI.runAt(p, brain, dir, now);
	}

	private static int surface(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	private record Peak(int x, int y, int z, double score) {
	}

	/**
	 * One scan of the loaded terrain for a mountain: a column well above him and well above the ground 16 blocks round it,
	 * then the steep face on his side. True (and the plan filled in) if one was found.
	 */
	static boolean planMountain(ServerPlayer p, Plan plan) {
		ServerLevel level = (ServerLevel) p.level();
		if (level.dimensionType().hasCeiling()) {
			return false; // the Nether's heightmap is its bedrock roof: he digs in where he is
		}
		int range = Math.max(16, cfg().takeoverSearchRange);
		int px = p.getBlockX();
		int pz = p.getBlockZ();
		int py = p.getBlockY();
		List<Peak> peaks = new ArrayList<>();
		for (int dx = -range; dx <= range; dx += 8) {
			for (int dz = -range; dz <= range; dz += 8) {
				if (dx * dx + dz * dz > range * range) {
					continue;
				}
				int x = px + dx;
				int z = pz + dz;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					continue; // never a spot in an unloaded chunk
				}
				int h = surface(level, x, z);
				if (h < py + 8 || h >= level.getMaxBuildHeight() - 4) {
					continue;
				}
				int sum = 0;
				int count = 0;
				for (int[] o : new int[][] { { 16, 0 }, { -16, 0 }, { 0, 16 }, { 0, -16 } }) {
					if (level.hasChunk((x + o[0]) >> 4, (z + o[1]) >> 4)) {
						sum += surface(level, x + o[0], z + o[1]);
						count++;
					}
				}
				if (count < 2) {
					continue;
				}
				double prominence = h - sum / (double) count;
				if (prominence < 6.0) {
					continue;
				}
				double dist = Math.sqrt(dx * dx + dz * dz);
				peaks.add(new Peak(x, h, z, prominence * 2.0 + (h - py) * 0.5 - dist * 0.03));
			}
		}
		peaks.sort(Comparator.comparingDouble(Peak::score).reversed());
		for (int i = 0; i < Math.min(8, peaks.size()); i++) {
			if (faceOf(level, p, peaks.get(i), plan)) {
				return true;
			}
		}
		return false;
	}

	/** The steep face of {@code peak}, tried from his side first: fills {@code plan} if one is found. */
	private static boolean faceOf(ServerLevel level, ServerPlayer p, Peak peak, Plan plan) {
		Direction toward = Direction.getNearest(p.getX() - peak.x(), 0, p.getZ() - peak.z());
		List<Direction> sides = new ArrayList<>(List.of(toward, toward.getClockWise(), toward.getCounterClockWise(), toward.getOpposite()));
		for (Direction out : sides) {
			for (int k = 3; k <= 48; k++) {
				int x = peak.x() + out.getStepX() * k;
				int z = peak.z() + out.getStepZ() * k;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					break;
				}
				int h = surface(level, x, z);
				if (h > peak.y() - 6) {
					continue; // still near the top
				}
				int behind = surface(level, x - out.getStepX() * 2, z - out.getStepZ() * 2);
				BlockPos mouth = new BlockPos(x, h, z);
				if (behind >= h + 3 && level.getFluidState(mouth).isEmpty() && level.getFluidState(mouth.below()).isEmpty()
						&& h > level.getMinBuildHeight() + 2) {
					fill(p, plan, mouth, out.getOpposite(), false);
					return true;
				}
				if (h < peak.y() - 30) {
					break; // the slope ran out into the valley
				}
			}
		}
		return false;
	}

	/**
	 * v0.16.4 (user, the fallback): no mountain -- the nearest wall round him (a solid block at body height within 10 blocks
	 * in one of the four directions) or, with none, a pit shelter straight down where he stands.
	 */
	public static void planHere(ServerPlayer p, Plan plan) {
		ServerLevel level = (ServerLevel) p.level();
		BlockPos feet = p.blockPosition();
		Direction best = null;
		int bestK = Integer.MAX_VALUE;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int k = 1; k <= 10; k++) {
				BlockPos at = feet.relative(d, k);
				if (!level.isLoaded(at)) {
					break;
				}
				BlockState st = level.getBlockState(at.above());
				if (!st.getCollisionShape(level, at.above()).isEmpty() && HulkCombat.breakable(level, at.above(), st)) {
					if (k < bestK) {
						bestK = k;
						best = d;
					}
					break;
				}
			}
		}
		if (best != null) {
			fill(p, plan, feet.relative(best, bestK), best, false);
		} else {
			fill(p, plan, feet, Direction.DOWN, true);
		}
	}

	private static void fill(ServerPlayer p, Plan plan, BlockPos mouth, Direction dir, boolean pit) {
		plan.mouth = mouth;
		plan.dir = dir;
		plan.pit = pit;
		plan.width = Math.max(3, Math.min(5, (int) Math.ceil(p.getBbWidth()) + 1));
		plan.height = Math.max(4, Math.min(7, (int) Math.ceil(p.getBbHeight()) + 1));
		plan.depth = Math.max(2, cfg().caveDepth);
		plan.slice = 0;
		plan.solidSlices = 0;
		plan.sitSpot = null;
	}

	// ---------------------------------------------------------------- 3: carve

	private static void tickCarve(ServerPlayer p, HulkRampageAI.Brain brain, Plan plan, long now) {
		if (plan.mouth == null) {
			planHere(p, plan);
		}
		// v0.16.4 (in-game check): the mouth was planned at the foot of the slope (forest, open air) and every slice came up
		// empty -- he sat down outside without a cave. Never sit without a hole: move the mouth forward to the first solid
		// rock along the cave line (twice at most), then fall back to a pit dug straight into the ground under him.
		if (!plan.pit && plan.slice >= plan.depth + 10 && plan.solidSlices < plan.depth) {
			BlockPos face = plan.replans < 2 ? solidAhead(p, plan) : null;
			plan.replans++;
			if (face != null) {
				int keep = plan.replans;
				fill(p, plan, face, plan.dir, false);
				plan.replans = keep;
			} else {
				fill(p, plan, p.blockPosition(), Direction.DOWN, true);
			}
			return;
		}
		boolean done = plan.pit ? plan.slice >= plan.height : plan.solidSlices >= plan.depth;
		if (!done && now - plan.phaseStart > 40L * 20L && plan.solidSlices < 2) {
			// out of time with no real hole yet: punch the pit out in one go so he still has his shelter
			if (!plan.pit) {
				fill(p, plan, p.blockPosition(), Direction.DOWN, true);
			}
			while (plan.slice < plan.height) {
				carveSlice(p, plan, plan.slice++);
			}
			done = true;
		}
		if (done || now - plan.phaseStart > 40L * 20L) {
			plan.sitSpot = sitSpot(plan);
			setPhase(p, plan, HulkState.TAKEOVER_SIT, now);
			return;
		}
		Vec3 sliceCentre = sliceCentre(plan, plan.slice);
		Vec3 standAt = plan.pit ? sliceCentre : sliceCentre.subtract(Vec3.atLowerCornerOf(plan.dir.getNormal()).scale(1.5));
		Vec3 into = plan.pit ? HulkAbilities.flatLook(p) : Vec3.atLowerCornerOf(plan.dir.getNormal());
		syncFace(p, yawOf(into));
		boolean close = plan.pit || flatDist(p, standAt) <= 1.6;
		if (!close && !HulkAbilities.busy(p) && now - plan.phaseStart < 30L * 20L && flatDist(p, standAt) < 24.0) {
			HulkRampageAI.runAt(p, brain, flatTo(p, standAt), now);
			if (flatDist(p, standAt) > 6.0) {
				return; // still walking up to it
			}
		} else {
			stand(p);
		}
		if (now < plan.nextCarve) {
			return;
		}
		// punch out the next slice (empty ones are skipped at once)
		for (int tries = 0; tries < 4; tries++) {
			int broke = carveSlice(p, plan, plan.slice);
			plan.slice++;
			if (broke >= 2) {
				plan.solidSlices++;
			}
			if (broke > 0) {
				HulkAbilities.anim(p, plan.slice % 2 == 0 ? HulkState.ANIM_SUPER_PUNCH : HulkState.ANIM_GROUND_SMASH);
				p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
				break;
			}
		}
		plan.nextCarve = now + 8L;
	}

	/**
	 * v0.16.4: the first column along the cave line (from the planned mouth, up to 16 blocks on) whose floor-to-head blocks are
	 * solid rock he may carve -- the real face of the slope -- or null.
	 */
	private static BlockPos solidAhead(ServerPlayer p, Plan plan) {
		ServerLevel level = (ServerLevel) p.level();
		for (int k = 0; k <= 16; k++) {
			BlockPos at = plan.mouth.relative(plan.dir, plan.slice + k); // on past the empty slices already tried
			int solid = 0;
			for (int h = 0; h < plan.height; h++) {
				BlockPos b = at.above(h);
				BlockState st = level.getBlockState(b);
				if (level.isLoaded(b) && !st.isAir() && !st.is(net.minecraft.tags.BlockTags.LEAVES) && HulkCombat.breakable(level, b, st)) {
					solid++;
				}
			}
			if (solid >= plan.height - 1) {
				return at.relative(plan.dir.getOpposite());
			}
		}
		return null;
	}

	/** The middle of slice {@code k}'s floor. */
	private static Vec3 sliceCentre(Plan plan, int k) {
		if (plan.pit) {
			return Vec3.atBottomCenterOf(plan.mouth.below(k + 1));
		}
		return Vec3.atBottomCenterOf(plan.mouth.relative(plan.dir, k));
	}

	private static Vec3 sitSpot(Plan plan) {
		if (plan.pit) {
			return Vec3.atBottomCenterOf(plan.mouth.below(Math.max(1, plan.slice)));
		}
		return Vec3.atBottomCenterOf(plan.mouth.relative(plan.dir, Math.max(0, plan.slice - 2)));
	}

	/** Carves slice {@code k} of the cave (or layer {@code k} of the pit). Returns how many blocks broke. */
	public static int carveSlice(ServerPlayer p, Plan plan, int k) {
		ServerLevel level = (ServerLevel) p.level();
		int broke = 0;
		int lo = -(plan.width - 1) / 2;
		int hi = plan.width / 2;
		if (plan.pit) {
			BlockPos layer = plan.mouth.below(k + 1);
			for (int a = lo; a <= hi; a++) {
				for (int b = lo; b <= hi + 1; b++) {
					broke += carveBlock(level, p, layer.offset(a, 0, b));
				}
			}
		} else {
			BlockPos centre = plan.mouth.relative(plan.dir, k);
			Direction side = plan.dir.getClockWise();
			for (int w = lo; w <= hi; w++) {
				for (int h = 0; h < plan.height; h++) {
					broke += carveBlock(level, p, centre.relative(side, w).above(h));
				}
			}
		}
		if (broke > 0) {
			Vec3 c = sliceCentre(plan, k).add(0, plan.pit ? 0.5 : plan.height * 0.5, 0);
			BlockState dust = level.getBlockState(plan.mouth.below());
			if (dust.isAir()) {
				dust = Blocks.STONE.defaultBlockState();
			}
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust), c.x, c.y, c.z, 30, 1.0, 1.0, 1.0, 0.25);
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.8f, 0.5f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.2f, 0.5f);
			HulkCombat.shake(level, c, 0.35f, 6, 18.0);
		}
		return broke;
	}

	/** One cave block: never bedrock / unbreakable / a block entity / fluid / outside the world or an unloaded chunk; drops nothing. */
	private static int carveBlock(ServerLevel level, ServerPlayer p, BlockPos pos) {
		if (pos.getY() <= level.getMinBuildHeight() + 1 || pos.getY() >= level.getMaxBuildHeight() - 1 || !level.isLoaded(pos)
				|| !level.mayInteract(p, pos) || !HulkCombat.canBreakBlocks(level)) {
			return 0;
		}
		BlockState st = level.getBlockState(pos);
		if (st.isAir() || st.is(Blocks.BEDROCK) || !HulkCombat.breakable(level, pos, st)) {
			return 0;
		}
		level.destroyBlock(pos, false, p);
		// v0.16.4 (in-game check): leaves and plants are cleared but do not count -- a tunnel through a canopy is not a cave
		return st.is(net.minecraft.tags.BlockTags.LEAVES) || st.canBeReplaced() ? 0 : 1;
	}

	/** Tests: carve the whole plan at once. Returns the blocks broken. */
	public static int carveAll(ServerPlayer p, Plan plan) {
		int total = 0;
		int limit = plan.pit ? plan.height : plan.depth + 10;
		for (int k = 0; k < limit; k++) {
			int broke = carveSlice(p, plan, k);
			total += broke;
			plan.slice = k + 1;
			if (broke >= 2) {
				plan.solidSlices++;
			}
			if (!plan.pit && plan.solidSlices >= plan.depth) {
				break;
			}
		}
		return total;
	}

	// ---------------------------------------------------------------- 4: sit

	private static void tickSit(ServerPlayer p, Plan plan, long now) {
		ServerLevel level = (ServerLevel) p.level();
		if (plan.sitSpot != null && flatDist(p, plan.sitSpot) > 1.0 && now - plan.phaseStart < 100L && !HulkAbilities.busy(p)) {
			Vec3 dir = flatTo(p, plan.sitSpot);
			syncFace(p, yawOf(dir));
			AbilityHelpers.launchSelf(p, new Vec3(dir.x * 0.25, p.onGround() ? -0.05 : p.getDeltaMovement().y - 0.08, dir.z * 0.25));
			return;
		}
		stand(p);
		// facing out of the cave
		Vec3 out = plan.mouth == null || plan.pit ? HulkAbilities.flatLook(p) : Vec3.atLowerCornerOf(plan.dir.getOpposite().getNormal());
		syncFace(p, yawOf(out));
		long t = now - plan.phaseStart;
		if (t % 50L == 0L) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.4f, 0.45f);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_AMBIENT, SoundSource.PLAYERS, 0.5f, 0.45f);
			Vec3 head = p.getEyePosition().add(out.scale(0.6));
			level.sendParticles(ParticleTypes.CLOUD, head.x, head.y, head.z, 3, 0.1, 0.05, 0.1, 0.01);
		}
		if (t % 10L == 0L) {
			level.sendParticles(HulkAbilities.GAMMA_DEEP, p.getX(), p.getY() + p.getBbHeight() * 0.4, p.getZ(), 1, 0.4, p.getBbHeight() * 0.2, 0.4, 0.0);
		}
	}

	// ---------------------------------------------------------------- 5: run

	private static void tickRun(ServerPlayer p, HulkRampageAI.Brain brain, Plan plan, long now) {
		if (plan.runStart < 0L) {
			plan.runStart = now;
			ServerLevel level = (ServerLevel) p.level();
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.0f, 0.6f);
		}
		Vec3 out = plan.mouth == null || plan.pit ? HulkAbilities.flatLook(p) : Vec3.atLowerCornerOf(plan.dir.getOpposite().getNormal());
		syncFace(p, yawOf(out));
		HulkState s = Hulk.state(p);
		if (s.leaping || HulkAbilities.busy(p)) {
			return;
		}
		long t = now - plan.runStart;
		if (!plan.ranLeap && (t >= 30L || plan.pit) && p.onGround()) {
			// "then he runs away": out and a leap off
			plan.ranLeap = true;
			HulkRampageAI.leapAt(p, out, 25.0, plan.pit ? plan.height + 2.0 : 0.5);
			return;
		}
		if (!plan.ranLeap) {
			HulkRampageAI.runAt(p, brain, out, now);
		}
	}
}
