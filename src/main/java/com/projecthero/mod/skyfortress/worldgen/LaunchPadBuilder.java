package com.projecthero.mod.skyfortress.worldgen;

import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.hideout.worldgen.HideoutCanvas;
import com.projecthero.mod.skyfortress.SkyFortressGuards;
import com.projecthero.mod.skyfortress.SkyFortressGuards.Kind;
import com.projecthero.mod.skyfortress.block.SkyFortressBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: the Sky Fortress's <b>Launch Pad</b> post on the ground, straight under the hangar's lift hatch. Local
 * {@code (0, 0, 0)} is the pad's centre block (the floor layer); a 21 x 21 fenced platform (on piles out of the water
 * when it stands in the sea) with the 3 x 3 pad in a hazard ring, a guard shack (Alarm Panel, a camera, a sniper on the
 * roof), floodlights, a radio mast and a windsock. Guards: the <b>Pad Sergeant</b> (drops the Fortress Pass), a trooper,
 * an Alarm Runner and the roof sniper. The column over the pad is cleared all the way up ({@link #CLEAR}).
 */
public final class LaunchPadBuilder {
	public static final LaunchPadBuilder INSTANCE = new LaunchPadBuilder();
	public static final int HALF = 10;
	public static final int CLEAR = 44;
	public static final int PILE_DEPTH = 48;
	public static final BlockPos SERGEANT = new BlockPos(2, 1, 3);

	private LaunchPadBuilder() {
	}

	public int minY(boolean water) {
		return water ? -PILE_DEPTH - 2 : -12;
	}

	public void build(HideoutCanvas c, boolean water) {
		BlockState base = Blocks.STONE_BRICKS.defaultBlockState();
		// footing: the whole platform on land, piles in the sea
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				if (water) {
					boolean pile = (Math.abs(x) == HALF || x == 0) && (Math.abs(z) == HALF || z == 0);
					if (pile) {
						c.footing(x, -1, z, PILE_DEPTH, base);
					}
				} else {
					c.footing(x, -1, z, 10, base);
				}
			}
		}
		c.air(-HALF, 1, -HALF, HALF, 9, HALF);
		c.air(-2, 10, -2, 2, CLEAR, 2);
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				boolean edge = Math.abs(x) == HALF || Math.abs(z) == HALF;
				double n = c.noise(x, z, 50);
				c.set(x, 0, z, edge ? Blocks.POLISHED_ANDESITE.defaultBlockState()
						: n < 0.1 ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
			}
		}
		// the pad in its hazard ring, guide lights
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
					c.set(x, 0, z, SkyFortressBlocks.LAUNCH_PAD);
				} else {
					c.set(x, 0, z, ((x + z) & 1) == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE);
				}
			}
		}
		for (int[] p : new int[][] { { -3, -3 }, { 3, -3 }, { -3, 3 }, { 3, 3 } }) {
			c.set(p[0], 0, p[1], Blocks.SEA_LANTERN);
		}
		// fence with a gate on the north side, floodlight posts at the corners
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				if ((Math.abs(x) == HALF || Math.abs(z) == HALF) && !(z == -HALF && Math.abs(x) <= 1)) {
					c.set(x, 1, z, Blocks.IRON_BARS);
				}
			}
		}
		for (int[] p : new int[][] { { -HALF, -HALF }, { HALF, -HALF }, { -HALF, HALF }, { HALF, HALF } }) {
			c.fill(p[0], 1, p[1], p[0], 4, p[1], Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
			c.set(p[0], 5, p[1], Blocks.SEA_LANTERN);
		}
		// the guard shack
		shack(c);
		// radio mast and windsock
		c.fill(-8, 1, -8, -8, 9, -8, Blocks.IRON_BARS.defaultBlockState());
		c.set(-8, 10, -8, Blocks.LIGHTNING_ROD);
		c.set(-8, 6, -7, Blocks.IRON_TRAPDOOR);
		c.set(-8, 1, -8, Blocks.IRON_BLOCK);
		c.fill(8, 1, -8, 8, 4, -8, Blocks.SPRUCE_FENCE.defaultBlockState());
		c.set(8, 4, -7, Blocks.ORANGE_WOOL);
		c.set(8, 4, -6, Blocks.WHITE_WOOL);
		// crates
		c.set(-7, 1, 7, Blocks.BARREL);
		c.set(-6, 1, 7, Blocks.BARREL);
		c.set(-7, 2, 7, Blocks.SPRUCE_PLANKS);
		c.set(-7, 1, 6, Blocks.SPRUCE_PLANKS);
		c.spawnPoint(-6, 1, 5);
		c.spawnPoint(6, 1, -7);
		guard(c, Kind.SERGEANT, SERGEANT.getX(), SERGEANT.getY(), SERGEANT.getZ(), Direction.NORTH, 5, null);
		guard(c, Kind.TROOPER, -4, 1, -5, Direction.NORTH, 6, null);
		guard(c, Kind.TROOPER, -5, 1, 4, Direction.EAST, 5, GuardRole.RUNNER);
		guard(c, Kind.RIFLE, 8, 6, 0, Direction.WEST, 2, GuardRole.SNIPER);
	}

	private void shack(HideoutCanvas c) {
		c.fill(5, 1, -4, 10, 4, 4, Blocks.GRAY_CONCRETE.defaultBlockState());
		c.fill(5, 5, -4, 10, 5, 4, Blocks.POLISHED_ANDESITE.defaultBlockState());
		c.air(6, 1, -3, 9, 4, 3);
		for (int z = -4; z <= 4; z++) {
			c.set(5, 3, z, z == 0 ? Blocks.GRAY_CONCRETE : Blocks.BLUE_CONCRETE);
			c.set(10, 3, z, Blocks.BLUE_CONCRETE);
		}
		c.set(5, 3, -2, Blocks.GRAY_STAINED_GLASS);
		c.set(5, 3, 2, Blocks.GRAY_STAINED_GLASS);
		c.air(5, 1, 0, 5, 2, 0);
		c.set(5, 4, 0, Blocks.SEA_LANTERN);
		c.alarmPanel(9, 1, 0, Direction.WEST);
		c.set(9, 1, -2, Blocks.SMOOTH_QUARTZ);
		c.set(9, 1, -3, Blocks.SMOOTH_QUARTZ);
		c.container(9, 1, 3, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier2"));
		c.set(7, 4, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		c.camera(4, 4, -3, Direction.WEST);
		c.alarmLamp(7, 4, -5);
		// roof rail for the sniper
		for (int z = -4; z <= 4; z++) {
			c.set(10, 6, z, Blocks.IRON_BARS);
		}
		c.fill(10, 1, 5, 10, 5, 5, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
	}

	private static void guard(HideoutCanvas c, Kind kind, int x, int y, int z, Direction facing, int leash, GuardRole role) {
		BlockPos p = c.at(x, y, z);
		if (!c.inClip(p)) {
			return;
		}
		SkyFortressGuards.spawn(c.level, kind, p, c.yaw(facing), leash, role);
	}
}
