package com.projecthero.mod.city;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.16.2 City part B: the Precinct Desk, the City Cop's job site ({@code projecthero:precinct_desk}). Using it reads
 * out the crime reports: every running crime and world event nearby, with directions.
 */
public class PrecinctDeskBlock extends CityFacingBlock {
	public static final MapCodec<PrecinctDeskBlock> CODEC = simpleCodec(PrecinctDeskBlock::new);

	public PrecinctDeskBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			var reports = DailyBulletin.reports(sl, pos, CityConfig.bulletin().reportRange);
			sp.sendSystemMessage(Component.translatable("block.projecthero.precinct_desk.header").withStyle(net.minecraft.ChatFormatting.BLUE));
			if (reports.isEmpty()) {
				sp.sendSystemMessage(Component.translatable("item.projecthero.daily_bulletin.quiet"));
			}
			for (DailyBulletin.Report r : reports) {
				sp.sendSystemMessage(r.line());
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}
}
