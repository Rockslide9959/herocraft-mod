package com.projecthero.mod.hideout.worldgen;

import static com.projecthero.mod.hideout.HideoutGuards.Role.BROODLING;
import static com.projecthero.mod.hideout.HideoutGuards.Role.THRALL;
import static com.projecthero.mod.hideout.HideoutGuards.Role.THRALL_BRUTE;
import static com.projecthero.mod.hideout.HideoutGuards.Role.THRALL_SPITTER;
import static com.projecthero.mod.hideout.HideoutGuards.Role.THRALL_STALKER;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.ladder;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.runX;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.runZ;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.slab;
import static com.projecthero.mod.hideout.worldgen.HideoutDecor.stair;

import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.hideout.HideoutEntityTypes;
import com.projecthero.mod.hideout.block.HideoutBlocks;
import com.projecthero.mod.hideout.block.HideoutVentBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * v0.17.7: the <b>Carnage Nest</b> -- an old crimson-meteor crater in temperate country, where a quarantine field station
 * was overrun by what crawled out of the rock. Red symbiote biomass has swallowed half of it.
 * <ul>
 * <li><b>The crater</b> (surface): a scorched bowl round the cracked meteor, red biomass creeping up its sides, egg sacs
 * pulsing in the cracks; a broken quarantine fence round the rim, floodlights.</li>
 * <li><b>The field station</b> (outer defences, tech): the guard post with Alarm Panel #1 and its runner, cameras on its
 * corners; an overrun lab module; a watchtower with a sniper and a floodlight.</li>
 * <li><b>The hatchery</b> (14 down, under the meteor): a living cave of pulsing egg walls and crimson biomass vents,
 * cocooned scientists, a strand tripwire to an egg sac, Carnage's brood.</li>
 * <li><b>The containment labs</b> (the crawl wing, tech, overgrown): checkpoint, laser gauntlet, the lead researcher's
 * office (Captain + keycard), the breeding pit of the <b>Brood Spawn of Carnage</b>, the sealed vault.</li>
 * </ul>
 * y = 0 is the surface at the crater's centre (the bowl dips 6 below it).
 */
public final class CarnageNestBuilder extends HideoutBuilder {
	public static final CarnageNestBuilder INSTANCE = new CarnageNestBuilder();

	static final int CRATER_Z = 6;
	static final int CRATER_R = 14;
	static final int HATCH = -14;
	static final int AX = 0;
	static final int AY = -22;
	static final int AZ = 34;
	static final BlockPos CORE = CrawlWing.core(AX, AY, AZ);
	static final BlockPos VAULT = CrawlWing.vault(AX, AY, AZ);
	static final CrawlTheme THEME = theme();

	private static CrawlTheme theme() {
		CrawlTheme t = new CrawlTheme();
		t.wall = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
		t.floor = Blocks.GRAY_CONCRETE.defaultBlockState();
		t.ceiling = t.wall;
		t.trim = HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState();
		t.pillar = HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState();
		t.light = Blocks.SEA_LANTERN.defaultBlockState();
		t.accent = Blocks.RED_NETHER_BRICKS.defaultBlockState();
		t.stairs = Blocks.POLISHED_ANDESITE_STAIRS;
		t.vent = HideoutVentBlock.Kind.CRIMSON;
		t.tech = true;
		t.grunt = THRALL;
		t.elite = THRALL_BRUTE;
		t.ranged = THRALL_SPITTER;
		t.shield = THRALL;
		t.captain = THRALL_BRUTE;
		t.runner = THRALL_STALKER;
		t.sniper = THRALL_SPITTER;
		t.boss = HideoutEntityTypes.BROOD_SPAWN;
		t.decor = new CrawlTheme.Decor() {
			@Override
			public void checkpoint(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				overgrow(c, x0, y, z0, x1, z1, 5, 80);
				console(c, x1, y + 1, z0 + 1, Direction.WEST);
				c.fill(x1, y + 1, z1 - 1, x1, y + 2, z1 - 1, Blocks.IRON_BLOCK.defaultBlockState());
			}

			@Override
			public void quarters(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the lead researcher's office, half eaten: lab benches, a brewing stand, specimen tanks, cocoons
				overgrow(c, x0, y, z0, x1 + 4, z1, 7, 81);
				c.fill(x0 + 2, y + 1, z0 + 5, x0 + 5, y + 1, z0 + 5, Blocks.SMOOTH_STONE.defaultBlockState());
				c.set(x0 + 2, y + 2, z0 + 5, Blocks.BREWING_STAND);
				c.set(x0 + 4, y + 2, z0 + 5, Blocks.FLOWER_POT);
				c.set(x0 + 5, y + 2, z0 + 5, Blocks.CAULDRON);
				for (int z : new int[] { z0 + 2, z0 + 4 }) {
					tank(c, x0, y + 1, z);
				}
				cocoon(c, x0 + 1, y + 1, z1);
				console(c, x0 + 3, y + 1, z1, Direction.NORTH);
			}

			@Override
			public void arena(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the breeding pit: egg clusters, biomass mounds, a broken specimen cage, goo pools
				overgrow(c, x0, y, z0, x1, z1, 9, 82);
				int cx = (x0 + x1) / 2;
				int cz = (z0 + z1) / 2;
				for (int[] p : new int[][] { { -6, -7 }, { 6, -7 }, { -7, 6 }, { 7, 6 }, { 0, 8 } }) {
					eggCluster(c, cx + p[0], y, cz + p[1]);
				}
				for (int[] p : new int[][] { { -3, 0 }, { 3, -2 }, { 1, 4 } }) {
					c.fill(cx + p[0] - 1, y, cz + p[1], cx + p[0] + 1, y, cz + p[1], Blocks.RED_CONCRETE.defaultBlockState());
				}
				// the cage it broke out of
				for (int x = x1 - 3; x <= x1; x++) {
					c.set(x, y + 1, z0, Blocks.IRON_BARS);
					c.set(x, y + 2, z0, x == x1 - 1 ? Blocks.AIR.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState());
				}
			}

			@Override
			public void vault(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				overgrow(c, x0, y, z0, x1, z1, 4, 83);
			}
		};
		return t;
	}

	private CarnageNestBuilder() {
		super(-21, AY - 3, -17, 21, 14, AZ + CrawlWing.END_Z);
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

	/** Red biomass creeping over a room's walls and ceiling (egg sacs among it). */
	static void overgrow(HideoutCanvas c, int x0, int y, int z0, int x1, int z1, int h, int salt) {
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				boolean wall = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
				for (int yy = y + 1; yy <= y + h; yy++) {
					if (!wall) {
						continue;
					}
					double n = c.noise(x, yy, z, salt);
					BlockState s = c.get(x, yy, z);
					if (n < 0.3 && (s.is(Blocks.LIGHT_GRAY_CONCRETE) || s.is(Blocks.WHITE_CONCRETE) || s.is(Blocks.GRAY_CONCRETE))) {
						c.set(x, yy, z, n < 0.035 ? HideoutBlocks.CRIMSON_EGG.defaultBlockState() : HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState());
					}
				}
				if (!wall && c.noise(x, z, salt + 1) < 0.18) {
					c.set(x, y, z, HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState());
				}
			}
		}
	}

	static void eggCluster(HideoutCanvas c, int x, int y, int z) {
		c.fill(x - 1, y, z - 1, x + 1, y, z + 1, HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState());
		c.set(x, y + 1, z, HideoutBlocks.CRIMSON_EGG.defaultBlockState());
		if (c.noise(x, z, 84) < 0.6) {
			c.set(x, y + 2, z, HideoutBlocks.CRIMSON_EGG.defaultBlockState());
		}
		if (c.noise(x, z, 85) < 0.5) {
			c.set(x + 1, y + 1, z, HideoutBlocks.CRIMSON_EGG.defaultBlockState());
		}
	}

	/** A victim cocooned in red goo against a wall. */
	static void cocoon(HideoutCanvas c, int x, int y, int z) {
		c.set(x, y, z, HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState());
		c.set(x, y + 1, z, Blocks.RED_WOOL.defaultBlockState());
		c.set(x, y + 2, z, HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState());
	}

	/** A specimen tank: glass round a red core, iron cap and base. */
	static void tank(HideoutCanvas c, int x, int y, int z) {
		c.set(x, y, z, Blocks.IRON_BLOCK);
		c.set(x, y + 1, z, Blocks.RED_STAINED_GLASS);
		c.set(x, y + 2, z, Blocks.RED_STAINED_GLASS);
		c.set(x, y + 3, z, Blocks.IRON_BLOCK);
	}

	/** A dead lab console: a smooth stone desk with an iron trapdoor screen and a lever. */
	static void console(HideoutCanvas c, int x, int y, int z, Direction facing) {
		c.set(x, y, z, Blocks.SMOOTH_STONE);
		c.set(x, y + 1, z, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, true)
				.setValue(TrapDoorBlock.HALF, Half.BOTTOM));
	}

	/** The surface block layer at local (x, z): the bowl, the raised rim, or the old ground. */
	static int floorAt(int x, int z) {
		double r = Math.sqrt(x * x + (z - CRATER_Z) * (z - CRATER_Z));
		if (r <= CRATER_R) {
			return -(int) Math.round(craterDepth(r));
		}
		return r <= CRATER_R + 3 ? 1 : 0;
	}

	// ---------------------------------------------------------------- build

	private static double craterDepth(double r) {
		if (r > CRATER_R) {
			return 0;
		}
		double t = r / CRATER_R;
		return 6.0 * (1 - t * t);
	}

	@Override
	public void build(HideoutCanvas c) {
		crater(c);
		meteor(c);
		station(c);
		hatchery(c);
		CrawlWing.build(c, THEME, AX, AY, AZ);
		CrawlWing.stairwell(c, THEME, AX, AY, AZ, HATCH);
		entranceTunnel(c);
	}

	private void crater(HideoutCanvas c) {
		for (int x = -20; x <= 20; x++) {
			for (int z = -16; z <= 28; z++) {
				double r = Math.sqrt(x * x + (z - CRATER_Z) * (z - CRATER_Z));
				int floor;
				BlockState top;
				double n = c.noise(x, z, 40);
				if (r <= CRATER_R) {
					floor = -(int) Math.round(craterDepth(r));
					double heat = 1 - r / CRATER_R;
					if (n < 0.25 + heat * 0.35) {
						top = HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState();
					} else if (n < 0.55 + heat * 0.2) {
						top = Blocks.BLACKSTONE.defaultBlockState();
					} else if (n < 0.8) {
						top = Blocks.BASALT.defaultBlockState();
					} else if (n < 0.9 && r < 9) {
						top = Blocks.MAGMA_BLOCK.defaultBlockState();
					} else {
						top = Blocks.TUFF.defaultBlockState();
					}
				} else if (r <= CRATER_R + 3) {
					floor = 1; // the ejecta rim
					top = n < 0.4 ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.7 ? Blocks.GRAVEL.defaultBlockState()
							: n < 0.85 ? Blocks.BLACKSTONE.defaultBlockState() : HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState();
				} else {
					floor = 0;
					top = n < 0.15 ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.22 ? HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState()
							: Blocks.GRASS_BLOCK.defaultBlockState();
				}
				c.air(x, floor + 1, z, x, 14, z);
				c.footing(x, floor - 1, z, 8, Blocks.STONE.defaultBlockState());
				c.fill(x, floor - 2, z, x, floor - 1, z, r <= CRATER_R ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState());
				c.set(x, floor, z, top);
				// egg sacs pulsing in the cracks of the bowl
				if (r <= CRATER_R && r > 5 && c.noise(x, z, 41) < 0.05) {
					c.set(x, floor + 1, z, HideoutBlocks.CRIMSON_EGG.defaultBlockState());
				}
			}
		}
		// crimson vents in the bowl
		for (int[] p : new int[][] { { -7, 2 }, { 8, 9 }, { -5, 13 } }) {
			int r = (int) Math.sqrt(p[0] * p[0] + (p[1] - CRATER_Z) * (p[1] - CRATER_Z));
			c.vent(p[0], -(int) Math.round(craterDepth(r)), p[1], HideoutVentBlock.Kind.CRIMSON);
		}
	}

	private void meteor(HideoutCanvas c) {
		int cy = -4;
		for (int x = -5; x <= 5; x++) {
			for (int y = -9; y <= 1; y++) {
				for (int z = CRATER_Z - 5; z <= CRATER_Z + 5; z++) {
					double dx = x / 4.2;
					double dy = (y - cy) / 3.6;
					double dz = (z - CRATER_Z) / 4.0;
					double v = dx * dx + dy * dy + dz * dz + (c.noise(x, y, z, 42) - 0.5) * 0.35;
					if (v > 1.0) {
						continue;
					}
					double n = c.noise(x, y, z, 43);
					BlockState s;
					if (v > 0.75 && n < 0.35) {
						s = HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState();
					} else if (n < 0.12) {
						s = HideoutBlocks.CRIMSON_EGG.defaultBlockState(); // the glowing cracks
					} else if (n < 0.4) {
						s = Blocks.CRYING_OBSIDIAN.defaultBlockState();
					} else if (n < 0.7) {
						s = Blocks.OBSIDIAN.defaultBlockState();
					} else if (n < 0.85) {
						s = Blocks.RED_NETHER_BRICKS.defaultBlockState();
					} else {
						s = Blocks.MAGMA_BLOCK.defaultBlockState();
					}
					c.set(x, y, z, s);
				}
			}
		}
	}

	/** The breach in the meteor's north face and the passage down to the hatchery. */
	private void entranceTunnel(HideoutCanvas c) {
		BlockState wall = HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState();
		// from the bowl floor (z 0, y -5) down to the hatchery floor (z 9, y -14)
		for (int k = 0; k <= 9; k++) {
			int z = k;
			int fy = -5 - k;
			c.fill(-2, fy - 1, z, 2, fy + 5, z, wall);
			c.fill(-1, fy, z, 1, fy, z, k % 3 == 0 ? Blocks.BLACKSTONE.defaultBlockState() : stair(Blocks.BLACKSTONE_STAIRS, Direction.NORTH, false));
			c.air(-1, fy + 1, z, 1, fy + 4, z);
		}
		c.air(-1, -4, -1, 1, -1, -1);
		c.set(-2, -2, 0, HideoutBlocks.CRIMSON_EGG.defaultBlockState());
		c.set(2, -3, 1, HideoutBlocks.CRIMSON_EGG.defaultBlockState());
	}

	// ---------------------------------------------------------------- the field station

	private void station(HideoutCanvas c) {
		// the quarantine fence round the rim (iron bars, broken in places) with hazard-striped posts
		for (int i = 0; i < 64; i++) {
			double a = i * Math.PI * 2 / 64;
			int x = (int) Math.round(Math.cos(a) * 19);
			int z = CRATER_Z + (int) Math.round(Math.sin(a) * 19);
			if (z < -16 || z > 28 || c.noise(x, z, 44) < 0.22) {
				continue;
			}
			if (i % 4 == 0) {
				c.set(x, 1, z, Blocks.YELLOW_CONCRETE);
				c.set(x, 2, z, Blocks.BLACK_CONCRETE);
			} else {
				c.set(x, 1, z, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true)
						.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true));
			}
		}
		guardPost(c);
		labModule(c);
		watchtower(c);
		// floodlights on poles
		for (int[] p : new int[][] { { 15, -6 }, { -17, 18 }, { 16, 20 } }) {
			c.fill(p[0], 1, p[1], p[0], 6, p[1], Blocks.IRON_BARS.defaultBlockState());
			c.set(p[0], 7, p[1], Blocks.SEA_LANTERN);
			c.set(p[0], 8, p[1], slab(Blocks.SMOOTH_STONE_SLAB, false));
		}
		// a crashed supply crate and a jeep-ish wreck by the road in
		c.fill(6, 1, -12, 7, 1, -11, Blocks.IRON_BLOCK.defaultBlockState());
		c.set(6, 2, -12, Blocks.BLACK_CONCRETE);
		c.set(7, 2, -11, HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState());
		c.container(5, 1, -11, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		// rim guards
		c.guard(THRALL, 10, floorAt(10, -4) + 1, -4, Direction.NORTH, 6);
		c.guard(THRALL_STALKER, -9, floorAt(-9, 12) + 1, 12, Direction.EAST, 6);
		c.guard(THRALL_BRUTE, 4, floorAt(4, 14) + 1, 14, Direction.NORTH, 5);
		c.guard(THRALL, -6, floorAt(-6, 4) + 1, 4, Direction.EAST, 5, GuardRole.SHIELD);
		c.guard(BROODLING, 7, floorAt(7, 2) + 1, 2, Direction.WEST, 5);
		c.spawnPoint(-12, floorAt(-12, -2) + 1, -2);
		c.spawnPoint(11, floorAt(11, 15) + 1, 15);
		c.spawnPoint(-3, floorAt(-3, -3) + 1, -3);
	}

	private void guardPost(HideoutCanvas c) {
		int x0 = -18;
		int x1 = -11;
		int z0 = -14;
		int z1 = -8;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.footing(x, 0, z, 8, Blocks.STONE.defaultBlockState());
				c.set(x, 1, z, Blocks.SMOOTH_STONE);
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				for (int y = 2; y <= 5; y++) {
					c.set(x, y, z, edge ? (y == 2 || y == 5 ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState())
							: Blocks.AIR.defaultBlockState());
				}
				c.set(x, 6, z, Blocks.LIGHT_GRAY_CONCRETE);
			}
		}
		// windows, the door (facing the crater, east), biomass crawling in through a broken window
		for (int x = x0 + 2; x <= x1 - 2; x += 2) {
			c.set(x, 3, z0, Blocks.GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true));
			c.set(x, 3, z1, Blocks.AIR.defaultBlockState());
		}
		c.air(x1, 2, -11, x1, 3, -11);
		overgrow(c, x0 + 1, 1, z0 + 1, x1 - 1, z1 - 1, 4, 45);
		// inside: Alarm Panel #1 on the back wall, a desk and a dead monitor, a locker
		c.alarmPanel(x0 + 1, 2, -11, Direction.EAST);
		console(c, x0 + 3, 2, z1 - 1, Direction.NORTH);
		c.fill(x0 + 1, 2, z0 + 1, x0 + 1, 3, z0 + 1, Blocks.IRON_BLOCK.defaultBlockState());
		c.camera(x1, 5, z0, Direction.EAST);
		c.camera(x1, 5, z1, Direction.EAST);
		c.alarmLamp(x0 + 3, 6, -11);
		c.alarmLamp(x1 - 1, 6, z1 - 1);
		c.guard(THRALL_STALKER, x0 + 3, 2, -11, Direction.EAST, 4, GuardRole.RUNNER);
		c.guard(THRALL, x1 + 2, 2, -10, Direction.NORTH, 4);
	}

	private void labModule(HideoutCanvas c) {
		int x0 = 12;
		int x1 = 19;
		int z0 = 1;
		int z1 = 11;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.footing(x, 0, z, 8, Blocks.STONE.defaultBlockState());
				c.set(x, 1, z, Blocks.POLISHED_ANDESITE);
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				for (int y = 2; y <= 6; y++) {
					c.set(x, y, z, edge ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
				// the roof caved in at one end
				if (!(z > 7 && c.noise(x, z, 46) < 0.6)) {
					c.set(x, 7, z, Blocks.GRAY_CONCRETE);
				}
			}
		}
		for (int z = z0 + 2; z <= z1 - 2; z += 3) {
			c.set(x0, 4, z, Blocks.AIR.defaultBlockState());
			c.set(x1, 4, z, Blocks.RED_STAINED_GLASS);
		}
		c.air(x0, 2, 5, x0, 4, 6);
		overgrow(c, x0 + 1, 1, z0 + 1, x1 - 1, z1 - 1, 5, 47);
		c.fill(x1 - 1, 2, z0 + 2, x1 - 1, 2, z0 + 6, Blocks.SMOOTH_STONE.defaultBlockState());
		c.set(x1 - 1, 3, z0 + 3, Blocks.BREWING_STAND);
		tank(c, x1 - 1, 2, z1 - 2);
		cocoon(c, x0 + 1, 2, z1 - 1);
		eggCluster(c, x0 + 3, 1, z1 - 3);
		c.container(x1 - 1, 2, z0 + 1, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		c.alarmLamp(x0 + 3, 7, z0 + 2);
		c.camera(x0, 6, z0, Direction.WEST);
		c.guard(THRALL, x0 + 3, 2, z0 + 3, Direction.WEST, 4);
		c.guard(THRALL_SPITTER, x0 + 4, 2, z0 + 6, Direction.WEST, 4, GuardRole.MEDIC);
		c.spawnPoint(x0 + 5, 2, z0 + 8);
	}

	private void watchtower(HideoutCanvas c) {
		int x0 = -17;
		int z0 = 22;
		for (int[] p : new int[][] { { 0, 0 }, { 2, 0 }, { 0, 2 }, { 2, 2 } }) {
			c.footing(x0 + p[0], 0, z0 + p[1], 8, Blocks.STONE.defaultBlockState());
			c.fill(x0 + p[0], 1, z0 + p[1], x0 + p[0], 10, z0 + p[1], Blocks.IRON_BLOCK.defaultBlockState());
		}
		ladder(c, x0 + 1, 1, 11, z0 + 3, Direction.NORTH);
		c.set(x0 + 1, 10, z0 + 2, Blocks.IRON_BLOCK);
		c.fill(x0 - 1, 11, z0 - 1, x0 + 3, 11, z0 + 3, Blocks.SMOOTH_STONE.defaultBlockState());
		c.set(x0 + 1, 11, z0 + 3, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
		runX(c, x0 - 1, x0 + 3, 12, z0 - 1, Blocks.IRON_BARS);
		runX(c, x0 - 1, x0 + 3, 12, z0 + 3, Blocks.IRON_BARS);
		runZ(c, z0 - 1, z0 + 3, 12, x0 - 1, Blocks.IRON_BARS);
		runZ(c, z0 - 1, z0 + 3, 12, x0 + 3, Blocks.IRON_BARS);
		c.set(x0 + 1, 12, z0 + 3, Blocks.AIR.defaultBlockState());
		c.set(x0 + 3, 13, z0 - 1, Blocks.SEA_LANTERN);
		c.alarmLamp(x0 - 1, 13, z0 + 3);
		c.guard(THRALL_SPITTER, x0 + 1, 12, z0 + 1, Direction.NORTH, 1, GuardRole.SNIPER);
	}

	// ---------------------------------------------------------------- the hatchery (y -14)

	private void hatchery(HideoutCanvas c) {
		int cx = 0;
		int cy = -10;
		int cz = 18;
		int rx = 11;
		int ry = 5;
		int rz = 9;
		for (int x = cx - rx - 2; x <= cx + rx + 2; x++) {
			for (int z = cz - rz - 2; z <= cz + rz + 2; z++) {
				for (int y = HATCH - 1; y <= cy + ry + 2; y++) {
					double dx = (x - cx) / (double) rx;
					double dy = (y - cy) / (double) ry;
					double dz = (z - cz) / (double) rz;
					double v = dx * dx + dy * dy + dz * dz;
					double wobble = (c.noise(x, y, z, 50) - 0.5) * 0.25;
					if (v <= 1.0 + wobble) {
						if (y > HATCH) {
							c.set(x, y, z, Blocks.AIR.defaultBlockState());
						} else if (y == HATCH) {
							double n = c.noise(x, z, 51);
							c.set(x, y, z, n < 0.5 ? HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState() : n < 0.8 ? Blocks.BLACKSTONE.defaultBlockState()
									: Blocks.RED_CONCRETE.defaultBlockState());
						} else {
							c.set(x, y, z, Blocks.BLACKSTONE.defaultBlockState());
						}
					} else if (v <= 1.5 + wobble) {
						// the pulsing egg walls
						double n = c.noise(x, y, z, 52);
						c.set(x, y, z, n < 0.16 ? HideoutBlocks.CRIMSON_EGG.defaultBlockState() : n < 0.8 ? HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState()
								: Blocks.BLACKSTONE.defaultBlockState());
					}
				}
			}
		}
		// egg clusters, biomass columns, cocooned scientists
		for (int[] p : new int[][] { { -7, 13 }, { 7, 12 }, { -8, 22 }, { 6, 24 }, { 0, 25 } }) {
			eggCluster(c, p[0], HATCH, p[1]);
		}
		for (int[] p : new int[][] { { -4, 15 }, { 5, 19 } }) {
			c.fill(p[0], HATCH + 1, p[1], p[0], cy + ry, p[1], HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState());
		}
		for (int[] p : new int[][] { { -10, 17 }, { 10, 16 }, { -9, 20 } }) {
			cocoon(c, p[0], HATCH + 1, p[1]);
		}
		// crimson vents and a strand tripwire to an egg sac across the way south
		c.vent(-2, HATCH, 17, HideoutVentBlock.Kind.CRIMSON);
		c.vent(2, HATCH, 19, HideoutVentBlock.Kind.CRIMSON);
		c.vent(0, HATCH, 22, HideoutVentBlock.Kind.CRIMSON);
		c.set(2, HATCH + 1, 14, HideoutBlocks.CRIMSON_BIOMASS.defaultBlockState());
		c.tripwireX(-1, 1, HATCH + 1, 14);
		CrawlWing.lampAt(c, THEME, -4, HATCH + 5, 15);
		CrawlWing.lampAt(c, THEME, 5, HATCH + 5, 19);
		c.spawnPoint(-6, HATCH + 1, 18);
		c.spawnPoint(6, HATCH + 1, 15);
		c.guard(THRALL, -5, HATCH + 1, 20, Direction.NORTH, 5);
		c.guard(THRALL, 4, HATCH + 1, 22, Direction.NORTH, 5);
		c.guard(THRALL_SPITTER, -2, HATCH + 1, 24, Direction.NORTH, 5);
		c.guard(BROODLING, 7, HATCH + 1, 14, Direction.WEST, 5);
		c.guard(BROODLING, -7, HATCH + 1, 16, Direction.EAST, 5);
		c.guard(THRALL_BRUTE, 0, HATCH + 1, 20, Direction.NORTH, 4);
	}
}
