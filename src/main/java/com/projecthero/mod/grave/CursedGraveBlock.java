package com.projecthero.mod.grave;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Cursed Grave: the focal point at the heart of a Graveyard, and the Graveyard's entire
 * interaction surface.
 *
 * <h2>Why activation is a right-click on one block</h2>
 * The design says entering or activating the Graveyard must apply the curse <em>once</em>, and warns
 * against re-cursing players every tick while they stand inside the structure. Proximity detection
 * would need either a per-tick check against the player's position or a structure lookup, and would
 * then need its own "have I already done this here" bookkeeping to avoid firing repeatedly.
 *
 * <p>Making one block the trigger removes the whole class of problem: there is no tick, no scan and no
 * structure query anywhere in the Graveyard's implementation -- the only cost is a right-click a player
 * deliberately performed. And it needs no per-structure "already triggered" record either, because
 * {@link GraveboundCurse#apply} is already a no-op on an already-cursed player; the block just reports
 * that back. Coming back to the same grave, or finding a second Graveyard, is therefore harmless by
 * construction.
 *
 * <p>The block sets its own {@link BlockStateProperties#LIT} once used so the grave visibly reads as
 * spent, which is feedback rather than a gate -- a player who breaks the curse can use it again.
 */
public class CursedGraveBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
	public static final MapCodec<CursedGraveBlock> CODEC = simpleCodec(CursedGraveBlock::new);
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);

	public CursedGraveBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, net.minecraft.core.Direction.NORTH)
				.setValue(LIT, false)
				.setValue(WATERLOGGED, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
		boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType()
				== net.minecraft.world.level.material.Fluids.WATER;
		return defaultBlockState()
				.setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(WATERLOGGED, water);
	}

	@Override
	protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED)
				? net.minecraft.world.level.material.Fluids.WATER.getSource(false)
				: super.getFluidState(state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.PASS;
		}

		if (!GraveboundCurse.apply(serverPlayer, CurseSource.GRAVEYARD)) {
			// Already cursed -- the timer is untouched, and the player is told so rather than being
			// left wondering whether they just reset it.
			serverPlayer.displayClientMessage(Component.translatable("message.projecthero.curse.already")
					.withStyle(ChatFormatting.DARK_GRAY), true);
			return InteractionResult.CONSUME;
		}

		level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
		serverLevel.playSound(null, pos, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 1.0f, 0.6f);
		serverLevel.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
				40, 0.5, 0.5, 0.5, 0.05);
		return InteractionResult.CONSUME;
	}

	/** A slow drift of soul particles so the grave reads as the important thing in the structure. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(6) != 0) {
			return;
		}
		level.addParticle(state.getValue(LIT) ? ParticleTypes.SMOKE : ParticleTypes.SOUL_FIRE_FLAME,
				pos.getX() + 0.3 + random.nextDouble() * 0.4,
				pos.getY() + 0.9,
				pos.getZ() + 0.3 + random.nextDouble() * 0.4,
				0.0, 0.02, 0.0);
	}
}
