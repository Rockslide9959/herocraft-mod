package com.projecthero.mod.client.saiyan;

import com.mojang.blaze3d.platform.InputConstants;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.network.SaiyanActionPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

import org.lwjgl.glfw.GLFW;

/**
 * v0.17.3: the Saiyan's client side (Wave 1) -- the HUD, the H form wheel and the Ki charge key. The charge key is its own
 * binding ("Saiyan: Charge Ki", default Q). While a Saiyan holds it, the vanilla Drop key (also Q by default) is held back so
 * charging never throws the held item away; Ctrl+Q still drops a whole stack. Press and release go to the server on the
 * edges. Hair and aura are drawn elsewhere ({@code client.saiyan.hair} / {@code client.saiyan.aura}).
 */
public final class SaiyanClient {
	public static final KeyMapping CHARGE = new KeyMapping("key.projecthero.saiyan_charge", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q,
			"key.category.projecthero.abilities");

	private static boolean chargeSent;

	private SaiyanClient() {
	}

	public static void initialize() {
		KeyBindingHelper.registerKeyBinding(CHARGE);
		HudRenderCallback.EVENT.register(SaiyanHud::render);
		ClientTickEvents.START_CLIENT_TICK.register(SaiyanClient::holdBackDrop);
		ClientTickEvents.END_CLIENT_TICK.register(SaiyanClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SaiyanLoopSounds::tick); // the aura and charge loops (SaiyanSounds)
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			chargeSent = false;
			SaiyanLoopSounds.reset();
		});
	}

	/** H (not sneaking) as a Saiyan: the form wheel. Shift+H still falls through to the power wheel. */
	public static boolean ownsH(LocalPlayer player) {
		return player != null && Saiyan.hasPower(player) && !Screen.hasShiftDown();
	}

	public static void pressH() {
		Minecraft.getInstance().setScreen(new SaiyanFormWheelScreen());
	}

	/** Before vanilla reads its keys: a Saiyan holding the charge key on the Drop key's key doesn't drop (Ctrl+Q still does). */
	private static void holdBackDrop(Minecraft client) {
		if (client.player == null || !Saiyan.hasPower(client.player) || !CHARGE.isDown()) {
			return;
		}
		KeyMapping drop = client.options.keyDrop;
		if (!drop.same(CHARGE) || Screen.hasControlDown()) {
			return;
		}
		while (drop.consumeClick()) {
			// swallowed: Q is charging Ki
		}
	}

	private static void tick(Minecraft client) {
		boolean connected = client.player != null && client.getConnection() != null;
		boolean want = connected && Saiyan.hasPower(client.player) && CHARGE.isDown() && client.screen == null;
		if (want != chargeSent && connected) {
			ClientPlayNetworking.send(new SaiyanActionPayload(want ? SaiyanActionPayload.Action.CHARGE_START
					: SaiyanActionPayload.Action.CHARGE_STOP, 0));
			chargeSent = want;
		} else if (!connected) {
			chargeSent = false;
		}
	}
}
