package com.projecthero.mod.grave;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.14.4: a boss trophy head standing on the floor -- the Grave Champion Head or the Empowered Zombie Head. Placed
 * like a vanilla mob head (it turns to face you, 4 ways), and {@link TrophyHeadWallBlock} is its wall-mounted twin;
 * the item ({@link com.projecthero.mod.grave.item.BossTrophyItem}) picks between them from the face you click.
 *
 * <p>Unlike a vanilla skull this is an ordinary block model (no skull renderer), so the worn look comes from the
 * item model's {@code head} transform -- see {@code scratchpad/gen_grave_heads_v0144.js}. The block entity keeps the
 * power and the kill record, so nothing is lost by putting a trophy on display; right-click it to read the plaque.
 */
public class TrophyHeadBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<TrophyHeadBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			TrophyHeads.Kind.CODEC.fieldOf("kind").forGetter(TrophyHeadBlock::kind),
			propertiesCodec()).apply(i, TrophyHeadBlock::new));

	/** Same footprint as a vanilla floor skull. */
	private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 8.0, 12.0);

	private final TrophyHeads.Kind kind;

	public TrophyHeadBlock(TrophyHeads.Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public TrophyHeads.Kind kind() {
		return kind;
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
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
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TrophyHeadBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return readPlaque(kind, level, pos, player);
	}

	/** Right-click with an empty hand: the plaque -- which head, which power, who took it and when -- in the action bar. */
	static InteractionResult readPlaque(TrophyHeads.Kind kind, Level level, BlockPos pos, Player player) {
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof TrophyHeadBlockEntity head) {
			player.displayClientMessage(TrophyHeads.plaque(kind, head.powerKey(), head.record()), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
