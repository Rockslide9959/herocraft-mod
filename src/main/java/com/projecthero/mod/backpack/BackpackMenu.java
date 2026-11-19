package com.projecthero.mod.backpack;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5 backpacks: the open backpack -- a chest-shaped menu, {@code tier.rows()} rows over the player's inventory.
 *
 * <h2>Dupe guard</h2>
 * The menu is a <em>view</em> onto one {@link ItemStack} instance: the stack sitting in inventory slot {@link #invSlot} when it was
 * opened. Every change is written straight back onto that stack ({@link BackpackContainer}), nothing is ever held in the menu and
 * nothing is handed back when it closes. On the server:
 * <ul>
 * <li>{@link #stillValid} is true only while that exact instance is still in that slot, so the moment the backpack is dropped,
 * moved, cleared, swapped or the player dies, vanilla closes the screen on the next tick;</li>
 * <li>until then every slot refuses to give or take ({@link #live}) and the container refuses to write, so there is no window in
 * which the old contents can be taken out while the moved stack keeps a copy;</li>
 * <li>the backpack's own slot in the inventory rows is locked: no picking up, no shift-click, no number-key or F swap, no Q.</li>
 * </ul>
 */
public class BackpackMenu extends AbstractContainerMenu {
	/** What the client needs to build the matching menu: which inventory slot holds the backpack, and its tier. */
	public record OpenData(int invSlot, int tier) {
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenData::invSlot,
				ByteBufCodecs.VAR_INT, OpenData::tier, OpenData::new);
	}

	private final BackpackTier tier;
	private final int invSlot;
	private final Player player;
	/** The backpack being viewed (server only; null on the client, which just mirrors the slots). */
	private final ItemStack backpack;
	private final Container container;

	/** Client side, from the screen-opening packet. */
	public BackpackMenu(int syncId, Inventory inventory, OpenData data) {
		this(syncId, inventory, data.invSlot(), BackpackTier.byOrdinal(data.tier()), null);
	}

	/** Server side (and gametests): a live view onto {@code backpack}, which must be the stack in {@code invSlot}. */
	public BackpackMenu(int syncId, Inventory inventory, int invSlot, BackpackTier tier, ItemStack backpack) {
		super(BackpackMod.MENU, syncId);
		this.tier = tier;
		this.invSlot = invSlot;
		this.player = inventory.player;
		this.backpack = backpack;
		this.container = backpack == null ? new SimpleContainer(tier.slots()) : new BackpackContainer(this, backpack, tier.slots());
		int rows = tier.rows();
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < 9; c++) {
				addSlot(new BackpackSlot(container, c + r * 9, 8 + c * 18, 18 + r * 18));
			}
		}
		int yOff = (rows - 4) * 18;
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 9; c++) {
				int index = c + r * 9 + 9;
				addSlot(index == invSlot ? new LockedSlot(inventory, index, 8 + c * 18, 103 + r * 18 + yOff)
						: new Slot(inventory, index, 8 + c * 18, 103 + r * 18 + yOff));
			}
		}
		for (int c = 0; c < 9; c++) {
			addSlot(c == invSlot ? new LockedSlot(inventory, c, 8 + c * 18, 161 + yOff) : new Slot(inventory, c, 8 + c * 18, 161 + yOff));
		}
	}

	public BackpackTier tier() {
		return tier;
	}

	public int invSlot() {
		return invSlot;
	}

	public int rows() {
		return tier.rows();
	}

	/** The backpack's own slots (the rest are the player's inventory). */
	public Container container() {
		return container;
	}

	/** Server: the backpack is still exactly where it was opened from. Always true on the client (the server decides). */
	public boolean live() {
		if (backpack == null) {
			return true;
		}
		return player.isAlive() && !player.isRemoved() && player.getInventory().getItem(invSlot) == backpack && BackpackItem.isBackpack(backpack);
	}

	@Override
	public boolean stillValid(Player p) {
		return live();
	}

	@Override
	public void clicked(int slotId, int button, ClickType type, Player p) {
		if (!live()) {
			return;
		}
		if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof LockedSlot && type != ClickType.CLONE) {
			return; // the backpack itself: never picked up, thrown, swapped or shift-clicked while open
		}
		if (type == ClickType.SWAP && button == invSlot) {
			return; // number key / F would pull the backpack out of its slot
		}
		super.clicked(slotId, button, type, p);
	}

	@Override
	public ItemStack quickMoveStack(Player p, int index) {
		if (!live()) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(index);
		if (slot instanceof LockedSlot || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		int size = tier.slots();
		if (index < size) {
			if (!moveItemStackTo(stack, size, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			if (BackpackItem.blockedInside(stack) || !moveItemStackTo(stack, 0, size, false)) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public void broadcastChanges() {
		// catch-all: anything that changed a slot's stack in place without calling setChanged still reaches the item
		if (container instanceof BackpackContainer bc) {
			bc.save();
		}
		super.broadcastChanges();
	}

	@Override
	public void removed(Player p) {
		if (container instanceof BackpackContainer bc) {
			bc.save();
		}
		super.removed(p);
	}

	// ------------------------------------------------------------------ slots + container

	/** A backpack slot: no backpacks or shulker boxes, and nothing in or out once the backpack has moved. */
	private final class BackpackSlot extends Slot {
		BackpackSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return live() && !BackpackItem.blockedInside(stack);
		}

		@Override
		public boolean mayPickup(Player p) {
			return live();
		}
	}

	/** The backpack's own slot in the inventory rows: shown, but frozen. */
	private static final class LockedSlot extends Slot {
		LockedSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player p) {
			return false;
		}
	}

	/** The slots, loaded from and written straight back to the backpack stack (only while the menu is live). */
	static final class BackpackContainer extends SimpleContainer {
		private final BackpackMenu menu;
		private final ItemStack backpack;
		private boolean loading;

		BackpackContainer(BackpackMenu menu, ItemStack backpack, int size) {
			super(size);
			this.menu = menu;
			this.backpack = backpack;
			loading = true;
			var items = BackpackItem.readContents(backpack);
			for (int i = 0; i < Math.min(size, items.size()); i++) {
				setItem(i, items.get(i));
			}
			loading = false;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (!loading) {
				save();
			}
		}

		void save() {
			if (menu.live()) {
				BackpackItem.writeContents(backpack, getItems());
			}
		}

		@Override
		public boolean stillValid(Player p) {
			return menu.live();
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return !BackpackItem.blockedInside(stack);
		}
	}

	/** Opens the menu for the stack in {@code invSlot}; the title is the backpack's name (an anvil name shows). */
	record Provider(int invSlot, ItemStack backpack) implements ExtendedScreenHandlerFactory<OpenData> {
		@Override
		public OpenData getScreenOpeningData(ServerPlayer p) {
			return new OpenData(invSlot, BackpackItem.tierOf(backpack).ordinal());
		}

		@Override
		public Component getDisplayName() {
			return backpack.getHoverName();
		}

		@Override
		public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player p) {
			return new BackpackMenu(syncId, inventory, invSlot, BackpackItem.tierOf(backpack), backpack);
		}
	}
}
