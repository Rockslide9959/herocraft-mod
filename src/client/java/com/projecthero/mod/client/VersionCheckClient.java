package com.projecthero.mod.client;

import com.projecthero.mod.network.VersionCheck;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * v0.14.22: client half of {@link VersionCheck}. Answers the server's version with ours (the server does the
 * comparing and the kicking), and warns in chat when the server is too old to send a version at all.
 */
public final class VersionCheckClient {
	private VersionCheckClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(VersionCheck.Payload.TYPE, (payload, context) ->
				context.responseSender().sendPacket(new VersionCheck.Payload(VersionCheck.localVersion())));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (client.hasSingleplayerServer() || ClientPlayNetworking.canSend(VersionCheck.Payload.TYPE)) {
				return;
			}
			// The server has no receiver for the handshake: it runs Project Hero older than 0.14.22.
			client.execute(() -> client.gui.getChat().addMessage(Component.translatable(
					"message.projecthero.version_check.old_server", VersionCheck.localVersion())
					.withStyle(ChatFormatting.RED)));
		});
	}
}
