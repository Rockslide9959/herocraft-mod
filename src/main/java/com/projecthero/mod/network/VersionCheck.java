package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.14.22: client/server Project Hero version handshake.
 *
 * <p>Why: Fabric syncs the block registry between sides, but each side builds its own block-state id table
 * from its own copy of the mod. When a version adds a property to a block (0.14.21 gave the Suit Platform
 * and Stark Fabricator a FACING / WORKING state), every block registered after it gets a different state id
 * on a mismatched client -- Hard-Light constructs rendered as Stark Fabricators, and so on. Nothing warned
 * the player, so we now refuse the join with a clear message instead.
 *
 * <p>Flow: the payload is registered in both directions. On join the server sends its version; a client
 * that cannot receive it is older than 0.14.22 and is kicked. A client that can receives it and replies
 * with its own version, which the server compares. The client side also warns when the <em>server</em>
 * is too old to take part (see {@code VersionCheckClient}).
 *
 * <p>Only runs for players on a real network socket (dedicated server or LAN guests) -- the singleplayer host is
 * on an in-memory channel and always matches, and GameTest mock players sit on an embedded channel with no client to
 * answer (the first cut gated on {@code isDedicatedServer()}, which is true on the gametest server, and kicked every
 * mock player).
 */
public final class VersionCheck {
	private VersionCheck() {
	}

	public record Payload(String version) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Payload> TYPE =
				new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "version_check"));

		public static final StreamCodec<FriendlyByteBuf, Payload> CODEC =
				ByteBufCodecs.STRING_UTF8.map(Payload::new, Payload::version).cast();

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** This jar's version, e.g. {@code "0.14.22"}. */
	public static String localVersion() {
		return FabricLoader.getInstance().getModContainer(ProjectHeroMod.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}

	/**
	 * Kick text. Literal on purpose: an older client would not have a new translation key in its lang file.
	 */
	public static Component mismatchMessage(String serverVersion, String clientVersion) {
		return Component.literal("Project Hero version mismatch!\n\n"
				+ "Server: " + serverVersion + "\nYou: " + clientVersion + "\n\n"
				+ "Install Project Hero " + serverVersion + " (and remove any other Project Hero jars from your "
				+ "mods folder). Mismatched versions make blocks show up as the wrong block.");
	}

	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(Payload.TYPE, Payload.CODEC);
		PayloadTypeRegistry.playC2S().register(Payload.TYPE, Payload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (!shouldCheck(server, player)) {
				return;
			}
			if (ServerPlayNetworking.canSend(player, Payload.TYPE)) {
				ServerPlayNetworking.send(player, new Payload(localVersion()));
			} else {
				// Has no receiver for the handshake, so it predates 0.14.22.
				server.execute(() -> handler.disconnect(mismatchMessage(localVersion(), "older than 0.14.22")));
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) -> {
			String server = localVersion();
			if (!server.equals(payload.version())) {
				ProjectHeroMod.LOGGER.warn("Disconnecting {}: Project Hero {} on client, {} on server",
						context.player().getGameProfile().getName(), payload.version(), server);
				context.player().connection.disconnect(mismatchMessage(server, payload.version()));
			}
		});
	}

	private static boolean shouldCheck(MinecraftServer server, ServerPlayer player) {
		if (server instanceof net.minecraft.gametest.framework.GameTestServer
				|| server.isSingleplayerOwner(player.getGameProfile())) {
			return false;
		}
		return player.connection.getRemoteAddress() instanceof java.net.InetSocketAddress;
	}
}
