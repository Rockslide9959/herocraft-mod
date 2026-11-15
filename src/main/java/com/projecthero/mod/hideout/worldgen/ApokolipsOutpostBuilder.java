package com.projecthero.mod.hideout.worldgen;

import static com.projecthero.mod.hideout.HideoutGuards.Role.PARADEMON;
import static com.projecthero.mod.hideout.HideoutGuards.Role.PARADEMON_BRUTE;
import static com.projecthero.mod.hideout.HideoutGuards.Role.PARADEMON_ELITE;
import static com.projecthero.mod.hideout.HideoutGuards.Role.PARADEMON_RANGED;

import com.projecthero.mod.hideout.HideoutEntityTypes;
import com.projecthero.mod.hideout.block.HideoutVentBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.5: the <b>Apokolips Outpost</b> -- a scorched crater (blackstone, basalt, magma, burning netherrack) with four fire
 * pits and four basalt perches for Parademon gunners round a dark tower of blackstone bricks and orange glass.
 * <ul>
 * <li><b>Ground floor</b>: the gate, a tripwire wired to a Boom Tube beacon (alarm: Parademons arrive), fire vents.</li>
 * <li><b>Barracks</b>: Elite Parademons.</li>
 * <li><b>War room</b> (top): the Hideout Core on a gilded pedestal, the sealed Hideout Vault, a balcony the Commander
 * flies from.</li>
 * </ul>
 * Ladders climb the tower's south-east corner. y = 0 is the surface at the crater's centre; the crater floor is 4 lower.
 */
public final class ApokolipsOutpostBuilder extends HideoutBuilder {
	public static final ApokolipsOutpostBuilder INSTANCE = new ApokolipsOutpostBuilder();

	static final int R = 18;
	static final int BASE = -4; // the tower's ground floor
	static final int L1 = 3; // barracks floor
	static final int L2 = 10; // war room floor
	static final int ROOF = 18;
	static final BlockPos CORE = new BlockPos(0, L2 + 2, 0);
	static final BlockPos VAULT = new BlockPos(0, L2 + 1, 4);

	private ApokolipsOutpostBuilder() {
		super(-22, -9, -22, 22, 28, 22);
	}

	@Override
	public BlockPos coreLocal() {
		return CORE;
	}

	@Override
	public BlockPos vaultLocal() {
		return VAULT;
	}

	/** The crater floor height at local (x, z). */
	static int floorAt(int x, int z) {
		double r = Math.sqrt(x * x + z * z);
		if (r > R) {
			return 0;
		}
		return -(int) Math.round(4.0 * (1.0 - (r / R) * (r / R)));
	}

	private static boolean inTower(int x, int z) {
		return Math.abs(x) <= 5 && Math.abs(z) <= 5 && Math.abs(x) + Math.abs(z) <= 9;
	}

	@Override
	public void build(HideoutCanvas c) {
		BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState gilded = Blocks.GILDED_BLACKSTONE.defaultBlockState();

		// ------------------------------------------------ the crater
		for (int x = -21; x <= 21; x++) {
			for (int z = -21; z <= 21; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r > R + 3.5) {
					continue;
				}
				int f = floorAt(x, z);
				if (r > R) {
					// the rim: heaped scorched rubble
					c.footing(x, -1, z, 10, Blocks.BLACKSTONE.defaultBlockState());
					double n = c.noise(x, z, 1);
					c.set(x, 0, z, n < 0.4 ? Blocks.BASALT.defaultBlockState() : n < 0.7 ? Blocks.BLACKSTONE.defaultBlockState()
							: Blocks.COARSE_DIRT.defaultBlockState());
					c.air(x, 1, z, x, 12, z);
					if (n < 0.25 && r < R + 2) {
						c.set(x, 1, z, Blocks.BLACKSTONE);
					}
					continue;
				}
				c.footing(x, f - 1, z, 10, Blocks.BLACKSTONE.defaultBlockState());
				c.air(x, f + 1, z, x, 14, z);
				double n = c.noise(x, z, 2);
				BlockState ground = n < 0.18 ? Blocks.MAGMA_BLOCK.defaultBlockState() : n < 0.42 ? Blocks.BLACKSTONE.defaultBlockState()
						: n < 0.62 ? Blocks.BASALT.defaultBlockState() : n < 0.75 ? Blocks.NETHERRACK.defaultBlockState()
						: n < 0.85 ? Blocks.BLACK_CONCRETE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
				c.fill(x, f - 1, z, x, f, z, ground);
				if (ground.is(Blocks.NETHERRACK) && c.noise(x, z, 3) < 0.35 && !inTower(x, z)) {
					c.set(x, f + 1, z, Blocks.FIRE);
				}
			}
		}
		// fire pits (diagonals)
		for (int[] d : new int[][] { { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 } }) {
			int px = d[0] * 8;
			int pz = d[1] * 8;
			int f = floorAt(px, pz);
			c.walls(px - 1, f + 1, pz - 1, px + 1, f + 1, pz + 1, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
			c.set(px, f, pz, Blocks.NETHERRACK);
			c.set(px, f + 1, pz, Blocks.FIRE);
		}
		// Parademon perches: basalt pillars with a platform on the rim
		for (int[] d : new int[][] { { 0, 15 }, { 15, 0 }, { -15, 0 }, { 11, -11 }, { -11, -11 } }) {
			int f = floorAt(d[0], d[1]);
			c.fill(d[0], f, d[1], d[0], f + 7, d[1], Blocks.BASALT.defaultBlockState());
			c.fill(d[0] - 1, f + 8, d[1] - 1, d[0] + 1, f + 8, d[1] + 1, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
			c.set(d[0] - 1, f + 9, d[1] - 1, Blocks.POINTED_DRIPSTONE);
			c.set(d[0] + 1, f + 9, d[1] + 1, Blocks.POINTED_DRIPSTONE);
			c.set(d[0] + 1, f + 9, d[1] - 1, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
			c.guard(PARADEMON_RANGED, d[0], f + 9, d[1], Direction.NORTH, 3);
		}

		// ------------------------------------------------ the tower shell (x/z -5..5, chamfered)
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				if (!inTower(x, z)) {
					continue;
				}
				c.footing(x, BASE - 1, z, 10, bricks);
				boolean edge = !inTower(x + 1, z) || !inTower(x - 1, z) || !inTower(x, z + 1) || !inTower(x, z - 1);
				for (int y = BASE; y <= ROOF; y++) {
					BlockState s;
					if (y == BASE || y == L1 || y == L2 || y == ROOF) {
						s = edge ? gilded : bricks;
					} else if (edge) {
						s = c.noise(x, y, z, 4) < 0.15 ? cracked : bricks;
					} else {
						s = Blocks.AIR.defaultBlockState();
					}
					c.set(x, y, z, s);
				}
			}
		}
		// buttresses and the spire
		for (int[] d : new int[][] { { 5, 3 }, { 5, -3 }, { -5, 3 }, { -5, -3 }, { 3, 5 }, { -3, 5 }, { 3, -5 }, { -3, -5 } }) {
			int ox = Integer.signum(d[0]) * (Math.abs(d[0]) == 5 ? 1 : 0);
			int oz = Integer.signum(d[1]) * (Math.abs(d[1]) == 5 ? 1 : 0);
			c.fill(d[0] + ox, BASE, d[1] + oz, d[0] + ox, ROOF - 2, d[1] + oz, Blocks.BLACKSTONE.defaultBlockState());
			c.set(d[0] + ox, ROOF - 1, d[1] + oz, Blocks.BLACKSTONE_WALL);
		}
		c.fill(-2, ROOF + 1, -2, 2, ROOF + 1, 2, bricks);
		c.fill(-1, ROOF + 2, -1, 1, ROOF + 4, 1, Blocks.BLACKSTONE.defaultBlockState());
		c.fill(0, ROOF + 5, 0, 0, ROOF + 8, 0, Blocks.ORANGE_STAINED_GLASS.defaultBlockState());
		c.set(0, ROOF + 9, 0, Blocks.SHROOMLIGHT);
		// windows: orange glass slits up every face, a big window band in the war room
		for (int y = L1 + 2; y <= L1 + 5; y++) {
			for (int k : new int[] { -2, 2 }) {
				c.set(k, y, -5, Blocks.ORANGE_STAINED_GLASS.defaultBlockState());
				c.set(k, y, 5, Blocks.ORANGE_STAINED_GLASS.defaultBlockState());
				c.set(-5, y, k, Blocks.ORANGE_STAINED_GLASS.defaultBlockState());
				c.set(5, y, k, Blocks.ORANGE_STAINED_GLASS.defaultBlockState());
			}
		}
		for (int y = L2 + 2; y <= L2 + 5; y++) {
			for (int k = -2; k <= 2; k++) {
				c.set(k, y, 5, Blocks.ORANGE_STAINED_GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true));
				c.set(-5, y, k, Blocks.ORANGE_STAINED_GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true));
				c.set(5, y, k, Blocks.ORANGE_STAINED_GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true));
			}
		}
		// ladders up the south-east corner, through holes in each floor
		for (int y = BASE + 1; y <= L2; y++) {
			c.set(3, y, 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
			c.set(4, y, 3, bricks);
		}

		// ------------------------------------------------ ground floor: the gate, the alarm, fire vents
		c.air(-1, BASE + 1, -5, 1, BASE + 3, -5);
		c.air(-1, BASE + 1, -6, 1, BASE + 3, -6);
		c.fill(-2, BASE + 4, -6, 2, BASE + 4, -6, gilded);
		c.fill(-2, BASE, -7, 2, BASE, -6, bricks);
		c.set(-2, BASE + 1, -6, Blocks.BLACKSTONE_WALL);
		c.set(2, BASE + 1, -6, Blocks.BLACKSTONE_WALL);
		c.set(-2, BASE + 2, -6, Blocks.MAGMA_BLOCK);
		c.set(2, BASE + 2, -6, Blocks.MAGMA_BLOCK);
		c.set(4, BASE + 1, -3, bricks);
		c.tripwireX(-3, 3, BASE + 1, -3);
		for (int[] v : new int[][] { { -2, -1 }, { 1, 0 }, { -1, 2 }, { 2, -2 }, { 0, 3 } }) {
			c.vent(v[0], BASE, v[1], HideoutVentBlock.Kind.FIRE);
		}
		c.set(-4, BASE + 3, 0, Blocks.SHROOMLIGHT);
		c.set(4, BASE + 3, 0, Blocks.SHROOMLIGHT);
		c.guard(PARADEMON, -3, BASE + 1, 1, Direction.NORTH, 4);
		c.guard(PARADEMON, 2, BASE + 1, 2, Direction.NORTH, 4);
		c.guard(PARADEMON_BRUTE, 0, floorAt(0, -9) + 1, -9, Direction.NORTH, 6);
		c.guard(PARADEMON, 6, floorAt(6, -9) + 1, -9, Direction.NORTH, 6);
		c.guard(PARADEMON, -7, floorAt(-7, 6) + 1, 6, Direction.NORTH, 6);

		// ------------------------------------------------ the barracks (floor L1)
		c.set(3, L1, 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		for (int z = -3; z <= 1; z += 2) {
			c.set(-4, L1 + 1, z, Blocks.RED_WOOL); // bunks
			c.set(-3, L1 + 1, z, Blocks.RED_WOOL);
		}
		c.fill(2, L1 + 1, -4, 4, L1 + 1, -4, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState()); // weapon rack
		c.set(3, L1 + 2, -4, Blocks.LIGHTNING_ROD);
		c.container(-4, L1 + 1, 3, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		c.set(0, L1 + 6, 0, Blocks.SHROOMLIGHT);
		c.guard(PARADEMON_ELITE, -1, L1 + 1, -2, Direction.NORTH, 4);
		c.guard(PARADEMON_ELITE, 1, L1 + 1, 2, Direction.SOUTH, 4);
		c.guard(PARADEMON_RANGED, 2, L1 + 1, -2, Direction.WEST, 4);

		// ------------------------------------------------ the war room (floor L2)
		c.set(3, L2, 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		c.fill(-1, L2 + 1, -1, 1, L2 + 1, 1, gilded);
		c.set(0, L2 + 1, 0, Blocks.CHISELED_POLISHED_BLACKSTONE);
		c.core(CORE.getX(), CORE.getY(), CORE.getZ());
		c.sealedVault(VAULT.getX(), VAULT.getY(), VAULT.getZ(), Direction.NORTH);
		c.set(-1, L2 + 1, 4, Blocks.MAGMA_BLOCK);
		c.set(1, L2 + 1, 4, Blocks.MAGMA_BLOCK);
		c.set(-3, L2 + 1, -3, Blocks.SOUL_CAMPFIRE);
		c.set(3, L2 + 1, -3, Blocks.SOUL_CAMPFIRE);
		c.set(0, ROOF - 1, 0, Blocks.SHROOMLIGHT);
		// the balcony to the north, open for the Commander's flight
		c.air(-2, L2 + 1, -5, 2, L2 + 4, -5);
		c.fill(-3, L2, -8, 3, L2, -6, bricks);
		for (int x = -3; x <= 3; x++) {
			c.set(x, L2 + 1, -8, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
		}
		c.set(-3, L2 + 1, -7, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
		c.set(3, L2 + 1, -7, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
		c.set(-3, L2 + 2, -8, Blocks.SOUL_LANTERN);
		c.set(3, L2 + 2, -8, Blocks.SOUL_LANTERN);
		c.guard(PARADEMON_RANGED, 2, L2 + 1, -6, Direction.NORTH, 3);
		c.boss(HideoutEntityTypes.PARADEMON_COMMANDER, 0, L2 + 1, -3, Direction.NORTH, c.corePos(CORE.getX(), CORE.getY(), CORE.getZ()));
	}
}
