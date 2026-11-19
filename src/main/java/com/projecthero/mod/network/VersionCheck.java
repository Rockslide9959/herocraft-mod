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

	// ---------------------------------------------------------------- v0.17.5: the build id

	/**
	 * v0.17.5: the build check. The version stayed {@code 0.17.5} across many test builds, so two different 0.17.5 jars passed the
	 * version check above but had different items / attachments, and the joining player failed with no clear reason (a tester could
	 * not rejoin the host's world). Every jar now carries a build id ({@code projecthero_build.properties}, written by the
	 * {@code generateBuildInfo} Gradle task: the git commit + the build time). In the CONFIGURATION phase -- before Fabric's
	 * registry sync -- the server sends its version + build id and the client answers with its own; a different build is refused
	 * with {@link #buildMismatchMessage}. A client too old to answer (any jar from before this check) is refused the same way.
	 *
	 * <p>Not checked: the singleplayer / LAN host itself (in-memory connection, always the same jar) and GameTest mock players (they
	 * never go through configuration). A jar without the file (an IDE run) reports {@link #DEV_BUILD}, which matches any build.
	 */
	public record BuildPayload(String version, String buildId, String built) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<BuildPayload> TYPE =
				new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "build_check"));

		public static final StreamCodec<FriendlyByteBuf, BuildPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, BuildPayload::version,
				ByteBufCodecs.STRING_UTF8, BuildPayload::buildId,
				ByteBufCodecs.STRING_UTF8, BuildPayload::built,
				BuildPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		/** This jar's payload. */
		public static BuildPayload local() {
			return new BuildPayload(localVersion(), VersionCheck.buildId(), VersionCheck.buildTime());
		}
	}

	/** The id a jar without {@code projecthero_build.properties} reports (an IDE run); it matches any build. */
	public static final String DEV_BUILD = "dev";

	private static java.util.Properties buildInfo;

	private static synchronized java.util.Properties buildInfo() {
		if (buildInfo == null) {
			java.util.Properties p = new java.util.Properties();
			try {
				java.util.Optional<java.nio.file.Path> path = FabricLoader.getInstance().getModContainer(ProjectHeroMod.MOD_ID)
						.flatMap(c -> c.findPath("projecthero_build.properties"));
				if (path.isPresent()) {
					try (java.io.InputStream in = java.nio.file.Files.newInputStream(path.get())) {
						p.load(in);
					}
				}
			} catch (Exception e) {
				ProjectHeroMod.LOGGER.warn("Couldn't read projecthero_build.properties; treating this jar as a dev build", e);
			}
			buildInfo = p;
		}
		return buildInfo;
	}

	/** This jar's build id, e.g. {@code "20cc782a5-20261119143005"}, or {@link #DEV_BUILD}. */
	public static String buildId() {
		String id = buildInfo().getProperty("id", "").trim();
		return id.isEmpty() ? DEV_BUILD : id;
	}

	/** When this jar was built, e.g. {@code "2026-11-19 14:30 UTC"}, or "" for a dev build. */
	public static String buildTime() {
		return buildInfo().getProperty("built", "").trim();
	}

	/** Whether a joining client's build may play with this server's. Versions must match; build ids too unless either is a dev build. */
	public static boolean sameBuild(String serverVersion, String serverId, String clientVersion, String clientId) {
		if (!serverVersion.equals(clientVersion)) {
			return false;
		}
		return DEV_BUILD.equals(serverId) || DEV_BUILD.equals(clientId) || serverId.equals(clientId);
	}

	/** "20cc782a5-20261119143005, built 2026-11-19 14:30 UTC" (or the id alone). */
	public static String describe(String id, String built) {
		return built == null || built.isBlank() ? id : id + ", built " + built;
	}

	/** Kick text for a different build of the same version. Literal on purpose (an older client lacks newer lang keys). */
	public static Component buildMismatchMessage(String hostDescription, String yoursDescription) {
		return Component.literal("Your Project Hero jar is a different build from the host's (yours: " + yoursDescription + ", host: "
				+ hostDescription + "). Get the latest jar and try again.");
	}

	/** Whether {@code profile}'s connection is checked: not on the GameTest server, not the singleplayer / LAN host. */
	public static boolean checksBuild(MinecraftServer server, com.mojang.authlib.GameProfile profile) {
		return !(server instanceof net.minecraft.gametest.framework.GameTestServer) && !server.isSingleplayerOwner(profile);
	}

	/** The configuration task: send our build, wait for the client's answer (the receiver completes or disconnects). */
	private record BuildTask() implements net.minecraft.server.network.ConfigurationTask {
		static final net.minecraft.server.network.ConfigurationTask.Type KEY =
				new net.minecraft.server.network.ConfigurationTask.Type(ProjectHeroMod.MOD_ID + ":build_check");

		@Override
		public void start(java.util.function.Consumer<net.minecraft.network.protocol.Packet<?>> sender) {
			sender.accept(net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking.createS2CPacket(BuildPayload.local()));
		}

		@Override
		public net.minecraft.server.network.ConfigurationTask.Type type() {
			return KEY;
		}
	}

	private static void initBuildCheck() {
		PayloadTypeRegistry.configurationS2C().register(BuildPayload.TYPE, BuildPayload.CODEC);
		PayloadTypeRegistry.configurationC2S().register(BuildPayload.TYPE, BuildPayload.CODEC);

		// BEFORE_CONFIGURE, in a phase ahead of the default one: Fabric adds its registry-sync task in the default phase, and tasks
		// run in the order they are added -- so a different build is refused before any registry data is exchanged
		ResourceLocation early = ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "build_check");
		var event = net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents.BEFORE_CONFIGURE;
		event.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
		event.register(early, (handler, server) -> {
			if (!checksBuild(server, handler.getOwner())) {
				return;
			}
			if (net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking.canSend(handler, BuildPayload.TYPE)) {
				handler.addTask(new BuildTask());
			} else {
				// no receiver for the build check: a jar from before it (every such jar is a different build)
				BuildPayload host = BuildPayload.local();
				ProjectHeroMod.LOGGER.warn("Refusing {}: their Project Hero jar predates the build check (host build {})",
						handler.getOwner().getName(), host.buildId());
				handler.disconnect(buildMismatchMessage(describe(host.buildId(), host.built()), "an older build"));
			}
		});

		net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking.registerGlobalReceiver(BuildPayload.TYPE, (payload, context) -> {
			BuildPayload host = BuildPayload.local();
			if (sameBuild(host.version(), host.buildId(), payload.version(), payload.buildId())) {
				context.networkHandler().completeTask(BuildTask.KEY);
				return;
			}
			ProjectHeroMod.LOGGER.warn("Refusing {}: Project Hero {} build {} on the client, {} build {} on the server",
					context.networkHandler().getOwner().getName(), payload.version(), payload.buildId(), host.version(), host.buildId());
			Component msg = host.version().equals(payload.version())
					? buildMismatchMessage(describe(host.buildId(), host.built()), describe(payload.buildId(), payload.built()))
					: mismatchMessage(host.version(), payload.version());
			context.networkHandler().disconnect(msg);
		});
	}

	public static void initialize() {
		initBuildCheck();
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
