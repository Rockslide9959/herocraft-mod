package com.projecthero.mod.temple.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.7: a <b>Canopic Jar</b> -- one of the four alabaster jars round Ankhatep's throne that anchor his curse
 * ({@code head}: 0 jackal, 1 falcon, 2 baboon, 3 human lid). Fragile: a hit or two by hand, or any projectile, shatters
 * it in a burst of green soul-light. While any jar stands in his arena his Curse of the Bound King keeps draining
 * whoever fights him (see {@code AnkhatepBoss}). Drops nothing.
 */
public class CanopicJarBlock extends Block {
	public static final MapCodec<CanopicJarBlock> CODEC = simpleCodec(CanopicJarBlock::new);
	public static final IntegerProperty HEAD = IntegerProperty.create("head", 0, 3);
	private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 13, 12);

	public CanopicJarBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(HEAD, 0));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HEAD);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server) {
			shatterFx(server, pos);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
		if (level instanceof ServerLevel server) {
			shatter(server, hit.getBlockPos());
		}
	}

	/** Shatters the jar at {@code pos} (no drop). */
	public static void shatter(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos).is(TempleBlocks.CANOPIC_JAR)) {
			shatterFx(level, pos);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
	}

	private static void shatterFx(ServerLevel level, BlockPos pos) {
		double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
		level.playSound(null, pos, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 1.6f, 0.8f);
		level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 2.0f, 0.6f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), x, y, z, 20, 0.2, 0.3, 0.2, 0.1);
		level.sendParticles(ParticleTypes.SOUL, x, y + 0.3, z, 14, 0.2, 0.5, 0.2, 0.06);
		level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.5, z, 6, 0.2, 0.4, 0.2, 0.04);
	}
}
