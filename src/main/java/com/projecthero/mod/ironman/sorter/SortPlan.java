package com.projecthero.mod.ironman.sorter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 *       categories. While there are more buckets than containers the smallest bucket is merged into its smallest
 *       <em>sibling</em> (same coarse group) if it has one, else into the smallest bucket overall. Since v0.14.21 a
 *       bucket's weight is how many slots its items take once packed (merged per distinct item), which moving and
 *       merging stacks never changes -- so planning twice from the same items always merges the same way.</li>
 *   <li><b>Assignment</b>: each bucket first gets the container holding the most of it (one per bucket); buckets
 *       still without a container take an empty one (the one last designated for them if any -- see
 *       {@code hints} -- else the nearest). v0.14.21: a left-over container only joins a bucket that needs the room
 *       (its items take more slots than its containers have) -- so a chest holding one lone stack of iron does
 *       NOT become a second Ores chest; it becomes a {@link #spares() spare}, its iron is carried to the Ores
 *       chest, and it is adopted later by whichever category fills up first ({@link #adoptSpare}).</li>
 * </ol>
 *
 * <h2>Where a stack goes (v0.14.21: strict)</h2>
 * {@link #destinationFor}: only ever one of its own bucket's containers -- the one already holding the identical
 * item first, then the others in assignment order (the "overflow" chests). If they are all full it returns null;
 * the station then places a new chest from its supply ({@link #addTarget}) or keeps the stack and reports how
 * many chests it needs. A category is therefore never scattered into other categories' chests.
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
		double weight;

		public Set<SortCategory> categories() {
			return categories;
		}

		public List<Target> targets() {
			return targets;
		}

		/** How many slots this bucket's items take once packed. */
		public double weight() {
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

		/** The coarse group this bucket is exactly, or null. */
		private SortCategory.Group wholeGroup() {
			for (SortCategory.Group g : SortCategory.Group.values()) {
				boolean whole = true;
				for (SortCategory c : SortCategory.values()) {
					if (c.group() == g && !categories.contains(c)) {
						whole = false;
						break;
					}
				}
				if (whole && categories.stream().allMatch(c -> c.group() == g)) {
					return g;
				}
			}
			return null;
		}

		/** A short readable name: "Wood", "Blocks", "Food + Redstone". */
		public Component displayName() {
			SortCategory.Group g = wholeGroup();
			if (g != null) {
				return g.displayName();
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

		/**
		 * v0.14.21: the label lines for a chest sign (at most three, each short enough for a sign): the group's
		 * name if the bucket is a whole group, else one category per line, the third line becoming "+N more" when
		 * there are more than three.
		 */
		public List<Component> signLines() {
			SortCategory.Group g = wholeGroup();
			if (g != null) {
				return List.of(Component.translatable("sort_group.projecthero." + g.name().toLowerCase(java.util.Locale.ROOT) + ".sign"));
			}
			List<Component> out = new ArrayList<>();
			List<SortCategory> cats = new ArrayList<>(categories);
			for (int i = 0; i < cats.size(); i++) {
				if (i == 2 && cats.size() > 3) {
					out.add(Component.translatable("sign.projecthero.stark_sorter.more", cats.size() - 2));
					break;
				}
				out.add(Component.translatable("sort_category.projecthero." + cats.get(i).name().toLowerCase(java.util.Locale.ROOT) + ".sign"));
			}
			return out;
		}
	}

	private final List<Target> targets;
	private final List<Bucket> buckets;
	private final Map<SortCategory, Bucket> byCategory = new EnumMap<>(SortCategory.class);
	private final boolean coarse;
	/** v0.14.21: containers no category needs yet (kept in reserve for overflow). */
	private final List<Target> spares;

	private SortPlan(List<Target> targets, List<Bucket> buckets, boolean coarse, List<Target> spares) {
		this.targets = new ArrayList<>(targets);
		this.buckets = buckets;
		this.coarse = coarse;
		this.spares = new ArrayList<>(spares);
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
					.max(Comparator.comparingDouble(b -> b.weight)).ifPresent(b -> byCategory.put(c, b));
		}
	}

	public List<Target> targets() {
		return Collections.unmodifiableList(targets);
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

	/**
	 * v0.14.21: a container placed mid-job (a new chest from the station's supply) joins {@code bucket} as its
	 * next overflow container.
	 */
	public void addTarget(Target target, Bucket bucket) {
		if (targets.contains(target) || !buckets.contains(bucket)) {
			return;
		}
		targets.add(target);
		bucket.targets.add(target);
	}

	/** v0.14.21: containers in range that no category needs (yet). */
	public List<Target> spares() {
		return Collections.unmodifiableList(spares);
	}

	/**
	 * v0.14.21: give {@code bucket} one of the spare containers as its next overflow container -- the one with
	 * the most free space, nearest to the bucket's first container on a tie. Null if there is none with room.
	 */
	public Target adoptSpare(Level level, Bucket bucket) {
		if (bucket == null || !buckets.contains(bucket)) {
			return null;
		}
		Vec3 anchor = bucket.targets.isEmpty() ? null : bucket.targets.get(0).center();
		Target best = null;
		int bestFree = 0;
		double bestDist = Double.MAX_VALUE;
		for (Target t : spares) {
			Container c = t.resolve(level);
			if (c == null) {
				continue;
			}
			int free = c.getContainerSize() - Stash.packedSlots(c);
			double dist = anchor == null ? 0 : t.center().distanceToSqr(anchor);
			if (free > bestFree || (free == bestFree && free > 0 && dist < bestDist)) {
				best = t;
				bestFree = free;
				bestDist = dist;
			}
		}
		if (best != null) {
			spares.remove(best);
			bucket.targets.add(best);
		}
		return best;
	}

	/** v0.14.21: what every container is designated for -- saved by the station as next plan's hints. */
	public Map<BlockPos, Set<SortCategory>> designations() {
		Map<BlockPos, Set<SortCategory>> out = new LinkedHashMap<>();
		for (Bucket b : buckets) {
			for (Target t : b.targets) {
				out.put(t.key(), EnumSet.copyOf(b.categories));
			}
		}
		return out;
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
			if (!(state.getBlock() instanceof ChestBlock) && !(state.getBlock() instanceof BarrelBlock)) {
				continue;
			}
			BlockPos pos = mutable.immutable();
			if (seen.contains(pos) || (filter != null && !filter.test(pos))) {
				continue;
			}
			Target t = targetAt(level, pos);
			if (t == null) {
				continue;
			}
			seen.addAll(t.blocks());
			if (t.blocks().size() > 1 && filter != null && !t.blocks().stream().allMatch(filter)) {
				continue;
			}
			out.add(t);
		}
		Vec3 origin = Vec3.atCenterOf(center);
		out.sort(Comparator.comparingDouble(t -> t.center().distanceToSqr(origin)));
		return out;
	}

	/** The container at {@code pos} as a {@link Target} (both halves of a double chest), or null. */
	public static Target targetAt(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof BarrelBlock) {
			if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity)) {
				return null;
			}
			Direction facing = state.getValue(BarrelBlock.FACING);
			Vec3 c = Vec3.atCenterOf(pos);
			return new Target(pos.immutable(), List.of(pos.immutable()), approachPoint(c, facing, 0.5), c);
		}
		if (!(state.getBlock() instanceof ChestBlock)) {
			return null;
		}
		List<BlockPos> blocks = new ArrayList<>();
		blocks.add(pos.immutable());
		if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
			BlockState otherState = level.getBlockState(other);
			if (otherState.is(state.getBlock()) && otherState.getValue(ChestBlock.TYPE) != ChestType.SINGLE
					&& other.relative(ChestBlock.getConnectedDirection(otherState)).equals(pos)) {
				blocks.add(other.immutable());
			}
		}
		blocks.sort(Comparator.naturalOrder());
		Vec3 c = blocks.size() == 1 ? Vec3.atCenterOf(blocks.get(0))
				: Vec3.atCenterOf(blocks.get(0)).add(Vec3.atCenterOf(blocks.get(1))).scale(0.5);
		return new Target(blocks.get(0), List.copyOf(blocks), approachPoint(c, state.getValue(ChestBlock.FACING), 0.0), c);
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

	/** Running totals of each distinct item (same components), for {@link #slotsByCategory}. */
	private static void tallyDistinct(List<ItemStack> reps, List<Integer> counts, ItemStack s) {
		for (int i = 0; i < reps.size(); i++) {
			if (ItemStack.isSameItemSameComponents(reps.get(i), s)) {
				counts.set(i, counts.get(i) + s.getCount());
				return;
			}
		}
		reps.add(s);
		counts.add(s.getCount());
	}

	/**
	 * v0.14.21: how many slots each category's items take once packed (merged per distinct item). Moving or merging
	 * stacks never changes it, so a re-plan after a Tidy sees exactly the same weights.
	 */
	private static EnumMap<SortCategory, Double> slotsByCategory(List<ItemStack> reps, List<Integer> counts) {
		EnumMap<SortCategory, Double> out = new EnumMap<>(SortCategory.class);
		for (int i = 0; i < reps.size(); i++) {
			int max = Math.max(1, reps.get(i).getMaxStackSize());
			out.merge(SortCategory.of(reps.get(i)), (double) ((counts.get(i) + max - 1) / max), Double::sum);
		}
		return out;
	}

	/** Build a plan for filing {@code incoming} into {@code targets}. {@code targets} must not be empty. */
	public static SortPlan build(Level level, List<Target> targets, List<ItemStack> incoming) {
		return build(level, targets, incoming, Map.of());
	}

	/**
	 * Build a plan for filing {@code incoming} into {@code targets}. {@code hints} (v0.14.21) is what each container
	 * was designated for last time (by its key); it only decides which <em>empty</em> container a bucket gets, so
	 * an emptied chest keeps its label and job between runs.
	 */
	public static SortPlan build(Level level, List<Target> targets, List<ItemStack> incoming,
			Map<BlockPos, Set<SortCategory>> hints) {
		int n = targets.size();
		boolean coarse = n <= 3;

		// per-target contents (stack counts, for "what is this chest mostly"), and every category's weight: the
		// slots its items take once packed (station + containers)
		List<EnumMap<SortCategory, Integer>> contents = new ArrayList<>();
		List<Integer> totals = new ArrayList<>();
		List<Integer> sizes = new ArrayList<>();
		List<ItemStack> reps = new ArrayList<>();
		List<Integer> counts = new ArrayList<>();
		for (ItemStack s : incoming) {
			if (!s.isEmpty()) {
				tallyDistinct(reps, counts, s);
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
						m.merge(SortCategory.of(s), 1, Integer::sum);
						tallyDistinct(reps, counts, s);
						total++;
					}
				}
			}
			contents.add(m);
			totals.add(total);
			sizes.add(c == null ? 27 : c.getContainerSize());
		}
		EnumMap<SortCategory, Double> weights = slotsByCategory(reps, counts);

		// 1. initial buckets
		List<Bucket> buckets = new ArrayList<>();
		if (coarse) {
			for (SortCategory.Group g : SortCategory.Group.values()) {
				Bucket b = new Bucket();
				boolean any = false;
				for (SortCategory c : SortCategory.values()) {
					if (c.group() == g) {
						b.categories.add(c);
						Double w = weights.get(c);
						if (w != null) {
							b.weight += w;
							any = true;
						}
					}
				}
				if (any) {
					buckets.add(b);
				}
			}
		} else {
			for (SortCategory c : SortCategory.values()) {
				Double w = weights.get(c);
				if (w != null) {
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
		Comparator<Bucket> small = Comparator.<Bucket>comparingDouble(b -> b.weight)
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
		record Pair(int target, Bucket bucket, int hits, double score) {
		}
		List<Pair> pairs = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (totals.get(i) == 0) {
				continue;
			}
			for (Bucket b : buckets) {
				double score = share(contents.get(i), totals.get(i), b);
				if (score > 0) {
					pairs.add(new Pair(i, b, hits(contents.get(i), b), score));
				}
			}
		}
		pairs.sort(Comparator.comparingInt((Pair p) -> -p.hits()).thenComparingDouble(p -> -p.score())
				.thenComparingInt(Pair::target));
		// a. affinity: each bucket's best already-matching container (the one holding the most of it)
		for (Pair p : pairs) {
			Target t = targets.get(p.target());
			if (!owner.containsKey(t) && p.bucket().targets.isEmpty()) {
				assign(owner, t, p.bucket());
			}
		}
		// b. buckets still without a container: the empty one designated for them, else the nearest empty one,
		//    else the best-matching leftover
		List<Bucket> heaviestFirst = new ArrayList<>(buckets);
		heaviestFirst.sort(small.reversed());
		for (Bucket b : heaviestFirst) {
			if (!b.targets.isEmpty()) {
				continue;
			}
			Target pick = null;
			int bestHint = 0;
			for (int i = 0; i < n; i++) {
				Target t = targets.get(i);
				if (totals.get(i) == 0 && !owner.containsKey(t)) {
					int h = overlap(hints.get(t.key()), b);
					if (pick == null || h > bestHint) {
						pick = t;
						bestHint = h;
					}
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
		// c. (v0.14.21) everything else -- occupied containers first -- only joins a bucket that needs the room:
		//    its items take more slots than its containers have. Preference: the bucket the container already
		//    holds most of, then the one it was designated for last time, then the one short of the most room.
		//    Containers no bucket needs are spares: their contents go home, and a bucket that fills up adopts one.
		Map<Bucket, Integer> capacity = new java.util.HashMap<>();
		for (Bucket b : buckets) {
			int cap = 0;
			for (Target t : b.targets) {
				cap += sizes.get(targets.indexOf(t));
			}
			capacity.put(b, cap);
		}
		List<Integer> leftovers = new ArrayList<>();
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < n; i++) {
				if (!owner.containsKey(targets.get(i)) && (totals.get(i) > 0) == (pass == 0)) {
					leftovers.add(i);
				}
			}
		}
		List<Target> spares = new ArrayList<>();
		for (int i : leftovers) {
			Target t = targets.get(i);
			Bucket pick = null;
			double bestScore = 0;
			for (Bucket b : buckets) {
				double sh = share(contents.get(i), totals.get(i), b);
				if (sh > bestScore && shortfall(b, capacity) > 0) {
					bestScore = sh;
					pick = b;
				}
			}
			if (pick == null) {
				int bestHint = 0;
				for (Bucket b : buckets) {
					int h = overlap(hints.get(t.key()), b);
					if (h > bestHint && shortfall(b, capacity) > 0) {
						bestHint = h;
						pick = b;
					}
				}
			}
			if (pick == null) {
				double most = 0;
				for (Bucket b : buckets) {
					double sf = shortfall(b, capacity);
					if (sf > most) {
						most = sf;
						pick = b;
					}
				}
			}
			if (pick == null) {
				spares.add(t);
				continue;
			}
			assign(owner, t, pick);
			capacity.merge(pick, sizes.get(i), Integer::sum);
		}
		return new SortPlan(targets, buckets, coarse, spares);
	}

	private static int overlap(Set<SortCategory> hint, Bucket b) {
		if (hint == null) {
			return 0;
		}
		int n = 0;
		for (SortCategory c : hint) {
			if (b.categories.contains(c)) {
				n++;
			}
		}
		return n;
	}

	/** Slots {@code b}'s items need beyond its containers' capacity (0 or less = it has enough). */
	private static double shortfall(Bucket b, Map<Bucket, Integer> capacity) {
		return b.weight - capacity.getOrDefault(b, 0);
	}

	private static int hits(EnumMap<SortCategory, Integer> contents, Bucket b) {
		int hits = 0;
		for (SortCategory c : b.categories) {
			hits += contents.getOrDefault(c, 0);
		}
		return hits;
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

	/**
	 * Where {@code stack} should go right now (live container contents): one of its own bucket's containers with
	 * room, or null when they are all full (v0.14.21: no "anywhere with room" fallback any more).
	 */
	public Target destinationFor(Level level, ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		return homeTargetWithRoom(level, homeOf(stack), stack, null);
	}

	/**
	 * A container of {@code home} with room for some of {@code stack} -- one already holding the identical item
	 * first, then any in assignment order -- skipping {@code exclude} (may be null). Null if none.
	 */
	private static Target homeTargetWithRoom(Level level, Bucket home, ItemStack stack, Target exclude) {
		if (home == null) {
			return null;
		}
		for (Target t : home.targets) {
			if (t.equals(exclude)) {
				continue;
			}
			Container c = t.resolve(level);
			if (c != null && Stash.holds(c, stack) && Stash.room(c, stack) > 0) {
				return t;
			}
		}
		for (Target t : home.targets) {
			if (t.equals(exclude)) {
				continue;
			}
			Container c = t.resolve(level);
			if (c != null && Stash.room(c, stack) > 0) {
				return t;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ tidying (v0.14.20)

	/** The bucket {@code stack} belongs in, or null when no container in range was given its category. */
	public Bucket homeOf(ItemStack stack) {
		return stack.isEmpty() ? null : byCategory.get(SortCategory.of(stack));
	}

	/**
	 * Tidy: true when {@code stack} sits in {@code where} but {@code where} is not one of its category's containers.
	 * A stack whose category has no container at all is never "misplaced" -- it has nowhere better to be.
	 */
	public boolean misplaced(Target where, ItemStack stack) {
		Bucket home = homeOf(stack);
		return home != null && !home.targets.contains(where);
	}

	/**
	 * Tidy: where a misplaced stack in {@code from} should be carried -- only ever one of its OWN category's
	 * containers with room (the identical item's chest first). Null = its chests are full (or it is not misplaced).
	 */
	public Target tidyDestinationFor(Level level, ItemStack stack, Target from) {
		if (!misplaced(from, stack)) {
			return null;
		}
		return homeTargetWithRoom(level, homeOf(stack), stack, from);
	}

	/**
	 * v0.14.21: the packing order inside {@code target}: stacks that belong there first, then any that are
	 * waiting to be moved out, each part in {@link Stash#ORDER}.
	 */
	public Comparator<ItemStack> orderFor(Target target) {
		return Comparator.<ItemStack>comparingInt(s -> misplaced(target, s) ? 1 : 0).thenComparing(Stash.ORDER);
	}

	/** True when {@code target} is a spare (no category needs it yet). */
	public boolean isSpare(Target target) {
		return spares.contains(target);
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
