package com.projecthero.mod.wardrobe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.5: the Wardrobe's contents -- {@link #SUITS} full suits of armour (head, chest, legs, feet each) and which one is
 * selected (shown on the figure in the middle). Lives on the middle bottom block only. Synced to clients on every change
 * so {@code WardrobeRenderer} always draws what is really stored; everything drops when the Wardrobe is broken.
 */
public class WardrobeBlockEntity extends BlockEntity {
	public static final int SUITS = 10;
	/** Storage order of a suit's pieces. */
	public static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	/** The middle figure's size (a player is 1.8 tall) and how high it stands (on the display plinth). */
	public static final float FIGURE_SCALE = 0.88f;
	public static final double FIGURE_Y = 3.0 / 16.0;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SUITS * SLOTS.length, ItemStack.EMPTY);
	private int selected;

	public WardrobeBlockEntity(BlockPos pos, BlockState state) {
		super(WardrobeMod.WARDROBE_BE, pos, state);
	}

	private static int index(int suit, EquipmentSlot slot) {
		return suit * SLOTS.length + (3 - slot.getIndex()); // HEAD has index 3, FEET 0
	}

	public ItemStack piece(int suit, EquipmentSlot slot) {
		return items.get(index(suit, slot));
	}

	public void setPiece(int suit, EquipmentSlot slot, ItemStack stack) {
		items.set(index(suit, slot), stack);
		changed();
	}

	public boolean suitEmpty(int suit) {
		for (EquipmentSlot slot : SLOTS) {
			if (!piece(suit, slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public int selected() {
		return selected;
	}

	/** Right-click the left / right column: the selection moves one suit that way, wrapping round. */
	public void scroll(int delta) {
		selected = Math.floorMod(selected + delta, SUITS);
		changed();
	}

	public NonNullList<ItemStack> items() {
		return items;
	}

	/**
	 * Shift+right-click the middle with empty hands: the selected suit and the one you wear change places, piece by piece
	 * -- wear nothing and you put the stored suit on, store into an empty slot and you take yours off. A cursed piece of
	 * Binding stays on you (outside creative). Returns how many slots changed.
	 */
	public int swapSuit(Player player) {
		int moved = 0;
		for (EquipmentSlot slot : SLOTS) {
			ItemStack worn = player.getItemBySlot(slot);
			ItemStack stored = piece(selected, slot);
			if (worn.isEmpty() && stored.isEmpty()) {
				continue;
			}
			if (!worn.isEmpty() && !player.getAbilities().instabuild
					&& EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
				continue;
			}
			items.set(index(selected, slot), worn);
			player.setItemSlot(slot, stored);
			moved++;
		}
		if (moved > 0) {
			changed();
		}
		return moved;
	}

	/**
	 * Plain right-click on the middle, armour-stand style: holding a piece of armour hangs it on the selected suit (the
	 * piece already there comes back to you); empty-handed you take the piece where you clicked (or the highest one left).
	 * Returns false when nothing happened.
	 */
	public boolean putOrTake(Player player, InteractionHand hand, EquipmentSlot clicked) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.isEmpty()) {
			EquipmentSlot target = player.getEquipmentSlotForItem(held);
			if (target.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
				return false;
			}
			ItemStack old = piece(selected, target);
			setPiece(selected, target, held.split(1));
			if (!old.isEmpty()) {
				if (held.isEmpty()) {
					player.setItemInHand(hand, old);
				} else if (!player.getInventory().add(old)) {
					player.drop(old, false);
				}
			}
			return true;
		}
		EquipmentSlot take = piece(selected, clicked).isEmpty() ? null : clicked;
		for (int i = 0; take == null && i < SLOTS.length; i++) {
			if (!piece(selected, SLOTS[i]).isEmpty()) {
				take = SLOTS[i];
			}
		}
		if (take == null) {
			return false;
		}
		player.setItemInHand(hand, piece(selected, take));
		setPiece(selected, take, ItemStack.EMPTY);
		return true;
	}

	/** Which piece of the middle figure a click at this height (blocks above the Wardrobe's floor) lands on. */
	public static EquipmentSlot slotAtHeight(double y) {
		double onFigure = (y - FIGURE_Y) / FIGURE_SCALE; // 0 at the feet, 1.8 at the top of the head
		return onFigure >= 1.45 ? EquipmentSlot.HEAD : onFigure >= 0.85 ? EquipmentSlot.CHEST : onFigure >= 0.4 ? EquipmentSlot.LEGS
				: EquipmentSlot.FEET;
	}

	/** "Suit 3 / 10: Amazing 2 Spider-Man Suit" -- the action-bar line after a scroll or a swap. */
	public Component describeSelected() {
		Component what = Component.translatable("block.projecthero.wardrobe.empty");
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (!piece(selected, slot).isEmpty()) {
				what = piece(selected, slot).getHoverName();
				break;
			}
		}
		return Component.translatable("block.projecthero.wardrobe.selected", selected + 1, SUITS, what);
	}

	private void changed() {
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, true, registries);
		tag.putInt("Selected", selected);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		for (int i = 0; i < items.size(); i++) {
			items.set(i, ItemStack.EMPTY); // an emptied slot is simply missing from the tag
		}
		ContainerHelper.loadAllItems(tag, items, registries);
		selected = Math.floorMod(tag.getInt("Selected"), SUITS);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}
}
