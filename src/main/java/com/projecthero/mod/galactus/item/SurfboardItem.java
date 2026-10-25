package com.projecthero.mod.galactus.item;

import java.util.List;

import com.projecthero.mod.galactus.GalactusFx;
import com.projecthero.mod.galactus.entity.GalactusEntityTypes;
import com.projecthero.mod.galactus.entity.SurferBoardEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.16.1: <b>the Silver Surfer's Board</b>, stored. v0.17.1: right-click floods the Power Cosmic into you -- the board
 * bonds to you (it is used up) and you become the Silver Surfer ({@code silversurfer.SilverSurfer#grant}, one Primary
 * power). Already the Surfer: right-click calls the board up under your feet (board flight) and the item stays. If the
 * power is someone else's (the unique-powers rule) it is still the v0.16.1 rideable board: it appears under you and you
 * ride it ({@link SurferBoardEntity}); sneak + right-click the board to put it back in your pack.
 */
public class SurfboardItem extends Item {
	public SurfboardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isPassenger()) {
			return InteractionResultHolder.fail(stack);
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			if (com.projecthero.mod.silversurfer.SilverSurfer.hasPower(sp)) {
				// already the Surfer: the board flies up under his feet
				com.projecthero.mod.silversurfer.SilverSurferFlight.start(sp);
				return InteractionResultHolder.success(stack);
			}
			if (com.projecthero.mod.silversurfer.SilverSurfer.grant(sp)) {
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
				return InteractionResultHolder.success(stack);
			}
			// the Power Cosmic is someone else's: just a rideable board
			SurferBoardEntity board = GalactusEntityTypes.SURFER_BOARD.create(server);
			if (board == null) {
				return InteractionResultHolder.fail(stack);
			}
			board.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);
			board.setOwner(player.getUUID());
			server.addFreshEntity(board);
			player.startRiding(board);
			GalactusFx.burst(server, board.position(), 0.6);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.6f);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.silver_surfer_board.hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.silver_surfer_board.hint2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
