package com.projecthero.mod.ironman.gantry;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ironman.IronManBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.15.4, explicit user request: the <b>Stark Gantry Floor</b> -- a piston-style floor tile. Lay a complete 5x5 of them
 * and it is a gantry ({@link StarkGantry}): stand on it as Tony Stark and press H to pick a suit racked in any Suit
 * Platform within 20 blocks; the centre tile lifts you half a block, the tiles to your left, right and front slide open
 * and robotic arms come up out of the floor and fit the suit onto you (H again in a suit takes it off the same way).
 *
 * <p>{@link #OPEN}: the tile's hatch is open (static model = an empty steel pit; the sliding panels, the lift pad, the
 * arm masts and the elevator are all drawn by the centre tile's block-entity renderer). Every tile carries a block
 * entity, but only the centre of a running gantry does anything with it; tiles heal themselves shut if a sequence
 * ended without closing them (chunk unload, crash).
 */
public class StarkGantryFloorBlock extends BaseEntityBlock {
	public static final MapCodec<StarkGantryFloorBlock> CODEC = simpleCodec(StarkGantryFloorBlock::new);
	public static final BooleanProperty OPEN = BooleanProperty.create("open");

	public StarkGantryFloorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(OPEN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(OPEN);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StarkGantryFloorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null
				: createTickerHelper(type, IronManBlocks.GANTRY_FLOOR_BE, StarkGantryFloorBlockEntity::serverTick);
	}

	/** Right-click: a one-line reminder of how the gantry works (and whether this 5x5 is complete). */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		boolean formed = StarkGantry.findCentre(level, pos) != null;
		player.displayClientMessage(Component.translatable(formed ? "message.projecthero.gantry.formed"
				: "message.projecthero.gantry.incomplete").withStyle(formed ? ChatFormatting.AQUA : ChatFormatting.GOLD), true);
		return InteractionResult.SUCCESS;
	}

	/**
	 * A tile broken out of a running gantry stops it (pieces still in the floor go back to their Suit Platform, anything
	 * that can't drops here); a broken centre also drops whatever its floor still held.
	 */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
			StarkGantry.floorRemoved(sl, pos);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
