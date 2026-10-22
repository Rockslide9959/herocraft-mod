package com.projecthero.mod.ironman.item;

import java.util.List;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.suit.PortableDeploy;

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
 * v0.16.7 (user) Iron Man Era II stage 2: a suit packed into a <b>backpack</b> -- the Mk VII Silver Centurion's pack and
 * the Mk VIII Space's thruster pack ({@code PortableForms.MK7_BACKPACK} / {@code MK8_THRUSTER_PACK}). Like the Mk VI
 * briefcase it holds the four real armour stacks ({@link SuitcaseContents}) with integrity and charge stamped on.
 *
 * <p>It is carried in the inventory, and while it is carried it is <b>worn on the back</b>: the server mirrors "which pack
 * is on this player's back" into a synced attachment ({@code ModAttachments.IRON_MAN_BACK_PACK},
 * {@link PortableDeploy#backTick}) and every viewer draws it there (the held item is never drawn in the hand). C (no suit
 * on) or right-click deploys it; holding Shift+C while wearing the suit folds it back in.
 */
public class IronManPackItem extends Item {
	private final String suitId;

	public IronManPackItem(Properties properties, String suitId) {
		super(properties);
		this.suitId = suitId;
	}

	public String suitId() {
		return suitId;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.pass(stack);
		}
		if (!TonyStark.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.ironman.armor_rejects")
					.withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}
		if (IronManArmor.wearingAnyIronMan(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.ironman.portable_take_off_first")
					.withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(stack);
		}
		return PortableDeploy.deploy(sp, suitId) ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable(getDescriptionId() + ".hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		if (!SuitcaseContents.isLegacyEmpty(stack)) {
			tooltip.add(Component.translatable("item.projecthero.iron_man_pack.contents",
					SuitcaseContents.nonEmpty(stack).size()).withStyle(ChatFormatting.GRAY));
			if (SuitcaseContents.isEmpty(stack)) { // v0.16.7 (user): a crafted / emptied pack -- how to fill it
				tooltip.add(Component.translatable("item.projecthero.portable.empty").withStyle(ChatFormatting.GRAY));
			}
			float max = IronManEnergy.maxIntegrity(suitId);
			if (max > 0f) {
				tooltip.add(Component.translatable("item.projecthero.iron_man_pack.integrity",
						Math.round(100f * IronManEnergy.stackIntegrity(stack, suitId) / max)).withStyle(ChatFormatting.GRAY));
			}
		}
		if (com.projecthero.mod.ironman.suit.ThrusterPackFlight.SUIT.equals(suitId)) {
			// v0.16.7 (user): carried, the thruster pack gives slow basic flight (TooltipWrap wraps the long line)
			tooltip.add(Component.translatable("item.projecthero.space_thruster_pack.flight",
					Math.round(IronManEnergy.stackEnergy(stack, suitId))).withStyle(ChatFormatting.AQUA));
		}
		tooltip.add(Component.translatable("item.projecthero.ironman.requires_tony_stark").withStyle(ChatFormatting.DARK_AQUA));
	}
}
