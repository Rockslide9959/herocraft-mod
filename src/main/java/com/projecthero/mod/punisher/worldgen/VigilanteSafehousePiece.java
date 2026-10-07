package com.projecthero.mod.punisher.worldgen;

import com.projecthero.mod.punisher.item.PunisherItems;
import com.projecthero.mod.worldgen.ModStructurePieceTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A small buried bunker: a stone-brick room under two blocks of earth, reached by a lined ladder shaft that comes up
 * through a wooden hatch ringed with cobblestone. Fittings: a weapon workbench along the west wall (crafting, smithing
 * and fletching tables either side of the barrel that always holds the Vigilante Training Manual), a bed and the
 * ammunition (loot table) and supply chests along the east wall, a firing range at the north end (a bullseye set into
 * the wall, a hay-bale backstop with two target blocks), a rug and hanging lanterns.
 *
 * <p>v0.15.11 rebuild: the ladder used to face SOUTH while its wall was to the south, so every rung hung off the air
 * on the room side; the hatch was an iron trapdoor (no hand can open one) floating a block above the ground, and the
 * room's roof was the surface itself. Now the ladder faces NORTH against the solid south wall all the way up, the
 * shaft is lined, and the hatch is a spruce trapdoor flush with the ground facing the same way as the ladder (so an
 * open hatch climbs like a ladder, vanilla's trapdoor-over-ladder rule).
 *
 * <p>Modelled on {@code SteelCrashSitePiece} / {@code GraveyardPiece}: no stored state beyond the base bounding box,
 * everything built at {@link #postProcess} (real terrain exists then), every placement clipped to the current chunk
 * box. The whole footprint (centre +-5, +-4) sits inside the one chunk the structure starts in.
 */
public class VigilanteSafehousePiece extends StructurePiece {
	public static final ResourceKey<LootTable> AMMO_LOOT = ResourceKey.create(
			net.minecraft.core.registries.Registries.LOOT_TABLE,
			com.projecthero.mod.ProjectHeroMod.id("chests/vigilante_safehouse"));

	private static final int HALF_X = 4;
	private static final int HALF_Z = 3;
	/** Interior rows above the floor: dy 0..HEIGHT are open, HEIGHT + 1 is the roof. */
	private static final int HEIGHT = 4;
	/** Floor this far below the surface: the roof ends up two blocks under the ground. */
	private static final int DEPTH = 8;

	public VigilanteSafehousePiece(int centerX, int estimatedSurfaceY, int centerZ) {
		super(ModStructurePieceTypes.VIGILANTE_SAFEHOUSE, 0, new BoundingBox(
				centerX - HALF_X - 1, estimatedSurfaceY - DEPTH - 2, centerZ - HALF_Z - 1,
				centerX + HALF_X + 1, estimatedSurfaceY + 3, centerZ + HALF_Z + 1));
	}

	public VigilanteSafehousePiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructurePieceTypes.VIGILANTE_SAFEHOUSE, tag);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		int cx = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		int cz = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
		int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz) - 1;
		int floorY = surfaceY - DEPTH;
		// the hatch comes up wherever the ground is over the shaft itself (never into the roof)
		int hatchY = Math.max(floorY + HEIGHT + 2,
				level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz + HALF_Z) - 1);
		build(level, random, chunkBox, cx, floorY, cz, hatchY);
	}

	/**
	 * Build the bunker with its floor at {@code floorY} under ({@code cx}, {@code cz}) and the hatch at {@code hatchY}
	 * (the ground level over the shaft). Public so a test or a debug harness can drop one into an already-generated
	 * world (pass an infinite box to skip the chunk clipping).
	 */
	public static void build(WorldGenLevel level, RandomSource random, BoundingBox box, int cx, int floorY, int cz,
			int hatchY) {
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		BlockState floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
		int roofY = floorY + HEIGHT + 1;

		// shell (walls + roof stone bricks, floor polished andesite) + hollow interior
		for (int dx = -HALF_X - 1; dx <= HALF_X + 1; dx++) {
			for (int dz = -HALF_Z - 1; dz <= HALF_Z + 1; dz++) {
				for (int dy = -1; dy <= HEIGHT + 1; dy++) {
					boolean wall = dx == -HALF_X - 1 || dx == HALF_X + 1 || dz == -HALF_Z - 1 || dz == HALF_Z + 1;
					BlockState state;
					if (dy == -1) {
						state = wall ? brick : floor;
					} else if (wall || dy == HEIGHT + 1) {
						float r = random.nextFloat();
						state = r < 0.12f ? cracked : (r < 0.24f ? mossy : brick);
					} else {
						state = Blocks.AIR.defaultBlockState();
					}
					place(level, box, c, cx + dx, floorY + dy, cz + dz, state);
				}
			}
		}

		// ---- entrance: a lined ladder shaft in the south-middle of the room, up through the roof to a hatch ----
		int shaftX = cx;
		int shaftZ = cz + HALF_Z; // last interior row; the solid south wall (cz + HALF_Z + 1) carries the ladder
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		for (int y = floorY; y < hatchY; y++) {
			if (y >= roofY) {
				// above the room the shaft runs through earth: line it so nothing spills in and the ladder has a wall
				place(level, box, c, shaftX - 1, y, shaftZ, brick);
				place(level, box, c, shaftX + 1, y, shaftZ, brick);
				place(level, box, c, shaftX, y, shaftZ - 1, brick);
				place(level, box, c, shaftX, y, shaftZ + 1, brick);
			}
			place(level, box, c, shaftX, y, shaftZ, ladder);
		}
		// the hatch, flush with the ground and facing the ladder's way (an open trapdoor over a ladder climbs as one)
		place(level, box, c, shaftX, hatchY, shaftZ, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.HALF, Half.TOP));
		for (int dy = 1; dy <= 2; dy++) {
			place(level, box, c, shaftX, hatchY + dy, shaftZ, Blocks.AIR.defaultBlockState());
		}
		// a cobblestone ring marks it
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					place(level, box, c, shaftX + dx, hatchY, shaftZ + dz, random.nextFloat() < 0.35f
							? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
					place(level, box, c, shaftX + dx, hatchY + 1, shaftZ + dz, Blocks.AIR.defaultBlockState());
				}
			}
		}

		// ---- west wall: the weapon workbench, the Training Manual barrel in the middle ----
		int west = cx - HALF_X;
		place(level, box, c, west, floorY, cz - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
		place(level, box, c, west, floorY, cz - 1, Blocks.SMITHING_TABLE.defaultBlockState());
		place(level, box, c, west, floorY, cz + 1, Blocks.FLETCHING_TABLE.defaultBlockState());
		place(level, box, c, west, floorY, cz + 2, Blocks.GRINDSTONE.defaultBlockState()
				.setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		// the Training Manual -- guaranteed, placed directly in a barrel (opening upward)
		if (box.isInside(c.set(west, floorY, cz))) {
			level.setBlock(c, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), 2);
			if (level.getBlockEntity(c) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
				barrel.setItem(0, new ItemStack(PunisherItems.VIGILANTE_TRAINING_MANUAL));
				barrel.setItem(1, new ItemStack(com.projecthero.mod.firearm.item.FirearmItems.WEAPON_PARTS,
						1 + random.nextInt(3)));
			}
		}

		// ---- east wall: the bed (head to the north) and the two chests, fronts to the room ----
		int east = cx + HALF_X;
		place(level, box, c, east, floorY, cz - 3, Blocks.RED_BED.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		place(level, box, c, east, floorY, cz - 2, Blocks.RED_BED.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		// supply chest (vanilla stronghold-ish supplies)
		if (box.isInside(c.set(east, floorY, cz))) {
			level.setBlock(c, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2);
			if (level.getBlockEntity(c) instanceof ChestBlockEntity chest) {
				chest.setLootTable(BuiltInLootTables.STRONGHOLD_CORRIDOR, random.nextLong());
			}
		}
		// ammunition chest (loot table)
		if (box.isInside(c.set(east, floorY, cz + 2))) {
			level.setBlock(c, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2);
			if (level.getBlockEntity(c) instanceof ChestBlockEntity chest) {
				chest.setLootTable(AMMO_LOOT, random.nextLong());
			}
		}

		// ---- north end: the firing range ----
		int northWall = cz - HALF_Z - 1;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 1; dy <= 3; dy++) {
				BlockState board;
				if (dx == 0 && dy == 2) {
					board = Blocks.RED_CONCRETE.defaultBlockState(); // the bullseye
				} else if (Math.abs(dx) <= 1) {
					board = Blocks.BLACK_CONCRETE.defaultBlockState();
				} else {
					board = Blocks.WHITE_WOOL.defaultBlockState();
				}
				place(level, box, c, cx + dx, floorY + dy, northWall, board); // set into the wall, flush
			}
			// a hay-bale backstop along the wall, two target blocks standing on it
			place(level, box, c, cx + dx, floorY, cz - HALF_Z, Blocks.HAY_BLOCK.defaultBlockState());
		}
		place(level, box, c, cx - 2, floorY + 1, cz - HALF_Z, Blocks.TARGET.defaultBlockState());
		place(level, box, c, cx + 2, floorY + 1, cz - HALF_Z, Blocks.TARGET.defaultBlockState());

		// ---- a rug and the lights ----
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				place(level, box, c, cx + dx, floorY, cz + dz, Blocks.GRAY_CARPET.defaultBlockState());
			}
		}
		lantern(level, box, c, cx - 2, floorY + HEIGHT, cz);
		lantern(level, box, c, cx + 2, floorY + HEIGHT, cz);
		lantern(level, box, c, cx, floorY + HEIGHT, cz - 2);
	}

	private static void place(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos c,
			int x, int y, int z, BlockState state) {
		if (box.isInside(c.set(x, y, z))) {
			level.setBlock(c, state, 2);
		}
	}

	private static void lantern(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos c, int x, int y, int z) {
		place(level, box, c, x, y, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
	}
}
