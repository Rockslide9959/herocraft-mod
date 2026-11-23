package com.projecthero.mod.loot.finds;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The Abandoned Hero Outpost: a one-room hideout a hero retired from, long since ruined -- broken walls of uneven
 * height, a half-collapsed roof, moss and vines and leaves taking it back. Inside: a cot, a workbench, a map table,
 * a lectern holding the retired hero's field notes (a written book hinting at the progression chain: Sites, villain
 * Hideouts, the Vault), the Tier 1 Hero Vault in the corner, and 2-4 zombies / skeletons lingering (persistent, in
 * helmets so the sun does not burn them out of the place).
 *
 * <p>Variants: 0 stone hut (cobble + spruce), 1 timber cabin (oak), 2 brick bunker (bricks + iron bars). The build is
 * also turned to one of four facings, and the ruin pattern comes from the piece's seed.
 */
public class HeroOutpostPiece extends FindPiece {
	static final int HALF = 6;
	/** The room: walls at x = +-4 and z = +-3, the door in the south wall. */
	private static final int RX = 4;
	private static final int RZ = 3;
	private static final int WALL = 3;
	public static final String[] AUTHORS = { "Night Owl", "Captain Ember", "The Iron Wasp" };

	public HeroOutpostPiece(int centerX, int surfaceY, int centerZ) {
		super(FindsMod.HERO_OUTPOST_PIECE, centerX, surfaceY, centerZ, HALF, 7, 10, 3, 3);
	}

	public HeroOutpostPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(FindsMod.HERO_OUTPOST_PIECE, tag);
	}

	@Override
	public BlockPos vaultPos() {
		return localToWorld(3, floorY() + 1, 1);
	}

	public BlockPos lecternPos() {
		return localToWorld(0, floorY() + 1, -2);
	}

	/** v0.17.7: the room's floor (layout 2, the dugout, is sunk two blocks into the ground). */
	public int floorY() {
		return layout == 2 ? groundY - 2 : groundY;
	}

	/** How many mobs this variant of the outpost spawns (2-4). */
	public int mobCount() {
		return 2 + (int) Math.floorMod(seed >>> 21, 3L);
	}

	private BlockState wallBlock(int x, int y, int z) {
		double n = noise(x, z, 30 + y);
		return switch (variant) {
			case 1 -> n < 0.2 ? Blocks.STRIPPED_OAK_LOG.defaultBlockState() : n < 0.3 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
					: Blocks.OAK_PLANKS.defaultBlockState();
			case 2 -> n < 0.3 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : n < 0.4 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
					: Blocks.BRICKS.defaultBlockState();
			default -> n < 0.35 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.5 ? Blocks.STONE_BRICKS.defaultBlockState()
					: Blocks.COBBLESTONE.defaultBlockState();
		};
	}

	private BlockState cornerBlock() {
		return switch (variant) {
			case 1 -> Blocks.OAK_LOG.defaultBlockState();
			case 2 -> Blocks.POLISHED_ANDESITE.defaultBlockState();
			default -> Blocks.SPRUCE_LOG.defaultBlockState();
		};
	}

	private BlockState floorBlock(int x, int z) {
		double n = noise(x, z, 40);
		if (n < 0.12) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (n < 0.22) {
			return Blocks.MOSS_BLOCK.defaultBlockState();
		}
		return switch (variant) {
			case 1 -> Blocks.OAK_PLANKS.defaultBlockState();
			case 2 -> Blocks.STONE_BRICKS.defaultBlockState();
			default -> Blocks.SPRUCE_PLANKS.defaultBlockState();
		};
	}

	private BlockState roofSlab() {
		Block b = switch (variant) {
			case 1 -> Blocks.DARK_OAK_SLAB;
			case 2 -> Blocks.SMOOTH_STONE_SLAB;
			default -> Blocks.SPRUCE_SLAB;
		};
		return b.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
	}

	private BlockState window() {
		return variant == 2 ? Blocks.IRON_BARS.defaultBlockState() : Blocks.GLASS_PANE.defaultBlockState();
	}

	@Override
	public void build(WorldGenLevel level, BoundingBox box) {
		int g = floorY();
		int ground = groundY;
		boolean dugout = layout == 2;
		// ---- clear the site (trees, tall grass) and lay a footing + floor
		for (int dx = -RX - 1; dx <= RX + 1; dx++) {
			for (int dz = -RZ - 1; dz <= RZ + 2; dz++) {
				boolean inside = Math.abs(dx) <= RX && Math.abs(dz) <= RZ;
				if (dugout && !inside) {
					// the dugout's surroundings stay at ground level, banked up against its walls
					clear(level, box, dx, dz, ground + 1, ground + 10);
					footing(level, box, dx, dz, ground - 1, Blocks.DIRT.defaultBlockState());
					put(level, box, dx, ground, dz, Blocks.DIRT.defaultBlockState());
					put(level, box, dx, ground - 1, dz, Blocks.DIRT.defaultBlockState());
					boolean ring = Math.abs(dx) == RX + 1 || Math.abs(dz) == RZ + 1;
					boolean trench = dx == 0 && dz > RZ;
					if (ring && !trench && lnoise(dx, dz, 42) < 0.8) {
						put(level, box, dx, ground + 1, dz, Blocks.GRASS_BLOCK.defaultBlockState());
					} else {
						put(level, box, dx, ground, dz, Blocks.GRASS_BLOCK.defaultBlockState());
					}
					continue;
				}
				clear(level, box, dx, dz, g + 1, ground + 10);
				footing(level, box, dx, dz, g - 1, Blocks.DIRT.defaultBlockState());
				int x = wx(dx, dz), z = wz(dx, dz);
				put(level, box, dx, g, dz, inside ? floorBlock(x, z)
						: noise(x, z, 41) < 0.3 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
			}
		}

		// ---- walls: full corners, broken stretches between, a doorway south, windows
		for (int dx = -RX; dx <= RX; dx++) {
			for (int dz = -RZ; dz <= RZ; dz++) {
				boolean wall = Math.abs(dx) == RX || Math.abs(dz) == RZ;
				if (!wall) {
					continue;
				}
				int x = wx(dx, dz), z = wz(dx, dz);
				boolean corner = Math.abs(dx) == RX && Math.abs(dz) == RZ;
				boolean door = dz == RZ && dx == 0;
				int h = corner ? WALL + 1 : noise(x, z, 31) < 0.4 ? (int) (noise(x, z, 32) * WALL) : WALL;
				for (int y = 1; y <= h; y++) {
					if (door && y <= 2) {
						continue;
					}
					boolean windowSpot = !dugout && y == 2 && !corner && ((Math.abs(dx) == RX && dz == 0) || (dz == -RZ && Math.abs(dx) == 2));
					BlockState s = corner ? cornerBlock() : windowSpot ? (noise(x, z, 33) < 0.5 ? window() : Blocks.AIR.defaultBlockState())
							: wallBlock(x, y, z);
					put(level, box, dx, g + y, dz, s);
				}
				// overgrowth: leaves on top of some walls, vines down the outside
				if (h >= 2 && noise(x, z, 34) < 0.3) {
					put(level, box, dx, g + h + 1, dz, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
				}
				if (!dugout && !corner && h >= 2 && noise(x, z, 35) < 0.35) {
					Direction out = Math.abs(dz) == RZ ? (dz > 0 ? Direction.SOUTH : Direction.NORTH) : (dx > 0 ? Direction.EAST : Direction.WEST);
					int ox = dx + out.getStepX(), oz = dz + out.getStepZ();
					BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(out.getOpposite()), true);
					for (int y = h; y >= Math.max(1, h - 2); y--) {
						put(level, box, ox, g + y, oz, vine);
					}
				}
			}
		}

		// ---- the half-collapsed roof, rubble where it fell in
		BlockState slab = roofSlab();
		for (int dx = -RX; dx <= RX; dx++) {
			for (int dz = -RZ; dz <= RZ; dz++) {
				int x = wx(dx, dz), z = wz(dx, dz);
				boolean corner = Math.abs(dx) == RX && Math.abs(dz) == RZ;
				// the roof survives over the back (north) half more often than over the door
				double keep = 0.75 - (dz + RZ) * 0.09;
				if (corner || noise(x, z, 36) < keep) {
					if (!corner) {
						put(level, box, dx, g + WALL + 1, dz, slab);
					}
				} else if (Math.abs(dx) < RX && Math.abs(dz) < RZ && noise(x, z, 37) < 0.18 && dz > -2) {
					put(level, box, dx, g + 1, dz, noise(x, z, 38) < 0.5 ? slab : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
				}
			}
		}

		// ---- inside: moss carpet, cobwebs
		for (int dx = -RX + 1; dx <= RX - 1; dx++) {
			for (int dz = -RZ + 1; dz <= RZ - 1; dz++) {
				int x = wx(dx, dz), z = wz(dx, dz);
				if (noise(x, z, 39) < 0.14 && get(level, dx, g + 1, dz).isAir()) {
					put(level, box, dx, g + 1, dz, Blocks.MOSS_CARPET.defaultBlockState());
				}
			}
		}
		put(level, box, -3, g + 3, 2, Blocks.COBWEB.defaultBlockState());
		put(level, box, 3, g + 3, -2, Blocks.COBWEB.defaultBlockState());

		// ---- furniture: the cot, the lectern, the map table, the workbench, the vault
		Block bed = variant == 1 ? Blocks.BROWN_BED : variant == 2 ? Blocks.GRAY_BED : Blocks.WHITE_BED;
		put(level, box, -3, g + 1, -2, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		put(level, box, -3, g + 1, -1, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		put(level, box, -3, g + 1, 0, Blocks.CANDLE.defaultBlockState());
		put(level, box, 2, g + 1, -2, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		put(level, box, 3, g + 1, -2, Blocks.CRAFTING_TABLE.defaultBlockState());
		put(level, box, 3, g + 2, -2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		put(level, box, 3, g + 1, 0, Blocks.BARREL.defaultBlockState());
		vault(level, box, 3, g + 1, 1, Direction.WEST);
		clear(level, box, 2, 1, g + 1, g + 2);
		lectern(level, box);

		// ---- v0.17.7 layouts and decoration
		if (dugout) {
			dugoutExtras(level, box, g, ground);
		} else if (layout == 1) {
			tower(level, box, g);
		}
		firepit(level, box, ground);

		// ---- the lingering mobs
		int[][] spots = { { -1, 1 }, { 1, 0 }, { -2, 2 }, { 1, 2 } };
		for (int i = 0; i < mobCount(); i++) {
			int x = wx(spots[i][0], spots[i][1]), z = wz(spots[i][0], spots[i][1]);
			if (!inBox(box, x, z)) {
				continue;
			}
			put(level, box, spots[i][0], g + 1, spots[i][1], Blocks.AIR.defaultBlockState());
			put(level, box, spots[i][0], g + 2, spots[i][1], Blocks.AIR.defaultBlockState());
			spawnMob(level, x, g + 1, z, noise(x, z, 50 + i) < 0.55);
		}
	}

	private void lectern(WorldGenLevel level, BoundingBox box) {
		BlockPos at = lecternPos();
		if (!inBox(box, at.getX(), at.getZ())) {
			return;
		}
		put(level, box, 0, at.getY(), -2, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH)
				.setValue(LecternBlock.HAS_BOOK, true));
		if (level.getBlockEntity(at) instanceof LecternBlockEntity lectern) {
			lectern.setBook(journal(variant));
		}
	}

	/** The retired hero's field notes: four pages, the first different per variant, all translatable. */
	public static ItemStack journal(int variant) {
		List<Filterable<Component>> pages = new ArrayList<>();
		pages.add(Filterable.passThrough(Component.translatable("projecthero.find.hero_outpost.journal.intro" + Math.floorMod(variant, 3))));
		for (int p = 2; p <= 5; p++) {
			pages.add(Filterable.passThrough(Component.translatable("projecthero.find.hero_outpost.journal.p" + p)));
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Field Notes"),
				AUTHORS[Math.floorMod(variant, AUTHORS.length)], 0, pages, true));
		return book;
	}

	private static void spawnMob(WorldGenLevel level, int x, int y, int z, boolean zombie) {
		Mob mob = (zombie ? EntityType.ZOMBIE : EntityType.SKELETON).create(level.getLevel());
		if (mob == null) {
			return;
		}
		mob.moveTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(x, y, z)), MobSpawnType.STRUCTURE, null);
		mob.setPersistenceRequired();
		// a helmet so daylight through the broken roof doesn't burn them away before anyone arrives
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(zombie ? Items.LEATHER_HELMET : Items.CHAINMAIL_HELMET));
		mob.setDropChance(EquipmentSlot.HEAD, 0f);
		level.addFreshEntityWithPassengers(mob);
	}

	/** v0.17.7 layout 2: steps up out of the door, a sod-and-moss roof, a radio mast on the corner. */
	private void dugoutExtras(WorldGenLevel level, BoundingBox box, int f, int ground) {
		put(level, box, 0, f + 1, RZ + 1, stair(Blocks.COBBLESTONE_STAIRS, Direction.SOUTH, false));
		clear(level, box, 0, RZ + 1, f + 2, ground + 2);
		clear(level, box, 0, RZ + 2, ground + 1, ground + 2);
		for (int dx = -RX + 1; dx <= RX - 1; dx++) {
			for (int dz = -RZ + 1; dz <= RZ - 1; dz++) {
				if (get(level, dx, f + WALL + 1, dz).getBlock() instanceof SlabBlock && lnoise(dx, dz, 43) < 0.35) {
					put(level, box, dx, f + WALL + 2, dz, Blocks.MOSS_CARPET.defaultBlockState());
				}
			}
		}
		for (int y = f + WALL + 2; y <= f + WALL + 4; y++) {
			put(level, box, RX, y, -RZ, Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		put(level, box, RX, f + WALL + 5, -RZ, Blocks.LIGHTNING_ROD.defaultBlockState());
		put(level, box, RX - 1, f + WALL + 4, -RZ, Blocks.TRIPWIRE_HOOK.defaultBlockState()
				.setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, Direction.WEST));
	}

	/** v0.17.7 layout 1: a lookout tower off the back corner, its top half fallen in. */
	private void tower(WorldGenLevel level, BoundingBox box, int f) {
		for (int dx = 4; dx <= 6; dx++) {
			for (int dz = -6; dz <= -4; dz++) {
				clear(level, box, dx, dz, f + 1, f + 9);
				footing(level, box, dx, dz, f, Blocks.COBBLESTONE.defaultBlockState());
			}
		}
		BlockState post = cornerBlock();
		int[][] posts = { { 4, -6 }, { 6, -6 }, { 4, -4 }, { 6, -4 } };
		for (int i = 0; i < posts.length; i++) {
			int top = i == 3 ? f + 4 : f + 7;
			for (int y = f + 1; y <= top; y++) {
				put(level, box, posts[i][0], y, posts[i][1], post);
			}
		}
		for (int y = f + 1; y <= f + 5; y++) {
			put(level, box, 5, y, -6, wallBlock(wx(5, -6), y, wz(5, -6)));
			put(level, box, 5, y, -5, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
		}
		for (int dx = 4; dx <= 6; dx++) {
			for (int dz = -6; dz <= -4; dz++) {
				if ((dx == 5 && dz == -5) || (dx == 6 && dz == -4)) {
					continue; // the ladder hole, and the corner that fell
				}
				if ((dx == 4 || dx == 6) && (dz == -6 || dz == -4)) {
					continue; // the posts
				}
				put(level, box, dx, f + 6, dz, roofSlab().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
			}
		}
		put(level, box, 4, f + 8, -6, Blocks.LANTERN.defaultBlockState());
		put(level, box, 5, f + 7, -6, Blocks.SPRUCE_FENCE.defaultBlockState());
		put(level, box, 4, f + 7, -5, Blocks.SPRUCE_FENCE.defaultBlockState());
		// the fallen corner, lying by the tower
		putSoft(level, box, 6, f + 1, -2, post.getBlock() instanceof net.minecraft.world.level.block.RotatedPillarBlock
				? post.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z) : post);
		putSoft(level, box, 6, f + 1, -1, roofSlab());
		putSoft(level, box, 5, f + 1, -2, Blocks.SPRUCE_FENCE.defaultBlockState());
	}

	/** v0.17.7 decoration: a cold firepit with log seats in front of the outpost. */
	private void firepit(WorldGenLevel level, BoundingBox box, int ground) {
		put(level, box, -3, ground, 5, Blocks.COARSE_DIRT.defaultBlockState());
		putSoft(level, box, -3, ground + 1, 5, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
		putSoft(level, box, -5, ground + 1, 5, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
				.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
		putSoft(level, box, -3, ground + 1, 6, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
				.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
		putSoft(level, box, -4, ground + 1, 4, Blocks.COBBLESTONE_SLAB.defaultBlockState());
	}
}
