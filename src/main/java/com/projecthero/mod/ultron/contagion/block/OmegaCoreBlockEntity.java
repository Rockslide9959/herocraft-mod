package com.projecthero.mod.ultron.contagion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** v0.16.1: the Omega Core's block entity -- it holds nothing; it lets the client draw the dimming eyes. */
public class OmegaCoreBlockEntity extends BlockEntity {
	public OmegaCoreBlockEntity(BlockPos pos, BlockState state) {
		super(ContagionBlocks.OMEGA_CORE_BE, pos, state);
	}
}
