package com.projecthero.mod.hideout.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.hideout.HideoutFaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.5: a <b>sealed Hideout Vault</b> -- a Tier 3 Hero Vault wrapped in the faction's bars and locks. Unbreakable.
 * When the hideout is cleared ({@code HideoutClearing}) every seal near the core turns into an open Tier 3 Hero Vault
 * with the same facing.
 */
public class HideoutSealBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<HideoutSealBlock> CODEC = simpleCodec(HideoutSealBlock::new);
	public static final EnumProperty<HideoutFaction> FACTION = HideoutBlocks.FACTION;

	public HideoutSealBlock(BlockBehaviour.Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACTION, HideoutFaction.KINGPIN));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, FACTION);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			HideoutFaction f = state.getValue(FACTION);
			player.displayClientMessage(Component.translatable("block.projecthero.hideout_seal.locked", f.bossName())
					.withStyle(s -> s.withColor(f.colour())), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
