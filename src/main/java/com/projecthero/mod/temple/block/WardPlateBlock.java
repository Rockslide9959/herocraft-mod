package com.projecthero.mod.temple.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.alarm.AlarmDirector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.7: a <b>Ward Plate</b> -- a thin glyph tile in a temple floor that "wakes the guardians" when a survival player
 * treads on it ({@link AlarmDirector#raise}, {@link AlarmDirector.Cause#SENSOR}, wired to the nearest ward stone like any
 * sensor). <b>Sneaking</b> players step softly and never set it off; anyone else can just walk round it -- the glyph is
 * plain to see. Clicks down for a second, then can fire again.
 */
public class WardPlateBlock extends Block {
	public static final MapCodec<WardPlateBlock> CODEC = simpleCodec(WardPlateBlock::new);
	public static final BooleanProperty PRESSED = BlockStateProperties.POWERED;
	private static final VoxelShape UP = Block.box(1, 0, 1, 15, 1, 15);
	private static final VoxelShape DOWN = Block.box(1, 0, 1, 15, 0.5, 15);

	public WardPlateBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(PRESSED, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PRESSED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(PRESSED) ? DOWN : UP;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (!(level instanceof ServerLevel server) || state.getValue(PRESSED)) {
			return;
		}
		if (!(entity instanceof ServerPlayer p) || !p.gameMode.isSurvival() || p.isShiftKeyDown() || !p.onGround()) {
			return;
		}
		press(server, pos, state, p);
	}

	/** The plate clicks down and raises the alarm. Returns whether the zone went Alert. */
	public static boolean press(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer culprit) {
		level.setBlock(pos, state.setValue(PRESSED, true), 3);
		level.scheduleTick(pos, state.getBlock(), 20);
		level.playSound(null, pos, SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 1.0f, 0.5f);
		level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 20, 0.3, 0.2, 0.3, 0.6);
		return AlarmDirector.raise(level, pos, AlarmDirector.Cause.SENSOR, culprit);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(PRESSED)) {
			level.setBlock(pos, state.setValue(PRESSED, false), 3);
			level.playSound(null, pos, SoundEvents.STONE_PRESSURE_PLATE_CLICK_OFF, SoundSource.BLOCKS, 0.8f, 0.5f);
		}
	}
}
