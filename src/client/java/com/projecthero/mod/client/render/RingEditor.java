package com.projecthero.mod.client.render;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** v0.14.23: opens {@link RingEditorScreen} -- the client command {@code /ringeditor}, or the (unbound) "Ring Editor" key. */
public final class RingEditor {
	public static final KeyMapping KEY = new KeyMapping("key.projecthero.ring_editor", InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN, "key.category.projecthero.abilities");

	private RingEditor() {
	}

	public static void init() {
		KeyBindingHelper.registerKeyBinding(KEY);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (KEY.consumeClick()) {
				if (client.screen == null && client.player != null) {
					client.setScreen(new RingEditorScreen());
				}
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("ringeditor").executes(ctx -> {
					// after the chat screen has closed
					Minecraft mc = Minecraft.getInstance();
					mc.tell(() -> mc.setScreen(new RingEditorScreen()));
					return 1;
				})));
	}
}
