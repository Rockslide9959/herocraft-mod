package com.projecthero.mod.hammer;

import com.projecthero.mod.item.ModDataComponents;

import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7: which hand a Mjolnir / Stormbreaker lands in when it comes back to its owner -- one rule for a thrown weapon flying
 * home, a scripted throw ({@link com.projecthero.mod.power.ThorThrown}) and the call key.
 * <ol>
 *   <li><b>Back where it left.</b> A thrown weapon returns to the hand it was thrown from (main or off) while that hand is
 *   empty -- so Twin Gods stay Twin Gods through Whirlwind Throw, Storm Hurl, Storm Anchor and Twin Throw.</li>
 *   <li><b>Called: dual wielding without the juggling</b> (user: "if player calls a weapon to themself while having a weapon in
 *   their inventory then the second weapon goes into the offhand"). With the OTHER Thor weapon in the main hand, the called one
 *   takes the off hand; with the other in the off hand, the called one takes the main hand; with the other somewhere in the
 *   pack, the other is brought to the main hand (a hotbar slot is selected, a backpack slot is swapped with the held slot) and
 *   the called one takes the off hand. Whatever was in the off hand moves into the pack, or is dropped at his feet if the pack
 *   is full -- never deleted.</li>
 *   <li><b>Otherwise as before:</b> the empty main hand, else the pack; a call forces it into the main hand (dropping what was
 *   held) when the pack is full; a plain throw coming back to a full pack waits at his feet ({@link Landing#NOWHERE}).</li>
 * </ol>
 * {@link #plan} answers the question without touching anything (the returning entities sync it so the client raises the right
 * arm for the catch); {@link #place} does it.
 */
public final class ThorWeaponHands {
	/** Synced form of a hand: none (the pack), main, off. */
	public static final byte HAND_NONE = -1, HAND_MAIN = 0, HAND_OFF = 1;

	public enum Landing {
		MAIN, OFF, PACK, NOWHERE
	}

	private ThorWeaponHands() {
	}

	public static byte encode(InteractionHand hand) {
		return hand == null ? HAND_NONE : hand == InteractionHand.MAIN_HAND ? HAND_MAIN : HAND_OFF;
	}

	public static InteractionHand decode(byte b) {
		return b == HAND_MAIN ? InteractionHand.MAIN_HAND : b == HAND_OFF ? InteractionHand.OFF_HAND : null;
	}

	/**
	 * Where {@code weapon} arriving at {@code player} would land: a hand, or null for the pack / his feet. Pure.
	 *
	 * @param from   the hand it was thrown from, or null (a recall from the ground, a chest, another player...)
	 * @param called whether the call key brought it (rule 2)
	 */
	public static InteractionHand plan(Player player, ThorWeapon weapon, InteractionHand from, boolean called) {
		if (from != null && player.getItemInHand(from).isEmpty()) {
			return from;
		}
		if (called && weapon != null) {
			ThorWeapon other = weapon.other();
			if (other.is(player.getMainHandItem())) {
				return InteractionHand.OFF_HAND;
			}
			if (other.is(player.getOffhandItem())) {
				return InteractionHand.MAIN_HAND;
			}
			if (packSlot(player, other) >= 0) {
				return InteractionHand.OFF_HAND;
			}
		}
		if (player.getMainHandItem().isEmpty()) {
			return InteractionHand.MAIN_HAND;
		}
		if (called && player.getInventory().getFreeSlot() < 0) {
			return InteractionHand.MAIN_HAND;
		}
		return null;
	}

	/**
	 * Puts {@code stack} (a returning {@code weapon}) into {@code player} by the rules above and notes it in the registry. On
	 * {@link Landing#NOWHERE} nothing was moved and the caller keeps the stack (a returning entity lies down at his feet).
	 */
	public static Landing place(Player player, ItemStack stack, ThorWeapon weapon, InteractionHand from, boolean called) {
		ItemStack record = stack.copy();
		Landing landing = placeOnly(player, stack, weapon, from, called);
		if (landing != Landing.NOWHERE && player.level() instanceof ServerLevel level
				&& (weapon == ThorWeapon.MJOLNIR || record.get(ModDataComponents.HAMMER_ID) != null)) {
			ItemStack ref = landing == Landing.MAIN ? player.getMainHandItem() : landing == Landing.OFF ? player.getOffhandItem() : record;
			MjolnirRegistry.get(level).noteCarried(ref, player, landing != Landing.PACK);
		}
		return landing;
	}

	private static Landing placeOnly(Player player, ItemStack stack, ThorWeapon weapon, InteractionHand from, boolean called) {
		if (from != null && player.getItemInHand(from).isEmpty()) {
			player.setItemInHand(from, stack);
			return from == InteractionHand.MAIN_HAND ? Landing.MAIN : Landing.OFF;
		}
		if (called && weapon != null) {
			ThorWeapon other = weapon.other();
			if (other.is(player.getMainHandItem())) {
				putOff(player, stack);
				return Landing.OFF;
			}
			if (other.is(player.getOffhandItem())) {
				putMainForDual(player, stack);
				return Landing.MAIN;
			}
			int slot = packSlot(player, other);
			if (slot >= 0) {
				bringToMainHand(player, slot);
				putOff(player, stack);
				return Landing.OFF;
			}
		}
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			return Landing.MAIN;
		}
		if (player.getInventory().add(stack)) {
			return Landing.PACK;
		}
		if (!called) {
			return Landing.NOWHERE;
		}
		// a deliberate call with a full pack: it takes the hand anyway and what was held drops at his feet
		ItemStack displaced = player.getMainHandItem();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.drop(displaced, false);
		return Landing.MAIN;
	}

	/**
	 * v0.17.7: the call key with both weapons already on him: brings them into the hands -- the Thor weapon in the main hand stays
	 * there (else one in the off hand decides the other's place, else Mjolnir takes the main hand) and the other takes the off
	 * hand. Only ever swaps slots or selects a hotbar slot, so nothing needs room and nothing is lost.
	 *
	 * @return whether anything moved (false: already dual wielding, or one of them is not in the inventory)
	 */
	public static boolean arrangeDual(Player player) {
		ThorWeapon inMain = ThorWeapon.of(player.getMainHandItem());
		ThorWeapon inOff = ThorWeapon.of(player.getOffhandItem());
		if (inMain != null && inOff == inMain.other()) {
			return false;
		}
		ThorWeapon mainKind;
		if (inMain != null) {
			mainKind = inMain;
		} else if (inOff != null) {
			mainKind = inOff.other();
		} else {
			int hammer = packSlot(player, ThorWeapon.MJOLNIR);
			int axe = packSlot(player, ThorWeapon.STORMBREAKER);
			if (hammer < 0 || axe < 0) {
				return false;
			}
			// the one already on the hotbar takes the hand (no backpack shuffle), Mjolnir if both are
			mainKind = hammer >= Inventory.getSelectionSize() && axe < Inventory.getSelectionSize() ? ThorWeapon.STORMBREAKER
					: ThorWeapon.MJOLNIR;
		}
		ThorWeapon offKind = mainKind.other();
		if (inMain != mainKind) {
			int slot = packSlot(player, mainKind);
			if (slot < 0) {
				return false;
			}
			bringToMainHand(player, slot);
		}
		if (!offKind.is(player.getOffhandItem())) {
			int slot = packSlot(player, offKind);
			if (slot < 0) {
				return true;
			}
			Inventory inventory = player.getInventory();
			ItemStack off = player.getOffhandItem();
			player.setItemInHand(InteractionHand.OFF_HAND, inventory.getItem(slot));
			inventory.setItem(slot, off);
		}
		return true;
	}

	/** The first hotbar / backpack slot (0-35, not the selected one) holding {@code weapon}, or -1. */
	public static int packSlot(Player player, ThorWeapon weapon) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.items.size(); slot++) {
			if (slot != inventory.selected && weapon.is(inventory.items.get(slot))) {
				return slot;
			}
		}
		return -1;
	}

	/** The off hand takes {@code stack}; whatever it held goes into the pack, or drops at his feet if the pack is full. */
	private static void putOff(Player player, ItemStack stack) {
		ItemStack old = player.getOffhandItem();
		player.setItemInHand(InteractionHand.OFF_HAND, stack);
		stow(player, old);
	}

	/** The main hand takes {@code stack}: empty hand, else an empty hotbar slot is selected, else the held item steps into the pack. */
	private static void putMainForDual(Player player, ItemStack stack) {
		if (!player.getMainHandItem().isEmpty()) {
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
				if (inventory.items.get(slot).isEmpty()) {
					select(player, slot);
					break;
				}
			}
		}
		ItemStack old = player.getMainHandItem();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		stow(player, old);
	}

	/** Brings the weapon in {@code slot} to the main hand: a hotbar slot is selected, a backpack slot swapped with the held one. */
	private static void bringToMainHand(Player player, int slot) {
		Inventory inventory = player.getInventory();
		if (Inventory.isHotbarSlot(slot)) {
			select(player, slot);
			return;
		}
		ItemStack held = inventory.getItem(inventory.selected);
		inventory.setItem(inventory.selected, inventory.getItem(slot));
		inventory.setItem(slot, held);
	}

	private static void select(Player player, int slot) {
		player.getInventory().selected = slot;
		if (player instanceof ServerPlayer sp && sp.connection != null) {
			sp.connection.send(new ClientboundSetCarriedItemPacket(slot));
		}
	}

	private static void stow(Player player, ItemStack old) {
		if (!old.isEmpty() && !player.getInventory().add(old)) {
			player.drop(old, false);
		}
	}
}
