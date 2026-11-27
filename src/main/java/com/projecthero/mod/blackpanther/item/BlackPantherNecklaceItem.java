package com.projecthero.mod.blackpanther.item;

import java.util.List;

import com.projecthero.mod.blackpanther.BlackPanther;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.7 (user: "Make the suit just a necklace, player crafts the necklace, equips it and presses H to make it transform into the
 * full suit"): the Black Panther suit folded into a vibranium tooth necklace. Equipped like the Flash Ring holding the Flash Suit:
 * a wearable item in the CHESTPLATE slot (right-click it, shift-click it or drag it there), so death, keepInventory and graves
 * treat it like armour. Worn by the Black Panther, H lets the suit out of it and H again draws it back
 * ({@code BlackPanther.toggleSuit}); taking it off draws the suit back too. It is not drawn on the player unless the suit is on.
 */
public class BlackPantherNecklaceItem extends Item implements Equipable {
	public BlackPantherNecklaceItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (!BlackPanther.hasPower(player)) {
			// as the Flash Ring: only its hero puts it on (the client sees the synced state too, so both sides agree)
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.projecthero.black_panther.necklace_refuse")
						.withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		return swapWithEquipmentSlot(this, level, player, hand);
	}

	/**
	 * v0.17.7 (user: "black panther necklace can only be worn by players with black panther ability"): a dispenser puts it on a
	 * Black Panther in front with the chest slot free; for anyone else (or no one) it is shot out like any item. The other paths:
	 * {@code LivingEntitySupermanSuitMixin} (armour slot, shift-click, hotbar swap, creative screen), {@link #use} (right-click)
	 * and {@code BlackPanther.ejectNecklace} (every tick, and when the power is lost: a worn necklace pops off).
	 */
	public static final net.minecraft.core.dispenser.DefaultDispenseItemBehavior DISPENSE = new net.minecraft.core.dispenser.DefaultDispenseItemBehavior() {
		@Override
		protected ItemStack execute(net.minecraft.core.dispenser.BlockSource source, ItemStack stack) {
			net.minecraft.core.BlockPos pos = source.pos().relative(source.state().getValue(net.minecraft.world.level.block.DispenserBlock.FACING));
			List<Player> targets = source.level().getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(pos),
					p -> p.isAlive() && !p.isSpectator() && p.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && BlackPanther.hasPower(p));
			if (targets.isEmpty()) {
				return super.execute(source, stack);
			}
			targets.get(0).setItemSlot(EquipmentSlot.CHEST, stack.split(1));
			return stack;
		}
	};

	@Override
	public EquipmentSlot getEquipmentSlot() {
		return EquipmentSlot.CHEST;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.black_panther_necklace.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.projecthero.black_panther_necklace.tooltip2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.black_panther_necklace.tooltip3").withStyle(ChatFormatting.DARK_GRAY));
	}
}
