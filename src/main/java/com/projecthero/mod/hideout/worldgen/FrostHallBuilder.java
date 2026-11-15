package com.projecthero.mod.hideout.worldgen;

import static com.projecthero.mod.hideout.HideoutGuards.Role.HOUND;
import static com.projecthero.mod.hideout.HideoutGuards.Role.RAIDER;
import static com.projecthero.mod.hideout.HideoutGuards.Role.SHAMAN;

import com.projecthero.mod.hideout.HideoutEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * v0.17.5: the <b>Frost Giant Hall</b> -- a Norse longhall of dark timber and blue ice on the snow, big enough for
 * giants: a steep gabled roof (ridge 15 up), log pillars and cross-beams, soul-lantern chandeliers, a long fire down the
 * middle, benches and tables, and at the far end a raised dais with the Jarl's ice throne.
 * <ul>
 * <li><b>The vestibule</b>: two rows of pressure plates over floor dispensers -- arrows from below.</li>
 * <li><b>The side door</b>: tripwired to a war horn (alarm: more Rime Hounds).</li>
 * <li><b>The dais</b>: the Hideout Core on a blue-ice pedestal, the sealed Hideout Vault by the throne; the Frost Giant
 * Jarl holds the floor in front of it.</li>
 * </ul>
 * y = 0 is the hall floor (the surface).
 */
public final class FrostHallBuilder extends HideoutBuilder {
	public static final FrostHallBuilder INSTANCE = new FrostHallBuilder();

	static final BlockPos CORE = new BlockPos(0, 4, 14);
	static final BlockPos VAULT = new BlockPos(5, 3, 19);

	private FrostHallBuilder() {
		super(-12, -2, -23, 12, 17, 22);
	}

	@Override
	public BlockPos coreLocal() {
		return CORE;
	}

	@Override
	public BlockPos vaultLocal() {
		return VAULT;
	}

	/** Roof height over local x (the gable). */
	static int roofY(int x) {
		return 8 + (11 - Math.min(11, Math.abs(x))) * 7 / 11;
	}

	private static BlockState stair(net.minecraft.world.level.block.Block b, Direction facing, Half half) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
	}

	@Override
	public void build(HideoutCanvas c) {
		BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState dark = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		BlockState post = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
		BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
		BlockState blue = Blocks.BLUE_ICE.defaultBlockState();
		BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();

		// ------------------------------------------------ ground
		c.air(-12, 1, -23, 12, 17, 22);
		for (int x = -12; x <= 12; x++) {
			for (int z = -23; z <= 22; z++) {
				c.footing(x, -1, z, 10, stone);
				boolean hall = Math.abs(x) <= 10 && z >= -20 && z <= 20;
				if (!hall) {
					c.set(x, 0, z, c.noise(x, z, 1) < 0.3 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
				}
			}
		}
		// the hall floor: spruce, a stone border, an ice path down the middle
		c.fill(-10, 0, -20, 10, 0, 20, plank);
		c.walls(-10, 0, -20, 10, 0, 20, stone);
		c.fill(-2, 0, -19, 2, 0, 11, Blocks.POLISHED_DIORITE.defaultBlockState());
		// approach: an ice path and two standing stones
		c.fill(-1, 0, -23, 1, 0, -21, ice);
		for (int x : new int[] { -4, 4 }) {
			c.fill(x, 1, -22, x, 3, -22, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			c.set(x, 4, -22, Blocks.SOUL_LANTERN);
		}

		// ------------------------------------------------ walls: dark posts, spruce panels over an ice footing band
		for (int y = 1; y <= 7; y++) {
			for (int x = -10; x <= 10; x++) {
				for (int z : new int[] { -20, 20 }) {
					c.set(x, y, z, x % 5 == 0 ? post : y == 1 ? ice : plank);
				}
			}
			for (int z = -20; z <= 20; z++) {
				for (int x : new int[] { -10, 10 }) {
					c.set(x, y, z, z % 5 == 0 ? post : y == 1 ? ice : plank);
				}
			}
		}
		// small blue-glass windows
		for (int z = -15; z <= 15; z += 5) {
			for (int x : new int[] { -10, 10 }) {
				if (z != 5 || x != 10) {
					c.fill(x, 4, z + 2, x, 5, z + 3, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
				}
			}
		}
		// the great doors (open) and the side door
		c.air(-1, 1, -20, 1, 4, -20);
		c.fill(-2, 5, -20, 2, 5, -20, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		c.air(10, 1, 4, 10, 2, 4);
		c.door(10, 1, 4, Blocks.SPRUCE_DOOR, Direction.WEST);

		// ------------------------------------------------ the roof (gable along z) and its end walls
		for (int x = -11; x <= 11; x++) {
			int ry = roofY(x);
			for (int z = -21; z <= 21; z++) {
				BlockState s = x == 0 ? Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)
						: stair(Blocks.DARK_OAK_STAIRS, x < 0 ? Direction.EAST : Direction.WEST, Half.BOTTOM);
				c.set(x, ry, z, s);
				if (x != 0 && roofY(x + Integer.signum(-x)) > ry + 1) {
					c.set(x, ry + 1, z, dark);
				}
				if (Math.abs(x) <= 10 && (z == -20 || z == 20)) {
					c.fill(x, 8, z, x, ry - 1, z, x % 5 == 0 ? post : plank);
				}
			}
			if (Math.abs(x) <= 1) {
				c.fill(x, ry + 1, -21, x, ry + 1, 21, Blocks.SNOW_BLOCK.defaultBlockState());
			}
		}
		// gable-end round windows and crossed "dragon" posts on the ridge ends
		c.fill(-1, 10, -20, 1, 11, -20, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
		c.fill(-1, 10, 20, 1, 11, 20, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
		for (int z : new int[] { -22, 22 }) {
			c.set(-1, roofY(0) + 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
			c.set(1, roofY(0) + 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
			c.set(-1, roofY(0) + 2, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
			c.set(1, roofY(0) + 2, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
		}

		// ------------------------------------------------ inside: pillars, beams, chandeliers, the long fire, benches
		for (int z = -15; z <= 15; z += 5) {
			for (int x : new int[] { -6, 6 }) {
				c.fill(x, 1, z, x, 8, z, post);
			}
			c.fill(-9, 8, z, 9, 8, z, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		}
		for (int z : new int[] { -10, 0, 10 }) {
			c.fill(0, 11, z, 0, roofY(0) - 1, z, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
			c.set(0, 10, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			c.set(-1, 10, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
			c.set(1, 10, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
			c.set(-1, 9, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
			c.set(1, 9, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
		}
		// the long fire: soul campfires in a stone trough
		for (int z = -12; z <= 6; z++) {
			c.set(-1, 0, z, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
			c.set(1, 0, z, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
			c.set(0, 0, z, Blocks.MAGMA_BLOCK);
			if (z % 3 == 0) {
				c.set(0, 1, z, Blocks.SOUL_CAMPFIRE);
			}
		}
		// benches and tables along the walls
		for (int z = -18; z <= 10; z++) {
			if (z % 5 == 0) {
				continue;
			}
			for (int side : new int[] { -1, 1 }) {
				c.set(side * 9, 1, z, Blocks.SPRUCE_SLAB.defaultBlockState());
				if (z % 5 == 2 || z % 5 == -3) {
					c.set(side * 8, 1, z, plank);
					c.set(side * 8, 2, z, Blocks.WHITE_CARPET.defaultBlockState());
				}
			}
		}
		c.container(-9, 1, -19, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		c.container(9, 1, 11, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));

		// ------------------------------------------------ the vestibule trap: plates over floor dispensers firing up
		for (int z : new int[] { -18, -16 }) {
			for (int x = -1; x <= 1; x++) {
				c.arrowDispenser(x, 0, z, Direction.UP);
				c.set(x, 1, z, Blocks.SPRUCE_PRESSURE_PLATE);
			}
		}
		// the side door's war horn
		c.set(9, 1, 7, Blocks.BARREL);
		c.tripwireZ(2, 6, 1, 9);

		// ------------------------------------------------ the dais and the throne
		c.fill(-9, 1, 13, 9, 2, 19, stone);
		c.fill(-9, 2, 13, 9, 2, 19, ice);
		for (int x = -9; x <= 9; x++) {
			c.set(x, 1, 12, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, Half.BOTTOM));
			c.set(x, 2, 13, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, Half.BOTTOM));
		}
		c.set(0, 3, 14, blue);
		c.core(CORE.getX(), CORE.getY(), CORE.getZ());
		// the ice throne
		c.set(0, 3, 18, stair(Blocks.QUARTZ_STAIRS, Direction.NORTH, Half.BOTTOM));
		c.fill(-1, 3, 19, 1, 6, 19, blue);
		c.set(-1, 3, 18, ice);
		c.set(1, 3, 18, ice);
		c.set(-1, 7, 19, Blocks.ICE);
		c.set(1, 7, 19, Blocks.ICE);
		// the sealed vault and a weapon rack beside the throne
		c.sealedVault(VAULT.getX(), VAULT.getY(), VAULT.getZ(), Direction.NORTH);
		c.fill(-5, 3, 19, -5, 4, 19, Blocks.SPRUCE_FENCE.defaultBlockState());
		c.set(-6, 3, 19, Blocks.ANVIL);
		for (int x : new int[] { -7, 7 }) {
			c.set(x, 3, 14, Blocks.SOUL_CAMPFIRE);
		}

		// ------------------------------------------------ the household
		c.guard(HOUND, -3, 1, -8, Direction.NORTH, 6);
		c.guard(HOUND, 3, 1, -6, Direction.NORTH, 6);
		c.guard(HOUND, -3, 1, -2, Direction.NORTH, 6);
		c.guard(RAIDER, -7, 1, -3, Direction.EAST, 5);
		c.guard(RAIDER, 7, 1, 3, Direction.WEST, 5);
		c.guard(SHAMAN, -4, 3, 17, Direction.NORTH, 3);
		c.boss(HideoutEntityTypes.FROST_GIANT_JARL, 0, 1, 9, Direction.NORTH, c.corePos(CORE.getX(), CORE.getY(), CORE.getZ()));
	}
}
