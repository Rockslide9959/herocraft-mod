package com.projecthero.mod.skyfortress.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.skyfortress.SkyTurbines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.7: a Sky Fortress engine <b>Turbine</b> core. Unbreakable; hold right-click on a running one for
 * {@link SkyTurbines#HOLD_TICKS} to sabotage it ({@link #RUNNING} false for good). When the last turbine in the
 * fortress dies the bridge shield drops and the whole ship lurches ({@link SkyTurbines#sabotage}).
 */
public class SkyTurbineBlock extends Block {
	public static final MapCodec<SkyTurbineBlock> CODEC = simpleCodec(SkyTurbineBlock::new);
	public static final BooleanProperty RUNNING = BooleanProperty.create("running");

	public SkyTurbineBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(RUNNING, true));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(RUNNING);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			SkyTurbines.use(server, pos, state, sp);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		if (state.getValue(RUNNING)) {
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (random.nextDouble() - 0.5), y + (random.nextDouble() - 0.5),
						z + (random.nextDouble() - 0.5), 0, 0, 0);
			}
		} else {
			if (random.nextInt(2) == 0) {
				level.addParticle(ParticleTypes.LARGE_SMOKE, x + (random.nextDouble() - 0.5) * 0.6, y + 0.6, z + (random.nextDouble() - 0.5) * 0.6,
						0, 0.05, 0);
			}
			if (random.nextInt(10) == 0) {
				level.addParticle(ParticleTypes.LAVA, x, y + 0.5, z, 0, 0, 0);
			}
		}
	}
}
