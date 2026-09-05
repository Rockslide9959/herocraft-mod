package com.herocraft.mod.ironman.ability;

import com.herocraft.mod.hero.AbilitySlot;
import com.herocraft.mod.ironman.IronManArmor;
import com.herocraft.mod.ironman.TonyStark;
import com.herocraft.mod.ironman.suit.IronManSuit;
import com.herocraft.mod.ironman.suit.IronManSuits;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges the six universal ability slots to the Iron Man ability set (spec sections 30, 36).
 *
 * <p>{@link com.herocraft.mod.hero.AbilityRouter} gives Iron Man the slots (ahead of any experimental
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
			if (inv.getItem(i).getItem() instanceof com.herocraft.mod.ironman.item.IronManArmorItem) {
				return true;
			}
		}
		return false;
	}

	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);

		if (suit != null && IronManArmor.wearingAnyIronMan(player)) {
			IronManAbilities.trigger(player, suit, slot.number(), pressed);
			return;
		}

		// Not wearing a valid suit -> slot 6 opens the call-armour picker, everything else nudges you to suit up.
		if (!pressed) {
			return;
		}
		if (slot == AbilitySlot.SLOT_6) {
			// "changes 19": plain C auto-equips a full inventory suit; sneak + C opens the picker.
			if (!player.isShiftKeyDown()
					&& com.herocraft.mod.ironman.suit.IronManSuitCall.autoEquipInventorySuit(player)) {
				return;
			}
			com.herocraft.mod.ironman.suit.IronManSuitCall.openMenu(player);
		} else {
			player.displayClientMessage(Component.translatable("message.herocraft.ironman.call_armor_first",
					Component.keybind("key.herocraft.ability_6")), true);
		}
	}
}
