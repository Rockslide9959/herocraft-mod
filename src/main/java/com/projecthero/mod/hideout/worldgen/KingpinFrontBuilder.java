package com.projecthero.mod.hideout.worldgen;

import static com.projecthero.mod.hideout.HideoutGuards.Role.ENFORCER;
import static com.projecthero.mod.hideout.HideoutGuards.Role.PISTOL;
import static com.projecthero.mod.hideout.HideoutGuards.Role.SHOTGUN;
import static com.projecthero.mod.hideout.HideoutGuards.Role.SNIPER;
import static com.projecthero.mod.hideout.HideoutGuards.Role.THUG;

import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.hideout.HideoutEntityTypes;
import com.projecthero.mod.hideout.block.HideoutVentBlock;

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

	// v0.17.7: the crawl wing below the counting room (the old vault room); its stairwell opens at z 13
	static final int YTOP = -8;
	static final int AX = 6;
	static final int AY = -20;
	static final int AZ = 13 + (YTOP - AY);
	static final BlockPos CORE = CrawlWing.core(AX, AY, AZ);
	static final BlockPos VAULT = CrawlWing.vault(AX, AY, AZ);
	static final CrawlTheme THEME = theme();

	private KingpinFrontBuilder() {
		super(-12, AY - 3, -16, AX + CrawlWing.HALF_WIDTH, 13, AZ + CrawlWing.END_Z);
	}

	private static CrawlTheme theme() {
		CrawlTheme t = new CrawlTheme();
		t.wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		t.floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		t.ceiling = t.wall;
		t.trim = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		t.pillar = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
		t.light = Blocks.SEA_LANTERN.defaultBlockState();
		t.accent = Blocks.GOLD_BLOCK.defaultBlockState();
		t.stairs = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
		t.vent = HideoutVentBlock.Kind.FIRE;
		t.grunt = THUG;
		t.elite = ENFORCER;
		t.ranged = PISTOL;
		t.shield = THUG;
		t.captain = ENFORCER;
		t.runner = THUG;
		t.sniper = SNIPER;
		t.boss = HideoutEntityTypes.KINGPIN_ENFORCER;
		t.decor = new CrawlTheme.Decor() {
			@Override
			public void checkpoint(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// a security desk with monitors and a locker row
				c.fill(x1 - 1, y + 1, z0 + 2, x1, y + 1, z0 + 5, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				c.set(x1, y + 2, z0 + 3, Blocks.BLACK_STAINED_GLASS);
				c.set(x1, y + 2, z0 + 4, Blocks.BLACK_STAINED_GLASS);
				c.fill(x0, y + 1, z1 - 1, x0, y + 3, z1, Blocks.IRON_BLOCK.defaultBlockState());
			}

			@Override
			public void quarters(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the Captain's office: a desk, a red rug, a bookcase wall, a safe and a city map
				c.fill(x0 + 4, y, z0 + 3, x0 + 8, y, z0 + 7, Blocks.RED_WOOL.defaultBlockState());
				c.fill(x0 + 5, y + 1, z0 + 6, x0 + 7, y + 1, z0 + 6, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				c.set(x0 + 6, y + 2, z0 + 6, Blocks.LANTERN);
				c.fill(x0, y + 1, z0 + 2, x0, y + 4, z1, Blocks.BOOKSHELF.defaultBlockState());
				c.fill(x0 + 1, y + 1, z1, x0 + 2, y + 2, z1, Blocks.IRON_BLOCK.defaultBlockState());
				c.fill(x0 + 3, y + 3, z1 + 1, x0 + 7, y + 5, z1 + 1, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
				for (int[] p : new int[][] { { 4, 4 }, { 6, 3 }, { 5, 5 }, { 7, 4 } }) {
					c.set(x0 + p[0], y + p[1], z1 + 1, Blocks.RED_CONCRETE);
				}
			}

			@Override
			public void arena(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the fight club: a roped ring of chains, cash pallets and crates round the walls
				int cx = (x0 + x1) / 2;
				int cz = (z0 + z1) / 2;
				for (int[] p : new int[][] { { -4, -4 }, { 4, -4 }, { -4, 4 }, { 4, 4 } }) {
					c.fill(cx + p[0], y + 1, cz + p[1], cx + p[0], y + 2, cz + p[1], Blocks.DARK_OAK_FENCE.defaultBlockState());
				}
				for (int[] p : new int[][] { { x0, z0 + 1 }, { x0 + 1, z0 + 1 }, { x1, z0 + 2 }, { x0, z1 - 4 }, { x1, z1 - 5 }, { x1 - 1, z1 - 5 } }) {
					c.set(p[0], y + 1, p[1], Blocks.GREEN_WOOL);
					c.set(p[0], y + 2, p[1], c.noise(p[0], p[1], 30) < 0.5 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.EMERALD_BLOCK.defaultBlockState());
				}
				for (int z = z0 + 6; z <= z1 - 7; z += 3) {
					c.set(x0, y + 1, z, Blocks.BARREL);
					c.set(x1, y + 1, z, Blocks.SPRUCE_PLANKS);
				}
			}

			@Override
			public void vault(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				c.fill(x0, y + 1, z0, x0, y + 2, z1, Blocks.GOLD_BLOCK.defaultBlockState());
				c.fill(x1, y + 1, z0, x1, y + 2, z1, Blocks.GOLD_BLOCK.defaultBlockState());
				c.fill(x0 + 1, y + 1, z0, x1 - 1, y + 1, z0 + 1, Blocks.RED_CARPET.defaultBlockState());
			}
		};
		return t;
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
		c.guard(SNIPER, 4, 7, 10, Direction.NORTH, 3, GuardRole.SNIPER);
		c.alarmLamp(-5, 11, 4);
		c.alarmLamp(5, 11, 8);
		c.camera(10, 5, 11, Direction.NORTH);
		c.spawnPoint(-8, 1, 6);
		c.spawnPoint(9, 1, 0);

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
		c.alarmLamp(-3, 6, -9);
		c.alarmLamp(4, 6, -5);

		// ------------------------------------------------ v0.17.7: the back lot, then the crawl wing and its stairwell
		backLot(c, brick, stone);
		CrawlWing.build(c, THEME, AX, AY, AZ);
		CrawlWing.stairwell(c, THEME, AX, AY, AZ, YTOP);
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
		// v0.17.7: the counting room -- the core and vault moved down to the crawl wing; a stairwell opens where the
		// pedestal stood (drawn last, by build); a counting table and a money press on the back wall
		c.fill(6, -7, 11, 7, -7, 12, Blocks.RED_CARPET.defaultBlockState());
		c.fill(4, -7, 18, 8, -7, 19, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		c.fill(4, -6, 18, 8, -6, 19, Blocks.GREEN_CARPET.defaultBlockState());
		c.set(6, -7, 20, Blocks.IRON_BLOCK);
		c.set(6, -6, 20, Blocks.PISTON);
		c.set(5, -7, 20, Blocks.CHISELED_POLISHED_BLACKSTONE);
		c.set(7, -7, 20, Blocks.CHISELED_POLISHED_BLACKSTONE);
		c.alarmLamp(-1, -3, 15);
		c.alarmLamp(13, -3, 15);
		c.camera(12, -4, 10, Direction.SOUTH);
		c.spawnPoint(1, -7, 17);
		c.spawnPoint(11, -7, 17);
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
		c.guard(SHOTGUN, 10, -7, 11, Direction.NORTH, 5);
		c.guard(THUG, 2, -7, 17, Direction.EAST, 4, GuardRole.MEDIC);
	}

	/**
	 * v0.17.7 outer defences: the fenced back lot behind the warehouse -- a guard booth with Alarm Panel #1 and the
	 * Alarm Runner, a water tower with a Sniper on it, a parked truck, floodlights, a camera on the warehouse wall.
	 */
	private void backLot(HideoutCanvas c, BlockState brick, BlockState stone) {
		for (int x = -12; x <= 15; x++) {
			for (int z = 13; z <= 36; z++) {
				c.footing(x, -1, z, 12, Blocks.DIRT.defaultBlockState());
				double n = c.noise(x, z, 40);
				c.set(x, 0, z, n < 0.55 ? Blocks.GRAVEL.defaultBlockState() : n < 0.8 ? Blocks.COARSE_DIRT.defaultBlockState()
						: Blocks.SMOOTH_STONE.defaultBlockState());
				c.air(x, 1, z, x, 12, z);
			}
		}
		// chain-link fence (iron bars) with a gate in the south side
		for (int x = -12; x <= 15; x++) {
			if (Math.abs(x - 2) > 1) {
				c.fill(x, 1, 36, x, 2, 36, bars(true));
			}
		}
		for (int z = 13; z <= 36; z++) {
			c.fill(-12, 1, z, -12, 2, z, bars(false));
			c.fill(15, 1, z, 15, 2, z, bars(false));
		}
		c.fill(1, 1, 36, 1, 3, 36, Blocks.STONE_BRICK_WALL.defaultBlockState());
		c.fill(3, 1, 36, 3, 3, 36, Blocks.STONE_BRICK_WALL.defaultBlockState());
		// the guard booth (x 9..14, z 15..20): brick, a window, Alarm Panel #1 inside
		c.room(9, 0, 15, 14, 4, 20, brick);
		c.fill(10, 0, 16, 13, 0, 19, Blocks.SMOOTH_STONE.defaultBlockState());
		c.fill(9, 2, 17, 9, 2, 18, pane(false));
		c.fill(10, 2, 15, 12, 2, 15, pane(true));
		c.air(9, 1, 19, 9, 2, 19);
		c.alarmPanel(13, 1, 18, Direction.WEST);
		c.set(13, 1, 16, Blocks.DARK_OAK_PLANKS);
		c.set(13, 2, 16, Blocks.LANTERN);
		c.alarmLamp(11, 4, 17);
		c.alarmLamp(12, 5, 19);
		c.guard(THUG, 11, 1, 18, Direction.WEST, 3, GuardRole.RUNNER);
		// the water tower (x -10..-6, z 26..30): four legs, a platform at y 8 with the sniper, a tank above
		for (int[] l : new int[][] { { -10, 26 }, { -6, 26 }, { -10, 30 }, { -6, 30 } }) {
			c.fill(l[0], 1, l[1], l[0], 7, l[1], Blocks.DARK_OAK_FENCE.defaultBlockState());
		}
		c.fill(-10, 8, 26, -6, 8, 30, Blocks.SPRUCE_PLANKS.defaultBlockState());
		c.fill(-9, 11, 27, -7, 13, 29, Blocks.SPRUCE_PLANKS.defaultBlockState());
		c.set(-8, 14, 28, Blocks.SPRUCE_SLAB);
		for (int x = -10; x <= -6; x++) {
			c.set(x, 9, 26, Blocks.DARK_OAK_FENCE.defaultBlockState());
			c.set(x, 9, 30, Blocks.DARK_OAK_FENCE.defaultBlockState());
		}
		for (int y = 1; y <= 8; y++) {
			c.set(-8, y, 31, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		c.set(-8, 8, 31, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		c.fill(-8, 1, 32, -8, 8, 32, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		c.guard(SNIPER, -8, 9, 28, Direction.NORTH, 2, GuardRole.SNIPER);
		// the truck (x 1..4, z 21..28)
		c.fill(1, 1, 21, 4, 1, 28, Blocks.BLACK_CONCRETE.defaultBlockState());
		c.fill(1, 2, 21, 4, 4, 25, Blocks.WHITE_CONCRETE.defaultBlockState());
		c.fill(1, 2, 26, 4, 3, 28, Blocks.GRAY_CONCRETE.defaultBlockState());
		c.fill(2, 3, 28, 3, 3, 28, Blocks.BLACK_STAINED_GLASS.defaultBlockState());
		for (int[] w : new int[][] { { 0, 22 }, { 5, 22 }, { 0, 27 }, { 5, 27 } }) {
			c.set(w[0], 1, w[1], Blocks.BLACK_WOOL);
		}
		// crates, floodlights, spawn points, a camera on the warehouse wall
		for (int[] p : new int[][] { { -10, 15 }, { -9, 15 }, { -10, 16 }, { 7, 33 }, { 8, 33 }, { 7, 34 } }) {
			c.set(p[0], 1, p[1], c.noise(p[0], p[1], 41) < 0.5 ? Blocks.BARREL.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
		}
		c.container(-9, 2, 15, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		for (int[] p : new int[][] { { -11, 14 }, { 14, 35 }, { -11, 35 } }) {
			c.fill(p[0], 1, p[1], p[0], 4, p[1], Blocks.IRON_BARS.defaultBlockState());
			c.set(p[0], 5, p[1], Blocks.SEA_LANTERN);
		}
		c.camera(0, 4, 13, Direction.SOUTH);
		c.alarmLamp(-4, 6, 12);
		c.alarmLamp(4, 6, 12);
		c.spawnPoint(-4, 1, 20);
		c.spawnPoint(7, 1, 30);
		c.guard(THUG, -3, 1, 24, Direction.EAST, 6);
		c.guard(SHOTGUN, 8, 1, 26, Direction.WEST, 5, GuardRole.SHIELD);
	}
}
