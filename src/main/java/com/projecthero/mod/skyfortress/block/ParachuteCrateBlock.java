package com.projecthero.mod.skyfortress.block;

import com.projecthero.mod.skyfortress.item.SkyFortressItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v0.17.7: a <b>Parachute Crate</b> by the hangar mouth. Right-click: take a Parachute (one at a time -- it refuses
 * while you already carry one). Never runs out. Breaking it drops nothing.
 */
public class ParachuteCrateBlock extends Block {
	public ParachuteCrateBlock(BlockBehaviour.Properties props) {
		super(props);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			give(server, pos, player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	/** Gives {@code player} a Parachute unless they carry one. True when one was given. */
	public static boolean give(ServerLevel level, BlockPos pos, Player player) {
		if (player.getInventory().contains(new ItemStack(SkyFortressItems.PARACHUTE))) {
			player.displayClientMessage(Component.translatable("block.projecthero.parachute_crate.have").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		ItemStack chute = new ItemStack(SkyFortressItems.PARACHUTE);
		if (!player.getInventory().add(chute)) {
			player.drop(chute, false);
		}
		level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
		player.displayClientMessage(Component.translatable("block.projecthero.parachute_crate.took").withStyle(ChatFormatting.GOLD), true);
		return true;
	}
}
