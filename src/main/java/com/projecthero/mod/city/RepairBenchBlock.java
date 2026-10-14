package com.projecthero.mod.city;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.16.2 City part B: the Repair Bench, the Repair Worker's job site ({@code projecthero:repair_bench}). Using it shows
 * the City's repair board: damaged blocks waiting, and when the next trip is due.
 */
public class RepairBenchBlock extends CityFacingBlock {
	public static final MapCodec<RepairBenchBlock> CODEC = simpleCodec(RepairBenchBlock::new);

	public RepairBenchBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			sp.sendSystemMessage(CityRepair.statusLine(sl, pos));
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
