package com.projecthero.mod.hideout.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.hideout.HideoutClearing;
import com.projecthero.mod.hideout.HideoutFaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.joml.Vector3f;

/**
 * v0.17.5: a hideout's <b>core</b> -- the faction's power source in the boss room. While its guardian (the mini-boss)
 * lives it is {@link #SEALED}: nothing can break it ({@link #getDestroyProgress} is 0, explosions and pistons can't
 * touch it). When the mini-boss dies the seal drops; breaking the core then clears the hideout
 * ({@link HideoutClearing#coreBroken}): the Hideout Vault unseals and the faction's raids calm nearby.
 */
public class HideoutCoreBlock extends Block {
	public static final MapCodec<HideoutCoreBlock> CODEC = simpleCodec(HideoutCoreBlock::new);
	public static final EnumProperty<HideoutFaction> FACTION = HideoutBlocks.FACTION;
	public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

	public HideoutCoreBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACTION, HideoutFaction.KINGPIN).setValue(SEALED, true));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACTION, SEALED);
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (state.getValue(SEALED)) {
			return 0.0f;
		}
		return super.getDestroyProgress(state, player, level, pos);
	}

	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (level instanceof ServerLevel server && state.getValue(SEALED)) {
			HideoutClearing.pokeSealed(server, pos, state, player);
		}
		super.attack(state, level, pos, player);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			if (state.getValue(SEALED)) {
				HideoutClearing.pokeSealed(server, pos, state, player);
			} else {
				player.displayClientMessage(HideoutClearing.coreOpenHint(state.getValue(FACTION)), true);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!newState.is(this) && level instanceof ServerLevel server && !state.getValue(SEALED)) {
			HideoutClearing.coreBroken(server, pos, state.getValue(FACTION));
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int rgb = state.getValue(FACTION).colour();
		Vector3f c = new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
		DustParticleOptions dust = new DustParticleOptions(c, state.getValue(SEALED) ? 1.0f : 1.6f);
		for (int i = 0; i < (state.getValue(SEALED) ? 2 : 4); i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = 0.7 + random.nextDouble() * 0.3;
			level.addParticle(dust, pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + random.nextDouble() * 1.2, pos.getZ() + 0.5 + Math.sin(a) * r,
					0, 0.02, 0);
		}
		if (!state.getValue(SEALED) && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0.1, 0);
		}
	}
}
