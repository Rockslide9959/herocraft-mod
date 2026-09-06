package com.projecthero.mod.punisher.ability;

import com.projecthero.mod.firearm.Firearms;
import com.projecthero.mod.firearm.item.FirearmItem;
import com.projecthero.mod.firearm.item.FirearmItems;
import com.projecthero.mod.network.PunisherArsenalOpenPayload;
import com.projecthero.mod.punisher.Punisher;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Ability 1 (R) -- Arsenal. Holding R opens a weapon wheel over the Punisher's available firearms
 * (always the pistol, plus any gun they have crafted / unlocked -- spec section 22); releasing on a
 * choice equips it. A Punisher is a weapon specialist: if they don't physically carry the chosen gun
 * a fresh one is drawn into hand, and whatever was in the main hand is stowed.
 */
public final class PunisherArsenal {
	private PunisherArsenal() {
	}

	/** Server tells the client to open the wheel; the client reads the unlocked list from synced state. */
	public static void open(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			return;
		}
		ServerPlayNetworking.send(player, PunisherArsenalOpenPayload.INSTANCE);
	}

	public static void equip(ServerPlayer player, String weaponId) {
		if (!Punisher.hasPower(player) || Firearms.get(weaponId) == null) {
			return;
		}
		if (!Punisher.weaponUnlocked(player, weaponId)) {
			player.displayClientMessage(Component.translatable("message.projecthero.punisher.weapon_locked")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (main.getItem() instanceof FirearmItem f && f.firearmId().equals(weaponId)) {
			return; // already holding it
		}
		Item item = itemFor(weaponId);
		if (item == null) {
			return;
		}

		// prefer an existing gun in the inventory (keeps its loaded magazine)
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.getItem() == item) {
				inv.setItem(i, main.copy());
				player.setItemInHand(InteractionHand.MAIN_HAND, s.copy());
				announce(player, weaponId);
				return;
			}
		}

		// none carried -- draw a fresh one, stow whatever was in hand
		if (!main.isEmpty() && !inv.add(main.copy())) {
			player.drop(main.copy(), false);
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		announce(player, weaponId);
	}

	private static void announce(ServerPlayer player, String weaponId) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_FRAME_ADD_ITEM, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
		player.displayClientMessage(Component.translatable("item.projecthero." + weaponId)
				.withStyle(ChatFormatting.GRAY), true);
	}

	private static Item itemFor(String weaponId) {
		return switch (weaponId) {
			case Firearms.PISTOL -> FirearmItems.PUNISHER_PISTOL;
			case Firearms.RIFLE -> FirearmItems.PUNISHER_ASSAULT_RIFLE;
			case Firearms.SHOTGUN -> FirearmItems.PUNISHER_SHOTGUN;
			case Firearms.SNIPER -> FirearmItems.PUNISHER_SNIPER;
			default -> null;
		};
	}
}
