package com.projecthero.mod.loot.sites;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.loot.sites.SiteTheme.Entry;
import com.projecthero.mod.loot.sites.SiteTheme.Style;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * v0.17.5 Tier 2 Sites: the annex an old site gains -- a themed 13x11 building (barracks, lab wing, plant room, field
 * camp, research station, mausoleum, shrine, overrun lab or ice hut, {@link Style}) beside the original site, its door
 * and porch facing it, holding a <b>Tier 2 Hero Vault</b> in a secured room ({@link Entry}: a reinforced corner room
 * behind an iron door, or a hidden basement under a carpet-covered trapdoor) and the site's guard squad
 * ({@link SiteGuards}) inside and on the porch.
 *
 * <p>Built in local coordinates: building x -6..6, z -5..5, porch z 6..8, the door at local south (+z). {@code floorY}
 * is the first air block above the floor, fixed at planning time from the generator's own terrain height so every
 * chunk the annex crosses agrees. Every write is clipped to the chunk box being generated; variation comes from the
 * piece seed, never the per-chunk random. Guards are spawned only by the chunk that holds their feet.
 */
public class SiteAnnexPiece extends StructurePiece {
	static final int HX = 6;
	static final int HZ = 5;
	static final int PORCH = 8;
	/** v0.17.7: how far the back wing reaches (local z -13). */
	static final int BACK = 13;
	/** v0.17.7: the layout every new annex gets; annexes saved before the "Layout" tag load as 1 (the v0.17.5 plan). */
	public static final int LAYOUT = 2;

	private final SiteTheme theme;
	private final int cx;
	private final int cz;
	private final int floorY;
	private final int rotation;
	private final long seed;
	private final int layout;

	public SiteAnnexPiece(SiteTheme theme, int centerX, int floorY, int centerZ, int rotation, long seed) {
		super(SiteUpgrade.ANNEX_PIECE, 0, box(centerX, floorY, centerZ, rotation, LAYOUT));
		this.theme = theme;
		this.cx = centerX;
		this.cz = centerZ;
		this.floorY = floorY;
		this.rotation = Math.floorMod(rotation, 4);
		this.seed = seed;
		this.layout = LAYOUT;
	}

	public SiteAnnexPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(SiteUpgrade.ANNEX_PIECE, tag);
		SiteTheme t;
		try {
			t = SiteTheme.valueOf(tag.getString("Theme"));
		} catch (IllegalArgumentException e) {
			t = SiteTheme.RESEARCH_FACILITY;
		}
		this.theme = t;
		this.cx = tag.getInt("CX");
		this.cz = tag.getInt("CZ");
		this.floorY = tag.getInt("Floor");
		this.rotation = tag.getInt("Rot");
		this.seed = tag.getLong("Seed");
		this.layout = tag.contains("Layout") ? tag.getInt("Layout") : 1;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Theme", theme.name());
		tag.putInt("CX", cx);
		tag.putInt("CZ", cz);
		tag.putInt("Floor", floorY);
		tag.putInt("Rot", rotation);
		tag.putLong("Seed", seed);
		tag.putInt("Layout", layout);
	}

	public int layout() {
		return layout;
	}

	private static BoundingBox box(int cx, int floorY, int cz, int rotation, int layout) {
		int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
		int back = layout >= 2 ? BACK : HZ;
		for (int[] c : new int[][] { { -HX, -back }, { HX, -back }, { -HX, PORCH }, { HX, PORCH } }) {
			int x = cx + rx(rotation, c[0], c[1]);
			int z = cz + rz(rotation, c[0], c[1]);
			x0 = Math.min(x0, x);
			x1 = Math.max(x1, x);
			z0 = Math.min(z0, z);
			z1 = Math.max(z1, z);
		}
		// minY = the floor block: a beard_thin site (the Dig Site) then levels the ground flush with the floor; the
		// basement and footing below it are still written (writes are clipped by x / z only)
		return new BoundingBox(x0, floorY - 1, z0, x1, floorY + 8, z1);
	}

	public SiteTheme theme() {
		return theme;
	}

	public int floorY() {
		return floorY;
	}

	/** How many guards this annex spawns ({@code min..max} of its theme, from the seed). */
	public int guardCount() {
		int span = theme.maxGuards - theme.minGuards + 1;
		return theme.minGuards + (int) Math.floorMod(seed >>> 17, (long) Math.max(1, span));
	}

	/** Where the Tier 2 vault is (world coordinates). */
	public BlockPos vaultPos() {
		if (layout >= 2) {
			return at(VAULT_X, floorY, VAULT_Z);
		}
		return theme.entry == Entry.HATCH ? at(2, floorY - 4, -4) : at(4, floorY, -4);
	}

	// ---------------------------------------------------------------- v0.17.7 (layout 2) positions
	static final int VAULT_X = 3;
	static final int VAULT_Z = -11;

	/** v0.17.7: one panel of the locked Blast Door into the vault room (world). */
	public BlockPos blastDoorPos() {
		return at(2, floorY, -HZ);
	}

	/** v0.17.7: the Alarm Panel in the hall (world). */
	public BlockPos panelPos() {
		return at(5, floorY, -2);
	}

	/** v0.17.7: the Captain's post at his desk (world). */
	public BlockPos captainPost() {
		return at(3, floorY, -2);
	}

	/** v0.17.7: guards in the whole annex -- the squad, the Captain and the back-room guard. */
	public int totalGuards() {
		return layout >= 2 ? guardCount() + 2 : guardCount();
	}

	/** The guards' posts (world feet positions), in spawn order; the first {@link #guardCount()} are used. */
	public List<BlockPos> guardPosts() {
		List<BlockPos> out = new ArrayList<>();
		for (int[] p : POSTS) {
			out.add(at(p[0], floorY, p[1]));
		}
		return out;
	}

	/** A local (x, z) point (block-centre offsets allowed) in world x / z, for cameras and tests. */
	public double[] localToWorldExact(double dx, double dz) {
		// local block n spans [n, n+1); turn the offset from the centre of local block (0, 0) round the world block (cx, cz)
		double u = dx - 0.5, v = dz - 0.5;
		double x = switch (rotation) {
			case 1 -> -v;
			case 2 -> -u;
			case 3 -> v;
			default -> u;
		};
		double z = switch (rotation) {
			case 1 -> u;
			case 2 -> -v;
			case 3 -> -u;
			default -> v;
		};
		return new double[] { cx + 0.5 + x, cz + 0.5 + z };
	}

	/** The annex door (world), the step just outside it. */
	public BlockPos doorPos() {
		return at(0, floorY, HZ + 1);
	}

	/** Local (x, z) of the guard posts: porch left, inside, porch right, then the rest of the room. */
	private static final int[][] POSTS = { { -2, 7 }, { 0, -2 }, { 2, 7 }, { -3, 1 }, { 2, 2 }, { -3, -2 } };

	// ---------------------------------------------------------------- build

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		build(level, chunkBox);
	}

	/** Builds the part of the annex inside {@code box} (all of it with {@link #getBoundingBox()}). */
	public void build(WorldGenLevel level, BoundingBox box) {
		if (layout >= 2) {
			buildV2(level, box);
			return;
		}
		Palette p = Palette.of(theme.style);
		int f = floorY;
		BlockState window = theme.window != null ? theme.window.defaultBlockState() : p.window;

		// 1. ground: foundation under the building and porch, the floor, headroom cleared
		for (int x = -HX; x <= HX; x++) {
			for (int z = -HZ; z <= PORCH; z++) {
				boolean porch = z > HZ;
				if (porch && Math.abs(x) > 3) {
					continue;
				}
				footing(level, box, x, z, f - 2, p.foundation, porch ? 6 : 14);
				put(level, box, x, f - 1, z, porch ? p.porch : p.floor);
				for (int y = f; y <= f + (porch ? 3 : 8); y++) {
					put(level, box, x, y, z, AIR);
				}
			}
		}

		// 2. walls, corner pillars, roof
		for (int x = -HX; x <= HX; x++) {
			for (int z = -HZ; z <= HZ; z++) {
				boolean edgeX = Math.abs(x) == HX, edgeZ = Math.abs(z) == HZ;
				if (edgeX || edgeZ) {
					boolean corner = edgeX && edgeZ;
					for (int y = f; y <= f + 3; y++) {
						put(level, box, x, y, z, corner ? p.pillar : wallBlock(p, x, y, z));
					}
				}
				put(level, box, x, f + 4, z, p.roof);
				if ((edgeX || edgeZ) && p.parapet != null && ((x + z) & 1) == 0) {
					put(level, box, x, f + 5, z, p.parapet);
				}
			}
		}
		// windows (never on the east wall beside the vault corner)
		if (window != null) {
			for (int y = f + 1; y <= f + 2; y++) {
				put(level, box, -HX, y, -2, window);
				put(level, box, -HX, y, 2, window);
				put(level, box, HX, y, 3, window);
				put(level, box, -3, y, HZ, window);
				put(level, box, 3, y, HZ, window);
			}
		}
		// the doorway
		put(level, box, 0, f, HZ, AIR);
		put(level, box, 0, f + 1, HZ, AIR);
		put(level, box, 0, f + 2, HZ, p.lintel);

		// 3. the vault
		if (theme.entry == Entry.IRON_DOOR) {
			vaultRoom(level, box, p, f);
		} else {
			hatchBasement(level, box, p, f);
		}

		// 4. furniture, lights, outside touches
		decorate(level, box, p, f);

		// 5. the guard squad
		int n = guardCount();
		for (int i = 0; i < n && i < POSTS.length; i++) {
			int x = wx(POSTS[i][0], POSTS[i][1]), z = wz(POSTS[i][0], POSTS[i][1]);
			if (!inBox(box, x, z)) {
				continue;
			}
			float yaw = yawOf(dir(POSTS[i][1] > HZ ? Direction.SOUTH : Direction.NORTH));
			SiteGuards.spawn(level, theme.roster.get(i), new BlockPos(x, f, z), yaw, 14);
		}
	}

	// ---------------------------------------------------------------- v0.17.7: layout 2

	/**
	 * v0.17.7 layout: the same hall (furniture by {@link Style}) with a back wing behind it -- a themed back room
	 * (west) and the vault room (east) behind a <b>locked Blast Door</b> -- an <b>Alarm Panel</b> in the hall, the
	 * <b>Captain</b> at his desk (the keycard), an <b>Alarm Runner</b> on the porch, cameras (or a ward-stone tripwire in
	 * the old-world styles), lamps and reinforcement points. Local x -6..6, z -13..5, porch to z 8.
	 */
	private void buildV2(WorldGenLevel level, BoundingBox box) {
		Palette p = Palette.of(theme.style);
		int f = floorY;
		BlockState window = theme.window != null ? theme.window.defaultBlockState() : p.window;
		com.projecthero.mod.alarm.block.AlarmStyle style = theme.oldWorld() ? com.projecthero.mod.alarm.block.AlarmStyle.ANCIENT
				: com.projecthero.mod.alarm.block.AlarmStyle.TECH;

		// 1. ground: foundation under the building and porch, the floor, headroom cleared
		for (int x = -HX; x <= HX; x++) {
			for (int z = -BACK; z <= PORCH; z++) {
				boolean porch = z > HZ;
				if (porch && Math.abs(x) > 3) {
					continue;
				}
				footing(level, box, x, z, f - 2, p.foundation, porch ? 6 : 14);
				put(level, box, x, f - 1, z, porch ? p.porch : p.floor);
				for (int y = f; y <= f + (porch ? 3 : 8); y++) {
					put(level, box, x, y, z, AIR);
				}
			}
		}

		// 2. walls: the outer shell, the cross wall between hall and wing, the wing's divider; corner pillars; the roof
		for (int x = -HX; x <= HX; x++) {
			for (int z = -BACK; z <= HZ; z++) {
				boolean edgeX = Math.abs(x) == HX, edgeZ = z == HZ || z == -BACK;
				boolean inner = z == -HZ || (x == 0 && z < -HZ);
				boolean vaultSide = x >= 0 && z <= -HZ;
				if (edgeX || edgeZ || inner) {
					boolean corner = edgeX && (edgeZ || z == -HZ);
					for (int y = f; y <= f + 3; y++) {
						put(level, box, x, y, z, corner ? p.pillar : vaultSide ? p.secure : wallBlock(p, x, y, z));
					}
				}
				put(level, box, x, f + 4, z, p.roof);
				if ((edgeX || edgeZ) && p.parapet != null && ((x + z) & 1) == 0) {
					put(level, box, x, f + 5, z, p.parapet);
				}
				if (vaultSide && !edgeX && !edgeZ && x > 0 && z < -HZ) {
					put(level, box, x, f - 1, z, p.secure); // the vault room's reinforced floor
				}
			}
		}
		if (window != null) {
			for (int y = f + 1; y <= f + 2; y++) {
				put(level, box, -HX, y, -2, window);
				put(level, box, -HX, y, 2, window);
				put(level, box, HX, y, 3, window);
				put(level, box, -3, y, HZ, window);
				put(level, box, 3, y, HZ, window);
				put(level, box, -HX, y, -9, window);
			}
		}
		// the front doorway, the back room's doorway
		put(level, box, 0, f, HZ, AIR);
		put(level, box, 0, f + 1, HZ, AIR);
		put(level, box, 0, f + 2, HZ, p.lintel);
		put(level, box, -3, f, -HZ, AIR);
		put(level, box, -3, f + 1, -HZ, AIR);
		put(level, box, -3, f + 2, -HZ, p.lintel);

		// 3. the vault room behind its locked Blast Door (keycard from the Captain, or a cleared alarm wave)
		BlockState door = com.projecthero.mod.alarm.block.AlarmBlocks.BLAST_DOOR.defaultBlockState()
				.setValue(com.projecthero.mod.alarm.block.BlastDoorBlock.LOCKED, true).setValue(com.projecthero.mod.alarm.block.BlastDoorBlock.OPEN, false)
				.setValue(com.projecthero.mod.alarm.block.BlastDoorBlock.STYLE, style);
		for (int x = 2; x <= 3; x++) {
			for (int y = f; y <= f + 2; y++) {
				put(level, box, x, y, -HZ, door);
			}
		}
		put(level, box, 1, f + 3, -HZ, p.lintel);
		put(level, box, 4, f + 3, -HZ, p.lintel);
		put(level, box, VAULT_X, f - 1, VAULT_Z, Blocks.GOLD_BLOCK.defaultBlockState());
		vault(level, box, VAULT_X, f, VAULT_Z, Direction.SOUTH);
		put(level, box, 5, f, -12, Blocks.LANTERN.defaultBlockState());
		put(level, box, 1, f, -12, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		put(level, box, 5, f, -7, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		put(level, box, 1, f, -7, Blocks.CHAIN.defaultBlockState());

		// 4. the hall's furniture (as layout 1) and the Captain's desk; the back room in the site's theme
		decorate(level, box, p, f);
		table(level, box, 4, f, -4, theme.oldWorld() ? Blocks.SPRUCE_TRAPDOOR : Blocks.IRON_TRAPDOOR);
		put(level, box, 4, f + 1, -4, theme.oldWorld() ? Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true)
				: Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
		themeRoom(level, box, p, f);

		// 5. the alarm: panel, lamps, cameras or a ward-stone tripwire, reinforcement points
		put(level, box, 5, f, -2, com.projecthero.mod.alarm.block.AlarmBlocks.PANEL.defaultBlockState()
				.setValue(com.projecthero.mod.alarm.block.AlarmPanelBlock.FACING, Direction.WEST)
				.setValue(com.projecthero.mod.alarm.block.AlarmPanelBlock.STYLE, style));
		BlockState lamp = com.projecthero.mod.alarm.block.AlarmBlocks.LAMP.defaultBlockState()
				.setValue(com.projecthero.mod.alarm.block.AlarmLampBlock.STYLE, style);
		put(level, box, 0, f + 4, -2, lamp);
		put(level, box, 3, f + 4, -9, lamp);
		put(level, box, -3, f + 4, -9, lamp);
		if (theme.oldWorld()) {
			// a string across the back room's doorway, its hook hung on a ward stone in the wall
			put(level, box, -5, f, -HZ, SiteUpgrade.WARD.defaultBlockState());
			BlockState hook = Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(net.minecraft.world.level.block.TripWireHookBlock.ATTACHED, true);
			put(level, box, -4, f, -HZ, hook.setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, Direction.EAST));
			put(level, box, -2, f, -HZ, hook.setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, Direction.WEST));
			put(level, box, -3, f, -HZ, Blocks.TRIPWIRE.defaultBlockState().setValue(net.minecraft.world.level.block.TripWireBlock.ATTACHED, true)
					.setValue(net.minecraft.world.level.block.TripWireBlock.EAST, true).setValue(net.minecraft.world.level.block.TripWireBlock.WEST, true));
		} else {
			BlockState cam = com.projecthero.mod.alarm.block.AlarmBlocks.CAMERA.defaultBlockState();
			put(level, box, -4, f + 3, HZ + 1, cam.setValue(com.projecthero.mod.alarm.block.SecurityCameraBlock.FACING, Direction.SOUTH));
			put(level, box, -5, f + 3, HZ - 1, cam.setValue(com.projecthero.mod.alarm.block.SecurityCameraBlock.FACING, Direction.NORTH));
		}
		BlockState spawn = com.projecthero.mod.alarm.block.AlarmBlocks.SPAWN_POINT.defaultBlockState();
		put(level, box, -2, f, PORCH, spawn);
		put(level, box, 2, f, PORCH, spawn);
		put(level, box, -3, f, -10, spawn);

		// 6. the guards: the squad (the first, on the porch, is the Alarm Runner), the Captain, the back-room guard
		int n = guardCount();
		for (int i = 0; i < n && i < POSTS.length; i++) {
			guardAt(level, box, theme.roster.get(i), POSTS[i][0], POSTS[i][1], i == 0 ? com.projecthero.mod.alarm.GuardRole.RUNNER : null);
		}
		guardAt(level, box, theme.captainKind(), 3, -2, com.projecthero.mod.alarm.GuardRole.CAPTAIN);
		guardAt(level, box, theme.backRoomKind(), -3, -8, null);
	}

	private void guardAt(WorldGenLevel level, BoundingBox box, SiteGuards.Kind kind, int lx, int lz, com.projecthero.mod.alarm.GuardRole role) {
		int x = wx(lx, lz), z = wz(lx, lz);
		if (!inBox(box, x, z)) {
			return;
		}
		float yaw = yawOf(dir(lz > HZ ? Direction.SOUTH : Direction.NORTH));
		SiteGuards.spawn(level, kind, new BlockPos(x, floorY, z), yaw, role == com.projecthero.mod.alarm.GuardRole.CAPTAIN ? 6 : 14, true, role);
	}

	/** v0.17.7: the back room (local x -5..-1, z -12..-6), dressed for the site's {@link Style}, with a Tier 1 barrel. */
	private void themeRoom(WorldGenLevel level, BoundingBox box, Palette p, int f) {
		BlockState up = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
		switch (theme.style) {
			case LAB -> { // specimen room: two glass tanks, a bench of brewing stands, a cauldron
				for (int z : new int[] { -11, -8 }) {
					for (int y = f; y <= f + 2; y++) {
						put(level, box, -5, y, z, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
					}
					put(level, box, -5, f + 3, z, Blocks.SEA_LANTERN.defaultBlockState());
				}
				table(level, box, -3, f, -12, Blocks.IRON_TRAPDOOR);
				table(level, box, -2, f, -12, Blocks.IRON_TRAPDOOR);
				put(level, box, -3, f + 1, -12, Blocks.BREWING_STAND.defaultBlockState());
				put(level, box, -2, f + 1, -12, Blocks.BREWING_STAND.defaultBlockState());
				put(level, box, -1, f, -9, Blocks.CAULDRON.defaultBlockState());
				put(level, box, -5, f, -6, Blocks.WHITE_SHULKER_BOX.defaultBlockState());
			}
			case MILITARY -> { // armoury: smithing, anvil, crates, a target, a rack of bars
				put(level, box, -5, f, -12, Blocks.SMITHING_TABLE.defaultBlockState());
				put(level, box, -4, f, -12, up);
				put(level, box, -4, f + 1, -12, up);
				put(level, box, -3, f, -12, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
				put(level, box, -5, f, -8, Blocks.TARGET.defaultBlockState());
				for (int z = -11; z <= -9; z++) {
					put(level, box, -5, f + 1, z, Blocks.IRON_BARS.defaultBlockState());
				}
				put(level, box, -1, f, -7, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
			}
			case BUNKER -> { // generator room: an iron generator block with copper lights, rods, chains
				for (int x = -4; x <= -2; x++) {
					for (int z = -11; z <= -10; z++) {
						put(level, box, x, f, z, Blocks.IRON_BLOCK.defaultBlockState());
						put(level, box, x, f + 1, z, (x + z) % 2 == 0 ? Blocks.COPPER_BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true)
								: Blocks.WAXED_OXIDIZED_COPPER_GRATE.defaultBlockState());
					}
				}
				put(level, box, -4, f + 2, -11, Blocks.LIGHTNING_ROD.defaultBlockState());
				put(level, box, -2, f + 2, -10, Blocks.LIGHTNING_ROD.defaultBlockState());
				put(level, box, -5, f + 3, -7, Blocks.CHAIN.defaultBlockState());
				put(level, box, -5, f, -7, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
			}
			case CAMP -> { // stash room: hay, crates, a bedroll, scaffolding shelves
				put(level, box, -5, f, -12, Blocks.HAY_BLOCK.defaultBlockState());
				put(level, box, -4, f, -12, Blocks.HAY_BLOCK.defaultBlockState());
				put(level, box, -5, f + 1, -12, Blocks.HAY_BLOCK.defaultBlockState());
				put(level, box, -3, f, -12, up);
				put(level, box, -3, f + 1, -12, up);
				put(level, box, -5, f, -9, Blocks.SCAFFOLDING.defaultBlockState());
				put(level, box, -5, f + 1, -9, Blocks.SCAFFOLDING.defaultBlockState());
				put(level, box, -2, f, -8, Blocks.WHITE_CARPET.defaultBlockState());
				put(level, box, -2, f, -7, Blocks.WHITE_CARPET.defaultBlockState());
				put(level, box, -5, f, -6, Blocks.LANTERN.defaultBlockState());
			}
			case STATION -> { // instrument room: sample cases, a tinted cabinet, observers
				table(level, box, -5, f, -12, Blocks.IRON_TRAPDOOR);
				table(level, box, -4, f, -12, Blocks.IRON_TRAPDOOR);
				put(level, box, -5, f + 1, -12, Blocks.AMETHYST_CLUSTER.defaultBlockState());
				put(level, box, -4, f + 1, -12, Blocks.TINTED_GLASS.defaultBlockState());
				put(level, box, -5, f, -9, Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
				put(level, box, -5, f + 1, -9, Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
				put(level, box, -2, f, -12, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
				put(level, box, -1, f, -7, up);
			}
			case CRYPT -> { // ossuary: bone walls, skulls, a sarcophagus, candles, webs
				for (int z = -12; z <= -9; z++) {
					put(level, box, -5, f + 1, z, Blocks.BONE_BLOCK.defaultBlockState());
					put(level, box, -5, f + 2, z, Blocks.BONE_BLOCK.defaultBlockState());
				}
				put(level, box, -5, f + 1, -10, Blocks.SKELETON_SKULL.defaultBlockState());
				for (int z = -11; z <= -10; z++) {
					put(level, box, -3, f, z, Blocks.STONE_BRICK_SLAB.defaultBlockState());
				}
				candles(level, box, -2, f, -12, 3);
				candles(level, box, -1, f, -9, 2);
				put(level, box, -4, f + 3, -7, Blocks.COBWEB.defaultBlockState());
				put(level, box, -1, f + 3, -12, Blocks.COBWEB.defaultBlockState());
			}
			case TEMPLE -> { // inner shrine: two pillars, an altar with candles, pots
				for (int y = f; y <= f + 3; y++) {
					put(level, box, -4, y, -10, Blocks.CHISELED_SANDSTONE.defaultBlockState());
					put(level, box, -2, y, -10, Blocks.CHISELED_SANDSTONE.defaultBlockState());
				}
				put(level, box, -3, f, -12, Blocks.CHISELED_SANDSTONE.defaultBlockState());
				candles(level, box, -3, f + 1, -12, 4);
				put(level, box, -5, f, -12, Blocks.DECORATED_POT.defaultBlockState());
				put(level, box, -1, f, -12, Blocks.DECORATED_POT.defaultBlockState());
				put(level, box, -3, f - 1, -8, Blocks.BLUE_TERRACOTTA.defaultBlockState());
			}
			case INFESTED -> { // the breach: a cracked cell, black biomass, webs, sculk
				for (int z = -12; z <= -10; z++) {
					put(level, box, -3, f, z, Blocks.IRON_BARS.defaultBlockState());
					put(level, box, -3, f + 1, z, z == -11 ? AIR : Blocks.GLASS_PANE.defaultBlockState());
				}
				for (int x = -5; x <= -1; x++) {
					for (int z = -12; z <= -6; z++) {
						if (noise(x, z, 131) < 0.35) {
							put(level, box, x, f - 1, z, Blocks.BLACK_CONCRETE.defaultBlockState());
						}
					}
				}
				put(level, box, -5, f + 3, -11, Blocks.COBWEB.defaultBlockState());
				put(level, box, -1, f + 3, -7, Blocks.COBWEB.defaultBlockState());
				put(level, box, -5, f, -7, Blocks.SCULK.defaultBlockState());
			}
			case ICE -> { // cold store: blue ice pillars, racks, a powder-snow cauldron
				for (int y = f; y <= f + 3; y++) {
					put(level, box, -5, y, -12, Blocks.BLUE_ICE.defaultBlockState());
					put(level, box, -5, y, -7, Blocks.BLUE_ICE.defaultBlockState());
				}
				put(level, box, -3, f, -12, Blocks.SPRUCE_LOG.defaultBlockState());
				put(level, box, -2, f, -12, Blocks.SPRUCE_LOG.defaultBlockState());
				put(level, box, -3, f + 1, -12, Blocks.SKELETON_SKULL.defaultBlockState());
				put(level, box, -1, f, -8, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState());
			}
		}
		// the stash: a Tier 1 barrel in the corner
		BlockPos stash = at(-1, f, -12);
		if (inBox(box, stash.getX(), stash.getZ())) {
			level.setBlock(stash, up, 2);
			if (level.getBlockEntity(stash) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity c) {
				c.setLootTable(com.projecthero.mod.hideout.worldgen.HideoutCanvas.table("chests/vault_tier1"), seed ^ stash.asLong());
			}
		}
		if (p.hangingLight) {
			put(level, box, -3, f + 3, -11, p.light.setValue(LanternBlock.HANGING, true));
			put(level, box, 3, f + 3, -7, p.light.setValue(LanternBlock.HANGING, true));
		} else {
			put(level, box, -3, f + 4, -11, p.light);
			put(level, box, 3, f + 4, -7, p.light);
		}
	}

	private BlockState wallBlock(Palette p, int x, int y, int z) {
		if (p.wallAlt != null && noise(x, z, y) < 0.22) {
			return p.wallAlt;
		}
		return p.wall;
	}

	/** A reinforced 4x4 room in the back-right corner behind an iron door, a button either side of it. */
	private void vaultRoom(WorldGenLevel level, BoundingBox box, Palette p, int f) {
		for (int y = f - 1; y <= f + 3; y++) {
			for (int z = -HZ; z <= 0; z++) {
				put(level, box, 1, y, z, p.secure);
			}
			for (int x = 1; x <= HX - 1; x++) {
				put(level, box, x, y, 0, p.secure);
			}
		}
		for (int x = 2; x <= HX - 1; x++) {
			for (int z = -HZ + 1; z <= -1; z++) {
				put(level, box, x, f - 1, z, p.secure);
			}
		}
		BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
				.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
		put(level, box, 3, f, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		put(level, box, 3, f + 1, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		BlockState button = Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL);
		put(level, box, 2, f + 1, 1, button.setValue(ButtonBlock.FACING, Direction.SOUTH));
		put(level, box, 4, f + 1, -1, button.setValue(ButtonBlock.FACING, Direction.NORTH));
		vault(level, box, 4, f, -4, Direction.SOUTH);
		put(level, box, 5, f, -4, Blocks.LANTERN.defaultBlockState());
		put(level, box, 2, f, -4, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
	}

	/** A hidden 4x4 basement under the back-right corner: carpet over a trapdoor, a ladder down, the vault below. */
	private void hatchBasement(WorldGenLevel level, BoundingBox box, Palette p, int f) {
		int b = f - 4; // basement floor level (first air)
		for (int x = 1; x <= HX; x++) {
			for (int z = -HZ; z <= 0; z++) {
				boolean shell = x == 1 || x == HX || z == -HZ || z == 0;
				put(level, box, x, b - 1, z, p.secure);
				for (int y = b; y <= f - 2; y++) {
					put(level, box, x, y, z, shell ? p.secure : AIR);
				}
			}
		}
		for (int y = b; y <= f - 2; y++) {
			put(level, box, 5, y, -4, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		}
		put(level, box, 5, f - 1, -4, p.trapdoor.setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.OPEN, false)
				.setValue(TrapDoorBlock.FACING, Direction.WEST));
		// a rug over the hatch (break the carpet to find it)
		for (int x = 4; x <= 5; x++) {
			for (int z = -4; z <= -3; z++) {
				put(level, box, x, f, z, p.rug);
			}
		}
		vault(level, box, 2, b, -4, Direction.EAST);
		put(level, box, 2, b, -1, Blocks.LANTERN.defaultBlockState());
		put(level, box, 4, b, -1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		put(level, box, 3, b, -1, Blocks.COBWEB.defaultBlockState());
	}

	private void decorate(WorldGenLevel level, BoundingBox box, Palette p, int f) {
		// ceiling lights
		if (p.hangingLight) {
			put(level, box, -3, f + 3, 0, p.light.setValue(LanternBlock.HANGING, true));
			put(level, box, 2, f + 3, 3, p.light.setValue(LanternBlock.HANGING, true));
			if (theme.entry == Entry.IRON_DOOR) {
				put(level, box, 4, f + 3, -2, p.light.setValue(LanternBlock.HANGING, true));
			}
		} else {
			put(level, box, -3, f + 4, 0, p.light);
			put(level, box, 2, f + 4, 3, p.light);
			if (theme.entry == Entry.IRON_DOOR) {
				put(level, box, 4, f + 4, -2, p.light);
			}
		}
		switch (theme.style) {
			case LAB -> {
				put(level, box, -5, f, -4, Blocks.BREWING_STAND.defaultBlockState());
				put(level, box, -4, f, -4, Blocks.CAULDRON.defaultBlockState());
				table(level, box, -3, f, -4, Blocks.IRON_TRAPDOOR);
				table(level, box, -2, f, -4, Blocks.IRON_TRAPDOOR);
				put(level, box, -2, f + 1, -4, Blocks.BREWING_STAND.defaultBlockState());
				put(level, box, -5, f, 0, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
				put(level, box, -5, f, 3, Blocks.WHITE_SHULKER_BOX.defaultBlockState());
				put(level, box, 5, f, 3, Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST));
				put(level, box, 5, f, 2, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
				put(level, box, -5, f + 2, -4, Blocks.FLOWER_POT.defaultBlockState());
				rooftopMast(level, box, f);
			}
			case MILITARY -> {
				bunk(level, box, -5, f, -4);
				bunk(level, box, -5, f, -1);
				put(level, box, -5, f, 1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
				put(level, box, -5, f, 2, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
				put(level, box, -5, f + 1, 1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
				put(level, box, -2, f, -4, Blocks.SMITHING_TABLE.defaultBlockState());
				put(level, box, -1, f, -4, Blocks.CRAFTING_TABLE.defaultBlockState());
				put(level, box, 0, f, -4, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
				put(level, box, 5, f, 4, Blocks.TARGET.defaultBlockState());
				put(level, box, 5, f, 3, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
				put(level, box, -4, f, 4, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
				outsideCrates(level, box, f);
			}
			case BUNKER -> {
				for (int z = -4; z <= 3; z++) {
					put(level, box, -5, f + 3, z, Blocks.IRON_BARS.defaultBlockState());
				}
				put(level, box, -5, f, -4, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
				put(level, box, -5, f, -3, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
				put(level, box, -4, f, -4, Blocks.LIGHTNING_ROD.defaultBlockState());
				put(level, box, -5, f, 0, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
				put(level, box, -5, f, 1, Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
				put(level, box, -5, f, 3, Blocks.COPPER_BULB.defaultBlockState());
				put(level, box, -2, f, -4, Blocks.WAXED_OXIDIZED_COPPER_GRATE.defaultBlockState());
				put(level, box, -1, f, -4, Blocks.WAXED_OXIDIZED_COPPER_GRATE.defaultBlockState());
				put(level, box, 5, f, 3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
				put(level, box, 5, f, 4, Blocks.CHAIN.defaultBlockState());
				rooftopMast(level, box, f);
			}
			case CAMP -> {
				put(level, box, -5, f, -4, Blocks.RED_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
				put(level, box, -5, f, -3, Blocks.RED_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
				put(level, box, -2, f, -4, Blocks.CRAFTING_TABLE.defaultBlockState());
				put(level, box, -1, f, -4, Blocks.FLETCHING_TABLE.defaultBlockState());
				put(level, box, -5, f, 1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
				put(level, box, -5, f, 2, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
				put(level, box, -5, f + 1, 2, Blocks.SCAFFOLDING.defaultBlockState());
				put(level, box, -5, f, 4, Blocks.DECORATED_POT.defaultBlockState());
				put(level, box, 5, f, 4, Blocks.COMPOSTER.defaultBlockState());
				put(level, box, 5, f, 3, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
				put(level, box, -3, f, PORCH, Blocks.CAMPFIRE.defaultBlockState());
				outsideCrates(level, box, f);
			}
			case STATION -> {
				table(level, box, -5, f, -4, Blocks.IRON_TRAPDOOR);
				table(level, box, -4, f, -4, Blocks.IRON_TRAPDOOR);
				put(level, box, -5, f + 1, -4, Blocks.BREWING_STAND.defaultBlockState());
				put(level, box, -2, f, -4, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
				put(level, box, -5, f, 0, Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
				put(level, box, -5, f, 1, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
				put(level, box, -5, f, 3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
				put(level, box, 5, f, 3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
				put(level, box, 5, f, 4, Blocks.TINTED_GLASS.defaultBlockState());
				rooftopMast(level, box, f);
				put(level, box, 3, f, PORCH, Blocks.SCAFFOLDING.defaultBlockState());
			}
			case CRYPT -> {
				for (int z = -4; z <= 3; z += 2) {
					put(level, box, -5, f, z, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
					candles(level, box, -5, f + 1, z, 1 + (int) (noise(-5, z, 3) * 3));
				}
				put(level, box, -2, f, -4, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
				put(level, box, -2, f + 1, -4, Blocks.SKELETON_SKULL.defaultBlockState());
				put(level, box, -1, f, -4, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
				candles(level, box, -1, f + 1, -4, 3);
				put(level, box, -4, f + 3, -4, Blocks.COBWEB.defaultBlockState());
				put(level, box, 5, f + 3, 4, Blocks.COBWEB.defaultBlockState());
				put(level, box, 5, f, 4, Blocks.DECORATED_POT.defaultBlockState());
				put(level, box, -3, f + 2, 0, Blocks.CHAIN.defaultBlockState());
			}
			case TEMPLE -> {
				put(level, box, -5, f, -4, Blocks.CHISELED_SANDSTONE.defaultBlockState());
				put(level, box, -5, f + 1, -4, Blocks.DECORATED_POT.defaultBlockState());
				put(level, box, -2, f, -4, Blocks.CUT_SANDSTONE_SLAB.defaultBlockState());
				put(level, box, -1, f, -4, Blocks.CHISELED_SANDSTONE.defaultBlockState());
				candles(level, box, -1, f + 1, -4, 4);
				put(level, box, 0, f, -4, Blocks.CUT_SANDSTONE_SLAB.defaultBlockState());
				put(level, box, -5, f, 1, Blocks.DECORATED_POT.defaultBlockState());
				put(level, box, -5, f, 3, Blocks.DECORATED_POT.defaultBlockState());
				candles(level, box, 5, f, 4, 2);
				put(level, box, 5, f, 3, Blocks.DECORATED_POT.defaultBlockState());
				for (int z = -1; z <= 1; z++) {
					put(level, box, -2, f - 1, z, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
					put(level, box, -1, f - 1, z, Blocks.BLUE_TERRACOTTA.defaultBlockState());
				}
			}
			case INFESTED -> {
				table(level, box, -5, f, -4, Blocks.IRON_TRAPDOOR);
				put(level, box, -4, f, -4, Blocks.CAULDRON.defaultBlockState());
				put(level, box, -5, f, 0, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
				put(level, box, -2, f, -4, Blocks.BLACK_CONCRETE_POWDER.defaultBlockState());
				put(level, box, -5, f, 3, Blocks.BLACK_WOOL.defaultBlockState());
				for (int[] w : new int[][] { { -4, -3 }, { -1, 4 }, { 4, 4 }, { -5, 2 }, { 1, -3 } }) {
					put(level, box, w[0], f + 3, w[1], Blocks.COBWEB.defaultBlockState());
				}
				for (int x = -5; x <= 5; x++) {
					for (int z = -4; z <= 4; z++) {
						if (noise(x, z, 77) < 0.2 && !(x >= 1 && z <= 0)) {
							put(level, box, x, f - 1, z, Blocks.BLACK_CONCRETE.defaultBlockState());
						}
					}
				}
				put(level, box, 5, f, 3, Blocks.SCULK.defaultBlockState());
				put(level, box, 5, f + 1, 3, Blocks.SCULK_VEIN.defaultBlockState().setValue(BlockStateProperties.DOWN, true));
			}
			case ICE -> {
				put(level, box, -5, f, -4, Blocks.BLUE_ICE.defaultBlockState());
				put(level, box, -5, f, -3, Blocks.BLUE_ICE.defaultBlockState());
				put(level, box, -2, f, -4, Blocks.SPRUCE_LOG.defaultBlockState());
				put(level, box, -1, f, -4, Blocks.SPRUCE_LOG.defaultBlockState());
				put(level, box, -2, f + 1, -4, Blocks.SKELETON_SKULL.defaultBlockState());
				put(level, box, -5, f, 1, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState());
				put(level, box, -5, f, 3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
				put(level, box, 5, f, 4, Blocks.SPRUCE_LOG.defaultBlockState());
				put(level, box, 5, f + 1, 4, Blocks.SOUL_CAMPFIRE.defaultBlockState());
				for (int x = -HX; x <= HX; x++) {
					for (int z = -HZ; z <= HZ; z++) {
						if (noise(x, z, 91) < 0.6) {
							put(level, box, x, f + 5, z, Blocks.SNOW.defaultBlockState());
						}
					}
				}
			}
		}
	}

	private void table(WorldGenLevel level, BoundingBox box, int x, int y, int z, Block trapdoor) {
		put(level, box, x, y, z, trapdoor.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.OPEN, false));
	}

	private void bunk(WorldGenLevel level, BoundingBox box, int x, int y, int z) {
		BlockState bed = Blocks.GREEN_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		put(level, box, x, y, z, bed.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
		put(level, box, x, y, z + 1, bed.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
		BlockState top = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		put(level, box, x, y + 2, z, top);
		put(level, box, x, y + 2, z + 1, top);
	}

	private void candles(WorldGenLevel level, BoundingBox box, int x, int y, int z, int n) {
		put(level, box, x, y, z, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, n)))
				.setValue(CandleBlock.LIT, true));
	}

	/** A radio mast on the roof: a lightning rod on two iron bars. */
	private void rooftopMast(WorldGenLevel level, BoundingBox box, int f) {
		put(level, box, -5, f + 5, -4, Blocks.IRON_BARS.defaultBlockState());
		put(level, box, -5, f + 6, -4, Blocks.IRON_BARS.defaultBlockState());
		put(level, box, -5, f + 7, -4, Blocks.LIGHTNING_ROD.defaultBlockState());
	}

	/** Two crates stacked beside the porch. */
	private void outsideCrates(WorldGenLevel level, BoundingBox box, int f) {
		put(level, box, 3, f, 7, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		put(level, box, 3, f, 8, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
		put(level, box, 3, f + 1, 7, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
	}

	// ---------------------------------------------------------------- palette

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	/** The blocks of a {@link Style}. */
	record Palette(BlockState wall, BlockState wallAlt, BlockState pillar, BlockState floor, BlockState roof, BlockState parapet,
			BlockState foundation, BlockState porch, BlockState secure, BlockState light, boolean hangingLight, BlockState window,
			BlockState lintel, BlockState trapdoor, BlockState rug) {
		static Palette of(Style style) {
			return switch (style) {
				case LAB -> new Palette(s(Blocks.WHITE_CONCRETE), null, s(Blocks.POLISHED_ANDESITE), s(Blocks.SMOOTH_STONE),
						s(Blocks.LIGHT_GRAY_CONCRETE), null, s(Blocks.STONE_BRICKS), s(Blocks.SMOOTH_STONE), s(Blocks.POLISHED_DEEPSLATE),
						s(Blocks.SEA_LANTERN), false, s(Blocks.GLASS_PANE), s(Blocks.POLISHED_ANDESITE), s(Blocks.IRON_TRAPDOOR), s(Blocks.WHITE_CARPET));
				case MILITARY -> new Palette(s(Blocks.GREEN_TERRACOTTA), null, s(Blocks.POLISHED_ANDESITE), s(Blocks.POLISHED_ANDESITE),
						s(Blocks.GRAY_CONCRETE), s(Blocks.ANDESITE_WALL), s(Blocks.STONE_BRICKS), s(Blocks.GRAVEL), s(Blocks.DEEPSLATE_TILES),
						s(Blocks.LANTERN), true, s(Blocks.IRON_BARS), s(Blocks.POLISHED_ANDESITE), s(Blocks.IRON_TRAPDOOR), s(Blocks.GREEN_CARPET));
				case BUNKER -> new Palette(s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.CYAN_TERRACOTTA), s(Blocks.POLISHED_DEEPSLATE),
						s(Blocks.GRAY_CONCRETE), s(Blocks.DEEPSLATE_TILES), null, s(Blocks.STONE_BRICKS), s(Blocks.GRAY_CONCRETE),
						s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.GLOWSTONE), false, s(Blocks.GLASS_PANE), s(Blocks.POLISHED_DEEPSLATE),
						s(Blocks.IRON_TRAPDOOR), s(Blocks.GRAY_CARPET));
				case CAMP -> new Palette(s(Blocks.SPRUCE_PLANKS), s(Blocks.STRIPPED_SPRUCE_LOG), log(Blocks.SPRUCE_LOG), s(Blocks.OAK_PLANKS),
						s(Blocks.DARK_OAK_PLANKS), s(Blocks.DARK_OAK_FENCE), s(Blocks.COBBLESTONE), s(Blocks.COARSE_DIRT), s(Blocks.STONE_BRICKS),
						s(Blocks.LANTERN), true, s(Blocks.GLASS_PANE), log(Blocks.STRIPPED_SPRUCE_LOG), s(Blocks.SPRUCE_TRAPDOOR), s(Blocks.BROWN_CARPET));
				case STATION -> new Palette(s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.WHITE_CONCRETE), s(Blocks.WAXED_EXPOSED_CUT_COPPER),
						s(Blocks.GRAY_CONCRETE), s(Blocks.SMOOTH_STONE), null, s(Blocks.STONE_BRICKS), s(Blocks.GRAVEL), s(Blocks.DEEPSLATE_TILES),
						s(Blocks.SEA_LANTERN), false, s(Blocks.GLASS_PANE), s(Blocks.WAXED_EXPOSED_CUT_COPPER), s(Blocks.IRON_TRAPDOOR), s(Blocks.GRAY_CARPET));
				case CRYPT -> new Palette(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CHISELED_STONE_BRICKS),
						s(Blocks.COBBLED_DEEPSLATE), s(Blocks.STONE_BRICKS), s(Blocks.STONE_BRICK_WALL), s(Blocks.COBBLESTONE), s(Blocks.GRAVEL),
						s(Blocks.CRACKED_STONE_BRICKS), s(Blocks.SOUL_LANTERN), true, s(Blocks.IRON_BARS), s(Blocks.CHISELED_STONE_BRICKS),
						s(Blocks.SPRUCE_TRAPDOOR), s(Blocks.GRAY_CARPET));
				case TEMPLE -> new Palette(s(Blocks.CUT_SANDSTONE), s(Blocks.SANDSTONE), s(Blocks.CHISELED_SANDSTONE), s(Blocks.SMOOTH_SANDSTONE),
						s(Blocks.CUT_SANDSTONE), s(Blocks.SANDSTONE_WALL), s(Blocks.SANDSTONE), s(Blocks.SMOOTH_SANDSTONE), s(Blocks.SMOOTH_SANDSTONE),
						s(Blocks.LANTERN), true, null, s(Blocks.CHISELED_SANDSTONE), s(Blocks.BIRCH_TRAPDOOR), s(Blocks.ORANGE_CARPET));
				case INFESTED -> new Palette(s(Blocks.WHITE_CONCRETE), s(Blocks.BLACK_CONCRETE), s(Blocks.POLISHED_ANDESITE),
						s(Blocks.SMOOTH_STONE), s(Blocks.LIGHT_GRAY_CONCRETE), null, s(Blocks.STONE_BRICKS), s(Blocks.SMOOTH_STONE),
						s(Blocks.POLISHED_DEEPSLATE), s(Blocks.SEA_LANTERN), false, s(Blocks.GLASS_PANE), s(Blocks.POLISHED_ANDESITE),
						s(Blocks.IRON_TRAPDOOR), s(Blocks.BLACK_CARPET));
				case ICE -> new Palette(s(Blocks.PACKED_ICE), s(Blocks.BLUE_ICE), log(Blocks.STRIPPED_SPRUCE_LOG), s(Blocks.SNOW_BLOCK),
						s(Blocks.PACKED_ICE), null, s(Blocks.STONE), s(Blocks.SNOW_BLOCK), s(Blocks.PACKED_ICE), s(Blocks.SOUL_LANTERN), true,
						null, log(Blocks.STRIPPED_SPRUCE_LOG), s(Blocks.SPRUCE_TRAPDOOR), s(Blocks.LIGHT_BLUE_CARPET));
			};
		}

		private static BlockState s(Block b) {
			return b.defaultBlockState();
		}

		private static BlockState log(Block b) {
			return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		}
	}

	// ---------------------------------------------------------------- local -> world

	private static int rx(int rotation, int dx, int dz) {
		return switch (rotation) {
			case 1 -> -dz;
			case 2 -> -dx;
			case 3 -> dz;
			default -> dx;
		};
	}

	private static int rz(int rotation, int dx, int dz) {
		return switch (rotation) {
			case 1 -> dx;
			case 2 -> -dz;
			case 3 -> -dx;
			default -> dz;
		};
	}

	private int wx(int dx, int dz) {
		return cx + rx(rotation, dx, dz);
	}

	private int wz(int dx, int dz) {
		return cz + rz(rotation, dx, dz);
	}

	private BlockPos at(int dx, int y, int dz) {
		return new BlockPos(wx(dx, dz), y, wz(dx, dz));
	}

	private Rotation rotationEnum() {
		return switch (rotation) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	private Direction dir(Direction local) {
		return rotationEnum().rotate(local);
	}

	private static float yawOf(Direction d) {
		return d.toYRot();
	}

	private static boolean inBox(BoundingBox box, int x, int z) {
		return x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
	}

	private void put(WorldGenLevel level, BoundingBox box, int dx, int y, int dz, BlockState state) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return;
		}
		level.setBlock(new BlockPos(x, y, z), state.rotate(rotationEnum()), 2);
	}

	/** Fills air / fluid / plants under a local column from {@code fromY} down (at most {@code depth}) with {@code fill}. */
	private void footing(WorldGenLevel level, BoundingBox box, int dx, int dz, int fromY, BlockState fill, int depth) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return;
		}
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
		for (int y = fromY; y >= fromY - depth; y--) {
			c.set(x, y, z);
			BlockState s = level.getBlockState(c);
			if (!s.isAir() && level.getFluidState(c).isEmpty() && !s.canBeReplaced()) {
				break;
			}
			level.setBlock(c, fill, 2);
		}
	}

	private void vault(WorldGenLevel level, BoundingBox box, int dx, int y, int dz, Direction facing) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return;
		}
		HeroVault.place(level, new BlockPos(x, y, z), 2, dir(facing));
	}

	/** A stable 0..1 value per (x, z, salt). */
	private double noise(int x, int z, int salt) {
		long h = seed ^ (x * 73856093L) ^ (z * 19349663L) ^ (salt * 83492791L);
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return ((h ^ (h >>> 33)) >>> 11) / (double) (1L << 53);
	}
}
