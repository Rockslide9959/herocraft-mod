package com.projecthero.mod.ironman;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.15.9, explicit user request ("when players suitup or suitdown make all arrows stuck on the players body
 * disappear"): every suit-up and suit-down starts and ends with the stuck arrows (and bee stingers) knocked off the
 * player's body.
 *
 * <p>Done centrally rather than in each of the many suit-up paths (C toggle / auto-equip, the Mark 5 suitcase, a
 * called suit's couriers, the Mark 7 pod, the bracelets, Protocol Phoenix, the Suit Platform, the Stark Gantry, a
 * sentry stepping in or out): once a tick the player's "suit signature" -- which of the four armour slots hold an
 * Iron Man piece, plus whether a suit-up / suit-down sequence is running -- is compared with last tick's, and any
 * change clears the arrows. A sequence beginning or ending flips the transition bits; a piece going on or coming off
 * (each gantry / courier / pod piece included) flips a slot bit -- so the start and the end of every path are both
 * caught. Server side, so the counts sync to every client. {@link #LAST} is cleared by {@code ServerStateReset}.
 */
public final class IronManSuitArrows {
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final Map<UUID, Integer> LAST = new ConcurrentHashMap<>();

	private IronManSuitArrows() {
	}

	public static void clearSessionState() {
		LAST.clear();
	}

	public static void forget(UUID player) {
		LAST.remove(player);
	}

	/** Which slots hold an Iron Man piece (bits 0-3), a sequence running (bit 4), assembling (bit 5). */
	public static int signature(ServerPlayer player) {
		int sig = 0;
		for (int i = 0; i < SLOTS.length; i++) {
			if (player.getItemBySlot(SLOTS[i]).getItem() instanceof IronManArmorItem) {
				sig |= 1 << i;
			}
		}
		if (IronManSuitUpManager.inTransition(player)) {
			sig |= 1 << 4;
		}
		if (IronManSuitUpManager.assembling(player)) {
			sig |= 1 << 5;
		}
		return sig;
	}

	/** Once a tick per player (from {@code IronManSuitTicker}). */
	public static void tick(ServerPlayer player) {
		int sig = signature(player);
		Integer prev = LAST.put(player.getUUID(), sig);
		if (prev != null && prev != sig) {
			clearStuck(player);
		}
	}

	/** Knocks every stuck arrow and stinger off the player. */
	public static void clearStuck(ServerPlayer player) {
		if (player.getArrowCount() != 0) {
			player.setArrowCount(0);
		}
		if (player.getStingerCount() != 0) {
			player.setStingerCount(0);
		}
	}
}
