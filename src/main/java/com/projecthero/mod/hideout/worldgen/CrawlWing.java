package com.projecthero.mod.hideout.worldgen;

import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.block.AlarmLampBlock;
import com.projecthero.mod.alarm.block.AlarmPanelBlock;
import com.projecthero.mod.alarm.block.BlastDoorBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * v0.17.7: the deep half every rebuilt hideout shares (the 10-15 minute crawl), drawn below / behind the v0.17.5 layout
 * from an anchor {@code (ax, ay, az)} -- {@code ay} is the floor block layer at the foot of the stairwell, the wing runs
 * toward local +z:
 * <ol>
 * <li><b>Stairwell</b> down from the old final room ({@link #stairwell}).</li>
 * <li><b>Checkpoint</b> (z 0..8): Alarm Panel #2, a camera (tech), a Shield-bearer and a guard, two spawn points.</li>
 * <li><b>Gauntlet</b> (z 10..26, 3 wide): knee- and head-height laser tripwires (tech) or string tripwires on the
 * faction alarm, vents, a pressure-plate arrow crossfire, a camera sweeping the far end.</li>
 * <li><b>Captain's quarters</b> (z 28..36): the Captain (keycard), a Medic, an Alarm Runner, a Sniper on the east
 * mezzanine, a Tier 2 barrel; the keycard Blast Door south into ...</li>
 * <li><b>The arena</b> (z 38..58, floor 2 lower): the mini-boss, two elites, pillars, Alarm Panel #3, four spawn
 * points; then</li>
 * <li><b>the vault room</b> (z 60..66): the sealed Hideout Core and the sealed Hideout Vault.</li>
 * </ol>
 * Footprint x -11..11, y -3..10, z 0..67 around the anchor (plus the stairwell north of it).
 */
public final class CrawlWing {
	public static final int CHECK_Z = 0;
	public static final int GAUNTLET_Z = 9;
	public static final int QUARTERS_Z = 27;
	public static final int ARENA_Z = 37;
	public static final int VAULT_Z = 59;
	public static final int END_Z = 67;
	public static final int HALF_WIDTH = 11;

	private CrawlWing() {
	}

	/** Local core position for a wing anchored at (ax, ay, az). */
	public static BlockPos core(int ax, int ay, int az) {
		return new BlockPos(ax, ay - 1, az + 62);
	}

	/** Local vault position for a wing anchored at (ax, ay, az). */
	public static BlockPos vault(int ax, int ay, int az) {
		return new BlockPos(ax, ay - 1, az + 66);
	}

	/** Local spot of the mini-boss. */
	public static BlockPos bossSpot(int ax, int ay, int az) {
		return new BlockPos(ax, ay - 1, az + 49);
	}

	/** Where a stairwell that ends at the wing's anchor starts (its first step) for a room floor at {@code yTop}. */
	public static int stairStartZ(int ay, int az, int yTop) {
		return az - (yTop - ay);
	}

	private static BlockState stair(CrawlTheme t, Direction facing) {
		return t.stairs.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	/**
	 * A 3-wide stairwell down along local +z from a room whose floor block layer is {@code yTop} (the hole opens at
	 * {@link #stairStartZ}) to the wing's anchor floor {@code ay} at {@code az}.
	 */
	public static void stairwell(HideoutCanvas c, CrawlTheme t, int ax, int ay, int az, int yTop) {
		int n = yTop - ay;
		int z0 = az - n;
		for (int k = 0; k < n; k++) {
			int z = z0 + k;
			int stepY = yTop - 1 - k;
			int top = Math.min(stepY + 5, yTop);
			c.fill(ax - 2, stepY - 1, z, ax + 2, top, z, t.wall);
			for (int x = ax - 1; x <= ax + 1; x++) {
				c.set(x, stepY, z, stair(t, Direction.NORTH));
				c.set(x, stepY - 1, z, t.floor);
			}
			int airTop = stepY + 5 <= yTop ? stepY + 4 : yTop + 3; // clear whatever stands on the hole too
			c.air(ax - 1, stepY + 1, z, ax + 1, airTop, z);
			if (k % 4 == 2) {
				c.set(ax + 2, stepY + 3, z, t.light);
			}
		}
		// a rail round the hole in the room above
		for (int z = z0; z <= z0 + 3 && z < az; z++) {
			c.set(ax - 2, yTop + 1, z, Blocks.IRON_BARS.defaultBlockState());
			c.set(ax + 2, yTop + 1, z, Blocks.IRON_BARS.defaultBlockState());
		}
		// the landing into the checkpoint
		c.air(ax - 1, ay + 1, az - 1, ax + 1, ay + 4, az - 1);
		c.fill(ax - 1, ay, az - 1, ax + 1, ay, az - 1, t.floor);
	}

	/** Draws the whole wing. */
	public static void build(HideoutCanvas c, CrawlTheme t, int ax, int ay, int az) {
		BlockPos core = core(ax, ay, az);
		BlockPos vault = vault(ax, ay, az);

		// ------------------------------------------------ shells (A, B, C, D, E), then the openings
		c.room(ax - 5, ay, az - 1, ax + 5, ay + 6, az + 9, t.wall);
		c.room(ax - 2, ay, az + GAUNTLET_Z, ax + 2, ay + 5, az + QUARTERS_Z, t.wall);
		c.room(ax - 7, ay, az + QUARTERS_Z, ax + 7, ay + 7, az + ARENA_Z, t.wall);
		c.room(ax - HALF_WIDTH, ay - 2, az + ARENA_Z, ax + HALF_WIDTH, ay + 9, az + VAULT_Z, t.wall);
		c.room(ax - 5, ay - 2, az + VAULT_Z, ax + 5, ay + 4, az + END_Z, t.wall);
		c.air(ax - 1, ay + 1, az + GAUNTLET_Z, ax + 1, ay + 3, az + GAUNTLET_Z);
		c.air(ax - 1, ay + 1, az + QUARTERS_Z, ax + 1, ay + 3, az + QUARTERS_Z);
		c.air(ax - 1, ay - 1, az + VAULT_Z, ax + 1, ay + 1, az + VAULT_Z);

		checkpoint(c, t, ax, ay, az);
		gauntlet(c, t, ax, ay, az);
		quarters(c, t, ax, ay, az);
		arena(c, t, ax, ay, az, core);
		vaultRoom(c, t, ax, ay, az, core, vault);
		stairwellCap(c, t, ax, ay, az);
	}

	private static void stairwellCap(HideoutCanvas c, CrawlTheme t, int ax, int ay, int az) {
		// the checkpoint's north wall keeps its opening for the stairwell (drawn by the builder afterwards)
		c.air(ax - 1, ay + 1, az - 1, ax + 1, ay + 4, az - 1);
	}

	// ---------------------------------------------------------------- A: checkpoint

	private static void checkpoint(HideoutCanvas c, CrawlTheme t, int ax, int ay, int az) {
		for (int x = ax - 4; x <= ax + 4; x++) {
			for (int z = az; z <= az + 8; z++) {
				c.set(x, ay, z, (x == ax - 2 || x == ax + 2) ? t.trim : t.floor);
			}
		}
		panel(c, t, ax - 4, ay + 1, az + 4, Direction.EAST);
		lamp(c, t, ax - 2, ay + 6, az + 2);
		lamp(c, t, ax + 2, ay + 6, az + 6);
		c.set(ax, ay + 6, az + 4, t.light);
		c.set(ax - 3, ay + 6, az + 7, t.light);
		c.set(ax + 3, ay + 6, az + 1, t.light);
		if (t.tech) {
			c.camera(ax + 4, ay + 4, az, Direction.SOUTH);
		}
		c.spawnPoint(ax - 3, ay + 1, az + 7);
		c.spawnPoint(ax + 3, ay + 1, az + 1);
		t.decor.checkpoint(c, ax - 4, ay, az, ax + 4, az + 8);
		c.guard(t.shield, ax, ay + 1, az + 6, Direction.NORTH, 4, GuardRole.SHIELD);
		c.guard(t.grunt, ax + 3, ay + 1, az + 4, Direction.WEST, 4);
	}

	// ---------------------------------------------------------------- B: gauntlet

	private static void gauntlet(HideoutCanvas c, CrawlTheme t, int ax, int ay, int az) {
		int z0 = az + GAUNTLET_Z + 1;
		int z1 = az + QUARTERS_Z - 1;
		for (int z = z0; z <= z1; z++) {
			c.fill(ax - 1, ay, z, ax + 1, ay, z, (z - z0) % 4 == 0 ? t.trim : t.floor);
		}
		for (int z = z0 + 1; z <= z1; z += 4) {
			c.set(ax, ay + 5, z, t.light);
		}
		lamp(c, t, ax, ay + 5, az + 13);
		lamp(c, t, ax, ay + 5, az + 21);
		if (t.tech) {
			c.laser(ax - 1, ay + 1, az + 12, Direction.EAST); // knee height: jump it
			c.laser(ax + 1, ay + 2, az + 16, Direction.WEST); // head height: crouch under it
			c.laser(ax + 1, ay + 1, az + 20, Direction.WEST); // jump ...
			c.laser(ax - 1, ay + 2, az + 21, Direction.EAST); // ... then crouch
			c.camera(ax + 1, ay + 4, z1, Direction.NORTH);
		} else {
			c.tripwireX(ax - 1, ax + 1, ay + 1, az + 12);
			c.tripwireX(ax - 1, ax + 1, ay + 1, az + 21);
		}
		c.vent(ax - 1, ay, az + 14, t.vent);
		c.vent(ax + 1, ay, az + 14, t.vent);
		c.vent(ax, ay, az + 15, t.vent);
		// arrow crossfire: plates beside dispensers set in both walls
		c.arrowDispenser(ax - 2, ay + 1, az + 18, Direction.EAST);
		c.arrowDispenser(ax + 2, ay + 1, az + 18, Direction.WEST);
		c.set(ax - 1, ay + 1, az + 18, Blocks.STONE_PRESSURE_PLATE);
		c.set(ax + 1, ay + 1, az + 18, Blocks.STONE_PRESSURE_PLATE);
		c.set(ax, ay + 1, az + 18, Blocks.STONE_PRESSURE_PLATE);
		c.vent(ax - 1, ay, az + 23, t.vent);
		c.vent(ax + 1, ay, az + 23, t.vent);
		c.vent(ax, ay, az + 24, t.vent);
		c.guard(t.grunt, ax, ay + 1, az + 25, Direction.SOUTH, 3);
	}

	// ---------------------------------------------------------------- C: the Captain's quarters

	private static void quarters(HideoutCanvas c, CrawlTheme t, int ax, int ay, int az) {
		int z0 = az + QUARTERS_Z + 1;
		int z1 = az + ARENA_Z - 1;
		for (int x = ax - 6; x <= ax + 6; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, ay, z, (x + z) % 5 == 0 ? t.accent : t.floor);
			}
		}
		// the sniper's mezzanine along the east wall, a ladder up at its south end
		c.fill(ax + 4, ay + 3, z0, ax + 6, ay + 3, z1, t.trim);
		for (int z = z0; z <= z1; z++) {
			c.set(ax + 4, ay + 4, z, Blocks.IRON_BARS.defaultBlockState());
		}
		c.air(ax + 6, ay + 3, z1, ax + 6, ay + 3, z1);
		for (int y = ay + 1; y <= ay + 3; y++) {
			c.set(ax + 6, y, z1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		}
		c.set(ax + 4, ay + 4, z1, Blocks.AIR.defaultBlockState());
		c.set(ax + 4, ay + 4, z1 - 1, Blocks.AIR.defaultBlockState());
		lamp(c, t, ax - 3, ay + 7, az + 32);
		lamp(c, t, ax + 1, ay + 7, az + 35);
		c.set(ax - 3, ay + 7, az + 29, t.light);
		c.set(ax, ay + 7, az + 32, t.light);
		c.set(ax + 5, ay + 7, az + 30, t.light);
		c.set(ax + 5, ay + 7, az + 34, t.light);
		c.container(ax - 6, ay + 1, z0, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier2"));
		c.spawnPoint(ax - 5, ay + 1, z0 + 1);
		c.spawnPoint(ax - 5, ay + 1, z1 - 1);
		t.decor.quarters(c, ax - 6, ay, z0, ax + 2, z1);
		// the keycard Blast Door into the arena
		door(c, t, ax - 1, ay + 1, az + ARENA_Z, ax + 1, ay + 3, az + ARENA_Z, true);
		c.set(ax - 2, ay + 4, az + ARENA_Z - 1, t.accent);
		c.set(ax + 2, ay + 4, az + ARENA_Z - 1, t.accent);
		c.guard(t.captain, ax, ay + 1, az + 33, Direction.NORTH, 4, GuardRole.CAPTAIN);
		c.guard(t.grunt, ax - 3, ay + 1, az + 34, Direction.NORTH, 4, GuardRole.MEDIC);
		c.guard(t.runner, ax - 4, ay + 1, az + 30, Direction.EAST, 4, GuardRole.RUNNER);
		c.guard(t.sniper, ax + 5, ay + 4, az + 32, Direction.WEST, 2, GuardRole.SNIPER);
	}

	// ---------------------------------------------------------------- D: the arena

	private static void arena(HideoutCanvas c, CrawlTheme t, int ax, int ay, int az, BlockPos core) {
		int x0 = ax - HALF_WIDTH + 1;
		int x1 = ax + HALF_WIDTH - 1;
		int z0 = az + ARENA_Z + 1;
		int z1 = az + VAULT_Z - 1;
		int fy = ay - 2;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int ring = Math.max(Math.abs(x - ax), Math.abs(z - (az + 49)));
				c.set(x, fy, z, ring == 4 || ring == 8 ? t.trim : t.floor);
			}
		}
		// the landing below the Blast Door and the steps down
		c.fill(ax - 2, ay - 1, z0, ax + 2, ay, z0 + 1, t.trim);
		for (int x = ax - 2; x <= ax + 2; x++) {
			c.set(x, ay - 1, z0 + 2, t.trim);
			c.set(x, ay, z0 + 2, stair(t, Direction.NORTH));
			c.set(x, ay - 1, z0 + 3, stair(t, Direction.NORTH));
		}
		// pillars
		for (int[] p : new int[][] { { -6, 44 }, { 6, 44 }, { -6, 53 }, { 6, 53 } }) {
			c.fill(ax + p[0], fy + 1, az + p[1], ax + p[0], ay + 8, az + p[1], t.pillar);
			c.set(ax + p[0], ay + 3, az + p[1], t.light);
		}
		// lights and lamps
		for (int[] p : new int[][] { { -5, 40 }, { 5, 40 }, { -5, 49 }, { 5, 49 }, { -5, 57 }, { 5, 57 }, { 0, 44 }, { 0, 54 } }) {
			c.set(ax + p[0], ay + 9, az + p[1], t.light);
		}
		for (int[] p : new int[][] { { -HALF_WIDTH, 44 }, { HALF_WIDTH, 44 }, { -HALF_WIDTH, 54 }, { HALF_WIDTH, 54 } }) {
			lamp(c, t, ax + p[0], ay + 4, az + p[1]);
		}
		lamp(c, t, ax, ay + 9, az + 49);
		// Alarm Panel #3 at the back right, the spawn points in the corners
		panel(c, t, x1, fy + 1, z1 - 1, Direction.WEST);
		c.spawnPoint(x0 + 1, fy + 1, z0 + 4);
		c.spawnPoint(x1 - 1, fy + 1, z0 + 4);
		c.spawnPoint(x0 + 1, fy + 1, z1 - 2);
		c.spawnPoint(x1 - 3, fy + 1, z1 - 2);
		t.decor.arena(c, x0, fy, z0, x1, z1);
		c.guard(t.elite, ax - 4, fy + 1, az + 50, Direction.NORTH, 6);
		c.guard(t.elite, ax + 4, fy + 1, az + 50, Direction.NORTH, 6);
		BlockPos b = bossSpot(ax, ay, az);
		c.boss(t.bossType(), b.getX(), b.getY(), b.getZ(), Direction.NORTH, c.corePos(core.getX(), core.getY(), core.getZ()));
	}

	// ---------------------------------------------------------------- E: the vault room

	private static void vaultRoom(HideoutCanvas c, CrawlTheme t, int ax, int ay, int az, BlockPos core, BlockPos vault) {
		int fy = ay - 2;
		c.fill(ax - 4, fy, az + VAULT_Z + 1, ax + 4, fy, az + END_Z - 1, t.floor);
		c.fill(ax - 1, fy, core.getZ() - 1, ax + 1, fy, core.getZ() + 1, t.trim);
		c.core(core.getX(), core.getY(), core.getZ());
		c.sealedVault(vault.getX(), vault.getY(), vault.getZ(), Direction.NORTH);
		c.set(vault.getX() - 1, vault.getY(), vault.getZ(), t.pillar);
		c.set(vault.getX() + 1, vault.getY(), vault.getZ(), t.pillar);
		c.set(vault.getX(), vault.getY() + 1, vault.getZ(), t.accent);
		for (int x : new int[] { ax - 3, ax + 3 }) {
			c.set(x, ay + 4, az + 62, t.light);
		}
		t.decor.vault(c, ax - 4, fy, az + VAULT_Z + 1, ax + 4, az + END_Z - 1);
	}

	// ---------------------------------------------------------------- helpers

	private static void lamp(HideoutCanvas c, CrawlTheme t, int x, int y, int z) {
		c.set(x, y, z, AlarmBlocks.LAMP.defaultBlockState().setValue(AlarmLampBlock.STYLE, t.style));
	}

	static void panel(HideoutCanvas c, CrawlTheme t, int x, int y, int z, Direction facing) {
		c.set(x, y, z, AlarmBlocks.PANEL.defaultBlockState().setValue(AlarmPanelBlock.FACING, facing).setValue(AlarmPanelBlock.STYLE, t.style));
	}

	static void door(HideoutCanvas c, CrawlTheme t, int x0, int y0, int z0, int x1, int y1, int z1, boolean locked) {
		c.fill(x0, y0, z0, x1, y1, z1, AlarmBlocks.BLAST_DOOR.defaultBlockState().setValue(BlastDoorBlock.LOCKED, locked)
				.setValue(BlastDoorBlock.OPEN, !locked).setValue(BlastDoorBlock.STYLE, t.style));
	}

	static void lampAt(HideoutCanvas c, CrawlTheme t, int x, int y, int z) {
		lamp(c, t, x, y, z);
	}
}
