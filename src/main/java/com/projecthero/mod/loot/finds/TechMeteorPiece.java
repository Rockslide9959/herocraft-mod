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
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * v0.17.7 Tech Meteor: a small crater with a lump of meteorite in it that is not quite rock -- dark basalt and blackstone
 * shot through with glowing veins, crystals and end-rod "antennae" sticking out, bits of alien scrap (copper grates,
 * iron bars, lit copper bulbs) strewn round the rim. The vault sits in a cleft in the meteor; a half-buried scrap
 * barrel ({@code chests/finds/tech_meteor}) holds what fell off. Nobody guards it.
 *
 * <p>Layouts: 0 one meteor, 1 split in two with the vault in the gap, 2 a meteor shower (the main crater and two small
 * ones). Variants: the glow (0 cyan: sea lanterns and prismarine, 1 green: verdant froglights, 2 violet: amethyst and
 * pearlescent froglights).
 */
public class TechMeteorPiece extends FindPiece {
	static final int HALF = 7;
	public static final ResourceKey<LootTable> SCRAP = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/finds/tech_meteor"));
	private static final double R = 4.6;

	public TechMeteorPiece(int centerX, int surfaceY, int centerZ) {
		super(FindsMod.TECH_METEOR_PIECE, centerX, surfaceY, centerZ, HALF, 7, 10, 3, 3);
	}

	public TechMeteorPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(FindsMod.TECH_METEOR_PIECE, tag);
	}

	private int floorTop(int dx, int dz) {
		double d = Math.hypot(dx, dz);
		int depth = d > R ? 0 : (int) Math.round(2.4 * (1 - (d / R) * (d / R)));
		if (layout == 2) {
			for (int[] c : smallCraters()) {
				double e = Math.hypot(dx - c[0], dz - c[1]);
				if (e < 2.2) {
					depth = Math.max(depth, (int) Math.round(1.4 * (1 - e / 2.2)));
				}
			}
		}
		return groundY - depth;
	}

	private static int[][] smallCraters() {
		return new int[][] { { 5, -4 }, { -5, 4 } };
	}

	/** The bottom layer of the meteor (it sits in the crater floor). */
	private int rockBase() {
		return groundY - 2;
	}

	@Override
	public BlockPos vaultPos() {
		return localToWorld(0, rockBase() + 1, layout == 1 ? 0 : 1);
	}

	@Override
	public BlockPos stashPos() {
		return localToWorld(-2, floorTop(-2, 3), 3);
	}

	private BlockState glow() {
		return switch (variant) {
			case 1 -> Blocks.VERDANT_FROGLIGHT.defaultBlockState();
			case 2 -> Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
			default -> Blocks.SEA_LANTERN.defaultBlockState();
		};
	}

	private BlockState vein() {
		return switch (variant) {
			case 1 -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
			case 2 -> Blocks.AMETHYST_BLOCK.defaultBlockState();
			default -> Blocks.PRISMARINE_BRICKS.defaultBlockState();
		};
	}

	private BlockState crystal(Direction up) {
		return switch (variant) {
			case 2 -> Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, up);
			case 1 -> Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, up);
			default -> Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, up);
		};
	}

	private BlockState rock(int dx, int y, int dz) {
		double n = lnoise(dx, dz, 110 + y);
		return n < 0.3 ? Blocks.BASALT.defaultBlockState() : n < 0.55 ? Blocks.BLACKSTONE.defaultBlockState()
				: n < 0.7 ? Blocks.SMOOTH_BASALT.defaultBlockState() : n < 0.8 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
				: n < 0.9 ? vein() : glow();
	}

	@Override
	public void build(WorldGenLevel level, BoundingBox box) {
		int g = groundY;
		// ---- the crater(s) and the scorched ring
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				int x = wx(dx, dz), z = wz(dx, dz);
				if (!inBox(box, x, z)) {
					continue;
				}
				int top = floorTop(dx, dz);
				double d = Math.hypot(dx, dz) + lnoise(dx, dz, 111) * 0.6 - 0.3;
				if (top < g || d <= R) {
					double n = lnoise(dx, dz, 112);
					BlockState floor = n < 0.3 ? Blocks.TUFF.defaultBlockState() : n < 0.5 ? Blocks.GRAVEL.defaultBlockState()
							: n < 0.68 ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.8 ? Blocks.BLACKSTONE.defaultBlockState()
							: n < 0.92 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
					footing(level, box, dx, dz, top - 1, Blocks.DIRT.defaultBlockState());
					put(level, box, dx, top, dz, floor);
					clear(level, box, dx, dz, top + 1, g + 9);
				} else if (d <= R + 1.8) {
					int t = level.getHeight(level instanceof ServerLevel ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
					BlockPos at = new BlockPos(x, t, z);
					BlockState s = level.getBlockState(at);
					if (lnoise(dx, dz, 113) < 0.55 && (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.SNOW_BLOCK))) {
						level.setBlock(at, lnoise(dx, dz, 114) < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.TUFF.defaultBlockState(), 2);
						BlockPos above = at.above();
						if (level.getBlockState(above).canBeReplaced() && level.getFluidState(above).isEmpty()) {
							level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
			}
		}
		int b = rockBase();
		// ---- the meteor
		if (layout == 1) {
			lump(level, box, -2, 0, 1.6, b);
			lump(level, box, 2, -1, 1.7, b);
			for (int dz = -1; dz <= 3; dz++) {
				clear(level, box, 0, dz, b + 1, b + 4);
				footing(level, box, 0, dz, b, Blocks.BLACKSTONE.defaultBlockState());
				put(level, box, 0, b, dz, Blocks.MAGMA_BLOCK.defaultBlockState());
			}
			put(level, box, 0, b, 0, Blocks.BLACKSTONE.defaultBlockState());
			vault(level, box, 0, b + 1, 0, Direction.SOUTH);
			put(level, box, 0, b + 1, -1, glow());
			put(level, box, 0, b + 2, 0, Blocks.SMOOTH_BASALT.defaultBlockState()); // a lintel of rock over the gap
		} else {
			lump(level, box, 0, -1, 2.2, b);
			// the cleft on the south side holding the vault
			clear(level, box, 0, 1, b + 1, b + 2);
			clear(level, box, 0, 2, b + 1, b + 2);
			put(level, box, 0, b, 1, Blocks.BLACKSTONE.defaultBlockState());
			vault(level, box, 0, b + 1, 1, Direction.SOUTH);
			put(level, box, -1, b + 2, 1, glow());
			put(level, box, 1, b + 2, 1, vein());
		}
		// ---- antennae / crystals growing out of the top
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -3; dz <= 1; dz++) {
				int top = topSolid(level, dx, dz, b + 6, b);
				if (top >= b + 2 && lnoise(dx, dz, 115) < 0.3) {
					putSoft(level, box, dx, top + 1, dz, crystal(Direction.UP));
				}
			}
		}
		// ---- smaller craters (layout 2) with a glowing chunk each
		if (layout == 2) {
			for (int[] c : smallCraters()) {
				int t = floorTop(c[0], c[1]);
				put(level, box, c[0], t + 1, c[1], rock(c[0], 1, c[1]));
				put(level, box, c[0] + 1, t + 1, c[1], glow());
				putSoft(level, box, c[0], t + 2, c[1], crystal(Direction.UP));
			}
		}
		// ---- strewn scrap
		for (int dx = -HALF + 1; dx <= HALF - 1; dx++) {
			for (int dz = -HALF + 1; dz <= HALF - 1; dz++) {
				double d = Math.hypot(dx, dz);
				if (d < 2.6 || d > R + 1.5 || lnoise(dx, dz, 116) >= 0.12 || (dx == 0 && dz > 0)) {
					continue;
				}
				double m = lnoise(dx, dz, 117);
				BlockState junk = m < 0.25 ? Blocks.IRON_BARS.defaultBlockState()
						: m < 0.45 ? Blocks.WAXED_EXPOSED_COPPER_GRATE.defaultBlockState()
						: m < 0.6 ? Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(CopperBulbBlock.LIT, true)
						: m < 0.75 ? Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.ChainBlock.AXIS, Direction.Axis.X)
						: m < 0.88 ? Blocks.IRON_TRAPDOOR.defaultBlockState() : Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState();
				int t = d <= R ? floorTop(dx, dz) : topSolid(level, dx, dz, g + 3, g - 3);
				putSoft(level, box, dx, t + 1, dz, junk);
			}
		}
		// ---- the half-buried scrap barrel
		int st = floorTop(-2, 3);
		lootContainer(level, box, -2, st, 3, facing(Blocks.BARREL, Direction.UP), SCRAP);
		clear(level, box, -2, 3, st + 1, st + 2);
		putSoft(level, box, -3, st + 1, 3, Blocks.IRON_TRAPDOOR.defaultBlockState());
	}

	/** A rough ball of meteorite centred on local (cx, cz), sunk into the crater floor. */
	private void lump(WorldGenLevel level, BoundingBox box, int ox, int oz, double r, int b) {
		int ri = (int) Math.ceil(r) + 1;
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dz = -ri; dz <= ri; dz++) {
				for (int dy = 0; dy <= ri + 1; dy++) {
					double d = Math.sqrt(dx * dx + dz * dz + (dy - 0.8) * (dy - 0.8) * 1.3) + lnoise(ox + dx, oz + dz, 118 + dy) * 0.7 - 0.35;
					if (d <= r) {
						put(level, box, ox + dx, b + dy, oz + dz, rock(ox + dx, dy, oz + dz));
					}
				}
				if (dx * dx + dz * dz <= r * r) {
					footing(level, box, ox + dx, oz + dz, b - 1, Blocks.BLACKSTONE.defaultBlockState(), 3);
				}
			}
		}
	}
}
