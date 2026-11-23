package com.projecthero.mod.alarm.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.alarm.AlarmZones;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.7: an invisible <b>reinforcement spawn point</b> -- builders put one at the feet position where alarm wave mobs
 * should appear (a side room, a hatch, a landing pad). No shape, no collision, draws nothing, replaceable. A zone
 * without any spawns its waves round its live panels, then its lamps, then 8-14 blocks from the intruders.
 */
public class AlarmSpawnPointBlock extends Block {
	public static final MapCodec<AlarmSpawnPointBlock> CODEC = simpleCodec(AlarmSpawnPointBlock::new);

	public AlarmSpawnPointBlock(BlockBehaviour.Properties props) {
		super(props);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return Shapes.empty();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
		super.onPlace(state, level, pos, old, moving);
		if (level instanceof ServerLevel server && !old.is(this)) {
			AlarmZones.noteBlock(server, pos, state);
		}
	}
}
