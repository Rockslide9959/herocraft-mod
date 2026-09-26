package com.projecthero.mod.allmight;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.allmight.item.AllMightArmorItem;
import com.projecthero.mod.allmight.item.AllMightItems;
import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The craftable All Might costume (v0.12.36; v0.12.38: a custom two-slot locker). The two pieces -- the costume (chest) and the trousers (legs) -- are ordinary
 * craftable items. N in the Power Form opens the <em>costume locker</em>: a two-slot screen (costume + trousers) where the pieces are left
 * (the contents live on the persistent {@code ALL_MIGHT_LOCKER} attachment, so they ride through death and dimension
 * changes). Whenever the player transforms into the Power Form the locker's pieces are put on automatically, and changing
 * back takes them off into the locker again. Nobody starts with the costume.
 */
public final class AllMightSuit {
	public static final EquipmentSlot[] SLOTS = { EquipmentSlot.CHEST, EquipmentSlot.LEGS };

	private AllMightSuit() {
	}

	private static List<ItemStack> locker(Player player) {
		ItemContainerContents c = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_LOCKER, ItemContainerContents.EMPTY);
		net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(SLOTS.length, ItemStack.EMPTY);
		c.copyInto(items);
		return new ArrayList<>(items);
	}

	private static void saveLocker(Player player, List<ItemStack> stacks) {
		player.setAttached(ModAttachments.ALL_MIGHT_LOCKER, ItemContainerContents.fromItems(stacks));
	}

	private static boolean fits(int index, ItemStack stack) {
		return stack.getItem() instanceof AllMightArmorItem a && a.getType().getSlot() == SLOTS[index];
	}

	private static final EquipmentSlot[] ARMOUR_SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	/**
	 * v0.12.43: the Power Form can only wear the All Might armour. On transforming, every regular piece he is wearing tears off his
	 * body: it loses {@link AllMightConfig#ARMOUR_TEAR_DAMAGE} durability (and is destroyed if that is more than it has left), then it
	 * unequips itself and falls to the ground.
	 */
	public static void tearOffRegularArmour(ServerPlayer player) {
		boolean any = false;
		for (EquipmentSlot slot : ARMOUR_SLOTS) {
			ItemStack worn = player.getItemBySlot(slot);
			if (worn.isEmpty() || worn.getItem() instanceof AllMightArmorItem) {
				continue;
			}
			any = true;
			player.setItemSlot(slot, ItemStack.EMPTY);
			if (worn.isDamageableItem()) {
				int damage = worn.getDamageValue() + AllMightConfig.ARMOUR_TEAR_DAMAGE;
				if (damage >= worn.getMaxDamage()) {
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.9f, 1.0f);
					continue; // torn apart completely
				}
				worn.setDamageValue(damage);
			}
			net.minecraft.world.entity.item.ItemEntity dropped = player.drop(worn, false);
			if (dropped != null) {
				dropped.setPickUpDelay(60);
			}
		}
		if (any) {
			player.displayClientMessage(Component.translatable("message.projecthero.all_might.armour_torn").withStyle(ChatFormatting.RED), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
		}
	}

	/** v0.12.43: while in the Power Form, a regular piece that gets equipped is bounced straight back to the inventory (or dropped if it is full). */
	public static void bounceRegularArmour(ServerPlayer player) {
		for (EquipmentSlot slot : ARMOUR_SLOTS) {
			ItemStack worn = player.getItemBySlot(slot);
			if (worn.isEmpty() || worn.getItem() instanceof AllMightArmorItem) {
				continue;
			}
			ItemStack piece = worn.copy();
			player.setItemSlot(slot, ItemStack.EMPTY);
			give(player, piece);
			player.displayClientMessage(Component.translatable("message.projecthero.all_might.armour_refused", piece.getHoverName())
					.withStyle(ChatFormatting.RED), true);
		}
	}

	/** Power Form: put on whatever is in the locker (anything already worn in that slot is moved to the inventory). */
	public static void equip(ServerPlayer player) {
		List<ItemStack> locker = locker(player);
		boolean changed = false;
		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack piece = locker.get(i);
			if (piece.isEmpty()) {
				continue;
			}
			ItemStack current = player.getItemBySlot(SLOTS[i]);
			if (current.getItem() instanceof AllMightArmorItem) {
				continue; // already wearing one
			}
			if (!current.isEmpty()) {
				ItemStack displaced = current.copy();
				player.setItemSlot(SLOTS[i], ItemStack.EMPTY);
				give(player, displaced);
				player.displayClientMessage(Component.translatable("message.projecthero.all_might.armour_stowed", displaced.getHoverName()), true);
			}
			player.setItemSlot(SLOTS[i], piece);
			locker.set(i, ItemStack.EMPTY);
			changed = true;
		}
		if (changed) {
			saveLocker(player, locker);
		}
	}

	/** Base Form: the worn costume goes back into the locker (or the inventory if the locker slot is taken). */
	public static void strip(Player player) {
		List<ItemStack> locker = locker(player);
		boolean changed = false;
		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack worn = player.getItemBySlot(SLOTS[i]);
			if (!(worn.getItem() instanceof AllMightArmorItem)) {
				continue;
			}
			player.setItemSlot(SLOTS[i], ItemStack.EMPTY);
			if (locker.get(i).isEmpty()) {
				locker.set(i, worn.copy());
				changed = true;
			} else if (player instanceof ServerPlayer sp) {
				give(sp, worn.copy());
			}
		}
		if (changed) {
			saveLocker(player, locker);
		}
	}

	/** Losing the power: everything comes back to the player (worn pieces and the locker's contents). */
	public static void returnAll(ServerPlayer player) {
		strip(player);
		List<ItemStack> locker = locker(player);
		for (ItemStack s : locker) {
			if (!s.isEmpty()) {
				give(player, s.copy());
			}
		}
		saveLocker(player, new ArrayList<>(List.of(ItemStack.EMPTY, ItemStack.EMPTY)));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/** N in the Power Form: the costume locker. */
	public static void openLocker(ServerPlayer player) {
		if (!AllMight.hasPower(player)) {
			return;
		}
		if (!AllMight.isFullPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.all_might.locker_power_form")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		// a piece he is already wearing goes into the locker so it can be seen and swapped
		strip(player);
		List<ItemStack> locker = locker(player);
		SimpleContainer container = new SimpleContainer(SLOTS.length) {
			@Override
			public boolean stillValid(Player p) {
				return p == player && AllMight.isFullPower(player);
			}

			@Override
			public boolean canPlaceItem(int slot, ItemStack stack) {
				return slot < SLOTS.length && fits(slot, stack);
			}

			@Override
			public void setChanged() {
				super.setChanged();
				List<ItemStack> now = new ArrayList<>(SLOTS.length);
				for (int i = 0; i < SLOTS.length; i++) {
					now.add(getItem(i).copy());
				}
				saveLocker(player, now);
			}
		};
		for (int i = 0; i < SLOTS.length; i++) {
			container.setItem(i, locker.get(i));
		}
		player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<Integer>() {
			@Override
			public Integer getScreenOpeningData(ServerPlayer p) {
				return 0;
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable("container.projecthero.all_might_locker");
			}

			@Override
			public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player p) {
				return new AllMightLockerMenu(syncId, inv, container);
			}
		});
		player.playNotifySound(SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8f, 0.9f);
	}

	public static boolean wearing(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (player.getItemBySlot(slot).getItem() instanceof AllMightArmorItem) {
				return true;
			}
		}
		return false;
	}

	/** Recipe outputs are the only source of pieces. */
	public static boolean isPiece(ItemStack stack) {
		return stack.is(AllMightItems.CHESTPLATE) || stack.is(AllMightItems.LEGGINGS);
	}
}
