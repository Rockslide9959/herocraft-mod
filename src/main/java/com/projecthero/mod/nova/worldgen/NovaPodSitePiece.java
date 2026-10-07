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
	/** The floor stays flat this far from the centre. */
	private static final double FLAT_RADIUS = 3.6;

	private final int baseY;

	public NovaPodSitePiece(int centerX, int surfaceY, int centerZ) {
		super(ModStructurePieceTypes.NOVA_POD_SITE, 0, new BoundingBox(
				centerX - RADIUS - 2, surfaceY - MAX_DEPTH - 6, centerZ - RADIUS - 2,
				centerX + RADIUS + 2, surfaceY + 10, centerZ + RADIUS + 2));
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
		return new BlockPos((this.boundingBox.minX() + this.boundingBox.maxX()) / 2, floorY(),
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
		if (centurion) {
			placeCenturion(level, box, cx, floor + 1, cz);
		}
	}

	private static RandomSource columnRandom(int x, int z) {
		return RandomSource.create(((long) x * 341873128712L) ^ ((long) z * 132897987541L) ^ 0x4E6F7661L);
	}

	private static BlockState scorch(RandomSource r, double dist) {
		float f = r.nextFloat();
		if (dist < 1.5 && f < 0.25f) {
			return Blocks.MAGMA_BLOCK.defaultBlockState();
		}
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

	/**
	 * The pod: a 7-long capsule along X, nose (-X) dug into the floor, tail (+X) lifted, a cyan canopy on top, a gold
	 * Nova star on each flank, the hatch open on the +Z side and a glowing, smoking engine at the tail.
	 */
	private static void buildPod(WorldGenLevel level, BoundingBox box, int cx, int y0, int cz) {
		BlockState hull = Blocks.YELLOW_CONCRETE.defaultBlockState();
		BlockState trim = Blocks.BLUE_CONCRETE.defaultBlockState();
		BlockState dark = Blocks.CYAN_TERRACOTTA.defaultBlockState();
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
		for (int dx = -3; dx <= 3; dx++) {
			int lift = dx <= -2 ? -1 : dx >= 2 ? 1 : 0; // nose down, tail up
			for (int dy = 0; dy <= 2; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean corner = (dy == 0 || dy == 2) && dz != 0;
					boolean tip = (dx == -3 || dx == 3) && (dy != 1 || dz != 0);
					if (corner || tip) {
						continue;
					}
					BlockState s = dy == 1 && dz != 0 ? trim : hull;
					if (dy == 2 && (dx == -1 || dx == 0)) {
						s = glass; // the canopy
					}
					if (dy == 0) {
						s = dark;
					}
					if (dy == 1 && dz != 0 && dx == 0) {
						s = gold; // the Nova star on the flank
					}
					if (dz == 1 && dx == 1 && dy <= 1) {
						s = Blocks.AIR.defaultBlockState(); // the open hatch
					}
					put(level, box, cx + dx, y0 + dy + lift, cz + dz, s);
				}
			}
		}
		// the nose cone and the engine
		put(level, box, cx - 4, y0, cz, gold);
		put(level, box, cx + 4, y0 + 2, cz, Blocks.SEA_LANTERN.defaultBlockState());
		put(level, box, cx + 4, y0 + 1, cz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)
				.setValue(CampfireBlock.SIGNAL_FIRE, false));
		put(level, box, cx + 4, y0, cz, Blocks.BLACKSTONE.defaultBlockState());
		// fins
		put(level, box, cx + 3, y0 + 2, cz - 2, Blocks.YELLOW_TERRACOTTA.defaultBlockState());
		put(level, box, cx + 3, y0 + 2, cz + 2, Blocks.YELLOW_TERRACOTTA.defaultBlockState());
		put(level, box, cx + 2, y0 + 3, cz, Blocks.YELLOW_TERRACOTTA.defaultBlockState());
		// a torn hull plate and a cable by the hatch
		put(level, box, cx + 2, y0, cz + 2, Blocks.IRON_BARS.defaultBlockState());
		put(level, box, cx - 1, y0, cz + 3, Blocks.YELLOW_CONCRETE.defaultBlockState());
		put(level, box, cx - 2, y0, cz - 3, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState());
		put(level, box, cx + 1, y0, cz - 2, Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.ChainBlock.AXIS,
				Direction.Axis.X));
	}

	/** Where the Centurion sits: against the hull beside the open hatch, facing out (+Z). */
	public static BlockPos centurionSpot(int cx, int y0, int cz) {
		return new BlockPos(cx, y0, cz + 2);
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
