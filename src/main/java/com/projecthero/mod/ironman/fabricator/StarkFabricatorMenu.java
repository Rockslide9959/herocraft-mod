package com.projecthero.mod.ironman.fabricator;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.item.BlueprintItem;

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
 * The Stark Workbench (ex-Fabricator) container menu: 9 component inputs, 1 blueprint slot, 1 output slot, plus the
 * player inventory. Three {@link ContainerData} ints (progress, maxProgress, the selected piece) sync the progress
 * bar to the client. v0.17.2 (user): no energy; nothing fabricates until the Fabricate button
 * ({@link StarkWorkbench#FABRICATE_BUTTON} / {@link StarkWorkbench#BUILD_BASE}), and Cancel stops it.
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
		this(syncId, playerInv, resolve(playerInv, pos), new net.minecraft.world.inventory.SimpleContainerData(3),
				ContainerLevelAccess.create(playerInv.player.level(), pos));
	}

	private StarkFabricatorMenu(int syncId, Inventory playerInv, Container container, ContainerData data,
			ContainerLevelAccess access) {
		super(IronManBlocks.STARK_FABRICATOR_MENU, syncId);
		this.container = container;
		this.data = data;
		this.access = access;
		checkContainerSize(container, StarkFabricatorBlockEntity.SIZE);

		// v0.16.7 (user): the Stark Workbench screen -- the machine slots sit bottom-right beside the player inventory
		// (IronManUiLayout.FAB_SLOTS); the suit list / hologram / stats / recipe live above them
		int[][] at = com.projecthero.mod.ironman.ui.IronManUiLayout.FAB_SLOTS;
		// 3x3 component inputs
		for (int i = 0; i < 9; i++) {
			addSlot(new Slot(container, i, at[i][0], at[i][1]));
		}
		// blueprint slot (left of the grid)
		addSlot(new Slot(container, StarkFabricatorBlockEntity.BLUEPRINT_SLOT, at[9][0], at[9][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof BlueprintItem;
			}
		});
		// output slot (right of the grid)
		addSlot(new Slot(container, StarkFabricatorBlockEntity.OUTPUT_SLOT, at[10][0], at[10][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});

		// player inventory (bottom-left; v0.16.7 (user): FAB_INV_Y / FAB_HOTBAR_Y are the Workbench layout)
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18,
						com.projecthero.mod.ironman.ui.IronManUiLayout.FAB_INV_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInv, col, 8 + col * 18, com.projecthero.mod.ironman.ui.IronManUiLayout.FAB_HOTBAR_Y));
		}

		addDataSlots(data);
	}

	private static Container resolve(Inventory playerInv, BlockPos pos) {
		BlockEntity be = playerInv.player.level().getBlockEntity(pos);
		return be instanceof StarkFabricatorBlockEntity fab ? fab : new SimpleContainer(StarkFabricatorBlockEntity.SIZE);
	}

	public int progress() {
		return data.get(0);
	}

	/** The running job's length in ticks; 0 = no job running. */
	public int maxProgress() {
		return data.get(1);
	}

	/** Is a fabrication job running (Fabricate pressed, not finished or cancelled)? */
	public boolean running() {
		return maxProgress() > 0;
	}

	/** "changes 18": 0 = none, 1 = helmet, 2 = chestplate, 3 = leggings, 4 = boots. */
	public int selectedPiece() {
		return data.get(2);
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
		// v0.17.2 (user): Fabricate what the slots hold as they are / Cancel the running job
		if (id == StarkWorkbench.FABRICATE_BUTTON && container instanceof StarkFabricatorBlockEntity fab
				&& player instanceof net.minecraft.server.level.ServerPlayer sp) {
			StarkWorkbench.start(sp, fab);
			return true;
		}
		if (id == StarkWorkbench.CANCEL_BUTTON && container instanceof StarkFabricatorBlockEntity fab) {
			fab.cancel();
			return true;
		}
		// the Workbench Fabricate button for a browsed piece -- BUILD_BASE + suitIndex * 10 + piece (index into MARK_ORDER):
		// load its blueprint + components from the inventory, then start
		if (id >= StarkWorkbench.BUILD_BASE && container instanceof StarkFabricatorBlockEntity fab
				&& player instanceof net.minecraft.server.level.ServerPlayer sp) {
			int code = id - StarkWorkbench.BUILD_BASE;
			int suitIndex = code / 10;
			int piece = code % 10;
			java.util.List<String> order = com.projecthero.mod.ironman.item.IronManItems.MARK_ORDER;
			if (suitIndex >= 0 && suitIndex < order.size()) {
				StarkWorkbench.fabricate(sp, fab, order.get(suitIndex), piece);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, IronManBlocks.STARK_FABRICATOR)
				&& com.projecthero.mod.ironman.TonyStark.hasPower(player);
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
