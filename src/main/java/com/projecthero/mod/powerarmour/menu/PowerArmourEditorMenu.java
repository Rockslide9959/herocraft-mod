package com.projecthero.mod.powerarmour.menu;

import com.projecthero.mod.powerarmour.BodyPart;
import com.projecthero.mod.powerarmour.ModuleType;
import com.projecthero.mod.powerarmour.PowerArmourMod;
import com.projecthero.mod.powerarmour.SuitData;
import com.projecthero.mod.powerarmour.entity.PowerArmourFrameEntity;
import com.projecthero.mod.powerarmour.item.ArmourModuleItem;
import com.projecthero.mod.powerarmour.item.PowerCoreItem;

import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5 Power Armour: the Armour Editor's menu, opened on a parked frame (Sneak + right-click with an empty hand). Slot 0 is
 * the core bay, then one slot per upgrade slot of the frame's tier (each only takes a module for its body part that fits --
 * {@link SuitData#whyNot}), then the player's inventory. The slots read and write the frame's {@link SuitData} directly
 * ({@link SuitContainer}). Paint and Pick Up go through {@code PowerArmourEditPayload}.
 */
public class PowerArmourEditorMenu extends AbstractContainerMenu {
	public static final int WIDTH = 256, HEIGHT = 230;
	public static final int CORE_X = 12, CORE_Y = 125;
	public static final int PART_Y0 = 20, PART_DY = 21, PART_SLOT_X = 150;
	public static final int INV_X = 48, INV_Y = 148;

	private final PowerArmourFrameEntity frame;
	private final SuitContainer container;
	private final int suitSlots;

	/** Client: the frame comes by entity id. */
	public PowerArmourEditorMenu(int id, Inventory inventory, Integer frameId) {
		this(id, inventory, inventory.player.level().getEntity(frameId) instanceof PowerArmourFrameEntity f ? f : null);
	}

	public PowerArmourEditorMenu(int id, Inventory inventory, PowerArmourFrameEntity frame) {
		super(PowerArmourMod.EDITOR_MENU, id);
		this.frame = frame;
		SuitData suit = frame == null ? SuitData.fresh(com.projecthero.mod.powerarmour.ArmourTier.IRON) : frame.suit();
		this.suitSlots = suit.modules.length;
		this.container = new SuitContainer(frame);
		addSlot(new Slot(container, 0, CORE_X, CORE_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof PowerCoreItem core && suit().whyNotCore(core.type()) == null;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		int[] perPart = new int[BodyPart.values().length];
		for (int i = 0; i < suitSlots; i++) {
			BodyPart part = suit.tier.slotPart(i);
			int k = perPart[part.ordinal()]++;
			final int index = i;
			addSlot(new Slot(container, 1 + i, PART_SLOT_X + k * 20, PART_Y0 + part.ordinal() * PART_DY) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.getItem() instanceof ArmourModuleItem m && suit().whyNot(index, m.type()) == null;
				}

				@Override
				public boolean mayPickup(Player player) {
					return suit().whyNotRemove(index) == null;
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 9; c++) {
				addSlot(new Slot(inventory, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
			}
		}
		for (int c = 0; c < 9; c++) {
			addSlot(new Slot(inventory, c, INV_X + c * 18, INV_Y + 58));
		}
	}

	public PowerArmourFrameEntity frame() {
		return frame;
	}

	public SuitData suit() {
		return frame == null ? SuitData.fresh(com.projecthero.mod.powerarmour.ArmourTier.IRON) : frame.suit();
	}

	/** Slot index of upgrade slot {@code i} (core bay is 0). */
	public int moduleSlot(int i) {
		return 1 + i;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem() || !slot.mayPickup(player)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		int invStart = 1 + suitSlots;
		if (index < invStart) {
			if (!moveItemStackTo(stack, invStart, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.getItem() instanceof PowerCoreItem) {
			if (!slots.get(0).mayPlace(stack) || slots.get(0).hasItem() || !moveItemStackTo(stack, 0, 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.getItem() instanceof ArmourModuleItem) {
			boolean moved = false;
			for (int i = 1; i < invStart && !moved; i++) {
				Slot target = slots.get(i);
				if (!target.hasItem() && target.mayPlace(stack)) {
					target.setByPlayer(stack.split(1));
					moved = true;
				}
			}
			if (!moved) {
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

	@Override
	public boolean stillValid(Player player) {
		return frame != null && frame.isAlive() && !frame.occupied() && player.distanceToSqr(frame) < 64.0;
	}

	/**
	 * The frame's suit as a container: 0 the core (as a charged core item), 1.. the upgrade slots (as module items). Writing a
	 * slot changes the suit and syncs the frame.
	 */
	static final class SuitContainer implements Container {
		private final PowerArmourFrameEntity frame;

		SuitContainer(PowerArmourFrameEntity frame) {
			this.frame = frame;
		}

		private SuitData suit() {
			return frame == null ? SuitData.fresh(com.projecthero.mod.powerarmour.ArmourTier.IRON) : frame.suit();
		}

		@Override
		public int getContainerSize() {
			return 1 + suit().modules.length;
		}

		@Override
		public boolean isEmpty() {
			SuitData s = suit();
			if (s.core != null) {
				return false;
			}
			for (ModuleType m : s.modules) {
				if (m != null) {
					return false;
				}
			}
			return true;
		}

		@Override
		public ItemStack getItem(int slot) {
			SuitData s = suit();
			if (slot == 0) {
				if (s.core == null) {
					return ItemStack.EMPTY;
				}
				ItemStack core = new ItemStack(PowerArmourMod.core(s.core));
				PowerCoreItem.setCharge(core, s.coreCharge);
				return core;
			}
			int i = slot - 1;
			if (i < 0 || i >= s.modules.length || s.modules[i] == null) {
				return ItemStack.EMPTY;
			}
			return new ItemStack(PowerArmourMod.module(s.modules[i]));
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			ItemStack was = getItem(slot);
			if (!was.isEmpty() && amount > 0) {
				setItem(slot, ItemStack.EMPTY);
			}
			return was;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return removeItem(slot, 1);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			if (frame == null) {
				return;
			}
			SuitData s = frame.suit();
			if (slot == 0) {
				if (stack.getItem() instanceof PowerCoreItem core) {
					s.core = core.type();
					s.coreCharge = PowerCoreItem.charge(stack);
				} else {
					s.core = null;
					s.coreCharge = 0f;
				}
			} else {
				int i = slot - 1;
				if (i >= 0 && i < s.modules.length) {
					s.modules[i] = stack.getItem() instanceof ArmourModuleItem m ? m.type() : null;
				}
				s.bank = Math.min(s.bank, s.bankCapacity());
			}
			if (!frame.level().isClientSide()) {
				frame.suitChanged();
			}
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(Player player) {
			return frame != null && frame.isAlive();
		}

		@Override
		public void clearContent() {
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}

	/** The id the client needs to find the frame. */
	public static int frameIdOf(Entity frame) {
		return frame.getId();
	}
}
