package com.projecthero.mod.city;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.16.2 (user: "a place in the village where the villagers can deposit food to repopulate their city"): the Food Bank
 * ({@code projecthero:food_bank}). Players use food on it, villagers with spare food walk over and drop it off, and every
 * {@link FoodBankBlockEntity#NEWCOMER_COST} food stored becomes a newborn villager -- while there is a free bed nearby.
 * Stands in every City's Market Square; craftable for any village.
 */
public class FoodBankBlock extends CityFacingBlock implements EntityBlock {
	public static final MapCodec<FoodBankBlock> CODEC = simpleCodec(FoodBankBlock::new);

	public FoodBankBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FoodBankBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != CityBlocks.FOOD_BANK_BE ? null
				: (BlockEntityTicker<T>) (BlockEntityTicker<FoodBankBlockEntity>) (l, p, s, be) -> be.serverTick((ServerLevel) l);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (stack.isEmpty()) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!(level.getBlockEntity(pos) instanceof FoodBankBlockEntity bank)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (FoodBankBlockEntity.value(stack) <= 0) {
			if (stack.has(net.minecraft.core.component.DataComponents.FOOD) && player instanceof ServerPlayer sp) {
				sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("block.projecthero.food_bank.rejected")
						.withStyle(net.minecraft.ChatFormatting.RED), true);
				return ItemInteractionResult.sidedSuccess(level.isClientSide());
			}
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			int added = bank.deposit(stack, !player.getAbilities().instabuild);
			if (added > 0) {
				sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("block.projecthero.food_bank.deposited", added)
						.withStyle(net.minecraft.ChatFormatting.GREEN), true);
				sp.sendSystemMessage(bank.statusLine(sl));
			} else {
				sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("block.projecthero.food_bank.full")
						.withStyle(net.minecraft.ChatFormatting.YELLOW), true);
			}
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof FoodBankBlockEntity bank) {
			sp.sendSystemMessage(bank.statusLine(sl));
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FoodBankBlockEntity bank && bank.food() > 0) {
			// the stock comes back out as bread (5 food a loaf)
			int loaves = Math.min(bank.food() / 5, 64 * 4);
			while (loaves > 0) {
				int n = Math.min(64, loaves);
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.BREAD, n));
				loaves -= n;
			}
		}
		super.onRemove(state, level, pos, newState, moved);
	}
}
