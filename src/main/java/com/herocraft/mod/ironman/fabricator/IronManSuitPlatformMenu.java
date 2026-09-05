package com.herocraft.mod.ironman.fabricator;

import com.herocraft.mod.ironman.IronManBlocks;
import com.herocraft.mod.ironman.TonyStark;
import com.herocraft.mod.ironman.item.IronManArmorItem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The Iron Man Suit Platform menu: four armour slots (helmet / chest / legs / boots), an energy
 * readout, and two menu buttons -- DEPLOY (id 0) puts the stored suit on you + recharges + repairs
 * it, RETRIEVE (id 1) pulls your worn suit back onto the platform.
 */
public class IronManSuitPlatformMenu extends AbstractContainerMenu {
	private final Container container;
	private final ContainerData data;
	private final ContainerLevelAccess access;

	public IronManSuitPlatformMenu(int syncId, Inventory playerInv, IronManSuitPlatformBlockEntity be, ContainerData data) {
		this(syncId, playerInv, be, data, ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()));
	}

	public IronManSuitPlatformMenu(int syncId, Inventory playerInv, BlockPos pos) {
		this(syncId, playerInv, resolve(playerInv, pos), new SimpleContainerData(4),
				ContainerLevelAccess.create(playerInv.player.level(), pos));
	}

	private IronManSuitPlatformMenu(int syncId, Inventory playerInv, Container container, ContainerData data,
			ContainerLevelAccess access) {
		super(IronManBlocks.SUIT_PLATFORM_MENU, syncId);
		this.container = container;
		this.data = data;
		this.access = access;
		checkContainerSize(container, IronManSuitPlatformBlockEntity.SIZE);

		for (int i = 0; i < 4; i++) {
			final int slotIdx = i;
			addSlot(new Slot(container, i, 53 + i * 20, 32) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return container.canPlaceItem(slotIdx, stack);
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 124 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInv, col, 8 + col * 18, 182));
		}
		addDataSlots(data);
	}

	private static Container resolve(Inventory playerInv, BlockPos pos) {
		BlockEntity be = playerInv.player.level().getBlockEntity(pos);
		return be instanceof IronManSuitPlatformBlockEntity p ? p : new SimpleContainer(IronManSuitPlatformBlockEntity.SIZE);
	}

	/** The stored suit's carried charge (spec "changes 9" -- the platform shows the armour's real energy). */
	public int suitEnergy() {
		return data.get(0) * 8;
	}

	public int suitCapacity() {
		return data.get(1) * 8;
	}

	/** Stored suit integrity, 0..100. */
	public int suitIntegrity() {
		return data.get(2);
	}

	/** The platform's own Reactor-Core reserve it feeds into the suit. */
	public int reserveEnergy() {
		return data.get(3) * 8;
	}

	// ---- exact (un-quantised) readouts (v0.6.6) ----
	//
	// The four values above travel through a vanilla ContainerData, whose sync packet is 16-bit, so
	// they are stored pre-divided by 8 and multiplied back -- a fresh suit could read 99% charge purely
	// from that rounding, and integrity arrives already collapsed to a whole percent. When this menu was
	// opened from a real Suit Platform its BlockEntity is fully synced to the client (getUpdateTag), so
	// we read the exact float straight off it and only fall back to the quantised path when it isn't.

	private IronManSuitPlatformBlockEntity liveBE() {
		return container instanceof IronManSuitPlatformBlockEntity be ? be : null;
	}

	public boolean hasStoredSuit() {
		IronManSuitPlatformBlockEntity be = liveBE();
		if (be != null) {
			return be.storedSuitId() != null;
		}
		return getSlot(0).hasItem() || getSlot(1).hasItem() || getSlot(2).hasItem() || getSlot(3).hasItem();
	}

	/** Stored suit charge, exact energy units. */
	public float suitEnergyExact() {
		IronManSuitPlatformBlockEntity be = liveBE();
		return be != null ? be.suitEnergy() : suitEnergy();
	}

	/** Stored suit energy capacity for its mark, exact energy units. */
	public float suitCapacityExact() {
		IronManSuitPlatformBlockEntity be = liveBE();
		return be != null ? be.suitCapacity() : Math.max(1, suitCapacity());
	}

	/** Stored suit integrity as a percentage 0..100, full float precision. */
	public float suitIntegrityPercentExact() {
		IronManSuitPlatformBlockEntity be = liveBE();
		if (be != null) {
			String id = be.storedSuitId();
			float max = id == null
					? com.herocraft.mod.ironman.IronManEnergy.MAX_INTEGRITY
					: com.herocraft.mod.ironman.IronManEnergy.maxIntegrity(id);
			return 100f * be.suitIntegrity() / Math.max(1f, max);
		}
		return suitIntegrity();
	}

	/** The platform's own Reactor-Core reserve, exact energy units. */
	public float reserveEnergyExact() {
		IronManSuitPlatformBlockEntity be = liveBE();
		return be != null ? be.storedEnergy() : reserveEnergy();
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (!(player instanceof ServerPlayer sp)) {
			return false;
		}
		return access.evaluate((level, pos) -> {
			if (!(level.getBlockEntity(pos) instanceof IronManSuitPlatformBlockEntity be)) {
				return false;
			}
			boolean ok = id == 0 ? be.deployTo(sp) : id == 1 ? be.retrieveFrom(sp) : false;
			if (ok) {
				broadcastChanges();
			}
			return ok;
		}, false);
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, IronManBlocks.IRON_MAN_SUIT_PLATFORM) && TonyStark.hasPower(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		int platformSlots = IronManSuitPlatformBlockEntity.SIZE;
		if (index < platformSlots) {
			if (!moveItemStackTo(stack, platformSlots, platformSlots + 36, true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.getItem() instanceof IronManArmorItem) {
			if (!moveItemStackTo(stack, 0, platformSlots, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}
}
