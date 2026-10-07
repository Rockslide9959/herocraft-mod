package com.projecthero.mod.ultron.block;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ultron.Ultron;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.15.12: the <b>Ultron Beacon</b>. Place it and right-click it (in Survival, never on Peaceful) to start the Ultron
 * Uprising centred on it: the beacon becomes the <b>Ultron Uplink</b> -- the heart of the arena, {@link #ACTIVE} (glowing
 * red, impossible to mine) while the uprising runs -- and turns into the reward chest when Ultron is purged.
 */
public class UltronBeaconBlock extends Block {
	public static final MapCodec<UltronBeaconBlock> CODEC = simpleCodec(UltronBeaconBlock::new);
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public UltronBeaconBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (state.getValue(ACTIVE)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ultron.already").withStyle(ChatFormatting.RED), true);
			return InteractionResult.CONSUME;
		}
		if (player.isCreative() || player.isSpectator()) {
			player.displayClientMessage(Component.translatable("message.projecthero.ultron.survival_only").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME;
		}
		if (server.getDifficulty() == Difficulty.PEACEFUL) {
			player.displayClientMessage(Component.translatable("message.projecthero.ultron.peaceful").withStyle(ChatFormatting.GRAY), true);
			return InteractionResult.CONSUME;
		}
		if (Ultron.start(server, pos, sp) == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.ultron.refused").withStyle(ChatFormatting.RED), true);
		}
		return InteractionResult.CONSUME;
	}

	/** While its uprising runs nobody can mine it. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return state.getValue(ACTIVE) ? 0f : super.getDestroyProgress(state, player, level, pos);
	}

	/** The beacon's item tooltip. */
	public static void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("block.projecthero.ultron_beacon.hint").withStyle(ChatFormatting.DARK_RED));
		tooltip.add(Component.translatable("block.projecthero.ultron_beacon.hint2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
