package com.projecthero.mod.temple.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.7: a <b>Crumbling Floor</b> tile -- cracked temple flagstone over a pit. A survival player standing on it makes it
 * shake ({@code stage} 1: dust, grinding) and {@link #SHAKE_TICKS} ticks later it falls away ({@code stage} 2: no shape,
 * not drawn). {@link #REFORM_TICKS} later it settles back (only when nobody stands in its space), so the room can be
 * tried again. Unbreakable. Keep moving, or take the solid ledge.
 */
public class CrumblingFloorBlock extends Block {
	public static final MapCodec<CrumblingFloorBlock> CODEC = simpleCodec(CrumblingFloorBlock::new);
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
	public static final int SHAKE_TICKS = 14;
	public static final int REFORM_TICKS = 400;

	public CrumblingFloorBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STAGE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(STAGE) == 2 ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return state.getValue(STAGE) == 2 ? RenderShape.INVISIBLE : RenderShape.MODEL;
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (level instanceof ServerLevel server && state.getValue(STAGE) == 0 && entity instanceof Player p && !p.isCreative() && !p.isSpectator()) {
			shake(server, pos, state);
		}
		super.stepOn(level, pos, state, entity);
	}

	/** Starts the tile shaking (it falls {@link #SHAKE_TICKS} later). */
	public static void shake(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(STAGE, 1), 3);
		level.scheduleTick(pos, state.getBlock(), SHAKE_TICKS);
		level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SANDSTONE.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 1.0,
				pos.getZ() + 0.5, 12, 0.35, 0.05, 0.35, 0.05);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int stage = state.getValue(STAGE);
		if (stage == 1) {
			level.setBlock(pos, state.setValue(STAGE, 2), 3);
			level.scheduleTick(pos, this, REFORM_TICKS);
			level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2f, 0.5f);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SANDSTONE.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5,
					pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.1);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() - 0.5, pos.getZ() + 0.5, 2, 0.2, 0.3, 0.2, 0.01);
		} else if (stage == 2) {
			if (!level.getEntitiesOfClass(Entity.class, new AABB(pos)).isEmpty()) {
				level.scheduleTick(pos, this, 40);
				return;
			}
			level.setBlock(pos, state.setValue(STAGE, 0), 3);
			level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.6f, 0.6f);
		}
	}
}
