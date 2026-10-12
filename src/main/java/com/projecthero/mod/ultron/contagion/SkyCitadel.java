package com.projecthero.mod.ultron.contagion;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.block.ContagionBlocks;

import it.unimi.dsi.fastutil.longs.Long2IntMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * v0.16.1: the Sokovia Protocol's <b>sky-citadel</b>, built by code -- never ripping up real terrain:
 * <ul>
 *   <li>a {@link ContagionConfig.Finale#radius}-block disc {@link ContagionConfig.Finale#height} blocks above the ground:
 *       a Citadel Plating floor in the middle with eight plated spokes, Citadel Rock outside them, ragged broken edges, a
 *       raised dais for the Vibranium Core and an inverted cone of hanging rock underneath;</li>
 *   <li>the <b>rising-rock path</b>: Rising Rock stepping stones that spiral up from the ground beside the disc (2x1 steps,
 *       one block up each, 30 degrees round a 4-block spiral) and a bridge onto the edge, so non-fliers can climb.</li>
 * </ul>
 * Blocks are only ever placed into air, and every one is recorded ({@link SokoviaFinale}) and taken away again at the end
 * -- only where the block is still ours. The plan is deterministic from the site, so a restart mid-build resumes it.
 *
 * <p>The crash crater ({@link #crater}) swaps natural top blocks for scorched rock and records the originals; it is put
 * back after {@link ContagionConfig.Finale#craterRevertTicks}.
 */
public final class SkyCitadel {
	/** One planned block. */
	public record Placement(long pos, BlockState state) {
	}

	private SkyCitadel() {
	}

	/** The disc's edge radius at angle {@code a} (ragged). */
	static double edge(double a, long seed) {
		int r = ContagionConfig.finale().radius;
		double n = Math.sin(a * 5 + seed % 7) * 1.2 + Math.sin(a * 11 + seed % 13) * 0.9 + Math.sin(a * 23) * 0.5;
		return r - 1.5 + n;
	}

	private static double hash(int x, int z, long seed) {
		long h = x * 341873128712L + z * 132897987541L + seed;
		h = (h ^ (h >>> 13)) * 0x5bd1e995L;
		return ((h ^ (h >>> 15)) & 0xffff) / 65535.0;
	}

	/** Every block of the disc, floor first (top-down), centred on {@code centre} (the floor block of the middle). */
	public static List<Placement> planDisc(BlockPos centre, long seed) {
		List<Placement> out = new ArrayList<>();
		int r = ContagionConfig.finale().radius + 3;
		BlockState plating = ContagionBlocks.CITADEL_PLATING.defaultBlockState();
		BlockState rock = ContagionBlocks.CITADEL_ROCK.defaultBlockState();
		BlockState light = ContagionBlocks.CITADEL_LIGHT.defaultBlockState();
		BlockState cable = ContagionBlocks.CITADEL_CABLE.defaultBlockState();
		List<Placement> hang = new ArrayList<>();
		List<Placement> under = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				double a = Math.atan2(dz, dx);
				double e = edge(a, seed);
				if (d > e) {
					continue;
				}
				// broken edge: bites out of the outer two blocks
				if (d > e - 2 && hash(dx, dz, seed) < 0.35) {
					continue;
				}
				boolean onSpoke = Math.abs(((Math.toDegrees(a) + 22.5) % 45 + 45) % 45 - 22.5) < (d < 4 ? 90 : 9.0 / Math.max(1.0, d * 0.25));
				boolean inner = d < e * 0.5;
				BlockState floor = inner || onSpoke ? plating : rock;
				// red light strips: every third block down each spoke, and a lit ring round the Core's plaza
				if (onSpoke && d > 4.5 && ((int) Math.round(d)) % 3 == 0 || Math.abs(d - 5.5) < 0.5) {
					floor = light;
				}
				// a raised walkway ring half way out, lit every so often, broken where the spokes cross it
				double ring = e * 0.5;
				if (Math.abs(d - ring) < 1.0 && !onSpoke) {
					out.add(new Placement(centre.offset(dx, 1, dz).asLong(), hash(dx, dz, seed + 21) < 0.18 ? light : plating));
				}
				out.add(new Placement(centre.offset(dx, 0, dz).asLong(), floor));
				// the Core's dais
				if (d <= 3.2) {
					out.add(new Placement(centre.offset(dx, 1, dz).asLong(), plating));
				}
				// a low broken parapet at the rim
				if (d > e - 1.2 && hash(dz, dx, seed + 3) < 0.45) {
					int h = 1 + (int) (hash(dx, dz, seed + 9) * 2.5);
					for (int y = 1; y <= h; y++) {
						out.add(new Placement(centre.offset(dx, y, dz).asLong(), y == h ? plating : rock));
					}
				}
				// hanging rock: an inverted cone, deeper towards the middle
				int depth = 1 + (int) ((e - d) * 0.55 + hash(dx * 3, dz * 3, seed) * 3.0);
				depth = Math.min(depth, 16);
				// rock stalactites near the rim: a few columns hang far deeper than the rest
				if (d > e - 5 && hash(dx * 7, dz * 5, seed + 4) < 0.08) {
					depth += 4 + (int) (hash(dx, dz * 3, seed + 6) * 7);
				}
				for (int y = 1; y <= depth; y++) {
					under.add(new Placement(centre.offset(dx, -y, dz).asLong(), y == depth && hash(dx, dz, seed + 8) < 0.25 ? light : rock));
				}
				// cables torn loose, dangling from the underside near the broken edge
				if (d > e - 4 && hash(dx * 11, dz * 13, seed + 2) < 0.06) {
					int len = 3 + (int) (hash(dx, dz, seed + 12) * 7);
					for (int y = 1; y <= len; y++) {
						hang.add(new Placement(centre.offset(dx, -depth - y, dz).asLong(), cable));
					}
				}
			}
		}
		// four angular Ultron towers between the Lift Engines, and eight lit pylons round the plaza
		for (int k = 0; k < 4; k++) {
			double a = Math.PI / 2 * k + Math.PI / 8; // clear of the bridge (east) and the Lift Engines (45 degrees)
			int tx = (int) Math.round(Math.cos(a) * ContagionConfig.finale().radius * 0.72);
			int tz = (int) Math.round(Math.sin(a) * ContagionConfig.finale().radius * 0.72);
			tower(out, centre.offset(tx, 1, tz), 11 + (int) (hash(k, k * 3, seed) * 5), plating, rock, light);
		}
		for (int k = 0; k < 8; k++) {
			double a = Math.PI * 2 * k / 8 + Math.PI / 8;
			int px = (int) Math.round(Math.cos(a) * 9.5);
			int pz = (int) Math.round(Math.sin(a) * 9.5);
			for (int y = 1; y <= 4; y++) {
				out.add(new Placement(centre.offset(px, y, pz).asLong(), y == 3 ? light : plating));
			}
			out.add(new Placement(centre.offset(px, 5, pz).asLong(), rock));
		}
		out.addAll(under);
		out.addAll(hang);
		return out;
	}

	/**
	 * An angular Ultron tower standing on {@code base}: a 5x5 diamond footprint that narrows in steps as it rises, red light
	 * strips up the four corner edges, buttress fins at the foot and a two-block spike on top.
	 */
	static void tower(List<Placement> out, BlockPos base, int height, BlockState plating, BlockState rock, BlockState light) {
		for (int y = 0; y < height; y++) {
			int r = y < height * 0.35 ? 2 : y < height * 0.75 ? 1 : 0;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) + Math.abs(dz) > r + (r > 0 ? 1 : 0)) {
						continue; // the diamond cut: angular, never a box
					}
					boolean corner = Math.abs(dx) + Math.abs(dz) == r + (r > 0 ? 1 : 0) && (dx == 0 || dz == 0);
					BlockState s = corner && y % 2 == 0 ? light : (y + dx + dz) % 5 == 0 ? rock : plating;
					out.add(new Placement(base.offset(dx, y, dz).asLong(), s));
				}
			}
		}
		for (int[] o : new int[][] { { 3, 0 }, { -3, 0 }, { 0, 3 }, { 0, -3 } }) {
			out.add(new Placement(base.offset(o[0], 0, o[1]).asLong(), plating));
			out.add(new Placement(base.offset(o[0] * 4 / 3, 0, o[1] * 4 / 3).asLong(), rock));
		}
		out.add(new Placement(base.above(height).asLong(), light));
		out.add(new Placement(base.above(height + 1).asLong(), rock));
	}

	/** The rising-rock path from {@code groundY} up to the disc's east edge; bottom step first. */
	public static List<Placement> planPath(BlockPos centre, int groundY) {
		List<Placement> out = new ArrayList<>();
		BlockState step = ContagionBlocks.RISING_ROCK.defaultBlockState();
		int r = ContagionConfig.finale().radius;
		double ax = centre.getX() + r + 7, az = centre.getZ();
		int steps = Math.max(0, centre.getY() - groundY);
		int lastX = 0, lastZ = 0;
		for (int i = 0; i < steps; i++) {
			double a = Math.toRadians(i * 30.0);
			int x = (int) Math.round(ax + Math.cos(a) * 4);
			int z = (int) Math.round(az + Math.sin(a) * 4);
			int y = groundY + i;
			out.add(new Placement(BlockPos.asLong(x, y, z), step));
			double a2 = Math.toRadians(i * 30.0 + 15.0);
			out.add(new Placement(BlockPos.asLong((int) Math.round(ax + Math.cos(a2) * 4), y, (int) Math.round(az + Math.sin(a2) * 4)), step));
			lastX = x;
			lastZ = z;
		}
		// the bridge from the top step back onto the disc
		int y = centre.getY();
		int edgeX = centre.getX() + r - 3;
		int from = Math.min(lastX, edgeX), to = Math.max(lastX, edgeX);
		for (int x = from; x <= to; x++) {
			int z = (int) Math.round(Mth.lerp((x - from) / (double) Math.max(1, to - from), lastX < edgeX ? lastZ : az, lastX < edgeX ? az : lastZ));
			out.add(new Placement(BlockPos.asLong(x, y, z), step));
			out.add(new Placement(BlockPos.asLong(x, y, z + 1), step));
		}
		return out;
	}

	/** Places one planned block if (and only if) its spot is loaded air. True if placed. */
	public static boolean place(ServerLevel level, Placement p) {
		BlockPos pos = BlockPos.of(p.pos());
		if (!level.isLoaded(pos) || pos.getY() >= level.getMaxBuildHeight() || pos.getY() < level.getMinBuildHeight()) {
			return false;
		}
		if (!level.getBlockState(pos).isAir()) {
			return false;
		}
		level.setBlock(pos, p.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		return true;
	}

	/** Takes a recorded block away again (only if it is still a citadel block). */
	public static boolean remove(ServerLevel level, long key, boolean particles) {
		BlockPos pos = BlockPos.of(key);
		if (!level.isLoaded(pos)) {
			return false;
		}
		if (ContagionBlocks.isCitadel(level.getBlockState(pos).getBlock())) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			if (particles) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.02);
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- the crash crater (cosmetic)

	/** A scorched decal of natural top blocks round {@code at} (recorded, reverted later); particles only if the decal is off. */
	public static void crater(ServerLevel level, ContagionState st, BlockPos at, int radius) {
		UltronFx.forced(level, ParticleTypes.EXPLOSION_EMITTER, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 6, 4, 1, 4, 0);
		UltronFx.forced(level, ParticleTypes.FLASH, at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5, 3, 2, 2, 2, 0);
		UltronFx.forced(level, UltronFx.RED_BIG, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 120, radius * 0.4, 1.5, radius * 0.4, 0.2);
		UltronFx.forced(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 60, radius * 0.4, 1.0, radius * 0.4, 0.02);
		if (!ContagionConfig.finale().craterDecal) {
			return;
		}
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > radius - hash(dx, dz, 77) * 2) {
					continue;
				}
				int x = at.getX() + dx, z = at.getZ() + dz;
				BlockPos col = new BlockPos(x, at.getY(), z);
				if (!level.isLoaded(col)) {
					continue;
				}
				BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
				BlockState was = level.getBlockState(top);
				if (NaniteCorruption.corruptedFor(was) == null || st.crater.containsKey(top.asLong())) {
					continue; // natural blocks only, never a block entity
				}
				st.crater.put(top.asLong(), st.stateId(was));
				BlockState scorched = d < radius * 0.45 || hash(dx, dz, 5) < 0.3 ? ContagionBlocks.CITADEL_ROCK.defaultBlockState()
						: Blocks.BLACKSTONE.defaultBlockState();
				level.setBlock(top, scorched, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
		st.craterRevertAt = level.getGameTime() + ContagionConfig.finale().craterRevertTicks;
		st.setDirty();
	}

	/** Puts the crater's recorded blocks back (loaded ones; the rest wait). */
	public static void revertCrater(ServerLevel level, ContagionState st) {
		List<Long> done = new ArrayList<>();
		for (Long2IntMap.Entry e : st.crater.long2IntEntrySet()) {
			BlockPos p = BlockPos.of(e.getLongKey());
			if (!level.isLoaded(p)) {
				continue;
			}
			BlockState now = level.getBlockState(p);
			if (now.is(ContagionBlocks.CITADEL_ROCK) || now.is(Blocks.BLACKSTONE)) {
				BlockState orig = st.state(e.getIntValue());
				if (orig != null) {
					level.setBlock(p, orig, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
			done.add(e.getLongKey());
		}
		for (long k : done) {
			st.crater.remove(k);
		}
		st.craterRevertAt = st.crater.isEmpty() ? -1L : level.getGameTime() + 200;
		st.setDirty();
	}
}
