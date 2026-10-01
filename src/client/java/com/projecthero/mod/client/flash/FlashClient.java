package com.projecthero.mod.client.flash;

import com.projecthero.mod.flash.FlashRing;
import com.projecthero.mod.flash.FlashSuit;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** v0.14.11: the Flash Suit / Flash Ring client side -- the ring layer, the light show and the H key. */
public final class FlashClient {
	private FlashClient() {
	}

	public static void initialize() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new FlashRingLayer(playerRenderer));
			}
		});
		FlashFxClient.init();
	}

	/**
	 * Whether plain H belongs to the Flash Ring for this player: a speedster wearing a Flash piece, wearing a ring that
	 * holds the suit, or carrying one in the inventory.
	 */
	public static boolean ownsH(LocalPlayer player) {
		if (!FlashSuit.mayWear(player)) {
			return false;
		}
		if (FlashSuit.wearsAny(player) || FlashRing.holdsSuit(player)) {
			return true;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var s = inv.getItem(i);
			if (s.is(FlashSuit.RING) && com.projecthero.mod.flash.item.FlashRingItem.pieces(s) > 0) {
				return true;
			}
		}
		return false;
	}

	public static void pressH() {
		ClientPlayNetworking.send(new FlashRing.TogglePayload());
	}
}
