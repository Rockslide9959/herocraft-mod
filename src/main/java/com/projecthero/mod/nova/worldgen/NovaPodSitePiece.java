package com.projecthero.mod.nova.worldgen;

import com.projecthero.mod.nova.entity.NovaCenturionEntity;
import com.projecthero.mod.nova.item.NovaItems;
import com.projecthero.mod.worldgen.ModStructurePieceTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * v0.15.13: a Crashed Nova Corps Pod, carved at world generation -- a scorched impact bowl about 15 blocks across with a
 * gold-and-blue Nova Corps pod nose-down in the middle (cyan canopy, glowing engine, a smouldering exhaust), hull plates
 * scattered round it, and the dying Centurion slumped against the open hatch. Like the Kryptonite crater it is one
 * procedural piece; unlike it, the pod and the floor share ONE reference height stored in the piece (the noise surface at
 * the centre when the structure was placed), so every chunk of the site builds at the same level whatever order the
 * chunks are generated in.
 */
public class NovaPodSitePiece extends StructurePiece {
	public static final int RADIUS = 7;
	private static final int MAX_DEPTH = 3;
	private static final double EDGE_JITTER = 1.2;
	/** How far the skid furrow runs out past the rim (+X). */
	private static final int FURROW = 12;
	/** The floor stays flat this far from the centre. */
	private static final double FLAT_RADIUS = 3.6;

	private final int baseY;

	public NovaPodSitePiece(int centerX, int surfaceY, int centerZ) {
		super(ModStructurePieceTypes.NOVA_POD_SITE, 0, new BoundingBox(
				centerX - RADIUS - 2, surfaceY - MAX_DEPTH - 6, centerZ - RADIUS - 2,
				centerX + RADIUS + FURROW + 1, surfaceY + 12, centerZ + RADIUS + 2));
		this.baseY = surfaceY;
	}

	public NovaPodSitePiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.NOVA_POD_SITE, tag);
		this.baseY = tag.contains("BaseY") ? tag.getInt("BaseY") : (this.boundingBox.minY() + MAX_DEPTH + 6);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("BaseY", baseY);
	}

	/** The floor block's Y at the centre (the pod rests on {@code floorY + 1}). */
	public int floorY() {
		return baseY - MAX_DEPTH - 1;
	}

	public BlockPos center() {
		return new BlockPos(this.boundingBox.minX() + RADIUS + 2, floorY(), // the box runs on past the rim for the furrow
				(this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		build(level, chunkBox, true, Heightmap.Types.WORLD_SURFACE_WG);
	}

	/**
	 * Carves the bowl and builds the pod inside {@code box} (one chunk at world generation; the whole site for the
	 * {@code /projecthero nova site} command). {@code centurion}: also place the Centurion if his spot is inside.
	 */
	public void build(WorldGenLevel level, BoundingBox box, boolean centurion, Heightmap.Types surface) {
		BlockPos c = center();
		int cx = c.getX();
		int cz = c.getZ();
		int floor = floorY();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int outer = RADIUS + 1;
		for (int dx = -outer; dx <= outer; dx++) {
			for (int dz = -outer; dz <= outer; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ()) {
					continue;
				}
				RandomSource r = columnRandom(x, z);
				double dist = Math.sqrt((double) dx * dx + (double) dz * dz) + (r.nextDouble() - 0.5) * EDGE_JITTER;
				if (dist > RADIUS) {
					continue;
				}
				int surfaceY = Math.max(level.getHeight(surface, x, z) - 1, baseY - 1);
				// a flat floor round the pod (the Centurion must sit level with it), then the bowl rises to the rim
				double rise = Math.max(0.0, (dist - FLAT_RADIUS) / (RADIUS - FLAT_RADIUS));
				int floorHere = Math.min(surfaceY - 1, floor + (int) Math.round(Math.pow(rise, 1.4) * MAX_DEPTH));
				for (int y = surfaceY + 4; y > floorHere; y--) {
					cursor.set(x, y, z);
					if (!level.getBlockState(cursor).hasBlockEntity()) {
						level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				cursor.set(x, floorHere, z);
				level.setBlock(cursor, scorch(r, dist), 2);
				// fill any gap under a raised floor so nothing floats
				for (int y = floorHere - 1; y > floorHere - 4; y--) {
					cursor.set(x, y, z);
					if (level.getBlockState(cursor).isAir() || !level.getBlockState(cursor).getFluidState().isEmpty()) {
						level.setBlock(cursor, Blocks.BLACKSTONE.defaultBlockState(), 2);
					}
				}
				if (dist > RADIUS - 1.5 && r.nextFloat() < 0.4f) {
					cursor.set(x, floorHere + 1, z);
					level.setBlock(cursor, scorch(r, dist), 2);
				} else if (dist > 4.5 && r.nextFloat() < 0.08f) {
					cursor.set(x, floorHere + 1, z); // a scattered hull plate
					level.setBlock(cursor, r.nextBoolean() ? Blocks.YELLOW_CONCRETE.defaultBlockState()
							: Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState(), 2);
				}
			}
		}
		buildPod(level, box, cx, floor + 1, cz);
		buildFurrow(level, box, cx, cz, surface);
		if (centurion) {
			placeCenturion(level, box, cx, floor + 1, cz);
		}
	}

	private static RandomSource columnRandom(int x, int z) {
		return RandomSource.create(((long) x * 341873128712L) ^ ((long) z * 132897987541L) ^ 0x4E6F7661L);
	}

	private static BlockState scorch(RandomSource r, double dist) {
		float f = r.nextFloat();
		return f < 0.45f ? Blocks.BLACKSTONE.defaultBlockState()
				: f < 0.7f ? Blocks.BASALT.defaultBlockState()
				: f < 0.88f ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GILDED_BLACKSTONE.defaultBlockState();
	}

	private static void put(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
		BlockPos p = new BlockPos(x, y, z);
		if (box.isInside(p)) {
			level.setBlock(p, state, 2);
		}
	}

	/** How high the hull sits at each X along its length (-8 = the buried nose tip, +7 = the lifted engine). */
	static int lift(int dx) {
		if (dx <= -7) {
			return -2;
		}
		if (dx <= -4) {
			return -1;
		}
		if (dx <= 0) {
			return 0;
		}
		if (dx <= 3) {
			return 1;
		}
		return 2;
	}

	/** Half-width of the hull at {@code dx}: the fuselage is 5 wide, tapering to 3 at the nose and the tail. */
	private static int halfWidth(int dx) {
		return dx <= -6 || dx >= 5 ? 1 : 2;
	}

	/** Top row of the hull at {@code dx} (rows count up from the belly). */
	private static int topRow(int dx) {
		if (dx <= -8) {
			return 1;
		}
		return halfWidth(dx) == 2 ? 3 : 2;
	}

	private static BlockState stair(net.minecraft.world.level.block.Block block, Direction facing, boolean upsideDown) {
		return block.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, facing)
				.setValue(net.minecraft.world.level.block.StairBlock.HALF, upsideDown
						? net.minecraft.world.level.block.state.properties.Half.TOP
						: net.minecraft.world.level.block.state.properties.Half.BOTTOM);
	}

	/**
	 * The pod: a long, low Nova Corps fighter (15 blocks, 5 wide, 4 tall) lying along X at an angle -- its gold nose cone
	 * ploughed into the crater floor at -X, the engine end lifted two blocks at +X. Blue flanks with sloping dark-blue
	 * shoulders (stairs) and a sloping belly, a gold band down the spine, a light-blue glass cockpit canopy behind the
	 * nose with a windscreen, a gold Nova star on each flank, the hatch torn open on the +Z side (the Centurion sits
	 * against the hull beside it), two swept tail fins and a dorsal fin, a glowing engine with a smouldering exhaust, and
	 * the lifted end propped on the earth it ploughed up. {@link #buildFurrow} adds the skid trench behind it.
	 */
	private static void buildPod(WorldGenLevel level, BoundingBox box, int cx, int y0, int cz) {
		BlockState blue = Blocks.BLUE_CONCRETE.defaultBlockState();
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState goldTrim = Blocks.YELLOW_CONCRETE.defaultBlockState();
		BlockState inside = Blocks.BLACK_CONCRETE.defaultBlockState();
		BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
		net.minecraft.world.level.block.Block shoulder = Blocks.DARK_PRISMARINE_STAIRS;

		for (int dx = -8; dx <= 6; dx++) {
			int y = y0 + lift(dx);
			int half = dx <= -8 ? 0 : halfWidth(dx);
			int top = topRow(dx);
			for (int dz = -half; dz <= half; dz++) {
				Direction in = dz > 0 ? Direction.NORTH : Direction.SOUTH; // the tall side of a stair faces the spine
				for (int dy = 0; dy <= top; dy++) {
					boolean edge = dz != 0 && Math.abs(dz) == half;
					BlockState s;
					if (dx <= -7) {
						s = dy == top || dz == 0 ? gold : goldTrim; // the gold nose cone
					} else if (dy == top) {
						if (edge && half == 2) {
							continue; // the shoulder stair below gives the slope
						}
						s = dz == 0 ? gold : blue; // a gold stripe down the spine between blue shoulders
						if (dx == -5 || dx == -4) {
							s = glass; // the cockpit canopy
						}
					} else if (dy == 0) {
						s = edge ? stair(shoulder, in, true) : blue; // the belly curves in
					} else if (edge && dy == top - 1 && half == 2) {
						s = stair(shoulder, in, false); // the shoulders
					} else if (edge) {
						s = blue;
					} else {
						s = inside;
					}
					if (dx == -6 && dy == top && dz == 0) {
						s = glass; // the windscreen
					}
					put(level, box, cx + dx, y + dy, cz + dz, s);
				}
				// prop the lifted end up on the earth it ploughed (and fill the gap under the belly)
				for (int py = y0 - 1; py < y; py++) {
					put(level, box, cx + dx, py, cz + dz, py == y - 1 ? Blocks.COARSE_DIRT.defaultBlockState()
							: Blocks.BLACKSTONE.defaultBlockState());
				}
			}
		}
		// the Nova star on each flank: a gold plus on the blue
		for (int side : new int[] { -1, 1 }) {
			int sx = side > 0 ? 2 : 0;
			int sy = y0 + lift(sx) + 1;
			int sz = cz + 2 * side;
			put(level, box, cx + sx, sy, sz, gold);
			put(level, box, cx + sx - 1, sy, sz, gold);
			put(level, box, cx + sx + 1, sy, sz, gold);
			put(level, box, cx + sx, sy - 1, sz, gold);
			put(level, box, cx + sx, sy + 1, sz, gold);
		}
		// the hatch, torn open on the +Z flank
		for (int hy = 1; hy <= 2; hy++) {
			put(level, box, cx - 1, y0 + lift(-1) + hy, cz + 2, Blocks.AIR.defaultBlockState());
		}
		put(level, box, cx - 3, y0, cz + 4, Blocks.BLUE_CONCRETE.defaultBlockState()); // the hatch door, thrown clear
		// the engine: a glowing core in a dark housing, a smouldering exhaust beside it
		int ey = y0 + lift(6);
		put(level, box, cx + 7, ey + 1, cz, Blocks.SEA_LANTERN.defaultBlockState());
		put(level, box, cx + 7, ey + 2, cz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
		put(level, box, cx + 7, ey + 1, cz - 1, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
		put(level, box, cx + 7, ey + 1, cz + 1, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
		put(level, box, cx + 7, ey, cz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)
				.setValue(CampfireBlock.SIGNAL_FIRE, false));
		for (int py = y0 - 1; py < ey; py++) {
			put(level, box, cx + 7, py, cz, Blocks.BLACKSTONE.defaultBlockState());
		}
		// two swept tail fins and a dorsal fin
		for (int side : new int[] { -1, 1 }) {
			put(level, box, cx + 5, y0 + lift(5) + 2, cz + 2 * side, goldTrim);
			put(level, box, cx + 6, y0 + lift(6) + 2, cz + 2 * side, goldTrim);
			put(level, box, cx + 6, y0 + lift(6) + 3, cz + 3 * side, goldTrim);
		}
		put(level, box, cx + 5, y0 + lift(5) + 3, cz, goldTrim);
		put(level, box, cx + 6, y0 + lift(6) + 3, cz, goldTrim);
		put(level, box, cx + 6, y0 + lift(6) + 4, cz, Blocks.YELLOW_TERRACOTTA.defaultBlockState());
	}

	/**
	 * The skid furrow behind the pod: a three-wide trench of scorched earth running out of the crater along +X, banked
	 * with ploughed-up dirt, littered with hull plates.
	 */
	private void buildFurrow(WorldGenLevel level, BoundingBox box, int cx, int cz, Heightmap.Types surface) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = RADIUS - 1; dx <= RADIUS + FURROW; dx++) {
			int x = cx + dx;
			double fade = (dx - (RADIUS - 1)) / (double) (FURROW + 1); // shallower and narrower as it runs out
			int width = fade > 0.7 ? 1 : 2;
			for (int dz = -width; dz <= width; dz++) {
				int z = cz + dz;
				if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ()) {
					continue;
				}
				RandomSource r = columnRandom(x, z * 31 + 7);
				int top = level.getHeight(surface, x, z) - 1;
				if (top < baseY - MAX_DEPTH - 2) {
					continue; // inside the bowl already
				}
				if (Math.abs(dz) == width && width == 2) {
					cursor.set(x, top + 1, z); // the bank of ploughed-up earth
					if (r.nextFloat() < 0.25f) {
						level.setBlock(cursor, r.nextFloat() < 0.3f ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), 2);
					}
					continue;
				}
				cursor.set(x, top, z);
				level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
				cursor.set(x, top - 1, z);
				level.setBlock(cursor, scorch(r, 6.0), 2);
				if (r.nextFloat() < 0.12f) {
					cursor.set(x, top, z); // a hull plate left behind
					level.setBlock(cursor, r.nextBoolean() ? Blocks.BLUE_CONCRETE.defaultBlockState()
							: Blocks.YELLOW_CONCRETE.defaultBlockState(), 2);
				} else if (r.nextFloat() < 0.06f) {
					cursor.set(x, top, z);
					level.setBlock(cursor, Blocks.IRON_BARS.defaultBlockState(), 2);
				}
			}
		}
	}

	/** Where the Centurion sits: against the hull beside the open hatch, facing out (+Z). */
	public static BlockPos centurionSpot(int cx, int y0, int cz) {
		return new BlockPos(cx - 1, y0, cz + 3);
	}

	private static void placeCenturion(WorldGenLevel level, BoundingBox box, int cx, int y0, int cz) {
		BlockPos spot = centurionSpot(cx, y0, cz);
		if (!box.isInside(spot)) {
			return;
		}
		NovaCenturionEntity centurion = NovaItems.CENTURION.create(level.getLevel());
		if (centurion == null) {
			return;
		}
		centurion.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.3, 0.0f, 0.0f);
		centurion.setYHeadRot(0.0f);
		centurion.setYBodyRot(0.0f);
		centurion.setPersistenceRequired();
		level.addFreshEntityWithPassengers(centurion);
	}
}
