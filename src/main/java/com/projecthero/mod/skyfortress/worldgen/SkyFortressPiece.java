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
 * v0.17.7: the flying fortress itself as one procedural piece -- origin (local 0,0,0 = the lower deck's floor layer, at
 * the ship's middle) and rotation saved; each chunk draws its share with {@link SkyFortressBuilder} on the hideouts'
 * {@link HideoutCanvas}.
 */
public class SkyFortressPiece extends StructurePiece {
	private final BlockPos origin;
	private final Rotation rotation;
	private final long seed;

	public SkyFortressPiece(BlockPos origin, Rotation rotation) {
		super(SkyFortressWorldgen.FORTRESS_PIECE, 0, box(origin, rotation));
		this.origin = origin.immutable();
		this.rotation = rotation;
		this.seed = ((long) origin.getX() * 341873128712L) ^ ((long) origin.getZ() * 132897987541L) ^ 0x5F0A7L;
	}

	public SkyFortressPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(SkyFortressWorldgen.FORTRESS_PIECE, tag);
		this.origin = BlockPos.of(tag.getLong("Origin"));
		this.rotation = Rotation.values()[Math.floorMod(tag.getInt("Rot"), 4)];
		this.seed = tag.getLong("Seed");
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putLong("Origin", origin.asLong());
		tag.putInt("Rot", rotation.ordinal());
		tag.putLong("Seed", seed);
	}

	public static BoundingBox box(BlockPos origin, Rotation rot) {
		SkyFortressBuilder b = SkyFortressBuilder.INSTANCE;
		HideoutCanvas probe = new HideoutCanvas(null, null, origin, rot, 0L, null);
		BlockPos a = probe.at(b.minX - 1, b.minY - 1, b.minZ - 1);
		BlockPos c = probe.at(b.maxX + 1, b.maxY + 1, b.maxZ + 1);
		return BoundingBox.fromCorners(a, c);
	}

	public BlockPos origin() {
		return origin;
	}

	public Rotation rotation() {
		return rotation;
	}

	public HideoutCanvas canvas(ServerLevelAccessor level, BoundingBox clip) {
		return new HideoutCanvas(level, clip, origin, rotation, seed, null);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		SkyFortressBuilder.INSTANCE.build(canvas(level, chunkBox));
	}

	/** Builds the whole fortress in one go on a live level (gametests, the harness). */
	public void buildAll(ServerLevel level) {
		SkyFortressBuilder.INSTANCE.build(canvas(level, getBoundingBox()));
	}

	public BlockPos world(BlockPos local) {
		return canvas(null, getBoundingBox()).at(local.getX(), local.getY(), local.getZ());
	}
}
