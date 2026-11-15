package com.projecthero.mod.vaultdungeon.block;

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
 * v0.17.5: a <b>security turret</b> bolted into the Security Floor's ceiling. It watches for a player within
 * {@link VaultTurretBlockEntity#RANGE} blocks in plain sight and fires three-dart bursts of stun darts (tipped arrows:
 * a little damage and Slowness). Mine it (pickaxe) to switch it off for good. Logic: {@link VaultTurretBlockEntity}.
 */
public class VaultTurretBlock extends BaseEntityBlock {
	public static final MapCodec<VaultTurretBlock> CODEC = simpleCodec(VaultTurretBlock::new);

	public VaultTurretBlock(BlockBehaviour.Properties props) {
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
		return new VaultTurretBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, VaultBlocks.TURRET_BE, VaultTurretBlockEntity::serverTick);
	}
}
