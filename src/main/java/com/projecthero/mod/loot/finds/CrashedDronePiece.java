package com.projecthero.mod.loot.finds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The Crashed Drone: a small scorched crater (radius ~5, 2 deep at the middle) with a ring of burnt ground, and in it
 * a wrecked quad-rotor tech drone -- an iron and weathered-copper hull, a light-blue glass sensor dome, an antenna,
 * iron-bar rotor arms ending in lit copper-bulb hubs with iron-trapdoor blades. Its engine (a campfire behind the hull)
 * still smokes. The cargo bay on the front is open and the Tier 1 Hero Vault sits inside it.
 *
 * <p>Variants: 0 both rotors on, 1 the right rotor torn off and thrown onto the crater rim, 2 the left one.
 */
public class CrashedDronePiece extends FindPiece {
	static final int HALF = 6;
	private static final double CRATER = 5.2;

	public CrashedDronePiece(int centerX, int surfaceY, int centerZ) {
		super(FindsMod.CRASHED_DRONE_PIECE, centerX, surfaceY, centerZ, HALF, 7, 10, 3);
	}

	public CrashedDronePiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(FindsMod.CRASHED_DRONE_PIECE, tag);
	}

	/** The crater floor's top block at a local column. */
	private int floorTop(int dx, int dz) {
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d > CRATER) {
			return groundY;
		}
		int depth = (int) Math.round(2.3 * (1 - (d / CRATER) * (d / CRATER)));
		return groundY - depth;
	}

	/** The hull's bottom layer (one above the crater's lowest floor). */
	private int base() {
		return groundY - 1;
	}

	@Override
	public BlockPos vaultPos() {
		return localToWorld(0, base(), 0);
	}

	@Override
	public void build(WorldGenLevel level, BoundingBox box) {
		// ---- the crater and the burnt ring around it
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				int x = wx(dx, dz), z = wz(dx, dz);
				if (!inBox(box, x, z)) {
					continue;
				}
				double d = Math.sqrt(dx * dx + dz * dz) + noise(x, z, 1) * 0.6 - 0.3;
				if (d <= CRATER) {
					int top = floorTop(dx, dz);
					double n = noise(x, z, 2);
					BlockState floor = n < 0.22 ? Blocks.COARSE_DIRT.defaultBlockState()
							: n < 0.42 ? Blocks.GRAVEL.defaultBlockState()
							: n < 0.62 ? Blocks.BLACKSTONE.defaultBlockState()
							: n < 0.78 ? Blocks.TUFF.defaultBlockState()
							: n < 0.86 ? Blocks.COAL_BLOCK.defaultBlockState()
							: Blocks.BLACK_CONCRETE_POWDER.defaultBlockState();
					footing(level, box, dx, dz, top - 1, Blocks.DIRT.defaultBlockState());
					put(level, box, dx, top, dz, floor);
					clear(level, box, dx, dz, top + 1, groundY + 10);
				} else if (d <= CRATER + 1.6) {
					// burnt ground at the column's own height (the terrain round the crater may not be flat)
					// a live world (tests, operator builds) has no worldgen heightmaps any more
					int top = level.getHeight(level instanceof net.minecraft.server.level.ServerLevel ? Heightmap.Types.OCEAN_FLOOR
							: Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
					BlockPos at = new BlockPos(x, top, z);
					BlockState s = level.getBlockState(at);
					if (noise(x, z, 3) < 0.6 && (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND))) {
						level.setBlock(at, noise(x, z, 4) < 0.7 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), 2);
						BlockPos above = at.above();
						if (level.getBlockState(above).canBeReplaced() && level.getFluidState(above).isEmpty()) {
							level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
			}
		}

		int b = base();
		BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
		BlockState copper = Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState();
		BlockState copperSlab = Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB.defaultBlockState();
		BlockState grate = Blocks.WAXED_OXIDIZED_COPPER_GRATE.defaultBlockState();

		// ---- the hull, bottom layer
		put(level, box, -1, b, -1, iron);
		put(level, box, 0, b, -1, iron);
		put(level, box, 1, b, -1, iron);
		put(level, box, -1, b, 0, copper);
		put(level, box, 1, b, 0, copper);
		put(level, box, -1, b, 1, iron);
		put(level, box, 1, b, 1, copperSlab);
		// the engine behind the hull, still smoking
		put(level, box, -1, b, -2, grate);
		put(level, box, 1, b, -2, grate);
		put(level, box, 0, b - 1, -2, Blocks.COAL_BLOCK.defaultBlockState());
		put(level, box, 0, b, -2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)
				.setValue(CampfireBlock.FACING, Direction.SOUTH));
		// top layer: panels, the glass sensor dome, the cargo-bay roof
		put(level, box, -1, b + 1, -1, copperSlab);
		put(level, box, 0, b + 1, -1, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
		put(level, box, 1, b + 1, -1, copperSlab);
		put(level, box, -1, b + 1, 0, iron);
		put(level, box, 0, b + 1, 0, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
		put(level, box, 1, b + 1, 0, iron);
		put(level, box, -1, b + 1, 1, Blocks.IRON_TRAPDOOR.defaultBlockState());
		put(level, box, 0, b + 1, 1, copperSlab.setValue(SlabBlock.TYPE, SlabType.TOP));
		put(level, box, 0, b + 2, 0, Blocks.LIGHTNING_ROD.defaultBlockState());
		put(level, box, -1, b + 2, -1, Blocks.REDSTONE_TORCH.defaultBlockState());
		// the cargo bay: open to the front, the vault inside, a clear way in
		vault(level, box, 0, b, 0, Direction.SOUTH);
		for (int dz = 1; dz <= 3; dz++) {
			clear(level, box, 0, dz, Math.max(b, floorTop(0, dz) + 1), b + (dz == 1 ? 0 : 2));
		}

		// ---- rotor arms and hubs
		rotor(level, box, -1, variant == 2);
		rotor(level, box, 1, variant == 1);

		// ---- debris strewn round the crater
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				boolean hull = Math.abs(dx) <= 1 && dz >= -2 && dz <= 1;
				boolean bay = dx == 0 && dz >= 1 && dz <= 4;
				boolean arm = dz == 0 && Math.abs(dx) <= 5;
				if (hull || bay || arm || dx * dx + dz * dz > 25) {
					continue;
				}
				int x = wx(dx, dz), z = wz(dx, dz);
				double n = noise(x, z, 5);
				if (n >= 0.09) {
					continue;
				}
				double m = noise(x, z, 6);
				BlockState junk = m < 0.25 ? Blocks.IRON_BARS.defaultBlockState()
						: m < 0.45 ? Blocks.REDSTONE_WIRE.defaultBlockState()
						: m < 0.62 ? Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState()
						: m < 0.8 ? Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB.defaultBlockState()
						: Blocks.IRON_TRAPDOOR.defaultBlockState();
				put(level, box, dx, floorTop(dx, dz) + 1, dz, junk);
			}
		}
	}

	/** One side's arm ({@code side} -1 left, +1 right); a torn-off rotor lies on the crater rim instead. */
	private void rotor(WorldGenLevel level, BoundingBox box, int side, boolean tornOff) {
		int b = base();
		BlockState bars = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
		if (tornOff) {
			// a stub of arm, then the rotor thrown up onto the rim
			put(level, box, 2 * side, b, 0, bars.setValue(side > 0 ? IronBarsBlock.EAST : IronBarsBlock.WEST, false));
			int hx = 4 * side, hz = -4;
			hub(level, box, hx, floorTop(hx, hz) + 1, hz, false);
			put(level, box, hx - side, floorTop(hx - side, hz + 1) + 1, hz + 1, Blocks.IRON_TRAPDOOR.defaultBlockState());
			return;
		}
		put(level, box, 2 * side, b, 0, bars);
		put(level, box, 3 * side, b, 0, bars);
		int hx = 4 * side;
		int hy = Math.max(b, floorTop(hx, 0) + 1);
		hub(level, box, hx, hy, 0, true);
	}

	private void hub(WorldGenLevel level, BoundingBox box, int hx, int hy, int hz, boolean blades) {
		put(level, box, hx, hy, hz, Blocks.WAXED_EXPOSED_COPPER_BULB.defaultBlockState().setValue(CopperBulbBlock.LIT, true));
		if (!blades) {
			return;
		}
		BlockState blade = Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF, Half.BOTTOM);
		put(level, box, hx, hy + 1, hz, blade);
		put(level, box, hx, hy + 1, hz - 1, blade);
		put(level, box, hx, hy + 1, hz + 1, blade);
		put(level, box, hx + Integer.signum(hx), hy + 1, hz, blade);
	}
}
