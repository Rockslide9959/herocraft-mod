package com.herocraft.mod.ironman;

import java.util.EnumMap;
import java.util.Map;

import com.herocraft.mod.ironman.item.IronManArmorItem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * The Iron Man armour restriction system (spec sections 4, 15, 16, 32). Server-authoritative.
 *
 * <h2>The rule</h2>
 * Wearing Iron Man armour never grants anything by itself. Every capability check is conceptually:
 * <pre>{@code player.hasPower(TONY_STARK) && isWearingValidIronManSuit(suitId)}</pre>
 * A normal player can hold or be handed Iron Man armour, but:
 * <ul>
 *   <li>{@link #enforce} runs every server tick and ejects any Iron Man piece worn by a player
 *       without the Tony Stark power back into their inventory (or drops it safely if the inventory is
 *       full) with "Stark armor rejects unauthorized user." -- the item is never destroyed;</li>
 *   <li>even if some external mod force-holds a piece in an armour slot, {@link #canOperate} still
 *       returns false, so no ability, flight, repulsor, HUD or energy system ever activates.</li>
 * </ul>
 */
public final class IronManArmor {
	private IronManArmor() {
	}

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	/** The single gate for every Iron Man capability. */
	public static boolean canOperate(Player player) {
		return TonyStark.hasPower(player);
	}

	public static boolean isIronMan(ItemStack stack) {
		return stack.getItem() instanceof IronManArmorItem;
	}

	/** The suit id of whatever Iron Man pieces the player is wearing, or {@code null} if none / mixed. */
	public static String wornSuitId(Player player) {
		String suitId = null;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.getItem() instanceof IronManArmorItem piece) {
				if (suitId == null) {
					suitId = piece.suitId();
				} else if (!suitId.equals(piece.suitId())) {
					return null; // mismatched pieces -- not a valid single suit
				}
			}
		}
		return suitId;
	}

	/** Which armour slots of {@code suitId} the player currently has on (partial armour support, spec 32). */
	public static Map<ArmorItem.Type, Boolean> wornPieces(Player player, String suitId) {
		EnumMap<ArmorItem.Type, Boolean> out = new EnumMap<>(ArmorItem.Type.class);
		out.put(ArmorItem.Type.HELMET, isPieceWorn(player, EquipmentSlot.HEAD, suitId));
		out.put(ArmorItem.Type.CHESTPLATE, isPieceWorn(player, EquipmentSlot.CHEST, suitId));
		out.put(ArmorItem.Type.LEGGINGS, isPieceWorn(player, EquipmentSlot.LEGS, suitId));
		out.put(ArmorItem.Type.BOOTS, isPieceWorn(player, EquipmentSlot.FEET, suitId));
		return out;
	}

	public static boolean isPieceWorn(Player player, EquipmentSlot slot, String suitId) {
		ItemStack stack = player.getItemBySlot(slot);
		return stack.getItem() instanceof IronManArmorItem piece && piece.suitId().equals(suitId);
	}

	public static boolean wearingAnyIronMan(Player player) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (player.getItemBySlot(slot).getItem() instanceof IronManArmorItem) {
				return true;
			}
		}
		return false;
	}

	public static boolean wearingFullSuit(Player player, String suitId) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (!isPieceWorn(player, slot, suitId)) {
				return false;
			}
		}
		return true;
	}

	public static boolean hasHelmet(Player player, String suitId) {
		return isPieceWorn(player, EquipmentSlot.HEAD, suitId);
	}

	public static boolean hasChestplate(Player player, String suitId) {
		return isPieceWorn(player, EquipmentSlot.CHEST, suitId);
	}

	/**
	 * Runs every server tick for every player (from {@code HeroCraftMod}). If a player without the
	 * Tony Stark power is wearing any Iron Man armour piece, it is removed from the slot and returned
	 * to the inventory (or dropped if full) -- once per offending player, with a message and a
	 * rejection sound. Never destroys the item.
	 */
	public static void enforce(ServerPlayer player) {
		if (TonyStark.hasPower(player)) {
			return;
		}
		boolean rejectedAny = false;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!(stack.getItem() instanceof IronManArmorItem)) {
				continue;
			}
			ItemStack removed = stack.copy();
			player.setItemSlot(slot, ItemStack.EMPTY);
			if (!player.getInventory().add(removed)) {
				player.drop(removed, false);
			}
			rejectedAny = true;
		}
		if (rejectedAny) {
			player.displayClientMessage(
					Component.translatable("message.herocraft.ironman.armor_rejects").withStyle(ChatFormatting.RED), true);
			ServerLevel level = (ServerLevel) player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7f, 1.8f);
		}
	}
}
