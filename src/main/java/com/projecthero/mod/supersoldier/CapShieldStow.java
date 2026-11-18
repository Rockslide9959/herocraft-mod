package com.projecthero.mod.supersoldier;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.supersoldier.item.AdamantiumShieldItem;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5: a Captain America shield carried but not in either hand is <b>slung on the back</b>. The server works out which
 * one (the first in the hotbar, while neither hand holds one) every few ticks and syncs a one-item copy to everyone tracking
 * the player ({@link #STOWED}, not saved); the client's {@code CapShieldBackLayer} draws it. Purely visual: the stack itself
 * stays where it is.
 */
public final class CapShieldStow {
	/** The shield drawn on this player's back (absent = none). Synced to all trackers, not persisted. */
	public static final AttachmentType<ItemStack> STOWED = AttachmentRegistry.create(ProjectHeroMod.id("cap_shield_stowed"),
			builder -> builder.syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final int PERIOD = 5;

	private CapShieldStow() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % PERIOD != 0) {
				return;
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				update(p);
			}
		});
	}

	/** True for the three Captain America shields (the Adamantium and Vibranium Shields and the Soldier's Shield). */
	public static boolean isCapShield(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof AdamantiumShieldItem || stack.is(SuperSoldierItems.SOLDIER_SHIELD));
	}

	/** The shield that belongs on his back: the first Captain America shield in the hotbar while neither hand holds one. */
	public static ItemStack stowed(Player p) {
		if (isCapShield(p.getMainHandItem()) || isCapShield(p.getOffhandItem()) || p.isSpectator()) {
			return ItemStack.EMPTY;
		}
		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (isCapShield(s)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Re-syncs the slung shield when it changed (public for tests). */
	public static void update(ServerPlayer p) {
		ItemStack want = stowed(p);
		ItemStack have = p.getAttached(STOWED);
		if (want.isEmpty()) {
			if (have != null) {
				p.removeAttached(STOWED);
			}
			return;
		}
		if (have == null || !ItemStack.isSameItemSameComponents(have, want)) {
			p.setAttached(STOWED, want.copyWithCount(1));
		}
	}
}
