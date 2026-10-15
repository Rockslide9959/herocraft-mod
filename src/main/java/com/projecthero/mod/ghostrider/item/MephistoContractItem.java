package com.projecthero.mod.ghostrider.item;

import java.util.List;
import java.util.function.Consumer;

import com.projecthero.mod.ghostrider.GhostRider;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.16.5: <b>Mephisto's Contract</b> -- rare loot in Nether fortress and bastion chests. Right-click opens the "sign in
 * blood" screen (client); signing sends {@code GhostRiderActionPayload.SIGN_CONTRACT} and {@link #sign} binds the Spirit
 * of Vengeance to the player (the Hero-Tier Ghost Rider power, replacing whatever Primary they held). The blood price --
 * 5 hearts of max health -- is owed until the first transformation. The contract burns away when signed.
 */
public class MephistoContractItem extends Item {
	/** Client hook: opens the signing screen (set by the client; does nothing on a dedicated server). */
	public static Consumer<Player> openScreen = p -> {
	};

	public MephistoContractItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (GhostRider.hasPower(player)) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.mephisto_contract.already")
						.withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(stack);
		}
		if (level.isClientSide()) {
			openScreen.accept(player);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/** The screen's "Sign" button: grants Ghost Rider and burns one contract from the hands. True if it took. */
	public static boolean sign(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem().getItem() instanceof MephistoContractItem ? player.getMainHandItem()
				: player.getOffhandItem().getItem() instanceof MephistoContractItem ? player.getOffhandItem() : ItemStack.EMPTY;
		if (stack.isEmpty() || GhostRider.hasPower(player)) {
			return false;
		}
		if (!GhostRider.grant(player)) {
			return false;
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.mephisto_contract.tooltip").withStyle(ChatFormatting.RED));
		tooltip.add(Component.translatable("item.projecthero.mephisto_contract.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
