package com.projecthero.mod.ironman.fabricator;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManItems;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Stark Fabricator block (spec sections 5-8). Right-click:
 * <ul>
 *   <li>with a {@code reactor_core} or {@code arc_reactor} in hand and the Tony Stark power: inject
 *       Stark energy into the buffer, consume the item, play a charge cue -- explicit, never a
 *       surprise consumption;</li>
 *   <li>empty-handed with the Tony Stark power: open the fabrication interface;</li>
 *   <li>without the Tony Stark power: "You don't understand Stark technology." -- the interface never
 *       opens, and {@link StarkFabricatorMenu#stillValid} would refuse it even if it did.</li>
 * </ul>
 */
public class StarkFabricatorBlock extends BaseEntityBlock {
	public static final MapCodec<StarkFabricatorBlock> CODEC = simpleCodec(StarkFabricatorBlock::new);

	private static final int ARC_REACTOR_ENERGY = 25_000;
	private static final int REACTOR_CORE_ENERGY = 8_000;

	public StarkFabricatorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/**
	 * Hand back whatever was in the machine when it is broken -- ingredients mid-craft and a finished
	 * suit piece still sitting in the output. The loot table only drops the block itself, so without
	 * this everything inside was silently deleted.
	 */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof net.minecraft.world.Container c) {
			net.minecraft.world.Containers.dropContents(level, pos, c);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StarkFabricatorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null
				: createTickerHelper(type, IronManBlocks.STARK_FABRICATOR_BE, StarkFabricatorBlockEntity::serverTick);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		int energy = stack.is(IronManItems.ARC_REACTOR) ? ARC_REACTOR_ENERGY
				: stack.is(IronManItems.REACTOR_CORE) ? REACTOR_CORE_ENERGY : -1;
		if (energy < 0) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!TonyStark.hasPower(player)) {
			deny(player);
			return ItemInteractionResult.SUCCESS;
		}
		if (level.isClientSide()) {
			return ItemInteractionResult.SUCCESS;
		}
		if (!(level.getBlockEntity(pos) instanceof StarkFabricatorBlockEntity be) || be.isFull()) {
			player.displayClientMessage(Component.translatable("message.projecthero.stark_fabricator.energy_full"), true);
			return ItemInteractionResult.SUCCESS;
		}
		be.addEnergy(energy);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		((ServerLevel) level).playSound(null, pos, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.stark_fabricator.energy_added",
				be.energy(), FabricatorRecipes.MAX_ENERGY).withStyle(ChatFormatting.AQUA), true);
		return ItemInteractionResult.sidedSuccess(false);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!TonyStark.hasPower(player)) {
			if (!level.isClientSide()) {
				deny(player);
			}
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof StarkFabricatorBlockEntity be) {
			serverPlayer.openMenu(be);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	private static void deny(Player player) {
		player.displayClientMessage(
				Component.translatable("message.projecthero.stark_fabricator.no_understanding").withStyle(ChatFormatting.RED), true);
	}
}
