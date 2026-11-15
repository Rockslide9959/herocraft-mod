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

	private final SiteTheme theme;
	private final int cx;
	private final int cz;
	private final int floorY;
	private final int rotation;
	private final long seed;

	public SiteAnnexPiece(SiteTheme theme, int centerX, int floorY, int centerZ, int rotation, long seed) {
		super(SiteUpgrade.ANNEX_PIECE, 0, box(centerX, floorY, centerZ, rotation));
		this.theme = theme;
		this.cx = centerX;
		this.cz = centerZ;
		this.floorY = floorY;
		this.rotation = Math.floorMod(rotation, 4);
		this.seed = seed;
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
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Theme", theme.name());
		tag.putInt("CX", cx);
		tag.putInt("CZ", cz);
		tag.putInt("Floor", floorY);
		tag.putInt("Rot", rotation);
		tag.putLong("Seed", seed);
	}

	private static BoundingBox box(int cx, int floorY, int cz, int rotation) {
		int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
		for (int[] c : new int[][] { { -HX, -HZ }, { HX, -HZ }, { -HX, PORCH }, { HX, PORCH } }) {
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
		return theme.entry == Entry.HATCH ? at(2, floorY - 4, -4) : at(4, floorY, -4);
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
