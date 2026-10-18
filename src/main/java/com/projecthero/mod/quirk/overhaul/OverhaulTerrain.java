package com.projecthero.mod.quirk.overhaul;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.power.p05.GeoTerrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.phys.AABB;

/**
 * v0.16.7: every block Overhaul reshapes -- spikes, walls, ramps, the pit and its lid, the block Shard Shot bursts. All
 * of it goes through the Geokinesis ledger ({@link GeoTerrain}): the same build rules (terrain config, build rights,
 * spawn protection / claims, world border), placed only into air or a replaceable plant, never a block entity, bedrock
 * or anything unbreakable, and always put back exactly on its own clock (and on server stop). On top of that a block is
 * never put into a cell a living thing is standing in.
 *
 * <p>A spike is a short column of the ground's own stone with a pointed-dripstone tip; base and tip share one expiry so
 * the ledger takes the tip first (top-down) and nothing ever drops.
 */
public final class OverhaulTerrain {
	private OverhaulTerrain() {
	}

	/** What the reshaped ground is made of: its own stone (fixed-state blocks only, so the ledger always matches). */
	public static BlockState material(BlockState ground) {
		if (ground.is(Blocks.DEEPSLATE) || ground.is(Blocks.COBBLED_DEEPSLATE) || ground.is(Blocks.TUFF)) {
			return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
		}
		if (ground.is(BlockTags.DIRT) || ground.is(Blocks.MUD) || ground.is(Blocks.CLAY) || ground.is(Blocks.FARMLAND) || ground.is(Blocks.DIRT_PATH)) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (ground.is(Blocks.RED_SAND) || ground.is(Blocks.RED_SANDSTONE)) {
			return Blocks.RED_SANDSTONE.defaultBlockState();
		}
		if (ground.is(BlockTags.SAND) || ground.is(Blocks.SANDSTONE)) {
			return Blocks.SANDSTONE.defaultBlockState();
		}
		if (ground.is(Blocks.NETHERRACK) || ground.is(BlockTags.NYLIUM)) {
			return Blocks.NETHERRACK.defaultBlockState();
		}
		if (ground.is(Blocks.END_STONE)) {
			return Blocks.END_STONE.defaultBlockState();
		}
		if (ground.is(Blocks.ANDESITE) || ground.is(Blocks.DIORITE) || ground.is(Blocks.GRANITE) || ground.is(Blocks.CALCITE)) {
			return ground.getBlock().defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	private static BlockState tip() {
		return Blocks.POINTED_DRIPSTONE.defaultBlockState()
				.setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
				.setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP);
	}

	/** A cell something stands on: air / a plant over a full solid top. */
	private static boolean standable(ServerLevel level, BlockPos cell) {
		BlockState here = level.getBlockState(cell);
		if (!(here.isAir() || here.canBeReplaced()) || !here.getFluidState().isEmpty()) {
			return false;
		}
		BlockPos below = cell.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	/** The open cell on the ground at (x, z) within a few blocks of {@code y} (the one nearest to {@code y}), or null. */
	public static @Nullable BlockPos groundCell(ServerLevel level, int x, int y, int z) {
		for (int i = 0; i <= 4; i++) {
			BlockPos up = new BlockPos(x, y + i, z);
			if (standable(level, up)) {
				return up;
			}
			if (i > 0) {
				BlockPos down = new BlockPos(x, y - i, z);
				if (standable(level, down)) {
					return down;
				}
			}
		}
		return null;
	}

	/** Somebody is standing in that cell (never build into a living thing). */
	public static boolean occupied(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> e.isAlive() && !e.isSpectator()).isEmpty();
	}

	/** One temporary block (see the class rules). */
	public static boolean place(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, int ttl) {
		if (occupied(level, pos)) {
			return false;
		}
		return GeoTerrain.placeTemp(p, level, pos, state, ttl);
	}

	/**
	 * A spike erupting from the ground cell {@code cell}: {@code height - 1} blocks of the ground's stone and a pointed
	 * tip. Particles and sound always (even where no block may go). Returns the number of blocks placed.
	 */
	public static int spike(ServerPlayer p, ServerLevel level, BlockPos cell, int height, int ttl) {
		BlockState ground = level.getBlockState(cell.below());
		BlockState mat = material(ground);
		burstAt(level, cell, ground.isAir() ? mat : ground, 10);
		level.playSound(null, cell, SoundEvents.POINTED_DRIPSTONE_PLACE, SoundSource.PLAYERS, 0.8f, 0.7f + level.random.nextFloat() * 0.3f);
		level.playSound(null, cell, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.7f, 0.8f);
		int placed = 0;
		BlockPos at = cell;
		for (int i = 0; i < height - 1; i++) {
			if (!place(p, level, at, mat, ttl)) {
				return placed;
			}
			placed++;
			at = at.above();
		}
		BlockPos under = at.below();
		if (level.getBlockState(under).isFaceSturdy(level, under, Direction.UP) && place(p, level, at, tip(), ttl)) {
			placed++;
		}
		return placed;
	}

	/** The ground breaking apart into cubes at {@code pos}. */
	public static void burstAt(ServerLevel level, BlockPos pos, BlockState state, int count) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
				count, 0.35, 0.3, 0.35, 0.15);
	}
}
