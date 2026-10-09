package com.projecthero.mod.firearm;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.firearm.item.FirearmItem;
import com.projecthero.mod.network.FirearmShotPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Per-player firearm upkeep: the trigger-held flag (so automatic fire is server-timed, not client-
 * spammed), the transient recoil accumulator, reload ticking, empty-magazine auto-reload, and
 * lowering the sights when the weapon is put away.
 *
 * <p>All per-player scratch here is static and world-object-free, but it is still registered with
 * {@link com.projecthero.mod.diagnostics.ServerStateReset} so it is dropped on server stop.
 */
public final class FirearmManager {
	private static final Map<UUID, Boolean> TRIGGER_HELD = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> RECOIL = new ConcurrentHashMap<>();

	/**
	 * v0.15.18 (user: move 30% slower while aiming): a transient movement-speed modifier held while a gun is aimed down
	 * its sights. Reconciled every tick in {@link #serverTick} against the synced {@link ModAttachments#FIREARM_AIMING}
	 * flag + a held firearm, so it can never stick (being transient it is not saved on logout, and a respawned player
	 * starts without it).
	 */
	public static final ResourceLocation ADS_SLOW_ID = ProjectHeroMod.id("firearm_ads_slow");
	public static final double ADS_SLOW = -0.3;

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
		syncAdsSlow(player, held != null && player.isAlive() && !player.isSpectator()
				&& player.getAttachedOrElse(ModAttachments.FIREARM_AIMING, false));
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

	/** Adds / removes the aim-down-sights slowdown so the speed attribute matches {@code aiming}. */
	public static void syncAdsSlow(ServerPlayer player, boolean aiming) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean has = speed.hasModifier(ADS_SLOW_ID);
		if (aiming && !has) {
			speed.addTransientModifier(new AttributeModifier(ADS_SLOW_ID, ADS_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!aiming && has) {
			speed.removeModifier(ADS_SLOW_ID);
		}
	}

	private static ItemStack heldFirearm(ServerPlayer player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		return main.getItem() instanceof FirearmItem ? main : null;
	}
}
