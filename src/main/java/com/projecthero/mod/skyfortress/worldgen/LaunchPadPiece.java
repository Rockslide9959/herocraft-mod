package com.projecthero.mod.skyfortress.worldgen;

import com.projecthero.mod.hideout.worldgen.HideoutCanvas;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * v0.17.7: the Launch Pad ground post as one procedural piece (origin = the pad's centre, floor layer; rotation; whether
 * it stands in water). Drawn by {@link LaunchPadBuilder}.
 */
public class LaunchPadPiece extends StructurePiece {
	private final BlockPos origin;
	private final Rotation rotation;
	private final boolean water;
	private final long seed;

	public LaunchPadPiece(BlockPos origin, Rotation rotation, boolean water) {
		super(SkyFortressWorldgen.PAD_PIECE, 0, box(origin, water));
		this.origin = origin.immutable();
		this.rotation = rotation;
		this.water = water;
		this.seed = ((long) origin.getX() * 73428767L) ^ ((long) origin.getZ() * 912931L) ^ 0x9AD5L;
	}

	public LaunchPadPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(SkyFortressWorldgen.PAD_PIECE, tag);
		this.origin = BlockPos.of(tag.getLong("Origin"));
		this.rotation = Rotation.values()[Math.floorMod(tag.getInt("Rot"), 4)];
		this.water = tag.getBoolean("Water");
		this.seed = tag.getLong("Seed");
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putLong("Origin", origin.asLong());
		tag.putInt("Rot", rotation.ordinal());
		tag.putBoolean("Water", water);
		tag.putLong("Seed", seed);
	}

	public static BoundingBox box(BlockPos origin, boolean water) {
		int h = LaunchPadBuilder.HALF + 1;
		return new BoundingBox(origin.getX() - h, origin.getY() + LaunchPadBuilder.INSTANCE.minY(water), origin.getZ() - h, origin.getX() + h,
				origin.getY() + LaunchPadBuilder.CLEAR + 1, origin.getZ() + h);
	}

	public BlockPos origin() {
		return origin;
	}

	public boolean water() {
		return water;
	}

	public HideoutCanvas canvas(ServerLevelAccessor level, BoundingBox clip) {
		return new HideoutCanvas(level, clip, origin, rotation, seed, null);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		LaunchPadBuilder.INSTANCE.build(canvas(level, chunkBox), water);
	}

	public void buildAll(ServerLevel level) {
		LaunchPadBuilder.INSTANCE.build(canvas(level, getBoundingBox()), water);
	}

	public BlockPos world(BlockPos local) {
		return canvas(null, getBoundingBox()).at(local.getX(), local.getY(), local.getZ());
	}
}
