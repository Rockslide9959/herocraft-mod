package com.projecthero.mod.hideout.worldgen;

import static com.projecthero.mod.hideout.HideoutGuards.Role.ENFORCER;
import static com.projecthero.mod.hideout.HideoutGuards.Role.PISTOL;
import static com.projecthero.mod.hideout.HideoutGuards.Role.SHOTGUN;
import static com.projecthero.mod.hideout.HideoutGuards.Role.SNIPER;
import static com.projecthero.mod.hideout.HideoutGuards.Role.THUG;

import com.projecthero.mod.hideout.HideoutEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * v0.17.5: <b>Kingpin's Front</b> -- "Fisk Imports", a two-storey brick pawn shop on a strip of street, with a warehouse
 * behind it and the real business underground.
 * <ul>
 * <li><b>Street floor</b>: the shop (counter, shelves, display cases); behind the counter a trapdoor hides a ladder down
 * -- the hidden stair.</li>
 * <li><b>Warehouse</b>: crates, a catwalk with a sniper, the side door tripwired to a siren box (alarm).</li>
 * <li><b>Upper office</b>: Fisk Imports' front office, reached from the catwalk.</li>
 * <li><b>Basement</b>: the back offices, a corridor with pressure-plate arrow traps, another tripwire, then the vault room:
 * the Hideout Core on a gold pedestal, the sealed Hideout Vault against the back wall, and Kingpin's Enforcer.</li>
 * </ul>
 * Local frame: street (entrance) to the north (-z); y = 0 is the shop floor.
 */
public final class KingpinFrontBuilder extends HideoutBuilder {
	public static final KingpinFrontBuilder INSTANCE = new KingpinFrontBuilder();

	static final BlockPos CORE = new BlockPos(6, -6, 16);
	static final BlockPos VAULT = new BlockPos(6, -7, 20);

	private KingpinFrontBuilder() {
		super(-12, -10, -16, 15, 13, 22);
	}

	@Override
	public BlockPos coreLocal() {
		return CORE;
	}

	@Override
	public BlockPos vaultLocal() {
		return VAULT;
	}

	private static BlockState pane(boolean alongX) {
		BlockState s = Blocks.GLASS_PANE.defaultBlockState();
		return alongX ? s.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
				: s.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
	}

	private static BlockState bars(boolean alongX) {
		BlockState s = Blocks.IRON_BARS.defaultBlockState();
		return alongX ? s.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
				: s.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
	}

	private static BlockState stair(net.minecraft.world.level.block.Block b, Direction facing, Half half) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
	}

	private static BlockState slab(net.minecraft.world.level.block.Block b, SlabType type) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, type);
	}

	@Override
	public void build(HideoutCanvas c) {
		BlockState brick = Blocks.BRICKS.defaultBlockState();
		BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState trim = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		BlockState lanternHang = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);

		// ------------------------------------------------ ground: clear above, footing below, street
		c.air(-12, 1, -16, 12, 14, 13);
		for (int x = -12; x <= 12; x++) {
			for (int z = -16; z <= 13; z++) {
				boolean building = x >= -11 && x <= 11 && z >= -12 && z <= 12;
				c.footing(x, -1, z, 12, building ? stone : Blocks.DIRT.defaultBlockState());
				if (!building) {
					BlockState top;
					if (z <= -15) {
						top = z == -16 && x % 4 != 0 ? Blocks.GRAY_CONCRETE.defaultBlockState()
								: (x % 4 == 0 && z == -15 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
					} else if (z <= -13) {
						top = Blocks.SMOOTH_STONE.defaultBlockState();
					} else {
						top = c.noise(x, z, 1) < 0.2 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
					}
					c.set(x, 0, z, top);
				}
			}
		}
		// street lamps
		for (int x : new int[] { -10, 10 }) {
			c.fill(x, 1, -14, x, 4, -14, Blocks.DARK_OAK_FENCE.defaultBlockState());
			c.set(x, 5, -14, Blocks.LANTERN);
		}

		// ------------------------------------------------ basement shells (before the building so its floor caps them)
		basement(c, stone, lanternHang);

		// ------------------------------------------------ the building shell
		c.fill(-11, 0, -12, 11, 0, 12, Blocks.POLISHED_ANDESITE.defaultBlockState());
		c.fill(-10, 0, -1, 10, 0, 11, Blocks.SMOOTH_STONE.defaultBlockState());
		c.walls(-11, 1, -12, 11, 10, 12, brick);
		// corner piers and a trim band
		for (int[] p : new int[][] { { -11, -12 }, { 11, -12 }, { -11, 12 }, { 11, 12 } }) {
			c.fill(p[0], 1, p[1], p[0], 11, p[1], stone);
		}
		c.fill(-11, 5, -12, 11, 5, -12, trim);
		// flat roof with a parapet, a skylight strip and a couple of AC units
		c.fill(-11, 11, -12, 11, 11, 12, stone);
		c.fill(-3, 11, 1, 3, 11, 9, Blocks.GLASS.defaultBlockState());
		c.walls(-11, 12, -12, 11, 12, 12, Blocks.STONE_BRICK_WALL.defaultBlockState());
		c.fill(-7, 12, 4, -6, 13, 6, Blocks.IRON_BLOCK.defaultBlockState());
		c.fill(6, 12, -8, 7, 12, -6, Blocks.IRON_BLOCK.defaultBlockState());
		// ------------------------------------------------ façade (north wall)
		for (int x = -9; x <= 9; x++) {
			if (Math.abs(x) >= 3) {
				c.fill(x, 2, -12, x, 4, -12, pane(true));
			}
		}
		c.air(0, 1, -12, 0, 2, -12);
		c.door(0, 1, -12, Blocks.DARK_OAK_DOOR, Direction.SOUTH);
		// striped awning
		for (int x = -10; x <= 10; x++) {
			c.set(x, 5, -13, (x & 1) == 0 ? Blocks.RED_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState());
		}
		// "FISK" sign: gold letters on a dark board above the awning
		c.fill(-5, 6, -13, 5, 6, -13, Blocks.BLACK_CONCRETE.defaultBlockState());
		for (int x : new int[] { -4, -2, 0, 2, 4 }) {
			c.set(x, 6, -13, Blocks.GOLD_BLOCK);
		}
		// upper windows
		for (int[] w : new int[][] { { -8, -6 }, { -1, 1 }, { 6, 8 } }) {
			c.fill(w[0], 7, -12, w[1], 9, -12, pane(true));
		}
		// side windows (shop) and the warehouse's side door
		c.fill(-11, 2, -9, -11, 4, -6, pane(false));
		c.fill(11, 2, -9, 11, 4, -6, pane(false));
		c.air(11, 1, 5, 11, 2, 5);
		c.door(11, 1, 5, Blocks.IRON_DOOR, Direction.WEST);
		// the (closed) loading door at the back
		c.fill(-4, 1, 12, 4, 5, 12, Blocks.YELLOW_CONCRETE.defaultBlockState());
		c.fill(-3, 1, 12, 3, 4, 12, Blocks.IRON_BLOCK.defaultBlockState());

		// ------------------------------------------------ the shop (z -11..-3)
		c.fill(-10, 6, -11, 10, 6, -3, trim); // upper floor
		c.fill(-10, 1, -2, 10, 10, -2, brick); // shop / warehouse wall
		c.air(-8, 1, -2, -7, 3, -2);
		c.fill(0, 0, -11, 0, 0, -8, Blocks.RED_WOOL.defaultBlockState()); // runner (wool in the floor reads as carpet)
		// the counter, with a flip gap at x 6..7
		for (int x = -6; x <= 5; x++) {
			c.set(x, 1, -7, trim);
			c.set(x, 2, -7, slab(Blocks.POLISHED_GRANITE_SLAB, SlabType.BOTTOM));
		}
		c.set(-6, 1, -6, trim);
		c.set(-6, 2, -6, slab(Blocks.POLISHED_GRANITE_SLAB, SlabType.BOTTOM));
		c.set(-2, 3, -7, Blocks.LANTERN);
		// display cases along the side walls (glass on a dark base, something shiny inside)
		for (int z = -10; z <= -8; z++) {
			c.set(-10, 1, z, trim);
			c.set(-10, 2, z, z == -9 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.GLASS.defaultBlockState());
			c.set(10, 1, z, trim);
			c.set(10, 2, z, z == -9 ? Blocks.EMERALD_BLOCK.defaultBlockState() : Blocks.GLASS.defaultBlockState());
		}
		// shelves along the back of the shop
		for (int x = -10; x <= 3; x++) {
			c.set(x, 1, -3, (x & 1) == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
			c.set(x, 2, -3, Blocks.BOOKSHELF);
			c.set(x, 3, -3, c.noise(x, -3, 2) < 0.5 ? Blocks.FLOWER_POT.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		c.container(-9, 1, -4, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		// the hidden hatch behind the counter
		c.set(8, 0, -5, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
				.setValue(TrapDoorBlock.FACING, Direction.NORTH));
		c.set(7, 0, -5, Blocks.BROWN_WOOL);
		c.set(7, 0, -6, Blocks.BROWN_WOOL);
		for (int x = -7; x <= 7; x += 7) {
			c.set(x, 5, -7, lanternHang);
		}
		c.guard(THUG, -4, 1, -9, Direction.NORTH, 6);
		c.guard(THUG, 3, 1, -5, Direction.NORTH, 5);

		// ------------------------------------------------ the warehouse (z -1..11)
		for (int x = -9; x <= 6; x += 3) { // the east aisle (x 8..10) stays clear for the side door
			for (int z = 1; z <= 9; z += 4) {
				if (Math.abs(x) <= 1 && z == 5) {
					continue; // a clear middle
				}
				int h = 1 + (int) (c.noise(x, z, 3) * 3);
				for (int y = 1; y <= h; y++) {
					double n = c.noise(x, y * 13 + z, 4);
					BlockState crate = n < 0.4 ? Blocks.BARREL.defaultBlockState() : n < 0.7 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
							: Blocks.HAY_BLOCK.defaultBlockState();
					c.set(x, y, z, crate);
					if (c.noise(x, z, 5) < 0.5) {
						c.set(x + 1, y, z, crate);
					}
				}
			}
		}
		c.container(-6, 1, 10, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		// catwalk along the back and the east side, up to the office door
		c.fill(-10, 6, 9, 10, 6, 11, trim);
		c.fill(8, 6, -1, 10, 6, 8, trim);
		for (int x = -10; x <= 7; x++) {
			c.set(x, 7, 8, Blocks.DARK_OAK_FENCE.defaultBlockState());
		}
		for (int z = -1; z <= 7; z++) {
			c.set(7, 7, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
		}
		for (int y = 1; y <= 7; y++) {
			c.set(-10, y, 11, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
		}
		c.set(-10, 6, 11, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
		c.air(8, 7, -2, 9, 8, -2); // into the upper office
		for (int x = -6; x <= 6; x += 6) {
			c.fill(x, 9, 4, x, 10, 4, Blocks.CHAIN.defaultBlockState());
			c.set(x, 8, 4, lanternHang);
		}
		// the side door's tripwire and siren box
		c.set(10, 1, 9, Blocks.BARREL);
		c.tripwireZ(3, 8, 1, 10);
		c.guard(THUG, -4, 1, 3, Direction.NORTH, 6);
		c.guard(THUG, 2, 1, 7, Direction.EAST, 6);
		c.guard(THUG, 6, 1, 2, Direction.WEST, 6);
		c.guard(PISTOL, -1, 1, 7, Direction.NORTH, 6);
		c.guard(SHOTGUN, 4, 1, 10, Direction.NORTH, 6);
		c.guard(ENFORCER, 0, 1, 4, Direction.NORTH, 6);
		c.guard(SNIPER, 4, 7, 10, Direction.NORTH, 3);

		// ------------------------------------------------ the upper office (over the shop, y 7..10)
		for (int x = -8; x <= -4; x++) {
			c.set(x, 7, -9, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, Half.TOP));
		}
		c.set(-6, 7, -10, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, Half.BOTTOM));
		c.fill(-10, 7, -4, -10, 9, -3, Blocks.BOOKSHELF.defaultBlockState());
		c.fill(4, 7, -10, 6, 7, -10, Blocks.IRON_BLOCK.defaultBlockState()); // the "safe"
		c.container(-9, 7, -10, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		c.set(0, 10, -7, lanternHang);
		c.guard(PISTOL, 0, 7, -6, Direction.NORTH, 5);
	}

	private void basement(HideoutCanvas c, BlockState stone, BlockState lanternHang) {
		BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		// corridor A (x 7..8, z -9..-1) and B (z 0..8), one shell
		c.room(6, -7, -10, 9, -2, 8, stone);
		c.fill(7, -7, -9, 8, -7, 7, floor);
		// the ladder shaft up to the hatch behind the shop counter
		c.fill(7, -1, -6, 9, -1, -4, stone);
		c.air(8, -2, -5, 8, -1, -5);
		for (int y = -6; y <= -1; y++) {
			c.set(8, y, -5, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		}
		for (int z = -8; z <= 6; z += 4) {
			c.set(7, -3, z, lanternHang);
		}
		// the back offices (west of corridor A)
		c.room(-4, -7, -11, 6, -2, -2, stone);
		c.fill(-3, -7, -10, 5, -7, -3, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		c.air(6, -6, -7, 6, -5, -6);
		for (int x = -2; x <= 2; x += 4) {
			c.set(x, -6, -8, Blocks.DARK_OAK_PLANKS);
			c.set(x + 1, -6, -8, Blocks.DARK_OAK_PLANKS);
			c.set(x, -5, -8, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
			c.set(x, -6, -7, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, Half.BOTTOM));
		}
		c.fill(-3, -6, -10, -3, -4, -3, Blocks.BOOKSHELF.defaultBlockState());
		c.fill(0, -6, -10, 4, -5, -10, Blocks.BARREL.defaultBlockState());
		c.container(4, -6, -3, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		c.set(1, -3, -6, lanternHang);
		c.guard(THUG, 0, -6, -5, Direction.EAST, 5);
		c.guard(PISTOL, 3, -6, -6, Direction.EAST, 5);
		// arrow traps in corridor B: two rows of pressure plates, a dispenser in each wall beside each plate
		for (int z : new int[] { 2, 4 }) {
			c.arrowDispenser(6, -6, z, Direction.EAST);
			c.arrowDispenser(9, -6, z, Direction.WEST);
			c.set(7, -6, z, Blocks.STONE_PRESSURE_PLATE);
			c.set(8, -6, z, Blocks.STONE_PRESSURE_PLATE);
		}
		// the tripwire before the vault room: corridor widened to 3 at z 6..8
		c.fill(10, -7, 5, 10, -2, 9, stone);
		c.air(9, -6, 6, 9, -3, 8);
		c.set(9, -7, 6, floor);
		c.set(9, -7, 7, floor);
		c.set(9, -7, 8, floor);
		c.tripwireX(7, 9, -6, 7);
		c.set(10, -6, 7, stone);

		// ------------------------------------------------ the vault room (x -2..14, z 9..21, floor y -8)
		c.room(-2, -8, 9, 14, -2, 21, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		c.fill(-1, -8, 10, 13, -8, 20, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
		c.air(7, -6, 9, 8, -4, 9); // the doorway from the corridor (one step down)
		c.set(7, -7, 10, stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.SOUTH, Half.BOTTOM));
		c.set(8, -7, 10, stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.SOUTH, Half.BOTTOM));
		// gold trim and pillars
		c.fill(-1, -3, 10, 13, -3, 10, Blocks.GOLD_BLOCK.defaultBlockState());
		c.fill(-1, -3, 20, 13, -3, 20, Blocks.GOLD_BLOCK.defaultBlockState());
		for (int[] p : new int[][] { { 0, 12 }, { 12, 12 }, { 0, 18 }, { 12, 18 } }) {
			c.fill(p[0], -7, p[1], p[0], -3, p[1], Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
			c.set(p[0], -4, p[1], Blocks.GOLD_BLOCK);
		}
		// red carpet from the door to the pedestal
		c.fill(6, -7, 11, 7, -7, 14, Blocks.RED_CARPET.defaultBlockState());
		// the pedestal and the core
		c.fill(5, -7, 15, 7, -7, 17, Blocks.GOLD_BLOCK.defaultBlockState());
		c.set(6, -7, 16, Blocks.CHISELED_POLISHED_BLACKSTONE);
		c.core(CORE.getX(), CORE.getY(), CORE.getZ());
		// the sealed vault on the back wall, behind bars
		c.sealedVault(VAULT.getX(), VAULT.getY(), VAULT.getZ(), Direction.NORTH);
		c.set(5, -7, 20, Blocks.CHISELED_POLISHED_BLACKSTONE);
		c.set(7, -7, 20, Blocks.CHISELED_POLISHED_BLACKSTONE);
		c.set(6, -6, 20, Blocks.CHISELED_POLISHED_BLACKSTONE);
		// money pallets and gold stacks
		for (int[] p : new int[][] { { 0, 15 }, { 1, 19 }, { 11, 15 }, { 12, 19 }, { 10, 19 } }) {
			c.set(p[0], -7, p[1], Blocks.GREEN_WOOL);
			c.set(p[0], -6, p[1], c.noise(p[0], p[1], 6) < 0.5 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.EMERALD_BLOCK.defaultBlockState());
		}
		// the boss's desk
		c.fill(1, -7, 12, 3, -7, 12, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		c.set(2, -7, 13, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH, Half.BOTTOM));
		// cages of contraband
		for (int z = 13; z <= 17; z++) {
			c.set(13, -7, z, bars(false));
			c.set(13, -6, z, bars(false));
		}
		for (int[] p : new int[][] { { 3, 14 }, { 9, 14 }, { 3, 18 }, { 9, 18 } }) {
			c.set(p[0], -3, p[1], Blocks.SEA_LANTERN);
		}
		c.guard(THUG, 3, -7, 11, Direction.NORTH, 5);
		c.guard(THUG, 10, -7, 11, Direction.NORTH, 5);
		c.boss(HideoutEntityTypes.KINGPIN_ENFORCER, 6, -7, 13, Direction.NORTH, c.corePos(CORE.getX(), CORE.getY(), CORE.getZ()));
	}
}
