package com.projecthero.mod.loot.finds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The Hidden Cache: a small marker on a patch of disturbed earth -- a stone cairn, a red flag on a fence pole, or a
 * sign scratched with an X -- and three blocks below it a 3x3 dug-out room (stone bricks, or sandstone in sandy ground)
 * holding a Tier 1 Hero Vault, a lantern and a cobweb. The two blocks between the grass and the ceiling are coarse
 * dirt, so digging straight down under the marker finds it.
 */
public class HiddenCachePiece extends FindPiece {
	static final int HALF = 3;
	/** The room: floor at groundY - 5, two blocks of air, ceiling at groundY - 2. */
	private static final int FLOOR = 5;
	private static final int CEILING = 2;

	public HiddenCachePiece(int centerX, int surfaceY, int centerZ) {
		super(FindsMod.HIDDEN_CACHE_PIECE, centerX, surfaceY, centerZ, HALF, 7, 6, 3);
	}

	public HiddenCachePiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(FindsMod.HIDDEN_CACHE_PIECE, tag);
	}

	@Override
	public BlockPos vaultPos() {
		return localToWorld(0, groundY - FLOOR + 1, -1);
	}

	/** The marker's bottom block (cairn / flag pole / sign), right over the filled-in shaft. */
	public BlockPos markerPos() {
		return localToWorld(0, groundY + 1, 0);
	}

	@Override
	public void build(WorldGenLevel level, BoundingBox box) {
		boolean sandy = level.getBlockState(new BlockPos(cx, groundY, cz)).is(BlockTags.SAND);
		int floor = groundY - FLOOR;
		int ceiling = groundY - CEILING;

		// ---- the room
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int x = wx(dx, dz), z = wz(dx, dz);
				for (int y = floor; y <= ceiling; y++) {
					boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || y == floor || y == ceiling;
					BlockState s;
					if (!shell) {
						s = Blocks.AIR.defaultBlockState();
					} else if (y == floor) {
						s = sandy ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
					} else if (y == ceiling) {
						s = sandy ? Blocks.CUT_SANDSTONE.defaultBlockState()
								: (dx == 0 ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
										.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.Z)
										: Blocks.SPRUCE_PLANKS.defaultBlockState());
					} else {
						double n = noise(x, z, 10 + y);
						s = sandy ? (n < 0.3 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState())
								: n < 0.3 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
								: n < 0.45 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
								: Blocks.STONE_BRICKS.defaultBlockState();
					}
					put(level, box, dx, y, dz, s);
				}
			}
		}
		int fy = floor + 1;
		vault(level, box, 0, fy, -1, Direction.SOUTH);
		put(level, box, 1, fy, 1, Blocks.LANTERN.defaultBlockState());
		put(level, box, -1, fy + 1, 1, Blocks.COBWEB.defaultBlockState());

		// ---- the filled-in shaft and the disturbed earth on top
		for (int y = ceiling + 1; y <= groundY - 1; y++) {
			put(level, box, 0, y, 0, Blocks.COARSE_DIRT.defaultBlockState());
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int x = wx(dx, dz), z = wz(dx, dz);
				if (dx == 0 && dz == 0 || noise(x, z, 20) < 0.7) {
					put(level, box, dx, groundY, dz, Blocks.COARSE_DIRT.defaultBlockState());
					clear(level, box, dx, dz, groundY + 1, groundY + 2);
				}
			}
		}

		// ---- the marker
		int m = groundY + 1;
		switch (variant) {
			case 0 -> { // a cairn
				BlockState base = sandy ? Blocks.SANDSTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
				BlockState mid = sandy ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
				BlockState slab = (sandy ? Blocks.SANDSTONE_SLAB : Blocks.COBBLESTONE_SLAB).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
				put(level, box, 0, m, 0, base);
				put(level, box, 0, m + 1, 0, mid);
				put(level, box, 0, m + 2, 0, slab);
				put(level, box, 1, m, 0, slab);
				put(level, box, -1, m, 1, (sandy ? Blocks.SANDSTONE_SLAB : Blocks.MOSSY_COBBLESTONE_SLAB).defaultBlockState());
			}
			case 1 -> { // a red flag on a fence pole
				for (int y = 0; y < 3; y++) {
					put(level, box, 0, m + y, 0, Blocks.SPRUCE_FENCE.defaultBlockState());
				}
				put(level, box, 1, m + 2, 0, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.EAST));
				put(level, box, 1, m, 1, Blocks.COBBLESTONE_SLAB.defaultBlockState());
			}
			default -> { // a sign scratched with an X
				put(level, box, 0, m, 0, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0));
				int x = wx(0, 0), z = wz(0, 0);
				if (inBox(box, x, z) && level.getBlockEntity(new BlockPos(x, m, z)) instanceof SignBlockEntity sign) {
					SignText text = new SignText()
							.setMessage(1, Component.translatable("projecthero.find.hidden_cache.sign1"))
							.setMessage(2, Component.translatable("projecthero.find.hidden_cache.sign2"));
					sign.setText(text, true);
					sign.setText(text, false);
				}
				put(level, box, -1, m, 0, Blocks.TORCH.defaultBlockState());
			}
		}
	}
}
