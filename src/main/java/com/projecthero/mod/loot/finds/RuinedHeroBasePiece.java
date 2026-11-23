package com.projecthero.mod.loot.finds;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * v0.17.7 Ruined Hero Base: a hero's old headquarters, wrecked in some last fight and left to the weather. Bigger than
 * the Abandoned Hero Outpost and more personal: the hero's suit still stands in a glass costume case (one pane smashed),
 * flanked by their banners; a lectern holds their case notes; a desk with dead monitors, a bunk, a training target, a
 * locker (themed chest, {@code chests/finds/ruined_hero_base}) and a Tier 1 Hero Vault. Rubble lies where the roof came in.
 *
 * <p>Layouts: 0 concrete hideout, 1 brick warehouse lair (with a mezzanine), 2 toppled watchtower (a round stump, the
 * fallen top lying in pieces beside it). Variants: the hero's colour (0 blue, 1 red, 2 green) for the suit, banners and trim.
 */
public class RuinedHeroBasePiece extends FindPiece {
	static final int HALF = 8;
	public static final ResourceKey<LootTable> LOCKER = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/finds/ruined_hero_base"));
	public static final String[] HEROES = { "Blue Comet", "Red Sentinel", "Green Warden" };
	private static final int[] DYE = { 0x2C4FB4, 0xB02A2A, 0x2F8F3A };

	public RuinedHeroBasePiece(int centerX, int surfaceY, int centerZ) {
		super(FindsMod.RUINED_HERO_BASE_PIECE, centerX, surfaceY, centerZ, HALF, 8, 12, 3, 3);
	}

	public RuinedHeroBasePiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(FindsMod.RUINED_HERO_BASE_PIECE, tag);
	}

	// ---- the fixed furniture spots (all layouts)
	private int caseZ() {
		return layout == 2 ? -2 : -3;
	}

	@Override
	public BlockPos vaultPos() {
		return layout == 2 ? localToWorld(2, groundY + 1, 0) : localToWorld(3, groundY + 1, -3);
	}

	public BlockPos lecternPos() {
		return layout == 2 ? localToWorld(-2, groundY + 1, 0) : localToWorld(-3, groundY + 1, -3);
	}

	@Override
	public BlockPos stashPos() {
		return layout == 2 ? localToWorld(-2, groundY + 1, 2) : localToWorld(4, groundY + 1, 0);
	}

	/** Where the suit's armour stand stands. */
	public BlockPos costumePos() {
		return localToWorld(0, groundY + 1, caseZ());
	}

	// ---- palette
	private Block banner() {
		return switch (variant) {
			case 1 -> Blocks.RED_WALL_BANNER;
			case 2 -> Blocks.GREEN_WALL_BANNER;
			default -> Blocks.BLUE_WALL_BANNER;
		};
	}

	private BlockState accent() {
		return (switch (variant) {
			case 1 -> Blocks.RED_CONCRETE;
			case 2 -> Blocks.GREEN_CONCRETE;
			default -> Blocks.BLUE_CONCRETE;
		}).defaultBlockState();
	}

	private BlockState carpet() {
		return (switch (variant) {
			case 1 -> Blocks.RED_CARPET;
			case 2 -> Blocks.GREEN_CARPET;
			default -> Blocks.BLUE_CARPET;
		}).defaultBlockState();
	}

	private BlockState wall(int dx, int y, int dz) {
		double n = lnoise(dx, dz, 60 + y);
		return switch (layout) {
			case 1 -> n < 0.18 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : n < 0.3 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
					: Blocks.BRICKS.defaultBlockState();
			case 2 -> n < 0.25 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : n < 0.42 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
					: Blocks.STONE_BRICKS.defaultBlockState();
			default -> n < 0.15 ? Blocks.GRAY_CONCRETE.defaultBlockState() : n < 0.25 ? Blocks.ANDESITE.defaultBlockState()
					: n < 0.32 ? Blocks.CYAN_TERRACOTTA.defaultBlockState() : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
		};
	}

	private BlockState floor(int dx, int dz) {
		double n = lnoise(dx, dz, 70);
		if (n < 0.1) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (n < 0.18) {
			return Blocks.MOSS_BLOCK.defaultBlockState();
		}
		return switch (layout) {
			case 1 -> n < 0.5 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState();
			case 2 -> Blocks.POLISHED_ANDESITE.defaultBlockState();
			default -> n < 0.4 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
		};
	}

	private BlockState rubble(int dx, int dz) {
		double n = lnoise(dx, dz, 71);
		BlockState slab = (layout == 1 ? Blocks.BRICK_SLAB : layout == 2 ? Blocks.STONE_BRICK_SLAB : Blocks.ANDESITE_SLAB).defaultBlockState();
		return n < 0.35 ? slab : n < 0.55 ? Blocks.GRAVEL.defaultBlockState() : n < 0.75 ? Blocks.COBBLESTONE.defaultBlockState()
				: wall(dx, 1, dz);
	}

	@Override
	public void build(WorldGenLevel level, BoundingBox box) {
		int g = groundY;
		// ---- site: clear plants / trees, a footing, a scuffed yard round it
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				if (dx * dx + dz * dz > (HALF + 0.5) * (HALF + 0.5)) {
					continue;
				}
				clear(level, box, dx, dz, g + 1, g + 11);
				footing(level, box, dx, dz, g - 1, Blocks.DIRT.defaultBlockState());
				double n = lnoise(dx, dz, 72);
				put(level, box, dx, g, dz, n < 0.25 ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.35 ? Blocks.GRAVEL.defaultBlockState()
						: Blocks.GRASS_BLOCK.defaultBlockState());
			}
		}
		switch (layout) {
			case 1 -> warehouse(level, box);
			case 2 -> tower(level, box);
			default -> hideout(level, box);
		}
		costumeCase(level, box, 0, caseZ());
		lectern(level, box, layout == 2 ? -2 : -3, g + 1, layout == 2 ? 0 : -3, layout == 2 ? Direction.EAST : Direction.SOUTH, notes(variant));
		vault(level, box, layout == 2 ? 2 : 3, g + 1, layout == 2 ? 0 : -3, layout == 2 ? Direction.WEST : Direction.SOUTH);
		// the vault's door always opens onto air
		if (layout == 2) {
			clear(level, box, 1, 0, g + 1, g + 2);
		} else {
			clear(level, box, 3, -2, g + 1, g + 2);
		}
		lootContainer(level, box, layout == 2 ? -2 : 4, g + 1, layout == 2 ? 2 : 0,
				facing(layout == 2 ? Blocks.CHEST : Blocks.BARREL, layout == 2 ? Direction.EAST : Direction.WEST), LOCKER);
	}

	/** The glass costume case: the suit on a stand, panes either side, the front pane smashed, a lamp above, banners by it. */
	private void costumeCase(WorldGenLevel level, BoundingBox box, int x, int z) {
		int g = groundY;
		put(level, box, x, g, z, Blocks.SMOOTH_QUARTZ.defaultBlockState());
		clear(level, box, x, z, g + 1, g + 2);
		BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
		for (int y = g + 1; y <= g + 2; y++) {
			put(level, box, x - 1, y, z, pane.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.NORTH, true));
			put(level, box, x + 1, y, z, pane.setValue(IronBarsBlock.WEST, true).setValue(IronBarsBlock.NORTH, true));
		}
		put(level, box, x - 1, g, z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		put(level, box, x + 1, g, z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		put(level, box, x, g + 3, z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		put(level, box, x - 1, g + 3, z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
		put(level, box, x + 1, g + 3, z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
		// the smashed front: glass on the floor
		putSoft(level, box, x, g + 1, z + 2, Blocks.GLASS_PANE.defaultBlockState());
		putSoft(level, box, x + 1, g + 1, z + 1, Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState());
		// banners
		for (int s : new int[] { -2, 2 }) {
			if (get(level, x + s, g + 3, z - 1).isSolid()) {
				put(level, box, x + s, g + 3, z, banner().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
			}
		}
		// the suit
		int c = DYE[Math.floorMod(variant, DYE.length)];
		armourStand(level, box, x, g + 1, z, Direction.SOUTH, dyed(Items.LEATHER_HELMET, c), dyed(Items.LEATHER_CHESTPLATE, c),
				dyed(Items.LEATHER_LEGGINGS, 0x262626), dyed(Items.LEATHER_BOOTS, c));
	}

	private static ItemStack dyed(Item item, int rgb) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, false));
		return s;
	}

	/** The hero's case notes: an intro per hero, then three shared pages. */
	public static ItemStack notes(int variant) {
		int v = Math.floorMod(variant, HEROES.length);
		ItemStack book = FindPiece.notes("Case Notes", HEROES[v], "projecthero.find.ruined_hero_base.notes.p", 4);
		// page 1 is the hero's own
		var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content != null) {
			var pages = new java.util.ArrayList<>(content.pages());
			pages.set(0, net.minecraft.server.network.Filterable.passThrough(
					net.minecraft.network.chat.Component.translatable("projecthero.find.ruined_hero_base.notes.intro" + v)));
			book.set(DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(content.title(),
					content.author(), 0, pages, true));
		}
		return book;
	}

	// ---------------------------------------------------------------- layout 0: concrete hideout

	private void hideout(WorldGenLevel level, BoundingBox box) {
		int g = groundY;
		int rx = 5, rz = 4, top = g + 5;
		for (int dx = -rx; dx <= rx; dx++) {
			for (int dz = -rz; dz <= rz; dz++) {
				boolean edge = Math.abs(dx) == rx || Math.abs(dz) == rz;
				boolean corner = Math.abs(dx) == rx && Math.abs(dz) == rz;
				put(level, box, dx, g, dz, edge ? Blocks.POLISHED_ANDESITE.defaultBlockState() : floor(dx, dz));
				if (!edge) {
					continue;
				}
				// a wall stretch is broken down to a random height on the east side (the fight came through there)
				int h = corner ? 5 : (dx >= 3 && lnoise(dx, dz, 61) < 0.55) ? 1 + (int) (lnoise(dx, dz, 62) * 3) : 4;
				for (int y = 1; y <= h; y++) {
					boolean door = dz == rz && dx == 0 && y <= 2;
					boolean window = y == 2 && !corner && ((Math.abs(dx) == rx && Math.abs(dz) == 2) || (dz == -rz && Math.abs(dx) == 3)
							|| (dz == rz && Math.abs(dx) == 3));
					BlockState s = door ? Blocks.AIR.defaultBlockState()
							: window ? Blocks.GRAY_STAINED_GLASS_PANE.defaultBlockState()
							: y == 4 && !corner ? accent()
							: corner ? Blocks.POLISHED_ANDESITE.defaultBlockState() : wall(dx, y, dz);
					if (window && lnoise(dx, dz, 63) < 0.4) {
						s = Blocks.AIR.defaultBlockState(); // shot out
					}
					put(level, box, dx, g + y, dz, s);
				}
				if (h >= 3 && lnoise(dx, dz, 64) < 0.25) {
					vines(level, box, dx, dz, h);
				}
			}
		}
		// the roof: smooth stone slabs, a big hole over the east half with the rubble under it
		for (int dx = -rx; dx <= rx; dx++) {
			for (int dz = -rz; dz <= rz; dz++) {
				double hole = Math.hypot(dx - 2.5, dz + 0.5);
				boolean gone = hole < 2.8 + lnoise(dx, dz, 65) * 1.5;
				if (!gone) {
					put(level, box, dx, top, dz, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
				} else if (Math.abs(dx) < rx && Math.abs(dz) < rz && lnoise(dx, dz, 66) < 0.45) {
					putSoft(level, box, dx, g + 1, dz, rubble(dx, dz));
					if (lnoise(dx, dz, 67) < 0.3) {
						putSoft(level, box, dx, g + 2, dz, (layout == 1 ? Blocks.BRICK_SLAB : Blocks.ANDESITE_SLAB).defaultBlockState());
					}
				}
			}
		}
		// a rebar stub and a hanging cable where the roof broke, a dish fallen on the roof
		put(level, box, 1, top, 1, Blocks.IRON_BARS.defaultBlockState());
		put(level, box, 4, top - 1, -1, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
		put(level, box, -3, top + 1, -2, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
		put(level, box, -3, top + 1, -1, Blocks.LIGHTNING_ROD.defaultBlockState());
		interior(level, box, rx, rz);
		// outside: a cracked path to the door, a dead lamppost
		for (int dz = rz + 1; dz <= HALF - 1; dz++) {
			put(level, box, 0, g, dz, lnoise(0, dz, 68) < 0.35 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
		}
		put(level, box, 2, g + 1, rz + 2, Blocks.ANDESITE_WALL.defaultBlockState());
		put(level, box, 2, g + 2, rz + 2, Blocks.IRON_BARS.defaultBlockState());
		put(level, box, 2, g + 3, rz + 2, Blocks.REDSTONE_LAMP.defaultBlockState());
	}

	// ---------------------------------------------------------------- layout 1: brick warehouse lair

	private void warehouse(WorldGenLevel level, BoundingBox box) {
		int g = groundY;
		int rx = 6, rz = 4, h = 5;
		for (int dx = -rx; dx <= rx; dx++) {
			for (int dz = -rz; dz <= rz; dz++) {
				boolean edge = Math.abs(dx) == rx || Math.abs(dz) == rz;
				boolean post = (Math.abs(dx) == rx || dx == 0 || Math.abs(dx) == 3) && Math.abs(dz) == rz;
				put(level, box, dx, g, dz, edge ? Blocks.STONE_BRICKS.defaultBlockState() : floor(dx, dz));
				if (!edge) {
					continue;
				}
				int wh = post ? h : (dx <= -3 && dz == rz && lnoise(dx, dz, 61) < 0.6) ? 2 : h - (lnoise(dx, dz, 62) < 0.2 ? 1 : 0);
				for (int y = 1; y <= wh; y++) {
					boolean door = dz == rz && Math.abs(dx) <= 1 && dx != 0 && y <= 3 || dz == rz && dx == 0 && y <= 3;
					boolean window = y == 3 && !post && (Math.abs(dx) == rx || dz == -rz) && Math.floorMod(dx + dz, 2) == 0;
					BlockState s = door ? Blocks.AIR.defaultBlockState()
							: window ? Blocks.IRON_BARS.defaultBlockState()
							: post ? Blocks.SPRUCE_LOG.defaultBlockState() : wall(dx, y, dz);
					put(level, box, dx, g + y, dz, s);
				}
				if (wh >= 3 && lnoise(dx, dz, 64) < 0.3) {
					vines(level, box, dx, dz, wh);
				}
			}
		}
		// roof: dark-oak beams across, what's left of the slab roof over the back half
		for (int dx = -rx; dx <= rx; dx++) {
			for (int dz = -rz; dz <= rz; dz++) {
				boolean beam = dx == 0 || Math.abs(dx) == 3;
				if (beam && Math.abs(dz) <= rz && !(dx == 3 && dz > 1)) {
					put(level, box, dx, g + h + 1, dz, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
				} else if (dz < 1 - lnoise(dx, dz, 65) * 3) {
					put(level, box, dx, g + h + 1, dz, Blocks.DARK_OAK_SLAB.defaultBlockState());
				} else if (Math.abs(dx) < rx && Math.abs(dz) < rz && lnoise(dx, dz, 66) < 0.3 && dz >= 1) {
					putSoft(level, box, dx, g + 1, dz, rubble(dx, dz));
				}
			}
		}
		// the fallen beam, slanting down into the room
		put(level, box, 3, g + 3, 2, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
		put(level, box, 3, g + 2, 3, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
		put(level, box, 3, g + 1, 3, Blocks.COBBLESTONE.defaultBlockState());
		// the mezzanine over the back-east corner (over the vault): posts, a slab floor, a ladder, a bunk up there
		for (int dx = 2; dx <= 5; dx++) {
			for (int dz = -3; dz <= -2; dz++) {
				put(level, box, dx, g + 3, dz, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
			}
		}
		for (int y = g + 1; y <= g + 3; y++) {
			put(level, box, 2, y, -1, Blocks.SPRUCE_FENCE.defaultBlockState());
			put(level, box, 5, y, -1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		}
		put(level, box, 5, g + 4, -2, Blocks.GRAY_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.HEAD));
		put(level, box, 4, g + 4, -2, Blocks.GRAY_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, BedPart.FOOT));
		put(level, box, 2, g + 4, -3, Blocks.LANTERN.defaultBlockState());
		put(level, box, 3, g + 4, -3, Blocks.BARREL.defaultBlockState());
		interior(level, box, rx, rz);
		// crates by the door, a puddle of oil
		put(level, box, -3, g + 1, 3, Blocks.BARREL.defaultBlockState());
		put(level, box, -4, g + 1, 3, Blocks.SPRUCE_PLANKS.defaultBlockState());
		put(level, box, -3, g + 2, 3, Blocks.SPRUCE_SLAB.defaultBlockState());
		putSoft(level, box, -2, g + 1, 5, Blocks.BLACK_CARPET.defaultBlockState());
		putSoft(level, box, -3, g + 1, 6, Blocks.BLACK_CARPET.defaultBlockState());
	}

	// ---------------------------------------------------------------- layout 2: toppled watchtower

	private void tower(WorldGenLevel level, BoundingBox box) {
		int g = groundY;
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				double d = Math.hypot(dx, dz);
				if (d > 4.4) {
					continue;
				}
				put(level, box, dx, g, dz, d > 3.4 ? Blocks.STONE_BRICKS.defaultBlockState() : floor(dx, dz));
				if (d <= 3.4) {
					continue;
				}
				// the stump: tall on the north side, broken low on the east where the top came down
				int h = 3 + (int) Math.round((dz < 0 ? 4 : 2) + lnoise(dx, dz, 61) * 2 - (dx > 1 ? 2 : 0));
				for (int y = 1; y <= h; y++) {
					boolean door = dz == 4 && dx == 0 && y <= 2;
					boolean slit = y == 3 && (dx == 0 && dz == -4 || Math.abs(dx) == 4 && dz == 0);
					put(level, box, dx, g + y, dz, door || slit ? Blocks.AIR.defaultBlockState() : wall(dx, y, dz));
				}
				if (h >= 4 && lnoise(dx, dz, 64) < 0.35) {
					vines(level, box, dx, dz, h);
				}
				if (lnoise(dx, dz, 66) < 0.3) {
					put(level, box, dx, g + h + 1, dz, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
				}
			}
		}
		// what's left of the first floor: a ledge of slabs on the north side, a ladder up to it
		for (int dx = -2; dx <= 2; dx++) {
			put(level, box, dx, g + 4, -3, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		}
		put(level, box, -1, g + 4, -2, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		for (int y = g + 1; y <= g + 4; y++) {
			put(level, box, 3, y, -1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		}
		put(level, box, 1, g + 5, -3, Blocks.CANDLE.defaultBlockState());
		put(level, box, -2, g + 5, -3, Blocks.LANTERN.defaultBlockState());
		// the fallen top: drums of stone brick lying east and south-east, battlements among them
		int[][] drums = { { 6, -2 }, { 7, 0 }, { 6, 3 } };
		for (int[] d : drums) {
			for (int ox = -1; ox <= 1; ox++) {
				for (int oy = 0; oy <= 2; oy++) {
					if (ox != 0 && oy == 1 || ox == 0) {
						putSoft(level, box, d[0], g + 1 + oy, d[1] + ox, oy == 1 && ox == 0 ? Blocks.AIR.defaultBlockState() : wall(d[0], oy, d[1] + ox));
					}
				}
			}
		}
		putSoft(level, box, 5, g + 1, 5, Blocks.STONE_BRICK_WALL.defaultBlockState());
		putSoft(level, box, -6, g + 1, 2, Blocks.STONE_BRICK_STAIRS.defaultBlockState());
		putSoft(level, box, -5, g + 1, -4, Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState());
		putSoft(level, box, 4, g + 1, 6, rubble(4, 6));
		// inside: a cot by the wall, a training target, a torn banner on the ledge
		put(level, box, -2, g + 1, -2, carpet());
		put(level, box, 1, g + 1, 2, Blocks.HAY_BLOCK.defaultBlockState());
		put(level, box, 1, g + 2, 2, Blocks.TARGET.defaultBlockState());
		put(level, box, 2, g + 2, 0, Blocks.AIR.defaultBlockState());
		put(level, box, 0, g + 1, 3, Blocks.AIR.defaultBlockState());
		put(level, box, 0, g + 2, 3, Blocks.AIR.defaultBlockState());
		put(level, box, 2, g + 3, 2, Blocks.COBWEB.defaultBlockState());
		put(level, box, -2, g + 3, 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		put(level, box, -2, g + 2, 1, Blocks.SPRUCE_FENCE.defaultBlockState());
		put(level, box, -2, g + 1, 1, Blocks.SPRUCE_FENCE.defaultBlockState());
	}

	// ---------------------------------------------------------------- shared

	/** Room furniture for the two rectangular layouts (room walls at x = +-rx, z = +-rz). */
	private void interior(WorldGenLevel level, BoundingBox box, int rx, int rz) {
		int g = groundY;
		// the desk with dead monitors on the west wall, a chair, a lever
		int wx = -rx + 1;
		for (int dz = -1; dz <= 1; dz++) {
			put(level, box, wx, g + 1, dz, Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
			put(level, box, wx, g + 2, dz, dz == 0 ? Blocks.BLACK_STAINED_GLASS_PANE.defaultBlockState()
					: Blocks.GRAY_STAINED_GLASS_PANE.defaultBlockState());
		}
		put(level, box, wx + 1, g + 1, 0, stair(Blocks.DARK_OAK_STAIRS, Direction.EAST, false));
		put(level, box, wx, g + 3, 1, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Direction.EAST));
		put(level, box, wx, g + 1, -2, Blocks.BARREL.defaultBlockState());
		put(level, box, wx, g + 2, -2, Blocks.CANDLE.defaultBlockState());
		// the bunk in the south-east corner, a rug in the hero's colour
		put(level, box, rx - 1, g + 1, rz - 2, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		put(level, box, rx - 1, g + 1, rz - 1, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				putSoft(level, box, dx, g + 1, dz, carpet());
			}
		}
		// the training corner: a target on a hay bale
		put(level, box, -rx + 1, g + 1, rz - 1, Blocks.HAY_BLOCK.defaultBlockState());
		put(level, box, -rx + 1, g + 2, rz - 1, Blocks.TARGET.defaultBlockState());
		// cobwebs, a lantern
		put(level, box, -rx + 1, g + 3, -rz + 1, Blocks.COBWEB.defaultBlockState());
		put(level, box, rx - 1, g + 3, -rz + 1, Blocks.COBWEB.defaultBlockState());
		put(level, box, -1, g + 4, 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		put(level, box, -1, g + 5, 2, Blocks.CHAIN.defaultBlockState());
	}

	private void vines(WorldGenLevel level, BoundingBox box, int dx, int dz, int h) {
		Direction out = Math.abs(dz) >= Math.abs(dx) ? (dz > 0 ? Direction.SOUTH : Direction.NORTH) : (dx > 0 ? Direction.EAST : Direction.WEST);
		int ox = dx + out.getStepX(), oz = dz + out.getStepZ();
		BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(out.getOpposite()), true);
		for (int y = h; y >= Math.max(1, h - 2); y--) {
			putSoft(level, box, ox, groundY + y, oz, vine);
		}
	}
}
