package com.projecthero.mod.thoracquire.forge;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.thoracquire.ThorAcquire;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.7: the <b>Star Forge</b> -- a netherite-and-blackstone core that burns like a dying star once lightning is poured into
 * it. It works only inside a ring of eight blackstone blocks ({@link StarForge#ringComplete}). See {@link StarForge} for the
 * whole process; {@code HEAT} (0 cold, 1 a head inside, 2 forged head / burning) drives the top texture and the light.
 */
public class StarForgeBlock extends BaseEntityBlock {
	public static final MapCodec<StarForgeBlock> CODEC = simpleCodec(StarForgeBlock::new);
	public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 2);

	public StarForgeBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(HEAT, 0));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HEAT);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StarForgeBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, ThorAcquire.STAR_FORGE_BE, (l, p, s, be) -> be.tick(l, p, s));
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		boolean ours = stack.is(ModItems.MJOLNIR) || stack.is(ModItems.UNFORGED_STORMBREAKER) || stack.is(ThorAcquire.LIVING_BRANCH);
		if (!ours) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof StarForgeBlockEntity be) {
			StarForge.use(server, pos, be, sp, stack);
		}
		// success on the client too: holding right-click with Mjolnir channels, it never throws the hammer
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof StarForgeBlockEntity be) {
			StarForge.useEmptyHand(server, pos, be, sp);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof StarForgeBlockEntity be && !be.head().isEmpty()) {
			Block.popResource(level, pos, be.head().copy());
		}
		super.onRemove(state, level, pos, newState, moved);
	}
}
