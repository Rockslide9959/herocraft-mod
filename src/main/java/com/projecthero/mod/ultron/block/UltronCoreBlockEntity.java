package com.projecthero.mod.ultron.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** v0.15.12: the Ultron Core's block entity -- it holds nothing; it exists so the client can draw the turning eye. */
public class UltronCoreBlockEntity extends BlockEntity {
	public UltronCoreBlockEntity(BlockPos pos, BlockState state) {
		super(UltronBlocks.ULTRON_CORE_BE, pos, state);
	}
}
