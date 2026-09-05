package com.herocraft.mod.client.firearm;

import com.herocraft.mod.firearm.FirearmData;
import com.herocraft.mod.firearm.Firearms;
import com.herocraft.mod.firearm.item.FirearmItem;
import com.herocraft.mod.network.FirearmActionPayload;
import com.herocraft.mod.network.FirearmFirePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side firearm input and view feel. Left-click fires (the actual attack is suppressed by
 * {@link com.herocraft.mod.client.mixin.FirearmAttackMixin}); holding right-click aims down the
 * sights. Everything authoritative -- ammo, fire rate, damage -- is server-side; this only sends
 * edge-triggered requests and applies the returned recoil kick to the local view.
 */
public final class FirearmClient {
	private static boolean attackWasDown;
	private static boolean useWasDown;
	private static boolean holdingFirearmLast;

	/** Extra pitch offset the recoil kick is currently pushing the view by; decays each tick. */
	private static float recoilPitch;
	/** Active scope zoom step index (0 = first zoom level). Cycled with the CYCLE_ZOOM gesture. */
	private static int zoomIndex;
	private static boolean aiming;

	private FirearmClient() {
	}

	public static void clientTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.screen != null) {
			if (holdingFirearmLast && attackWasDown) {
				ClientPlayNetworking.send(new FirearmFirePayload(false));
			}
			attackWasDown = false;
			useWasDown = false;
			aiming = false;
			return;
		}

		ItemStack main = player.getMainHandItem();
		boolean holdingFirearm = main.getItem() instanceof FirearmItem;

		if (!holdingFirearm) {
			if (holdingFirearmLast) {
				if (attackWasDown) {
					ClientPlayNetworking.send(new FirearmFirePayload(false));
				}
				if (aiming) {
					ClientPlayNetworking.send(new FirearmActionPayload(FirearmActionPayload.Action.AIM_STOP));
				}
				attackWasDown = false;
				useWasDown = false;
				aiming = false;
				zoomIndex = 0;
			}
			holdingFirearmLast = false;
			decayRecoil();
			return;
		}
		holdingFirearmLast = true;

		// ---- fire (left click) ----
		boolean attackDown = mc.options.keyAttack.isDown();
		if (attackDown && !attackWasDown) {
			ClientPlayNetworking.send(new FirearmFirePayload(true));
		} else if (!attackDown && attackWasDown) {
			ClientPlayNetworking.send(new FirearmFirePayload(false));
		}
		attackWasDown = attackDown;

		// ---- aim (hold right click) ----
		boolean useDown = mc.options.keyUse.isDown();
		if (useDown && !useWasDown) {
			aiming = true;
			zoomIndex = 0;
			ClientPlayNetworking.send(new FirearmActionPayload(FirearmActionPayload.Action.AIM_START));
		} else if (!useDown && useWasDown) {
			aiming = false;
			ClientPlayNetworking.send(new FirearmActionPayload(FirearmActionPayload.Action.AIM_STOP));
		}
		useWasDown = useDown;

		decayRecoil();
	}

	private static void decayRecoil() {
		if (recoilPitch > 0f) {
			recoilPitch = Math.max(0f, recoilPitch - 0.35f);
		}
	}

	/** Server told us a shot landed -- kick the view up a little (smoothly recovered by decay). */
	public static void applyKick(float verticalKick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		recoilPitch = Math.min(6f, recoilPitch + verticalKick);
		mc.player.setXRot(mc.player.getXRot() - verticalKick * 0.6f);
	}

	// ---- state read by the HUD / FOV / overlay ----

	public static boolean aiming() {
		return aiming;
	}

	public static int zoomIndex() {
		return zoomIndex;
	}

	public static void cycleZoom() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		FirearmData d = Firearms.of(mc.player.getMainHandItem());
		if (d == null || d.zoomLevels.length <= 1) {
			return;
		}
		zoomIndex = (zoomIndex + 1) % d.zoomLevels.length;
		ClientPlayNetworking.send(new FirearmActionPayload(FirearmActionPayload.Action.CYCLE_ZOOM));
	}

	/** True when the scroll wheel should cycle zoom instead of the hotbar. */
	public static boolean canScrollZoom() {
		Minecraft mc = Minecraft.getInstance();
		if (!aiming || mc.player == null || mc.screen != null) {
			return false;
		}
		FirearmData d = Firearms.of(mc.player.getMainHandItem());
		return d != null && d.zoomLevels.length > 1;
	}

	/** Mouse-look slowdown while scoped past the first zoom step (1.0 = no change). */
	public static float scopedSensitivityFactor() {
		Minecraft mc = Minecraft.getInstance();
		if (!aiming || mc.player == null) {
			return 1.0f;
		}
		FirearmData d = Firearms.of(mc.player.getMainHandItem());
		if (d == null || !d.scopeOverlay || zoomIndex < 1) {
			return 1.0f;
		}
		// scale the configured factor further by how deep the zoom is
		float deep = 1.0f - (zoomIndex / (float) Math.max(1, d.zoomLevels.length)) * 0.5f;
		return Math.max(0.15f, d.scopedSensitivityFactor * deep);
	}

	/** FOV multiplier to apply right now (1.0 = no zoom). */
	public static float zoomFactor() {
		Minecraft mc = Minecraft.getInstance();
		if (!aiming || mc.player == null) {
			return 1.0f;
		}
		FirearmData d = Firearms.of(mc.player.getMainHandItem());
		if (d == null || d.zoomLevels.length == 0) {
			return 1.0f;
		}
		return d.zoomLevels[Math.min(zoomIndex, d.zoomLevels.length - 1)];
	}

	/** True when the full sniper-scope overlay should be drawn. */
	public static boolean scopeOverlayActive() {
		Minecraft mc = Minecraft.getInstance();
		if (!aiming || mc.player == null) {
			return false;
		}
		FirearmData d = Firearms.of(mc.player.getMainHandItem());
		return d != null && d.scopeOverlay && zoomIndex >= 1;
	}
}
