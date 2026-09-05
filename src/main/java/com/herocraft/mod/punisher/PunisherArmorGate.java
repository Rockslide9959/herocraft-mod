package com.herocraft.mod.punisher;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.herocraft.mod.punisher.item.PunisherArmorItem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Only the Punisher may wear the Punisher tactical armour (v0.9.4). A crafting gate already exists
 * ({@code CraftingMenuMixin}); this is the wear gate. Continuous ejection (the same discipline the
 * mod uses for Mjolnir worthiness) covers every path a piece could reach an armour slot -- dragging
 * it on, {@code /item}, a dispenser, a death-drop pickup -- so there is no equip event to hook.
 *
 * <p>Called for <em>every</em> player from {@code AbilityRouter.serverTick}. A no-op for a Punisher
 * and for anyone not wearing a piece.
 */
public final class PunisherArmorGate {
	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	/** Throttle the "you can't wear this" message per player. */
	private static final Map<UUID, Long> WARNED = new ConcurrentHashMap<>();

	private PunisherArmorGate() {
	}

	public static void clearSessionState() {
		WARNED.clear();
	}

	public static void enforce(ServerPlayer player) {
		if (player.isSpectator() || Punisher.hasPower(player)) {
			return;
		}
		boolean ejectedAny = false;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack worn = player.getItemBySlot(slot);
			if (!(worn.getItem() instanceof PunisherArmorItem)) {
				continue;
			}
			player.setItemSlot(slot, ItemStack.EMPTY);
			ItemStack copy = worn.copy();
			if (!player.getInventory().add(copy)) {
				player.drop(copy, false);
			}
			ejectedAny = true;
		}
		if (!ejectedAny) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = WARNED.get(player.getUUID());
		if (last == null || now - last > 60L) {
			WARNED.put(player.getUUID(), now);
			player.displayClientMessage(Component.translatable("message.herocraft.punisher.armor_locked")
					.withStyle(ChatFormatting.RED), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 0.5f, 0.6f);
		}
	}
}
