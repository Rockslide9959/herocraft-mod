package com.projecthero.mod.greenlantern.block;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternBattery;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Personal Power Battery. Right-click by its bonded owner begins the Oath recitation that fully
 * recharges the ring on completion (v0.11.4); any other Green Lantern may also use it (per the build
 * brief's fallback -- ownership on placed batteries isn't tracked, team-sharing is implicit). See
 * {@link GreenLanternBattery} for the oath state machine and cancel conditions.
 */
public class PowerBatteryBlock extends Block {
	public PowerBatteryBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (!GreenLantern.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.green_lantern.not_a_lantern"), true);
			return InteractionResult.CONSUME;
		}
		GreenLanternBattery.beginOath(sp, pos);
		return InteractionResult.CONSUME;
	}
}
