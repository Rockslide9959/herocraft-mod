package com.projecthero.mod.omnitrix.pod;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.omnitrix.OmnitrixConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.1: the Omnitrix Containment Pod -- a two-block-tall machine (the lower half holds the block entity) you stand INSIDE:
 * a heavy base plate, four pillars and a capping ring around a green glass field. Only the base plate is solid, so a player
 * walks straight in.
 *
 * <ul>
 *   <li>Right-click with a Redstone Block (1 fuel) or an Echo Shard (2 fuel) to fuel it ({@link OmnitrixConfig#MAX_FUEL} max).</li>
 *   <li>Right-click it from inside with an empty hand, wearing the Omnitrix as a human, to start the 40-second extraction
 *       ({@link OmnitrixConfig#FUEL_PER_EXTRACTION} fuel). See {@link OmnitrixPodBlockEntity}.</li>
 * </ul>
 */
public class OmnitrixPodBlock extends BaseEntityBlock {
	public static final MapCodec<OmnitrixPodBlock> CODEC = simpleCodec(OmnitrixPodBlock::new);
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	private static final VoxelShape PLATE = Block.box(0, 0, 0, 16, 2, 16);
	private static final VoxelShape PILLARS_LOW = Shapes.or(Block.box(0, 2, 0, 2, 16, 2), Block.box(14, 2, 0, 16, 16, 2),
			Block.box(0, 2, 14, 2, 16, 16), Block.box(14, 2, 14, 16, 16, 16));
	private static final VoxelShape PILLARS_HIGH = Shapes.or(Block.box(0, 0, 0, 2, 14, 2), Block.box(14, 0, 0, 16, 14, 2),
			Block.box(0, 0, 14, 2, 14, 16), Block.box(14, 0, 14, 16, 14, 16));
	private static final VoxelShape CAP = Block.box(0, 14, 0, 16, 16, 16);
	private static final VoxelShape LOWER_OUTLINE = Shapes.or(PLATE, PILLARS_LOW);
	private static final VoxelShape UPPER_OUTLINE = Shapes.or(PILLARS_HIGH, CAP);

	public OmnitrixPodBlock(Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HALF, FACING);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_OUTLINE : UPPER_OUTLINE;
	}

	/** Only the base plate is solid: the wearer steps in between the pillars. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? PLATE : Shapes.empty();
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		BlockPos pos = ctx.getClickedPos();
		Level level = ctx.getLevel();
		if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(ctx)) {
			return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(HALF, DoubleBlockHalf.LOWER);
		}
		return null;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockState below = level.getBlockState(pos.below());
			return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
		}
		return true;
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		DoubleBlockHalf half = state.getValue(HALF);
		if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
			return neighbour.is(this) && neighbour.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, dir, neighbour, level, pos, neighbourPos);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockPos below = pos.below();
			if (level.getBlockState(below).is(this)) {
				level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
				level.levelEvent(player, 2001, below, Block.getId(level.getBlockState(below)));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** The lower half's position for either half. */
	public static BlockPos base(BlockState state, BlockPos pos) {
		return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		int fuel = stack.is(Items.REDSTONE_BLOCK) ? OmnitrixConfig.FUEL_REDSTONE_BLOCK : stack.is(Items.ECHO_SHARD) ? OmnitrixConfig.FUEL_ECHO_SHARD : 0;
		if (fuel == 0) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level.isClientSide()) {
			return ItemInteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(base(state, pos)) instanceof OmnitrixPodBlockEntity be) {
			if (be.fuel() + fuel > OmnitrixConfig.MAX_FUEL) {
				player.displayClientMessage(Component.translatable("message.projecthero.omnitrix_pod.full", be.fuel(), OmnitrixConfig.MAX_FUEL)
						.withStyle(ChatFormatting.GRAY), true);
				return ItemInteractionResult.CONSUME;
			}
			be.addFuel(fuel);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.8f, 1.4f);
			player.displayClientMessage(Component.translatable("message.projecthero.omnitrix_pod.fuel", be.fuel(), OmnitrixConfig.FUEL_PER_EXTRACTION)
					.withStyle(ChatFormatting.GREEN), true);
		}
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer sp && level.getBlockEntity(base(state, pos)) instanceof OmnitrixPodBlockEntity be) {
			be.tryStart(sp);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	@Nullable
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new OmnitrixPodBlockEntity(pos, state) : null;
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
			return null;
		}
		return createTickerHelper(type, OmnitrixPodBlocks.POD_BE, (l, p, s, be) -> be.serverTick());
	}
}
