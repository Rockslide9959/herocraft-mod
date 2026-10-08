package com.projecthero.mod.ironman.ability;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the Iron Man ability set (spec sections 30, 36).
 *
 * <p>{@link com.projecthero.mod.hero.AbilityRouter} gives Iron Man the slots (ahead of any experimental
 * power) whenever {@link #hasContext} is true. Two states:
 * <ul>
 *   <li><b>wearing a suit</b> -- all six slots route to that suit's ability layout;</li>
 *   <li><b>not wearing, but has developed a suit</b> -- only slot 6 ({@code C}) is live, and it
 *       <b>calls the armour to you</b> (spec "changes 8" -- the Tony Stark power's summon ability).
 *       Slots 1-5 just remind you to suit up first.</li>
 * </ul>
 */
public final class IronManAbilityManager {
	private IronManAbilityManager() {
	}

	/**
	 * True when the six slots should route to Iron Man: the player has the Tony Stark power AND is
	 * either wearing a suit, has developed one, or is carrying any Iron Man armour piece. (A Tony
	 * Stark player with no armour at all keeps their experimental-power slots.)
	 */
	public static boolean hasContext(ServerPlayer player) {
		if (!TonyStark.hasPower(player)) {
			return false;
		}
		if (IronManArmor.wearingAnyIronMan(player) || !TonyStark.builtSuitIds(player).isEmpty()) {
			return true;
		}
		net.minecraft.world.entity.player.Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				return true;
			}
		}
		return false;
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);

		if (suit != null && IronManArmor.wearingAnyIronMan(player)) {
			// v0.14.27: nothing fires until the suit-up has finished building every piece on
			if (com.projecthero.mod.ironman.suit.IronManSuitUpManager.blockedWhileAssembling(player, pressed)) {
				return;
			}
			// v0.15.3, explicit user request: using any suit ability seals an open faceplate (C -- store the suit -- doesn't)
			if (pressed && !IronManAbilities.SUIT_TOGGLE.equals(suit.abilityInSlot(slot.number()))) {
				com.projecthero.mod.ironman.IronManFaceplate.autoClose(player);
			}
			// v0.14.29 (agent D): Sneak+C while suited opens the Call Armour picker with "send home" for the worn suit
			// (it flies itself back to its platform for repair). Plain C is still suit-down; a suitcase mark (Mark 5)
			// keeps its own Sneak+C fold.
			// v0.15.15, explicit user request: Sneak+C (the picker / send home) is the Mark 8's alone -- on Marks 1-7 it does
			// nothing at all (plain C still takes the suit off)
			if (slot == AbilitySlot.SLOT_6 && player.isShiftKeyDown() && suit.markNumber() < 8) {
				return;
			}
			IronManAbilities.trigger(player, suit, slot.number(), pressed);
			return;
		}

		// Not wearing a valid suit -> slot 6 opens the call-armour picker, everything else nudges you to suit up.
		if (!pressed) {
			return;
		}
		if (slot == AbilitySlot.SLOT_6) {
			// "changes 19": plain C auto-equips a full inventory suit; sneak + C opens the picker.
			// v0.15.7: with the Colantotte Bracelets on, plain C calls the Mark 7
			// v0.15.15, explicit user request: the call picker only opens with the Stark Glasses on -- otherwise plain C is
			// the bracelet call / putting on a carried suit, and anything else (or Sneak+C) quietly does nothing
			com.projecthero.mod.ironman.suit.IronManSuitCall.unsuitedC(player);
		} else {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.call_armor_first",
					Component.keybind("key.projecthero.ability_6")), true);
		}
	}
}
