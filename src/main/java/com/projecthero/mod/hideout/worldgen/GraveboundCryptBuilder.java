package com.projecthero.mod.hideout.worldgen;

import static com.projecthero.mod.hideout.HideoutGuards.Role.ACID;
import static com.projecthero.mod.hideout.HideoutGuards.Role.BONE_BLADE;
import static com.projecthero.mod.hideout.HideoutGuards.Role.CURSED;
import static com.projecthero.mod.hideout.HideoutGuards.Role.JUGGERNAUT;
import static com.projecthero.mod.hideout.HideoutGuards.Role.RISEN;
import static com.projecthero.mod.hideout.HideoutGuards.Role.RISEN_ARMOURED;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.candles;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.chainLantern;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.ladder;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.lantern;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.log;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.runX;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.runZ;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.skull;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.slab;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.stair;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.weathered;

import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.alarm.block.AlarmStyle;
import com.projecthero.mod.hideout.HideoutEntityTypes;
import com.projecthero.mod.hideout.block.HideoutVentBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;

/**
 * v0.17.7: the <b>Gravebound Crypt</b> -- a ruined graveyard chapel in a dark forest or swamp, over catacombs that run
 * deep under it. The Gravebound Curse's own dead guard it.
 * <ul>
 * <li><b>The graveyard</b> (surface): a fenced yard of crooked headstones, open graves and dead trees behind a lych-gate;
 * a cursed headstone on a tripwire across the path (the Crypt Curse + the dead claw up), a bell tower with a Bone Blade
 * sniper on its platform.</li>
 * <li><b>The chapel</b>: a roofless-in-places nave of mossy stone, pews, a candle-lit altar; Alarm Panel #1 (a carved
 * ward stone) by the altar with its runner; the stair down opens in the aisle.</li>
 * <li><b>The ossuary</b> (9 down): a bone-niched entry hall, then a vaulted burial chamber of sarcophagi and soul-fire
 * curse vents, a curse tripwire across its doorway, side tombs (one holds a Cursed Zombie -- its touch is the real
 * Gravebound Curse), and the stair down to ...</li>
 * <li><b>the Lord's Barrow</b> (the crawl wing, ancient style): checkpoint, a curse-trapped gauntlet, the embalmer's
 * quarters (Captain + keycard), the tomb arena of the <b>Crypt Lord</b>, the sealed vault.</li>
 * </ul>
 * y = 0 is the graveyard surface.
 */
public final class GraveboundCryptBuilder extends HideoutBuilder {
	public static final GraveboundCryptBuilder INSTANCE = new GraveboundCryptBuilder();

	static final int OSS = -9;
	static final int AX = 0;
	static final int AY = -17;
	static final int AZ = 46;
	static final BlockPos CORE = CrawlWing.core(AX, AY, AZ);
	static final BlockPos VAULT = CrawlWing.vault(AX, AY, AZ);
	static final CrawlTheme THEME = theme();

	private static CrawlTheme theme() {
		CrawlTheme t = new CrawlTheme();
		t.wall = Blocks.DEEPSLATE_TILES.defaultBlockState();
		t.floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		t.ceiling = t.wall;
		t.trim = Blocks.BONE_BLOCK.defaultBlockState();
		t.pillar = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		t.light = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
		t.accent = Blocks.CRYING_OBSIDIAN.defaultBlockState();
		t.stairs = Blocks.DEEPSLATE_TILE_STAIRS;
		t.vent = HideoutVentBlock.Kind.CURSE;
		t.tech = false;
		t.style = AlarmStyle.ANCIENT;
		t.grunt = RISEN;
		t.elite = JUGGERNAUT;
		t.ranged = BONE_BLADE;
		t.shield = RISEN_ARMOURED;
		t.captain = RISEN_ARMOURED;
		t.runner = RISEN;
		t.sniper = BONE_BLADE;
		t.boss = HideoutEntityTypes.CRYPT_LORD;
		t.decor = new CrawlTheme.Decor() {
			/** Burial niches with skulls in the walls round a room. */
			private void niches(HideoutCanvas c, int x0, int y, int z0, int x1, int z1, int salt) {
				for (int z = z0 + 1; z < z1; z += 2) {
					for (int x : new int[] { x0 - 1, x1 + 1 }) {
						if (c.noise(x, z, salt) < 0.7) {
							c.set(x, y + 2, z, Blocks.AIR.defaultBlockState());
							c.set(x, y + 1, z, Blocks.BONE_BLOCK.defaultBlockState());
							skull(c, x, y + 2, z, x < x0 ? 12 : 4);
						}
					}
				}
			}

			@Override
			public void checkpoint(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				niches(c, x0, y, z0, x1, z1, 70);
				candles(c, x0, y + 1, z0, Blocks.PURPLE_CANDLE, 3, true);
				candles(c, x1, y + 1, z1, Blocks.PURPLE_CANDLE, 2, true);
				c.fill(x1, y + 1, z0, x1, y + 1, z0 + 1, Blocks.BONE_BLOCK.defaultBlockState());
				c.set(x1, y + 2, z0, Blocks.COBWEB);
			}

			@Override
			public void quarters(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the embalmer's room: a stone slab table, shelves of jars (bookshelves + candles), two sarcophagi
				int cx = x0 + 4;
				c.fill(cx - 1, y + 1, z0 + 4, cx + 1, y + 1, z0 + 5, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
				c.fill(cx - 1, y + 2, z0 + 4, cx + 1, y + 2, z0 + 5, slab(Blocks.SMOOTH_STONE_SLAB, false));
				candles(c, cx, y + 3, z0 + 4, Blocks.WHITE_CANDLE, 3, true);
				for (int z = z0 + 1; z <= z0 + 3; z++) {
					c.set(x0, y + 1, z, Blocks.BOOKSHELF);
					c.set(x0, y + 2, z, Blocks.BOOKSHELF);
				}
				candles(c, x0, y + 3, z0 + 2, Blocks.PURPLE_CANDLE, 2, true);
				for (int z : new int[] { z1 - 2, z1 }) {
					c.fill(x0, y + 1, z, x0 + 1, y + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
					c.fill(x0, y + 2, z, x0 + 1, y + 2, z, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, false));
				}
				c.set(x1, y + 1, z1, Blocks.COBWEB);
			}

			@Override
			public void arena(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the Lord's tomb: a ring of sarcophagi, chained soul lanterns, crying obsidian graves, bone heaps
				int cx = (x0 + x1) / 2;
				int cz = (z0 + z1) / 2;
				for (int i = 0; i < 6; i++) {
					double a = i * Math.PI / 3 + Math.PI / 6;
					int x = cx + (int) Math.round(Math.cos(a) * 8);
					int z = cz + (int) Math.round(Math.sin(a) * 7);
					c.set(x, y + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
					c.set(x, y + 2, z, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, false));
					candles(c, x, y + 3, z, Blocks.PURPLE_CANDLE, 2, true);
				}
				for (int[] p : new int[][] { { -5, -6 }, { 5, -6 }, { -5, 5 }, { 5, 5 } }) {
					chainLantern(c, cx + p[0], y + 11, cz + p[1], 3, true);
				}
				for (int[] p : new int[][] { { x0, z0 + 2 }, { x1, z1 - 5 }, { x0 + 1, z1 }, { x1, z0 + 5 } }) {
					c.fill(p[0], y + 1, p[1], p[0], y + 2, p[1], Blocks.BONE_BLOCK.defaultBlockState());
					skull(c, p[0], y + 3, p[1], (int) (c.noise(p[0], p[1], 71) * 16));
				}
				c.set(cx - 2, y, cz + 6, Blocks.CRYING_OBSIDIAN);
				c.set(cx + 2, y, cz + 6, Blocks.CRYING_OBSIDIAN);
				c.set(cx, y, cz - 6, Blocks.SOUL_SOIL);
			}

			@Override
			public void vault(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				for (int z = z0; z <= z1; z += 2) {
					candles(c, x0, y + 1, z, Blocks.PURPLE_CANDLE, 1 + (z % 3), true);
					candles(c, x1, y + 1, z, Blocks.PURPLE_CANDLE, 1 + ((z + 1) % 3), true);
				}
				skull(c, x0, y + 1, z1, 6);
				skull(c, x1, y + 1, z1, 10);
			}
		};
		return t;
	}

	private GraveboundCryptBuilder() {
		super(-18, AY - 3, -17, 18, 15, AZ + CrawlWing.END_Z);
	}

	@Override
	public BlockPos coreLocal() {
		return CORE;
	}

	@Override
	public BlockPos vaultLocal() {
		return VAULT;
	}

	private static BlockState ground(HideoutCanvas c, int x, int z) {
		double n = c.noise(x, z, 1);
		if (n < 0.3) {
			return Blocks.PODZOL.defaultBlockState();
		}
		if (n < 0.55) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (n < 0.65) {
			return Blocks.ROOTED_DIRT.defaultBlockState();
		}
		if (n < 0.72) {
			return Blocks.MUD.defaultBlockState();
		}
		return Blocks.GRASS_BLOCK.defaultBlockState();
	}

	private static boolean inChapel(int x, int z) {
		return x >= -7 && x <= 7 && z >= 3 && z <= 19;
	}

	private static boolean inTower(int x, int z) {
		return x >= -13 && x <= -8 && z >= 5 && z <= 10;
	}

	@Override
	public void build(HideoutCanvas c) {
		yard(c);
		graves(c);
		deadTrees(c);
		bellTower(c);
		chapel(c);
		ossuary(c);
		CrawlWing.build(c, THEME, AX, AY, AZ);
		// the stairs from the chapel aisle down to the ossuary, and from the burial chamber down to the barrow
		CrawlWing.stairwell(c, THEME, 0, OSS, 18, 0);
		CrawlWing.stairwell(c, THEME, AX, AY, AZ, OSS);
	}

	// ---------------------------------------------------------------- the yard

	private void yard(HideoutCanvas c) {
		for (int x = -17; x <= 17; x++) {
			for (int z = -16; z <= 21; z++) {
				c.air(x, 1, z, x, 13, z);
				c.footing(x, -1, z, 6, Blocks.DIRT.defaultBlockState());
				c.set(x, 0, z, ground(c, x, z));
			}
		}
		// the path from the lych-gate to the chapel door
		for (int z = -16; z <= 3; z++) {
			for (int x = -1; x <= 1; x++) {
				double n = c.noise(x, z, 2);
				c.set(x, 0, z, n < 0.55 ? Blocks.COBBLESTONE.defaultBlockState() : n < 0.8 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
						: Blocks.GRAVEL.defaultBlockState());
			}
		}
		// the fence: dark oak rails between mossy posts, soul lanterns on some posts
		runX(c, -16, -3, 1, -15, Blocks.DARK_OAK_FENCE);
		runX(c, 3, 16, 1, -15, Blocks.DARK_OAK_FENCE);
		runX(c, -16, 16, 1, 21, Blocks.DARK_OAK_FENCE);
		runZ(c, -15, 21, 1, -16, Blocks.DARK_OAK_FENCE);
		runZ(c, -15, 21, 1, 16, Blocks.DARK_OAK_FENCE);
		for (int x = -16; x <= 16; x += 4) {
			for (int z : new int[] { -15, 21 }) {
				if (z == -15 && Math.abs(x) < 3) {
					continue;
				}
				c.set(x, 1, z, Blocks.MOSSY_COBBLESTONE);
				if (c.noise(x, z, 3) < 0.5) {
					lantern(c, x, 2, z, true, false);
				}
			}
		}
		for (int z = -11; z <= 21; z += 4) {
			for (int x : new int[] { -16, 16 }) {
				c.set(x, 1, z, Blocks.MOSSY_COBBLESTONE);
				if (c.noise(x, z, 3) < 0.4) {
					lantern(c, x, 2, z, true, false);
				}
			}
		}
		// broken fence gaps
		for (int[] p : new int[][] { { -9, -15 }, { -16, 3 }, { 16, 12 }, { 7, 21 } }) {
			c.set(p[0], 1, p[1], Blocks.AIR.defaultBlockState());
		}
		// the lych-gate
		for (int x : new int[] { -3, 3 }) {
			c.fill(x, 1, -15, x, 3, -15, Blocks.STONE_BRICKS.defaultBlockState());
			c.set(x, 4, -15, Blocks.CHISELED_STONE_BRICKS);
		}
		for (int x = -4; x <= 4; x++) {
			c.set(x, 5, -16, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
			c.set(x, 5, -14, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
			c.set(x, 5, -15, Blocks.DARK_OAK_PLANKS);
			c.set(x, 6, -15, slab(Blocks.DARK_OAK_SLAB, false));
		}
		chainLantern(c, 0, 5, -15, 1, true);
		c.spawnPoint(-12, 1, -12);
		c.spawnPoint(12, 1, -10);
	}

	// ---------------------------------------------------------------- graves

	private void grave(HideoutCanvas c, int gx, int gz) {
		double n = c.noise(gx, gz, 10);
		BlockState stone;
		if (n < 0.3) {
			stone = Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState();
		} else if (n < 0.55) {
			stone = Blocks.COBBLESTONE_WALL.defaultBlockState();
		} else if (n < 0.75) {
			stone = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
		} else {
			stone = Blocks.STONE_BRICK_WALL.defaultBlockState();
		}
		c.set(gx, 1, gz, stone);
		if (n > 0.85) {
			c.set(gx, 2, gz, Blocks.STONE_BUTTON); // a little cross-piece on the tall ones
		}
		boolean open = c.noise(gx, gz, 11) < 0.18;
		for (int dz = 1; dz <= 2; dz++) {
			if (open) {
				c.set(gx, 0, gz + dz, Blocks.AIR.defaultBlockState());
				c.set(gx, -1, gz + dz, Blocks.SOUL_SOIL.defaultBlockState());
			} else {
				c.set(gx, 0, gz + dz, c.noise(gx, gz + dz, 12) < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState());
			}
		}
		if (!open && c.noise(gx, gz, 13) < 0.25) {
			candles(c, gx, 1, gz + 2, Blocks.PURPLE_CANDLE, 1, false);
		}
	}

	private void graves(HideoutCanvas c) {
		for (int gz = -12; gz <= 18; gz += 4) {
			for (int gx = -14; gx <= 14; gx += 3) {
				if (Math.abs(gx) <= 2 || inChapel(gx, gz) || inChapel(gx, gz + 2) || inTower(gx, gz) || inTower(gx, gz + 2)) {
					continue;
				}
				int jx = gx + (c.noise(gx, gz, 14) < 0.3 ? 1 : 0);
				grave(c, jx, gz);
			}
		}
		// the curse trap across the path: a cursed headstone on a tripwire (the hook hangs on a headstone east of it)
		c.set(2, 1, -8, Blocks.CHISELED_STONE_BRICKS);
		c.tripwireX(-1, 1, 1, -8);
	}

	private void deadTrees(HideoutCanvas c) {
		for (int[] t : new int[][] { { -11, -4, 5 }, { 11, -9, 6 }, { 12, 6, 4 }, { -6, -12, 4 } }) {
			int x = t[0];
			int z = t[1];
			int h = t[2];
			c.fill(x, 1, z, x, h, z, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
			c.set(x + 1, h - 1, z, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			c.set(x + 2, h, z, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			c.set(x, h - 2, z - 1, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
			c.set(x, h - 1, z - 2, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
			c.set(x + 2, h - 1, z, Blocks.COBWEB);
		}
	}

	// ---------------------------------------------------------------- the bell tower (sniper)

	private void bellTower(HideoutCanvas c) {
		int x0 = -12;
		int x1 = -9;
		int z0 = 6;
		int z1 = 9;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.footing(x, 0, z, 6, Blocks.COBBLESTONE.defaultBlockState());
				for (int y = 0; y <= 11; y++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					if (y == 0 || y == 11 || edge) {
						c.set(x, y, z, y == 0 ? Blocks.COBBLESTONE.defaultBlockState()
								: y == 11 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : weathered(c, x, y, z, 20));
					} else {
						c.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		// the door (east face), arrow slits, the ladder up the west wall through a hatch
		c.air(x1, 1, 7, x1, 2, 7);
		for (int y : new int[] { 4, 8 }) {
			c.set(x0, y, 7, Blocks.AIR.defaultBlockState());
			c.set(x1, y, 8, Blocks.AIR.defaultBlockState());
		}
		ladder(c, x0 + 1, 1, 11, z0 + 1, Direction.WEST);
		c.set(x0 + 1, 11, z0 + 1, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
		// the platform: corner posts, a slab roof, the bell
		for (int[] p : new int[][] { { x0, z0 }, { x1, z0 }, { x0, z1 }, { x1, z1 } }) {
			c.fill(p[0], 12, p[1], p[0], 13, p[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
		}
		c.fill(x0 - 1, 14, z0 - 1, x1 + 1, 14, z1 + 1, slab(Blocks.DEEPSLATE_TILE_SLAB, false));
		c.set(x0 + 2, 13, z0 + 2, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
		CrawlWing.lampAt(c, THEME, x1, 12, z1 - 1);
		c.guard(BONE_BLADE, x0 + 2, 12, z0 + 1, Direction.EAST, 1, GuardRole.SNIPER);
	}

	// ---------------------------------------------------------------- the chapel

	private static int roofY(int x) {
		return 8 + (7 - Math.min(7, Math.abs(x))) / 2;
	}

	private void chapel(HideoutCanvas c) {
		int x0 = -6;
		int x1 = 6;
		int z0 = 4;
		int z1 = 18;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.footing(x, -1, z, 6, Blocks.COBBLESTONE.defaultBlockState());
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				c.set(x, 0, z, edge ? Blocks.DEEPSLATE_BRICKS.defaultBlockState()
						: (x + z) % 3 == 0 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
				if (edge) {
					int top = (z == z0 || z == z1) ? roofY(x) : 7;
					for (int y = 1; y <= top; y++) {
						c.set(x, y, z, y == 1 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : weathered(c, x, y, z, 21));
					}
					// ruin: the top of the walls crumbles away here and there
					if (c.noise(x, z, 22) < 0.25 && top > 5) {
						c.air(x, top - 1, z, x, top, z);
					}
				}
			}
		}
		// the door: a pointed arch
		c.air(-1, 1, z0, 1, 4, z0);
		c.set(-1, 4, z0, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
		c.set(1, 4, z0, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
		// windows: purple glass, some smashed
		for (int z = 7; z <= 16; z += 3) {
			for (int x : new int[] { x0, x1 }) {
				for (int y = 3; y <= 5; y++) {
					c.set(x, y, z, c.noise(x, y, z, 23) < 0.3 ? Blocks.AIR.defaultBlockState() : Blocks.PURPLE_STAINED_GLASS.defaultBlockState());
				}
			}
		}
		c.set(0, roofY(0) - 2, z1, Blocks.PURPLE_STAINED_GLASS);
		c.set(0, roofY(0) - 3, z1, Blocks.PURPLE_STAINED_GLASS);
		// the roof: deepslate tile stairs up a gable, fallen in over the middle of the nave
		for (int z = z0 - 1; z <= z1 + 1; z++) {
			for (int x = x0 - 1; x <= x1 + 1; x++) {
				int y = roofY(x);
				boolean hole = z > 7 && z < 14 && Math.abs(x) <= 3 && c.noise(x, z, 24) < 0.65;
				if (hole) {
					continue;
				}
				if (x == 0) {
					c.set(x, y, z, Blocks.DEEPSLATE_TILES);
				} else {
					c.set(x, y, z, stair(Blocks.DEEPSLATE_TILE_STAIRS, x < 0 ? Direction.EAST : Direction.WEST, false));
					if (roofY(x) == roofY(x + (x < 0 ? 1 : -1))) {
						c.set(x, y, z, slab(Blocks.DEEPSLATE_TILE_SLAB, false));
					}
				}
			}
		}
		// rubble under the hole
		for (int[] p : new int[][] { { -2, 9 }, { 3, 11 }, { 1, 12 } }) {
			c.set(p[0], 1, p[1], c.noise(p[0], p[1], 25) < 0.5 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : slab(Blocks.DEEPSLATE_TILE_SLAB, false));
		}
		// pews either side of the aisle (backs to the north, facing the altar)
		for (int z = 6; z <= 14; z += 2) {
			for (int x : new int[] { -4, -3, 3, 4 }) {
				if (c.noise(x, z, 26) < 0.15) {
					continue; // broken
				}
				c.set(x, 1, z, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
			}
		}
		// the altar: polished blackstone, candles, a crying-obsidian sigil
		c.fill(-2, 1, 16, 2, 1, 16, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
		candles(c, -2, 2, 16, Blocks.PURPLE_CANDLE, 3, true);
		candles(c, 0, 2, 16, Blocks.BLACK_CANDLE, 4, true);
		candles(c, 2, 2, 16, Blocks.PURPLE_CANDLE, 2, true);
		c.set(0, 3, z1 - 0, Blocks.CRYING_OBSIDIAN);
		c.set(-1, 4, z1, Blocks.CRYING_OBSIDIAN);
		c.set(1, 4, z1, Blocks.CRYING_OBSIDIAN);
		c.set(0, 5, z1, Blocks.CRYING_OBSIDIAN);
		// stone pillars with the alarm braziers on them
		for (int[] p : new int[][] { { -5, 8 }, { 5, 8 }, { -5, 13 }, { 5, 13 } }) {
			c.fill(p[0], 1, p[1], p[0], 3, p[1], Blocks.POLISHED_DEEPSLATE.defaultBlockState());
			CrawlWing.lampAt(c, THEME, p[0], 4, p[1]);
		}
		chainLantern(c, 0, roofY(0), 15, 2, true);
		// Alarm Panel #1 (a carved ward stone) beside the altar, its runner, a medic, a shield-bearer at the door
		CrawlWing.panel(c, THEME, -5, 1, 16, Direction.EAST);
		c.guard(RISEN, -3, 1, 15, Direction.WEST, 4, GuardRole.RUNNER);
		c.guard(ACID, 3, 1, 14, Direction.NORTH, 5, GuardRole.MEDIC);
		c.guard(RISEN_ARMOURED, 0, 1, 6, Direction.NORTH, 4, GuardRole.SHIELD);
		c.guard(RISEN, -4, 1, 11, Direction.EAST, 5);
		c.spawnPoint(4, 1, 7);
		c.spawnPoint(-4, 1, 9);
		// graveyard watch
		c.guard(RISEN, -8, 1, -6, Direction.NORTH, 6);
		c.guard(RISEN, 9, 1, -2, Direction.WEST, 6);
		c.guard(BONE_BLADE, 6, 1, -11, Direction.NORTH, 6);
		c.guard(RISEN_ARMOURED, -9, 1, 14, Direction.EAST, 6);
	}

	// ---------------------------------------------------------------- the ossuary (y -9)

	private void ossuary(HideoutCanvas c) {
		BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		// the entry hall
		c.room(-4, OSS, 17, 4, OSS + 5, 24, wall);
		c.fill(-3, OSS, 18, 3, OSS, 23, Blocks.DEEPSLATE_TILES.defaultBlockState());
		for (int z = 18; z <= 23; z += 1) {
			for (int x : new int[] { -4, 4 }) {
				if (z % 2 == 0) {
					c.set(x, OSS + 2, z, Blocks.AIR.defaultBlockState());
					c.set(x, OSS + 1, z, Blocks.BONE_BLOCK.defaultBlockState());
					skull(c, x, OSS + 2, z, x < 0 ? 12 : 4);
				} else {
					c.fill(x, OSS + 1, z, x, OSS + 4, z, Blocks.BONE_BLOCK.defaultBlockState());
				}
			}
		}
		candles(c, -3, OSS + 1, 23, Blocks.PURPLE_CANDLE, 3, true);
		candles(c, 3, OSS + 1, 18, Blocks.PURPLE_CANDLE, 2, true);
		chainLantern(c, 0, OSS + 5, 21, 1, true);
		c.air(-1, OSS + 1, 24, 1, OSS + 3, 24);

		// the burial chamber
		int x0 = -11;
		int x1 = 11;
		int z0 = 24;
		int z1 = 42;
		int top = OSS + 8;
		c.room(x0, OSS, z0, x1, top, z1, wall);
		c.air(-1, OSS + 1, z0, 1, OSS + 3, z0);
		for (int x = x0 + 1; x < x1; x++) {
			for (int z = z0 + 1; z < z1; z++) {
				int ring = Math.max(Math.abs(x), Math.abs(z - 33));
				c.set(x, OSS, z, ring % 4 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
						: (x * 7 + z) % 5 == 0 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
			}
		}
		// the vault: upside-down stairs along the walls under the ceiling
		for (int z = z0 + 1; z < z1; z++) {
			c.set(x0 + 1, top - 1, z, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST, true));
			c.set(x1 - 1, top - 1, z, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST, true));
		}
		// burial niches all down both long walls
		for (int z = z0 + 2; z < z1 - 1; z += 2) {
			for (int x : new int[] { x0, x1 }) {
				for (int y : new int[] { OSS + 2, OSS + 4 }) {
					c.set(x, y, z, Blocks.AIR.defaultBlockState());
					c.set(x, y - 1, z, Blocks.BONE_BLOCK.defaultBlockState());
					if (c.noise(x, y, z, 30) < 0.6) {
						skull(c, x, y, z, x < 0 ? 12 : 4);
					} else if (c.noise(x, y, z, 31) < 0.5) {
						c.set(x, y, z, Blocks.COBWEB);
					}
				}
			}
		}
		// pillars
		for (int z : new int[] { 28, 33, 38 }) {
			for (int x : new int[] { -6, 6 }) {
				c.fill(x, OSS + 1, z, x, top - 1, z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
				c.set(x, OSS + 4, z, Blocks.CHISELED_DEEPSLATE);
			}
		}
		// sarcophagi in two rows
		for (int z : new int[] { 27, 31, 35, 39 }) {
			for (int x : new int[] { -9, 8 }) {
				c.fill(x, OSS + 1, z, x + 1, OSS + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				c.fill(x, OSS + 2, z, x + 1, OSS + 2, z, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, false));
				if (c.noise(x, z, 32) < 0.5) {
					candles(c, x + (z % 2), OSS + 3, z, Blocks.PURPLE_CANDLE, 2, true);
				}
			}
		}
		// soul-fire curse vents across the middle
		c.vent(0, OSS, 30, HideoutVentBlock.Kind.CURSE);
		c.vent(-1, OSS, 33, HideoutVentBlock.Kind.CURSE);
		c.vent(1, OSS, 33, HideoutVentBlock.Kind.CURSE);
		c.vent(0, OSS, 36, HideoutVentBlock.Kind.CURSE);
		// the curse tripwire across the doorway (its hook hangs on a carved headstone)
		c.set(2, OSS + 1, z0 + 1, Blocks.CHISELED_DEEPSLATE);
		c.tripwireX(-1, 1, OSS + 1, z0 + 1);
		for (int[] p : new int[][] { { -3, 28 }, { 3, 28 }, { -3, 40 }, { 3, 40 } }) {
			chainLantern(c, p[0], top, p[1], 2, true);
		}
		CrawlWing.lampAt(c, THEME, -6, OSS + 6, 33);
		CrawlWing.lampAt(c, THEME, 6, OSS + 6, 33);
		c.spawnPoint(-8, OSS + 1, 25);
		c.spawnPoint(8, OSS + 1, 41);
		c.spawnPoint(-4, OSS + 1, 36);

		// side tombs (west: a Cursed Zombie guards a barrel -- its touch is the real Gravebound Curse; east: loot)
		for (int side : new int[] { -1, 1 }) {
			int ox = side < 0 ? -18 : 11;
			c.room(ox, OSS, 30, ox + 7, OSS + 5, 36, wall);
			c.air(side < 0 ? x0 : x1, OSS + 1, 32, side < 0 ? x0 : x1, OSS + 3, 34);
			int far = side < 0 ? ox + 1 : ox + 6;
			c.fill(far, OSS + 1, 31, far, OSS + 1, 35, Blocks.BONE_BLOCK.defaultBlockState());
			for (int z = 31; z <= 35; z += 2) {
				skull(c, far, OSS + 2, z, side < 0 ? 12 : 4);
			}
			c.container(far + (side < 0 ? 1 : -1), OSS + 1, 33, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
			c.set(ox + 3, OSS + 1, 31, Blocks.COBWEB);
			chainLantern(c, ox + 4, OSS + 5, 33, 1, true);
		}
		c.guard(CURSED, -15, OSS + 1, 33, Direction.EAST, 3);
		c.guard(RISEN, 14, OSS + 1, 33, Direction.WEST, 3);

		// the chamber's dead
		c.guard(JUGGERNAUT, 0, OSS + 1, 38, Direction.NORTH, 5);
		c.guard(RISEN, -4, OSS + 1, 30, Direction.EAST, 5);
		c.guard(RISEN, 4, OSS + 1, 34, Direction.WEST, 5);
		c.guard(BONE_BLADE, -8, OSS + 1, 37, Direction.EAST, 5);
		c.guard(BONE_BLADE, 8, OSS + 1, 29, Direction.WEST, 5);
		c.guard(RISEN, 2, OSS + 1, 20, Direction.NORTH, 3);
	}
}
