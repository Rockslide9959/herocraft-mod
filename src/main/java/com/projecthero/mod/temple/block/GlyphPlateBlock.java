package com.projecthero.mod.temple.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.7: a <b>Glyph Plate</b> of the Hall of Trials -- a gold-inlaid floor plate showing one of the four temple glyphs
 * ({@code glyph}: 0 Sun, 1 Eye, 2 Scarab, 3 Ankh). Stepping on it tells the hall's Seal of Glyphs
 * ({@link TempleSealBlockEntity#stepGlyph}); a right step lights the plate, a wrong one puts every plate out and starts the
 * order again. The order is carved on the mural (glyph carvings, with one to four candles under each). Unbreakable.
 */
public class GlyphPlateBlock extends Block {
	public static final MapCodec<GlyphPlateBlock> CODEC = simpleCodec(GlyphPlateBlock::new);
	public static final IntegerProperty GLYPH = IntegerProperty.create("glyph", 0, 3);
	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");
	public static final String[] NAMES = { "sun", "eye", "scarab", "ankh" };
	/** How far from a plate its seal is looked for. */
	public static final int SEAL_SEARCH = 20;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

	public GlyphPlateBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(GLYPH, 0).setValue(LIT, false).setValue(PRESSED, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(GLYPH, LIT, PRESSED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (level instanceof ServerLevel server && !state.getValue(PRESSED) && entity instanceof ServerPlayer p && !p.isSpectator()) {
			press(server, pos, state);
		}
	}

	/** A player stepped onto the plate: tell the seal (once per visit). */
	public static void press(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(PRESSED, true), 3);
		level.scheduleTick(pos, state.getBlock(), 10);
		level.playSound(null, pos, SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.8f, 0.8f);
		TempleSealBlockEntity seal = TempleSealBlockEntity.find(level, pos, TempleSealBlock.Kind.GLYPH, SEAL_SEARCH);
		if (seal != null) {
			seal.stepGlyph(level, pos, state.getValue(GLYPH));
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(PRESSED)) {
			return;
		}
		if (!level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).expandTowards(0, 1, 0)).isEmpty()) {
			level.scheduleTick(pos, this, 10); // still standing on it
			return;
		}
		level.setBlock(pos, state.setValue(PRESSED, false), 3);
	}
}
