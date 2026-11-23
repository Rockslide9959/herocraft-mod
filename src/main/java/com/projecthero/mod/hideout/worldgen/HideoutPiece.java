package com.projecthero.mod.hideout.worldgen;

import com.projecthero.mod.hideout.HideoutFaction;

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
 * v0.17.5: a whole villain hideout as one procedural piece -- the faction, the origin (local 0,0,0) and the rotation are
 * fixed at construction and saved; {@link #postProcess} hands each chunk to the faction's {@link HideoutBuilder}.
 */
public class HideoutPiece extends StructurePiece {
	private final HideoutFaction faction;
	private final BlockPos origin;
	private final Rotation rotation;
	private final long seed;

	public HideoutPiece(HideoutFaction faction, BlockPos origin, Rotation rotation) {
		super(HideoutWorldgen.PIECE, 0, box(HideoutBuilder.of(faction), origin, rotation));
		this.faction = faction;
		this.origin = origin.immutable();
		this.rotation = rotation;
		this.seed = ((long) origin.getX() * 341873128712L) ^ ((long) origin.getZ() * 132897987541L) ^ faction.ordinal();
	}

	public HideoutPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(HideoutWorldgen.PIECE, tag);
		HideoutFaction f = HideoutFaction.byName(tag.getString("Faction"));
		this.faction = f == null ? HideoutFaction.KINGPIN : f;
		this.origin = BlockPos.of(tag.getLong("Origin"));
		this.rotation = Rotation.values()[Math.floorMod(tag.getInt("Rot"), 4)];
		this.seed = tag.getLong("Seed");
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Faction", faction.getSerializedName());
		tag.putLong("Origin", origin.asLong());
		tag.putInt("Rot", rotation.ordinal());
		tag.putLong("Seed", seed);
	}

	/** The world box the builder's local extents cover at this origin / rotation (+1 margin). */
	public static BoundingBox box(HideoutBuilder b, BlockPos origin, Rotation rot) {
		HideoutCanvas probe = new HideoutCanvas(null, null, origin, rot, 0L, null);
		BlockPos a = probe.at(b.minX - 1, b.minY - 1, b.minZ - 1);
		BlockPos c = probe.at(b.maxX + 1, b.maxY + 1, b.maxZ + 1);
		return BoundingBox.fromCorners(a, c);
	}

	public HideoutFaction faction() {
		return faction;
	}

	public BlockPos origin() {
		return origin;
	}

	public Rotation rotation() {
		return rotation;
	}

	/** The canvas for {@code level} clipped to {@code clip}. */
	public HideoutCanvas canvas(ServerLevelAccessor level, BoundingBox clip) {
		return new HideoutCanvas(level, clip, origin, rotation, seed, faction);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		HideoutBuilder.of(faction).build(canvas(level, chunkBox));
	}

	/** Builds the whole hideout in one go on a live level (gametests, the harness). */
	public void buildAll(ServerLevel level) {
		HideoutBuilder.of(faction).build(canvas(level, getBoundingBox()));
		// v0.17.7: no structure start on a live build, so register the alarm zone by hand (a rebuild starts a fresh zone)
		com.projecthero.mod.alarm.AlarmZones zones = com.projecthero.mod.alarm.AlarmZones.get(level);
		zones.removeWithin(getBoundingBox());
		zones.register(getBoundingBox(), com.projecthero.mod.hideout.HideoutAlarmProfiles.id(faction));
	}

	/** World position of the core. */
	public BlockPos corePos() {
		BlockPos l = HideoutBuilder.of(faction).coreLocal();
		return canvas(null, getBoundingBox()).at(l.getX(), l.getY(), l.getZ());
	}

	/** World position of the sealed vault. */
	public BlockPos vaultPos() {
		BlockPos l = HideoutBuilder.of(faction).vaultLocal();
		return canvas(null, getBoundingBox()).at(l.getX(), l.getY(), l.getZ());
	}
}
