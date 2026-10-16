package com.projecthero.mod.bluebeetle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.5: the walking paths for Khaji-Da's takeover -- an A* over the cells a player can stand in (something solid
 * underfoot, two blocks of air, no lava), stepping eight ways: up one block (a jump), level, or down as far as three
 * (a drop). Diagonals only where both straight steps are open, so he never clips a corner. No mob needed (vanilla's
 * navigation wants a Mob), so it works on the player himself.
 */
public final class BeetlePath {
	private static final int MAX_DROP = 3;
	private static final int[][] STEPS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

	private BeetlePath() {
	}

	/** Something solid to stand on below {@code pos}, two blocks of open air at it, no lava. */
	public static boolean standable(Level level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState floor = level.getBlockState(below);
		if (floor.getCollisionShape(level, below).isEmpty() || floor.getFluidState().is(FluidTags.LAVA)) {
			return false;
		}
		return open(level, pos) && open(level, pos.above());
	}

	static boolean open(Level level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return s.getCollisionShape(level, pos).isEmpty() && !s.getFluidState().is(FluidTags.LAVA);
	}

	/** The cell he stands in from {@code feet} (looking up to 1 and down to 4 blocks), or null if there is none. */
	public static BlockPos ground(Level level, BlockPos feet) {
		for (int dy = 0; dy <= 4; dy++) {
			BlockPos p = feet.below(dy);
			if (standable(level, p)) {
				return p;
			}
		}
		return standable(level, feet.above()) ? feet.above() : null;
	}

	private record Node(BlockPos pos, double g, double f) {
	}

	/**
	 * A walking path from {@code start} to within {@code reach} blocks (flat, and 1.5 up or down) of {@code goal}: the cells
	 * to walk through, start excluded; empty if already there; null if there is no way on foot inside the budget.
	 */
	public static List<BlockPos> find(Level level, BlockPos start, BlockPos goal, double reach, int budget) {
		BlockPos from = ground(level, start);
		if (from == null) {
			return null;
		}
		if (close(from, goal, reach)) {
			return List.of();
		}
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
		Map<BlockPos, Double> best = new HashMap<>();
		Map<BlockPos, BlockPos> came = new HashMap<>();
		open.add(new Node(from, 0, h(from, goal)));
		best.put(from, 0.0);
		int expanded = 0;
		BlockPos nearest = from;
		double nearestH = h(from, goal);
		while (!open.isEmpty() && expanded < budget) {
			Node n = open.poll();
			if (n.g() > best.getOrDefault(n.pos(), Double.MAX_VALUE) + 1.0e-6) {
				continue;
			}
			expanded++;
			if (close(n.pos(), goal, reach)) {
				return unwind(came, n.pos(), from);
			}
			double hn = h(n.pos(), goal);
			if (hn < nearestH) {
				nearestH = hn;
				nearest = n.pos();
			}
			for (int[] s : STEPS) {
				boolean diag = s[0] != 0 && s[1] != 0;
				BlockPos side = n.pos().offset(s[0], 0, s[1]);
				if (diag && !(passable(level, n.pos().offset(s[0], 0, 0)) && passable(level, n.pos().offset(0, 0, s[1])))) {
					continue;
				}
				BlockPos next = land(level, n.pos(), side);
				if (next == null) {
					continue;
				}
				double cost = (diag ? 1.414 : 1.0) + (next.getY() > n.pos().getY() ? 0.6 : next.getY() < n.pos().getY() ? 0.2 : 0.0);
				double g = n.g() + cost;
				if (g < best.getOrDefault(next, Double.MAX_VALUE)) {
					best.put(next, g);
					came.put(next, n.pos());
					open.add(new Node(next, g, g + h(next, goal)));
				}
			}
		}
		// out of budget: walk as far as the search got if that is closer (then search again from there)
		return nearest.equals(from) || expanded < budget ? null : unwind(came, nearest, from);
	}

	/** Where a step from {@code at} into column {@code side} ends: a jump up one, level, or a drop of up to three. */
	private static BlockPos land(Level level, BlockPos at, BlockPos side) {
		if (standable(level, side.above()) && open(level, at.above(2))) {
			return side.above(); // a jump up (head room above him first)
		}
		if (standable(level, side)) {
			return side;
		}
		if (!open(level, side) || !open(level, side.above())) {
			return null;
		}
		for (int d = 1; d <= MAX_DROP; d++) {
			BlockPos down = side.below(d);
			if (!open(level, down)) {
				return null;
			}
			if (standable(level, down)) {
				return down;
			}
		}
		return null;
	}

	private static boolean passable(Level level, BlockPos pos) {
		return open(level, pos) && open(level, pos.above());
	}

	private static boolean close(BlockPos a, BlockPos goal, double reach) {
		double dx = a.getX() - goal.getX(), dz = a.getZ() - goal.getZ();
		return dx * dx + dz * dz <= reach * reach && Math.abs(a.getY() - goal.getY()) <= 1.5;
	}

	private static double h(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static List<BlockPos> unwind(Map<BlockPos, BlockPos> came, BlockPos end, BlockPos from) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p = end; p != null && !p.equals(from); p = came.get(p)) {
			out.add(p);
		}
		Collections.reverse(out);
		return out;
	}
}
