package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.gear.StarkGear;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/** v0.15.1: client side of the Stark Gear slot -- the screen, the glasses on the face, and the Sneak + N request. */
public final class StarkGearClient {
	private StarkGearClient() {
	}

	public static void initialize() {
		MenuScreens.register(StarkGear.MENU, com.projecthero.mod.client.gui.StarkGearScreen::new);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new StarkGlassesLayer(playerRenderer));
			}
		});
	}

	/** Sneak + N belongs to the Stark Gear screen for a Tony Stark player (or anyone still wearing the glasses). */
	public static boolean ownsSneakN(Minecraft client) {
		return client.player != null && client.screen == null
				&& (TonyStark.hasPower(client.player) || StarkGear.hasGlasses(client.player));
	}

	public static void requestOpen() {
		ClientPlayNetworking.send(new StarkGear.OpenPayload());
	}
}
