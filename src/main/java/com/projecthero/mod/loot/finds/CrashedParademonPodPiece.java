package com.projecthero.mod.loot.finds;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * v0.17.7 Crashed Parademon Pod: an Apokolips drop pod that came down hard -- a scorched crater of blackstone, basalt
 * and magma, smoke still rising from the burn marks, and the pod itself: a blackstone-and-iron capsule with red glass
 * ports and lit firepits, its hatch blown off and lying in the dirt. Inside, an Apokolips tech cache (themed barrel,
 * {@code chests/finds/crashed_parademon_pod}) and the Tier 1 Hero Vault. Two Parademons (one with a blaster) still
 * guard it.
 *
 * <p>Layouts: 0 upright pod in the crater, 1 pod on its side at the end of a long skid trench, 2 twin pods (one intact,
 * one shattered). Variants: the trim (0 gilded blackstone, 1 red nether brick, 2 oxidised copper), so no two look alike.
 */
public class CrashedParademonPodPiece extends FindPiece {
	static final int HALF = 8;
	public static final ResourceKey<LootTable> CACHE = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/finds/crashed_parademon_pod"));

	public CrashedParademonPodPiece(int centerX, int surfaceY, int centerZ) {
		super(FindsMod.PARADEMON_POD_PIECE, centerX, surfaceY, centerZ, HALF, 8, 12, 3, 3);
	}

	public CrashedParademonPodPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(FindsMod.PARADEMON_POD_PIECE, tag);
	}

	/** Local crater centre and radius per layout. */
	private double craterR() {
		return layout == 1 ? 4.6 : 5.6;
	}

	private int craterZ() {
		return layout == 1 ? -3 : 0;
	}

	/** The crater floor's top block at a local column (the skid trench of layout 1 included). */
	private int floorTop(int dx, int dz) {
		double r = craterR();
		double d = Math.hypot(dx, dz - craterZ());
		int depth = d > r ? 0 : (int) Math.round(2.6 * (1 - (d / r) * (d / r)));
		if (layout == 1 && Math.abs(dx) <= 1 && dz >= craterZ() && dz <= 7) {
			depth = Math.max(depth, Math.abs(dx) == 1 ? 1 : 2);
		}
		if (layout == 2) {
			double d2 = Math.hypot(dx + 5, dz + 5);
			if (d2 < 2.6) {
				depth = Math.max(depth, (int) Math.round(1.5 * (1 - d2 / 2.6)));
			}
		}
		return groundY - depth;
	}

	private int podBase() {
		return groundY - 2;
	}

	/** Solid ground under a pod's footprint (the crater dips deeper than the pod floor at its middle). */
	private void base(WorldGenLevel level, BoundingBox box, int x0, int z0, int x1, int z1) {
		for (int dx = x0; dx <= x1; dx++) {
			for (int dz = z0; dz <= z1; dz++) {
				footing(level, box, dx, dz, podBase() - 1, Blocks.BLACKSTONE.defaultBlockState());
			}
		}
	}

	@Override
	public BlockPos vaultPos() {
		return switch (layout) {
			case 1 -> localToWorld(0, podBase() + 1, -4);
			case 2 -> localToWorld(1, podBase() + 1, 1);
			default -> localToWorld(0, podBase() + 1, -1);
		};
	}

	@Override
	public BlockPos stashPos() {
		return switch (layout) {
			case 1 -> localToWorld(0, podBase() + 1, -6);
			case 2 -> localToWorld(-1, podBase() + 1, 1);
			default -> localToWorld(1, podBase() + 1, -1);
		};
	}

	private BlockState trim() {
		return switch (variant) {
			case 1 -> Blocks.RED_NETHER_BRICKS.defaultBlockState();
			case 2 -> Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState();
			default -> Blocks.GILDED_BLACKSTONE.defaultBlockState();
		};
	}

	private BlockState hull(int dx, int y, int dz) {
		double n = lnoise(dx, dz, 80 + y);
		return n < 0.5 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : n < 0.75 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
				: n < 0.9 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
	}

	@Override
	public void build(WorldGenLevel level, BoundingBox box) {
		int g = groundY;
		// ---- the crater, the skid trench, the burnt ring
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				int x = wx(dx, dz), z = wz(dx, dz);
				if (!inBox(box, x, z)) {
					continue;
				}
				int top = floorTop(dx, dz);
				double d = Math.hypot(dx, dz - craterZ()) + lnoise(dx, dz, 81) * 0.8 - 0.4;
				if (top < g || d <= craterR()) {
					double n = lnoise(dx, dz, 82);
					BlockState floor = n < 0.25 ? Blocks.BLACKSTONE.defaultBlockState() : n < 0.45 ? Blocks.BASALT.defaultBlockState()
							: n < 0.6 ? Blocks.MAGMA_BLOCK.defaultBlockState() : n < 0.75 ? Blocks.COARSE_DIRT.defaultBlockState()
							: n < 0.88 ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState();
					footing(level, box, dx, dz, top - 1, Blocks.DIRT.defaultBlockState());
					put(level, box, dx, top, dz, floor);
					clear(level, box, dx, dz, top + 1, g + 12);
				} else if (d <= craterR() + 2.4) {
					int top2 = level.getHeight(level instanceof ServerLevel ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
					BlockPos at = new BlockPos(x, top2, z);
					BlockState s = level.getBlockState(at);
					if (lnoise(dx, dz, 83) < 0.65 && (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.SNOW_BLOCK))) {
						level.setBlock(at, lnoise(dx, dz, 84) < 0.6 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), 2);
						BlockPos above = at.above();
						if (level.getBlockState(above).canBeReplaced() && level.getFluidState(above).isEmpty()) {
							level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
			}
		}
		switch (layout) {
			case 1 -> sidePod(level, box);
			case 2 -> twinPods(level, box);
			default -> uprightPod(level, box);
		}
		// ---- burning debris (campfires, never spreading fire) and smoke
		int[][] fires = layout == 1 ? new int[][] { { 2, 3 }, { -2, 6 }, { 3, -6 } } : new int[][] { { 3, 3 }, { -4, 1 }, { 2, -4 } };
		for (int[] f : fires) {
			int t = floorTop(f[0], f[1]);
			put(level, box, f[0], t + 1, f[1], Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		}
		// ---- the guards
		int[][] spots = layout == 1 ? new int[][] { { 2, 0 }, { -3, -1 } } : layout == 2 ? new int[][] { { -1, 5 }, { 4, -3 } }
				: new int[][] { { 3, 1 }, { -3, -2 } };
		for (int i = 0; i < spots.length; i++) {
			int x = wx(spots[i][0], spots[i][1]), z = wz(spots[i][0], spots[i][1]);
			if (!inBox(box, x, z)) {
				continue;
			}
			int t = floorTop(spots[i][0], spots[i][1]);
			clear(level, box, spots[i][0], spots[i][1], t + 1, t + 2);
			FindMobs.spawn(level, i == 0 ? FindMobs.Kind.PARADEMON : FindMobs.Kind.PARADEMON_RANGED, new BlockPos(x, t + 1, z),
					dir(Direction.SOUTH).toYRot(), 10);
		}
	}

	/** Layout 0: the capsule stands in the crater, 3 x 3 and 5 tall, its front hatch blown off. */
	private void uprightPod(WorldGenLevel level, BoundingBox box) {
		int b = podBase();
		base(level, box, -2, -3, 2, 1);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -3; dz <= 1; dz++) {
				boolean corner = Math.abs(dx) == 2 && (dz == -3 || dz == 1);
				if (corner) {
					continue;
				}
				boolean shell = Math.abs(dx) == 2 || dz == -3 || dz == 1;
				for (int y = b; y <= b + 5; y++) {
					BlockState s;
					if (y == b) {
						s = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
					} else if (y == b + 5) {
						s = Math.abs(dx) <= 1 && dz >= -2 && dz <= 0 ? trim() : Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState();
					} else if (!shell) {
						s = Blocks.AIR.defaultBlockState();
					} else if (dz == 1 && Math.abs(dx) <= 1 && y <= b + 3) {
						s = Blocks.AIR.defaultBlockState(); // the blown-off hatch
					} else if (y == b + 3 && (dx == 0 || dz == -1)) {
						s = Blocks.RED_STAINED_GLASS.defaultBlockState();
					} else if (y == b + 4) {
						s = trim();
					} else {
						s = hull(dx, y, dz);
					}
					put(level, box, dx, y, dz, s);
				}
			}
		}
		// the nose spike and its warning lamp
		put(level, box, 0, b + 6, -1, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
		put(level, box, 0, b + 7, -1, Blocks.LIGHTNING_ROD.defaultBlockState());
		put(level, box, 0, b + 5, -1, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
		put(level, box, 0, b + 4, -1, Blocks.REDSTONE_BLOCK.defaultBlockState());
		// inside: the cache, the vault, a seat, chains
		vault(level, box, 0, b + 1, -1, Direction.SOUTH);
		lootContainer(level, box, 1, b + 1, -1, facing(Blocks.BARREL, Direction.SOUTH), CACHE);
		put(level, box, -1, b + 1, -2, stair(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.SOUTH, false));
		put(level, box, -1, b + 3, -1, Blocks.CHAIN.defaultBlockState());
		put(level, box, 1, b + 3, -2, Blocks.SOUL_LANTERN.defaultBlockState());
		clear(level, box, 0, 0, b + 1, b + 2);
		clear(level, box, 0, 1, b + 1, b + 3);
		// fins at the base
		for (Direction d : new Direction[] { Direction.EAST, Direction.WEST }) {
			int fx = d.getStepX() * 3;
			put(level, box, fx, b, -1, stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, d.getOpposite(), false));
			put(level, box, fx, b + 1, -1, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
		}
		// the hatch lies in front, face down
		hatch(level, box, -1, 4);
	}

	/** Layout 1: the capsule lies on its side along the trench it dug; the open hatch faces the trench. */
	private void sidePod(WorldGenLevel level, BoundingBox box) {
		int b = podBase();
		base(level, box, -2, -7, 2, -2);
		for (int dz = -7; dz <= -2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int y = b; y <= b + 4; y++) {
					int ry = y - b - 2;
					double r = Math.hypot(dx, ry);
					if (r > 2.4 || (dz == -7 || dz == -2) && r > 1.6) {
						continue;
					}
					boolean shell = r > 1.3 || dz == -7;
					BlockState s;
					if (!shell) {
						s = Blocks.AIR.defaultBlockState();
					} else if (dz == -2 && Math.abs(dx) <= 1 && ry >= -1 && ry <= 1) {
						s = Blocks.AIR.defaultBlockState(); // hatch end blown open
					} else if (dz == -4 && ry == 2) {
						s = Blocks.RED_STAINED_GLASS.defaultBlockState();
					} else if (dz == -5 || dz == -3) {
						s = trim();
					} else {
						s = hull(dx, y, dz);
					}
					put(level, box, dx, y, dz, s);
				}
			}
		}
		// the floor inside is the lower curve: lay slabs so it walks flat
		for (int dz = -6; dz <= -2; dz++) {
			put(level, box, 0, b, dz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
			put(level, box, -1, b + 1, dz, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
			put(level, box, 1, b + 1, dz, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
		}
		vault(level, box, 0, b + 1, -4, Direction.SOUTH);
		lootContainer(level, box, 0, b + 1, -6, facing(Blocks.BARREL, Direction.SOUTH), CACHE);
		clear(level, box, 0, -3, b + 1, b + 2);
		clear(level, box, 0, -5, b + 1, b + 2);
		put(level, box, 0, b + 3, -5, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
		put(level, box, 0, b + 4, -5, Blocks.REDSTONE_BLOCK.defaultBlockState());
		put(level, box, 1, b + 3, -3, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Z));
		// the fins stick up out of the dirt
		put(level, box, 0, b + 5, -6, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
		put(level, box, 0, b + 6, -6, Blocks.LIGHTNING_ROD.defaultBlockState());
		put(level, box, 3, b + 1, -6, stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.WEST, false));
		put(level, box, -3, b + 1, -6, stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.EAST, false));
		// gouged earth heaped along the trench sides, and the hatch at its far end
		for (int dz = -1; dz <= 6; dz++) {
			for (int s : new int[] { -2, 2 }) {
				if (lnoise(s, dz, 85) < 0.6) {
					putSoft(level, box, s, groundY + 1, dz, lnoise(s, dz, 86) < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState()
							: Blocks.ROOTED_DIRT.defaultBlockState());
				}
			}
		}
		hatch(level, box, 0, 6);
	}

	/** Layout 2: one intact capsule (smaller, 3 x 3) holding the cache, and a shattered one off to the side. */
	private void twinPods(WorldGenLevel level, BoundingBox box) {
		int b = podBase();
		base(level, box, -1, -1, 3, 3);
		for (int dx = -1; dx <= 3; dx++) {
			for (int dz = -1; dz <= 3; dz++) {
				boolean corner = (dx == -1 || dx == 3) && (dz == -1 || dz == 3);
				if (corner) {
					continue;
				}
				boolean shell = dx == -1 || dx == 3 || dz == -1 || dz == 3;
				for (int y = b; y <= b + 4; y++) {
					BlockState s;
					if (y == b) {
						s = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
					} else if (y == b + 4) {
						s = dx >= 0 && dx <= 2 && dz >= 0 && dz <= 2 ? trim() : Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState();
					} else if (!shell) {
						s = Blocks.AIR.defaultBlockState();
					} else if (dz == 3 && dx >= 0 && dx <= 2 && y <= b + 2) {
						s = Blocks.AIR.defaultBlockState();
					} else if (y == b + 2 && (dx == 1 || dz == 1)) {
						s = Blocks.RED_STAINED_GLASS.defaultBlockState();
					} else {
						s = hull(dx, y, dz);
					}
					put(level, box, dx, y, dz, s);
				}
			}
		}
		put(level, box, 1, b + 5, 1, Blocks.LIGHTNING_ROD.defaultBlockState());
		put(level, box, 1, b + 3, 0, Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		vault(level, box, 1, b + 1, 1, Direction.SOUTH);
		put(level, box, -1, b + 1, 1, Blocks.AIR.defaultBlockState());
		lootContainer(level, box, -1, b + 1, 1, facing(Blocks.BARREL, Direction.EAST), CACHE);
		clear(level, box, 1, 2, b + 1, b + 2);
		clear(level, box, 1, 3, b + 1, b + 2);
		// the shattered pod: a ring of broken hull in the small crater, glass and chains spilled out
		for (int dx = -7; dx <= -3; dx++) {
			for (int dz = -7; dz <= -3; dz++) {
				double r = Math.hypot(dx + 5, dz + 5);
				if (r < 1.2 || r > 2.3) {
					continue;
				}
				int t = floorTop(dx, dz);
				int h = 1 + (int) (lnoise(dx, dz, 87) * 3);
				for (int y = 1; y <= h; y++) {
					put(level, box, dx, t + y, dz, y == h && lnoise(dx, dz, 88) < 0.4 ? trim() : hull(dx, y, dz));
				}
			}
		}
		putSoft(level, box, -5, floorTop(-5, -5) + 1, -5, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
		putSoft(level, box, -2, floorTop(-2, -4) + 1, -4, Blocks.RED_STAINED_GLASS_PANE.defaultBlockState());
		putSoft(level, box, -4, floorTop(-4, -1) + 1, -1, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X));
		hatch(level, box, 4, 5);
	}

	/** The blown-off hatch: a 2 x 2 plate of trapdoors and slabs lying on the ground at local (x, z). */
	private void hatch(WorldGenLevel level, BoundingBox box, int x, int z) {
		for (int ox = 0; ox <= 1; ox++) {
			for (int oz = 0; oz <= 1; oz++) {
				int t = floorTop(x + ox, z + oz);
				BlockState s = (ox + oz) % 2 == 0 ? Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM)
						: Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
				put(level, box, x + ox, t + 1, z + oz, s);
			}
		}
		int t = floorTop(x, z - 1);
		putSoft(level, box, x, t + 1, z - 1, Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
	}
}
