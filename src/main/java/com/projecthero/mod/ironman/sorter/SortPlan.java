package com.projecthero.mod.ironman.sorter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16: the Stark Sorter Bot's filing plan -- which containers around a Sorting Station hold which
 * {@link SortCategory categories}, and where each incoming stack should go.
 *
 * <h2>How the plan is decided</h2>
 * <ol>
 *   <li><b>Scan</b> ({@link #scan}): every chest, trapped chest and barrel whose centre is within the radius of the
 *       station. A double chest is ONE {@link Target} (keyed by its lower half) with 54 slots.</li>
 *   <li><b>Granularity</b>: the categories present -- in the station <em>and</em> already in the containers, so a chest
 *       that is "the wood chest" keeps being the wood chest -- become {@link Bucket buckets}. With 1-3 containers they
 *       start as the four coarse {@link SortCategory.Group groups}; with 4+ they start as the thirteen fine
 *       categories. While there are more buckets than containers the smallest bucket (by stack count) is merged into
 *       its smallest <em>sibling</em> (same coarse group) if it has one, else into the smallest bucket overall.</li>
 *   <li><b>Assignment</b>: containers that already hold things are matched to the bucket that makes up the largest
 *       share of their contents first (one per bucket); buckets still without a container take the nearest empty
 *       one. Left-over containers go to the bucket they already hold most of, or -- if empty -- to the bucket with the
 *       most stacks per container, so the biggest categories get several chests.</li>
 * </ol>
 *
 * <h2>Where a stack goes</h2>
 * {@link #destinationFor}: a container of its bucket that already holds the identical item, then any container of
 * its bucket with room (in assignment order -- that is the "next chest of the same category" overflow), then any
 * container anywhere already holding the identical item, then a container of a sibling bucket, then any container
 * with room. {@code null} means nothing nearby can take it and it stays in the station.
 */
public final class SortPlan {
	/** A container the bot can file into. {@code blocks} has one entry, or two for a double chest. */
	public record Target(BlockPos key, List<BlockPos> blocks, Vec3 approach, Vec3 center) {
		/** The live container, or null if the block was broken / changed since the scan. */
		public Container resolve(Level level) {
			BlockState state = level.getBlockState(key);
			if (state.getBlock() instanceof ChestBlock chest) {
				return ChestBlock.getContainer(chest, state, level, key, true);
			}
			if (state.getBlock() instanceof BarrelBlock && level.getBlockEntity(key) instanceof BarrelBlockEntity barrel) {
				return barrel;
			}
			return null;
		}
	}

	/** A set of categories that share containers. */
	public static final class Bucket {
		final EnumSet<SortCategory> categories = EnumSet.noneOf(SortCategory.class);
		final List<Target> targets = new ArrayList<>();
		int weight;

		public Set<SortCategory> categories() {
			return categories;
		}

		public List<Target> targets() {
			return targets;
		}

		public int weight() {
			return weight;
		}

		boolean sharesGroupWith(Bucket other) {
			for (SortCategory a : categories) {
				for (SortCategory b : other.categories) {
					if (a.group() == b.group()) {
						return true;
					}
				}
			}
			return false;
		}

		/** A short readable name: "Wood", "Blocks", "Food + Redstone". */
		public Component displayName() {
			for (SortCategory.Group g : SortCategory.Group.values()) {
				boolean whole = true;
				for (SortCategory c : SortCategory.values()) {
					if (c.group() == g && !categories.contains(c)) {
						whole = false;
						break;
					}
				}
				if (whole && categories.stream().allMatch(c -> c.group() == g)) {
					return g.displayName();
				}
			}
			MutableComponent out = Component.empty();
			int shown = 0;
			for (SortCategory c : categories) {
				if (shown == 2) {
					out.append(" +" + (categories.size() - 2));
					break;
				}
				if (shown > 0) {
					out.append(" + ");
				}
				out.append(c.displayName());
				shown++;
			}
			return out;
		}
	}

	private final List<Target> targets;
	private final List<Bucket> buckets;
	private final Map<SortCategory, Bucket> byCategory = new EnumMap<>(SortCategory.class);
	private final boolean coarse;

	private SortPlan(List<Target> targets, List<Bucket> buckets, boolean coarse) {
		this.targets = targets;
		this.buckets = buckets;
		this.coarse = coarse;
		for (Bucket b : buckets) {
			for (SortCategory c : b.categories) {
				byCategory.put(c, b);
			}
		}
		// categories nothing was seen of (items added to the station mid-run): the heaviest bucket of their group
		for (SortCategory c : SortCategory.values()) {
			if (byCategory.containsKey(c)) {
				continue;
			}
			buckets.stream().filter(b -> b.categories.stream().anyMatch(o -> o.group() == c.group()))
					.max(Comparator.comparingInt(b -> b.weight)).ifPresent(b -> byCategory.put(c, b));
		}
	}

	public List<Target> targets() {
		return targets;
	}

	public List<Bucket> buckets() {
		return buckets;
	}

	/** True when the plan was built from the four coarse groups (1-3 containers). */
	public boolean coarse() {
		return coarse;
	}

	/** The bucket that {@code category} is filed under, or null. */
	public Bucket bucketOf(SortCategory category) {
		return byCategory.get(category);
	}

	/** The bucket a target was assigned to, or null. */
	public Bucket bucketOf(Target target) {
		for (Bucket b : buckets) {
			if (b.targets.contains(target)) {
				return b;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ scanning

	/**
	 * Every chest / trapped chest / barrel whose centre is within {@code radius} blocks of {@code center}'s centre,
	 * nearest first. Double chests are counted once. {@code filter} (may be null) narrows the blocks considered --
	 * the game tests use it to stay inside their own structure.
	 */
	public static List<Target> scan(Level level, BlockPos center, int radius, Predicate<BlockPos> filter) {
		List<Target> out = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		double r2 = (double) radius * radius;
		for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
				center.offset(radius, radius, radius))) {
			if (mutable.distSqr(center) > r2 || mutable.equals(center)) {
				continue;
			}
			BlockState state = level.getBlockState(mutable);
			boolean chest = state.getBlock() instanceof ChestBlock;
			boolean barrel = state.getBlock() instanceof BarrelBlock;
			if (!chest && !barrel) {
				continue;
			}
			BlockPos pos = mutable.immutable();
			if (seen.contains(pos) || (filter != null && !filter.test(pos))) {
				continue;
			}
			if (barrel) {
				if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity)) {
					continue;
				}
				seen.add(pos);
				Direction facing = state.getValue(BarrelBlock.FACING);
				Vec3 c = Vec3.atCenterOf(pos);
				out.add(new Target(pos, List.of(pos), approachPoint(c, facing, 0.5), c));
				continue;
			}
			List<BlockPos> blocks = new ArrayList<>();
			blocks.add(pos);
			if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
				BlockState otherState = level.getBlockState(other);
				if (otherState.is(state.getBlock()) && otherState.getValue(ChestBlock.TYPE) != ChestType.SINGLE
						&& other.relative(ChestBlock.getConnectedDirection(otherState)).equals(pos)) {
					blocks.add(other);
				}
			}
			blocks.sort(Comparator.naturalOrder());
			seen.addAll(blocks);
			if (blocks.size() > 1 && filter != null && !filter.test(blocks.get(1))) {
				continue;
			}
			Vec3 c = blocks.size() == 1 ? Vec3.atCenterOf(blocks.get(0))
					: Vec3.atCenterOf(blocks.get(0)).add(Vec3.atCenterOf(blocks.get(1))).scale(0.5);
			out.add(new Target(blocks.get(0), List.copyOf(blocks), approachPoint(c, state.getValue(ChestBlock.FACING), 0.0), c));
		}
		Vec3 origin = Vec3.atCenterOf(center);
		out.sort(Comparator.comparingDouble(t -> t.center().distanceToSqr(origin)));
		return out;
	}

	/** Where the bot hovers while filing: just in front of the container's opening, a little above it. */
	private static Vec3 approachPoint(Vec3 center, Direction facing, double extraUp) {
		if (facing == Direction.UP) {
			return center.add(0, 0.85, 0);
		}
		if (facing == Direction.DOWN) {
			return center.add(0, -1.45, 0);
		}
		return center.add(facing.getStepX() * 0.95, 0.05 + extraUp, facing.getStepZ() * 0.95);
	}

	// ------------------------------------------------------------------ building

	/** Build a plan for filing {@code incoming} into {@code targets}. {@code targets} must not be empty. */
	public static SortPlan build(Level level, List<Target> targets, List<ItemStack> incoming) {
		int n = targets.size();
		boolean coarse = n <= 3;

		// per-target contents, and the overall weight of every category (station + containers)
		List<EnumMap<SortCategory, Integer>> contents = new ArrayList<>();
		List<Integer> totals = new ArrayList<>();
		EnumMap<SortCategory, Integer> weights = new EnumMap<>(SortCategory.class);
		for (ItemStack s : incoming) {
			if (!s.isEmpty()) {
				weights.merge(SortCategory.of(s), 1, Integer::sum);
			}
		}
		for (Target t : targets) {
			EnumMap<SortCategory, Integer> m = new EnumMap<>(SortCategory.class);
			int total = 0;
			Container c = t.resolve(level);
			if (c != null) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack s = c.getItem(i);
					if (!s.isEmpty()) {
						SortCategory cat = SortCategory.of(s);
						m.merge(cat, 1, Integer::sum);
						weights.merge(cat, 1, Integer::sum);
						total++;
					}
				}
			}
			contents.add(m);
			totals.add(total);
		}

		// 1. initial buckets
		List<Bucket> buckets = new ArrayList<>();
		if (coarse) {
			for (SortCategory.Group g : SortCategory.Group.values()) {
				Bucket b = new Bucket();
				for (SortCategory c : SortCategory.values()) {
					if (c.group() == g) {
						b.categories.add(c);
						b.weight += weights.getOrDefault(c, 0);
					}
				}
				if (b.weight > 0) {
					buckets.add(b);
				}
			}
		} else {
			for (SortCategory c : SortCategory.values()) {
				int w = weights.getOrDefault(c, 0);
				if (w > 0) {
					Bucket b = new Bucket();
					b.categories.add(c);
					b.weight = w;
					buckets.add(b);
				}
			}
		}
		if (buckets.isEmpty()) {
			Bucket all = new Bucket();
			all.categories.addAll(EnumSet.allOf(SortCategory.class));
			buckets.add(all);
		}

		// 2. merge the smallest buckets (sibling first) until every bucket can have a container
		Comparator<Bucket> small = Comparator.<Bucket>comparingInt(b -> b.weight)
				.thenComparingInt(b -> b.categories.iterator().next().ordinal());
		while (buckets.size() > n) {
			Bucket smallest = buckets.stream().min(small).orElseThrow();
			Bucket partner = buckets.stream().filter(b -> b != smallest && b.sharesGroupWith(smallest)).min(small)
					.orElseGet(() -> buckets.stream().filter(b -> b != smallest).min(small).orElseThrow());
			partner.categories.addAll(smallest.categories);
			partner.weight += smallest.weight;
			buckets.remove(smallest);
		}

		// 3. assign containers
		Map<Target, Bucket> owner = new java.util.HashMap<>();
		record Pair(int target, Bucket bucket, double score) {
		}
		List<Pair> pairs = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (totals.get(i) == 0) {
				continue;
			}
			for (Bucket b : buckets) {
				double score = share(contents.get(i), totals.get(i), b);
				if (score > 0) {
					pairs.add(new Pair(i, b, score));
				}
			}
		}
		pairs.sort(Comparator.comparingDouble((Pair p) -> -p.score()).thenComparingInt(Pair::target));
		// a. affinity: each bucket's best already-matching container
		for (Pair p : pairs) {
			Target t = targets.get(p.target());
			if (!owner.containsKey(t) && p.bucket().targets.isEmpty()) {
				assign(owner, t, p.bucket());
			}
		}
		// b. buckets still without a container: nearest empty one, else the best-matching leftover
		List<Bucket> heaviestFirst = new ArrayList<>(buckets);
		heaviestFirst.sort(small.reversed());
		for (Bucket b : heaviestFirst) {
			if (!b.targets.isEmpty()) {
				continue;
			}
			Target pick = null;
			for (int i = 0; i < n; i++) {
				if (totals.get(i) == 0 && !owner.containsKey(targets.get(i))) {
					pick = targets.get(i);
					break;
				}
			}
			if (pick == null) {
				double best = -1;
				for (int i = 0; i < n; i++) {
					Target t = targets.get(i);
					if (owner.containsKey(t)) {
						continue;
					}
					double s = share(contents.get(i), totals.get(i), b);
					if (s > best) {
						best = s;
						pick = t;
					}
				}
			}
			if (pick != null) {
				assign(owner, pick, b);
			}
		}
		// c. everything else: occupied containers to what they hold most of, empty ones to the busiest bucket
		for (int i = 0; i < n; i++) {
			Target t = targets.get(i);
			if (owner.containsKey(t) || totals.get(i) == 0) {
				continue;
			}
			Bucket best = null;
			double bestScore = 0;
			for (Bucket b : buckets) {
				double s = share(contents.get(i), totals.get(i), b);
				if (s > bestScore) {
					bestScore = s;
					best = b;
				}
			}
			if (best != null) {
				assign(owner, t, best);
			}
		}
		for (int i = 0; i < n; i++) {
			Target t = targets.get(i);
			if (owner.containsKey(t)) {
				continue;
			}
			Bucket busiest = buckets.stream()
					.max(Comparator.comparingDouble((Bucket b) -> b.weight / (double) Math.max(1, b.targets.size()))
							.thenComparing(small.reversed()))
					.orElseThrow();
			assign(owner, t, busiest);
		}
		return new SortPlan(List.copyOf(targets), buckets, coarse);
	}

	private static void assign(Map<Target, Bucket> owner, Target t, Bucket b) {
		owner.put(t, b);
		b.targets.add(t);
	}

	private static double share(EnumMap<SortCategory, Integer> contents, int total, Bucket b) {
		if (total <= 0) {
			return 0;
		}
		int hits = 0;
		for (SortCategory c : b.categories) {
			hits += contents.getOrDefault(c, 0);
		}
		return hits / (double) total;
	}

	// ------------------------------------------------------------------ routing

	/** Where {@code stack} should go right now (live container contents), or null if nothing in range can take any of it. */
	public Target destinationFor(Level level, ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Bucket home = byCategory.get(SortCategory.of(stack));
		if (home != null) {
			for (Target t : home.targets) {
				Container c = t.resolve(level);
				if (c != null && Stash.holds(c, stack) && Stash.room(c, stack) > 0) {
					return t;
				}
			}
			for (Target t : home.targets) {
				Container c = t.resolve(level);
				if (c != null && Stash.room(c, stack) > 0) {
					return t;
				}
			}
		}
		for (Target t : targets) {
			Container c = t.resolve(level);
			if (c != null && Stash.holds(c, stack) && Stash.room(c, stack) > 0) {
				return t;
			}
		}
		if (home != null) {
			for (Bucket b : buckets) {
				if (b == home || !b.sharesGroupWith(home)) {
					continue;
				}
				for (Target t : b.targets) {
					Container c = t.resolve(level);
					if (c != null && Stash.room(c, stack) > 0) {
						return t;
					}
				}
			}
		}
		for (Target t : targets) {
			Container c = t.resolve(level);
			if (c != null && Stash.room(c, stack) > 0) {
				return t;
			}
		}
		return null;
	}

	/** "Wood, Stone & Building x2, Food & Farming" -- for the deployment chat line. */
	public Component summary() {
		MutableComponent out = Component.empty();
		for (int i = 0; i < buckets.size(); i++) {
			Bucket b = buckets.get(i);
			if (i > 0) {
				out.append(", ");
			}
			out.append(b.displayName());
			if (b.targets.size() > 1) {
				out.append(" x" + b.targets.size());
			}
		}
		return out;
	}
}
