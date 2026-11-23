package com.projecthero.mod.loot.finds;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * v0.17.7 Abandoned Workshop: an inventor's lab nobody has swept in years. Dust (light grey carpet) and cobwebs over
 * everything; long workbenches with a smithing table, a grindstone, a stonecutter and a chipped anvil; blueprints on a
 * cartography table; dead lamps; a copper "tesla coil" in the corner; and the centrepiece, a <b>broken suit frame</b>
 * -- a half-built iron suit on a stand, hung from chains, its arm lying on the bench. A parts bin (themed barrel,
 * {@code chests/finds/abandoned_workshop}, always Salvaged Tech) and the Tier 1 Hero Vault.
 *
 * <p>Layouts: 0 timber barn workshop (gabled roof, big double doors), 1 brick garage (flat roof, a roll-up door jammed
 * half open, a skylight), 2 copper-roofed lab (stone base, stepped copper roof, a lightning-rod mast). Variants: the
 * wood (0 spruce, 1 oak, 2 dark oak) for benches, frames and doors.
 */
public class AbandonedWorkshopPiece extends FindPiece {
	static final int HALF = 8;
	public static final ResourceKey<LootTable> PARTS = ResourceKey.create(Registries.LOOT_TABLE, ProjectHeroMod.id("chests/finds/abandoned_workshop"));
	/** Room walls at x = +-RX, z = +-RZ; the doorway is in the south wall. */
	private static final int RX = 5;
	private static final int RZ = 4;

	public AbandonedWorkshopPiece(int centerX, int surfaceY, int centerZ) {
		super(FindsMod.ABANDONED_WORKSHOP_PIECE, centerX, surfaceY, centerZ, HALF, 8, 12, 3, 3);
	}

	public AbandonedWorkshopPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(FindsMod.ABANDONED_WORKSHOP_PIECE, tag);
	}

	@Override
	public BlockPos vaultPos() {
		return localToWorld(-4, groundY + 1, -3);
	}

	@Override
	public BlockPos stashPos() {
		return localToWorld(4, groundY + 1, 2);
	}

	/** Where the broken suit frame's stand is. */
	public BlockPos suitFramePos() {
		return localToWorld(0, groundY + 1, -2);
	}

	private Block planks() {
		return switch (variant) {
			case 1 -> Blocks.OAK_PLANKS;
			case 2 -> Blocks.DARK_OAK_PLANKS;
			default -> Blocks.SPRUCE_PLANKS;
		};
	}

	private Block log() {
		return switch (variant) {
			case 1 -> Blocks.OAK_LOG;
			case 2 -> Blocks.DARK_OAK_LOG;
			default -> Blocks.SPRUCE_LOG;
		};
	}

	private Block slab() {
		return switch (variant) {
			case 1 -> Blocks.OAK_SLAB;
			case 2 -> Blocks.DARK_OAK_SLAB;
			default -> Blocks.SPRUCE_SLAB;
		};
	}

	private Block stairs() {
		return switch (variant) {
			case 1 -> Blocks.OAK_STAIRS;
			case 2 -> Blocks.DARK_OAK_STAIRS;
			default -> Blocks.SPRUCE_STAIRS;
		};
	}

	private Block trapdoor() {
		return switch (variant) {
			case 1 -> Blocks.OAK_TRAPDOOR;
			case 2 -> Blocks.DARK_OAK_TRAPDOOR;
			default -> Blocks.SPRUCE_TRAPDOOR;
		};
	}

	private BlockState wall(int dx, int y, int dz) {
		double n = lnoise(dx, dz, 90 + y);
		return switch (layout) {
			case 1 -> n < 0.15 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : n < 0.25 ? Blocks.MUD_BRICKS.defaultBlockState()
					: Blocks.BRICKS.defaultBlockState();
			case 2 -> y <= 1 ? (n < 0.3 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState())
					: n < 0.2 ? Blocks.TUFF_BRICKS.defaultBlockState() : Blocks.POLISHED_TUFF.defaultBlockState();
			default -> n < 0.12 ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : planks().defaultBlockState();
		};
	}

	@Override
	public void build(WorldGenLevel level, BoundingBox box) {
		int g = groundY;
		// ---- site
		for (int dx = -HALF + 1; dx <= HALF - 1; dx++) {
			for (int dz = -HALF + 1; dz <= HALF - 1; dz++) {
				boolean inside = Math.abs(dx) <= RX && Math.abs(dz) <= RZ;
				clear(level, box, dx, dz, g + 1, g + 11);
				footing(level, box, dx, dz, g - 1, Blocks.DIRT.defaultBlockState());
				double n = lnoise(dx, dz, 91);
				put(level, box, dx, g, dz, inside ? (n < 0.5 ? Blocks.STONE_BRICKS.defaultBlockState() : n < 0.75
						? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState())
						: n < 0.3 ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.38 ? Blocks.GRAVEL.defaultBlockState()
								: Blocks.GRASS_BLOCK.defaultBlockState());
			}
		}
		// ---- walls (the three layouts share the room, and differ in walls, door and roof)
		int wallH = 4;
		for (int dx = -RX; dx <= RX; dx++) {
			for (int dz = -RZ; dz <= RZ; dz++) {
				boolean edge = Math.abs(dx) == RX || Math.abs(dz) == RZ;
				if (!edge) {
					continue;
				}
				boolean corner = Math.abs(dx) == RX && Math.abs(dz) == RZ;
				for (int y = 1; y <= wallH; y++) {
					boolean door = dz == RZ && Math.abs(dx) <= 1 && y <= 3;
					boolean window = y == 2 && !corner && (Math.abs(dx) == RX && Math.abs(dz) == 1 || dz == -RZ && Math.abs(dx) == 3);
					BlockState s;
					if (door) {
						s = Blocks.AIR.defaultBlockState();
					} else if (corner || layout == 0 && (dz == RZ && Math.abs(dx) == 2)) {
						s = layout == 2 ? Blocks.POLISHED_TUFF.defaultBlockState() : log().defaultBlockState();
					} else if (window) {
						s = lnoise(dx, dz, 92) < 0.35 ? Blocks.AIR.defaultBlockState() : Blocks.GLASS_PANE.defaultBlockState();
					} else {
						s = wall(dx, y, dz);
					}
					put(level, box, dx, g + y, dz, s);
				}
			}
		}
		switch (layout) {
			case 1 -> garageRoof(level, box, wallH);
			case 2 -> copperRoof(level, box, wallH);
			default -> barnRoof(level, box, wallH);
		}
		furnish(level, box);
		// ---- outside: a rusty cart wheel, a scrap pile, a lamp post by the door
		putSoft(level, box, 6, g + 1, 2, Blocks.CHIPPED_ANVIL.defaultBlockState()
				.setValue(AnvilBlock.FACING, Direction.NORTH));
		putSoft(level, box, 6, g + 1, 3, Blocks.WAXED_EXPOSED_COPPER_GRATE.defaultBlockState());
		putSoft(level, box, 7, g + 1, 3, Blocks.IRON_TRAPDOOR.defaultBlockState());
		putSoft(level, box, -6, g + 1, 4, Blocks.COMPOSTER.defaultBlockState());
		putSoft(level, box, -2, g + 1, 6, Blocks.SPRUCE_FENCE.defaultBlockState());
		putSoft(level, box, -2, g + 2, 6, Blocks.SPRUCE_FENCE.defaultBlockState());
		putSoft(level, box, -2, g + 3, 6, Blocks.LANTERN.defaultBlockState());
	}

	private void barnRoof(WorldGenLevel level, BoundingBox box, int h) {
		int g = groundY;
		// gable along x: stairs climbing from both long sides to a ridge at z = 0
		for (int dx = -RX - 1; dx <= RX + 1; dx++) {
			for (int step = 0; step <= RZ; step++) {
				int y = g + h + 1 + step;
				boolean gap = dx >= 1 && dx <= 3 && step == 1 && lnoise(dx, step, 93) < 0.7; // a hole in the roof
				if (step == RZ) {
					put(level, box, dx, y, 0, slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
				} else if (!gap) {
					put(level, box, dx, y, RZ - step, stair(stairs(), Direction.NORTH, false));
					put(level, box, dx, y, -RZ + step, stair(stairs(), Direction.SOUTH, false));
				}
			}
		}
		// the gable ends: planks filling the triangles
		for (int step = 0; step < RZ; step++) {
			for (int dz = -RZ + step + 1; dz <= RZ - step - 1; dz++) {
				put(level, box, -RX, g + h + 1 + step, dz, planks().defaultBlockState());
				put(level, box, RX, g + h + 1 + step, dz, planks().defaultBlockState());
			}
		}
		put(level, box, RX, g + h + 2, 0, Blocks.GLASS_PANE.defaultBlockState());
		// the doors: one hangs open, one is gone
		Block door = variant == 1 ? Blocks.OAK_DOOR : variant == 2 ? Blocks.DARK_OAK_DOOR : Blocks.SPRUCE_DOOR;
		put(level, box, -1, g + 1, RZ, door.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.OPEN, true)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		put(level, box, -1, g + 2, RZ, door.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.OPEN, true)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		put(level, box, 0, g + 3, RZ, log().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		put(level, box, 1, g + 3, RZ, log().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		put(level, box, -1, g + 3, RZ, log().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		// the fallen second door outside
		putSoft(level, box, 2, g + 1, RZ + 2, trapdoor().defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM));
		putSoft(level, box, 2, g + 1, RZ + 3, trapdoor().defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM));
	}

	private void garageRoof(WorldGenLevel level, BoundingBox box, int h) {
		int g = groundY;
		for (int dx = -RX; dx <= RX; dx++) {
			for (int dz = -RZ; dz <= RZ; dz++) {
				boolean skylight = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
				BlockState s = skylight ? (lnoise(dx, dz, 94) < 0.4 ? Blocks.AIR.defaultBlockState() : Blocks.GLASS.defaultBlockState())
						: Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
				put(level, box, dx, g + h + 1, dz, s);
			}
		}
		// a parapet with a chimney pipe and a rusty water tank
		for (int dx = -RX; dx <= RX; dx++) {
			put(level, box, dx, g + h + 1, -RZ, Blocks.BRICK_WALL.defaultBlockState());
		}
		put(level, box, -3, g + h + 2, -2, Blocks.WAXED_WEATHERED_COPPER.defaultBlockState());
		put(level, box, -3, g + h + 3, -2, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB.defaultBlockState());
		put(level, box, 3, g + h + 2, -3, Blocks.ANDESITE_WALL.defaultBlockState());
		put(level, box, 3, g + h + 3, -3, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
		// the roll-up door, jammed half open: iron trapdoors across the top of the doorway
		for (int dx = -1; dx <= 1; dx++) {
			put(level, box, dx, g + 3, RZ, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH)
					.setValue(TrapDoorBlock.OPEN, true));
		}
		put(level, box, -2, g + 3, RZ + 1, Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.SOUTH)
				.setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL));
		// an oil-stained drive and a sign-board over the door
		for (int dz = RZ + 1; dz <= RZ + 3; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				put(level, box, dx, g, dz, lnoise(dx, dz, 95) < 0.3 ? Blocks.GRAY_CONCRETE_POWDER.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
			}
		}
	}

	private void copperRoof(WorldGenLevel level, BoundingBox box, int h) {
		int g = groundY;
		// a stepped copper roof: three rings of cut copper stairs climbing to a flat top with a mast
		for (int ring = 0; ring <= 2; ring++) {
			int rx = RX - ring, rz = RZ - ring, y = g + h + 1 + ring;
			for (int dx = -rx; dx <= rx; dx++) {
				for (int dz = -rz; dz <= rz; dz++) {
					boolean edge = Math.abs(dx) == rx || Math.abs(dz) == rz;
					if (!edge && ring < 2) {
						continue;
					}
					BlockState s;
					if (ring == 2) {
						s = Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState();
					} else if (Math.abs(dz) == rz && Math.abs(dx) < rx) {
						s = stair(Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, dz > 0 ? Direction.NORTH : Direction.SOUTH, false);
					} else if (Math.abs(dx) == rx && Math.abs(dz) < rz) {
						s = stair(Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, dx > 0 ? Direction.WEST : Direction.EAST, false);
					} else {
						s = Blocks.WAXED_EXPOSED_CUT_COPPER.defaultBlockState();
					}
					// patched with a few holes
					if (lnoise(dx, dz, 96 + ring) < 0.08 && ring < 2) {
						s = Blocks.AIR.defaultBlockState();
					}
					put(level, box, dx, y, dz, s);
				}
			}
		}
		put(level, box, 0, g + h + 3, 0, Blocks.WAXED_COPPER_GRATE.defaultBlockState());
		put(level, box, 0, g + h + 4, 0, Blocks.IRON_BARS.defaultBlockState());
		put(level, box, 0, g + h + 5, 0, Blocks.IRON_BARS.defaultBlockState());
		put(level, box, 0, g + h + 6, 0, Blocks.LIGHTNING_ROD.defaultBlockState());
		// arched entry: tuff brick stairs over the doorway, a copper door frame
		put(level, box, -1, g + 3, RZ, stair(Blocks.TUFF_BRICK_STAIRS, Direction.SOUTH, true));
		put(level, box, 1, g + 3, RZ, stair(Blocks.TUFF_BRICK_STAIRS, Direction.SOUTH, true));
		put(level, box, 0, g + 3, RZ, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
		put(level, box, -2, g + 1, RZ + 1, Blocks.WAXED_EXPOSED_COPPER_BULB.defaultBlockState());
		put(level, box, 2, g + 1, RZ + 1, Blocks.WAXED_EXPOSED_COPPER_BULB.defaultBlockState());
	}

	/** The workshop inside, the same plan in every layout. */
	private void furnish(WorldGenLevel level, BoundingBox box) {
		int g = groundY;
		BlockState benchTop = slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
		// the long bench along the east wall: smithing table, a top-slab bench, grindstone, stonecutter
		put(level, box, 4, g + 1, -3, Blocks.SMITHING_TABLE.defaultBlockState());
		put(level, box, 4, g + 1, -2, benchTop);
		put(level, box, 4, g + 1, -1, benchTop);
		put(level, box, 4, g + 1, 0, facing(Blocks.STONECUTTER, Direction.WEST));
		put(level, box, 4, g + 2, -2, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState()); // a gear
		put(level, box, 4, g + 2, -1, Blocks.IRON_BARS.defaultBlockState()); // the frame's torn-off arm
		put(level, box, 4, g + 2, -3, Blocks.CANDLE.defaultBlockState());
		// west bench: crafting table, a vice (anvil), the cartography table with blueprints
		put(level, box, -4, g + 1, 0, Blocks.CRAFTING_TABLE.defaultBlockState());
		put(level, box, -4, g + 1, 1, Blocks.CHIPPED_ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.NORTH));
		put(level, box, -4, g + 1, 2, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		put(level, box, -4, g + 1, -1, facing(Blocks.GRINDSTONE, Direction.EAST));
		put(level, box, -3, g + 1, 1, stair(stairs(), Direction.WEST, false)); // a stool
		// the broken suit frame: an iron half-suit on a stand, hanging chains, a gantry beam over it
		for (int dx = -1; dx <= 1; dx++) {
			put(level, box, dx, g + 4, -2, log().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		}
		put(level, box, -1, g + 3, -2, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
		put(level, box, 1, g + 3, -2, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
		put(level, box, 1, g + 2, -2, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
		put(level, box, 0, g, -2, Blocks.WAXED_CUT_COPPER.defaultBlockState());
		put(level, box, -1, g + 1, -3, Blocks.WAXED_COPPER_GRATE.defaultBlockState());
		put(level, box, 1, g + 1, -3, Blocks.WAXED_COPPER_GRATE.defaultBlockState());
		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		chest.setDamageValue(chest.getMaxDamage() * 3 / 4);
		ItemStack helm = new ItemStack(Items.IRON_HELMET);
		helm.setDamageValue(helm.getMaxDamage() * 2 / 3);
		armourStand(level, box, 0, g + 1, -2, Direction.SOUTH, helm, chest, null, new ItemStack(Items.CHAINMAIL_BOOTS));
		// the tesla coil in the north-west corner
		put(level, box, -3, g + 1, -3, Blocks.WAXED_COPPER_BLOCK.defaultBlockState());
		put(level, box, -3, g + 2, -3, Blocks.LIGHTNING_ROD.defaultBlockState());
		// the parts bin, the vault, crates
		vault(level, box, -4, g + 1, -3, Direction.SOUTH);
		lootContainer(level, box, 4, g + 1, 2, facing(Blocks.BARREL, Direction.WEST), PARTS);
		put(level, box, 4, g + 1, 3, Blocks.BARREL.defaultBlockState());
		put(level, box, 4, g + 2, 3, trapdoor().defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM));
		// dead lamps on chains, dust, cobwebs
		put(level, box, -2, g + 3, 1, Blocks.REDSTONE_LAMP.defaultBlockState());
		put(level, box, 2, g + 3, 1, Blocks.REDSTONE_LAMP.defaultBlockState());
		put(level, box, 2, g + 4, 1, Blocks.CHAIN.defaultBlockState());
		put(level, box, -2, g + 4, 1, Blocks.CHAIN.defaultBlockState());
		put(level, box, 0, g + 3, 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		put(level, box, 0, g + 4, 2, Blocks.CHAIN.defaultBlockState());
		for (int dx = -RX + 1; dx <= RX - 1; dx++) {
			for (int dz = -RZ + 1; dz <= RZ - 1; dz++) {
				double n = lnoise(dx, dz, 97);
				if (n < 0.22) {
					putSoft(level, box, dx, g + 1, dz, Blocks.LIGHT_GRAY_CARPET.defaultBlockState());
				} else if (n > 0.93) {
					putSoft(level, box, dx, g + 3, dz, Blocks.COBWEB.defaultBlockState());
				}
			}
		}
		put(level, box, -RX + 1, g + 3, -RZ + 1, Blocks.COBWEB.defaultBlockState());
		put(level, box, RX - 1, g + 3, -RZ + 1, Blocks.COBWEB.defaultBlockState());
		put(level, box, RX - 1, g + 3, RZ - 1, Blocks.COBWEB.defaultBlockState());
		// the vault's door opens onto air
		put(level, box, -4, g + 1, -2, Blocks.AIR.defaultBlockState());
	}
}
