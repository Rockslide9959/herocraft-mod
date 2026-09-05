package com.herocraft.mod.ironman.fabricator;

import com.herocraft.mod.ironman.IronManBlocks;
import com.herocraft.mod.ironman.item.BlueprintItem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The Stark Fabricator container menu: 9 component inputs, 1 blueprint slot, 1 output slot, plus the
 * player inventory. Four {@link ContainerData} ints (energy, maxEnergy, progress, maxProgress) sync
 * the FABRICATE bar and ENERGY bar to the client.
 *
 * <p>{@link #stillValid} re-checks the Tony Stark power server-side every tick the menu is open, so
 * closing the loophole is not just "don't open the screen" -- the container itself refuses a
 * non-Tony-Stark player.
 */
public class StarkFabricatorMenu extends AbstractContainerMenu {
	private final Container container;
	private final ContainerData data;
	private final ContainerLevelAccess access;

	/** Server-side. */
	public StarkFabricatorMenu(int syncId, Inventory playerInv, StarkFabricatorBlockEntity be, ContainerData data) {
		this(syncId, playerInv, be, data,
				ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()));
	}

	/** Client-side (from the extended screen handler type, given the block pos). */
	public StarkFabricatorMenu(int syncId, Inventory playerInv, BlockPos pos) {
		this(syncId, playerInv, resolve(playerInv, pos), new net.minecraft.world.inventory.SimpleContainerData(5),
				ContainerLevelAccess.create(playerInv.player.level(), pos));
	}

	private StarkFabricatorMenu(int syncId, Inventory playerInv, Container container, ContainerData data,
			ContainerLevelAccess access) {
		super(IronManBlocks.STARK_FABRICATOR_MENU, syncId);
		this.container = container;
		this.data = data;
		this.access = access;
		checkContainerSize(container, StarkFabricatorBlockEntity.SIZE);

		// 3x3 component inputs (top-left cluster)
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				addSlot(new Slot(container, row * 3 + col, 44 + col * 18, 17 + row * 18));
			}
		}
		// blueprint slot (left of the grid)
		addSlot(new Slot(container, StarkFabricatorBlockEntity.BLUEPRINT_SLOT, 12, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof BlueprintItem;
			}
		});
		// output slot (right of the grid)
		addSlot(new Slot(container, StarkFabricatorBlockEntity.OUTPUT_SLOT, 120, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});

		// player inventory (pushed down so the fabricator's energy / status readouts + the "changes 18"
		// per-piece button band have their own space -- see StarkFabricatorScreen; imageHeight matches)
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 164 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInv, col, 8 + col * 18, 224));
		}

		addDataSlots(data);
	}

	private static Container resolve(Inventory playerInv, BlockPos pos) {
		BlockEntity be = playerInv.player.level().getBlockEntity(pos);
		return be instanceof StarkFabricatorBlockEntity fab ? fab : new SimpleContainer(StarkFabricatorBlockEntity.SIZE);
	}

	public int energy() {
		return data.get(0) * StarkFabricatorBlockEntity.ENERGY_SCALE;
	}

	public int maxEnergy() {
		return data.get(1) * StarkFabricatorBlockEntity.ENERGY_SCALE;
	}

	public int progress() {
		return data.get(2);
	}

	public int maxProgress() {
		return data.get(3);
	}

	/** "changes 18": 0 = none, 1 = helmet, 2 = chestplate, 3 = leggings, 4 = boots. */
	public int selectedPiece() {
		return data.get(4);
	}

	/** The stack in the blueprint slot (menu slot 9 -> container slot {@link StarkFabricatorBlockEntity#BLUEPRINT_SLOT}). */
	public ItemStack blueprintStack() {
		return slots.get(9).getItem();
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		// ids 1..4 pick a suit piece to fabricate; the same id again clears the pick.
		if (id >= 1 && id <= 4 && container instanceof StarkFabricatorBlockEntity fab) {
			fab.setSelectedPiece(fab.selectedPiece() == id ? 0 : id);
			return true;
		}
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, IronManBlocks.STARK_FABRICATOR)
				&& com.herocraft.mod.ironman.TonyStark.hasPower(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return result;
		}
		ItemStack stack = slot.getItem();
		result = stack.copy();

		int fabSlots = StarkFabricatorBlockEntity.SIZE; // 0..10
		int invStart = fabSlots;
		int invEnd = fabSlots + 36;

		if (index < fabSlots) {
			// fabricator -> player inventory
			if (!moveItemStackTo(stack, invStart, invEnd, true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// player inventory -> fabricator (blueprint to blueprint slot, else inputs)
			if (stack.getItem() instanceof BlueprintItem) {
				if (!moveItemStackTo(stack, StarkFabricatorBlockEntity.BLUEPRINT_SLOT,
						StarkFabricatorBlockEntity.BLUEPRINT_SLOT + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (!moveItemStackTo(stack, 0, 9, false)) {
				return ItemStack.EMPTY;
			}
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return result;
	}
}
