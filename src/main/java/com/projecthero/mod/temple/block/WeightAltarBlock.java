package com.projecthero.mod.temple.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.temple.item.TempleWeightItem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.7: the <b>Weight Altar</b> -- the Hall of Trials' bronze scale. Right-click it with a temple weight
 * ({@link TempleWeightItem}: Feather 1, Ibis 2, Jackal 3, Lion 4 -- they lie in the hall's urns) to lay it on the pan;
 * the scale balances at exactly {@link WeightAltarBlockEntity#TARGET} and the Seal of Weight cracks. Too heavy and it
 * tips, throwing every weight back. Sneak + empty hand takes the weights back. {@code load} shows how full the pan is.
 */
public class WeightAltarBlock extends BaseEntityBlock {
	public static final MapCodec<WeightAltarBlock> CODEC = simpleCodec(WeightAltarBlock::new);
	public static final BooleanProperty SOLVED = BooleanProperty.create("solved");
	public static final IntegerProperty LOAD = IntegerProperty.create("load", 0, 2);
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

	public WeightAltarBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(SOLVED, false).setValue(LOAD, 0));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SOLVED, LOAD);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WeightAltarBlockEntity(pos, state);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (!(stack.getItem() instanceof TempleWeightItem w)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof WeightAltarBlockEntity be) {
			if (be.lay(server, w.weight(), sp)) {
				stack.consume(1, player);
			}
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof WeightAltarBlockEntity be) {
			if (player.isShiftKeyDown()) {
				be.takeBack(server, sp);
			} else {
				be.explain(sp);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
