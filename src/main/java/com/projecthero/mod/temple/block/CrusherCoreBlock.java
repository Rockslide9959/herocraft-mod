package com.projecthero.mod.temple.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: the <b>Crushing Corridor</b>'s trigger stone -- the cracked tile in the middle of the corridor's first row
 * (unbreakable). It remembers the corridor's shape and runs the walls ({@link CrusherCoreBlockEntity}).
 */
public class CrusherCoreBlock extends BaseEntityBlock {
	public static final MapCodec<CrusherCoreBlock> CODEC = simpleCodec(CrusherCoreBlock::new);

	public CrusherCoreBlock(BlockBehaviour.Properties props) {
		super(props);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CrusherCoreBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, TempleBlocks.CRUSHER_CORE_BE, CrusherCoreBlockEntity::serverTick);
	}
}
