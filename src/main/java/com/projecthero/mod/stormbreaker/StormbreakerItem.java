package com.projecthero.mod.stormbreaker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hammer.HammerRecord;
import com.projecthero.mod.hammer.MjolnirRegistry;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.power.ThorFeedback;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
 *   <li>Sneak + right-click opens the Bifrost screen ({@link Bifrost}, v0.14.20) -- travel to typed coordinates or one
 *   of three saved waypoints, carrying nearby squadmates, on its own 60 s cooldown (the throw is never held). (Sneak +
 *   right-click therefore never binds anything; Stormbreaker binds itself on first carry -- see {@link #inventoryTick}.)</li>
 *   <li>v0.15.1: bound and tracked like Mjolnir ({@link com.projecthero.mod.hammer.MjolnirRegistry}), so the call key
 *   brings it back from anywhere, interchangeably with Mjolnir -- see {@link com.projecthero.mod.hammer.MjolnirRecall}.</li>
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
		tooltip.add(Component.translatable("item.projecthero.stormbreaker.ability.bifrost3").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.projecthero.stormbreaker.thor_weapon").withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("item.projecthero.stormbreaker.worthy_only").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(ownershipLine(stack));
	}

	private static Component ownershipLine(ItemStack stack) {
		UUID boundOwner = stack.get(ModDataComponents.BOUND_OWNER);
		if (boundOwner == null) {
			return Component.translatable("item.projecthero.stormbreaker.unbound").withStyle(ChatFormatting.DARK_GRAY);
		}
		String name = stack.get(ModDataComponents.BOUND_OWNER_NAME);
		Component owner = Component.literal(name != null ? name : boundOwner.toString().substring(0, 8))
				.withStyle(ChatFormatting.AQUA);
		return Component.translatable("item.projecthero.mjolnir.bound_to", owner).withStyle(ChatFormatting.GRAY);
	}

	/**
	 * v0.15.1: Stormbreaker now has Mjolnir's ownership tracking, so the call key can bring it back from anywhere.
	 * Same housekeeping as {@code MjolnirItem#inventoryTick} -- a copy a recall already superseded deletes itself, an
	 * unidentified one is given its identity, and the registry is told who is carrying it -- plus the binding rule:
	 * an unbound Stormbreaker binds to the first <b>worthy</b> player (a Thor) who carries it, provided they do not
	 * already have a Stormbreaker of their own. One bound to someone else never changes hands by being picked up;
	 * its owner can call it back out of the borrower's inventory, exactly like Mjolnir.
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
		UUID id = stack.get(ModDataComponents.HAMMER_ID);
		if (id == null) {
			registry.noteCarried(stack, player, selected);
			id = stack.get(ModDataComponents.HAMMER_ID);
		} else {
			// cheap: re-recorded when it changed hands (picked up, called home) or on the periodic refresh
			Optional<HammerRecord> record = registry.record(id);
			if (player.tickCount % 100 == 0 || record.isEmpty()
					|| record.get().placement() != HammerRecord.Placement.CARRIED
					|| !record.get().holder().equals(Optional.of(player.getUUID()))) {
				registry.noteCarried(stack, player, selected);
			}
		}
		if (player instanceof ServerPlayer serverPlayer && id != null) {
			tryBind(serverPlayer, stack, registry, id);
		}
	}

	/** The binding rule from {@link #inventoryTick}. Public for the GameTests. */
	public static void tryBind(ServerPlayer player, ItemStack stack, MjolnirRegistry registry, UUID id) {
		UUID owner = stack.get(ModDataComponents.BOUND_OWNER);
		UUID current = player.getAttachedOrElse(ModAttachments.BOUND_STORMBREAKER_ID, null);
		if (owner != null) {
			// already theirs but their own record of it was cleared (e.g. a revoke while it was out of reach): adopt it
			if (owner.equals(player.getUUID()) && current == null && Worthiness.isWorthy(player)) {
				player.setAttached(ModAttachments.BOUND_STORMBREAKER_ID, id);
			}
			return;
		}
		if (!Worthiness.isWorthy(player) || com.projecthero.mod.hulk.Hulk.isHulk(player)) {
			return;
		}
		if (current != null && !current.equals(id)) {
			Optional<HammerRecord> theirs = registry.record(current);
			if (theirs.isPresent() && theirs.get().isOwnedBy(player.getUUID())) {
				return; // one Stormbreaker each -- a second one stays unbound, free for another Thor
			}
		}
		String name = player.getGameProfile().getName();
		stack.set(ModDataComponents.BOUND_OWNER, player.getUUID());
		stack.set(ModDataComponents.BOUND_OWNER_NAME, name);
		registry.setOwner(stack, Optional.of(player.getUUID()), name);
		player.setAttached(ModAttachments.BOUND_STORMBREAKER_ID, id);
		ThorFeedback.stormbreakerBound(player);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.6f);
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
			// v0.14.20: Sneak + right-click opens the Bifrost screen. The one exception: sneak-clicking a block within
			// arm's reach with something in the off hand is left to vanilla, so a block can still be sneak-placed
			// from the off hand while holding the axe.
			if (!player.getOffhandItem().isEmpty() && Bifrost.targetsBlockInReach(player)) {
				return InteractionResultHolder.pass(stack);
			}
			if (player instanceof ServerPlayer serverPlayer) {
				Bifrost.sendScreen(serverPlayer, true);
			}
			return InteractionResultHolder.success(stack);
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
