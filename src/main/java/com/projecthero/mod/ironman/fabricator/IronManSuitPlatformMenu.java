package com.projecthero.mod.ironman.fabricator;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManArmorItem;

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
		this(syncId, playerInv, resolve(playerInv, pos), new SimpleContainerData(3),
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

				@Override
				public boolean mayPickup(Player player) {
					// v0.15.8: the rack is locked while the Mark 5 folds into its case (v0.15.9: or unfolds out of it)
					return !(container instanceof IronManSuitPlatformBlockEntity be && be.locked());
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

	/** v0.16.7 (user) Era I: is a complete Stark Gantry in this rack's range (synced from the block entity)? */
	public boolean gantryInRange() {
		IronManSuitPlatformBlockEntity be = liveBE();
		return be != null && be.gantryInRange();
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
					? com.projecthero.mod.ironman.IronManEnergy.MAX_INTEGRITY
					: com.projecthero.mod.ironman.IronManEnergy.maxIntegrity(id);
			return 100f * be.suitIntegrity() / Math.max(1f, max);
		}
		return suitIntegrity();
	}


	// v0.15.4: no more DEPLOY / RETRIEVE buttons -- suiting up and down is done on a Stark Gantry Floor (StarkGantry).

	/**
	 * v0.15.4: menu button id of the old side "Bracelets" button. v0.16.7 (user): RETIRED -- the platform no longer hands
	 * out Colantotte Bracelets (the Era II Mk VII / Mk VIII pack into their backpacks instead); the id stays reserved so
	 * an old client's press is simply refused. The bracelets item itself stays registered for old saves.
	 */
	public static final int BRACELETS_BUTTON = 2;

	/** v0.16.7 (user): never -- the Bracelets tab is gone from the platform (see {@link #BRACELETS_BUTTON}). */
	public boolean offersBracelets() {
		return false;
	}

	/** v0.15.6: menu button id of the side pack tab (v0.16.7: any Era II suit with a carried form). */
	public static final int SUITCASE_BUTTON = 3;

	/** v0.16.7 (user): the racked suit's id (the block entity's, else read off the synced slots), or null. */
	public String rackedSuitId() {
		IronManSuitPlatformBlockEntity be = liveBE();
		String id = be != null ? be.storedSuitId() : null;
		if (id == null) {
			for (int i = 0; i < 4 && id == null; i++) {
				if (getSlot(i).getItem().getItem() instanceof IronManArmorItem a) {
					id = a.suitId();
				}
			}
		}
		return id;
	}

	/**
	 * v0.16.7 (user): the form the racked suit packs into (Mk VI briefcase, Mk VII backpack, Mk VIII thruster pack, Mk IX
	 * heat case), or null -- the pack tab is shown only then. Never the Hulkbuster (its Uplink has its own path).
	 */
	public com.projecthero.mod.ironman.suit.PortableForm packForm() {
		IronManSuitPlatformBlockEntity be = liveBE();
		if (be != null && be.locked()) {
			return null; // already folding / unfolding
		}
		return IronManSuitPlatformBlockEntity.platformForm(rackedSuitId());
	}

	/** v0.15.6: is a suit that packs racked here, so the pack tab is offered? (v0.16.7: any of the four Era II forms.) */
	public boolean offersSuitcase() {
		return packForm() != null;
	}

	/** v0.16.7 (user): menu button id of the side "Uplink" tab (only while the whole Hulkbuster is racked). */
	public static final int UPLINK_BUTTON = 7;

	/** v0.16.7 (user): is the whole Hulkbuster racked here, so the Uplink tab is offered? */
	public boolean offersUplink() {
		IronManSuitPlatformBlockEntity be = liveBE();
		if (be != null) {
			return be.holdsHulkbuster();
		}
		for (int i = 0; i < 4; i++) {
			if (!(getSlot(i).getItem().getItem() instanceof IronManArmorItem a)
					|| !com.projecthero.mod.ironman.crate.HulkbusterCrate.SUIT_ID.equals(a.suitId())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (player instanceof ServerPlayer sp && id == UPLINK_BUTTON) {
			// v0.16.7 (user): the racked Hulkbuster goes into its Uplink, ready to call down in its drop crate
			boolean packed = access.evaluate((level, pos) -> level.getBlockEntity(pos) instanceof IronManSuitPlatformBlockEntity be
					&& be.packUplink(sp), false);
			if (packed) {
				com.projecthero.mod.ironman.IronManSounds.play(sp, com.projecthero.mod.ironman.IronManSounds.SERVO, 0.8f, 0.9f);
			} else {
				sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.suit_platform.uplink_cant")
						.withStyle(net.minecraft.ChatFormatting.GOLD), true);
			}
			return packed;
		}
		if (player instanceof ServerPlayer sp && id == SUITCASE_BUTTON) {
			// v0.15.6: fold the racked suit back into its packed form and hand it over (v0.16.7: any Era II form)
			com.projecthero.mod.ironman.suit.PortableForm form = packForm();
			boolean packed = form != null && access.evaluate((level, pos) -> level.getBlockEntity(pos) instanceof IronManSuitPlatformBlockEntity be
					&& be.packSuitcase(sp), false);
			net.minecraft.network.chat.Component msg = packed
					? net.minecraft.network.chat.Component.translatable("message.projecthero.suit_platform.suitcase_packed",
							net.minecraft.network.chat.Component.translatable(
									com.projecthero.mod.ironman.suit.IronManSuits.byId(form.suitId()).nameKey()),
							new ItemStack(form.packedItem().get()).getHoverName())
					: net.minecraft.network.chat.Component.translatable("message.projecthero.suit_platform.suitcase_refused");
			sp.displayClientMessage(msg.copy().withStyle(packed ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GOLD), true);
			if (packed) {
				com.projecthero.mod.ironman.IronManSounds.play(sp, com.projecthero.mod.ironman.IronManSounds.SERVO, 0.8f, 1.2f);
				sp.closeContainer(); // v0.15.8: step back and watch the suit fold itself up into its case
			}
			return packed;
		}
		// v0.16.7 (user): BRACELETS_BUTTON (2) is retired -- the platform hands out no bracelets any more
		return false;
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
		} else if (IronManSuitPlatformBlockEntity.platformForm(stack) != null) {
			// v0.14.30: shift-clicking the Mark 5 Suitcase unfolds it onto the rack (v0.16.7: any Era II packed item)
			if (container instanceof IronManSuitPlatformBlockEntity be && be.storeSuitcase(player, stack)) {
				slot.setChanged();
			}
			return ItemStack.EMPTY;
		} else if (stack.is(com.projecthero.mod.ironman.item.IronManItems.HULKBUSTER_UPLINK)) {
			// v0.16.7 (user): shift-clicking a loaded Uplink racks the Hulkbuster (the Uplink stays, emptied)
			if (container instanceof IronManSuitPlatformBlockEntity be && be.getLevel() != null && !be.getLevel().isClientSide()) {
				IronManSuitPlatformBlock.rackUplink(be.getLevel(), be.getBlockPos(), be, player, stack);
				slot.setChanged();
			}
			return ItemStack.EMPTY;
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
