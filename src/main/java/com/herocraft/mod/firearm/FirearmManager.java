package com.herocraft.mod.firearm;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.firearm.item.FirearmItem;
import com.herocraft.mod.network.FirearmShotPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Per-player firearm upkeep: the trigger-held flag (so automatic fire is server-timed, not client-
 * spammed), the transient recoil accumulator, reload ticking, empty-magazine auto-reload, and
 * lowering the sights when the weapon is put away.
 *
 * <p>All per-player scratch here is static and world-object-free, but it is still registered with
 * {@link com.herocraft.mod.diagnostics.ServerStateReset} so it is dropped on server stop.
 */
public final class FirearmManager {
	private static final Map<UUID, Boolean> TRIGGER_HELD = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> RECOIL = new ConcurrentHashMap<>();

	private FirearmManager() {
	}

	public static void clearSessionState() {
		TRIGGER_HELD.clear();
		RECOIL.clear();
	}

	public static void onCleanup(UUID id) {
		TRIGGER_HELD.remove(id);
		RECOIL.remove(id);
	}

	// ---------------- input ----------------

	/** The client's attack button, edge-reported. A semi-auto weapon fires once on the press. */
	public static void onFireInput(ServerPlayer player, boolean pressed) {
		TRIGGER_HELD.put(player.getUUID(), pressed);
		if (!pressed) {
			return;
		}
		ItemStack held = heldFirearm(player);
		if (held == null) {
			return;
		}
		FirearmData data = Firearms.of(held);
		if (data == null) {
			return;
		}
		if (!data.automatic) {
			FirearmShooting.fire(player, held, data);
		}
		// automatic weapons are driven from serverTick while the trigger stays held
	}

	public static void onReloadInput(ServerPlayer player) {
		ItemStack held = heldFirearm(player);
		if (held == null) {
			return;
		}
		FirearmData data = Firearms.of(held);
		if (data != null) {
			FirearmReload.start(player, held, data);
		}
	}

	// ---------------- recoil ----------------

	public static float currentRecoil(ServerPlayer player) {
		return RECOIL.getOrDefault(player.getUUID(), 0f);
	}

	public static void addRecoil(ServerPlayer player, float degrees, FirearmData data) {
		float next = Math.min(data.maxRecoilDegrees, currentRecoil(player) + degrees);
		RECOIL.put(player.getUUID(), next);
	}

	public static void applyCameraKick(ServerPlayer player, float degrees) {
		if (degrees > 0f) {
			ServerPlayNetworking.send(player, new FirearmShotPayload(degrees));
		}
	}

	// ---------------- tick ----------------

	public static void serverTick(ServerPlayer player) {
		ItemStack held = heldFirearm(player);
		if (held == null) {
			TRIGGER_HELD.remove(player.getUUID());
			RECOIL.remove(player.getUUID());
			if (player.getAttachedOrElse(ModAttachments.FIREARM_AIMING, false)) {
				player.setAttached(ModAttachments.FIREARM_AIMING, false);
			}
			return;
		}
		FirearmData data = Firearms.of(held);
		if (data == null) {
			return;
		}

		// recoil recovery
		float r = currentRecoil(player);
		if (r > 0f) {
			RECOIL.put(player.getUUID(), Math.max(0f, r - data.recoilRecoveryPerTick));
		}

		FirearmReload.tick(player, held, data);

		boolean triggerHeld = Boolean.TRUE.equals(TRIGGER_HELD.get(player.getUUID()));
		int mag = FirearmStack.magazine(held, data);

		if (triggerHeld && data.automatic && !FirearmStack.isReloading(held) && mag > 0) {
			FirearmShooting.fire(player, held, data);
		}

		// empty-magazine auto-reload when the trigger is released (spec section 40), but only when
		// there is actually reserve ammo to load -- otherwise this polls FirearmReload.start every
		// tick for nothing (the "constantly plays a sound" bug).
		if (mag <= 0 && !triggerHeld && !FirearmStack.isReloading(held)
				&& FirearmAmmo.reserveCount(player, data.ammo) > 0) {
			FirearmReload.start(player, held, data);
		}
	}

	private static ItemStack heldFirearm(ServerPlayer player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		return main.getItem() instanceof FirearmItem ? main : null;
	}
}
