package com.projecthero.mod.ultron.contagion.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * v0.16.1: Corrupted Circuitry ground. {@link #EDGE} marks the rim of the corruption -- where it meets normal terrain the
 * metal is darker and broken up with soot, the veins thinner ({@code corrupted_circuitry_edge_*} textures). The spread sets
 * it on each newly corrupted column and clears it on the column inside it, so the rim follows the growing radius. Four
 * texture variants at random rotations keep the ground from reading as a grid.
 */
public class CorruptedGroundBlock extends Block {
	public static final BooleanProperty EDGE = BooleanProperty.create("edge");

	public CorruptedGroundBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(EDGE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(EDGE);
	}
}
