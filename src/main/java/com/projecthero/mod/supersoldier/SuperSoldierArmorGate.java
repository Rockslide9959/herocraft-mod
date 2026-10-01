package com.projecthero.mod.supersoldier;

import com.projecthero.mod.supersoldier.item.SuperSoldierArmorItem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Only a Super Soldier may wear the Captain America suit (v0.14.9). Right-clicking a piece on is refused outright
 * ({@link SuperSoldierArmorItem#use}); every other way a piece can reach an armour slot (dragging it in the inventory,
 * a dispenser, {@code /item}, losing the power while wearing it) is caught here the next tick: the piece pops off into
 * the inventory, or drops at his feet if that is full. The same continuous-ejection discipline as
 * {@code PunisherArmorGate}. Runs for every player from {@link SuperSoldierAbilityManager#serverTick}.
 */
public final class SuperSoldierArmorGate {
	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private SuperSoldierArmorGate() {
	}

	/** True if {@code player} may wear the suit. */
	public static boolean mayWear(net.minecraft.world.entity.player.Player player) {
		return SuperSoldier.hasPower(player);
	}

	/** Ejects every suit piece a non-Super-Soldier is wearing. Returns how many came off. */
	public static int enforce(ServerPlayer player) {
		if (player.isSpectator() || mayWear(player)) {
			return 0;
		}
		int ejected = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack worn = player.getItemBySlot(slot);
			if (!(worn.getItem() instanceof SuperSoldierArmorItem)) {
				continue;
			}
			player.setItemSlot(slot, ItemStack.EMPTY);
			if (!player.getInventory().add(worn) || !worn.isEmpty()) {
				player.drop(worn, false);
			}
			ejected++;
		}
		if (ejected > 0) {
			SuperSoldier.say(player, "message.projecthero.super_soldier.armor_locked", ChatFormatting.RED);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 0.5f, 0.6f);
		}
		return ejected;
	}

	/** The refusal shown when a right-click equip is blocked. */
	public static Component lockedMessage() {
		return Component.translatable("message.projecthero.super_soldier.armor_locked").withStyle(ChatFormatting.RED);
	}
}
