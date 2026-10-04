package com.projecthero.mod.ironman.sorter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.21: what the Sorter Bot builds with -- the wall signs it labels chests with, and the chests it places when
 * a category runs out of room. Both come out of the Sorting Station's supply slots; nothing here creates items.
 *
 * <h2>Labels</h2>
 * A Stark label is a waxed wall sign whose first front line is exactly {@value #MARKER}; the rest is the chest's
 * category ({@link SortPlan.Bucket#signLines()}). A chest counts as labelled if any wall sign hangs on one of its
 * side faces, or any sign stands on top of it. Stark labels are re-written when the chest's category changes;
 * signs the player placed are never touched (and a chest that has one never gets a Stark label too). A double
 * chest gets one label. A sign only goes into an air block, on the chest's front first, then its outer sides,
 * then its back.
 *
 * <h2>New chests</h2>
 * {@link #chestSpot}: an air block within the station's radius (and the scan filter), on solid ground or on top of
 * another container, with nothing solid above it (so the lid opens), not in front of a container's opening and
 * not on the bot's dock. Preference: continuing the row of one of the category's own chests, then any managed
 * container's row, then on top of a container, then beside the station. Always a single chest (it never joins an
 * existing chest into a double one), facing the same way as its neighbour.
 */
public final class SorterSupply {
	/** First line of every Stark label. */
	public static final String MARKER = "[Stark]";

	private static final Map<Item, Block> WALL_SIGNS = new HashMap<>();
	private static boolean wallSignsBuilt;

	private SorterSupply() {
	}

	// ------------------------------------------------------------------ supply items

	/** The wall-sign block a sign item places, or null (hanging signs and non-signs). */
	public static Block wallSignFor(Item item) {
		synchronized (WALL_SIGNS) {
			if (!wallSignsBuilt) {
				// a sign item registers its wall block too, so the wall block's item IS the sign item
				for (Block b : BuiltInRegistries.BLOCK) {
					if (b instanceof WallSignBlock && b.asItem() != Items.AIR) {
						WALL_SIGNS.putIfAbsent(b.asItem(), b);
					}
				}
				wallSignsBuilt = true;
			}
			return WALL_SIGNS.get(item);
		}
	}

	/** A sign the bot can hang on a chest (any standing/wall sign; not a hanging sign). */
	public static boolean isSign(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ItemTags.SIGNS) && wallSignFor(stack.getItem()) != null;
	}

	/** A chest the bot can place. */
	public static boolean isChest(ItemStack stack) {
		return stack.is(Items.CHEST);
	}

	// ------------------------------------------------------------------ labels

	/** An existing sign on a container: where it is, and whether it is one of ours. */
	public record Label(BlockPos pos, boolean stark) {
	}

	/** The sign labelling {@code target}, or null if it has none. */
	public static Label findLabel(Level level, SortPlan.Target target) {
		Label player = null;
		for (BlockPos b : target.blocks()) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos p = b.relative(d);
				BlockState s = level.getBlockState(p);
				if (s.getBlock() instanceof WallSignBlock && s.getValue(WallSignBlock.FACING) == d) {
					if (isStark(level, p)) {
						return new Label(p, true);
					}
					player = player == null ? new Label(p, false) : player;
				}
			}
			BlockPos up = b.above();
			if (level.getBlockState(up).getBlock() instanceof StandingSignBlock) {
				if (isStark(level, up)) {
					return new Label(up, true);
				}
				player = player == null ? new Label(up, false) : player;
			}
		}
		return player;
	}

	private static boolean isStark(Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof SignBlockEntity sign
				&& MARKER.equals(sign.getFrontText().getMessage(0, false).getString());
	}

	/** The front text of a Stark label for a chest holding {@code bucket} (null = a spare chest). */
	public static SignText labelText(SortPlan.Bucket bucket) {
		SignText text = new SignText().setMessage(0, Component.literal(MARKER));
		List<Component> lines = bucket == null ? List.of(Component.translatable("sign.projecthero.stark_sorter.spare"))
				: bucket.signLines();
		for (int i = 0; i < Math.min(3, lines.size()); i++) {
			text = text.setMessage(i + 1, lines.get(i));
		}
		return text;
	}

	private static boolean sameText(SignText a, SignText b) {
		for (int i = 0; i < SignText.LINES; i++) {
			if (!a.getMessage(i, false).equals(b.getMessage(i, false))) {
				return false;
			}
		}
		return true;
	}

	/** True when {@code label} (a Stark sign) does not say {@code bucket} (null = a spare chest). */
	public static boolean needsRelabel(Level level, Label label, SortPlan.Bucket bucket) {
		return label.stark() && level.getBlockEntity(label.pos()) instanceof SignBlockEntity sign
				&& !sameText(sign.getFrontText(), labelText(bucket));
	}

	/** Re-write a Stark sign's text. True if it changed. */
	public static boolean relabel(Level level, Label label, SortPlan.Bucket bucket) {
		if (!needsRelabel(level, label, bucket) || !(level.getBlockEntity(label.pos()) instanceof SignBlockEntity sign)) {
			return false;
		}
		return sign.setText(labelText(bucket), true);
	}

	/** The faces to try for a sign, best first: front, outer sides, back. */
	private static List<BlockPos> signCandidates(Level level, SortPlan.Target target) {
		List<BlockPos> out = new ArrayList<>();
		BlockState state = level.getBlockState(target.key());
		Direction front;
		if (state.getBlock() instanceof ChestBlock) {
			front = state.getValue(ChestBlock.FACING);
		} else if (state.getBlock() instanceof BarrelBlock && state.getValue(BarrelBlock.FACING).getAxis().isHorizontal()) {
			front = state.getValue(BarrelBlock.FACING);
		} else {
			front = Direction.NORTH;
		}
		Direction[] order = { front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite() };
		for (Direction d : order) {
			for (BlockPos b : target.blocks()) {
				BlockPos p = b.relative(d);
				if (!target.blocks().contains(p) && !out.contains(p)) {
					out.add(p);
				}
			}
		}
		return out;
	}

	/** Where a new sign for {@code target} would go, or null if no face is free. */
	public static BlockPos signSpot(Level level, SortPlan.Target target, BlockPos station) {
		for (BlockPos p : signCandidates(level, target)) {
			if (p.equals(station) || p.equals(station.above())) {
				continue;
			}
			if (!level.getBlockState(p).isAir()) {
				continue;
			}
			Direction face = faceOf(target, p);
			if (face == null) {
				continue;
			}
			BlockState sign = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, face);
			if (sign.canSurvive(level, p)) {
				return p;
			}
		}
		return null;
	}

	private static Direction faceOf(SortPlan.Target target, BlockPos p) {
		for (BlockPos b : target.blocks()) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (b.relative(d).equals(p)) {
					return d;
				}
			}
		}
		return null;
	}

	/** Hang a Stark label made of {@code signItem} at {@code spot} (from {@link #signSpot}). True if placed. */
	public static boolean placeSign(Level level, SortPlan.Target target, BlockPos spot, Item signItem, SortPlan.Bucket bucket) {
		Block wall = wallSignFor(signItem);
		Direction face = faceOf(target, spot);
		if (wall == null || face == null || !level.getBlockState(spot).isAir()) {
			return false;
		}
		BlockState state = wall.defaultBlockState().setValue(WallSignBlock.FACING, face);
		if (!state.canSurvive(level, spot) || !level.setBlock(spot, state, Block.UPDATE_ALL)) {
			return false;
		}
		if (level.getBlockEntity(spot) instanceof SignBlockEntity sign) {
			sign.setText(labelText(bucket), true);
			sign.setWaxed(true); // players can't edit it by accident; the bot re-writes it directly
		}
		return true;
	}

	// ------------------------------------------------------------------ new chests

	/** A spot for a new chest and the way it should face. */
	public record ChestSpot(BlockPos pos, Direction facing) {
	}

	/**
	 * The best place for a new chest of {@code bucket} (may be null) among {@code targets}, or null if there is
	 * none. See the class comment for the rules.
	 */
	public static ChestSpot chestSpot(Level level, BlockPos station, Direction stationFacing, int radius,
			Predicate<BlockPos> filter, List<SortPlan.Target> targets, SortPlan.Bucket bucket) {
		List<BlockPos> fronts = new ArrayList<>();
		fronts.add(station.relative(stationFacing)); // where the player stands to use the station
		for (SortPlan.Target t : targets) {
			for (BlockPos b : t.blocks()) {
				Direction f = facingOf(level.getBlockState(b));
				if (f != null) {
					fronts.add(b.relative(f));
				}
			}
		}
		Vec3 anchor = bucket != null && !bucket.targets().isEmpty() ? bucket.targets().get(0).center() : Vec3.atCenterOf(station);
		ChestSpot best = null;
		double bestScore = Double.MAX_VALUE;
		for (SortPlan.Target t : targets) {
			boolean own = bucket != null && bucket.targets().contains(t);
			for (BlockPos b : t.blocks()) {
				Direction f = facingOf(level.getBlockState(b));
				Direction rowFacing = f != null && f.getAxis().isHorizontal() ? f : stationFacing;
				List<BlockPos> row = new ArrayList<>();
				if (f != null && f.getAxis().isHorizontal()) {
					row.add(b.relative(f.getClockWise()));
					row.add(b.relative(f.getCounterClockWise()));
				}
				for (BlockPos p : row) {
					double score = (own ? 0 : 1000) + Vec3.atCenterOf(p).distanceToSqr(anchor);
					if (score < bestScore && chestFits(level, p, station, radius, filter, fronts, false)) {
						best = new ChestSpot(p.immutable(), rowFacing);
						bestScore = score;
					}
				}
				BlockPos up = b.above();
				double score = 2000 + (own ? 0 : 1000) + Vec3.atCenterOf(up).distanceToSqr(anchor);
				if (score < bestScore && chestFits(level, up, station, radius, filter, fronts, true)) {
					best = new ChestSpot(up.immutable(), rowFacing);
					bestScore = score;
				}
			}
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = station.relative(d);
			double score = 5000 + Vec3.atCenterOf(p).distanceToSqr(anchor);
			if (score < bestScore && chestFits(level, p, station, radius, filter, fronts, false)) {
				best = new ChestSpot(p.immutable(), stationFacing);
				bestScore = score;
			}
		}
		return best;
	}

	private static Direction facingOf(BlockState state) {
		if (state.getBlock() instanceof ChestBlock) {
			return state.getValue(ChestBlock.FACING);
		}
		if (state.getBlock() instanceof BarrelBlock) {
			return state.getValue(BarrelBlock.FACING);
		}
		return null;
	}

	private static boolean chestFits(Level level, BlockPos p, BlockPos station, int radius, Predicate<BlockPos> filter,
			List<BlockPos> fronts, boolean stacking) {
		if (p.equals(station) || p.equals(station.above()) || p.distSqr(station) > (double) radius * radius) {
			return false;
		}
		if ((filter != null && !filter.test(p)) || !level.getBlockState(p).isAir() || fronts.contains(p)) {
			return false;
		}
		BlockPos below = p.below();
		BlockState under = level.getBlockState(below);
		boolean grounded = under.isFaceSturdy(level, below, Direction.UP)
				|| under.getBlock() instanceof ChestBlock || under.getBlock() instanceof BarrelBlock;
		if (!grounded && !stacking) {
			return false;
		}
		BlockPos above = p.above();
		return !level.getBlockState(above).isRedstoneConductor(level, above);
	}

	/** Place a single chest at {@code spot}. True if it went down. */
	public static boolean placeChest(Level level, ChestSpot spot) {
		if (!level.getBlockState(spot.pos()).isAir()) {
			return false;
		}
		BlockState state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, spot.facing())
				.setValue(ChestBlock.TYPE, ChestType.SINGLE);
		return level.setBlock(spot.pos(), state, Block.UPDATE_ALL);
	}
}
