package com.projecthero.mod.ironman.furnace;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** v0.14.26: a Stark Furnace / Smelter / Smoker block (see {@link StarkFurnaces}). Vanilla furnace behaviour throughout. */
public class StarkFurnaceBlock extends AbstractFurnaceBlock {
	private final StarkFurnaces.Kind kind;
	private final MapCodec<StarkFurnaceBlock> codec;

	public StarkFurnaceBlock(StarkFurnaces.Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
		this.codec = simpleCodec(p -> new StarkFurnaceBlock(kind, p));
	}

	public StarkFurnaces.Kind kind() {
		return kind;
	}

	@Override
	protected MapCodec<? extends AbstractFurnaceBlock> codec() {
		return codec;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StarkFurnaceBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createFurnaceTicker(level, type, StarkFurnaces.BLOCK_ENTITY);
	}

	@Override
	protected void openContainer(Level level, BlockPos pos, Player player) {
		if (level.getBlockEntity(pos) instanceof StarkFurnaceBlockEntity be) {
			player.openMenu(be);
		}
	}

	/** Lit: the vanilla flicker and crackle plus a few arc-reactor sparks, and a hum. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
		if (random.nextDouble() < 0.1) {
			level.playLocalSound(x, y, z, kind == StarkFurnaces.Kind.SMOKER ? SoundEvents.SMOKER_SMOKE : SoundEvents.FURNACE_FIRE_CRACKLE,
					SoundSource.BLOCKS, 1.0f, 1.2f, false);
		}
		if (random.nextDouble() < 0.06) {
			level.playLocalSound(x, y, z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.25f, 1.8f, false);
		}
		Direction d = state.getValue(FACING);
		double off = random.nextDouble() * 0.6 - 0.3;
		double px = d.getAxis() == Direction.Axis.X ? d.getStepX() * 0.52 : off;
		double pz = d.getAxis() == Direction.Axis.Z ? d.getStepZ() * 0.52 : off;
		level.addParticle(ParticleTypes.SMOKE, x + px, y + random.nextDouble() * 6.0 / 16.0, z + pz, 0, 0, 0);
		level.addParticle(kind == StarkFurnaces.Kind.SMELTER ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.FLAME,
				x + px, y + random.nextDouble() * 6.0 / 16.0, z + pz, 0, 0, 0);
		if (random.nextDouble() < 0.3) {
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + px, y + 0.75, z + pz, 0, 0.02, 0);
		}
	}
}
