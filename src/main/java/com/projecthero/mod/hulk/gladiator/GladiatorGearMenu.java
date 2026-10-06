package com.projecthero.mod.hulk.gladiator;

import com.projecthero.mod.hulk.Hulk;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.3: the Gladiator Gear screen's menu -- seven gear slots (each takes only its own piece) plus the player's
 * inventory and hotbar. On the server the gear slots read and write the
 * {@link com.projecthero.mod.attachment.ModAttachments#GLADIATOR_GEAR} attachment directly ({@link GearContainer}), so a
 * piece only ever lives in one place; the client mirrors it in a plain container the vanilla menu sync fills.
 *
 * <p>Locked while he is the Hulk: nothing can be put in or taken out, and the menu stops being valid (vanilla closes it)
 * the moment he changes.
 */
public class GladiatorGearMenu extends AbstractContainerMenu {
	public static final int GEAR_START = 0;
	public static final int GEAR_END = GladiatorGear.SLOTS;
	private static final int INV_START = GEAR_END;
	private static final int INV_END = INV_START + 36;

	private final Player player;

	/** Client (and the menu-type factory). */
	public GladiatorGearMenu(int syncId, Inventory playerInv) {
		this(syncId, playerInv, playerInv.player.level().isClientSide ? new SimpleContainer(GladiatorGear.SLOTS)
				: new GearContainer(playerInv.player));
	}

	private GladiatorGearMenu(int syncId, Inventory playerInv, Container gear) {
		super(GladiatorGear.MENU, syncId);
		this.player = playerInv.player;
		for (int i = 0; i < GladiatorGear.SLOTS; i++) {
			final int index = i;
			addSlot(new Slot(gear, i, GladiatorGearLayout.slotX(i), GladiatorGearLayout.slotY(i)) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return !locked() && GladiatorGear.accepts(index, stack);
				}

				@Override
				public boolean mayPickup(Player who) {
					return !locked();
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, GladiatorGearLayout.INV_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInv, col, 8 + col * 18, GladiatorGearLayout.HOTBAR_Y));
		}
	}

	/** The gear is locked on while he is the Hulk. */
	public boolean locked() {
		return Hulk.isHulk(player);
	}

	@Override
	public ItemStack quickMoveStack(Player who, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem() || locked()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < GEAR_END) {
			ItemStack moving = stack.copy(); // never shrink the attachment's own stack in place
			if (!moveItemStackTo(moving, INV_START, INV_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.setByPlayer(moving.isEmpty() ? ItemStack.EMPTY : moving);
		} else {
			int target = stack.getItem() instanceof GladiatorGearItem piece ? piece.slot() : -1;
			if (target < 0 || slots.get(target).hasItem() || !moveItemStackTo(stack, target, target + 1, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player who) {
		return who.isAlive() && who == player && !locked();
	}

	/** The server-side gear slots: a seven-item view of the player's Gladiator Gear attachment. */
	static final class GearContainer implements Container {
		private final Player player;

		GearContainer(Player player) {
			this.player = player;
		}

		@Override
		public int getContainerSize() {
			return GladiatorGear.SLOTS;
		}

		@Override
		public boolean isEmpty() {
			return GladiatorGear.equippedCount(player) == 0;
		}

		@Override
		public ItemStack getItem(int slot) {
			return GladiatorGear.get(player, slot);
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			if (amount <= 0 || slot < 0 || slot >= GladiatorGear.SLOTS) {
				return ItemStack.EMPTY;
			}
			ItemStack out = GladiatorGear.get(player, slot).copy();
			GladiatorGear.set(player, slot, ItemStack.EMPTY);
			return out;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return removeItem(slot, 1);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			GladiatorGear.set(player, slot, stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(Player who) {
			return who == player;
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return GladiatorGear.accepts(slot, stack);
		}

		@Override
		public void clearContent() {
			for (int i = 0; i < GladiatorGear.SLOTS; i++) {
				GladiatorGear.set(player, i, ItemStack.EMPTY);
			}
		}
	}
}
