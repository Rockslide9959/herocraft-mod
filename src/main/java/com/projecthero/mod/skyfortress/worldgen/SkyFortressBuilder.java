package com.projecthero.mod.skyfortress.worldgen;

import java.util.List;

import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.hideout.worldgen.HideoutCanvas;
import com.projecthero.mod.skyfortress.SkyFortressEntityTypes;
import com.projecthero.mod.skyfortress.SkyFortressGuards;
import com.projecthero.mod.skyfortress.SkyFortressGuards.Kind;
import com.projecthero.mod.skyfortress.block.SkyFortressBlocks;
import com.projecthero.mod.skyfortress.block.SkyVaultSealBlock;
import com.projecthero.mod.skyfortress.block.WindVentBlock;
import com.projecthero.mod.skyfortress.entity.AdmiralKestrelBoss;
import com.projecthero.mod.skyfortress.entity.DeckOfficerBoss;
import com.projecthero.mod.skyfortress.entity.SkyPrisoner;
import com.projecthero.mod.vaultdungeon.block.VaultBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * v0.17.7: the Sky Fortress's layout, drawn in local coordinates on a {@link HideoutCanvas} (no faction): {@code y = 0}
 * is the lower deck's floor layer (at {@link SkyFortressStructure#DECK_Y} in the world), the bow -- the open hangar mouth --
 * faces local north ({@code -z}). Writes are rotated and clipped per chunk; mobs spawn only in the chunk holding their feet.
 * <ul>
 * <li><b>Hull</b> x -15..15, z -44..32, a stepped keel under it down to {@code y -7}, two engine nacelles on pylons
 * (x &plusmn;20), stern thrusters; portholes, a navy livery stripe, nav lights, downlights.</li>
 * <li><b>Hangar deck</b> (z -43..-21): the lift hatch over the Launch Pad (x -1..1, z -37..-35), two parked jets,
 * catwalks (a sniper), two ceiling turrets by the engine door, Alarm Panel #1, cameras, Parachute Crates by the mouth.</li>
 * <li><b>Engine room</b> (z -19..-3): four turbine housings (cores face the centre walk), laser tripwires, Panel #2.</li>
 * <li><b>Cell block</b> (z -1..14): six barred cells (four prisoners), the guard desk (Panel #3), the Captain (keycard);
 * the energy shield over the stair-hall door (z 15).</li>
 * <li><b>Stern</b> (z 16..31): the stair hall up to the bridge, the armory and the barracks.</li>
 * <li><b>Command Bridge</b> (x -9..9, z 18..32, on the flight deck): consoles, the Deck Officer, Panel #4, a sniper
 * on the roof, the radar mast; the keycard Blast Door north onto the flight deck.</li>
 * <li><b>Flight deck</b> ({@code y 8}, open edges): runway markings, wind vents, Admiral Kestrel; her cabin at the bow
 * holds the sealed Fortress Vault.</li>
 * </ul>
 */
public final class SkyFortressBuilder {
	public static final SkyFortressBuilder INSTANCE = new SkyFortressBuilder();

	public static final int X = 15;
	public static final int BOW = -44;
	public static final int STERN = 32;
	public static final int P_HANGAR = -20;
	public static final int P_ENGINE = -2;
	public static final int P_CELL = 15;
	public static final int DECK = 8;

	/** The lift hatch centre (the Launch Pad sits straight below). */
	public static final BlockPos HATCH = new BlockPos(0, 0, -36);
	/** Admiral Kestrel's home on the flight deck (standing level) and her spawn. */
	public static final BlockPos KESTREL_HOME = new BlockPos(0, DECK + 1, -12);
	public static final BlockPos KESTREL_SPAWN = new BlockPos(0, DECK + 1, -6);
	/** The Deck Officer's home on the bridge. */
	public static final BlockPos OFFICER_HOME = new BlockPos(0, DECK + 1, 21);
	public static final BlockPos VAULT = new BlockPos(0, DECK + 1, -42);
	public static final List<BlockPos> TURBINES = List.of(new BlockPos(-7, 2, -15), new BlockPos(7, 2, -15), new BlockPos(-7, 2, -7),
			new BlockPos(7, 2, -7));
	public static final List<BlockPos> SHIELD = List.of(new BlockPos(-1, 1, P_CELL), new BlockPos(0, 1, P_CELL), new BlockPos(1, 1, P_CELL),
			new BlockPos(-1, 2, P_CELL), new BlockPos(0, 2, P_CELL), new BlockPos(1, 2, P_CELL), new BlockPos(-1, 3, P_CELL),
			new BlockPos(0, 3, P_CELL), new BlockPos(1, 3, P_CELL));
	/** Lower blocks of the keycard Blast Door from the bridge onto the flight deck. */
	public static final BlockPos BRIDGE_DOOR = new BlockPos(0, DECK + 1, 18);
	public static final List<BlockPos> PANELS = List.of(new BlockPos(14, 1, -26), new BlockPos(-13, 1, -11), new BlockPos(0, 1, 7),
			new BlockPos(8, DECK + 1, 30));

	public final int minX = -23;
	public final int minY = -8;
	public final int minZ = -48;
	public final int maxX = 23;
	public final int maxY = 25;
	public final int maxZ = 34;

	private SkyFortressBuilder() {
	}

	// ---------------------------------------------------------------- palettes

	private static BlockState s(Block b) {
		return b.defaultBlockState();
	}

	private static BlockState hull(HideoutCanvas c, int x, int y, int z) {
		double n = c.noise(x, y, z, 31);
		return n < 0.12 ? s(Blocks.POLISHED_ANDESITE) : n < 0.2 ? s(Blocks.SMOOTH_STONE) : s(Blocks.LIGHT_GRAY_CONCRETE);
	}

	private static BlockState keel(HideoutCanvas c, int x, int y, int z) {
		double n = c.noise(x, y, z, 32);
		return n < 0.3 ? s(Blocks.POLISHED_DEEPSLATE) : n < 0.38 ? s(Blocks.DEEPSLATE_TILES) : s(Blocks.GRAY_CONCRETE);
	}

	private static BlockState deck(HideoutCanvas c, int x, int z) {
		double n = c.noise(x, z, 33);
		return n < 0.015 ? s(Blocks.BLACK_CONCRETE) : n < 0.07 ? s(Blocks.POLISHED_DEEPSLATE) : s(Blocks.GRAY_CONCRETE);
	}

	private static BlockState engineWall(HideoutCanvas c, int x, int y, int z) {
		double n = c.noise(x, y, z, 34);
		return n < 0.2 ? s(Blocks.DEEPSLATE_TILES) : n < 0.3 ? s(Blocks.CRACKED_DEEPSLATE_TILES) : s(Blocks.POLISHED_DEEPSLATE);
	}

	private interface Palette {
		BlockState at(HideoutCanvas c, int x, int y, int z);
	}

	private static void fillP(HideoutCanvas c, int x0, int y0, int z0, int x1, int y1, int z1, Palette p) {
		c.fill(x0, y0, z0, x1, y1, z1, a -> p.at(c, a[0], a[1], a[2]));
	}

	private static BlockState stair(Block b, Direction facing) {
		return s(b).setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	private static BlockState slab(Block b, SlabType t) {
		return s(b).setValue(SlabBlock.TYPE, t);
	}

	private static BlockState bars(boolean alongX) {
		BlockState b = s(Blocks.IRON_BARS);
		return alongX ? b.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
				: b.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
	}

	// ---------------------------------------------------------------- build

	public void build(HideoutCanvas c) {
		keel(c);
		nacelles(c);
		shell(c);
		hangar(c);
		engineRoom(c);
		cellBlock(c);
		stern(c);
		flightDeck(c);
		cabin(c);
		bridge(c);
		livery(c);
	}

	// ---------------------------------------------------------------- hull

	private void keel(HideoutCanvas c) {
		int[] half = { 15, 13, 12, 10, 9, 7, 5, 4 };
		for (int k = 1; k <= 7; k++) {
			int w = half[k];
			int z0 = BOW + 2 * k;
			int z1 = STERN - (int) Math.round(k * 1.4);
			fillP(c, -w, -k, z0, w, -k, z1, SkyFortressBuilder::keel);
		}
		// downlights along the keel line, nav lights at the bow
		for (int z = BOW + 16; z <= STERN - 12; z += 6) {
			c.set(0, -7, z, Blocks.SEA_LANTERN);
		}
		for (int z = BOW + 6; z <= STERN - 4; z += 8) {
			c.set(-X, 0, z, Blocks.SHROOMLIGHT);
			c.set(X, 0, z, Blocks.VERDANT_FROGLIGHT);
		}
		// the lift shaft through the keel under the hatch
		c.air(HATCH.getX() - 1, -4, HATCH.getZ() - 1, HATCH.getX() + 1, -1, HATCH.getZ() + 1);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
					c.set(HATCH.getX() + dx, -4, HATCH.getZ() + dz, (dx + dz) % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE);
				}
			}
		}
	}

	private void nacelles(HideoutCanvas c) {
		for (int sx = -1; sx <= 1; sx += 2) {
			int cx = 20 * sx;
			for (int z = -12; z <= 10; z++) {
				for (int dx = -3; dx <= 3; dx++) {
					for (int dy = -3; dy <= 3; dy++) {
						double r2 = dx * dx + dy * dy;
						if (r2 > 7.3) {
							continue;
						}
						BlockState st;
						if (z == -12) {
							st = r2 <= 1.1 ? s(Blocks.IRON_BLOCK) : s(Blocks.BLACK_CONCRETE);
						} else if (z == 10) {
							st = r2 <= 1.1 ? s(Blocks.SEA_LANTERN) : r2 <= 4.1 ? s(Blocks.LIGHT_BLUE_STAINED_GLASS) : s(Blocks.GRAY_CONCRETE);
						} else if (z == -8 || z == 6) {
							st = s(Blocks.BLUE_CONCRETE);
						} else {
							st = r2 > 4.1 ? hull(c, cx + dx, 1 + dy, z) : s(Blocks.GRAY_CONCRETE);
						}
						c.set(cx + dx, 1 + dy, z, st);
					}
				}
			}
			// the exhaust's outer glow ring and a tail fin
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					if (dx != 0 || dy != 0) {
						c.set(cx + dx, 1 + dy, 11, Blocks.CYAN_STAINED_GLASS);
					}
				}
			}
			c.fill(cx, 4, 2, cx, 4, 9, s(Blocks.LIGHT_GRAY_CONCRETE));
			c.fill(cx, 5, 6, cx, 5, 9, s(Blocks.BLUE_CONCRETE));
			// the pylon to the hull
			for (int x = 16; x <= 17; x++) {
				fillP(c, x * sx, 0, -5, x * sx, 1, 3, SkyFortressBuilder::hull);
			}
			c.set(18 * sx, 1, -1, Blocks.IRON_BLOCK);
		}
	}

	private void shell(HideoutCanvas c) {
		fillP(c, -X, 0, BOW, X, 0, STERN, SkyFortressBuilder::hull);
		fillP(c, -X, 1, BOW, -X, 7, STERN, SkyFortressBuilder::hull);
		fillP(c, X, 1, BOW, X, 7, STERN, SkyFortressBuilder::hull);
		fillP(c, -X, 1, STERN, X, 7, STERN, SkyFortressBuilder::hull);
		fillP(c, -X, 1, BOW, X, 7, BOW, SkyFortressBuilder::hull);
		fillP(c, -X, 7, BOW, X, 7, STERN, SkyFortressBuilder::hull);
		c.fill(-X, DECK, BOW, X, DECK, STERN, a -> deck(c, a[0], a[2]));
		c.air(-X + 1, 1, BOW + 1, X - 1, 6, STERN - 1);
		// partitions
		for (int z : new int[] { P_HANGAR, P_ENGINE, P_CELL }) {
			fillP(c, -X + 1, 1, z, X - 1, 6, z, SkyFortressBuilder::hull);
		}
		// the hangar mouth and its apron, hazard-striped frame
		c.air(-11, 1, BOW, 11, 6, BOW);
		c.fill(-12, 0, BOW - 3, 12, 0, BOW - 1, s(Blocks.GRAY_CONCRETE));
		for (int x = -12; x <= 12; x++) {
			c.set(x, 0, BOW - 3, (x & 1) == 0 ? s(Blocks.YELLOW_CONCRETE) : s(Blocks.BLACK_CONCRETE));
		}
		for (int y = 1; y <= 6; y++) {
			c.set(-12, y, BOW, (y & 1) == 0 ? s(Blocks.YELLOW_CONCRETE) : s(Blocks.BLACK_CONCRETE));
			c.set(12, y, BOW, (y & 1) == 1 ? s(Blocks.YELLOW_CONCRETE) : s(Blocks.BLACK_CONCRETE));
		}
		// the lift hatch through the hangar floor
		c.air(HATCH.getX() - 1, 0, HATCH.getZ() - 1, HATCH.getX() + 1, 0, HATCH.getZ() + 1);
	}

	private void livery(HideoutCanvas c) {
		for (int z = BOW + 1; z < STERN; z++) {
			for (int sx = -1; sx <= 1; sx += 2) {
				c.set(sx * X, 2, z, Blocks.BLUE_CONCRETE);
				c.set(sx * X, 6, z, Blocks.GRAY_CONCRETE);
				if ((z - BOW) % 6 == 3 && z > BOW + 4) {
					c.set(sx * X, 4, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
				}
			}
		}
		for (int x = -X; x <= X; x++) {
			c.set(x, 2, STERN, Blocks.BLUE_CONCRETE);
		}
		// stern thrusters
		for (int tx : new int[] { -8, 0, 8 }) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					c.set(tx + dx, 4 + dy, STERN + 1, dx == 0 && dy == 0 ? s(Blocks.SEA_LANTERN) : s(Blocks.IRON_BLOCK));
				}
			}
			c.set(tx, 4, STERN + 2, Blocks.LIGHT_BLUE_STAINED_GLASS);
		}
		// deck-edge bollards and lights
		for (int z = BOW + 3; z < 18; z += 6) {
			c.set(-X, DECK + 1, z, Blocks.POLISHED_BLACKSTONE_WALL);
			c.set(X, DECK + 1, z, Blocks.POLISHED_BLACKSTONE_WALL);
		}
		for (int z = BOW + 9; z < 18; z += 12) {
			for (int sx = -1; sx <= 1; sx += 2) {
				c.set(sx * (X - 1), DECK + 1, z, Blocks.IRON_BARS);
				c.set(sx * (X - 1), DECK + 2, z, s(Blocks.LANTERN).setValue(LanternBlock.HANGING, false));
			}
		}
	}

	// ---------------------------------------------------------------- hangar

	private void hangar(HideoutCanvas c) {
		int z0 = BOW;
		int z1 = P_HANGAR - 1;
		for (int x = -14; x <= 14; x++) {
			for (int z = z0; z <= z1; z++) {
				BlockState f = s(Blocks.GRAY_CONCRETE);
				if (Math.abs(x) == 12) {
					f = s(Blocks.YELLOW_CONCRETE);
				} else if (x == 0 && Math.floorMod(z, 3) == 0) {
					f = s(Blocks.WHITE_CONCRETE);
				} else if (c.noise(x, z, 40) < 0.06) {
					f = s(Blocks.LIGHT_GRAY_CONCRETE);
				}
				c.set(x, 0, z, f);
			}
		}
		// hatch ring
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
					c.set(HATCH.getX() + dx, 0, HATCH.getZ() + dz, (dx + dz) % 2 == 0 ? s(Blocks.YELLOW_CONCRETE) : s(Blocks.BLACK_CONCRETE));
				}
			}
		}
		c.air(HATCH.getX() - 1, 0, HATCH.getZ() - 1, HATCH.getX() + 1, 0, HATCH.getZ() + 1);
		// ceiling lights
		for (int x = -10; x <= 10; x += 5) {
			for (int z = z0 + 3; z <= z1; z += 6) {
				if (Math.abs(x - HATCH.getX()) <= 1 && Math.abs(z - HATCH.getZ()) <= 1) {
					continue;
				}
				c.set(x, 7, z, Blocks.SEA_LANTERN);
			}
		}
		jet(c, -8, -40);
		jet(c, 8, -33);
		// catwalks with a ladder at their inner end
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int z = z0 + 1; z <= z1 - 1; z++) {
				c.set(sx * 13, 4, z, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.TOP));
				c.set(sx * 14, 4, z, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.TOP));
				c.set(sx * 12, 5, z, bars(false));
			}
			c.set(sx * 12, 5, z1 - 1, Blocks.AIR);
			for (int y = 1; y <= 4; y++) {
				c.set(sx * 14, y, z1, s(Blocks.LADDER).setValue(LadderBlock.FACING, sx < 0 ? Direction.EAST : Direction.WEST));
			}
		}
		// cargo: barrels, crates, fuel tanks, the Parachute Crates by the mouth
		for (int z = -31; z <= -28; z++) {
			c.set(-11, 1, z, s(Blocks.BARREL));
			if (z != -29) {
				c.set(-11, 2, z, s(Blocks.SPRUCE_PLANKS));
			}
		}
		c.container(-11, 1, -27, s(Blocks.BARREL), HideoutCanvas.table("chests/vault_tier2"));
		for (int z = -40; z <= -38; z++) {
			c.set(11, 1, z, Blocks.IRON_BLOCK);
			c.set(11, 2, z, Blocks.CAULDRON);
		}
		c.set(10, 1, -39, Blocks.CHAIN);
		c.set(-13, 1, BOW + 2, SkyFortressBlocks.PARACHUTE_CRATE);
		c.set(13, 1, BOW + 2, SkyFortressBlocks.PARACHUTE_CRATE);
		c.set(-13, 2, BOW + 2, Blocks.WHITE_BANNER);
		// turrets covering the engine door, alarm kit
		c.set(-5, 6, -24, VaultBlocks.TURRET);
		c.set(5, 6, -24, VaultBlocks.TURRET);
		c.alarmPanel(14, 1, -26, Direction.WEST);
		c.camera(-13, 5, P_HANGAR - 1, Direction.NORTH);
		c.camera(13, 5, P_HANGAR - 1, Direction.NORTH);
		c.alarmLamp(-7, 6, -30);
		c.alarmLamp(7, 6, -30);
		c.alarmLamp(0, 6, -23);
		c.spawnPoint(-9, 1, BOW + 3);
		c.spawnPoint(9, 1, BOW + 3);
		c.spawnPoint(-9, 1, -23);
		c.spawnPoint(9, 1, -23);
		blastDoor(c, -1, 1, P_HANGAR, 1, 3, P_HANGAR, false);
		guard(c, Kind.TROOPER, 0, 1, -25, Direction.NORTH, 5, GuardRole.SHIELD);
		guard(c, Kind.TROOPER, -6, 1, -29, Direction.EAST, 6, null);
		guard(c, Kind.TROOPER, 5, 1, -41, Direction.NORTH, 6, null);
		guard(c, Kind.TROOPER, 11, 1, -28, Direction.WEST, 4, GuardRole.RUNNER);
		guard(c, Kind.RIFLE, -13, 5, -32, Direction.EAST, 2, GuardRole.SNIPER);
	}

	/** A parked jet, nose toward local north, its centre line at {@code x}, nose at {@code z}. */
	private void jet(HideoutCanvas c, int x, int z) {
		c.set(x, 1, z, Blocks.LIGHT_GRAY_CONCRETE);
		c.set(x, 1, z + 1, Blocks.LIGHT_GRAY_CONCRETE);
		c.set(x, 2, z + 1, Blocks.GRAY_STAINED_GLASS);
		c.set(x, 2, z + 2, Blocks.LIGHT_BLUE_STAINED_GLASS);
		c.set(x, 2, z + 3, Blocks.GRAY_STAINED_GLASS);
		for (int dz = 2; dz <= 8; dz++) {
			c.set(x, 1, z + dz, dz >= 4 ? s(Blocks.GRAY_CONCRETE) : s(Blocks.LIGHT_GRAY_CONCRETE));
			if (dz >= 4) {
				c.set(x, 2, z + dz, Blocks.LIGHT_GRAY_CONCRETE);
			}
		}
		c.set(x, 2, z + 5, Blocks.BLUE_CONCRETE);
		c.set(x, 1, z + 9, Blocks.BLACK_CONCRETE);
		c.set(x, 2, z + 9, Blocks.BLACK_CONCRETE);
		c.set(x, 1, z + 10, Blocks.ORANGE_STAINED_GLASS);
		// intakes
		c.set(x - 1, 1, z + 4, Blocks.BLACK_CONCRETE);
		c.set(x + 1, 1, z + 4, Blocks.BLACK_CONCRETE);
		// wings (swept), wingtips navy
		for (int w = 1; w <= 4; w++) {
			for (int sx = -1; sx <= 1; sx += 2) {
				int dz0 = 4 + w / 2;
				for (int dz = dz0; dz <= 7; dz++) {
					c.set(x + sx * w, 1, z + dz, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.TOP));
				}
				if (w == 4) {
					c.set(x + sx * w, 1, z + 7, s(Blocks.BLUE_CONCRETE));
				}
			}
		}
		// tailplanes and twin fins
		for (int sx = -1; sx <= 1; sx += 2) {
			c.set(x + sx, 1, z + 8, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.TOP));
			c.set(x + sx * 2, 1, z + 9, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.TOP));
			c.set(x + sx, 3, z + 8, Blocks.LIGHT_GRAY_CONCRETE);
			c.set(x + sx, 3, z + 9, Blocks.LIGHT_GRAY_CONCRETE);
			c.set(x + sx, 4, z + 9, Blocks.BLUE_CONCRETE);
			c.set(x + sx, 2, z + 8, Blocks.LIGHT_GRAY_CONCRETE);
			c.set(x + sx, 2, z + 9, Blocks.GRAY_CONCRETE);
		}
		// wheel chocks
		c.set(x - 2, 1, z + 6, Blocks.YELLOW_TERRACOTTA);
		c.set(x + 2, 1, z + 6, Blocks.YELLOW_TERRACOTTA);
	}

	// ---------------------------------------------------------------- engine room

	private void engineRoom(HideoutCanvas c) {
		int z0 = P_HANGAR + 1;
		int z1 = P_ENGINE - 1;
		for (int x = -14; x <= 14; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, 0, z, Math.abs(x) <= 1 ? s(Blocks.WAXED_CUT_COPPER) : Math.abs(x) == 2 ? s(Blocks.YELLOW_CONCRETE)
						: s(Blocks.POLISHED_DEEPSLATE));
			}
			fillP(c, -14, 1, z0, -14, 6, z1, SkyFortressBuilder::engineWall);
			fillP(c, 14, 1, z0, 14, 6, z1, SkyFortressBuilder::engineWall);
		}
		for (int z = z0; z <= z1; z++) {
			c.set(-13, 5, z, Blocks.WAXED_CUT_COPPER);
			c.set(13, 5, z, Blocks.WAXED_CUT_COPPER);
			c.set(-13, 6, z, Blocks.WAXED_EXPOSED_CUT_COPPER);
			c.set(13, 6, z, Blocks.WAXED_EXPOSED_CUT_COPPER);
		}
		for (BlockPos t : TURBINES) {
			turbine(c, t.getX() < 0 ? -1 : 1, t.getZ());
		}
		for (int z = z0 + 2; z <= z1; z += 5) {
			c.set(0, 7, z, Blocks.OCHRE_FROGLIGHT);
		}
		c.set(-6, 7, -11, Blocks.SEA_LANTERN);
		c.set(6, 7, -11, Blocks.SEA_LANTERN);
		// lasers: knee, head, knee
		c.laser(-6, 1, -17, Direction.EAST);
		c.laser(6, 2, -11, Direction.WEST);
		c.laser(-6, 1, -4, Direction.EAST);
		c.camera(0, 5, z0, Direction.SOUTH);
		c.camera(-4, 5, z1, Direction.NORTH);
		c.alarmPanel(-13, 1, -11, Direction.EAST);
		c.alarmLamp(0, 6, -11);
		c.alarmLamp(-10, 6, -11);
		c.alarmLamp(10, 6, -11);
		c.spawnPoint(11, 1, -11);
		c.spawnPoint(-11, 1, -10);
		blastDoor(c, -1, 1, P_ENGINE, 1, 3, P_ENGINE, false);
		guard(c, Kind.TROOPER, 0, 1, -14, Direction.NORTH, 5, null);
		guard(c, Kind.TROOPER, 3, 1, -8, Direction.WEST, 5, GuardRole.MEDIC);
		guard(c, Kind.GUNNER, -3, 1, -6, Direction.NORTH, 5, null);
		guard(c, Kind.TROOPER, 0, 1, -4, Direction.NORTH, 4, GuardRole.SHIELD);
	}

	/** A turbine housing on side {@code sx} centred on {@code cz}; its core faces the centre walk at x = 7 * sx. */
	private void turbine(HideoutCanvas c, int sx, int cz) {
		int xi = 7 * sx;
		int xo = 13 * sx;
		for (int y = 1; y <= 5; y++) {
			for (int x = Math.min(xi, xo); x <= Math.max(xi, xo); x++) {
				for (int z = cz - 2; z <= cz + 2; z++) {
					boolean corner = (x == xi || x == xo) && Math.abs(z - cz) == 2;
					if (corner) {
						continue;
					}
					c.set(x, y, z, (y == 1 || y == 5) ? s(Blocks.IRON_BLOCK) : s(Blocks.POLISHED_DEEPSLATE));
				}
			}
		}
		c.set(xi, 2, cz, SkyFortressBlocks.TURBINE);
		c.set(xi, 3, cz, Blocks.LIGHT_BLUE_STAINED_GLASS);
		c.set(xi, 2, cz - 1, Blocks.LIGHT_BLUE_STAINED_GLASS);
		c.set(xi, 2, cz + 1, Blocks.LIGHT_BLUE_STAINED_GLASS);
		c.set(xi, 4, cz, Blocks.SEA_LANTERN);
		// exhaust stacks to the ceiling, warning stripes round the core
		c.set(10 * sx, 6, cz, Blocks.WAXED_COPPER_BLOCK);
		c.set(10 * sx, 6, cz - 1, Blocks.LIGHTNING_ROD);
		c.set(10 * sx, 6, cz + 1, Blocks.LIGHTNING_ROD);
		for (int z = cz - 2; z <= cz + 2; z++) {
			c.set(6 * sx, 0, z, (z & 1) == 0 ? s(Blocks.YELLOW_CONCRETE) : s(Blocks.BLACK_CONCRETE));
		}
	}

	// ---------------------------------------------------------------- cell block

	private void cellBlock(HideoutCanvas c) {
		int z0 = P_ENGINE + 1;
		int z1 = P_CELL - 1;
		for (int x = -14; x <= 14; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, 0, z, Math.floorMod(z, 4) == 0 && Math.abs(x) < 9 ? s(Blocks.LIGHT_GRAY_CONCRETE) : s(Blocks.WHITE_CONCRETE));
			}
		}
		for (int x = -6; x <= 6; x += 6) {
			for (int z = z0 + 2; z <= z1; z += 5) {
				c.set(x, 7, z, Blocks.SEA_LANTERN);
			}
		}
		// six cells, three a side
		int prisoner = 0;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int cz : new int[] { 0, 5, 10 }) {
				for (int dz : new int[] { -1, 4 }) {
					c.fill(sx * 10, 1, cz + dz, sx * 14, 6, cz + dz, s(Blocks.WHITE_CONCRETE));
					c.fill(sx * 9, 1, cz + dz, sx * 9, 6, cz + dz, s(Blocks.IRON_BLOCK));
				}
				for (int z = cz; z <= cz + 3; z++) {
					for (int y = 1; y <= 4; y++) {
						c.set(sx * 9, y, z, bars(false));
					}
					c.fill(sx * 9, 5, z, sx * 9, 6, z, s(Blocks.WHITE_CONCRETE));
				}
				Direction doorFacing = sx < 0 ? Direction.EAST : Direction.WEST;
				BlockState lower = s(Blocks.IRON_DOOR).setValue(DoorBlock.FACING, doorFacing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
				c.set(sx * 9, 1, cz + 1, lower);
				c.set(sx * 9, 2, cz + 1, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
				c.set(sx * 8, 2, cz - 1, s(Blocks.STONE_BUTTON).setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, doorFacing));
				c.set(sx * 13, 1, cz, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
				c.set(sx * 13, 1, cz + 1, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
				c.set(sx * 13, 1, cz + 3, Blocks.CAULDRON);
				c.set(sx * 11, 6, cz + 2, s(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
				boolean occupied = (sx < 0 && cz != 5) || (sx > 0 && cz != 0);
				if (occupied) {
					prisoner(c, sx * 12, 1, cz + 2, prisoner++ & 1);
				}
			}
		}
		// the guard desk with Panel #3
		for (int x = -2; x <= 2; x++) {
			if (x != 0) {
				c.set(x, 1, 7, Blocks.SMOOTH_QUARTZ);
			}
		}
		c.set(-2, 1, 6, Blocks.SMOOTH_QUARTZ);
		c.set(2, 1, 6, Blocks.SMOOTH_QUARTZ);
		c.alarmPanel(0, 1, 7, Direction.NORTH);
		c.set(0, 1, 9, stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
		c.camera(5, 5, z0, Direction.SOUTH);
		c.camera(-5, 5, z1, Direction.NORTH);
		c.alarmLamp(0, 6, 3);
		c.alarmLamp(0, 6, 11);
		c.spawnPoint(-6, 1, 13);
		c.spawnPoint(6, 1, 13);
		// the stair-hall door: the energy shield in an emitter frame; side doors to the armory / barracks
		for (int y = 1; y <= 3; y++) {
			c.set(-2, y, P_CELL, Blocks.POLISHED_BLACKSTONE);
			c.set(2, y, P_CELL, Blocks.POLISHED_BLACKSTONE);
		}
		c.set(0, 4, P_CELL, Blocks.SEA_LANTERN);
		c.set(-2, 4, P_CELL - 1, Blocks.END_ROD);
		c.set(2, 4, P_CELL - 1, Blocks.END_ROD);
		for (BlockPos p : SHIELD) {
			c.set(p.getX(), p.getY(), p.getZ(), SkyFortressBlocks.SHIELD);
		}
		c.air(-7, 1, P_CELL, -7, 2, P_CELL);
		c.air(7, 1, P_CELL, 7, 2, P_CELL);
		guard(c, Kind.TROOPER, 0, 1, 10, Direction.NORTH, 4, GuardRole.CAPTAIN);
		guard(c, Kind.TROOPER, -5, 1, 12, Direction.EAST, 4, GuardRole.RUNNER);
		guard(c, Kind.TROOPER, 4, 1, 2, Direction.NORTH, 4, GuardRole.SHIELD);
		guard(c, Kind.GUNNER, 6, 1, 11, Direction.WEST, 4, null);
		guard(c, Kind.TROOPER, -5, 1, 1, Direction.SOUTH, 4, null);
	}

	private void prisoner(HideoutCanvas c, int x, int y, int z, int variant) {
		BlockPos p = c.at(x, y, z);
		if (!c.inClip(p)) {
			return;
		}
		SkyPrisoner m = SkyFortressEntityTypes.PRISONER.create(c.level.getLevel());
		if (m == null) {
			return;
		}
		float yaw = c.yaw(x < 0 ? Direction.EAST : Direction.WEST);
		m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0f);
		m.setYHeadRot(yaw);
		m.setVariant(variant);
		m.setPersistenceRequired();
		m.restrictTo(p, 2);
		c.level.addFreshEntityWithPassengers(m);
	}

	// ---------------------------------------------------------------- stern: stair hall, armory, barracks

	private void stern(HideoutCanvas c) {
		int z0 = P_CELL + 1;
		int z1 = STERN - 1;
		for (int sx = -1; sx <= 1; sx += 2) {
			fillP(c, sx * 5, 1, z0, sx * 5, 6, z1, SkyFortressBuilder::hull);
		}
		for (int x = -4; x <= 4; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, 0, z, Math.abs(x) <= 1 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.GRAY_CONCRETE));
			}
		}
		// stairs climbing north from z 30 to the bridge floor (landing at z 23)
		for (int k = 0; k <= 6; k++) {
			int z = 30 - k;
			for (int x = -1; x <= 1; x++) {
				for (int y = 1; y <= k; y++) {
					c.set(x, y, z, hull(c, x, y, z));
				}
				c.set(x, k + 1, z, stair(Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH));
			}
		}
		c.fill(-1, 1, 23, 1, 6, 23, s(Blocks.POLISHED_ANDESITE));
		c.air(-1, 7, 24, 1, DECK, 27);
		for (int z = z0 + 1; z <= z1; z += 4) {
			c.set(-3, 7, z, Blocks.SEA_LANTERN);
			c.set(3, 7, z, Blocks.SEA_LANTERN);
		}
		c.set(-4, 1, 18, Blocks.BARREL);
		c.set(4, 1, 18, Blocks.BARREL);
		// the armory
		for (int x = -14; x <= -6; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, 0, z, (x + z) % 4 == 0 ? s(Blocks.POLISHED_DEEPSLATE) : s(Blocks.DEEPSLATE_TILES));
			}
		}
		c.set(-13, 1, 18, Blocks.ANVIL);
		c.set(-13, 1, 20, Blocks.SMITHING_TABLE);
		c.set(-13, 1, 22, Blocks.GRINDSTONE);
		for (int z = 24; z <= 29; z++) {
			c.set(-14, 2, z, Blocks.IRON_BARS);
			c.set(-14, 3, z, Blocks.LIGHTNING_ROD);
		}
		c.container(-13, 1, 30, s(Blocks.BARREL), HideoutCanvas.table("chests/vault_tier3"));
		c.set(-10, 7, 23, Blocks.SEA_LANTERN);
		guard(c, Kind.TROOPER, -9, 1, 25, Direction.NORTH, 4, null);
		// the barracks
		for (int x = 6; x <= 14; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, 0, z, s(Blocks.SPRUCE_PLANKS));
			}
		}
		for (int z = 18; z <= 29; z += 4) {
			c.set(13, 1, z, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
			c.set(13, 1, z + 1, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
			c.set(13, 3, z, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
			c.set(13, 3, z + 1, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
			c.set(14, 2, z, Blocks.SPRUCE_FENCE);
			c.set(7, 1, z, Blocks.BARREL);
			c.set(7, 2, z, Blocks.BARREL);
		}
		c.set(10, 7, 23, Blocks.SEA_LANTERN);
		guard(c, Kind.GUNNER, 10, 1, 24, Direction.NORTH, 4, null);
	}

	// ---------------------------------------------------------------- flight deck

	private void flightDeck(HideoutCanvas c) {
		for (int x = -X; x <= X; x++) {
			for (int z = BOW; z <= STERN; z++) {
				BlockState f = deck(c, x, z);
				double ring = Math.sqrt(x * x + (z - KESTREL_HOME.getZ()) * (z - KESTREL_HOME.getZ()));
				if (Math.abs(x) == 13 && z < 17) {
					f = s(Blocks.YELLOW_CONCRETE);
				} else if (x == 0 && Math.floorMod(z, 4) < 2 && z < 17 && z > BOW + 9) {
					f = s(Blocks.WHITE_CONCRETE);
				} else if (ring >= 6.5 && ring < 7.5) {
					f = s(Blocks.YELLOW_CONCRETE);
				} else if (Math.abs(x) == 5 && z >= BOW + 10 && z <= BOW + 22) {
					f = s(Blocks.BLACK_CONCRETE);
				}
				c.set(x, DECK, z, f);
			}
		}
		// Kestrel's chevron, pointing down the runway
		for (int i = 0; i <= 5; i++) {
			c.set(i, DECK, -24 - i, Blocks.ORANGE_CONCRETE);
			c.set(-i, DECK, -24 - i, Blocks.ORANGE_CONCRETE);
			if (i >= 1) {
				c.set(i, DECK, -23 - i, Blocks.ORANGE_CONCRETE);
				c.set(-i, DECK, -23 - i, Blocks.ORANGE_CONCRETE);
			}
		}
		// the hatch in the deck of the stair well is cut by the stern; wind vents gust toward the open edges
		vent(c, 5, -30, Direction.EAST);
		vent(c, -5, -20, Direction.WEST);
		vent(c, 5, -6, Direction.EAST);
		vent(c, -5, 4, Direction.WEST);
		// Admiral Kestrel
		boss(c);
	}

	private void vent(HideoutCanvas c, int x, int z, Direction facing) {
		c.set(x, DECK, z, s(SkyFortressBlocks.WIND_VENT).setValue(WindVentBlock.FACING, facing));
	}

	private void cabin(HideoutCanvas c) {
		int x0 = -6;
		int x1 = 6;
		int z0 = BOW + 1;
		int z1 = BOW + 8;
		fillP(c, x0, DECK + 1, z0, x1, DECK + 5, z1, SkyFortressBuilder::hull);
		c.fill(x0, DECK + 6, z0, x1, DECK + 6, z1, s(Blocks.GRAY_CONCRETE));
		c.air(x0 + 1, DECK + 1, z0 + 1, x1 - 1, DECK + 5, z1 - 1);
		for (int x = x0; x <= x1; x++) {
			c.set(x, DECK + 5, z0, Blocks.BLUE_CONCRETE);
			c.set(x, DECK + 5, z1, Blocks.BLUE_CONCRETE);
		}
		for (int z = z0 + 2; z <= z1 - 2; z++) {
			c.set(x0, DECK + 3, z, Blocks.GRAY_STAINED_GLASS);
			c.set(x1, DECK + 3, z, Blocks.GRAY_STAINED_GLASS);
		}
		c.air(-1, DECK + 1, z1, 1, DECK + 3, z1);
		c.set(-2, DECK + 4, z1 + 1, Blocks.LANTERN);
		c.set(2, DECK + 4, z1 + 1, Blocks.LANTERN);
		for (int x = x0 + 1; x <= x1 - 1; x++) {
			for (int z = z0 + 1; z <= z1 - 1; z++) {
				c.set(x, DECK, z, (x + z) % 2 == 0 ? s(Blocks.DARK_OAK_PLANKS) : s(Blocks.SPRUCE_PLANKS));
			}
		}
		c.set(VAULT.getX(), VAULT.getY(), VAULT.getZ(), s(SkyFortressBlocks.SEAL).setValue(SkyVaultSealBlock.FACING, Direction.SOUTH));
		c.set(-1, DECK + 1, z0 + 1, Blocks.POLISHED_DEEPSLATE);
		c.set(1, DECK + 1, z0 + 1, Blocks.POLISHED_DEEPSLATE);
		c.set(0, DECK + 2, z0 + 1, Blocks.SEA_LANTERN);
		c.set(-4, DECK + 1, z0 + 3, stair(Blocks.DARK_OAK_STAIRS, Direction.EAST));
		c.set(-3, DECK + 1, z0 + 3, Blocks.DARK_OAK_PLANKS);
		c.set(-3, DECK + 1, z0 + 4, Blocks.DARK_OAK_PLANKS);
		c.set(-3, DECK + 2, z0 + 3, Blocks.LANTERN);
		c.set(4, DECK + 1, z0 + 2, Blocks.CARTOGRAPHY_TABLE);
		c.container(5, DECK + 1, z0 + 1, s(Blocks.BARREL), HideoutCanvas.table("chests/vault_tier3"));
		c.set(5, DECK + 1, z1 - 1, Blocks.LODESTONE);
		c.set(0, DECK + 5, BOW + 4, Blocks.SEA_LANTERN);
		c.set(-5, DECK + 2, BOW + 1, Blocks.BLUE_BANNER);
	}

	// ---------------------------------------------------------------- command bridge

	private void bridge(HideoutCanvas c) {
		int x0 = -9;
		int x1 = 9;
		int z0 = 18;
		int z1 = STERN;
		int top = DECK + 7;
		fillP(c, x0, DECK + 1, z0, x1, top, z1, SkyFortressBuilder::hull);
		c.air(x0 + 1, DECK + 1, z0 + 1, x1 - 1, top - 1, z1 - 1);
		for (int x = x0; x <= x1; x++) {
			c.set(x, top - 1, z0, Blocks.BLUE_CONCRETE);
			c.set(x, top, z0, Blocks.GRAY_CONCRETE);
		}
		// the long front window over the flight deck, side windows
		for (int x = -7; x <= 7; x++) {
			for (int y = DECK + 2; y <= DECK + 4; y++) {
				c.set(x, y, z0, Blocks.GRAY_STAINED_GLASS);
			}
		}
		for (int z = 21; z <= 29; z++) {
			for (int y = DECK + 2; y <= DECK + 4; y++) {
				c.set(x0, y, z, Blocks.GRAY_STAINED_GLASS);
				c.set(x1, y, z, Blocks.GRAY_STAINED_GLASS);
			}
		}
		for (int x = x0 + 1; x <= x1 - 1; x++) {
			for (int z = z0 + 1; z <= z1 - 1; z++) {
				c.set(x, DECK, z, s(Blocks.POLISHED_DEEPSLATE));
				if (Math.abs(x) <= 1 && z < 23) {
					c.set(x, DECK + 1, z, Blocks.BLUE_CARPET);
				}
			}
		}
		// the stairwell from the stern comes up through the floor (z 24..27), railed
		c.air(-1, DECK, 24, 1, DECK, 27);
		c.air(-1, 7, 24, 1, 7, 27);
		for (int z = 24; z <= 28; z++) {
			c.set(-2, DECK + 1, z, bars(false));
			c.set(2, DECK + 1, z, bars(false));
		}
		for (int x = -1; x <= 1; x++) {
			c.set(x, DECK + 1, 28, bars(true));
		}
		// consoles under the window
		for (int x = -7; x <= 7; x++) {
			if (Math.abs(x) <= 2) {
				continue;
			}
			c.set(x, DECK + 1, z0 + 1, stair(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.NORTH));
			if ((x & 1) == 0) {
				c.set(x, DECK + 2, z0 + 1, Blocks.OBSERVER);
			}
		}
		c.set(-4, DECK + 1, 26, Blocks.CARTOGRAPHY_TABLE);
		c.set(4, DECK + 1, 26, Blocks.CARTOGRAPHY_TABLE);
		c.set(-5, DECK + 1, 26, Blocks.LECTERN);
		c.set(0, DECK + 1, 30, Blocks.SMOOTH_QUARTZ);
		c.set(0, DECK + 2, 30, stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
		for (int x = -4; x <= 4; x += 8) {
			c.set(x, top, 22, Blocks.SEA_LANTERN);
			c.set(x, top, 28, Blocks.SEA_LANTERN);
		}
		c.alarmPanel(8, DECK + 1, 30, Direction.WEST);
		c.alarmLamp(-5, top - 1, 25);
		c.alarmLamp(5, top - 1, 25);
		c.spawnPoint(-6, DECK + 1, 30);
		c.spawnPoint(6, DECK + 1, 22);
		blastDoor(c, -1, DECK + 1, z0, 1, DECK + 3, z0, true);
		c.set(-2, DECK + 4, z0 - 1, Blocks.ORANGE_CONCRETE);
		c.set(2, DECK + 4, z0 - 1, Blocks.ORANGE_CONCRETE);
		// a parapet and the radar mast on the roof; a sniper up there watching the deck
		for (int x = x0; x <= x1; x++) {
			c.set(x, top + 1, z0, Blocks.POLISHED_BLACKSTONE_WALL);
		}
		fillP(c, -2, top + 1, 27, 2, top + 4, 31, SkyFortressBuilder::hull);
		c.fill(-2, top + 4, 27, 2, top + 4, 31, s(Blocks.BLUE_CONCRETE));
		c.fill(0, top + 5, 29, 0, top + 8, 29, s(Blocks.IRON_BARS));
		c.set(0, top + 9, 29, Blocks.LIGHTNING_ROD);
		for (int d = 1; d <= 2; d++) {
			c.set(d, top + 6, 29, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
			c.set(-d, top + 6, 29, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
		}
		c.set(0, top + 7, 28, Blocks.END_ROD);
		c.set(-6, top + 1, 31, Blocks.IRON_BARS);
		c.set(-6, top + 2, 31, Blocks.SHROOMLIGHT);
		guard(c, Kind.RIFLE, -6, top + 1, 21, Direction.NORTH, 2, GuardRole.SNIPER);
		guard(c, Kind.GUNNER, -6, DECK + 1, 22, Direction.NORTH, 4, null);
		guard(c, Kind.GUNNER, 6, DECK + 1, 24, Direction.NORTH, 4, null);
		officer(c);
	}

	// ---------------------------------------------------------------- helpers

	private static void blastDoor(HideoutCanvas c, int x0, int y0, int z0, int x1, int y1, int z1, boolean locked) {
		c.blastDoor(x0, y0, z0, x1, y1, z1, locked);
	}

	private static void guard(HideoutCanvas c, Kind kind, int x, int y, int z, Direction facing, int leash, GuardRole role) {
		BlockPos p = c.at(x, y, z);
		if (!c.inClip(p)) {
			return;
		}
		SkyFortressGuards.spawn(c.level, kind, p, c.yaw(facing), leash, role);
	}

	private void officer(HideoutCanvas c) {
		BlockPos p = c.at(4, DECK + 1, 22);
		if (!c.inClip(p)) {
			return;
		}
		DeckOfficerBoss m = SkyFortressEntityTypes.DECK_OFFICER.create(c.level.getLevel());
		if (m == null) {
			return;
		}
		float yaw = c.yaw(Direction.NORTH);
		m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0f);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		m.finalizeSpawn(c.level, c.level.getCurrentDifficultyAt(p), MobSpawnType.STRUCTURE, null);
		m.setHome(c.at(OFFICER_HOME.getX(), OFFICER_HOME.getY(), OFFICER_HOME.getZ()));
		m.setPersistenceRequired();
		c.level.addFreshEntityWithPassengers(m);
	}

	private void boss(HideoutCanvas c) {
		BlockPos p = c.at(KESTREL_SPAWN.getX(), KESTREL_SPAWN.getY(), KESTREL_SPAWN.getZ());
		if (!c.inClip(p)) {
			return;
		}
		AdmiralKestrelBoss m = SkyFortressEntityTypes.ADMIRAL_KESTREL.create(c.level.getLevel());
		if (m == null) {
			return;
		}
		float yaw = c.yaw(Direction.SOUTH);
		m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0f);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		m.finalizeSpawn(c.level, c.level.getCurrentDifficultyAt(p), MobSpawnType.STRUCTURE, null);
		m.setHome(c.at(KESTREL_HOME.getX(), KESTREL_HOME.getY(), KESTREL_HOME.getZ()));
		m.setPersistenceRequired();
		c.level.addFreshEntityWithPassengers(m);
	}
}
