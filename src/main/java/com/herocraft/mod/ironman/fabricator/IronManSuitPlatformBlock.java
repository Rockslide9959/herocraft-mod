package com.herocraft.mod.ironman.fabricator;

import com.herocraft.mod.ironman.IronManBlocks;
import com.herocraft.mod.ironman.TonyStark;
import com.herocraft.mod.ironman.item.IronManItems;

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
 * The Iron Man Suit Platform block (spec section 33).
 * <ul>
 *   <li>right-click with an Iron Man armour piece: store it;</li>
 *   <li>right-click with a Reactor Core: add {@value IronManSuitPlatformBlockEntity#REACTOR_CORE_ENERGY}
 *       to the platform's recharge buffer;</li>
 *   <li>right-click empty-handed (Tony Stark): open the platform GUI (slots + energy + DEPLOY / RETRIEVE);</li>
 *   <li>sneak + right-click empty-handed (Tony Stark): retrieve your worn suit straight onto the platform.</li>
 * </ul>
 */
public class IronManSuitPlatformBlock extends BaseEntityBlock {
	public static final MapCodec<IronManSuitPlatformBlock> CODEC = simpleCodec(IronManSuitPlatformBlock::new);

	public IronManSuitPlatformBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/**
	 * Give the racked suit back when the platform is broken. Without this, mining a platform holding a
	 * fully-fabricated Mark 50 destroyed it outright -- the block's loot table only ever drops the
	 * block. The stacks dropped are the stored ones, so the suit's carried charge and integrity come
	 * with it.
	 *
	 * <p>This is also the <em>only</em> place the {@link com.herocraft.mod.ironman.data.StarkPlatformRegistry}
	 * entry is dropped. It used to be done from the block entity's {@code setRemoved}, but vanilla
	 * calls that on chunk unload too, which deleted the record that exists specifically so a suit can
	 * be called off a platform in an unloaded chunk. {@code onRemove} fires only when the block itself
	 * actually changes, which is the real "this platform is gone" signal.
	 */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container c) {
				net.minecraft.world.Containers.dropContents(level, pos, c);
			}
			if (level instanceof ServerLevel sl) {
				com.herocraft.mod.ironman.data.StarkPlatformRegistry.get(sl).remove(sl, pos);
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new IronManSuitPlatformBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && placer instanceof ServerPlayer player
				&& level.getBlockEntity(pos) instanceof IronManSuitPlatformBlockEntity be) {
			be.bindTo(player.getUUID());
		}
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null
				: createTickerHelper(type, IronManBlocks.SUIT_PLATFORM_BE, IronManSuitPlatformBlockEntity::serverTick);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof IronManSuitPlatformBlockEntity be)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (stack.getItem() instanceof com.herocraft.mod.ironman.item.IronManArmorItem) {
			if (!level.isClientSide() && be.store(stack)) {
				((ServerLevel) level).playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide());
		}
		if (stack.is(IronManItems.REACTOR_CORE) || stack.is(IronManItems.ARC_REACTOR)) {
			if (!TonyStark.hasPower(player)) {
				return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			}
			if (!level.isClientSide()) {
				be.addEnergy(stack.is(IronManItems.ARC_REACTOR) ? 25_000 : IronManSuitPlatformBlockEntity.REACTOR_CORE_ENERGY);
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
				((ServerLevel) level).playSound(null, pos, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.3f);
				player.displayClientMessage(Component.translatable("screen.herocraft.suit_platform.energy",
						be.storedEnergy(), IronManSuitPlatformBlockEntity.MAX_ENERGY).withStyle(ChatFormatting.AQUA), true);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide());
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)
				|| !(level.getBlockEntity(pos) instanceof IronManSuitPlatformBlockEntity be)) {
			return InteractionResult.PASS;
		}
		if (!TonyStark.hasPower(serverPlayer)) {
			serverPlayer.displayClientMessage(
					Component.translatable("message.herocraft.ironman.armor_rejects").withStyle(ChatFormatting.RED), true);
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			if (be.retrieveFrom(serverPlayer)) {
				((ServerLevel) level).playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9f, 1.1f);
			}
			return InteractionResult.SUCCESS;
		}
		serverPlayer.openMenu(be);
		return InteractionResult.SUCCESS;
	}
}
