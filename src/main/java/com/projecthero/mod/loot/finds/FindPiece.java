package com.projecthero.mod.loot.finds;

import com.projecthero.mod.loot.HeroVault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Shared base of the three Tier 1 Find pieces: a square footprint of {@code 2*half+1} centred on the chunk middle, a
 * ground level fixed at construction (the generator's own terrain height, so the build sits at the same level in every
 * chunk it crosses), a per-piece seed for deterministic variation, a rotation (0-3 quarter turns) and a variant.
 *
 * <p>Pieces are written in <em>local</em> coordinates: {@code (dx, dz)} with the front (door / open side) towards
 * local south (+z); {@link #wx}/{@link #wz} turn them by the piece's rotation, and {@link #rot} turns block states.
 * Every write is clipped to the chunk box being generated.
 */
public abstract class FindPiece extends StructurePiece {
	/** The top block of the ground (one below the first air block). */
	protected final int groundY;
	protected final long seed;
	protected final int rotation;
	protected final int variant;
	protected final int half;
	protected final int cx;
	protected final int cz;

	protected FindPiece(StructurePieceType type, int centerX, int surfaceY, int centerZ, int half, int below, int above, int variants) {
		super(type, 0, new BoundingBox(centerX - half, surfaceY - below, centerZ - half, centerX + half, surfaceY + above, centerZ + half));
		this.groundY = surfaceY - 1;
		this.seed = ((long) centerX * 341873128712L) ^ ((long) centerZ * 132897987541L) ^ (half * 0x9E3779B97F4A7C15L);
		this.rotation = (int) Math.floorMod(seed >>> 7, 4L);
		this.variant = (int) Math.floorMod(seed >>> 13, (long) variants);
		this.half = half;
		this.cx = centerX;
		this.cz = centerZ;
	}

	protected FindPiece(StructurePieceType type, CompoundTag tag) {
		super(type, tag);
		this.groundY = tag.getInt("GroundY");
		this.seed = tag.getLong("Seed");
		this.rotation = tag.getInt("Rot");
		this.variant = tag.getInt("Variant");
		this.half = tag.getInt("Half");
		this.cx = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		this.cz = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("GroundY", groundY);
		tag.putLong("Seed", seed);
		tag.putInt("Rot", rotation);
		tag.putInt("Variant", variant);
		tag.putInt("Half", half);
	}

	public int variant() {
		return variant;
	}

	public int groundY() {
		return groundY;
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		build(level, chunkBox);
	}

	/** Builds the part of the find inside {@code box} (the whole find when given {@link #getBoundingBox()}). */
	public abstract void build(WorldGenLevel level, BoundingBox box);

	/** Where the vault is placed (world coordinates), for tests and the harness. */
	public abstract BlockPos vaultPos();

	// ---------------------------------------------------------------- helpers

	/** A stable 0..1 value per (x, z, salt). */
	protected double noise(int x, int z, int salt) {
		long h = seed ^ (x * 73856093L) ^ (z * 19349663L) ^ (salt * 83492791L);
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return ((h ^ (h >>> 33)) >>> 11) / (double) (1L << 53);
	}

	protected int wx(int dx, int dz) {
		return cx + switch (rotation) {
			case 1 -> -dz;
			case 2 -> -dx;
			case 3 -> dz;
			default -> dx;
		};
	}

	protected int wz(int dx, int dz) {
		return cz + switch (rotation) {
			case 1 -> dx;
			case 2 -> -dz;
			case 3 -> -dx;
			default -> dz;
		};
	}

	protected Rotation rotationEnum() {
		return switch (rotation) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** A local direction turned to world space. */
	protected Direction dir(Direction local) {
		return rotationEnum().rotate(local);
	}

	protected BlockState rot(BlockState state) {
		return state.rotate(rotationEnum());
	}

	protected static boolean inBox(BoundingBox box, int x, int z) {
		return x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
	}

	/** Sets a block at a local position (rotating the state). */
	protected void put(WorldGenLevel level, BoundingBox box, int dx, int y, int dz, BlockState state) {
		putWorld(level, box, wx(dx, dz), y, wz(dx, dz), rot(state));
	}

	protected void putWorld(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
		if (!inBox(box, x, z)) {
			return;
		}
		level.setBlock(new BlockPos(x, y, z), state, 2);
	}

	protected BlockState get(WorldGenLevel level, int dx, int y, int dz) {
		return level.getBlockState(new BlockPos(wx(dx, dz), y, wz(dx, dz)));
	}

	/** Fills air / fluid / plants under a local column from {@code fromY} down (at most 6 blocks) with {@code fill}. */
	protected void footing(WorldGenLevel level, BoundingBox box, int dx, int dz, int fromY, BlockState fill) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return;
		}
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
		for (int y = fromY; y >= fromY - 6; y--) {
			c.set(x, y, z);
			BlockState s = level.getBlockState(c);
			if (!s.isAir() && level.getFluidState(c).isEmpty() && !s.canBeReplaced()) {
				break;
			}
			level.setBlock(c, fill, 2);
		}
	}

	/** Clears a local column from {@code y0} to {@code y1} (inclusive). */
	protected void clear(WorldGenLevel level, BoundingBox box, int dx, int dz, int y0, int y1) {
		for (int y = y0; y <= y1; y++) {
			put(level, box, dx, y, dz, Blocks.AIR.defaultBlockState());
		}
	}

	/** Places the Tier 1 Hero Vault at a local position, its door towards local {@code facing}. */
	protected void vault(WorldGenLevel level, BoundingBox box, int dx, int y, int dz, Direction facing) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return;
		}
		HeroVault.place(level, new BlockPos(x, y, z), 1, dir(facing));
	}

	protected BlockPos localToWorld(int dx, int y, int dz) {
		return new BlockPos(wx(dx, dz), y, wz(dx, dz));
	}
}
