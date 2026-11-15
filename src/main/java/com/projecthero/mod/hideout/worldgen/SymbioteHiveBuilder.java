package com.projecthero.mod.hideout.worldgen;

import static com.projecthero.mod.hideout.HideoutGuards.Role.HOST_HUSK;
import static com.projecthero.mod.hideout.HideoutGuards.Role.HOST_SKELETON;
import static com.projecthero.mod.hideout.HideoutGuards.Role.HOST_SPIDER;
import static com.projecthero.mod.hideout.HideoutGuards.Role.HOST_VINDICATOR;
import static com.projecthero.mod.hideout.HideoutGuards.Role.HOST_ZOMBIE;

import com.projecthero.mod.hideout.HideoutEntityTypes;
import com.projecthero.mod.hideout.block.HideoutBlocks;
import com.projecthero.mod.hideout.block.HideoutVentBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;

/**
 * v0.17.5: the <b>Symbiote Hive</b> -- a living cave deep under a forest. On the surface only a sinkhole ringed with
 * black biomass shows; a ladder runs 28 blocks down its shaft into the hive.
 * <ul>
 * <li><b>The great chamber</b>: dripping biomass walls set with egg pods, hanging biomass stalactites, black pools,
 * cocooned victims. Infested zombies, skeletons, husks, a spider.</li>
 * <li><b>The nursery</b> (east): walls crusted with pods; a strand of webbing across its tunnel is hooked to an egg pod
 * (alarm: the pods burst and more hosts crawl out).</li>
 * <li><b>The den</b> (south, past biomass vents): the Hideout Core on a pod mound, the sealed Hideout Vault, the Hive
 * Mother.</li>
 * </ul>
 * y = 0 is the surface at the sinkhole; the hive floor is 29 below.
 */
public final class SymbioteHiveBuilder extends HideoutBuilder {
	public static final SymbioteHiveBuilder INSTANCE = new SymbioteHiveBuilder();

	static final int FLOOR = -29;
	static final BlockPos CORE = new BlockPos(0, -27, 25);
	static final BlockPos VAULT = new BlockPos(0, -28, 30);

	private SymbioteHiveBuilder() {
		super(-16, -34, -16, 31, 3, 34);
	}

	@Override
	public BlockPos coreLocal() {
		return CORE;
	}

	@Override
	public BlockPos vaultLocal() {
		return VAULT;
	}

	private static BlockState lining(HideoutCanvas c, int x, int y, int z, double podChance) {
		double n = c.noise(x, y, z, 7);
		if (n < podChance) {
			return HideoutBlocks.HIVE_POD.defaultBlockState();
		}
		if (n < 0.68) {
			return HideoutBlocks.HIVE_BIOMASS.defaultBlockState();
		}
		if (n < 0.84) {
			return Blocks.BLACKSTONE.defaultBlockState();
		}
		if (n < 0.88) {
			return Blocks.SCULK.defaultBlockState();
		}
		return Blocks.BLACK_CONCRETE.defaultBlockState();
	}

	private static BlockState floorBlock(HideoutCanvas c, int x, int z) {
		double n = c.noise(x, z, 8);
		return n < 0.45 ? HideoutBlocks.HIVE_BIOMASS.defaultBlockState() : n < 0.75 ? Blocks.BLACKSTONE.defaultBlockState()
				: n < 0.82 ? Blocks.SCULK.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState();
	}

	/** Carves an ellipsoid chamber with a flat floor at {@link #FLOOR}, lined with biomass where the rock was. */
	private static void chamber(HideoutCanvas c, int cx, int cy, int cz, int rx, int ry, int rz, double pods) {
		for (int x = cx - rx - 2; x <= cx + rx + 2; x++) {
			for (int z = cz - rz - 2; z <= cz + rz + 2; z++) {
				for (int y = FLOOR - 1; y <= cy + ry + 2; y++) {
					double dx = (x - cx) / (double) rx;
					double dy = (y - cy) / (double) ry;
					double dz = (z - cz) / (double) rz;
					double v = dx * dx + dy * dy + dz * dz;
					double wobble = (c.noise(x, y, z, 9) - 0.5) * 0.25;
					if (v <= 1.0 + wobble) {
						if (y > FLOOR) {
							c.set(x, y, z, Blocks.AIR.defaultBlockState());
						} else if (y == FLOOR) {
							c.set(x, y, z, floorBlock(c, x, z));
						} else {
							c.set(x, y, z, Blocks.BLACKSTONE.defaultBlockState());
						}
					} else if (v <= 1.45 + wobble && !c.get(x, y, z).isAir()) {
						c.set(x, y, z, lining(c, x, y, z, pods));
					}
				}
			}
		}
	}

	/** A 3x3 tunnel between chambers (air, lined where it cut rock). */
	private static void tunnel(HideoutCanvas c, int x0, int z0, int x1, int z1) {
		boolean alongX = z0 == z1;
		for (int a = Math.min(alongX ? x0 : z0, alongX ? x1 : z1); a <= Math.max(alongX ? x0 : z0, alongX ? x1 : z1); a++) {
			for (int w = -2; w <= 2; w++) {
				for (int y = FLOOR; y <= FLOOR + 4; y++) {
					int x = alongX ? a : x0 + w;
					int z = alongX ? z0 + w : a;
					boolean inside = Math.abs(w) <= 1 && y > FLOOR && y < FLOOR + 4;
					if (inside) {
						c.set(x, y, z, Blocks.AIR.defaultBlockState());
					} else if (!c.get(x, y, z).isAir()) {
						c.set(x, y, z, y == FLOOR ? floorBlock(c, x, z) : lining(c, x, y, z, 0.05));
					}
				}
			}
		}
	}

	private static BlockState drip(DripstoneThickness t) {
		return Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
				.setValue(PointedDripstoneBlock.THICKNESS, t);
	}

	@Override
	public void build(HideoutCanvas c) {
		BlockState biomass = HideoutBlocks.HIVE_BIOMASS.defaultBlockState();

		// ------------------------------------------------ the sinkhole and its shaft
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r > 6.2 || (Math.abs(x) <= 1 && Math.abs(z) <= 1)) {
					continue;
				}
				double n = c.noise(x, z, 1);
				if (r <= 2.9) {
					c.fill(x, -16, z, x, 0, z, biomass); // the shaft lining
				} else if (n < 0.75 - (r - 3) * 0.15) {
					c.footing(x, -1, z, 6, Blocks.DIRT.defaultBlockState());
					c.set(x, 0, z, n < 0.35 ? biomass : n < 0.55 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BLACK_CONCRETE_POWDER.defaultBlockState());
					c.air(x, 1, z, x, 3, z);
				}
			}
		}
		c.air(-1, -16, -1, 1, 3, 1);
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				if (Math.abs(x) == 2 || Math.abs(z) == 2) {
					c.set(x, 1, z, c.noise(x, z, 2) < 0.5 ? biomass : Blocks.AIR.defaultBlockState()); // a ragged lip
				}
			}
		}

		// ------------------------------------------------ the chambers and tunnels
		chamber(c, 0, -24, 0, 13, 7, 13, 0.08);
		chamber(c, 21, -26, 0, 8, 4, 8, 0.32);
		chamber(c, 0, -26, 22, 9, 5, 9, 0.12);
		tunnel(c, 9, 0, 16, 0);
		tunnel(c, 0, 9, 0, 16);

		// the ladder: down the shaft (kept open through the chamber's ceiling) and a biomass pillar to the chamber floor
		c.air(-1, -21, -1, 1, 0, 1);
		c.fill(0, FLOOR + 1, -2, 0, -17, -2, biomass);
		for (int y = FLOOR + 1; y <= 0; y++) {
			c.set(0, y, -1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}

		// ------------------------------------------------ the great chamber
		for (int i = 0; i < 26; i++) {
			int x = (int) Math.round((c.noise(i, 0, 11) - 0.5) * 20);
			int z = (int) Math.round((c.noise(i, 1, 11) - 0.5) * 20);
			if (Math.abs(x) <= 2 && Math.abs(z) <= 3) {
				continue;
			}
			// find the ceiling above (x, z) and hang a stalactite from it
			for (int y = -17; y >= -26; y--) {
				if (!c.get(x, y, z).isAir() && c.get(x, y - 1, z).isAir()) {
					int len = 1 + (int) (c.noise(x, z, 12) * 3);
					for (int k = 1; k <= len; k++) {
						c.set(x, y - k, z, biomass);
					}
					c.set(x, y - len - 1, z, drip(DripstoneThickness.FRUSTUM));
					c.set(x, y - len - 2, z, drip(DripstoneThickness.TIP));
					break;
				}
			}
		}
		// black pools and biomass vents
		for (int[] p : new int[][] { { -6, 5 }, { 7, -3 }, { -4, -8 } }) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					c.set(p[0] + dx, FLOOR, p[1] + dz, Blocks.BLACK_CONCRETE.defaultBlockState());
				}
			}
			c.vent(p[0], FLOOR, p[1], HideoutVentBlock.Kind.BIOMASS);
		}
		// cocooned victims
		for (int[] p : new int[][] { { 9, 5 }, { -9, -2 }, { 5, 9 } }) {
			c.set(p[0], FLOOR + 1, p[1], Blocks.BONE_BLOCK);
			c.set(p[0], FLOOR + 2, p[1], Blocks.COBWEB);
			c.set(p[0] + 1, FLOOR + 1, p[1], Blocks.COBWEB);
		}
		c.guard(HOST_ZOMBIE, -5, FLOOR + 1, 3, Direction.NORTH, 6);
		c.guard(HOST_ZOMBIE, 4, FLOOR + 1, -5, Direction.WEST, 6);
		c.guard(HOST_SKELETON, 6, FLOOR + 1, 4, Direction.NORTH, 6);
		c.guard(HOST_SKELETON, -7, FLOOR + 1, -4, Direction.EAST, 6);
		c.guard(HOST_HUSK, 2, FLOOR + 1, 6, Direction.NORTH, 6);
		c.guard(HOST_SPIDER, -3, FLOOR + 1, -7, Direction.SOUTH, 6);

		// ------------------------------------------------ the nursery (east) and its strand alarm
		c.tripwireZ(-1, 1, FLOOR + 1, 13);
		for (int[] p : new int[][] { { 19, -3 }, { 23, 2 }, { 21, 4 }, { 24, -2 }, { 18, 3 } }) {
			c.set(p[0], FLOOR + 1, p[1], HideoutBlocks.HIVE_POD.defaultBlockState());
			if (c.noise(p[0], p[1], 13) < 0.5) {
				c.set(p[0], FLOOR + 2, p[1], HideoutBlocks.HIVE_POD.defaultBlockState());
			}
		}
		c.container(25, FLOOR + 1, 0, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		c.guard(HOST_SPIDER, 20, FLOOR + 1, 0, Direction.WEST, 5);
		c.guard(HOST_SPIDER, 22, FLOOR + 1, -4, Direction.WEST, 5);
		c.guard(HOST_HUSK, 23, FLOOR + 1, 3, Direction.WEST, 5);

		// ------------------------------------------------ the tunnel to the den: biomass vents
		c.vent(0, FLOOR, 11, HideoutVentBlock.Kind.BIOMASS);
		c.vent(-1, FLOOR, 13, HideoutVentBlock.Kind.BIOMASS);
		c.vent(1, FLOOR, 14, HideoutVentBlock.Kind.BIOMASS);

		// ------------------------------------------------ the den
		c.fill(-1, FLOOR + 1, 24, 1, FLOOR + 1, 26, HideoutBlocks.HIVE_POD.defaultBlockState());
		c.set(0, FLOOR + 1, 25, biomass);
		c.core(CORE.getX(), CORE.getY(), CORE.getZ());
		c.sealedVault(VAULT.getX(), VAULT.getY(), VAULT.getZ(), Direction.NORTH);
		c.fill(-1, FLOOR + 1, 31, 1, FLOOR + 3, 31, biomass);
		c.set(-1, FLOOR + 1, 30, biomass);
		c.set(1, FLOOR + 1, 30, biomass);
		for (int[] p : new int[][] { { -6, 20 }, { 6, 24 }, { -5, 27 } }) {
			c.set(p[0], FLOOR + 1, p[1], Blocks.BONE_BLOCK);
			c.set(p[0], FLOOR + 2, p[1], Blocks.COBWEB);
			c.set(p[0], FLOOR + 1, p[1] + 1, Blocks.COBWEB);
		}
		c.guard(HOST_ZOMBIE, -4, FLOOR + 1, 20, Direction.NORTH, 5);
		c.guard(HOST_ZOMBIE, 4, FLOOR + 1, 20, Direction.NORTH, 5);
		c.guard(HOST_VINDICATOR, 3, FLOOR + 1, 28, Direction.NORTH, 4);
		c.boss(HideoutEntityTypes.HIVE_MOTHER, 0, FLOOR + 1, 18, Direction.NORTH, c.corePos(CORE.getX(), CORE.getY(), CORE.getZ()));
	}
}
