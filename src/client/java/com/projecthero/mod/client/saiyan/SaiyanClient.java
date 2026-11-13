package com.projecthero.mod.client.saiyan;

import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.network.SaiyanActionPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

/**
 * v0.17.3 Saiyan revamp: the Saiyan's client side -- the HUD, H (Super Saiyan once the Rage bar is full; the server says why not
 * otherwise) and the double-tap-jump flight request. V (charge Ki / Kaioken) and the moves go through the ability slots. The aura
 * and tail are drawn elsewhere ({@code client.saiyan.aura}, {@code SaiyanTailRenderer}).
 */
public final class SaiyanClient {
	private SaiyanClient() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(SaiyanHud::render);
		SaiyanVisualsClient.initialize(); // the aura
		ClientTickEvents.END_CLIENT_TICK.register(SaiyanLoopSounds::tick); // the aura and charge loops (SaiyanSounds)
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SaiyanLoopSounds.reset());
	}

	/** H (not sneaking) as a Saiyan: transform. Shift+H still falls through to the power wheel. */
	public static boolean ownsH(LocalPlayer player) {
		return player != null && Saiyan.hasPower(player) && !Screen.hasShiftDown();
	}

	public static void pressH() {
		ClientPlayNetworking.send(new SaiyanActionPayload(SaiyanActionPayload.Action.TRANSFORM, 0));
	}

	/** Double-tap jump in the air as a Saiyan: take off / drop out of flight (the server re-checks). */
	public static boolean wantsFlightToggle(LocalPlayer player) {
		return player != null && !player.onGround() && Saiyan.hasPower(player);
	}

	public static void toggleFlight() {
		ClientPlayNetworking.send(new SaiyanActionPayload(SaiyanActionPayload.Action.TOGGLE_FLIGHT, 0));
	}
}
