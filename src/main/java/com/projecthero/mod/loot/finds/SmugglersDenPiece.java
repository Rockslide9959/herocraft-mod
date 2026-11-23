package com.projecthero.mod.loot.finds;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * v0.17.7 Smugglers' Den: a cave stash dug into a cliff face. A timber-framed tunnel runs from the foot of the cliff into
 * a low cave room: crates and barrels of contraband along the walls, a card table with candles, bedrolls, a hanging
 * lantern, the contraband chest ({@code chests/finds/smugglers_den}) and the Tier 1 Hero Vault. Three Syndicate
 * smugglers (existing player-model crooks) lounge inside.
 *
 * <p>The structure places it only where the ground rises steeply on one side (see {@code FindStructure}); local north
 * (-z) points into the cliff, and the cliff's face position and height are measured from the noise at planning time
 * and saved with the piece, so every chunk of the den agrees on them.
 *
 * <p>Layouts: 0 one cave room, 1 a room and a side store (the vault deeper in). Variants: the entrance (0 hidden behind
 * vines and bushes, 1 behind a <b>waterfall</b> pouring off the cliff into a little pool -- falls back to vines on a cliff
 * too low for it, 2 a boarded mine adit with rails and a "keep out" sign).
 */
public class SmugglersDenPiece extends FindPiece {
	static final int HALF = 11;
	public static final ResourceKey<LootTable> CONTRABAND = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/finds/smugglers_den"));

	/** Local z of the first column of cliff rock on the entrance line (x = 0); always 1 or less. */
	private int faceZ;
	/** The top block of the cliff above the entrance. */
	private int cliffTop;

	public SmugglersDenPiece(int centerX, int surfaceY, int centerZ) {
		this(centerX, surfaceY, centerZ, -2, surfaceY + 7);
	}

	public SmugglersDenPiece(int centerX, int surfaceY, int centerZ, int faceZ, int cliffTop) {
		super(FindsMod.SMUGGLERS_DEN_PIECE, centerX, surfaceY, centerZ, HALF, 6, 14, 3, 2);
		this.faceZ = Math.min(1, Math.max(-5, faceZ));
		this.cliffTop = cliffTop;
	}

	public SmugglersDenPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(FindsMod.SMUGGLERS_DEN_PIECE, tag);
		this.faceZ = tag.getInt("FaceZ");
		this.cliffTop = tag.getInt("CliffTop");
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		super.addAdditionalSaveData(context, tag);
		tag.putInt("FaceZ", faceZ);
		tag.putInt("CliffTop", cliffTop);
	}

	/**
	 * Measures the face and the cliff height from a live level (tests, the harness): call after {@link #force} and before
	 * {@link #build}. Returns this.
	 */
	public SmugglersDenPiece measure(LevelReader level) {
		int g = groundY;
		for (int dz = 1; dz >= -5; dz--) {
			int top = g;
			BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos(wx(0, dz), g + 30, wz(0, dz));
			for (int y = g + 30; y > g; y--) {
				c.setY(y);
				BlockState s = level.getBlockState(c);
				if (!s.isAir() && !s.canBeReplaced() && !s.is(net.minecraft.tags.BlockTags.LEAVES) && !s.is(net.minecraft.tags.BlockTags.LOGS)) {
					top = y;
					break;
				}
			}
			if (top >= g + 4) {
				faceZ = dz;
				cliffTop = top;
				return this;
			}
		}
		faceZ = -5;
		cliffTop = g + 4;
		return this;
	}

	public int faceZ() {
		return faceZ;
	}

	@Override
	public BlockPos vaultPos() {
		return layout == 1 ? localToWorld(9, groundY + 1, -6) : localToWorld(-4, groundY + 1, -8);
	}

	@Override
	public BlockPos stashPos() {
		return localToWorld(4, groundY + 1, -9);
	}

	/** How many smugglers lounge here. */
	public int mobCount() {
		return 3;
	}

	private boolean waterfall() {
		return variant == 1 && cliffTop >= groundY + 5;
	}

	private boolean inRoom(int dx, int dz, double wobble) {
		double ex = dx / (5.0 + wobble), ez = (dz + 8) / (3.6 + wobble);
		return ex * ex + ez * ez <= 1.0;
	}

	private boolean inStore(int dx, int dz, double wobble) {
		double ex = (dx - 8) / (2.6 + wobble), ez = (dz + 6) / (2.6 + wobble);
		return layout == 1 && ex * ex + ez * ez <= 1.0;
	}

	private boolean inPassage(int dx, int dz) {
		return layout == 1 && dx >= 4 && dx <= 6 && (dz == -6 || dz == -7);
	}

	private boolean inTunnel(int dx, int dz) {
		return Math.abs(dx) <= 1 && dz <= 3 && dz >= -5;
	}

	@Override
	public void build(WorldGenLevel level, BoundingBox box) {
		int g = groundY;
		Block log = Blocks.SPRUCE_LOG;
		// ---- carve the tunnel, the room (and the store), lay a floor, seal any breach in the ceiling
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= 3; dz++) {
				double w = lnoise(dx, dz, 100) * 0.7 - 0.2;
				boolean tunnel = inTunnel(dx, dz);
				boolean room = inRoom(dx, dz, w) || inStore(dx, dz, w) || inPassage(dx, dz);
				if (!tunnel && !room) {
					continue;
				}
				int top = tunnel && !room ? g + 3 : g + 3 + (inRoom(dx, dz, w - 0.9) || inStore(dx, dz, w - 0.6) ? 1 : 0);
				clear(level, box, dx, dz, g + 1, top);
				double n = lnoise(dx, dz, 101);
				put(level, box, dx, g, dz, n < 0.4 ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.6 ? Blocks.GRAVEL.defaultBlockState()
						: n < 0.85 ? Blocks.PACKED_MUD.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
				footing(level, box, dx, dz, g - 1, Blocks.COBBLESTONE.defaultBlockState());
				if (dz < faceZ) {
					BlockState above = get(level, dx, top + 1, dz);
					if (above.isAir() || above.canBeReplaced()) {
						put(level, box, dx, top + 1, dz, lnoise(dx, dz, 102) < 0.5 ? Blocks.STONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
					}
				}
			}
		}
		// ---- timber frames: at the mouth and twice down the tunnel
		for (int fz : new int[] { faceZ, -2, -5 }) {
			if (fz > faceZ) {
				continue;
			}
			for (int y = g + 1; y <= g + 3; y++) {
				put(level, box, -2, y, fz, log.defaultBlockState());
				put(level, box, 2, y, fz, log.defaultBlockState());
			}
			for (int dx = -2; dx <= 2; dx++) {
				put(level, box, dx, g + 4, fz, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
			}
		}
		// a plank walkway down the tunnel
		for (int dz = Math.min(faceZ, 0); dz >= -5; dz--) {
			put(level, box, 0, g, dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		// ---- the room
		// crates along the back wall
		int[][] crates = { { -2, -11 }, { -1, -11 }, { 1, -11 }, { 2, -11 }, { 3, -10 }, { -3, -10 } };
		for (int i = 0; i < crates.length; i++) {
			put(level, box, crates[i][0], g + 1, crates[i][1], i % 3 == 1 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.BARREL.defaultBlockState());
			if (i % 2 == 0) {
				put(level, box, crates[i][0], g + 2, crates[i][1], i == 2 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.BARREL.defaultBlockState());
			}
		}
		put(level, box, 0, g + 1, -11, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM));
		// the card table and stools
		put(level, box, 0, g + 1, -7, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		put(level, box, 1, g + 1, -7, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		put(level, box, 0, g + 2, -7, Blocks.CANDLE.defaultBlockState());
		put(level, box, 1, g + 2, -7, Blocks.WHITE_CARPET.defaultBlockState());
		put(level, box, 0, g + 1, -6, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
		put(level, box, 2, g + 1, -7, stair(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
		put(level, box, 1, g + 1, -8, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
		// bedrolls, a lantern on a chain, a campfire pit (unlit: smoke would give them away)
		put(level, box, -3, g + 1, -7, Blocks.BROWN_CARPET.defaultBlockState());
		put(level, box, -3, g + 1, -6, Blocks.BROWN_CARPET.defaultBlockState());
		put(level, box, -2, g + 1, -9, Blocks.GREEN_CARPET.defaultBlockState());
		put(level, box, 0, g + 4, -8, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
		put(level, box, 0, g + 3, -8, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		put(level, box, 3, g + 1, -6, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
		put(level, box, -1, g + 1, -4, Blocks.LANTERN.defaultBlockState());
		// the contraband chest
		lootContainer(level, box, 4, g + 1, -9, facing(Blocks.CHEST, Direction.WEST), CONTRABAND);
		if (layout == 1) {
			// the side store: stacked crates, a grindstone, the vault at the back
			for (int[] c : new int[][] { { 9, -8 }, { 10, -7 }, { 7, -4 }, { 9, -4 }, { 10, -5 } }) {
				put(level, box, c[0], g + 1, c[1], Blocks.BARREL.defaultBlockState());
				if ((c[0] + c[1]) % 2 == 0) {
					put(level, box, c[0], g + 2, c[1], Blocks.SPRUCE_PLANKS.defaultBlockState());
				}
			}
			put(level, box, 7, g + 1, -8, facing(Blocks.GRINDSTONE, Direction.SOUTH));
			put(level, box, 8, g + 3, -6, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			put(level, box, 8, g + 4, -6, Blocks.CHAIN.defaultBlockState());
			vault(level, box, 9, g + 1, -6, Direction.WEST);
			clear(level, box, 8, -6, g + 1, g + 2);
			put(level, box, 5, g + 3, -6, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
			put(level, box, 5, g + 3, -7, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
		} else {
			vault(level, box, -4, g + 1, -8, Direction.EAST);
			clear(level, box, -3, -8, g + 1, g + 2);
		}
		// ---- the entrance
		entrance(level, box);
		// ---- the smugglers
		int[][] spots = { { 2, -6 }, { -2, -8 }, { 1, -10 } };
		for (int i = 0; i < mobCount(); i++) {
			int x = wx(spots[i][0], spots[i][1]), z = wz(spots[i][0], spots[i][1]);
			if (!inBox(box, x, z)) {
				continue;
			}
			clear(level, box, spots[i][0], spots[i][1], g + 1, g + 2);
			FindMobs.spawn(level, i == 0 ? FindMobs.Kind.SMUGGLER_PISTOL : i == 1 ? FindMobs.Kind.SMUGGLER : FindMobs.Kind.SMUGGLER_SHOTGUN,
					new BlockPos(x, g + 1, z), dir(Direction.SOUTH).toYRot(), 8);
		}
	}

	private void entrance(WorldGenLevel level, BoundingBox box) {
		int g = groundY;
		int mouth = faceZ + 1;
		if (waterfall()) {
			// a stone spout at the cliff top, the fall in front of the mouth, a little pool at its foot
			int t = cliffTop;
			put(level, box, -1, t, mouth, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
			put(level, box, 1, t, mouth, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
			put(level, box, 0, t, mouth + 1, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
			put(level, box, 0, t + 1, mouth, Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState());
			for (int y = g + 1; y < t; y++) {
				put(level, box, 0, y, mouth, Blocks.AIR.defaultBlockState());
			}
			water(level, box, 0, t, mouth);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = mouth; dz <= mouth + 1; dz++) {
					water(level, box, dx, g, dz);
				}
			}
			// a rim round the pool so it never runs off down the slope
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = mouth - 1; dz <= mouth + 2; dz++) {
					boolean pool = Math.abs(dx) <= 1 && dz >= mouth && dz <= mouth + 1;
					if (!pool) {
						BlockState s = get(level, dx, g, dz);
						if (s.isAir() || s.canBeReplaced()) {
							put(level, box, dx, g, dz, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
						}
					}
					footing(level, box, dx, dz, g - 1, Blocks.COBBLESTONE.defaultBlockState(), 3);
				}
			}
			putSoft(level, box, -2, g + 1, mouth + 1, Blocks.FERN.defaultBlockState());
			putSoft(level, box, 2, g + 1, mouth, Blocks.LILY_OF_THE_VALLEY.defaultBlockState());
			return;
		}
		if (variant == 2) {
			// a boarded mine adit: rails running in, broken boards across the mouth, a keep-out sign
			for (int dz = Math.min(3, mouth + 2); dz >= -4; dz--) {
				if (get(level, 0, g + 1, dz).isAir()) {
					put(level, box, 0, g + 1, dz, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
							net.minecraft.world.level.block.state.properties.RailShape.NORTH_SOUTH));
				}
			}
			put(level, box, -1, g + 2, faceZ, Blocks.SPRUCE_FENCE.defaultBlockState());
			put(level, box, -1, g + 1, faceZ, Blocks.SPRUCE_FENCE.defaultBlockState());
			put(level, box, 1, g + 3, faceZ, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true)
					.setValue(TrapDoorBlock.FACING, Direction.NORTH));
			put(level, box, -1, g + 3, faceZ, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
			int sx = 2, sz = Math.min(3, mouth + 1);
			putSoft(level, box, sx, g + 1, sz, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0));
			int x = wx(sx, sz), z = wz(sx, sz);
			if (inBox(box, x, z) && level.getBlockEntity(new BlockPos(x, g + 1, z)) instanceof SignBlockEntity sign) {
				SignText text = new SignText().setMessage(1, Component.translatable("projecthero.find.smugglers_den.sign1"))
						.setMessage(2, Component.translatable("projecthero.find.smugglers_den.sign2"));
				net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
				SignText.DIRECT_CODEC.encodeStart(ops, text).result().ifPresent(tg -> {
					CompoundTag tag = new CompoundTag();
					tag.put("front_text", tg);
					tag.put("back_text", tg.copy());
					sign.loadCustomOnly(tag, level.registryAccess());
				});
			}
			putSoft(level, box, -2, g + 1, Math.min(3, mouth + 1), Blocks.COBWEB.defaultBlockState());
			return;
		}
		// hidden behind vines and bushes
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int s : new int[] { -2, 2 }) {
			putSoft(level, box, s, g + 1, mouth, leaves);
			if (lnoise(s, 0, 103) < 0.6) {
				putSoft(level, box, s, g + 2, mouth, leaves);
			}
			putSoft(level, box, s + Integer.signum(s), g + 1, mouth, Blocks.SWEET_BERRY_BUSH.defaultBlockState());
		}
		BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true);
		for (int dx = -2; dx <= 2; dx++) {
			int len = 1 + (int) (lnoise(dx, 1, 104) * 3);
			for (int y = g + 4; y > g + 4 - len; y--) {
				putSoft(level, box, dx, y, mouth, vine);
			}
		}
	}

	private void water(WorldGenLevel level, BoundingBox box, int dx, int y, int dz) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return;
		}
		BlockPos at = new BlockPos(x, y, z);
		level.setBlock(at, Blocks.WATER.defaultBlockState(), 2);
		level.scheduleTick(at, Fluids.WATER, 1);
	}
}
