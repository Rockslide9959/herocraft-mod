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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A small buried bunker: a stone-brick room a few blocks below the surface, reached by a short
 * ladder shaft with a trapdoor. Fittings: a weapon workbench (crafting + smithing table), an
 * ammunition chest (loot table), a supply chest, a wall of target boards, a bed and a couple of
 * lanterns. The Vigilante Training Manual is placed <em>directly</em> in a barrel so it is always
 * there, not left to a loot roll.
 *
 * <p>Modelled on {@code SteelCrashSitePiece} / {@code GraveyardPiece}: no stored state beyond the
 * base bounding box, everything built at {@link #postProcess} (real terrain exists then), every
 * placement clipped to the current chunk box.
 */
public class VigilanteSafehousePiece extends StructurePiece {
	public static final ResourceKey<LootTable> AMMO_LOOT = ResourceKey.create(
			net.minecraft.core.registries.Registries.LOOT_TABLE,
			com.projecthero.mod.ProjectHeroMod.id("chests/vigilante_safehouse"));

	private static final int HALF_X = 4;
	private static final int HALF_Z = 3;
	private static final int HEIGHT = 4;
	private static final int DEPTH = 5; // floor this far below the surface

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

		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();

		// shell + hollow interior
		for (int dx = -HALF_X - 1; dx <= HALF_X + 1; dx++) {
			for (int dz = -HALF_Z - 1; dz <= HALF_Z + 1; dz++) {
				for (int dy = -1; dy <= HEIGHT + 1; dy++) {
					int x = cx + dx;
					int y = floorY + dy;
					int z = cz + dz;
					if (!chunkBox.isInside(c.set(x, y, z))) {
						continue;
					}
					boolean shell = dx == -HALF_X - 1 || dx == HALF_X + 1
							|| dz == -HALF_Z - 1 || dz == HALF_Z + 1
							|| dy == -1 || dy == HEIGHT + 1;
					if (shell) {
						float r = random.nextFloat();
						level.setBlock(c, r < 0.12f ? cracked : (r < 0.24f ? mossy : brick), 2);
					} else {
						level.setBlock(c, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}

		// ladder shaft up to the surface, at the +Z wall
		int shaftX = cx;
		int shaftZ = cz + HALF_Z + 1;
		for (int y = floorY; y <= surfaceY + 1; y++) {
			if (chunkBox.isInside(c.set(shaftX, y, shaftZ))) {
				level.setBlock(c, Blocks.AIR.defaultBlockState(), 2);
			}
			if (chunkBox.isInside(c.set(shaftX, y, shaftZ + 1))) {
				level.setBlock(c, brick, 2);
			}
			if (chunkBox.isInside(c.set(shaftX, y, shaftZ))) {
				level.setBlock(c, Blocks.LADDER.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 2);
			}
		}
		if (chunkBox.isInside(c.set(shaftX, surfaceY + 1, shaftZ))) {
			level.setBlock(c, Blocks.IRON_TRAPDOOR.defaultBlockState()
					.setValue(BlockStateProperties.HALF, net.minecraft.world.level.block.state.properties.Half.TOP), 2);
		}

		// ---- interior fittings ----
		place(level, chunkBox, c, cx - 3, floorY, cz - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
		place(level, chunkBox, c, cx - 2, floorY, cz - 2, Blocks.SMITHING_TABLE.defaultBlockState());
		place(level, chunkBox, c, cx - 3, floorY, cz + 2, Blocks.FLETCHING_TABLE.defaultBlockState());

		lantern(level, chunkBox, c, cx - HALF_X, floorY + HEIGHT, cz - HALF_Z);
		lantern(level, chunkBox, c, cx + HALF_X, floorY + HEIGHT, cz + HALF_Z);

		// bed (basic supplies / the vigilante slept here)
		if (chunkBox.isInside(c.set(cx + 3, floorY, cz - 2))) {
			level.setBlock(c, Blocks.RED_BED.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
					.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
		}
		if (chunkBox.isInside(c.set(cx + 3, floorY, cz - 3))) {
			level.setBlock(c, Blocks.RED_BED.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
					.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
		}

		// target boards on the -Z wall
		for (int dx = -2; dx <= 2; dx++) {
			place(level, chunkBox, c, cx + dx, floorY + 2, cz - HALF_Z, Blocks.WHITE_WOOL.defaultBlockState());
		}
		place(level, chunkBox, c, cx, floorY + 2, cz - HALF_Z, Blocks.RED_CONCRETE.defaultBlockState());
		place(level, chunkBox, c, cx - 2, floorY + 2, cz - HALF_Z, Blocks.BLACK_CONCRETE.defaultBlockState());
		place(level, chunkBox, c, cx + 2, floorY + 2, cz - HALF_Z, Blocks.BLACK_CONCRETE.defaultBlockState());

		// ammunition chest (loot table)
		if (chunkBox.isInside(c.set(cx + 2, floorY, cz + 2))) {
			level.setBlock(c, Blocks.CHEST.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2);
			if (level.getBlockEntity(c) instanceof ChestBlockEntity chest) {
				chest.setLootTable(AMMO_LOOT, random.nextLong());
			}
		}
		// supply chest (vanilla stronghold-ish supplies)
		if (chunkBox.isInside(c.set(cx + 2, floorY, cz + 1))) {
			level.setBlock(c, Blocks.CHEST.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2);
			if (level.getBlockEntity(c) instanceof ChestBlockEntity chest) {
				chest.setLootTable(BuiltInLootTables.STRONGHOLD_CORRIDOR, random.nextLong());
			}
		}
		// the Training Manual -- guaranteed, placed directly in a barrel
		if (chunkBox.isInside(c.set(cx - 3, floorY, cz))) {
			level.setBlock(c, Blocks.BARREL.defaultBlockState(), 2);
			if (level.getBlockEntity(c) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
				barrel.setItem(0, new ItemStack(PunisherItems.VIGILANTE_TRAINING_MANUAL));
				barrel.setItem(1, new ItemStack(com.projecthero.mod.firearm.item.FirearmItems.WEAPON_PARTS,
						1 + random.nextInt(3)));
			}
		}
	}

	private void place(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos c,
			int x, int y, int z, BlockState state) {
		if (box.isInside(c.set(x, y, z))) {
			level.setBlock(c, state, 2);
		}
	}

	private void lantern(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos c, int x, int y, int z) {
		if (box.isInside(c.set(x, y, z))) {
			level.setBlock(c, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), 2);
		}
	}
}
