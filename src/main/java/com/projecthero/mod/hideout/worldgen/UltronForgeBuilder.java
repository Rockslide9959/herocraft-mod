package com.projecthero.mod.hideout.worldgen;

import static com.projecthero.mod.hideout.HideoutGuards.Role.DRONE;
import static com.projecthero.mod.hideout.HideoutGuards.Role.HEAVY;
import static com.projecthero.mod.hideout.HideoutGuards.Role.LANCER;
import static com.projecthero.mod.hideout.HideoutGuards.Role.SENTINEL;

import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.hideout.HideoutEntityTypes;
import com.projecthero.mod.hideout.block.HideoutVentBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * v0.17.5: the <b>Ultron Forge</b> -- a deepslate-and-iron bunker sunk into a mountainside, where Ultron builds his
 * robots. A trench leads in to a torn-open blast door.
 * <ul>
 * <li><b>Entrance hall</b>: a laser tripwire across it wired to a sensor (alarm: drones come).</li>
 * <li><b>Assembly hall</b>: two conveyor lines of half-built robots, crane rails, red floor and ceiling lights.</li>
 * <li><b>Drone bay</b>: charging pods in the walls with Sentinel Drones resting in them, plasma vents in the floor and
 * a second tripwire at the far door.</li>
 * <li><b>Core room</b>: the Hideout Core on a dais of iron and red, the sealed Hideout Vault, server racks, the
 * Forge Overseer.</li>
 * </ul>
 * y = 0 is the bunker floor (the generator's surface height): on a slope the bunker is half buried, which is the look.
 */
public final class UltronForgeBuilder extends HideoutBuilder {
	public static final UltronForgeBuilder INSTANCE = new UltronForgeBuilder();

	// v0.17.7: the Deep Forge (crawl wing) below the control room; its stairwell opens at z 9
	static final int YTOP = 0;
	static final int AX = 4;
	static final int AY = -16;
	static final int AZ = 9 + (YTOP - AY);
	static final BlockPos CORE = CrawlWing.core(AX, AY, AZ);
	static final BlockPos VAULT = CrawlWing.vault(AX, AY, AZ);
	static final CrawlTheme THEME = theme();

	private UltronForgeBuilder() {
		super(-15, AY - 3, -28, 15, 14, AZ + CrawlWing.END_Z);
	}

	private static CrawlTheme theme() {
		CrawlTheme t = new CrawlTheme();
		t.wall = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		t.floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		t.ceiling = Blocks.DEEPSLATE_TILES.defaultBlockState();
		t.trim = Blocks.IRON_BLOCK.defaultBlockState();
		t.pillar = Blocks.DEEPSLATE_TILES.defaultBlockState();
		t.light = Blocks.SEA_LANTERN.defaultBlockState();
		t.accent = Blocks.RED_CONCRETE.defaultBlockState();
		t.stairs = Blocks.POLISHED_DEEPSLATE_STAIRS;
		t.vent = HideoutVentBlock.Kind.PLASMA;
		t.grunt = SENTINEL;
		t.elite = HEAVY;
		t.ranged = LANCER;
		t.shield = HEAVY;
		t.captain = HEAVY;
		t.runner = SENTINEL;
		t.sniper = LANCER;
		t.boss = HideoutEntityTypes.FORGE_OVERSEER;
		t.decor = new CrawlTheme.Decor() {
			@Override
			public void checkpoint(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// a scanner arch over the way through, charging pods in the east wall
				int cx = (x0 + x1) / 2;
				c.fill(cx - 2, y + 1, z1 - 1, cx - 2, y + 4, z1 - 1, Blocks.IRON_BLOCK.defaultBlockState());
				c.fill(cx + 2, y + 1, z1 - 1, cx + 2, y + 4, z1 - 1, Blocks.IRON_BLOCK.defaultBlockState());
				c.fill(cx - 1, y + 4, z1 - 1, cx + 1, y + 4, z1 - 1, Blocks.RED_STAINED_GLASS.defaultBlockState());
				for (int z = z0 + 1; z <= z0 + 5; z += 2) {
					c.set(x1, y + 1, z, Blocks.IRON_BLOCK);
					c.set(x1, y + 2, z, Blocks.RED_STAINED_GLASS);
				}
			}

			@Override
			public void quarters(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the command node: a holotable, server racks along the west wall
				int cx = x0 + 5;
				int cz = (z0 + z1) / 2;
				c.fill(cx - 1, y + 1, cz + 1, cx + 1, y + 1, cz + 2, Blocks.IRON_BLOCK.defaultBlockState());
				c.fill(cx - 1, y + 2, cz + 1, cx + 1, y + 2, cz + 2, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
				c.set(cx, y + 3, cz + 1, Blocks.END_ROD);
				for (int z = z0 + 2; z <= z1; z++) {
					for (int yy = y + 1; yy <= y + 3; yy++) {
						c.set(x0, yy, z, (z + yy) % 3 == 0 ? Blocks.RED_STAINED_GLASS.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
					}
				}
			}

			@Override
			public void arena(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the assembly pit: half-built robot frames round the walls, a crane rail overhead, a furnace core
				for (int z = z0 + 4; z <= z1 - 3; z += 4) {
					for (int x : new int[] { x0, x1 }) {
						c.fill(x, y + 1, z, x, y + 2, z, Blocks.IRON_BLOCK.defaultBlockState());
						c.set(x, y + 3, z, Blocks.RED_STAINED_GLASS);
						c.set(x, y + 4, z, Blocks.CHAIN);
					}
				}
				for (int x = x0; x <= x1; x++) {
					c.set(x, y + 9, (z0 + z1) / 2, Blocks.IRON_BLOCK);
				}
				int cx = (x0 + x1) / 2;
				c.fill(cx - 1, y, z1 - 1, cx + 1, y, z1, Blocks.MAGMA_BLOCK.defaultBlockState());
			}

			@Override
			public void vault(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				for (int z = z0; z <= z1; z++) {
					for (int yy = y + 1; yy <= y + 3; yy++) {
						c.set(x0, yy, z, (z + yy) % 2 == 0 ? Blocks.RED_STAINED_GLASS.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
						c.set(x1, yy, z, (z + yy) % 2 == 1 ? Blocks.RED_STAINED_GLASS.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
					}
				}
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

	private static BlockState bars(boolean alongX) {
		BlockState s = Blocks.IRON_BARS.defaultBlockState();
		return alongX ? s.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
				: s.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
	}

	/** A permanently lit lamp: a redstone block behind it powers it. */
	private static void lamp(HideoutCanvas c, int x, int y, int z, int bx, int by, int bz) {
		c.set(bx, by, bz, Blocks.REDSTONE_BLOCK);
		c.set(x, y, z, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
	}

	@Override
	public void build(HideoutCanvas c) {
		BlockState wall = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		BlockState tiles = Blocks.DEEPSLATE_TILES.defaultBlockState();
		BlockState floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState red = Blocks.RED_CONCRETE.defaultBlockState();
		BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();

		// ------------------------------------------------ the shell, carved into the slope
		c.air(-15, 1, -28, 15, 13, 19);
		for (int x = -14; x <= 14; x++) {
			for (int z = -18; z <= 18; z++) {
				c.footing(x, -2, z, 14, wall);
			}
		}
		c.room(-14, -1, -18, 14, 10, 18, wall);
		c.fill(-13, 0, -17, 13, 0, 17, floor);
		c.fill(-14, 10, -18, 14, 10, 18, tiles);
		// iron ribs on the outside walls
		for (int x = -14; x <= 14; x += 4) {
			c.fill(x, 0, -18, x, 9, -18, iron);
			c.fill(x, 0, 18, x, 9, 18, iron);
		}
		for (int z = -18; z <= 18; z += 4) {
			c.fill(-14, 0, z, -14, 9, z, iron);
			c.fill(14, 0, z, 14, 9, z, iron);
		}
		// roof: vents and antennae
		for (int[] p : new int[][] { { -9, -9 }, { 9, -9 }, { -9, 9 }, { 9, 9 } }) {
			c.set(p[0], 11, p[1], Blocks.IRON_BLOCK);
			c.set(p[0], 12, p[1], Blocks.LIGHTNING_ROD);
		}
		c.fill(-2, 11, 0, 2, 11, 0, Blocks.IRON_TRAPDOOR.defaultBlockState());
		// the approach trench: a ramp from the blast door up and out to the north
		for (int k = 0; k <= 9; k++) {
			int z = -19 - k;
			int y0 = Math.min(4, k / 2);
			c.fill(-2, -1, z, 2, y0, z, wall);
			c.fill(-2, y0, z, 2, y0, z, k % 3 == 0 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : floor);
			c.air(-2, y0 + 1, z, 2, y0 + 5, z);
			c.fill(-3, -1, z, -3, y0 + 2, z, wall);
			c.fill(3, -1, z, 3, y0 + 2, z, wall);
		}

		// ------------------------------------------------ the blast door (torn open) and the entrance hall
		c.air(-1, 1, -18, 1, 3, -18);
		for (int y = 1; y <= 4; y++) {
			c.set(-2, y, -18, (y & 1) == 0 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
			c.set(2, y, -18, (y & 1) == 0 ? Blocks.BLACK_CONCRETE.defaultBlockState() : Blocks.YELLOW_CONCRETE.defaultBlockState());
		}
		c.fill(-2, 4, -18, 2, 4, -18, Blocks.BLACK_CONCRETE.defaultBlockState());
		c.set(-1, 3, -18, bars(true));
		c.set(1, 1, -19, Blocks.IRON_BLOCK); // the door slab, bent outward
		c.fill(-13, 1, -17, -6, 9, -12, wall);
		c.fill(6, 1, -17, 13, 9, -12, wall);
		c.fill(-5, 1, -17, -5, 9, -11, wall);
		c.fill(5, 1, -17, 5, 9, -11, wall);
		c.fill(-13, 1, -11, 13, 9, -11, wall);
		c.air(-2, 1, -11, 2, 4, -11);
		for (int z = -16; z <= -12; z += 2) {
			lamp(c, -4, 9, z, -4, 10, z);
			lamp(c, 4, 9, z, 4, 10, z);
		}
		c.tripwireX(-3, 4, 1, -14); // the hook at x 4 hangs on the hall's east wall... (x 5)
		c.guard(SENTINEL, -2, 1, -16, Direction.NORTH, 5);
		c.guard(SENTINEL, 2, 1, -12, Direction.NORTH, 5);
		// v0.17.7 outer defences: Alarm Panel #1 by the inner door, its runner, a camera over the blast door
		c.alarmPanel(-4, 1, -13, Direction.EAST);
		c.guard(SENTINEL, -3, 1, -15, Direction.EAST, 3, GuardRole.RUNNER);
		c.camera(4, 4, -17, Direction.SOUTH);
		c.alarmLamp(0, 10, -14);
		c.spawnPoint(3, 1, -15);

		// ------------------------------------------------ the assembly hall (z -10..5)
		for (int side : new int[] { -1, 1 }) {
			int bx = side * 7;
			for (int z = -9; z <= 3; z++) {
				c.fill(bx - 1, 1, z, bx + 1, 1, z, Blocks.SMOOTH_STONE.defaultBlockState());
				c.set(bx, 2, z, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
				if ((z + 9) % 4 == 2) { // a half-built robot on the belt: frame + glowing head
					c.set(bx - 1, 2, z, Blocks.IRON_BLOCK);
					c.set(bx - 1, 3, z, Blocks.RED_STAINED_GLASS);
				}
			}
			// robot arms over the belts
			for (int z = -7; z <= 3; z += 5) {
				c.fill(bx + side * 3, 1, z, bx + side * 3, 6, z, Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
				c.fill(bx + side, 6, z, bx + side * 2, 6, z, Blocks.IRON_BLOCK.defaultBlockState());
				c.fill(bx, 4, z, bx, 6, z, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
				c.set(bx, 3, z, Blocks.GRINDSTONE);
			}
			c.set(bx + side * 4, 1, -5, Blocks.ANVIL);
			c.set(bx + side * 4, 1, 1, Blocks.SMITHING_TABLE);
		}
		// red strip lights down the aisle (lamps in the floor, powered from beneath)
		for (int z = -9; z <= 4; z += 3) {
			lamp(c, 0, 0, z, 0, -1, z);
		}
		c.fill(-1, 0, -10, -1, 0, 5, red);
		c.fill(1, 0, -10, 1, 0, 5, red);
		// crane rails across the ceiling
		for (int x = -13; x <= 13; x++) {
			c.set(x, 9, -6, iron);
			c.set(x, 9, 2, iron);
		}
		for (int x = -10; x <= 10; x += 5) {
			lamp(c, x, 9, -2, x, 10, -2);
		}
		c.container(-12, 1, 4, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		c.container(12, 1, -9, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		c.guard(DRONE, -3, 5, -6, Direction.NORTH, 8);
		c.guard(DRONE, 4, 5, 0, Direction.NORTH, 8);
		c.guard(HEAVY, 0, 1, 2, Direction.NORTH, 6);
		c.guard(LANCER, -3, 1, -3, Direction.NORTH, 6, GuardRole.SNIPER);
		c.guard(DRONE, 9, 1, -2, Direction.WEST, 5, GuardRole.MEDIC);
		c.camera(-12, 6, -10, Direction.SOUTH);
		c.alarmLamp(-10, 10, 4);
		c.alarmLamp(10, 10, -8);
		c.spawnPoint(-10, 1, -8);
		c.spawnPoint(10, 1, 0);
		c.guard(SENTINEL, 11, 1, 3, Direction.WEST, 5);

		// ------------------------------------------------ back wall (z 6) with the drone-bay door; bay / core-room wall (x -5)
		c.fill(-13, 1, 6, 13, 9, 6, wall);
		c.air(-10, 1, 6, -8, 3, 6);
		c.fill(-5, 1, 7, -5, 9, 17, wall);
		c.air(-5, 1, 15, -5, 3, 16);

		// ------------------------------------------------ the drone bay (x -13..-6, z 7..17)
		for (int z = 8; z <= 14; z += 3) {
			// a charging pod: an alcove in the west wall with a coil above and bars at its sides
			c.air(-14, 1, z, -14, 2, z);
			c.set(-14, 0, z, Blocks.IRON_BLOCK);
			c.set(-14, 3, z, Blocks.RED_STAINED_GLASS);
			c.set(-15, 1, z, Blocks.IRON_BLOCK);
			c.set(-15, 2, z, Blocks.IRON_BLOCK);
			c.set(-15, 3, z, Blocks.IRON_BLOCK);
			c.set(-15, 4, z, Blocks.IRON_BLOCK);
			c.set(-14, 4, z, Blocks.IRON_BLOCK);
			c.set(-14, 1, z - 1, wall);
			c.set(-14, 1, z + 1, wall);
		}
		c.guard(SENTINEL, -13, 1, 8, Direction.EAST, 4);
		c.guard(SENTINEL, -13, 1, 14, Direction.EAST, 4);
		c.guard(DRONE, -9, 5, 12, Direction.SOUTH, 6);
		// plasma vents across the bay floor
		for (int[] v : new int[][] { { -9, 9 }, { -7, 10 }, { -11, 11 }, { -8, 13 }, { -10, 14 }, { -7, 16 } }) {
			c.vent(v[0], 0, v[1], HideoutVentBlock.Kind.PLASMA);
		}
		for (int z = 9; z <= 15; z += 3) {
			lamp(c, -9, 9, z, -9, 10, z);
		}
		// the tripwire at the far door into the core room
		c.tripwireZ(14, 17, 1, -6);

		// ------------------------------------------------ the core room (x -4..13, z 7..17)
		c.fill(-4, 0, 7, 13, 0, 17, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
		// v0.17.7: the control room -- the dais became the stairwell down to the Deep Forge (drawn by build at the end)
		c.fill(2, 0, 13, 6, 0, 15, red);
		for (int[] p : new int[][] { { 1, 9 }, { 7, 9 }, { 1, 14 }, { 7, 14 } }) {
			c.set(p[0], 1, p[1], Blocks.IRON_BLOCK);
			c.set(p[0], 2, p[1], Blocks.END_ROD);
			c.fill(p[0], 6, p[1], p[0], 9, p[1], Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
			c.set(p[0], 5, p[1], Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		// server racks along the east wall
		for (int z = 8; z <= 16; z++) {
			for (int y = 1; y <= 3; y++) {
				c.set(13, y, z, (z + y) % 3 == 0 ? Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true) : iron);
			}
			c.set(14, 2, z, Blocks.REDSTONE_BLOCK);
			c.set(14, 1, z, Blocks.REDSTONE_BLOCK);
			c.set(14, 3, z, Blocks.REDSTONE_BLOCK);
		}
		// a reactor column against the back wall
		c.set(4, 1, 17, Blocks.IRON_BLOCK);
		c.fill(3, 1, 17, 3, 3, 17, Blocks.IRON_BLOCK.defaultBlockState());
		c.fill(5, 1, 17, 5, 3, 17, Blocks.IRON_BLOCK.defaultBlockState());
		c.set(4, 2, 17, Blocks.RED_STAINED_GLASS);
		c.set(4, 3, 17, Blocks.IRON_BLOCK);
		lamp(c, 4, 9, 12, 4, 10, 12);
		lamp(c, 10, 9, 9, 10, 10, 9);
		lamp(c, 10, 9, 15, 10, 10, 15);
		c.guard(SENTINEL, 11, 1, 10, Direction.WEST, 4);
		c.guard(HEAVY, 8, 1, 15, Direction.NORTH, 4, GuardRole.SHIELD);
		c.alarmLamp(-2, 10, 9);
		c.camera(12, 5, 7, Direction.SOUTH);

		// ------------------------------------------------ v0.17.7: the roof turret nest (sniper perch)
		c.fill(-2, 11, 6, 2, 11, 10, iron);
		for (int x = -2; x <= 2; x++) {
			c.set(x, 12, 6, bars(true));
			c.set(x, 12, 10, bars(true));
		}
		for (int z = 7; z <= 9; z++) {
			c.set(-2, 12, z, bars(false));
			c.set(2, 12, z, bars(false));
		}
		c.set(0, 12, 8, Blocks.AIR.defaultBlockState());
		c.guard(LANCER, 0, 12, 8, Direction.NORTH, 1, GuardRole.SNIPER);

		// ------------------------------------------------ v0.17.7: the Deep Forge and its stairwell
		CrawlWing.build(c, THEME, AX, AY, AZ);
		CrawlWing.stairwell(c, THEME, AX, AY, AZ, YTOP);
	}
}
