package com.projecthero.mod.powerarmour.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.powerarmour.PowerArmourMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.5 Power Armour: the Core Charger -- a furnace for power cores. A core in the top slot, any furnace fuel (or redstone) in
 * the bottom one: while it burns, the core charges ({@link CoreChargerBlockEntity#CHARGE_PER_TICK} a tick).
 */
public class CoreChargerBlock extends BaseEntityBlock {
	public static final MapCodec<CoreChargerBlock> CODEC = simpleCodec(CoreChargerBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public CoreChargerBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CoreChargerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, PowerArmourMod.CHARGER_BE, CoreChargerBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CoreChargerBlockEntity be) {
			player.openMenu(be);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CoreChargerBlockEntity be) {
			Containers.dropContents(level, pos, be);
			level.updateNeighbourForOutputSignal(pos, this);
		}
		super.onRemove(state, level, pos, newState, moved);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
		if (random.nextDouble() < 0.08) {
			level.playLocalSound(x, y, z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3f, 1.9f, false);
		}
		Direction d = state.getValue(FACING);
		double off = random.nextDouble() * 0.5 - 0.25;
		double px = d.getAxis() == Direction.Axis.X ? d.getStepX() * 0.52 : off;
		double pz = d.getAxis() == Direction.Axis.Z ? d.getStepZ() * 0.52 : off;
		level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + px, y + 0.3 + random.nextDouble() * 0.4, z + pz, 0, 0.02, 0);
		level.addParticle(ParticleTypes.SMOKE, x, y + 1.05, z, 0, 0.02, 0);
	}
}
