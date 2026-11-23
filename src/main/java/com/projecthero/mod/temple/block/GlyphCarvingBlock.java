package com.projecthero.mod.temple.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * v0.17.7: a carved <b>glyph</b> block (Sun, Eye, Scarab or Ankh, as {@link GlyphPlateBlock#GLYPH}) -- the Hall of Trials'
 * order mural, and decoration all over the temple.
 */
public class GlyphCarvingBlock extends Block {
	public static final MapCodec<GlyphCarvingBlock> CODEC = simpleCodec(GlyphCarvingBlock::new);

	public GlyphCarvingBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(GlyphPlateBlock.GLYPH, 0));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(GlyphPlateBlock.GLYPH);
	}
}
