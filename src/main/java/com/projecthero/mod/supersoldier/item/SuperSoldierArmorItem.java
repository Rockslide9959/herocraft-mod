package com.projecthero.mod.supersoldier.item;

import java.util.List;

import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.supersoldier.SuperSoldierArmorGate;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * One piece of the craftable Captain America suit (v0.14.9), drawn from the user's own Blockbench model
 * ({@code geo/captain_america.geo.json} + {@code textures/armor/captain_america.png}, converted by
 * {@code scratchpad/gen_supersoldier_v0149.js}). Only a Super Soldier can wear it: a right-click equip is refused here,
 * every other route is undone by {@link SuperSoldierArmorGate} on the next tick.
 */
public class SuperSoldierArmorItem extends SuperheroArmorItem {
	public static final String SET_ID = "captain_america";

	public SuperSoldierArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public String armorSetId() {
		return SET_ID;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (!SuperSoldierArmorGate.mayWear(player)) {
			if (!level.isClientSide) {
				player.displayClientMessage(SuperSoldierArmorGate.lockedMessage(), true);
			}
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		return super.use(level, player, hand);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.captain_america_suit.desc").withStyle(ChatFormatting.BLUE));
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
