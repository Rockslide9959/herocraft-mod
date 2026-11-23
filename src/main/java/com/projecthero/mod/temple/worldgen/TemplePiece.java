package com.projecthero.mod.temple.worldgen;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.alarm.AlarmZones;
import com.projecthero.mod.hideout.worldgen.HideoutCanvas;
import com.projecthero.mod.temple.TempleMod;

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
 * v0.17.7: the whole Ancient Temple as one procedural piece -- origin (local 0,0,0 = the surface under the doorway's
 * hall), rotation, look (desert / jungle) and seed fixed at construction and saved. {@link #postProcess} hands each chunk
 * to {@link TempleBuilder} on a {@link HideoutCanvas} (rotates every write, clips it to the chunk).
 */
public class TemplePiece extends StructurePiece {
	private final BlockPos origin;
	private final Rotation rotation;
	private final boolean desert;
	private final long seed;

	public TemplePiece(BlockPos origin, Rotation rotation, boolean desert) {
		super(TempleWorldgen.PIECE, 0, box(origin, rotation));
		this.origin = origin.immutable();
		this.rotation = rotation;
		this.desert = desert;
		this.seed = ((long) origin.getX() * 341873128712L) ^ ((long) origin.getZ() * 132897987541L) ^ 0x7E3B1EL;
	}

	public TemplePiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(TempleWorldgen.PIECE, tag);
		this.origin = BlockPos.of(tag.getLong("Origin"));
		this.rotation = Rotation.values()[Math.floorMod(tag.getInt("Rot"), 4)];
		this.desert = tag.getBoolean("Desert");
		this.seed = tag.getLong("Seed");
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putLong("Origin", origin.asLong());
		tag.putInt("Rot", rotation.ordinal());
		tag.putBoolean("Desert", desert);
		tag.putLong("Seed", seed);
	}

	public static BoundingBox box(BlockPos origin, Rotation rot) {
		TempleBuilder b = TempleBuilder.INSTANCE;
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

	public boolean desert() {
		return desert;
	}

	public HideoutCanvas canvas(ServerLevelAccessor level, BoundingBox clip) {
		return new HideoutCanvas(level, clip, origin, rotation, seed, null);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		TempleBuilder.INSTANCE.build(canvas(level, chunkBox), new TemplePalette(desert));
	}

	/** Builds the whole temple in one go on a live level (gametests, the harness) and registers its alarm zone. */
	public void buildAll(ServerLevel level) {
		TempleBuilder.INSTANCE.build(canvas(level, getBoundingBox()), new TemplePalette(desert));
		AlarmZones zones = AlarmZones.get(level);
		zones.removeWithin(getBoundingBox());
		zones.register(getBoundingBox(), TempleMod.ALARM_PROFILE);
	}

	/** World position of local {@code l}. */
	public BlockPos world(BlockPos l) {
		return canvas(null, getBoundingBox()).at(l.getX(), l.getY(), l.getZ());
	}

	public BlockPos kingHome() {
		return world(TempleBuilder.KING_HOME);
	}

	public BlockPos sentinelHome() {
		return world(TempleBuilder.SENTINEL_HOME);
	}

	public List<BlockPos> vaults() {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos l : TempleBuilder.VAULTS) {
			out.add(world(l));
		}
		return out;
	}

	/** The glyph order this temple carved on its mural. */
	public int[] glyphOrder() {
		return TempleBuilder.glyphOrder(canvas(null, getBoundingBox()));
	}

	/** Which glyph each plate shows. */
	public int[] plateGlyphs() {
		return TempleBuilder.plateGlyphs(canvas(null, getBoundingBox()));
	}

	/** A world direction for local {@code d}. */
	public net.minecraft.core.Direction dir(net.minecraft.core.Direction d) {
		return rotation.rotate(d);
	}
}
