package com.projecthero.mod.syndicate.item;

import java.util.List;

import com.projecthero.mod.syndicate.SyndicateGunfire;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.25: the Kingpin's cane -- his boss trophy. A heavy, slow club (9 attack damage, a slow swing) with a barrel hidden
 * in the shaft: use it to fire one hit-scan shot (7 damage, 32 blocks) every 2.5 seconds. 1,200 uses.
 */
public class KingpinCaneItem extends Item {
	public static final float SHOT_DAMAGE = 7.0f;
	public static final int SHOT_COOLDOWN = 50;

	public KingpinCaneItem(Properties properties) {
		super(properties.durability(1200).attributes(modifiers()));
	}

	public static ItemAttributeModifiers modifiers() {
		return ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 8.0, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.9, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.build();
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			Vec3 aim = player.getEyePosition().add(player.getLookAngle().scale(32));
			SyndicateGunfire.fire(server, player, aim, 32, SHOT_DAMAGE, 0.004, 1, SyndicateGunfire.Report.CANE);
			stack.hurtAndBreak(2, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		}
		player.getCooldowns().addCooldown(this, SHOT_COOLDOWN);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		return true;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.kingpin_cane.tooltip").withStyle(ChatFormatting.GRAY));
		// v0.14.31: the swing animations (KingpinCaneSwing) -- cosmetic, the damage is unchanged
		tooltip.add(Component.translatable("item.projecthero.kingpin_cane.tooltip.strikes").withStyle(ChatFormatting.DARK_GRAY));
	}
}
