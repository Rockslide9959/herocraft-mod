package com.projecthero.mod.wardrobe;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.16.5 (user): the Wardrobe -- a wooden wardrobe three blocks wide and two tall that keeps up to ten full suits of
 * armour ({@link WardrobeBlockEntity}). The selected suit stands on a mannequin figure in the middle; the others hang on
 * waterfall rails in the left and right columns ({@code WardrobeRenderer}).
 * <ul>
 * <li>Right-click the left / right column: the selection moves one suit that way (wrapping round).</li>
 * <li>Shift+right-click the middle (v0.16.5: whatever you hold): your suit and the selected one change places -- a full swap.</li>
 * <li>Right-click the middle: like an armour stand for the selected suit -- put a piece on, or take the one you click.</li>
 * </ul>
 * Six blocks, placed facing you, broken as one (break any of them); the stored suits and the Wardrobe drop. Only the
 * middle bottom block carries the block entity and the loot.
 */
public class WardrobeBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<WardrobeBlock> CODEC = simpleCodec(WardrobeBlock::new);
	public static final EnumProperty<WardrobePart> PART = EnumProperty.create("part", WardrobePart.class);
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

	public WardrobeBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, WardrobePart.CENTER)
				.setValue(HALF, DoubleBlockHalf.LOWER));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART, HALF);
	}

	// ---------------- the six-block layout ----------------

	/** The viewer's left, standing in front of a Wardrobe that faces {@code facing}. */
	public static Direction leftOf(Direction facing) {
		return facing.getClockWise();
	}

	/** The middle bottom block (the one with the block entity) of the Wardrobe this block belongs to. */
	public static BlockPos origin(BlockState state, BlockPos pos) {
		Direction left = leftOf(state.getValue(FACING));
		BlockPos p = switch (state.getValue(PART)) {
			case LEFT -> pos.relative(left.getOpposite());
			case RIGHT -> pos.relative(left);
			case CENTER -> pos;
		};
		return state.getValue(HALF) == DoubleBlockHalf.UPPER ? p.below() : p;
	}

	public static BlockPos partPos(BlockPos origin, Direction facing, WardrobePart part, DoubleBlockHalf half) {
		Direction left = leftOf(facing);
		BlockPos p = switch (part) {
			case LEFT -> origin.relative(left);
			case RIGHT -> origin.relative(left.getOpposite());
			case CENTER -> origin;
		};
		return half == DoubleBlockHalf.UPPER ? p.above() : p;
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite(); // the front faces the player
		Level level = context.getLevel();
		BlockPos origin = context.getClickedPos();
		for (WardrobePart part : WardrobePart.values()) {
			for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
				BlockPos p = partPos(origin, facing, part, half);
				if (p.getY() >= level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(p)
						|| !level.getBlockState(p).canBeReplaced(context)) {
					return null;
				}
			}
		}
		return defaultBlockState().setValue(FACING, facing);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		Direction facing = state.getValue(FACING);
		for (WardrobePart part : WardrobePart.values()) {
			for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
				if (part != WardrobePart.CENTER || half != DoubleBlockHalf.LOWER) {
					level.setBlock(partPos(pos, facing, part, half), state.setValue(PART, part).setValue(HALF, half), Block.UPDATE_ALL);
				}
			}
		}
	}

	/** Lose any neighbouring part and this one goes too -- so breaking one block takes down all six. */
	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos,
			BlockPos neighborPos) {
		Direction facing = state.getValue(FACING);
		Direction left = leftOf(facing);
		WardrobePart part = state.getValue(PART);
		DoubleBlockHalf half = state.getValue(HALF);
		BlockState expected = null;
		if (direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)) {
			expected = state.setValue(HALF, half == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
		} else if (direction == left && part != WardrobePart.LEFT) {
			expected = state.setValue(PART, part == WardrobePart.CENTER ? WardrobePart.LEFT : WardrobePart.CENTER);
		} else if (direction == left.getOpposite() && part != WardrobePart.RIGHT) {
			expected = state.setValue(PART, part == WardrobePart.CENTER ? WardrobePart.RIGHT : WardrobePart.CENTER);
		}
		if (expected != null && neighbor != expected) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
	}

	/**
	 * Breaking any other block takes the middle bottom one down through {@link #updateShape}, and vanilla destroys a block
	 * that way with its drops -- so its loot table drops the Wardrobe, once. In creative that would still drop it: there
	 * the middle bottom block goes first, without drops (its suits still fall out, see {@link #onRemove}).
	 */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		BlockPos origin = origin(state, pos);
		if (!level.isClientSide() && player.getAbilities().instabuild && !origin.equals(pos) && level.getBlockState(origin).is(this)) {
			BlockState middle = level.getBlockState(origin);
			level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
			level.levelEvent(player, 2001, origin, Block.getId(middle));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WardrobeBlockEntity wardrobe) {
			Containers.dropContents(level, pos, wardrobe.items());
		}
		super.onRemove(state, level, pos, newState, moved);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.block();
	}

	@Override
	@Nullable
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(PART) == WardrobePart.CENTER && state.getValue(HALF) == DoubleBlockHalf.LOWER
				? new WardrobeBlockEntity(pos, state) : null;
	}

	// ---------------- using it ----------------

	/** The full-suit swap with the selected slot, with its sound and action-bar line (server side). */
	static void swap(Level level, BlockPos pos, Player player, WardrobeBlockEntity wardrobe) {
		if (wardrobe.swapSuit(player) > 0) {
			level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1.0f, 0.9f);
			player.displayClientMessage(wardrobe.describeSelected(), true);
		} else {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("block.projecthero.wardrobe.nothing"), true);
		}
	}

	/**
	 * v0.16.5 (user): Shift+right-click the middle swaps your whole suit whatever you are holding. Vanilla never calls a
	 * block's use while you sneak with something in hand, so this runs from a {@code UseBlockCallback} (WardrobeMod).
	 */
	static net.minecraft.world.InteractionResult sneakUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		BlockState state = level.getBlockState(hit.getBlockPos());
		if (!player.isSecondaryUseActive() || player.isSpectator() || !(state.getBlock() instanceof WardrobeBlock)
				|| state.getValue(PART) != WardrobePart.CENTER || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(level.getBlockEntity(origin(state, hit.getBlockPos())) instanceof WardrobeBlockEntity wardrobe)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			swap(level, hit.getBlockPos(), player, wardrobe);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(origin(state, pos)) instanceof WardrobeBlockEntity wardrobe)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (state.getValue(PART) != WardrobePart.CENTER) {
			scroll(state, level, pos, player, wardrobe);
			return ItemInteractionResult.sidedSuccess(level.isClientSide());
		}
		if (player.getEquipmentSlotForItem(stack).getType() != net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!level.isClientSide() && wardrobe.putOrTake(player, hand, WardrobeBlockEntity.slotAtHeight(hit.getLocation().y() - wardrobe.getBlockPos().getY()))) {
			level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8f, 1.0f);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(origin(state, pos)) instanceof WardrobeBlockEntity wardrobe)) {
			return InteractionResult.PASS;
		}
		if (state.getValue(PART) != WardrobePart.CENTER) {
			scroll(state, level, pos, player, wardrobe);
			return InteractionResult.sidedSuccess(level.isClientSide());
		}
		if (!player.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS; // holding something that is not armour: let it be used as normal
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			swap(level, pos, player, wardrobe);
		} else if (wardrobe.putOrTake(player, InteractionHand.MAIN_HAND,
				WardrobeBlockEntity.slotAtHeight(hit.getLocation().y() - wardrobe.getBlockPos().getY()))) {
			level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8f, 1.0f);
		}
		return InteractionResult.CONSUME;
	}

	private static void scroll(BlockState state, Level level, BlockPos pos, Player player, WardrobeBlockEntity wardrobe) {
		if (level.isClientSide()) {
			return;
		}
		wardrobe.scroll(state.getValue(PART) == WardrobePart.LEFT ? -1 : 1);
		level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.6f, 1.4f);
		if (player instanceof ServerPlayer sp) {
			sp.displayClientMessage(wardrobe.describeSelected(), true);
		}
	}
}
