package com.projecthero.mod.vaultdungeon.block;

import org.joml.Vector3f;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.vaultdungeon.VaultGuards;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * v0.17.5: a <b>laser tripwire</b> emitter. A tripwire hook hangs on its {@link #FACING} side and a red laser (client
 * dust, drawn along the wire) crosses the corridor. The first time anything powers it -- the wire snaps -- it fires once
 * ({@link #SPENT}): sirens, and a squad of riot wardens arrives ({@link VaultGuards#alarm}). Break it first and the
 * trap is disarmed.
 */
public class VaultAlarmBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<VaultAlarmBlock> CODEC = simpleCodec(VaultAlarmBlock::new);
	public static final BooleanProperty SPENT = BooleanProperty.create("spent");
	/** How far the laser is drawn (blocks). */
	public static final int LASER_REACH = 10;
	private static final DustParticleOptions LASER = new DustParticleOptions(new Vector3f(1.0f, 0.08f, 0.05f), 0.55f);

	public VaultAlarmBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST).setValue(SPENT, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SPENT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos, boolean moving) {
		super.neighborChanged(state, level, pos, neighbor, fromPos, moving);
		// only a player nearby can set it off (a guard wandering into the wire with nobody around changes nothing)
		if (level instanceof ServerLevel server && !state.getValue(SPENT) && level.hasNeighborSignal(pos)
				&& !com.projecthero.mod.vaultdungeon.VaultClearing.playersNear(server, pos, 16).stream().noneMatch(p -> p.gameMode.isSurvival())) {
			level.setBlock(pos, state.setValue(SPENT, true), 3);
			VaultGuards.alarm(server, pos);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(SPENT)) {
			return;
		}
		Direction d = state.getValue(FACING);
		int len = 0;
		for (int i = 1; i <= LASER_REACH; i++) {
			BlockState s = level.getBlockState(pos.relative(d, i));
			if (!(s.isAir() || s.is(Blocks.TRIPWIRE) || s.is(Blocks.TRIPWIRE_HOOK))) {
				break;
			}
			len = i;
		}
		double y = pos.getY() + 0.18;
		for (int k = 0; k < 6; k++) {
			double t = 0.5 + random.nextDouble() * (len + 0.5 - 0.5);
			level.addParticle(LASER, pos.getX() + 0.5 + d.getStepX() * t, y, pos.getZ() + 0.5 + d.getStepZ() * t, 0, 0, 0);
		}
	}
}
