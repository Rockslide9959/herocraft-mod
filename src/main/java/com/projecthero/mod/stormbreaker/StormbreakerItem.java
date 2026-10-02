package com.projecthero.mod.stormbreaker;

import java.util.List;

import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * v0.14.19: Stormbreaker -- Thor's second weapon, crafted as an {@link UnforgedStormbreakerItem} and forged in Nether
 * lava ({@link StormbreakerForge}).
 *
 * <ul>
 *   <li>Anyone can swing it (14 melee damage). Only the worthy can use its powers; an unworthy right-click gets
 *   Mjolnir's dull clang and nothing else.</li>
 *   <li>Right-click throws it ({@link StormbreakerEntity}): it pierces, calls lightning on the first thing it hits
 *   and always comes back on its own.</li>
 *   <li>Sneak + right-click opens the Bifrost ({@link Bifrost}) -- a long-range teleport with a 30 s item cooldown.
 *   It never binds or unbinds anything; that is Mjolnir's alone.</li>
 *   <li>For Thor's keybind powers and flight it counts as his weapon -- see
 *   {@link com.projecthero.mod.power.ThorPowers#isHoldingThorWeapon}.</li>
 * </ul>
 */
public class StormbreakerItem extends Item {
	/** 1 base + 13 = 14 damage -- three more than Mjolnir's 11. */
	public static final double ATTACK_DAMAGE_BONUS = 13.0;
	/** 4.0 base - 3.1 = 0.9 attacks/second: a touch slower than Mjolnir's 1.1. */
	public static final double ATTACK_SPEED_BONUS = -3.1;

	public StormbreakerItem(Properties properties) {
		super(properties);
	}

	public static ItemAttributeModifiers createAttributes() {
		return ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE,
						new AttributeModifier(BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE_BONUS, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED,
						new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED_BONUS, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.build();
	}

	/** Short lines only -- tooltips never wrap on their own (TooltipWrap is a guard, not a layout tool). */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.stormbreaker.flavor.line1").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		tooltip.add(Component.translatable("item.projecthero.stormbreaker.flavor.line2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		tooltip.add(Component.empty());
		tooltip.add(Component.translatable("item.projecthero.stormbreaker.ability.throw").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.stormbreaker.ability.bifrost").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.stormbreaker.ability.bifrost2").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.projecthero.stormbreaker.thor_weapon").withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("item.projecthero.stormbreaker.worthy_only").withStyle(ChatFormatting.DARK_GRAY));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResultHolder.pass(stack);
		}

		if (!Worthiness.isWorthy(player)) {
			if (!level.isClientSide()) {
				level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.6f);
			}
			return InteractionResultHolder.fail(stack);
		}

		if (player.isShiftKeyDown()) {
			// Sneak + right-click on a block within arm's reach stays vanilla (nothing happens, the block is not
			// opened -- exactly as with any held sword). The Bifrost only opens toward somewhere further away.
			if (Bifrost.targetsBlockInReach(player)) {
				return InteractionResultHolder.pass(stack);
			}
			if (level.isClientSide()) {
				return InteractionResultHolder.success(stack);
			}
			if (player instanceof ServerPlayer serverPlayer && Bifrost.open(serverPlayer)) {
				return InteractionResultHolder.success(stack);
			}
			return InteractionResultHolder.fail(stack);
		}

		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		if (player instanceof ServerPlayer serverPlayer && StormbreakerEntity.throwFrom(serverPlayer)) {
			return InteractionResultHolder.success(stack);
		}
		return InteractionResultHolder.pass(stack);
	}
}
