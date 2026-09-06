package com.projecthero.mod.firearm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Reserve-ammunition bookkeeping. A Punisher ({@link FirearmHooks#usesPersonalReserve}) draws on a
 * personal per-gun reserve pool instead of inventory items; every other player must carry the matching
 * {@link AmmoKind} item and a reload consumes only as many rounds as it takes to top the magazine off
 * -- partial reloads are allowed (spec section 35).
 */
public final class FirearmAmmo {
	private FirearmAmmo() {
	}

	/** How many reserve rounds of this kind the player can draw on right now (for the HUD). */
	public static int reserveCount(ServerPlayer player, AmmoKind kind) {
		if (FirearmHooks.get().usesPersonalReserve(player)) {
			return FirearmHooks.get().personalReserveCount(player, kind);
		}
		int total = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (kind.item() != null && s.is(kind.item())) {
				total += s.getCount();
			}
		}
		return total;
	}

	/**
	 * Take up to {@code want} rounds from the player's reserve. Returns how many were actually
	 * available and removed (0..want). Infinite-reserve players always get exactly {@code want}.
	 */
	public static int take(ServerPlayer player, AmmoKind kind, int want) {
		if (want <= 0) {
			return 0;
		}
		if (FirearmHooks.get().usesPersonalReserve(player)) {
			return FirearmHooks.get().personalReserveTake(player, kind, want);
		}
		if (kind.item() == null) {
			return 0;
		}
		int taken = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && taken < want; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.is(kind.item())) {
				continue;
			}
			int use = Math.min(s.getCount(), want - taken);
			s.shrink(use);
			taken += use;
		}
		return taken;
	}
}
