package com.projecthero.mod.hideout.worldgen;

import static com.projecthero.mod.hideout.HideoutGuards.Role.ARBALIST;
import static com.projecthero.mod.hideout.HideoutGuards.Role.KNIGHT;
import static com.projecthero.mod.hideout.HideoutGuards.Role.OATHSWORN;
import static com.projecthero.mod.hideout.HideoutGuards.Role.SQUIRE;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.candles;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.chainLantern;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.ladder;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.log;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.runX;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.runZ;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.slab;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.stair;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.weathered;

import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.alarm.block.AlarmStyle;
import com.projecthero.mod.hideout.HideoutEntityTypes;
import com.projecthero.mod.hideout.block.HideoutBlocks;
import com.projecthero.mod.hideout.block.HideoutVentBlock;
import com.projecthero.mod.hideout.block.OathBallistaBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: the <b>Oathbreaker Keep</b> -- a ruined fortress cut into a mountainside, still held by the knights who
 * swore themselves to the Oathbreaker.
 * <ul>
 * <li><b>The curtain wall</b>: weathered stone with crenellated wall-walks (ladders up from the courtyard), four corner
 * towers and a gatehouse with a raised portcullis; a stretch of the north wall has collapsed into rubble. <b>Oath
 * Ballistae</b> on the towers, the gatehouse, the keep roof and the south wall -- manned only once the keep is on its
 * guard (alarm Suspicious / Alert). An Arbalist sniper on the north-east tower.</li>
 * <li><b>The courtyard</b>: a training yard of straw dummies, barracks lean-tos, a well and a broken oath shrine between
 * soul-fire braziers.</li>
 * <li><b>The keep</b>: a great hall (banners, chandeliers, the long table, the champion's empty throne; Alarm Panel #1 --
 * a rune ward stone -- with its runner), an armoury above, ballista and banner on the roof. The stair down opens in the
 * hall floor.</li>
 * <li><b>The undercroft</b> (the crawl wing, ancient style): checkpoint, the war-horn gauntlet, the marshal's quarters
 * (Captain + keycard), the hall of oaths where the <b>Oathbound Champion</b> waits, the sealed vault.</li>
 * </ul>
 * y = 0 is the courtyard floor (the generator surface at the centre); walls and floors stand on footings.
 */
public final class OathbreakerKeepBuilder extends HideoutBuilder {
	public static final OathbreakerKeepBuilder INSTANCE = new OathbreakerKeepBuilder();

	static final int AX = 0;
	static final int AY = -14;
	static final int AZ = 24;
	static final BlockPos CORE = CrawlWing.core(AX, AY, AZ);
	static final BlockPos VAULT = CrawlWing.vault(AX, AY, AZ);
	static final CrawlTheme THEME = theme();
	static final int WALL_TOP = 8;

	private static CrawlTheme theme() {
		CrawlTheme t = new CrawlTheme();
		t.wall = Blocks.STONE_BRICKS.defaultBlockState();
		t.floor = Blocks.DEEPSLATE_TILES.defaultBlockState();
		t.ceiling = t.wall;
		t.trim = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		t.pillar = Blocks.POLISHED_BASALT.defaultBlockState();
		t.light = Blocks.SEA_LANTERN.defaultBlockState();
		t.accent = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
		t.stairs = Blocks.STONE_BRICK_STAIRS;
		t.vent = HideoutVentBlock.Kind.FIRE;
		t.tech = false;
		t.style = AlarmStyle.ANCIENT;
		t.grunt = KNIGHT;
		t.elite = OATHSWORN;
		t.ranged = ARBALIST;
		t.shield = KNIGHT;
		t.captain = KNIGHT;
		t.runner = SQUIRE;
		t.sniper = ARBALIST;
		t.boss = HideoutEntityTypes.OATHBOUND_CHAMPION;
		t.decor = new CrawlTheme.Decor() {
			/** Cracks and moss through a room's walls. */
			private void weather(HideoutCanvas c, int x0, int y, int z0, int x1, int z1, int h, int salt) {
				for (int x = x0 - 1; x <= x1 + 1; x++) {
					for (int z = z0 - 1; z <= z1 + 1; z++) {
						if (x != x0 - 1 && x != x1 + 1 && z != z0 - 1 && z != z1 + 1) {
							continue;
						}
						for (int yy = y + 1; yy <= y + h; yy++) {
							if (c.get(x, yy, z).is(Blocks.STONE_BRICKS)) {
								c.set(x, yy, z, weathered(c, x, yy, z, salt));
							}
						}
					}
				}
			}

			@Override
			public void checkpoint(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				weather(c, x0, y, z0, x1, z1, 5, 90);
				banner(c, x0 - 1, y + 2, z0 + 2, Direction.EAST);
				banner(c, x1 + 1, y + 2, z1 - 2, Direction.WEST);
				rack(c, x1, y + 1, z0 + 1);
			}

			@Override
			public void quarters(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the marshal's quarters: a map table, a weapon rack, a cot, chained lanterns
				weather(c, x0, y, z0, x1 + 4, z1, 6, 91);
				int cx = x0 + 4;
				c.fill(cx - 1, y + 1, z0 + 4, cx + 1, y + 1, z0 + 4, Blocks.SPRUCE_PLANKS.defaultBlockState());
				c.set(cx, y + 2, z0 + 4, Blocks.CARTOGRAPHY_TABLE);
				candles(c, cx - 1, y + 2, z0 + 4, Blocks.WHITE_CANDLE, 2, true);
				rack(c, x0, y + 1, z0 + 1);
				rack(c, x0, y + 1, z0 + 3);
				c.fill(x0, y + 1, z1 - 1, x0 + 1, y + 1, z1 - 1, Blocks.CYAN_WOOL.defaultBlockState());
				banner(c, x0 - 1, y + 3, z1 - 3, Direction.EAST);
			}

			@Override
			public void arena(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the hall of oaths: oath tablets round the walls, soul-fire braziers, broken statues of knights
				weather(c, x0, y, z0, x1, z1, 8, 92);
				int cx = (x0 + x1) / 2;
				int cz = (z0 + z1) / 2;
				for (int z = z0 + 2; z <= z1 - 2; z += 4) {
					for (int x : new int[] { x0 - 1, x1 + 1 }) {
						c.set(x, y + 3, z, Blocks.CHISELED_STONE_BRICKS);
						c.set(x, y + 4, z, Blocks.CHISELED_STONE_BRICKS);
					}
				}
				for (int[] p : new int[][] { { -8, -8 }, { 8, -8 }, { -8, 8 }, { 8, 8 } }) {
					brazier(c, cx + p[0], y + 1, cz + p[1]);
				}
				for (int[] p : new int[][] { { -3, 9 }, { 3, 9 } }) {
					statue(c, cx + p[0], y + 1, cz + p[1], c.noise(p[0], p[1], 93) < 0.5);
				}
				for (int[] p : new int[][] { { -4, -4 }, { 4, 4 } }) {
					chainLantern(c, cx + p[0], y + 11, cz + p[1], 3, true);
				}
			}

			@Override
			public void vault(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				for (int z = z0; z <= z1; z += 3) {
					c.fill(x0, y + 1, z, x0, y + 3, z, Blocks.CHAIN.defaultBlockState());
					c.fill(x1, y + 1, z, x1, y + 3, z, Blocks.CHAIN.defaultBlockState());
				}
				banner(c, x0 - 1, y + 2, z1 - 1, Direction.EAST);
				banner(c, x1 + 1, y + 2, z1 - 1, Direction.WEST);
			}
		};
		return t;
	}

	private OathbreakerKeepBuilder() {
		super(-20, AY - 3, -16, 20, 19, AZ + CrawlWing.END_Z);
	}

	@Override
	public BlockPos coreLocal() {
		return CORE;
	}

	@Override
	public BlockPos vaultLocal() {
		return VAULT;
	}

	// ---------------------------------------------------------------- dressing

	/** A two-colour oath banner (cyan over black wool) hung flat on a wall. */
	static void banner(HideoutCanvas c, int x, int y, int z, Direction out) {
		c.set(x, y + 2, z, Blocks.CYAN_WOOL);
		c.set(x, y + 1, z, Blocks.CYAN_WOOL);
		c.set(x, y, z, Blocks.BLACK_WOOL);
		c.set(x, y + 3, z, Blocks.SPRUCE_FENCE.defaultBlockState());
	}

	/** A weapon rack: a spruce frame with a grindstone. */
	static void rack(HideoutCanvas c, int x, int y, int z) {
		c.set(x, y, z, Blocks.SPRUCE_PLANKS);
		c.set(x, y + 1, z, Blocks.GRINDSTONE);
	}

	/** A soul-fire brazier on a short stone stand. */
	static void brazier(HideoutCanvas c, int x, int y, int z) {
		c.set(x, y, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
		c.set(x, y + 1, z, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
	}

	/** A knight statue (broken: no head). */
	static void statue(HideoutCanvas c, int x, int y, int z, boolean broken) {
		c.set(x, y, z, Blocks.CHISELED_STONE_BRICKS);
		c.set(x, y + 1, z, Blocks.STONE_BRICK_WALL);
		c.set(x, y + 2, z, Blocks.STONE_BRICKS);
		if (!broken) {
			c.set(x, y + 3, z, Blocks.POLISHED_ANDESITE);
		} else {
			c.set(x + 1, y, z, Blocks.COBBLESTONE);
		}
	}

	static void ballista(HideoutCanvas c, int x, int y, int z, Direction facing) {
		c.set(x, y, z, HideoutBlocks.BALLISTA.defaultBlockState().setValue(OathBallistaBlock.FACING, facing));
	}

	private static BlockState courtyard(HideoutCanvas c, int x, int z) {
		double n = c.noise(x, z, 100);
		if (n < 0.35) {
			return Blocks.COBBLESTONE.defaultBlockState();
		}
		if (n < 0.55) {
			return Blocks.STONE.defaultBlockState();
		}
		if (n < 0.7) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		if (n < 0.85) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
	}

	// ---------------------------------------------------------------- build

	@Override
	public void build(HideoutCanvas c) {
		ground(c);
		walls(c);
		towers(c);
		gatehouse(c);
		yard(c);
		keep(c);
		CrawlWing.build(c, THEME, AX, AY, AZ);
		CrawlWing.stairwell(c, THEME, AX, AY, AZ, 0);
	}

	private void ground(HideoutCanvas c) {
		for (int x = -20; x <= 20; x++) {
			for (int z = -16; z <= 23; z++) {
				c.air(x, 1, z, x, 19, z);
				c.footing(x, -1, z, 24, Blocks.STONE.defaultBlockState());
				boolean inside = x > -15 && x < 15 && z > -11 && z < 19;
				c.set(x, 0, z, inside ? courtyard(c, x, z) : (c.noise(x, z, 101) < 0.5 ? Blocks.STONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState()));
			}
		}
		// the path from the gate to the keep door
		for (int z = -16; z <= 8; z++) {
			for (int x = -1; x <= 1; x++) {
				c.set(x, 0, z, c.noise(x, z, 102) < 0.7 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
			}
		}
	}

	/** A wall section along local x (z0..z0+1 thick) or z; crenellations on the outer row. */
	private void wallX(HideoutCanvas c, int x0, int x1, int zOuter, int zInner) {
		for (int x = x0; x <= x1; x++) {
			int top = WALL_TOP;
			// the collapsed stretch of the north wall
			if (zOuter < 0 && x >= -12 && x <= -7) {
				top = 2 + (int) (c.noise(x, 0, 103) * 4);
			}
			for (int y = 1; y <= top; y++) {
				c.set(x, y, zOuter, y <= 2 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : weathered(c, x, y, zOuter, 104));
				c.set(x, y, zInner, y <= 2 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : weathered(c, x, y, zInner, 105));
			}
			if (top == WALL_TOP && x % 2 == 0) {
				c.set(x, WALL_TOP + 1, zOuter, weathered(c, x, WALL_TOP + 1, zOuter, 106));
			}
			if (top < WALL_TOP) {
				// rubble spilled outside
				int out = zOuter + (zOuter < zInner ? -1 : 1);
				c.set(x, 1, out, c.noise(x, out, 107) < 0.5 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
				if (c.noise(x, out, 108) < 0.4) {
					c.set(x, 2, out, Blocks.COBBLESTONE_SLAB.defaultBlockState());
				}
			}
		}
	}

	private void wallZ(HideoutCanvas c, int z0, int z1, int xOuter, int xInner) {
		for (int z = z0; z <= z1; z++) {
			for (int y = 1; y <= WALL_TOP; y++) {
				c.set(xOuter, y, z, y <= 2 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : weathered(c, xOuter, y, z, 109));
				c.set(xInner, y, z, y <= 2 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : weathered(c, xInner, y, z, 110));
			}
			if (z % 2 == 0) {
				c.set(xOuter, WALL_TOP + 1, z, weathered(c, xOuter, WALL_TOP + 1, z, 111));
			}
		}
	}

	private void walls(HideoutCanvas c) {
		wallX(c, -15, 15, -12, -11);
		wallX(c, -15, 15, 20, 19);
		wallZ(c, -11, 19, -16, -15);
		wallZ(c, -11, 19, 16, 15);
		// arrow slits
		for (int x = -12; x <= 12; x += 4) {
			c.set(x, 5, 20, Blocks.AIR.defaultBlockState());
		}
		for (int z = -6; z <= 16; z += 4) {
			c.set(-16, 5, z, Blocks.AIR.defaultBlockState());
			c.set(16, 5, z, Blocks.AIR.defaultBlockState());
		}
		// ladders up to the wall-walks
		ladder(c, -14, 1, WALL_TOP, 10, Direction.WEST);
		ladder(c, 14, 1, WALL_TOP, -2, Direction.EAST);
		ladder(c, 9, 1, WALL_TOP, -10, Direction.NORTH);
		ladder(c, -9, 1, WALL_TOP, 18, Direction.SOUTH);
		// a ballista on the south wall-walk covering the courtyard, wall braziers (alarm lamps)
		ballista(c, 8, WALL_TOP + 1, 19, Direction.NORTH);
		CrawlWing.lampAt(c, THEME, -15, WALL_TOP + 1, 8);
		CrawlWing.lampAt(c, THEME, 15, WALL_TOP + 1, 8);
		c.guard(ARBALIST, -15, WALL_TOP + 1, 0, Direction.EAST, 3);
		c.guard(KNIGHT, 3, WALL_TOP + 1, 19, Direction.NORTH, 4);
	}

	private void towers(HideoutCanvas c) {
		int[][] towers = { { -17, -13 }, { 17, -13 }, { -17, 21 }, { 17, 21 } };
		Direction[] aims = { Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH };
		for (int i = 0; i < 4; i++) {
			int cx = towers[i][0];
			int cz = towers[i][1];
			int top = 13;
			for (int x = cx - 2; x <= cx + 2; x++) {
				for (int z = cz - 2; z <= cz + 2; z++) {
					boolean edge = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
					for (int y = 1; y <= top; y++) {
						boolean hollow = !edge && y >= WALL_TOP + 1 && y < top;
						c.set(x, y, z, hollow ? Blocks.AIR.defaultBlockState() : y <= 3 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : weathered(c, x, y, z, 112 + i));
					}
					if (edge && (x + z) % 2 == 0) {
						c.set(x, top + 1, z, weathered(c, x, top + 1, z, 116));
					}
				}
			}
			// a door from the wall-walk (on the side facing the keep's centre) and a ladder up to the roof
			int dx = cx < 0 ? cx + 2 : cx - 2;
			int dz = cz < 0 ? cz + 1 : cz - 1;
			c.air(dx, WALL_TOP + 1, dz, dx, WALL_TOP + 2, dz);
			int lx = cx < 0 ? cx - 1 : cx + 1;
			int lz = cz < 0 ? cz - 1 : cz + 1;
			ladder(c, lx, WALL_TOP + 1, top, lz, cz < 0 ? Direction.NORTH : Direction.SOUTH);
			// arrow slits
			c.set(cx, 6, cz < 0 ? cz - 2 : cz + 2, Blocks.AIR.defaultBlockState());
			c.set(cx < 0 ? cx - 2 : cx + 2, 10, cz, Blocks.AIR.defaultBlockState());
			ballista(c, cx, top + 1, cz, aims[i]);
		}
		// the Arbalist sniper on the north-east tower
		c.guard(ARBALIST, 17, 14, -14, Direction.WEST, 1, GuardRole.SNIPER);
	}

	private void gatehouse(HideoutCanvas c) {
		int x0 = -4;
		int x1 = 4;
		int z0 = -15;
		int z1 = -9;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				for (int y = 1; y <= 11; y++) {
					boolean room = !edge && y >= 6 && y <= 10;
					c.set(x, y, z, room ? Blocks.AIR.defaultBlockState()
							: y <= 3 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : weathered(c, x, y, z, 120));
				}
				if (edge && (x + z) % 2 == 0) {
					c.set(x, 12, z, weathered(c, x, 12, z, 121));
				}
			}
		}
		// the gate passage, its arch and the raised portcullis
		c.air(-1, 1, z0, 1, 4, z1);
		for (int x = -1; x <= 1; x++) {
			c.set(x, 4, -13, Blocks.IRON_BARS.defaultBlockState());
		}
		runX(c, -1, 1, 4, -13, Blocks.IRON_BARS);
		c.set(-1, 4, z0, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
		c.set(1, 4, z0, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
		// soul-fire braziers either side of the gate (outside), the gate lamps
		brazier(c, -3, 0, z0 - 1);
		brazier(c, 3, 0, z0 - 1);
		CrawlWing.lampAt(c, THEME, -2, 4, z1);
		CrawlWing.lampAt(c, THEME, 2, 4, z1);
		// the gate room above: doors to the wall-walk, arrow slits, the windlass
		c.air(x0, WALL_TOP + 1, -11, x0, WALL_TOP + 2, -11);
		c.air(x1, WALL_TOP + 1, -11, x1, WALL_TOP + 2, -11);
		for (int x = -2; x <= 2; x += 2) {
			c.set(x, 8, z0, Blocks.AIR.defaultBlockState());
		}
		c.set(0, 6, -11, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
		c.set(-1, 6, -11, Blocks.CHAIN);
		ballista(c, 0, 12, -14, Direction.NORTH);
		c.guard(KNIGHT, 2, 6, -12, Direction.NORTH, 3);
		c.guard(KNIGHT, -2, 1, -8, Direction.NORTH, 4, GuardRole.SHIELD);
	}

	private void yard(HideoutCanvas c) {
		// the training yard: a fenced ring of straw dummies
		runX(c, 6, 12, 1, -8, Blocks.SPRUCE_FENCE);
		runX(c, 6, 12, 1, -1, Blocks.SPRUCE_FENCE);
		runZ(c, -8, -1, 1, 6, Blocks.SPRUCE_FENCE);
		runZ(c, -8, -1, 1, 12, Blocks.SPRUCE_FENCE);
		c.set(6, 1, -4, Blocks.AIR.defaultBlockState());
		for (int[] p : new int[][] { { 8, -6 }, { 10, -6 }, { 9, -3 } }) {
			c.set(p[0], 1, p[1], Blocks.SPRUCE_FENCE);
			c.set(p[0], 2, p[1], Blocks.HAY_BLOCK);
			c.set(p[0], 3, p[1], Blocks.CARVED_PUMPKIN.defaultBlockState()
					.setValue(net.minecraft.world.level.block.CarvedPumpkinBlock.FACING, Direction.WEST));
		}
		c.set(11, 1, -2, Blocks.HAY_BLOCK);
		c.set(11, 2, -2, Blocks.HAY_BLOCK);
		// barracks lean-to along the west wall
		for (int z = -6; z <= 6; z++) {
			for (int x = -14; x <= -10; x++) {
				int y = 5 - (x + 14) / 2;
				c.set(x, y, z, stair(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
			}
			if (z % 4 == -2 || z % 4 == 2) {
				c.fill(-10, 1, z, -10, 2, z, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
			}
		}
		for (int z = -5; z <= 5; z += 2) {
			c.set(-14, 1, z, z % 4 == 1 ? Blocks.CYAN_CARPET.defaultBlockState() : Blocks.BLACK_CARPET.defaultBlockState());
			c.set(-13, 1, z, z % 4 == 1 ? Blocks.CYAN_CARPET.defaultBlockState() : Blocks.BLACK_CARPET.defaultBlockState());
		}
		c.container(-14, 1, 6, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		c.set(-14, 1, -6, Blocks.BARREL);
		// the oath shrine: a broken knight statue between soul-fire braziers
		statue(c, -6, 1, 4, true);
		brazier(c, -8, 1, 4);
		brazier(c, -4, 1, 4);
		c.set(-6, 0, 3, Blocks.CHISELED_STONE_BRICKS);
		// the well
		for (int[] p : new int[][] { { 7, 9 }, { 9, 9 }, { 7, 11 }, { 9, 11 } }) {
			c.fill(p[0], 1, p[1], p[0], 3, p[1], Blocks.COBBLESTONE_WALL.defaultBlockState());
		}
		c.fill(7, 4, 9, 9, 4, 11, slab(Blocks.SPRUCE_SLAB, false));
		c.fill(7, 1, 10, 9, 1, 10, Blocks.COBBLESTONE_WALL.defaultBlockState());
		c.set(8, 1, 9, Blocks.COBBLESTONE_WALL);
		c.set(8, 1, 11, Blocks.COBBLESTONE_WALL);
		c.set(8, 1, 10, Blocks.AIR.defaultBlockState());
		c.fill(8, -3, 10, 8, -1, 10, Blocks.WATER.defaultBlockState());
		c.set(8, 0, 10, Blocks.AIR.defaultBlockState());
		// rubble and supply crates
		for (int[] p : new int[][] { { -10, -8 }, { 11, 15 }, { -12, 15 } }) {
			c.set(p[0], 1, p[1], Blocks.COBBLESTONE);
			c.set(p[0] + 1, 1, p[1], slab(Blocks.COBBLESTONE_SLAB, false));
		}
		c.fill(12, 1, 3, 13, 1, 4, Blocks.BARREL.defaultBlockState());
		c.set(12, 2, 3, Blocks.BARREL);
		// courtyard guards
		c.guard(KNIGHT, 9, 1, -5, Direction.WEST, 5);
		c.guard(SQUIRE, 10, 1, -3, Direction.WEST, 5);
		c.guard(OATHSWORN, -6, 1, 6, Direction.NORTH, 5);
		c.guard(SQUIRE, -12, 1, 0, Direction.EAST, 4);
		c.guard(KNIGHT, 5, 1, 12, Direction.NORTH, 5);
		c.spawnPoint(-8, 1, -6);
		c.spawnPoint(10, 1, 6);
		c.spawnPoint(0, 1, -6);
	}

	private void keep(HideoutCanvas c) {
		int x0 = -7;
		int x1 = 7;
		int z0 = 8;
		int z1 = 18;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				c.set(x, 0, z, edge ? Blocks.DEEPSLATE_BRICKS.defaultBlockState()
						: (x + z) % 2 == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
				for (int y = 1; y <= 13; y++) {
					if (edge) {
						c.set(x, y, z, y <= 2 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : weathered(c, x, y, z, 130));
					} else if (y == 7 || y == 13) {
						c.set(x, y, z, y == 7 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
					}
				}
				if (edge && (x + z) % 2 == 0) {
					c.set(x, 14, z, weathered(c, x, 14, z, 131));
				}
			}
		}
		// the door: an arch onto the courtyard
		c.air(-1, 1, z0, 1, 4, z0);
		c.set(-1, 4, z0, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
		c.set(1, 4, z0, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
		// arrow slits / windows on both floors
		for (int z = 10; z <= 16; z += 3) {
			for (int x : new int[] { x0, x1 }) {
				c.set(x, 3, z, Blocks.AIR.defaultBlockState());
				c.set(x, 4, z, Blocks.AIR.defaultBlockState());
				c.set(x, 10, z, Blocks.AIR.defaultBlockState());
			}
		}
		// ---- the great hall
		for (int z = 10; z <= 16; z++) {
			c.set(-5, 1, z, Blocks.SPRUCE_PLANKS);
			c.set(-5, 2, z, z % 2 == 0 ? Blocks.CYAN_CARPET.defaultBlockState() : Blocks.BLACK_CARPET.defaultBlockState());
			c.set(-6, 1, z, stair(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
			c.set(-4, 1, z, stair(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
		}
		candles(c, -5, 3, 11, Blocks.WHITE_CANDLE, 3, true);
		candles(c, -5, 3, 15, Blocks.WHITE_CANDLE, 2, true);
		// the champion's empty throne
		c.set(0, 1, 17, Blocks.POLISHED_BLACKSTONE_BRICKS);
		c.set(0, 2, 17, stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.SOUTH, false));
		c.set(-1, 2, 17, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
		c.set(1, 2, 17, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
		c.fill(-1, 3, z1, 1, 5, z1, Blocks.CYAN_WOOL.defaultBlockState());
		c.set(0, 4, z1, Blocks.SOUL_LANTERN);
		for (int z = 10; z <= 16; z += 6) {
			banner(c, x1 - 1, 2, z, Direction.WEST);
		}
		chainLantern(c, 0, 7, 15, 2, true);
		chainLantern(c, -4, 7, 12, 1, true);
		// stairs up the east wall to the armoury (hole in the floor above them)
		for (int k = 0; k <= 6; k++) {
			c.set(5, 1 + k, 9 + k, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
			c.set(6, 1 + k, 9 + k, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
			if (k < 6) {
				c.air(5, 7, 9 + k, 6, 7, 9 + k);
			}
		}
		runZ(c, 9, 14, 8, 4, Blocks.SPRUCE_FENCE);
		// Alarm Panel #1 (a rune ward stone) by the door, its runner, a medic, the hall's knights
		CrawlWing.panel(c, THEME, -6, 1, 9, Direction.EAST);
		CrawlWing.lampAt(c, THEME, -3, 6, 9);
		CrawlWing.lampAt(c, THEME, 3, 6, 17);
		c.guard(SQUIRE, -3, 1, 10, Direction.WEST, 4, GuardRole.RUNNER);
		c.guard(SQUIRE, 3, 1, 15, Direction.NORTH, 4, GuardRole.MEDIC);
		c.guard(KNIGHT, -2, 1, 16, Direction.NORTH, 4);
		c.spawnPoint(4, 1, 10);
		c.spawnPoint(-3, 1, 16);
		// ---- the armoury
		for (int x = -5; x <= -2; x++) {
			rack(c, x, 8, 17);
		}
		c.set(-6, 8, 13, Blocks.ANVIL);
		c.set(-6, 8, 11, Blocks.SMITHING_TABLE);
		c.set(-6, 8, 10, Blocks.BLAST_FURNACE);
		c.container(0, 8, 17, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		c.set(1, 8, 17, Blocks.BARREL);
		banner(c, x0 + 1, 8, 15, Direction.EAST);
		chainLantern(c, 0, 13, 12, 1, true);
		c.guard(KNIGHT, -1, 8, 12, Direction.NORTH, 3);
		// ---- the roof: ladder up, a ballista, the oath banner on its pole
		ladder(c, -6, 8, 13, 17, Direction.SOUTH);
		ballista(c, 0, 14, 9, Direction.NORTH);
		c.fill(3, 14, 15, 3, 18, 15, Blocks.SPRUCE_FENCE.defaultBlockState());
		c.fill(4, 16, 15, 5, 18, 15, Blocks.CYAN_WOOL.defaultBlockState());
		c.set(4, 15, 15, Blocks.BLACK_WOOL);
		c.set(5, 15, 15, Blocks.BLACK_WOOL);
	}
}
