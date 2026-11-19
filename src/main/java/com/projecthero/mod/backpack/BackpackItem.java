package com.projecthero.mod.backpack;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * v0.17.5 backpacks: a portable chest. Right-click with it in either hand (or press Open Backpack, {@code B}) to open a chest
 * screen sized to its tier. The contents live on the stack in the vanilla {@code minecraft:container} component (the one shulker
 * boxes use), so a backpack keeps everything when dropped, put in a chest or upgraded. It never goes inside another backpack, a
 * shulker box or a bundle ({@link #canFitInsideContainerItems}), and a shulker box never goes inside it ({@link BackpackMenu}).
 * Destroyed in lava or a cactus (every tier but Netherite) it spills its contents like a shulker box.
 */
public class BackpackItem extends Item {
	private static final int TOOLTIP_ENTRIES = 5;

	private final BackpackTier tier;

	public BackpackItem(BackpackTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public BackpackTier tier() {
		return tier;
	}

	public static boolean isBackpack(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BackpackItem;
	}

	public static BackpackTier tierOf(ItemStack stack) {
		return stack.getItem() instanceof BackpackItem b ? b.tier : null;
	}

	/** The canvas colour: the dye if it has been dyed, else the tier's own colour. RGB. */
	public static int color(ItemStack stack) {
		BackpackTier t = tierOf(stack);
		return DyedItemColor.getOrDefault(stack, t == null ? 0xFFFFFF : t.defaultColor()) & 0xFFFFFF;
	}

	// ------------------------------------------------------------------ contents

	/** The contents as exactly {@code tier.slots()} stacks (copies). */
	public static NonNullList<ItemStack> readContents(ItemStack backpack) {
		BackpackTier t = tierOf(backpack);
		NonNullList<ItemStack> items = NonNullList.withSize(t == null ? 0 : t.slots(), ItemStack.EMPTY);
		ItemContainerContents c = backpack.get(DataComponents.CONTAINER);
		if (c != null && !items.isEmpty()) {
			c.copyInto(items);
		}
		return items;
	}

	/** Writes {@code items} onto the stack (only if it changed, so an unchanged backpack is not re-synced). */
	public static void writeContents(ItemStack backpack, List<ItemStack> items) {
		ItemContainerContents now = ItemContainerContents.fromItems(items);
		ItemContainerContents was = backpack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		if (!now.equals(was)) {
			backpack.set(DataComponents.CONTAINER, now);
		}
	}

	public static int usedSlots(ItemStack backpack) {
		ItemContainerContents c = backpack.get(DataComponents.CONTAINER);
		return c == null ? 0 : (int) c.nonEmptyStream().count();
	}

	/** True for anything that may not go into a backpack: other backpacks, shulker boxes (and anything else that says so). */
	public static boolean blockedInside(ItemStack stack) {
		return !stack.isEmpty() && !stack.getItem().canFitInsideContainerItems();
	}

	// ------------------------------------------------------------------ opening

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
			open(sp, slot);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/** Opens the backpack in inventory slot {@code invSlot} (0-35 main, 40 offhand). False if there is none there. */
	public static boolean open(ServerPlayer player, int invSlot) {
		ItemStack stack = player.getInventory().getItem(invSlot);
		if (!isBackpack(stack) || player.isSpectator()) {
			return false;
		}
		player.openMenu(new BackpackMenu.Provider(invSlot, stack));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS,
				0.6f, 1.15f);
		return true;
	}

	// ------------------------------------------------------------------ item rules

	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	@Override
	public void onDestroyed(ItemEntity entity) {
		ItemContainerContents c = entity.getItem().get(DataComponents.CONTAINER);
		if (c != null) {
			entity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
			ItemUtils.onContainerDestroyed(entity, c.nonEmptyItemsCopy());
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.backpack.slots", usedSlots(stack), tier.slots()).withStyle(ChatFormatting.GOLD));
		ItemContainerContents c = stack.get(DataComponents.CONTAINER);
		if (c != null) {
			List<ItemStack> items = new ArrayList<>();
			c.nonEmptyItems().forEach(items::add);
			for (int i = 0; i < Math.min(TOOLTIP_ENTRIES, items.size()); i++) {
				ItemStack s = items.get(i);
				tooltip.add(Component.translatable("item.projecthero.backpack.entry", s.getHoverName(), s.getCount()).withStyle(ChatFormatting.GRAY));
			}
			if (items.size() > TOOLTIP_ENTRIES) {
				tooltip.add(Component.translatable("item.projecthero.backpack.more", items.size() - TOOLTIP_ENTRIES)
						.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		}
		if (tier.fireproof()) {
			tooltip.add(Component.translatable("item.projecthero.backpack.fireproof").withStyle(ChatFormatting.DARK_PURPLE));
		}
		tooltip.add(Component.translatable("item.projecthero.backpack.hint", Component.keybind("key.projecthero.open_backpack")
				.withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.projecthero." + tier.id() + "_backpack.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
