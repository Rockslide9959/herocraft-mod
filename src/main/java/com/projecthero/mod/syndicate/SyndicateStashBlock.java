package com.projecthero.mod.syndicate;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.14.25: the Syndicate's stash -- a locked crate of contraband in the Kingpin's office. It is the bust's trigger and
 * its prize: walk into the warehouse (or open the crate) and the {@link SyndicateBust} starts round it; win, and the
 * crate becomes the loot chest. {@link #FACING} is the warehouse's {@code forward}, so the bust can find every spawn
 * point from the crate alone. While a bust runs it is {@link #ACTIVE} and can't be mined.
 */
public class SyndicateStashBlock extends BaseEntityBlock {
	public static final MapCodec<SyndicateStashBlock> CODEC = simpleCodec(SyndicateStashBlock::new);
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public SyndicateStashBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ACTIVE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		// placed by hand (creative), its warehouse is "behind" it from where the player stands
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SyndicateStashBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, SyndicateItems.STASH_BLOCK_ENTITY, SyndicateStashBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		if (state.getValue(ACTIVE)) {
			player.displayClientMessage(Component.translatable("event.projecthero.syndicate.locked").withStyle(ChatFormatting.RED), true);
			return InteractionResult.CONSUME;
		}
		if (player.isCreative() || player.isSpectator()) {
			player.displayClientMessage(Component.translatable("event.projecthero.syndicate.survival_only").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME;
		}
		if (!SyndicateBust.start(server, pos)) {
			player.displayClientMessage(Component.translatable("event.projecthero.syndicate.refused").withStyle(ChatFormatting.RED), true);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return state.getValue(ACTIVE) ? 0f : super.getDestroyProgress(state, player, level, pos);
	}
}
