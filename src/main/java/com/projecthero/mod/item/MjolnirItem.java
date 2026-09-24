package com.projecthero.mod.item;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.hammer.MjolnirRegistry;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
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
 * Mjolnir. Right-click throws it, sneak-right-click binds/unbinds it; the rest of the powers are
 * driven by keybinds (or, for flight, a double-tap of jump) -- see {@link ThorPowers}.
 *
 * <p>It carries real melee attribute modifiers like any vanilla weapon, so the game's own tooltip
 * shows its attack damage/speed, and {@link #appendHoverText} adds the things vanilla has no way of
 * knowing about: who the hammer answers to and what each of its abilities is bound to.
 */
public class MjolnirItem extends Item {
	/** Two points above a netherite sword: this is the weapon of a god, not a tool. */
	private static final double ATTACK_DAMAGE_BONUS = 9.0;
	/** Base attack speed is 4.0, so this leaves one swing per second -- it's a very heavy hammer. */
	private static final double ATTACK_SPEED_BONUS = -3.0;

	public MjolnirItem(Properties properties) {
		super(properties);
	}

	/**
	 * Melee stats, in the same shape vanilla weapons use -- which is what makes the game render the
	 * familiar "When in Main Hand: +N Attack Damage" block under the tooltip for free.
	 */
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

	/**
	 * Runs before vanilla's attribute/enchantment blocks (see {@code ItemStack.getTooltipLines}), so
	 * these lines sit between the item name and the "+N Attack Damage" section.
	 *
	 * <p>Collapsed by default. The full ability list is ten lines long, and a tooltip that tall gets
	 * repositioned by the game to fit on screen -- which is why it used to end up pinned in a corner
	 * looking like a permanent HUD panel instead of sitting beside the item. Held shift expands it;
	 * see {@link MjolnirTooltip} for how a client-only key state is read from common code.
	 */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);

		// Three short lines rather than one long sentence -- a single-line flavour string this long
		// forces the whole tooltip to stretch far wider than the item name or ability lines need,
		// which is what made the box look oversized. Tooltips don't word-wrap on their own.
		tooltip.add(Component.translatable("item.projecthero.mjolnir.flavor.line1").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		tooltip.add(Component.translatable("item.projecthero.mjolnir.flavor.line2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		tooltip.add(Component.translatable("item.projecthero.mjolnir.flavor.line3").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		tooltip.add(Component.translatable("item.projecthero.mjolnir.lift_hint").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));

		tooltip.add(Component.empty());
		tooltip.add(ownershipLine(stack));

		if (MjolnirTooltip.expanded()) {
			tooltip.add(Component.empty());
			tooltip.add(Component.translatable("item.projecthero.mjolnir.abilities")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			MjolnirTooltip.appendAbilities(tooltip);
		} else {
			tooltip.add(MjolnirTooltip.expandHint());
		}
	}

	private static Component ownershipLine(ItemStack stack) {
		UUID boundOwner = stack.get(ModDataComponents.BOUND_OWNER);
		if (boundOwner == null) {
			return Component.translatable("item.projecthero.mjolnir.unbound").withStyle(ChatFormatting.DARK_GRAY);
		}
		String name = stack.get(ModDataComponents.BOUND_OWNER_NAME);
		Component owner = Component.literal(name != null ? name : boundOwner.toString().substring(0, 8))
				.withStyle(ChatFormatting.AQUA);
		return Component.translatable("item.projecthero.mjolnir.bound_to", owner).withStyle(ChatFormatting.GRAY);
	}

	/**
	 * Two pieces of housekeeping that need to happen wherever a hammer ends up, and this is the one
	 * hook that fires for every inventory Mjolnir can be in:
	 * <ul>
	 *   <li>give it its identity, if some path we do not have a hook on (crafting, {@code /give}, a
	 *   loot table, another mod) produced it;</li>
	 *   <li>delete it if it is a copy that a recall has already superseded -- the other half of the
	 *   duplication guarantee described on {@link MjolnirRegistry}.</li>
	 * </ul>
	 * Both are guarded so the common case is a component read and an integer compare.
	 */
	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, level, entity, slot, selected);

		if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player)) {
			return;
		}

		MjolnirRegistry registry = MjolnirRegistry.get(serverLevel);

		if (registry.isStale(stack)) {
			stack.setCount(0);
			return;
		}

		if (stack.get(ModDataComponents.HAMMER_ID) == null) {
			registry.noteCarried(stack, player, selected);
			return;
		}

		// Refresh the record occasionally rather than every tick: it only needs to be roughly right
		// (which player is carrying it), and the expensive part -- marking the SavedData dirty --
		// already no-ops when nothing changed.
		if (player.tickCount % 100 == 0) {
			registry.noteCarried(stack, player, selected);
		}
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
			// Bind/unbind: designate this player as the hammer's owner (or release it), independent
			// of whoever last threw or dropped it.
			if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
				ThorPowers.toggleBinding(serverPlayer, stack);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}

		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}

		if (player instanceof ServerPlayer serverPlayer && ThorPowers.throwMjolnir(serverPlayer)) {
			return InteractionResultHolder.success(stack);
		}

		return InteractionResultHolder.pass(stack);
	}
}
