package com.projecthero.mod.alarm.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.alarm.AlarmSabotage;
import com.projecthero.mod.alarm.AlarmZones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.joml.Vector3f;

/**
 * v0.17.7: an <b>Alarm Panel</b> -- the console a zone's Alarm Runner sprints to. A runner reaching it raises the alarm.
 * While the zone is Calm a player can <b>sabotage</b> it: hold right-click on it for 3 s ({@link AlarmSabotage}); it
 * sparks and goes dead ({@link #SABOTAGED}, for good), and every sensor wired to it (the sensors nearer to it than to any
 * other panel) shuts off. {@link #ALERT} only drives the look (red screen while the zone is Alert). Unbreakable.
 */
public class AlarmPanelBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<AlarmPanelBlock> CODEC = simpleCodec(AlarmPanelBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final EnumProperty<AlarmStyle> STYLE = AlarmBlocks.STYLE;
	public static final BooleanProperty SABOTAGED = BooleanProperty.create("sabotaged");
	public static final BooleanProperty ALERT = BooleanProperty.create("alert");

	public AlarmPanelBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STYLE, AlarmStyle.TECH)
				.setValue(SABOTAGED, false).setValue(ALERT, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, STYLE, SABOTAGED, ALERT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			AlarmSabotage.use(server, pos, state, sp);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
		super.onPlace(state, level, pos, old, moving);
		if (level instanceof ServerLevel server && !old.is(this)) {
			AlarmZones.noteBlock(server, pos, state);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		Direction f = state.getValue(FACING);
		double x = pos.getX() + 0.5 + f.getStepX() * 0.52;
		double z = pos.getZ() + 0.5 + f.getStepZ() * 0.52;
		double y = pos.getY() + 0.6;
		if (state.getValue(SABOTAGED)) {
			if (random.nextInt(5) == 0) {
				level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.02, 0);
			}
			if (random.nextInt(12) == 0) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (random.nextDouble() - 0.5) * 0.4, y, z + (random.nextDouble() - 0.5) * 0.4,
						f.getStepX() * 0.1, 0.05, f.getStepZ() * 0.1);
			}
		} else if (state.getValue(ALERT) && random.nextInt(2) == 0) {
			level.addParticle(new DustParticleOptions(new Vector3f(1f, 0.15f, 0.1f), 0.8f), x, y + 0.2, z, 0, 0, 0);
		}
	}
}
