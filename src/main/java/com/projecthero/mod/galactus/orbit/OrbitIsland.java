package com.projecthero.mod.galactus.orbit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;

/**
 * v0.17.2 Galactus raid v2: one meteor island of Stage 1 -- real blocks, its shape a pure function of its seed (a flat
 * rock top on the island plane, a jagged cone hanging under it, amethyst crystals and crying-obsidian veins), so building,
 * shattering and re-forming always agree and nothing has to be recorded. States: {@link #INTACT} -> {@link #MARKED} (Galactus
 * picked it: it glows red) -> {@link #GONE} (smashed into flying rock) -> {@link #REFORMING} (the rock drifts back) ->
 * intact. The Anchor is index 0 and is never smashed.
 */
public final class OrbitIsland {
	public static final int INTACT = 0, MARKED = 1, GONE = 2, REFORMING = 3;

	public final int index;
	public final int x, z;
	public final int radius;
	public final long seed;
	public int state = INTACT;
	public long stateAt;

	public OrbitIsland(int index, int x, int z, int radius, long seed) {
		this.index = index;
		this.x = x;
		this.z = z;
		this.radius = radius;
		this.seed = seed;
	}

	public boolean anchor() {
		return index == 0;
	}

	public boolean standing() {
		return state == INTACT || state == MARKED;
	}

	/** The middle of its top surface (where a player stands). */
	public Vec3 top() {
		return new Vec3(x + 0.5, GalactusOrbit.PLANE_Y + 1, z + 0.5);
	}

	/** Is {@code at} (a player's feet) over this island's top? */
	public boolean holds(Vec3 at, double margin) {
		double dx = at.x - (x + 0.5), dz = at.z - (z + 0.5);
		return dx * dx + dz * dz <= (radius + margin) * (radius + margin);
	}

	public AABB area() {
		return new AABB(x - radius - 1, GalactusOrbit.PLANE_Y - radius * 2 - 2, z - radius - 1, x + radius + 2, GalactusOrbit.PLANE_Y + 6,
				z + radius + 2);
	}

	// ---------------------------------------------------------------- shape

	private static double hash(long seed, int a, int b) {
		long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** The edge's radius in direction {@code angle} (smooth noise round the rim). */
	private double rim(double angle) {
		double s = angle / (Math.PI * 2) * 8;
		int i = Mth.floor(s);
		double f = s - i;
		f = f * f * (3 - 2 * f);
		double a = hash(seed, i & 7, 11), b = hash(seed, (i + 1) & 7, 11);
		return radius * (0.82 + 0.3 * Mth.lerp(f, a, b));
	}

	/** Every block of the island (position, state), always in the same order. */
	public void blocks(BiConsumer<BlockPos, BlockState> out) {
		int top = GalactusOrbit.PLANE_Y;
		int r = radius + 2;
		boolean spire = !anchor() && hash(seed, 7, 7) < 0.45;
		int spireX = x + (int) Math.round((hash(seed, 3, 1) - 0.5) * radius * 0.9);
		int spireZ = z + (int) Math.round((hash(seed, 3, 2) - 0.5) * radius * 0.9);
		int spireH = 3 + (int) (hash(seed, 3, 3) * 4);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				double edge = rim(Math.atan2(dz, dx) + Math.PI);
				if (d > edge) {
					continue;
				}
				double t = d / edge;
				double n = hash(seed, dx, dz);
				int depth = (int) ((1.0 - t * t) * (radius * 1.25) + 1 + n * 2.2);
				for (int y = top - depth; y <= top; y++) {
					m.set(x + dx, y, z + dz);
					out.accept(m.immutable(), pick(y == top, top - y, t, x + dx, y, z + dz));
				}
				// crystals on the top
				if (y0Crystal(dx, dz, t)) {
					m.set(x + dx, top + 1, z + dz);
					out.accept(m.immutable(), Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
				}
			}
		}
		if (spire) {
			for (int y = 1; y <= spireH; y++) {
				out.accept(new BlockPos(spireX, top + y, spireZ), y == spireH ? Blocks.BUDDING_AMETHYST.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState());
			}
			out.accept(new BlockPos(spireX, top + spireH + 1, spireZ), Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
		}
	}

	private boolean y0Crystal(int dx, int dz, double t) {
		return t > 0.25 && t < 0.85 && hash(seed, dx * 3 + 1, dz * 5 + 2) < (anchor() ? 0.02 : 0.045);
	}

	private BlockState pick(boolean surface, int below, double t, int bx, int by, int bz) {
		double n = hash(seed, bx * 31 + by, bz * 17 - by);
		if (surface) {
			if (anchor() && t < 0.3) {
				return n < 0.5 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			}
			if (n < 0.34) {
				return Blocks.TUFF.defaultBlockState();
			}
			if (n < 0.58) {
				return Blocks.DEEPSLATE.defaultBlockState();
			}
			if (n < 0.72) {
				return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
			}
			if (n < 0.84) {
				return Blocks.CALCITE.defaultBlockState();
			}
			if (n < 0.93) {
				return Blocks.SMOOTH_BASALT.defaultBlockState();
			}
			return Blocks.AMETHYST_BLOCK.defaultBlockState();
		}
		if (n < 0.06) {
			return Blocks.CRYING_OBSIDIAN.defaultBlockState();
		}
		if (n < 0.1 && below > 2) {
			return Blocks.AMETHYST_BLOCK.defaultBlockState();
		}
		if (n < 0.45) {
			return Blocks.BLACKSTONE.defaultBlockState();
		}
		if (n < 0.75) {
			return Blocks.BASALT.defaultBlockState();
		}
		return Blocks.DEEPSLATE.defaultBlockState();
	}

	// ---------------------------------------------------------------- build / shatter / re-form

	public int build(ServerLevel level) {
		int[] n = { 0 };
		blocks((pos, st) -> {
			level.setBlock(pos, st, 2 | 16);
			n[0]++;
		});
		return n[0];
	}

	/** Smashed: the blocks go and a share of them fly apart from {@code impact}. */
	public void shatter(ServerLevel level, Vec3 impact, int debris) {
		List<BlockPos> all = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		blocks((pos, st) -> {
			all.add(pos);
			states.add(st);
		});
		// clusters first (no neighbour updates either way, so nothing pops off as an item)
		for (int i = all.size() - 1; i >= 0; i--) {
			level.setBlock(all.get(i), Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		int step = Math.max(1, all.size() / Math.max(1, debris));
		var rnd = level.getRandom();
		for (int i = 0; i < all.size(); i += step) {
			BlockPos p = all.get(i);
			Vec3 from = Vec3.atLowerCornerOf(p);
			Vec3 away = Vec3.atCenterOf(p).subtract(impact).normalize();
			double dist = 14 + rnd.nextDouble() * 22;
			Vec3 to = from.add(away.x * dist, away.y * dist * 0.4 - 10 - rnd.nextDouble() * 18, away.z * dist);
			Quaternionf spin = new Quaternionf().rotateXYZ(rnd.nextFloat() * 6f, rnd.nextFloat() * 6f, rnd.nextFloat() * 6f);
			float s = 0.7f + rnd.nextFloat() * 0.5f;
			OrbitDebris.fly(level, states.get(i), from, to, s, new Quaternionf(), spin, 1, 50 + rnd.nextInt(30), 0);
		}
		state = GONE;
		stateAt = level.getGameTime();
	}

	/** The rock drifts back over {@code ticks}; {@link #finishReform} sets the blocks. */
	public void beginReform(ServerLevel level, int ticks, int debris) {
		List<BlockPos> all = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		blocks((pos, st) -> {
			all.add(pos);
			states.add(st);
		});
		int step = Math.max(1, all.size() / Math.max(1, debris));
		var rnd = level.getRandom();
		for (int i = 0; i < all.size(); i += step) {
			Vec3 to = Vec3.atLowerCornerOf(all.get(i));
			double a = rnd.nextDouble() * Math.PI * 2;
			double dist = 12 + rnd.nextDouble() * 16;
			Vec3 from = to.add(Math.cos(a) * dist, -8 - rnd.nextDouble() * 14, Math.sin(a) * dist);
			Quaternionf startSpin = new Quaternionf().rotateXYZ(rnd.nextFloat() * 6f, rnd.nextFloat() * 6f, rnd.nextFloat() * 6f);
			OrbitDebris.fly(level, states.get(i), from, to, 1.0f, startSpin, new Quaternionf(), 1, ticks, 3);
		}
		state = REFORMING;
		stateAt = level.getGameTime();
		level.playSound(null, x + 0.5, GalactusOrbit.PLANE_Y, z + 0.5, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 3.0f, 0.5f);
	}

	public void finishReform(ServerLevel level) {
		build(level);
		state = INTACT;
		stateAt = level.getGameTime();
		level.playSound(null, x + 0.5, GalactusOrbit.PLANE_Y, z + 0.5, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3.0f, 0.6f);
	}

	/** Every block gone (no debris): the hull taking over, a wipe. */
	public void remove(ServerLevel level) {
		blocks((pos, st) -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16));
	}

	// ---------------------------------------------------------------- save

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("I", index);
		t.putInt("X", x);
		t.putInt("Z", z);
		t.putInt("R", radius);
		t.putLong("S", seed);
		t.putInt("St", state);
		t.putLong("At", stateAt);
		return t;
	}

	public static OrbitIsland load(CompoundTag t) {
		OrbitIsland i = new OrbitIsland(t.getInt("I"), t.getInt("X"), t.getInt("Z"), t.getInt("R"), t.getLong("S"));
		i.state = t.getInt("St");
		i.stateAt = t.getLong("At");
		return i;
	}

	/**
	 * The Stage 1 field round {@code origin}: the Anchor (radius 7) in the middle, a ring of 6 at 15 blocks, then
	 * {@code outer} more on the next ring (hex spacing 15, radius 5: 5-block gaps), each with its own seed.
	 */
	public static List<OrbitIsland> field(int ox, int oz, int outer, long seed) {
		List<OrbitIsland> out = new ArrayList<>();
		out.add(new OrbitIsland(0, ox, oz, 7, seed));
		double spacing = 15.0;
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3;
			out.add(new OrbitIsland(out.size(), ox + (int) Math.round(Math.cos(a) * spacing), oz + (int) Math.round(Math.sin(a) * spacing), 5,
					seed * 31 + out.size()));
		}
		// the second hex ring: 6 corners (2 spacings out) and 6 edges (between them); edges first so a small field is round
		List<double[]> ring = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			double a1 = i * Math.PI / 3, a2 = (i + 1) * Math.PI / 3;
			ring.add(new double[] { (Math.cos(a1) + Math.cos(a2)) * spacing, (Math.sin(a1) + Math.sin(a2)) * spacing });
		}
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3;
			ring.add(new double[] { Math.cos(a) * spacing * 2, Math.sin(a) * spacing * 2 });
		}
		int[] order = { 0, 3, 1, 4, 2, 5, 6, 9, 7, 10, 8, 11 };
		for (int k = 0; k < Math.min(outer, 12); k++) {
			double[] p = ring.get(order[k]);
			out.add(new OrbitIsland(out.size(), ox + (int) Math.round(p[0]), oz + (int) Math.round(p[1]), 5, seed * 31 + out.size()));
		}
		return out;
	}
}
