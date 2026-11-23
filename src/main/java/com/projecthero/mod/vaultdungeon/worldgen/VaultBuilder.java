package com.projecthero.mod.vaultdungeon.worldgen;

import static com.projecthero.mod.vaultdungeon.VaultGuards.Role.GUNNER;
import static com.projecthero.mod.vaultdungeon.VaultGuards.Role.INMATE;
import static com.projecthero.mod.vaultdungeon.VaultGuards.Role.WARDEN;

import java.util.List;

import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.block.AlarmPanelBlock;
import com.projecthero.mod.hideout.worldgen.HideoutCanvas;
import com.projecthero.mod.vaultdungeon.block.VaultBarrierBlock;
import com.projecthero.mod.vaultdungeon.block.VaultBreakerBlock;
import com.projecthero.mod.vaultdungeon.entity.RiotCaptainBoss;
import com.projecthero.mod.vaultdungeon.VaultEntityTypes;
import com.projecthero.mod.vaultdungeon.VaultGuards;
import com.projecthero.mod.vaultdungeon.block.VaultAlarmBlock;
import com.projecthero.mod.vaultdungeon.block.VaultBlocks;
import com.projecthero.mod.vaultdungeon.block.VaultConsoleBlock;
import com.projecthero.mod.vaultdungeon.block.VaultSealBlock;
import com.projecthero.mod.vaultdungeon.entity.WardenKrossBoss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * v0.17.5: The Vault's layout, drawn in local coordinates on a {@link HideoutCanvas} (entrance to local north,
 * {@code y = 0} = the surface under the guard post). Everything stays inside {@link #minX}..{@link #maxZ} (the piece's
 * box); the canvas clips each write to the chunk being generated, and mobs are only spawned by the chunk holding their
 * feet. No block entity gets custom data during worldgen except loot-table containers (the vanilla
 * {@code setLootTable} path); the turret's block entity is created by the chunk itself.
 * <ul>
 * <li><b>Surface</b>: a fenced guard post (gray concrete, iron-framed blast doorway, barred windows, floodlights) over
 * a ladder shaft; two riot wardens at the gate.</li>
 * <li><b>Floor 1, the Cell Block</b> ({@code y -14}): a two-tier hall of 28 barred cells around a glass guard tower
 * (the shaft comes down inside it); many cell doors are broken open -- escaped inmates roam the hall and the
 * galleries, two riot wardens and a gunner hold the floor. A stair tunnel in the south-east corner leads down.</li>
 * <li><b>Floor 2, the Security Floor</b> ({@code y -28}): a white checkpoint corridor crossed by two laser tripwires
 * (each calls a riot squad), a turret hall with four ceiling turrets and cover, the security office (console: opens
 * the lockdown gate) and the gate itself -- a pair of iron doors to the next stair.</li>
 * <li><b>Floor 3, the Warden's Level</b> ({@code y -44}): the processing room (riot wardens, a locker of Tier 3 loot),
 * the arena of Warden Kross (blackstone, red ring, four pillars, his dais; both doorways marked with lockdown track)
 * and behind it the vault room with three sealed Dungeon Vaults.</li>
 * </ul>
 */
public final class VaultBuilder {
	public static final VaultBuilder INSTANCE = new VaultBuilder();

	public static final int F1 = -14;
	public static final int F2 = -28;
	public static final int F3 = -44;
	/** The arena centre: Warden Kross's home and the clearing anchor. */
	public static final BlockPos ANCHOR = new BlockPos(0, F3 + 1, 5);
	/** Where Kross stands when the dungeon generates. */
	public static final BlockPos BOSS = new BlockPos(0, F3 + 1, 10);
	public static final List<BlockPos> VAULTS = List.of(new BlockPos(-3, F3 + 1, 24), new BlockPos(0, F3 + 1, 24), new BlockPos(3, F3 + 1, 24));
	/** The security console (opens the lockdown gate). */
	/** v0.17.5: the sealed O.X.E. serum vat (Sentry), in the middle of the vault room. */
	public static final BlockPos SERUM_VAT = new BlockPos(0, F3 + 1, 21);
	public static final BlockPos CONSOLE = new BlockPos(6, F2 + 1, 19);
	/** The two halves of the lockdown gate (lower door blocks). */
	public static final List<BlockPos> GATE = List.of(new BlockPos(-1, F2 + 1, -7), new BlockPos(0, F2 + 1, -7));
	public static final List<BlockPos> TURRETS = List.of(new BlockPos(-7, F2 + 6, -2), new BlockPos(7, F2 + 6, -2),
			new BlockPos(-7, F2 + 6, 5), new BlockPos(7, F2 + 6, 5));
	public static final List<BlockPos> LASERS = List.of(new BlockPos(-9, F2 + 1, 18), new BlockPos(-9, F2 + 1, 13));

	// ---------------------------------------------------------------- v0.17.7 (layout 2): the two new floors
	/** The layout every newly generated Vault gets (pieces saved before v0.17.7 load as layout 1 and keep the old plan). */
	public static final int LAYOUT = 2;
	/** The keycard Blast Door from the gate landing into the Maximum Security wing (one panel of it). */
	public static final BlockPos MAXSEC_DOOR = new BlockPos(-1, F2 + 1, -12);
	/** The Riot Captain's post: the middle of the Maximum Security hall. */
	public static final BlockPos RIOT_CAPTAIN = new BlockPos(0, F2 + 1, -44);
	/** The red field (Riot Captain) on the door from the hall to the Control Room (one block of it). */
	public static final BlockPos CAPTAIN_FIELD = new BlockPos(0, F2 + 1, -52);
	/** The cell-block breaker in the Warden's Control Room. */
	public static final BlockPos BREAKER = new BlockPos(0, F2 + 1, -62);
	/** The blue field (cell-block power) across the way out of the Control Room (one block of it). */
	public static final BlockPos POWER_FIELD = new BlockPos(-9, F2 + 1, -57);
	/** The eight Maximum Security pods (an inmate's feet in each). */
	public static final List<BlockPos> PODS = List.of(new BlockPos(-10, F2 + 1, -49), new BlockPos(-10, F2 + 1, -45), new BlockPos(-10, F2 + 1, -41),
			new BlockPos(-10, F2 + 1, -37), new BlockPos(9, F2 + 1, -49), new BlockPos(9, F2 + 1, -45), new BlockPos(9, F2 + 1, -41), new BlockPos(9, F2 + 1, -37));

	public final int minX = -28;
	public final int minY = -47;
	public final int minZ = -66;
	public final int maxX = 13;
	public final int maxY = 9;
	public final int maxZ = 26;

	private VaultBuilder() {
		this(true);
	}

	private VaultBuilder(boolean v2) {
		this.v2 = v2;
	}

	// ---------------------------------------------------------------- palettes

	private static BlockState s(Block b) {
		return b.defaultBlockState();
	}

	private static BlockState cellWall(HideoutCanvas c, int x, int y, int z) {
		double n = c.noise(x, y, z, 21);
		return n < 0.12 ? s(Blocks.CRACKED_STONE_BRICKS) : n < 0.2 ? s(Blocks.MOSSY_STONE_BRICKS) : s(Blocks.STONE_BRICKS);
	}

	private static BlockState securityWall(HideoutCanvas c, int x, int y, int z) {
		return c.noise(x, y, z, 22) < 0.15 ? s(Blocks.LIGHT_GRAY_CONCRETE) : s(Blocks.WHITE_CONCRETE);
	}

	private static BlockState wardenWall(HideoutCanvas c, int x, int y, int z) {
		double n = c.noise(x, y, z, 23);
		return n < 0.18 ? s(Blocks.CRACKED_DEEPSLATE_TILES) : n < 0.6 ? s(Blocks.DEEPSLATE_TILES) : s(Blocks.POLISHED_DEEPSLATE);
	}

	private interface Palette {
		BlockState at(HideoutCanvas c, int x, int y, int z);
	}

	/** Fills the box (inclusive) from a palette. */
	private static void fillP(HideoutCanvas c, int x0, int y0, int z0, int x1, int y1, int z1, Palette p) {
		c.fill(x0, y0, z0, x1, y1, z1, a -> p.at(c, a[0], a[1], a[2]));
	}

	/** A solid box of {@code wall} with the inside carved out (walls, floor and ceiling one block thick). */
	private static void shell(HideoutCanvas c, int x0, int y0, int z0, int x1, int y1, int z1, Palette wall) {
		fillP(c, x0, y0, z0, x1, y1, z1, wall);
		c.air(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1);
	}

	private static BlockState bars(boolean alongX) {
		BlockState b = s(Blocks.IRON_BARS);
		return alongX ? b.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
				: b.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
	}

	private static BlockState pane(Block b, boolean alongX) {
		BlockState p = s(b);
		return alongX ? p.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
				: p.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
	}

	private static BlockState stair(Block b, Direction facing) {
		return s(b).setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	private static void ironDoor(HideoutCanvas c, int x, int y, int z, Direction facing, DoorHingeSide hinge) {
		BlockState lower = s(Blocks.IRON_DOOR).setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		c.set(x, y, z, lower);
		c.set(x, y + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	// ---------------------------------------------------------------- mobs

	/**
	 * Layout 2 is drawing (the v0.17.7 floors, alarm blocks, alarm guards). Final per builder object: worldgen draws
	 * chunks on several threads, so {@link #build(HideoutCanvas, int)} makes a fresh builder for each call.
	 */
	private final boolean v2;

	private void guard(HideoutCanvas c, VaultGuards.Role role, int x, int y, int z, Direction facing, int leash) {
		guard(c, role, x, y, z, facing, leash, null);
	}

	/** A guard; in layout 2 an alarm guard with {@code alarmRole} (inmates are never guards of the place). */
	private void guard(HideoutCanvas c, VaultGuards.Role role, int x, int y, int z, Direction facing, int leash, GuardRole alarmRole) {
		BlockPos p = c.at(x, y, z);
		if (c.inClip(p)) {
			boolean alarm = v2 && role != INMATE;
			VaultGuards.spawn(c.level, role, p, c.yaw(facing), leash, alarm, alarm ? alarmRole : null);
		}
	}

	/** A Maximum Security pod inmate (tagged so the power cut lets him out). */
	private static void podInmate(HideoutCanvas c, int x, int y, int z, Direction facing) {
		BlockPos p = c.at(x, y, z);
		if (c.inClip(p)) {
			VaultGuards.spawn(c.level, INMATE, p, c.yaw(facing), 1, false, null, com.projecthero.mod.vaultdungeon.VaultSecurity.POD_TAG);
		}
	}

	private static void riotCaptain(HideoutCanvas c) {
		BlockPos p = c.at(RIOT_CAPTAIN.getX(), RIOT_CAPTAIN.getY(), RIOT_CAPTAIN.getZ());
		if (!c.inClip(p)) {
			return;
		}
		RiotCaptainBoss rc = VaultEntityTypes.RIOT_CAPTAIN.create(c.level.getLevel());
		if (rc == null) {
			return;
		}
		float yaw = c.yaw(Direction.SOUTH);
		rc.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0f);
		rc.setYHeadRot(yaw);
		rc.setYBodyRot(yaw);
		rc.finalizeSpawn(c.level, c.level.getCurrentDifficultyAt(p), MobSpawnType.STRUCTURE, null);
		rc.setHome(p);
		rc.setPersistenceRequired();
		c.level.addFreshEntityWithPassengers(rc);
	}

	// ---------------------------------------------------------------- v0.17.7 alarm blocks (tech style)

	private static void panel(HideoutCanvas c, int x, int y, int z, Direction facing) {
		c.set(x, y, z, AlarmBlocks.PANEL.defaultBlockState().setValue(AlarmPanelBlock.FACING, facing));
	}

	private static void lamp(HideoutCanvas c, int x, int y, int z) {
		c.alarmLamp(x, y, z);
	}

	private static void barrier(HideoutCanvas c, int x0, int y0, int z0, int x1, int y1, int z1, VaultBarrierBlock.Kind kind) {
		c.fill(x0, y0, z0, x1, y1, z1, s(VaultBlocks.BARRIER).setValue(VaultBarrierBlock.KIND, kind));
	}

	private static void boss(HideoutCanvas c) {
		BlockPos p = c.at(BOSS.getX(), BOSS.getY(), BOSS.getZ());
		if (!c.inClip(p)) {
			return;
		}
		WardenKrossBoss kross = VaultEntityTypes.WARDEN_KROSS.create(c.level.getLevel());
		if (kross == null) {
			return;
		}
		float yaw = c.yaw(Direction.NORTH);
		kross.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0f);
		kross.setYHeadRot(yaw);
		kross.setYBodyRot(yaw);
		kross.finalizeSpawn(c.level, c.level.getCurrentDifficultyAt(p), MobSpawnType.STRUCTURE, null);
		kross.setHome(c.at(ANCHOR.getX(), ANCHOR.getY(), ANCHOR.getZ()));
		kross.setPersistenceRequired();
		c.level.addFreshEntityWithPassengers(kross);
	}

	// ---------------------------------------------------------------- build

	/** Draws the current layout ({@link #LAYOUT}). */
	public void build(HideoutCanvas c) {
		build(c, LAYOUT);
	}

	/** Draws {@code layout} (1 = the v0.17.5 three floors, 2 = v0.17.7 with the two new floors and the alarm). */
	public void build(HideoutCanvas c, int layout) {
		new VaultBuilder(layout >= 2).draw(c);
	}

	private void draw(HideoutCanvas c) {
		surface(c);
		cellBlock(c);
		shaft(c);
		stairs1to2(c);
		securityFloor(c);
		stairs2to3(c);
		if (v2) {
			maxSecurity(c);
			controlRoom(c);
			descent(c);
		}
		wardensLevel(c);
		if (v2) {
			frames(c);
		}
	}

	/** v0.17.7: the three doorways of the new floors -- frames, then the door / fields -- over the shared walls. */
	private static void frames(HideoutCanvas c) {
		int y0 = F2 + 1;
		// gate landing -> Maximum Security: the keycard Blast Door in a yellow-banded iron frame
		c.fill(-2, y0, -12, 1, y0 + 3, -12, s(Blocks.IRON_BLOCK));
		c.fill(-2, y0 + 3, -12, 1, y0 + 3, -12, s(Blocks.YELLOW_CONCRETE));
		c.blastDoor(MAXSEC_DOOR.getX(), MAXSEC_DOOR.getY(), MAXSEC_DOOR.getZ(), MAXSEC_DOOR.getX() + 1, MAXSEC_DOOR.getY() + 2, MAXSEC_DOOR.getZ(), true);
		// hall -> Control Room: the Riot Captain's red field
		c.fill(-2, y0, -52, 1, y0 + 3, -52, s(Blocks.IRON_BLOCK));
		c.fill(-2, y0 + 3, -52, 1, y0 + 3, -52, s(Blocks.RED_CONCRETE));
		barrier(c, -1, y0, -52, 0, y0 + 2, -52, VaultBarrierBlock.Kind.CAPTAIN);
		// Control Room -> the way down: the cell-block power's blue field
		c.fill(-9, y0, -59, -9, y0 + 3, -56, s(Blocks.IRON_BLOCK));
		c.fill(-9, y0 + 3, -59, -9, y0 + 3, -56, s(Blocks.LIGHT_BLUE_CONCRETE));
		barrier(c, -9, y0, -58, -9, y0 + 2, -57, VaultBarrierBlock.Kind.POWER);
	}

	/** The guard post on the surface, the shaft through the rock below it. */
	private void surface(HideoutCanvas c) {
		// the pad: footing under, terrain and trees cleared above
		for (int x = -11; x <= 11; x++) {
			for (int z = -11; z <= 11; z++) {
				c.footing(x, -1, z, 10, s(Blocks.DIRT));
				double n = c.noise(x, z, 1);
				c.set(x, 0, z, n < 0.45 ? s(Blocks.GRAVEL) : n < 0.8 ? s(Blocks.COARSE_DIRT) : s(Blocks.ANDESITE));
				c.air(x, 1, z, x, 9, z);
			}
		}
		// the chain-link fence (gap at the north gate) and the floodlights
		for (int i = -10; i <= 10; i++) {
			for (int y = 1; y <= 2; y++) {
				if (Math.abs(i) > 2) {
					c.set(i, y, -10, bars(true));
				}
				c.set(i, y, 10, bars(true));
				c.set(-10, y, i, bars(false));
				c.set(10, y, i, bars(false));
			}
		}
		for (int[] p : new int[][] { { -10, -10 }, { 10, -10 }, { -10, 10 }, { 10, 10 }, { -3, -10 }, { 3, -10 } }) {
			c.fill(p[0], 1, p[1], p[0], 3, p[1], s(Blocks.POLISHED_ANDESITE));
			c.set(p[0], 4, p[1], s(Blocks.SEA_LANTERN));
		}
		// the guard post
		c.fill(-6, 0, -6, 6, 6, 6, s(Blocks.GRAY_CONCRETE));
		c.air(-5, 1, -5, 5, 5, 5);
		c.fill(-5, 0, -5, 5, 0, 5, a -> (a[0] + a[2]) % 2 == 0 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.ANDESITE));
		for (int[] p : new int[][] { { -6, -6 }, { 6, -6 }, { -6, 6 }, { 6, 6 } }) {
			c.fill(p[0], 0, p[1], p[0], 7, p[1], s(Blocks.POLISHED_ANDESITE));
		}
		c.fill(-6, 6, -6, 6, 6, 6, s(Blocks.SMOOTH_STONE));
		for (int i = -6; i <= 6; i += 2) {
			c.set(i, 7, -6, s(Blocks.STONE_BRICKS));
			c.set(i, 7, 6, s(Blocks.STONE_BRICKS));
			c.set(-6, 7, i, s(Blocks.STONE_BRICKS));
			c.set(6, 7, i, s(Blocks.STONE_BRICKS));
		}
		c.fill(-6, 5, -6, 6, 5, -6, s(Blocks.ORANGE_CONCRETE)); // the warning band
		c.fill(-6, 5, 6, 6, 5, 6, s(Blocks.ORANGE_CONCRETE));
		c.fill(-6, 5, -6, -6, 5, 6, s(Blocks.ORANGE_CONCRETE));
		c.fill(6, 5, -6, 6, 5, 6, s(Blocks.ORANGE_CONCRETE));
		// the blast doorway (north), iron-framed, hazard track on the sill
		c.fill(-2, 1, -6, 2, 4, -6, s(Blocks.IRON_BLOCK));
		c.air(-1, 1, -6, 1, 3, -6);
		c.fill(-1, 0, -7, 1, 0, -6, s(VaultBlocks.TRACK));
		// barred windows
		for (int i : new int[] { -3, 3 }) {
			c.fill(i, 2, 6, i, 3, 6, bars(true));
			c.fill(-6, 2, i, -6, 3, i, bars(false));
			c.fill(6, 2, i, 6, 3, i, bars(false));
		}
		// inside: the monitor desk, lockers, light
		c.fill(-5, 1, 3, -5, 1, 5, s(Blocks.POLISHED_ANDESITE));
		c.fill(-5, 2, 3, -5, 2, 5, a -> a[2] == 4 ? pane(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, false) : s(Blocks.BLACK_CONCRETE));
		c.set(-4, 1, 4, stair(Blocks.SPRUCE_STAIRS, Direction.EAST));
		c.container(4, 1, -4, s(Blocks.BARREL), HideoutCanvas.table("chests/vault_tier1"));
		c.set(5, 1, -4, s(Blocks.BARREL));
		c.set(5, 1, 4, s(Blocks.CAULDRON));
		for (int[] p : new int[][] { { -3, -3 }, { 3, -3 }, { -3, 3 }, { 3, 3 } }) {
			c.set(p[0], 6, p[1], s(Blocks.SEA_LANTERN));
		}

		guard(c, WARDEN, -3, 1, -9, Direction.NORTH, 6);
		guard(c, WARDEN, 3, 1, -9, Direction.NORTH, 6);
		if (v2) {
			// v0.17.7: Alarm Panel #1 in the post (and its runner), cameras on the walls, lamps, spawn points in the yard
			panel(c, 5, 1, 1, Direction.WEST);
			c.camera(2, 4, -7, Direction.NORTH);
			c.camera(-7, 4, 3, Direction.WEST);
			lamp(c, 0, 6, -4);
			lamp(c, 0, 6, 4);
			c.spawnPoint(-8, 1, -8);
			c.spawnPoint(8, 1, -8);
			c.spawnPoint(8, 1, 8);
			guard(c, WARDEN, 3, 1, 3, Direction.WEST, 4, GuardRole.RUNNER);
		}
	}

	/**
	 * The shaft: an opening in the guard post's floor, railed on three sides, walled down through the rock to the Cell
	 * Block's ceiling (the guard tower walls it below that), the ladder on its north wall. Drawn after the Cell Block so
	 * the hall's carve doesn't erase the ladder.
	 */
	private void shaft(HideoutCanvas c) {
		c.air(-1, 0, -1, 1, 0, 1);
		c.fill(-2, 1, -1, -2, 1, 1, bars(false));
		c.fill(2, 1, -1, 2, 1, 1, bars(false));
		c.fill(-1, 1, 2, 1, 1, 2, bars(true));
		for (int y = -4; y <= -1; y++) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) == 2 || Math.abs(z) == 2) {
						c.set(x, y, z, s(Blocks.POLISHED_ANDESITE));
					}
				}
			}
		}
		c.air(-1, F1 + 1, -1, 1, -1, 1);
		for (int y = F1 + 1; y <= 0; y++) {
			c.set(0, y, -1, s(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.SOUTH));
		}
	}

	/** Floor 1: the Cell Block. */
	private void cellBlock(HideoutCanvas c) {
		int y0 = F1 + 1; // -13
		shell(c, -12, F1, -19, 12, -4, 19, VaultBuilder::cellWall);
		// floor: andesite checker with orange walkway lines; ceiling smooth stone with lights
		c.fill(-11, F1, -18, 11, F1, 18, a -> Math.abs(a[0]) == 4 ? s(Blocks.ORANGE_CONCRETE)
				: (a[0] + a[2]) % 2 == 0 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.GRAY_CONCRETE));
		c.fill(-11, -4, -18, 11, -4, 18, s(Blocks.SMOOTH_STONE));
		for (int x : new int[] { -8, 0, 8 }) {
			for (int z = -15; z <= 15; z += 6) {
				c.set(x, -4, z, s(Blocks.SEA_LANTERN));
			}
		}

		// the guard tower round the shaft: glass windows, orange band, a doorway south
		for (int y = y0; y <= -5; y++) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) != 2 && Math.abs(z) != 2) {
						continue;
					}
					BlockState b = s(Blocks.POLISHED_ANDESITE);
					boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
					if (y == -6) {
						b = s(Blocks.ORANGE_TERRACOTTA);
					} else if ((y == -11 || y == -10) && !corner && z != -2) {
						b = pane(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, Math.abs(z) == 2);
					}
					c.set(x, y, z, b);
				}
			}
		}
		c.air(0, y0, 2, 0, y0 + 1, 2);

		cells(c, -1);
		cells(c, 1);

		// a few barrels by the tower, a broken table
		c.container(3, y0, -3, s(Blocks.BARREL), HideoutCanvas.table("chests/vault_tier1"));
		c.set(-3, y0, 3, s(Blocks.BARREL));
		c.set(-4, y0, -12, s(Blocks.SMOOTH_STONE_SLAB));
		c.set(-3, y0, -12, s(Blocks.SMOOTH_STONE_SLAB));

		// the loose inmates, the wardens holding the floor
		guard(c, INMATE, -3, y0, -10, Direction.SOUTH, 8);
		guard(c, INMATE, 3, y0, -6, Direction.WEST, 8);
		guard(c, INMATE, -3, y0, 8, Direction.NORTH, 8);
		guard(c, INMATE, 4, y0, 12, Direction.NORTH, 8);
		guard(c, INMATE, -9, y0, -4, Direction.EAST, 6);
		guard(c, INMATE, 9, y0, 8, Direction.WEST, 6);
		guard(c, INMATE, -6, -9, 4, Direction.NORTH, 6);
		guard(c, INMATE, 6, -9, -8, Direction.SOUTH, 6);
		guard(c, WARDEN, 0, y0, 5, Direction.SOUTH, 8, GuardRole.SHIELD);
		guard(c, WARDEN, -4, y0, 16, Direction.NORTH, 8, GuardRole.RUNNER);
		guard(c, GUNNER, 6, -9, 10, Direction.WEST, 6, GuardRole.SNIPER);
		if (v2) {
			// v0.17.7: Alarm Panel #2 at the foot of the guard tower, cameras on the tower, lamps, spawn points, a medic
			panel(c, -3, y0, 0, Direction.WEST);
			c.camera(0, -8, -3, Direction.NORTH);
			c.camera(0, -8, 3, Direction.SOUTH);
			lamp(c, -4, -4, 0);
			lamp(c, 4, -4, 0);
			lamp(c, 0, -4, 12);
			c.spawnPoint(-3, y0, -16);
			c.spawnPoint(3, y0, -16);
			c.spawnPoint(3, y0, 16);
			guard(c, WARDEN, 2, y0, -14, Direction.SOUTH, 6, GuardRole.MEDIC);
		}
	}

	/** One side's two tiers of cells ({@code s} = -1 west, +1 east), the gallery and its stair. */
	private static void cells(HideoutCanvas c, int s) {
		int[] bases = { F1 + 1, -9 };
		// the upper floor (cells + gallery) and the upper cells' roof
		c.fill(s * 4, -10, -14, s * 11, -10, 14, s(Blocks.POLISHED_ANDESITE));
		c.fill(s * 4, -10, -15, s * 6, -10, -15, s(Blocks.POLISHED_ANDESITE));
		c.fill(s * 4, -10, 15, s * 6, -10, 15, s(Blocks.POLISHED_ANDESITE));
		c.fill(s * 7, -6, -14, s * 11, -6, 14, s(Blocks.STONE_BRICKS));
		for (int base : bases) {
			for (int z = -14; z <= 14; z += 4) {
				fillP(c, s * 8, base, z, s * 11, base + 2, z, VaultBuilder::cellWall);
				c.fill(s * 7, base, z, s * 7, base + 2, z, s(Blocks.POLISHED_ANDESITE));
			}
			for (int zc = -12; zc <= 12; zc += 4) {
				boolean broken = c.noise(s * 7, base, zc, 31) < 0.55;
				for (int dz = -1; dz <= 1; dz++) {
					BlockState front = dz == 0 && broken ? s(Blocks.AIR) : bars(false);
					c.fill(s * 7, base, zc + dz, s * 7, base + 2, zc + dz, front);
				}
				if (broken && base == F1 + 1) {
					c.set(s * 6, base, zc + (c.noise(zc, base, 32) < 0.5 ? -1 : 1), bars(false)); // a torn-off bar on the floor
				}
				c.set(s * 11, base, zc - 1, s(Blocks.CAULDRON));
				c.set(s * 10, base, zc - 1, s(Blocks.WHITE_CARPET));
				c.set(s * 10, base, zc, s(Blocks.WHITE_CARPET));
				c.set(s * 11, base, zc, s(Blocks.WHITE_CARPET));
				if (c.noise(zc, base, s, 33) < 0.3) {
					c.container(s * 11, base, zc + 1, s(Blocks.BARREL), HideoutCanvas.table("chests/vault_tier1"));
				}
			}
		}
		// the gallery railing and its stair (north end, rising south)
		c.fill(s * 4, -9, -15, s * 4, -9, 14, bars(false));
		c.fill(s * 5, -9, 15, s * 6, -9, 15, bars(true));
		for (int k = 0; k < 3; k++) {
			int z = -18 + k;
			int y = F1 + 1 + k;
			c.fill(s * 5, y, z, s * 6, y, z, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
			if (k > 0) {
				c.fill(s * 5, F1 + 1, z, s * 6, y - 1, z, s(Blocks.STONE_BRICKS));
			}
		}
	}

	/** The stair tunnel from the Cell Block's south-east corner down to the Security Floor. */
	private void stairs1to2(HideoutCanvas c) {
		fillP(c, 8, F1, 19, 12, F1 + 5, 24, VaultBuilder::cellWall);
		c.air(9, F1 + 1, 20, 11, F1 + 4, 23);
		c.air(9, F1 + 1, 19, 11, F1 + 3, 19);
		c.fill(9, F1, 20, 11, F1, 23, s(Blocks.POLISHED_ANDESITE));
		c.set(10, F1 + 4, 22, s(Blocks.SEA_LANTERN));
		for (int k = 1; k <= 14; k++) {
			int x = 9 - k;
			int top = F1 - k;
			fillP(c, x, top - 1, 20, x, top + 5, 24, k < 8 ? VaultBuilder::cellWall : VaultBuilder::securityWall);
			c.fill(x, top, 21, x, top, 23, k == 14 ? s(Blocks.LIGHT_GRAY_CONCRETE) : stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
			c.air(x, top + 1, 21, x, top + 4, 23);
			if (k % 5 == 0) {
				c.set(x, top + 5, 22, s(Blocks.SEA_LANTERN));
			}
		}
	}

	/** Floor 2: the Security Floor. */
	private void securityFloor(HideoutCanvas c) {
		int y0 = F2 + 1; // -27
		// the checkpoint corridor and its lasers
		shell(c, -9, F2, 8, -5, F2 + 6, 24, VaultBuilder::securityWall);
		c.fill(-8, F2, 9, -6, F2, 23, a -> a[2] % 3 == 0 ? s(Blocks.WHITE_CONCRETE) : s(Blocks.LIGHT_GRAY_CONCRETE));
		c.fill(-8, F2 + 6, 9, -6, F2 + 6, 23, s(Blocks.SMOOTH_QUARTZ));
		for (int z = 10; z <= 22; z += 4) {
			c.set(-7, F2 + 6, z, s(Blocks.SEA_LANTERN));
		}
		if (v2) {
			// v0.17.7: the alarm framework's laser tripwires, set in the west wall: a knee beam (jump) and a head beam (crouch)
			c.laser(LASERS.get(0).getX(), y0, LASERS.get(0).getZ(), Direction.EAST);
			c.laser(LASERS.get(1).getX(), y0 + 1, LASERS.get(1).getZ(), Direction.EAST);
			c.camera(-8, F2 + 5, 23, Direction.NORTH);
		} else {
			for (BlockPos l : LASERS) {
				laser(c, l.getZ(), y0);
			}
		}
		// open the corridor's east side to the stair from floor 1 (the tunnel draws its own last step)
		c.air(-5, y0, 21, -5, y0 + 3, 23);

		// the turret hall
		shell(c, -12, F2, -7, 12, F2 + 7, 9, VaultBuilder::securityWall);
		c.fill(-11, F2, -6, 11, F2, 8, a -> (Math.abs(a[0]) + Math.abs(a[2])) % 4 == 0 ? s(Blocks.WHITE_CONCRETE) : s(Blocks.LIGHT_GRAY_CONCRETE));
		c.fill(-11, F2 + 7, -6, 11, F2 + 7, 8, s(Blocks.SMOOTH_QUARTZ));
		for (int x : new int[] { -10, -3, 3, 10 }) {
			for (int z : new int[] { -4, 2, 7 }) {
				c.set(x, F2 + 7, z, s(Blocks.SEA_LANTERN));
			}
		}
		c.air(-8, y0, 9, -6, y0 + 3, 9); // from the corridor
		for (BlockPos t : TURRETS) {
			c.set(t.getX(), t.getY(), t.getZ(), s(VaultBlocks.TURRET));
		}
		// cover
		for (int[] w : new int[][] { { -4, -2, 3, 3 }, { 2, 4, 0, 0 }, { -6, -5, -3, -3 }, { 6, 8, 4, 4 }, { -9, -9, 0, 1 }, { 8, 8, -4, -3 } }) {
			c.fill(w[0], y0, w[2], w[1], y0 + 1, w[3], s(Blocks.LIGHT_GRAY_CONCRETE));
			c.fill(w[0], y0 + 1, w[2], w[1], y0 + 1, w[3], s(Blocks.SMOOTH_QUARTZ_SLAB));
		}
		c.set(-10, y0, 6, s(Blocks.BARREL));
		c.set(-10, y0 + 1, 6, s(Blocks.BARREL));
		c.set(10, y0, -5, s(Blocks.IRON_BLOCK));

		// the lockdown gate: iron frame, red lintel, hazard track, a pair of iron doors (opened from the console)
		c.fill(-2, y0, -7, 1, y0 + 2, -7, s(Blocks.IRON_BLOCK));
		c.fill(-2, y0 + 2, -7, 1, y0 + 2, -7, s(Blocks.RED_CONCRETE));
		ironDoor(c, -1, y0, -7, Direction.NORTH, DoorHingeSide.LEFT);
		ironDoor(c, 0, y0, -7, Direction.NORTH, DoorHingeSide.RIGHT);
		c.fill(-2, F2, -6, 1, F2, -5, s(VaultBlocks.TRACK));

		// the security office
		shell(c, 1, F2, 10, 11, F2 + 6, 20, VaultBuilder::securityWall);
		c.fill(2, F2, 11, 10, F2, 19, s(Blocks.GRAY_CONCRETE));
		c.fill(2, F2 + 6, 11, 10, F2 + 6, 19, s(Blocks.SMOOTH_QUARTZ));
		c.set(6, F2 + 6, 15, s(Blocks.SEA_LANTERN));
		c.set(3, F2 + 6, 12, s(Blocks.SEA_LANTERN));
		c.air(5, y0, 9, 6, y0 + 1, 10);
		c.fill(3, y0 + 1, 20, 9, y0 + 2, 20, a -> a[0] % 2 == 0 ? s(Blocks.BLACK_CONCRETE) : s(Blocks.LIGHT_BLUE_STAINED_GLASS));
		c.fill(3, y0, 19, 9, y0, 19, s(Blocks.POLISHED_ANDESITE));
		c.set(CONSOLE.getX(), CONSOLE.getY(), CONSOLE.getZ(), s(VaultBlocks.CONSOLE).setValue(VaultConsoleBlock.FACING, Direction.NORTH));
		c.set(6, y0, 18, stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
		c.container(2, y0, 11, s(Blocks.BARREL), HideoutCanvas.table("chests/vault_tier2"));
		c.container(10, y0, 11, s(Blocks.BARREL), HideoutCanvas.table("chests/vault_tier2"));
		c.set(2, y0, 12, s(Blocks.BARREL));
		c.set(10, y0, 17, s(Blocks.IRON_BLOCK));
		c.set(10, y0 + 1, 17, s(Blocks.IRON_TRAPDOOR));

		guard(c, WARDEN, -3, y0, 6, Direction.SOUTH, 5, GuardRole.SHIELD);
		guard(c, WARDEN, -6, y0, 2, Direction.SOUTH, 5, GuardRole.RUNNER);
		guard(c, WARDEN, 5, y0, -2, Direction.SOUTH, 8);
		guard(c, WARDEN, 0, y0, -4, Direction.SOUTH, 6, GuardRole.MEDIC);
		guard(c, GUNNER, -9, y0, -4, Direction.EAST, 8, GuardRole.SNIPER);
		guard(c, GUNNER, 9, y0, 6, Direction.WEST, 8);
		guard(c, WARDEN, 8, y0, 15, Direction.WEST, 5);
		if (v2) {
			// v0.17.7: Alarm Panel #3 in the turret hall, cameras in its corners, lamps, spawn points, and the Captain in the
			// security office: his keycard opens the Blast Door from the gate landing into Maximum Security
			panel(c, -11, y0, 3, Direction.EAST);
			c.camera(-11, F2 + 5, -6, Direction.EAST);
			c.camera(11, F2 + 5, 8, Direction.WEST);
			lamp(c, -6, F2 + 7, 0);
			lamp(c, 6, F2 + 7, 0);
			lamp(c, 6, F2 + 6, 17);
			c.spawnPoint(10, y0, 7);
			c.spawnPoint(-10, y0, -5);
			c.spawnPoint(0, y0, 7);
			guard(c, WARDEN, 4, y0, 15, Direction.NORTH, 4, GuardRole.CAPTAIN);
		}
	}

	/** A laser tripwire across the checkpoint corridor at row {@code z}: emitter in the west wall, hooks, wire. */
	private static void laser(HideoutCanvas c, int z, int y) {
		c.set(-9, y, z, s(VaultBlocks.ALARM).setValue(VaultAlarmBlock.FACING, Direction.EAST));
		BlockState hook = s(Blocks.TRIPWIRE_HOOK).setValue(TripWireHookBlock.ATTACHED, true);
		c.set(-8, y, z, hook.setValue(TripWireHookBlock.FACING, Direction.EAST));
		c.set(-6, y, z, hook.setValue(TripWireHookBlock.FACING, Direction.WEST));
		c.set(-7, y, z, s(Blocks.TRIPWIRE).setValue(TripWireBlock.ATTACHED, true).setValue(TripWireBlock.EAST, true).setValue(TripWireBlock.WEST, true));
	}

	/** The stair past the lockdown gate, down to the Warden's Level. */
	private void stairs2to3(HideoutCanvas c) {
		shell(c, -3, F2, -12, 2, F2 + 5, -7, VaultBuilder::securityWall);
		c.fill(-2, F2, -11, 1, F2, -8, s(Blocks.LIGHT_GRAY_CONCRETE));
		c.set(0, F2 + 5, -10, s(Blocks.SEA_LANTERN));
		// re-open the gate (the landing's shell shares its wall)
		c.fill(-2, F2 + 1, -7, 1, F2 + 3, -7, s(Blocks.IRON_BLOCK));
		c.fill(-2, F2 + 3, -7, 1, F2 + 3, -7, s(Blocks.RED_CONCRETE));
		ironDoor(c, -1, F2 + 1, -7, Direction.NORTH, DoorHingeSide.LEFT);
		ironDoor(c, 0, F2 + 1, -7, Direction.NORTH, DoorHingeSide.RIGHT);
		if (v2) {
			// v0.17.7: the landing now ends at the keycard Blast Door north into Maximum Security (see frames())
			c.fill(-2, F2, -11, 1, F2, -11, s(VaultBlocks.TRACK));
			lamp(c, 0, F2 + 5, -9);
		}
	}

	// ---------------------------------------------------------------- v0.17.7: Maximum Security

	private static BlockState maxWall(HideoutCanvas c, int x, int y, int z) {
		double n = c.noise(x, y, z, 24);
		return n < 0.12 ? s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : n < 0.55 ? s(Blocks.POLISHED_BLACKSTONE_BRICKS) : s(Blocks.GRAY_CONCRETE);
	}

	private static BlockState controlWall(HideoutCanvas c, int x, int y, int z) {
		return c.noise(x, y, z, 25) < 0.2 ? s(Blocks.LIGHT_GRAY_CONCRETE) : s(Blocks.CYAN_TERRACOTTA);
	}

	/**
	 * Floor 2b, <b>Maximum Security</b> (y {@code F2}, north of the gate landing): the laser corridor (knee and head
	 * beams from both walls, cameras each end, Alarm Panel #4), the laser grid (three rows of floor beams, one gap each,
	 * swept by two cameras) and the hall -- eight power-fielded pods with their inmates, a catwalk over them, the Riot
	 * Captain and his squad, Alarm Panel #5 -- whose far door is the Riot Captain's red field.
	 */
	private void maxSecurity(HideoutCanvas c) {
		int y0 = F2 + 1;
		// ---- the laser corridor (interior x -2..1, z -23..-13)
		shell(c, -3, F2, -24, 2, F2 + 5, -12, VaultBuilder::maxWall);
		c.fill(-2, F2, -23, 1, F2, -13, a -> a[2] % 2 == 0 ? s(Blocks.WHITE_CONCRETE) : s(Blocks.LIGHT_GRAY_CONCRETE));
		c.fill(-2, F2 + 5, -23, 1, F2 + 5, -13, s(Blocks.SMOOTH_QUARTZ));
		for (int z : new int[] { -14, -18, -22 }) {
			c.set(-1, F2 + 5, z, s(Blocks.SEA_LANTERN));
			c.set(0, F2 + 5, z, s(Blocks.SEA_LANTERN));
		}
		int[][] beams = { { -15, 0, 0 }, { -17, 1, 0 }, { -19, 0, 1 }, { -21, 1, 1 }, { -22, 0, 0 } }; // z, head?, from east?
		for (int[] b : beams) {
			c.fill(-2, F2, b[0], 1, F2, b[0], s(Blocks.YELLOW_CONCRETE)); // a hazard stripe under each beam
			if (b[2] == 0) {
				c.laser(-3, y0 + b[1], b[0], Direction.EAST);
			} else {
				c.laser(2, y0 + b[1], b[0], Direction.WEST);
			}
		}
		panel(c, 1, y0, -14, Direction.WEST);
		c.camera(-2, y0 + 3, -13, Direction.NORTH);
		c.camera(1, y0 + 3, -23, Direction.SOUTH);
		lamp(c, 0, F2 + 5, -16);
		lamp(c, -1, F2 + 5, -20);
		c.air(-1, y0, -24, 0, y0 + 2, -24);

		// ---- the laser grid (interior x -6..5, z -33..-25)
		shell(c, -7, F2, -34, 6, F2 + 6, -24, VaultBuilder::maxWall);
		c.fill(-6, F2, -33, 5, F2, -25, a -> (a[0] + a[2]) % 2 == 0 ? s(Blocks.BLACK_CONCRETE) : s(Blocks.GRAY_CONCRETE));
		c.fill(-6, F2 + 6, -33, 5, F2 + 6, -25, s(Blocks.POLISHED_BLACKSTONE));
		for (int[] p : new int[][] { { -4, -26 }, { 3, -26 }, { -4, -32 }, { 3, -32 } }) {
			c.set(p[0], F2 + 6, p[1], s(Blocks.SEA_LANTERN));
		}
		c.air(-1, y0, -24, 0, y0 + 2, -24);
		c.air(-1, y0, -34, 0, y0 + 2, -34);
		int[][] rows = { { -27, 4 }, { -30, -5 }, { -32, 0 } }; // row z, the gap's x
		for (int[] r : rows) {
			for (int x = -6; x <= 5; x++) {
				if (x != r[1]) {
					c.laser(x, F2, r[0], Direction.UP);
				}
			}
			c.set(r[1], F2, r[0], s(Blocks.LIME_CONCRETE)); // the gap, marked (if you look for it)
		}
		c.camera(-6, y0 + 3, -33, Direction.SOUTH);
		c.camera(5, y0 + 3, -33, Direction.SOUTH);
		lamp(c, 0, F2 + 6, -29);
		guard(c, WARDEN, -5, y0, -25, Direction.SOUTH, 3, GuardRole.SHIELD);

		// ---- the hall (interior x -11..10, z -51..-35)
		shell(c, -12, F2, -52, 11, F2 + 8, -34, VaultBuilder::maxWall);
		int rz = RIOT_CAPTAIN.getZ();
		c.fill(-11, F2, -51, 10, F2, -35, a -> {
			int ring = Math.max(Math.abs(a[0]), Math.abs(a[2] - rz));
			if (ring == 5) {
				return s(Blocks.YELLOW_CONCRETE);
			}
			return (a[0] + a[2]) % 2 == 0 ? s(Blocks.POLISHED_BLACKSTONE) : s(Blocks.GRAY_CONCRETE);
		});
		c.fill(-11, F2 + 8, -51, 10, F2 + 8, -35, s(Blocks.POLISHED_BLACKSTONE));
		for (int x : new int[] { -4, 3 }) {
			for (int z : new int[] { -48, -43, -38 }) {
				c.set(x, F2 + 8, z, s(Blocks.SEA_LANTERN));
			}
		}
		c.air(-1, y0, -34, 0, y0 + 2, -34);
		// the pods: west x -11..-8 (field at x -8), east x 7..10 (field at x 7); separators, a roof = the catwalk
		for (int side : new int[] { -1, 1 }) {
			int inner = side < 0 ? -8 : 7;
			int outer = side < 0 ? -11 : 10;
			int lo = Math.min(inner, outer);
			int hi = Math.max(inner, outer);
			c.fill(lo, y0 + 3, -51, hi, y0 + 3, -35, s(Blocks.IRON_BLOCK)); // pod roofs / catwalk floor
			for (int z : new int[] { -51, -47, -43, -39, -35 }) {
				c.fill(lo, y0, z, hi, y0 + 2, z, s(Blocks.OBSIDIAN));
			}
			for (int zc : new int[] { -49, -45, -41, -37 }) {
				barrier(c, inner, y0, zc - 1, inner, y0 + 2, zc + 1, VaultBarrierBlock.Kind.POWER);
				c.set(outer, y0, zc - 1, s(Blocks.WHITE_WOOL));
				c.set(outer, y0, zc + 1, s(Blocks.CAULDRON));
				c.set(outer, y0 + 2, zc, s(Blocks.REDSTONE_LAMP).setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
			}
			// the catwalk rail
			c.fill(inner, y0 + 4, -50, inner, y0 + 4, -36, bars(false));
		}
		// a ladder up to the west catwalk at the hall's south end (on the last separator)
		for (int y = y0; y <= y0 + 3; y++) {
			c.set(-7, y, -35, s(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.EAST));
		}
		c.set(-7, y0 + 4, -35, s(Blocks.AIR));
		for (BlockPos p : PODS) {
			podInmate(c, p.getX(), p.getY(), p.getZ(), p.getX() < 0 ? Direction.EAST : Direction.WEST);
		}
		// a warning stripe before the far door (its frame and the red field go in last, see frames())
		c.fill(-2, F2, -51, 1, F2, -51, s(VaultBlocks.TRACK));
		panel(c, 4, y0, -35, Direction.NORTH);
		c.camera(-6, y0 + 5, -35, Direction.NORTH);
		c.camera(5, y0 + 5, -35, Direction.NORTH);
		c.camera(-3, y0 + 5, -51, Direction.SOUTH);
		for (int[] p : new int[][] { { -6, F2 + 8, -43 }, { 5, F2 + 8, -43 }, { 0, F2 + 8, -36 }, { 0, F2 + 8, -50 } }) {
			lamp(c, p[0], p[1], p[2]);
		}
		c.spawnPoint(-5, y0, -38);
		c.spawnPoint(5, y0, -38);
		c.spawnPoint(-5, y0, -50);
		c.spawnPoint(5, y0, -50);
		c.container(-6, y0, -50, s(Blocks.BARREL), HideoutCanvas.table("chests/vault_tier2"));
		riotCaptain(c);
		guard(c, WARDEN, -3, y0, -41, Direction.SOUTH, 6, GuardRole.SHIELD);
		guard(c, WARDEN, 3, y0, -47, Direction.SOUTH, 6, GuardRole.MEDIC);
		guard(c, WARDEN, -4, y0, -37, Direction.SOUTH, 5, GuardRole.RUNNER);
		guard(c, GUNNER, 8, y0 + 4, -43, Direction.WEST, 2, GuardRole.SNIPER);
		guard(c, GUNNER, -9, y0 + 4, -45, Direction.EAST, 2);
	}

	/**
	 * Floor 2c, the <b>Warden's Control Room</b> (north of the hall): a monitor wall, desks, the cell-block breaker
	 * (shuts the power down: the blue field on the way out drops -- and so do the pods'), Alarm Panel #6, two guards,
	 * a Tier 3 locker.
	 */
	private void controlRoom(HideoutCanvas c) {
		int y0 = F2 + 1;
		shell(c, -9, F2, -64, 8, F2 + 6, -52, VaultBuilder::controlWall);
		c.fill(-8, F2, -63, 7, F2, -53, a -> Math.abs(a[0]) % 4 == 1 ? s(Blocks.CYAN_TERRACOTTA) : s(Blocks.LIGHT_GRAY_CONCRETE));
		c.fill(-8, F2 + 6, -63, 7, F2 + 6, -53, s(Blocks.SMOOTH_QUARTZ));
		for (int[] p : new int[][] { { -5, -56 }, { 4, -56 }, { -5, -61 }, { 4, -61 } }) {
			c.set(p[0], F2 + 6, p[1], s(Blocks.SEA_LANTERN));
		}
		c.air(-1, y0, -52, 0, y0 + 2, -52);
		// the monitor wall (set into the north wall)
		c.fill(-7, y0 + 1, -64, 6, y0 + 3, -64, a -> (a[0] + a[1]) % 2 == 0 ? s(Blocks.BLACK_CONCRETE) : s(Blocks.LIGHT_BLUE_STAINED_GLASS));
		// the desks and chairs, the breaker between them
		c.fill(-6, y0, -61, -2, y0, -61, s(Blocks.POLISHED_ANDESITE));
		c.fill(1, y0, -61, 5, y0, -61, s(Blocks.POLISHED_ANDESITE));
		for (int x : new int[] { -5, -3, 2, 4 }) {
			c.set(x, y0, -60, stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
			c.set(x, y0 + 1, -61, s(Blocks.DAYLIGHT_DETECTOR));
		}
		c.fill(-1, F2, -63, 0, F2, -61, s(VaultBlocks.TRACK));
		c.set(BREAKER.getX(), BREAKER.getY(), BREAKER.getZ(), s(VaultBlocks.BREAKER).setValue(VaultBreakerBlock.FACING, Direction.SOUTH));
		c.set(BREAKER.getX(), BREAKER.getY() + 1, BREAKER.getZ() - 1, s(Blocks.REDSTONE_BLOCK));
		c.set(-1, y0, -63, s(Blocks.IRON_BLOCK));
		c.set(1, y0, -63, s(Blocks.IRON_BLOCK));
		// cable runs on the floor toward the pods (decoration)
		c.fill(0, F2, -60, 0, F2, -53, s(Blocks.REDSTONE_BLOCK));
		// lockers and loot
		c.container(-8, y0, -63, s(Blocks.CHEST), HideoutCanvas.table("chests/vault_tier3"));
		c.container(7, y0, -63, s(Blocks.BARREL), HideoutCanvas.table("chests/vault_tier2"));
		c.set(7, y0, -62, s(Blocks.BARREL));
		c.set(7, y0 + 1, -62, s(Blocks.BARREL));
		// the way out is west, through the blue field (frame + field drawn last, see frames())
		panel(c, 7, y0, -58, Direction.WEST);
		c.camera(7, y0 + 3, -53, Direction.WEST);
		lamp(c, -3, F2 + 6, -58);
		lamp(c, 3, F2 + 6, -58);
		c.spawnPoint(-6, y0, -54);
		c.spawnPoint(5, y0, -54);
		guard(c, WARDEN, -4, y0, -56, Direction.SOUTH, 5, GuardRole.SHIELD);
		guard(c, GUNNER, 4, y0, -55, Direction.SOUTH, 5);
	}

	/**
	 * The way down from the Control Room: a passage west, a long stair south down to the Warden's Level and a tunnel into
	 * the processing room's north wall.
	 */
	private void descent(HideoutCanvas c) {
		int y0 = F2 + 1;
		// the tunnel on the Warden's Level (interior x -24..-22, z -40..-13), then the stair (drawn over its north end)
		shell(c, -25, F3, -41, -21, F3 + 5, -12, VaultBuilder::wardenWall);
		c.fill(-24, F3, -40, -22, F3, -13, a -> a[2] % 4 == 0 ? s(Blocks.RED_NETHER_BRICKS) : s(Blocks.POLISHED_DEEPSLATE));
		for (int z = -38; z <= -14; z += 6) {
			c.set(-23, F3 + 5, z, s(Blocks.SHROOMLIGHT));
		}
		lamp(c, -23, F3 + 5, -27);
		c.camera(-22, F3 + 4, -13, Direction.NORTH);
		c.spawnPoint(-23, F3 + 1, -30);
		guard(c, GUNNER, -23, F3 + 1, -20, Direction.NORTH, 6);
		// the passage west from the Control Room (interior x -24..-10, z -58..-57)
		shell(c, -25, F2, -59, -9, F2 + 5, -56, VaultBuilder::controlWall);
		c.fill(-24, F2, -58, -10, F2, -57, s(Blocks.GRAY_CONCRETE));
		for (int x = -22; x <= -12; x += 5) {
			c.set(x, F2 + 5, -57, s(Blocks.SEA_LANTERN));
		}
		// the stair: 16 steps down toward local south (+z), z -56..-41
		for (int k = 1; k <= 16; k++) {
			int z = -57 + k;
			int top = F2 - k;
			fillP(c, -25, top - 1, z, -21, top + 5, z, k < 8 ? VaultBuilder::controlWall : VaultBuilder::wardenWall);
			c.fill(-24, top, z, -22, top, z, k == 16 ? s(Blocks.POLISHED_DEEPSLATE) : stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH));
			c.air(-24, top + 1, z, -22, top + 4, z);
			if (k % 5 == 0) {
				c.set(-23, top + 5, z, s(Blocks.SHROOMLIGHT));
			}
		}
		c.air(-24, y0, -56, -22, y0 + 2, -56); // the passage opens onto the first step
	}

	/** The stair's flights (drawn after the processing room's shell so they cut through it). */
	private static void flights2to3(HideoutCanvas c) {
		for (int k = 1; k <= 15; k++) {
			int x = -2 - k;
			int top = F2 - k;
			fillP(c, x, top - 1, -12, x, top + 5, -8, VaultBuilder::wardenWall);
			c.fill(x, top, -11, x, top, -9, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST));
			c.air(x, top + 1, -11, x, top + 4, -9);
			if (k % 5 == 0) {
				c.set(x, top + 5, -10, s(Blocks.SHROOMLIGHT));
			}
		}
	}

	/** Floor 3: the processing room, the arena, the vault room. */
	private void wardensLevel(HideoutCanvas c) {
		int y0 = F3 + 1; // -43
		// the processing room
		shell(c, -26, F3, -12, -17, F3 + 6, 5, VaultBuilder::wardenWall);
		c.fill(-25, F3, -11, -18, F3, 4, a -> (a[0] + a[2]) % 2 == 0 ? s(Blocks.POLISHED_BLACKSTONE_BRICKS) : s(Blocks.POLISHED_DEEPSLATE));
		c.fill(-25, F3 + 6, -11, -18, F3 + 6, 4, s(Blocks.POLISHED_DEEPSLATE));
		for (int z = -8; z <= 2; z += 5) {
			c.set(-21, F3 + 6, z, s(Blocks.SHROOMLIGHT));
		}
		if (v2) {
			// v0.17.7: the way in is now the tunnel from the Control Room, through the north wall
			c.air(-24, y0, -12, -22, y0 + 2, -12);
			panel(c, -18, y0, -10, Direction.WEST);
			lamp(c, -21, F3 + 6, -6);
			c.spawnPoint(-24, y0, -9);
			c.spawnPoint(-19, y0, 3);
		} else {
			flights2to3(c);
		}
		c.container(-25, y0, -5, s(Blocks.CHEST), HideoutCanvas.table("chests/vault_tier3"));
		c.set(-25, y0, -4, s(Blocks.BARREL));
		c.set(-25, y0, -3, s(Blocks.BARREL));
		c.fill(-25, y0, 1, -23, y0 + 2, 1, bars(true)); // a holding cage
		c.fill(-23, y0, 2, -23, y0 + 2, 4, bars(false));
		c.set(-24, y0, 3, s(Blocks.CHAIN));
		c.set(-19, y0 + 3, -1, s(Blocks.LANTERN).setValue(LanternBlock.HANGING, false));
		guard(c, WARDEN, -21, y0, -2, Direction.EAST, 6, GuardRole.SHIELD);
		guard(c, WARDEN, -21, y0, 2, Direction.EAST, 6, GuardRole.MEDIC);
		guard(c, GUNNER, -24, y0, -1, Direction.EAST, 6);

		// the arena
		shell(c, -12, F3, -7, 12, F3 + 10, 17, VaultBuilder::wardenWall);
		int ax = ANCHOR.getX();
		int az = ANCHOR.getZ();
		c.fill(-11, F3, -6, 11, F3, 16, a -> {
			double d = Math.sqrt((a[0] - ax) * (a[0] - ax) + (a[2] - az) * (a[2] - az));
			if (d < 1.6) {
				return s(Blocks.GILDED_BLACKSTONE);
			}
			if (Math.abs(d - 8.0) < 0.55) {
				return s(Blocks.RED_CONCRETE);
			}
			return (a[0] + a[2]) % 2 == 0 ? s(Blocks.POLISHED_BLACKSTONE_BRICKS) : s(Blocks.POLISHED_BLACKSTONE);
		});
		c.fill(-11, F3 + 10, -6, 11, F3 + 10, 16, s(Blocks.POLISHED_DEEPSLATE));
		for (int x : new int[] { -8, 0, 8 }) {
			for (int z : new int[] { -3, 5, 13 }) {
				c.set(x, F3 + 10, z, s(Blocks.SHROOMLIGHT));
			}
		}
		for (int[] p : new int[][] { { -7, 0 }, { 6, 0 }, { -7, 10 }, { 6, 10 } }) {
			c.fill(p[0], y0, p[1], p[0] + 1, F3 + 9, p[1] + 1, s(Blocks.POLISHED_DEEPSLATE));
			c.fill(p[0], y0 + 4, p[1], p[0] + 1, y0 + 4, p[1] + 1, s(Blocks.SHROOMLIGHT));
			c.fill(p[0], y0, p[1], p[0] + 1, y0, p[1] + 1, s(Blocks.RED_NETHER_BRICKS));
		}
		// red banding round the walls
		c.fill(-12, y0 + 5, -7, 12, y0 + 5, -7, s(Blocks.RED_NETHER_BRICKS));
		c.fill(-12, y0 + 5, 17, 12, y0 + 5, 17, s(Blocks.RED_NETHER_BRICKS));
		c.fill(-12, y0 + 5, -7, -12, y0 + 5, 17, s(Blocks.RED_NETHER_BRICKS));
		c.fill(12, y0 + 5, -7, 12, y0 + 5, 17, s(Blocks.RED_NETHER_BRICKS));
		// Kross's dais and chair
		c.fill(-4, y0, 13, 4, y0, 15, s(Blocks.POLISHED_DEEPSLATE));
		c.fill(-4, y0, 13, 4, y0, 13, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH));
		c.set(0, y0 + 1, 15, stair(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.SOUTH));
		c.set(-1, y0 + 1, 15, s(Blocks.POLISHED_BLACKSTONE_WALL));
		c.set(1, y0 + 1, 15, s(Blocks.POLISHED_BLACKSTONE_WALL));

		// the passage from the processing room, its lockdown track
		fillP(c, -17, F3, -2, -12, F3 + 4, 2, VaultBuilder::wardenWall);
		c.air(-17, y0, -1, -12, y0 + 2, 1);
		c.fill(-16, F3, -1, -13, F3, 1, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
		c.fill(-12, F3, -1, -12, F3, 1, s(VaultBlocks.TRACK));
		c.fill(-12, y0 + 3, -2, -12, y0 + 3, 2, s(Blocks.RED_CONCRETE));

		// the vault room behind the arena, its lockdown track and three sealed Dungeon Vaults
		shell(c, -7, F3, 17, 7, F3 + 6, 25, VaultBuilder::wardenWall);
		c.fill(-6, F3, 18, 6, F3, 24, a -> (a[0] + a[2]) % 2 == 0 ? s(Blocks.DARK_PRISMARINE) : s(Blocks.POLISHED_DEEPSLATE));
		c.fill(-6, F3 + 6, 18, 6, F3 + 6, 24, s(Blocks.POLISHED_DEEPSLATE));
		for (int x : new int[] { -5, 5 }) {
			c.fill(x, y0, 24, x, F3 + 5, 24, s(Blocks.SEA_LANTERN));
			c.fill(x, y0, 19, x, F3 + 5, 19, s(Blocks.PRISMARINE_BRICKS));
		}
		c.set(0, F3 + 6, 21, s(Blocks.SEA_LANTERN));
		c.air(-1, y0, 17, 1, y0 + 2, 17);
		c.fill(-1, F3, 17, 1, F3, 17, s(VaultBlocks.TRACK));
		c.fill(-2, y0 + 3, 17, 2, y0 + 3, 17, s(Blocks.RED_CONCRETE));
		for (BlockPos v : VAULTS) {
			c.set(v.getX(), F3, v.getZ(), s(Blocks.GOLD_BLOCK));
			c.set(v.getX(), v.getY(), v.getZ(), s(VaultBlocks.SEAL).setValue(VaultSealBlock.FACING, Direction.NORTH));
		}
		// v0.17.5: the sealed O.X.E. serum vat in the middle of the vault room (the Sentry Serum's only source), on a gold plinth
		c.set(SERUM_VAT.getX(), F3, SERUM_VAT.getZ(), s(Blocks.GOLD_BLOCK));
		c.set(SERUM_VAT.getX(), SERUM_VAT.getY(), SERUM_VAT.getZ(), s(com.projecthero.mod.sentry.block.SentryBlocks.SERUM_VAT)
				.setValue(com.projecthero.mod.sentry.block.SerumVatBlock.FACING, Direction.NORTH));

		if (v2) {
			// v0.17.7: alarm lamps in the arena walls, reinforcement points in its corners
			lamp(c, -12, y0 + 3, 5);
			lamp(c, 12, y0 + 3, 5);
			lamp(c, 0, y0 + 3, -7);
			c.spawnPoint(-10, y0, -5);
			c.spawnPoint(10, y0, -5);
			c.spawnPoint(-10, y0, 15);
			c.spawnPoint(10, y0, 15);
		}
		boss(c);
	}
}
