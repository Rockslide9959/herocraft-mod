package com.projecthero.mod.vaultdungeon.worldgen;

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
 * v0.17.5: the whole Vault as one procedural piece -- origin (local 0,0,0 = the surface under the guard post) and
 * rotation fixed at construction and saved; {@link #postProcess} hands each chunk to {@link VaultBuilder}, drawing on
 * the hideouts' {@link HideoutCanvas} (rotates every write and clips it to the chunk; no faction).
 */
public class VaultPiece extends StructurePiece {
	private final BlockPos origin;
	private final Rotation rotation;
	private final long seed;
	/**
	 * v0.17.7: which plan this piece draws -- 1 (v0.17.5, three floors) for pieces saved before the "Layout" tag existed,
	 * so a half-generated old Vault finishes the way it started; {@link VaultBuilder#LAYOUT} for new ones.
	 */
	private final int layout;

	public VaultPiece(BlockPos origin, Rotation rotation) {
		super(VaultWorldgen.PIECE, 0, box(origin, rotation));
		this.origin = origin.immutable();
		this.rotation = rotation;
		this.seed = ((long) origin.getX() * 341873128712L) ^ ((long) origin.getZ() * 132897987541L) ^ 0x5A17L;
		this.layout = VaultBuilder.LAYOUT;
	}

	public VaultPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(VaultWorldgen.PIECE, tag);
		this.origin = BlockPos.of(tag.getLong("Origin"));
		this.rotation = Rotation.values()[Math.floorMod(tag.getInt("Rot"), 4)];
		this.seed = tag.getLong("Seed");
		this.layout = tag.contains("Layout") ? tag.getInt("Layout") : 1;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putLong("Origin", origin.asLong());
		tag.putInt("Rot", rotation.ordinal());
		tag.putLong("Seed", seed);
		tag.putInt("Layout", layout);
	}

	public int layout() {
		return layout;
	}

	/** The world box the builder's local extents cover at this origin / rotation (+1 margin). */
	public static BoundingBox box(BlockPos origin, Rotation rot) {
		VaultBuilder b = VaultBuilder.INSTANCE;
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
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		VaultBuilder.INSTANCE.build(canvas(level, chunkBox), layout);
	}

	/**
	 * Builds the whole dungeon in one go on a live level (gametests, the harness), and -- with no structure start on a
	 * live build -- registers its alarm zone by hand (a rebuild starts a fresh zone).
	 */
	public void buildAll(ServerLevel level) {
		VaultBuilder.INSTANCE.build(canvas(level, getBoundingBox()), layout);
		com.projecthero.mod.alarm.AlarmZones zones = com.projecthero.mod.alarm.AlarmZones.get(level);
		zones.removeWithin(getBoundingBox());
		zones.register(getBoundingBox(), com.projecthero.mod.vaultdungeon.VaultSecurity.PROFILE);
	}

	/** World position of local {@code l}. */
	public BlockPos world(BlockPos l) {
		return canvas(null, getBoundingBox()).at(l.getX(), l.getY(), l.getZ());
	}

	/** The arena anchor (Warden Kross's home) in the world. */
	public BlockPos anchor() {
		return world(VaultBuilder.ANCHOR);
	}

	/** The sealed vaults' world positions. */
	public java.util.List<BlockPos> vaults() {
		java.util.List<BlockPos> out = new java.util.ArrayList<>();
		for (BlockPos l : VaultBuilder.VAULTS) {
			out.add(world(l));
		}
		return out;
	}
}
