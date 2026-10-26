package com.projecthero.mod.client.speedforce;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.speedforce.network.SprintKeyPayload;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

/**
 * v0.17.1 (user: "i want momentum to increase only when im holding ctrl and whether i have toggle sprint on or hold sprint
 * on shouldnt matter"): tells the server whether the sprint key is PHYSICALLY down. {@code keySprint.isDown()} can't be
 * used -- with Toggle Sprint it stays "down" after the key is let go -- so the bound key (keyboard or mouse button) is
 * read straight from GLFW. A key held while a screen is open doesn't count. Sent on every change, refreshed once a second.
 */
public final class SpeedForceSprintKey {
	private static Boolean sent;
	private static int sinceSent;

	private SpeedForceSprintKey() {
	}

	static void tick(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null || !SpeedForceSpeedster.hasPower(mc.player)) {
			sent = null;
			return;
		}
		boolean held = mc.screen == null && physicallyDown(mc);
		sinceSent++;
		if (sent == null || sent != held || sinceSent >= 20) {
			if (ClientPlayNetworking.canSend(SprintKeyPayload.TYPE)) {
				ClientPlayNetworking.send(new SprintKeyPayload(held));
			}
			sent = held;
			sinceSent = 0;
		}
	}

	private static boolean physicallyDown(Minecraft mc) {
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mc.options.keySprint);
		long window = mc.getWindow().getWindow();
		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
		}
		return key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
	}
}
