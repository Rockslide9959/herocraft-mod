package com.projecthero.mod.moonknight.temple;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Altar of Khonshu, at the heart of every Temple of Khonshu beneath the roof's open oculus. Right-click it at
 * night holding the {@link ScarabOfKhonshuItem Scarab} to lay the scarab on it and begin the pact ritual
 * ({@link KhonshuRitual}); the {@link KhonshuAltarBlockEntity} runs it. Once a pact has been sealed on it the altar
 * is {@link #SPENT} -- cracked, dark, and never usable again.
 */
public class KhonshuAltarBlock extends BaseEntityBlock {
	public static final MapCodec<KhonshuAltarBlock> CODEC = simpleCodec(KhonshuAltarBlock::new);
	/** The cracked look of an altar whose pact has been sealed. Mirrors the block entity's SPENT state. */
	public static final BooleanProperty SPENT = BooleanProperty.create("spent");

	public KhonshuAltarBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(SPENT, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SPENT);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new KhonshuAltarBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || state.getValue(SPENT) ? null
				: createTickerHelper(type, KhonshuTemple.KHONSHU_ALTAR_BE, KhonshuAltarBlockEntity::serverTick);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(KhonshuTemple.SCARAB_OF_KHONSHU)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			KhonshuRitual.placeScarab(sp, sl, pos, stack);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			boolean holding = level.getBlockEntity(pos) instanceof KhonshuAltarBlockEntity be
					&& be.altarState() == KhonshuAltarBlockEntity.AltarState.HOLDING_SCARAB;
			String key = state.getValue(SPENT) ? "message.projecthero.khonshu.altar.spent_hint"
					: holding ? "message.projecthero.khonshu.altar.holding_hint" : "message.projecthero.khonshu.altar.empty_hint";
			player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
