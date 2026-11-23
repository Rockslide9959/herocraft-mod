package com.projecthero.mod.hideout.worldgen;

import static com.projecthero.mod.hideout.HideoutGuards.Role.HOUND;
import static com.projecthero.mod.hideout.HideoutGuards.Role.RAIDER;
import static com.projecthero.mod.hideout.HideoutGuards.Role.SHAMAN;

import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.alarm.block.AlarmStyle;
import com.projecthero.mod.hideout.HideoutEntityTypes;
import com.projecthero.mod.hideout.block.HideoutVentBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * v0.17.5: the <b>Frost Giant Hall</b> -- a Norse longhall of dark timber and blue ice on the snow, big enough for
 * giants: a steep gabled roof (ridge 15 up), log pillars and cross-beams, soul-lantern chandeliers, a long fire down the
 * middle, benches and tables, and at the far end a raised dais with the Jarl's ice throne.
 * <ul>
 * <li><b>The vestibule</b>: two rows of pressure plates over floor dispensers -- arrows from below.</li>
 * <li><b>The side door</b>: tripwired to a war horn (alarm: more Rime Hounds).</li>
 * <li><b>The dais</b>: the Hideout Core on a blue-ice pedestal, the sealed Hideout Vault by the throne; the Frost Giant
 * Jarl holds the floor in front of it.</li>
 * </ul>
 * y = 0 is the hall floor (the surface).
 */
public final class FrostHallBuilder extends HideoutBuilder {
	public static final FrostHallBuilder INSTANCE = new FrostHallBuilder();

	// v0.17.7: the Jarl's Barrow (crawl wing) under the hall; its stairwell opens in the dais beside the throne
	static final int YTOP = 2;
	static final int AX = -5;
	static final int AY = -16;
	static final int AZ = 14 + (YTOP - AY);
	static final BlockPos CORE = CrawlWing.core(AX, AY, AZ);
	static final BlockPos VAULT = CrawlWing.vault(AX, AY, AZ);
	static final CrawlTheme THEME = theme();

	private static CrawlTheme theme() {
		CrawlTheme t = new CrawlTheme();
		t.wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		t.floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		t.ceiling = Blocks.PACKED_ICE.defaultBlockState();
		t.trim = Blocks.PACKED_ICE.defaultBlockState();
		t.pillar = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
		t.light = Blocks.SEA_LANTERN.defaultBlockState();
		t.accent = Blocks.BLUE_ICE.defaultBlockState();
		t.stairs = Blocks.DEEPSLATE_BRICK_STAIRS;
		t.vent = HideoutVentBlock.Kind.FROST;
		t.tech = false;
		t.style = AlarmStyle.ANCIENT;
		t.grunt = RAIDER;
		t.elite = RAIDER;
		t.ranged = SHAMAN;
		t.shield = RAIDER;
		t.captain = RAIDER;
		t.runner = HOUND;
		t.sniper = SHAMAN;
		t.boss = HideoutEntityTypes.FROST_GIANT_JARL;
		t.decor = new CrawlTheme.Decor() {
			@Override
			public void checkpoint(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				for (int[] p : new int[][] { { x0, z1 }, { x1, z0 + 2 } }) {
					c.set(p[0], y + 1, p[1], Blocks.CHISELED_STONE_BRICKS);
					c.set(p[0], y + 2, p[1], Blocks.SOUL_LANTERN);
				}
				c.fill(x0, y + 1, z0, x0, y + 2, z0 + 2, Blocks.SPRUCE_FENCE.defaultBlockState());
			}

			@Override
			public void quarters(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the huscarl's hall: a long table, shields on the walls, a fur bed
				int cx = x0 + 5;
				c.fill(cx - 2, y + 1, z0 + 5, cx + 2, y + 1, z0 + 5, Blocks.SPRUCE_PLANKS.defaultBlockState());
				c.fill(cx - 2, y + 2, z0 + 5, cx + 2, y + 2, z0 + 5, Blocks.WHITE_CARPET.defaultBlockState());
				for (int z = z0 + 1; z <= z1; z += 2) {
					c.set(x0 - 1, y + 3, z, Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
				}
				c.fill(x0, y + 1, z1 - 1, x0 + 1, y + 1, z1, Blocks.WHITE_WOOL.defaultBlockState());
			}

			@Override
			public void arena(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				// the barrow hall: ice spikes, the bones of old giants, a runestone ring
				int cx = (x0 + x1) / 2;
				int cz = (z0 + z1) / 2;
				for (int i = 0; i < 8; i++) {
					double a = i * Math.PI / 4;
					int x = cx + (int) Math.round(Math.cos(a) * 7);
					int z = cz + (int) Math.round(Math.sin(a) * 7);
					if (Math.abs(x - cx) == 6 && Math.abs(z - cz) >= 4) {
						continue;
					}
					c.fill(x, y + 1, z, x, y + 2, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
				}
				for (int[] p : new int[][] { { x0, z0 + 2 }, { x1, z0 + 4 }, { x0, z1 - 1 }, { x1, z1 - 6 } }) {
					c.fill(p[0], y + 1, p[1], p[0], y + 3 + (int) (c.noise(p[0], p[1], 60) * 3), p[1], Blocks.PACKED_ICE.defaultBlockState());
				}
				c.fill(x0 + 1, y + 1, z1, x0 + 3, y + 1, z1, Blocks.BONE_BLOCK.defaultBlockState());
				c.set(x0 + 2, y + 2, z1, Blocks.SKELETON_SKULL);
			}

			@Override
			public void vault(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
				for (int z = z0; z <= z1; z += 2) {
					c.set(x0, y + 1, z, Blocks.BLUE_ICE);
					c.set(x1, y + 1, z, Blocks.BLUE_ICE);
				}
			}
		};
		return t;
	}

	private FrostHallBuilder() {
		super(AX - CrawlWing.HALF_WIDTH, AY - 3, -23, 12, 17, AZ + CrawlWing.END_Z);
	}

	@Override
	public BlockPos coreLocal() {
		return CORE;
	}

	@Override
	public BlockPos vaultLocal() {
		return VAULT;
	}

	/** Roof height over local x (the gable). */
	static int roofY(int x) {
		return 8 + (11 - Math.min(11, Math.abs(x))) * 7 / 11;
	}

	private static BlockState stair(net.minecraft.world.level.block.Block b, Direction facing, Half half) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
	}

	@Override
	public void build(HideoutCanvas c) {
		BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState dark = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		BlockState post = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
		BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
		BlockState blue = Blocks.BLUE_ICE.defaultBlockState();
		BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();

		// ------------------------------------------------ ground
		c.air(-12, 1, -23, 12, 17, 22);
		for (int x = -12; x <= 12; x++) {
			for (int z = -23; z <= 22; z++) {
				c.footing(x, -1, z, 10, stone);
				boolean hall = Math.abs(x) <= 10 && z >= -20 && z <= 20;
				if (!hall) {
					c.set(x, 0, z, c.noise(x, z, 1) < 0.3 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
				}
			}
		}
		// the hall floor: spruce, a stone border, an ice path down the middle
		c.fill(-10, 0, -20, 10, 0, 20, plank);
		c.walls(-10, 0, -20, 10, 0, 20, stone);
		c.fill(-2, 0, -19, 2, 0, 11, Blocks.POLISHED_DIORITE.defaultBlockState());
		// approach: an ice path and two standing stones
		c.fill(-1, 0, -23, 1, 0, -21, ice);
		for (int x : new int[] { -4, 4 }) {
			c.fill(x, 1, -22, x, 3, -22, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			c.set(x, 4, -22, Blocks.SOUL_LANTERN);
		}

		// ------------------------------------------------ walls: dark posts, spruce panels over an ice footing band
		for (int y = 1; y <= 7; y++) {
			for (int x = -10; x <= 10; x++) {
				for (int z : new int[] { -20, 20 }) {
					c.set(x, y, z, x % 5 == 0 ? post : y == 1 ? ice : plank);
				}
			}
			for (int z = -20; z <= 20; z++) {
				for (int x : new int[] { -10, 10 }) {
					c.set(x, y, z, z % 5 == 0 ? post : y == 1 ? ice : plank);
				}
			}
		}
		// small blue-glass windows
		for (int z = -15; z <= 15; z += 5) {
			for (int x : new int[] { -10, 10 }) {
				if (z != 5 || x != 10) {
					c.fill(x, 4, z + 2, x, 5, z + 3, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
				}
			}
		}
		// the great doors (open) and the side door
		c.air(-1, 1, -20, 1, 4, -20);
		c.fill(-2, 5, -20, 2, 5, -20, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		c.air(10, 1, 4, 10, 2, 4);
		c.door(10, 1, 4, Blocks.SPRUCE_DOOR, Direction.WEST);

		// ------------------------------------------------ the roof (gable along z) and its end walls
		for (int x = -11; x <= 11; x++) {
			int ry = roofY(x);
			for (int z = -21; z <= 21; z++) {
				BlockState s = x == 0 ? Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)
						: stair(Blocks.DARK_OAK_STAIRS, x < 0 ? Direction.EAST : Direction.WEST, Half.BOTTOM);
				c.set(x, ry, z, s);
				if (x != 0 && roofY(x + Integer.signum(-x)) > ry + 1) {
					c.set(x, ry + 1, z, dark);
				}
				if (Math.abs(x) <= 10 && (z == -20 || z == 20)) {
					c.fill(x, 8, z, x, ry - 1, z, x % 5 == 0 ? post : plank);
				}
			}
			if (Math.abs(x) <= 1) {
				c.fill(x, ry + 1, -21, x, ry + 1, 21, Blocks.SNOW_BLOCK.defaultBlockState());
			}
		}
		// gable-end round windows and crossed "dragon" posts on the ridge ends
		c.fill(-1, 10, -20, 1, 11, -20, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
		c.fill(-1, 10, 20, 1, 11, 20, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
		for (int z : new int[] { -22, 22 }) {
			c.set(-1, roofY(0) + 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
			c.set(1, roofY(0) + 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
			c.set(-1, roofY(0) + 2, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
			c.set(1, roofY(0) + 2, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
		}

		// ------------------------------------------------ inside: pillars, beams, chandeliers, the long fire, benches
		for (int z = -15; z <= 15; z += 5) {
			for (int x : new int[] { -6, 6 }) {
				c.fill(x, 1, z, x, 8, z, post);
			}
			c.fill(-9, 8, z, 9, 8, z, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		}
		for (int z : new int[] { -10, 0, 10 }) {
			c.fill(0, 11, z, 0, roofY(0) - 1, z, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
			c.set(0, 10, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			c.set(-1, 10, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
			c.set(1, 10, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
			c.set(-1, 9, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
			c.set(1, 9, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
		}
		// the long fire: soul campfires in a stone trough
		for (int z = -12; z <= 6; z++) {
			c.set(-1, 0, z, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
			c.set(1, 0, z, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
			c.set(0, 0, z, Blocks.MAGMA_BLOCK);
			if (z % 3 == 0) {
				c.set(0, 1, z, Blocks.SOUL_CAMPFIRE);
			}
		}
		// benches and tables along the walls
		for (int z = -18; z <= 10; z++) {
			if (z % 5 == 0) {
				continue;
			}
			for (int side : new int[] { -1, 1 }) {
				c.set(side * 9, 1, z, Blocks.SPRUCE_SLAB.defaultBlockState());
				if (z % 5 == 2 || z % 5 == -3) {
					c.set(side * 8, 1, z, plank);
					c.set(side * 8, 2, z, Blocks.WHITE_CARPET.defaultBlockState());
				}
			}
		}
		c.container(-9, 1, -19, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));
		c.container(9, 1, 11, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier1"));

		// ------------------------------------------------ the vestibule trap: plates over floor dispensers firing up
		for (int z : new int[] { -18, -16 }) {
			for (int x = -1; x <= 1; x++) {
				c.arrowDispenser(x, 0, z, Direction.UP);
				c.set(x, 1, z, Blocks.SPRUCE_PRESSURE_PLATE);
			}
		}
		// the side door's war horn
		c.set(9, 1, 7, Blocks.BARREL);
		c.tripwireZ(2, 6, 1, 9);

		// ------------------------------------------------ the dais and the throne
		c.fill(-9, 1, 13, 9, 2, 19, stone);
		c.fill(-9, 2, 13, 9, 2, 19, ice);
		for (int x = -9; x <= 9; x++) {
			c.set(x, 1, 12, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, Half.BOTTOM));
			c.set(x, 2, 13, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, Half.BOTTOM));
		}
		c.set(0, 3, 14, blue);
		c.set(0, 4, 14, Blocks.SOUL_LANTERN);
		// the ice throne
		c.set(0, 3, 18, stair(Blocks.QUARTZ_STAIRS, Direction.NORTH, Half.BOTTOM));
		c.fill(-1, 3, 19, 1, 6, 19, blue);
		c.set(-1, 3, 18, ice);
		c.set(1, 3, 18, ice);
		c.set(-1, 7, 19, Blocks.ICE);
		c.set(1, 7, 19, Blocks.ICE);
		// the sealed vault and a weapon rack beside the throne
		c.container(5, 3, 19, Blocks.BARREL.defaultBlockState(), HideoutCanvas.table("chests/vault_tier2"));
		c.fill(-5, 3, 19, -5, 4, 19, Blocks.SPRUCE_FENCE.defaultBlockState());
		c.set(-6, 3, 19, Blocks.ANVIL);
		for (int x : new int[] { -7, 7 }) {
			c.set(x, 3, 14, Blocks.SOUL_CAMPFIRE);
		}

		// ------------------------------------------------ the household
		c.guard(HOUND, -3, 1, -8, Direction.NORTH, 6);
		c.guard(HOUND, 3, 1, -6, Direction.NORTH, 6, GuardRole.RUNNER);
		c.guard(HOUND, -3, 1, -2, Direction.NORTH, 6);
		c.guard(RAIDER, -7, 1, -3, Direction.EAST, 5, GuardRole.SHIELD);
		c.guard(RAIDER, 7, 1, 3, Direction.WEST, 5);
		c.guard(SHAMAN, 4, 3, 17, Direction.NORTH, 3, GuardRole.SNIPER);
		c.guard(SHAMAN, 5, 1, -14, Direction.NORTH, 4, GuardRole.MEDIC);
		c.guard(RAIDER, 0, 1, 9, Direction.NORTH, 6);
		// v0.17.7 outer defences: the ward stone (Alarm Panel #1) in the vestibule, braziers that light on Alert
		CrawlWing.panel(c, THEME, -9, 1, -17, Direction.EAST);
		for (int[] p : new int[][] { { -9, -10 }, { 9, -10 }, { -9, 5 }, { 9, 5 } }) {
			CrawlWing.lampAt(c, THEME, p[0], 1, p[1]);
		}
		c.spawnPoint(-4, 1, -12);
		c.spawnPoint(4, 1, -12);
		c.spawnPoint(0, 1, 8);
		watchtower(c, post, plank);

		// ------------------------------------------------ v0.17.7: the Jarl's Barrow and its stairwell
		CrawlWing.build(c, THEME, AX, AY, AZ);
		CrawlWing.stairwell(c, THEME, AX, AY, AZ, YTOP);
	}

	/** v0.17.7: a log watchtower west of the hall (x -16..-13, z -19..-16) with a Shaman sniper on its platform. */
	private void watchtower(HideoutCanvas c, BlockState post, BlockState plank) {
		for (int x = -16; x <= -13; x++) {
			for (int z = -19; z <= -16; z++) {
				c.footing(x, -1, z, 10, Blocks.STONE_BRICKS.defaultBlockState());
				c.set(x, 0, z, Blocks.SNOW_BLOCK);
				c.air(x, 1, z, x, 14, z);
			}
		}
		for (int[] l : new int[][] { { -16, -19 }, { -13, -19 }, { -16, -16 }, { -13, -16 } }) {
			c.fill(l[0], 1, l[1], l[0], 10, l[1], post);
		}
		c.fill(-16, 8, -19, -13, 8, -16, plank);
		for (int x = -16; x <= -13; x++) {
			c.set(x, 9, -19, Blocks.SPRUCE_FENCE.defaultBlockState());
			c.set(x, 9, -16, Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		c.fill(-16, 11, -19, -13, 11, -16, Blocks.DARK_OAK_SLAB.defaultBlockState());
		for (int y = 1; y <= 8; y++) {
			c.set(-14, y, -15, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
		}
		c.fill(-14, 1, -16, -14, 7, -16, plank);
		CrawlWing.lampAt(c, THEME, -15, 9, -17);
		c.guard(SHAMAN, -14, 9, -18, Direction.NORTH, 1, GuardRole.SNIPER);
	}
}
