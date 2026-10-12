package com.projecthero.mod.hero;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

/**
 * v0.16.1: the {@code projectheroUniquePowers} gamerule (default off). While it is on, each Hero-Tier power
 * ({@link PowerGrants#HERO_TIER_KEYS}: every Primary hero plus the Symbiote) can be held by only one player at a time;
 * the mutations are never restricted.
 *
 * <h2>How it stays in sync</h2>
 * {@link UniquePowersData} maps each power to its holders. A holder is recorded the moment a power is claimed
 * ({@link HeroTiers#claimPrimary} and {@link com.projecthero.mod.symbiote.Symbiote#grant} call {@link #onClaimed}),
 * and every online player is reconciled once a second, on join and on leave -- so a power lost any other way (revoke
 * command, a new Primary replacing it, death, the Symbiote leaving) frees its slot without every revoke path having to
 * know about this class. Offline holders keep their slot. The table is kept whether or not the rule is on.
 *
 * <h2>How a grant is refused</h2>
 * Every acquisition path asks {@link #refuse} <em>before</em> it consumes anything (serum, item, levels, a ritual
 * scarab, a Gamma dose...), and every hero's own grant routine asks again as a safety net. A refusal tells the player
 * "&lt;power&gt; is already held by &lt;player&gt;". Operators can override with {@code /projecthero power grant <power>
 * <player> force} ({@link #forced}).
 */
public final class UniquePowers {
	public static final String RULE_NAME = "projectheroUniquePowers";
	public static GameRules.Key<GameRules.BooleanValue> RULE;

	/** How often (ticks) online players are reconciled against the table. */
	private static final int SCAN_INTERVAL = 20;
	/** Minimum ticks between two refusal messages to one player (a Mjolnir on the ground is touched every tick). */
	private static final int MESSAGE_COOLDOWN = 20;

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> FORCED = ThreadLocal.withInitial(() -> false);

	private UniquePowers() {
	}

	public static void initialize() {
		RULE = GameRuleRegistry.register(RULE_NAME, GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(false));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SCAN_INTERVAL == 0) {
				for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
					reconcile(p, null);
				}
			}
		});
		ServerPlayerEvents.JOIN.register(UniquePowers::onJoin);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			if (p != null) {
				reconcile(p, null);
				LAST_MESSAGE.remove(p.getUUID());
			}
		});
	}

	/** {@code ServerStateReset}: the message throttle is the only static state. */
	public static void clearSessionState() {
		LAST_MESSAGE.clear();
	}

	// ---------------------------------------------------------------- queries

	/** Whether {@code key} is a power this rule restricts (Hero-Tier heroes and the Symbiote, never a mutation). */
	public static boolean isTracked(String key) {
		return PowerGrants.HERO_TIER_KEYS.contains(key);
	}

	public static boolean enabled(MinecraftServer server) {
		return RULE != null && server.getGameRules().getBoolean(RULE);
	}

	/** Tests / admin tooling. */
	public static void setEnabled(MinecraftServer server, boolean on) {
		server.getGameRules().getRule(RULE).set(on, server);
	}

	/** Whether the player currently holds {@code key}. Thor counts from the moment the player is worthy. */
	public static boolean holds(ServerPlayer player, String key) {
		if ("thor".equals(key)) {
			return Worthiness.isWorthy(player) || HeroTiers.holdsHero(player, "thor");
		}
		return PowerGrants.holdsHeroTier(player, key);
	}

	/**
	 * The name of a holder that stops {@code player} gaining {@code key}, or null when nothing does (rule off, an
	 * untracked power, nobody else holds it, or an operator override is running). An online "holder" who no longer has
	 * the power is dropped from the table on the spot rather than blocking.
	 */
	@Nullable
	public static String blockingHolder(ServerPlayer player, String key) {
		if (FORCED.get() || !isTracked(key) || player.getServer() == null || !enabled(player.getServer())) {
			return null;
		}
		MinecraftServer server = player.getServer();
		UniquePowersData data = UniquePowersData.get(server);
		for (Map.Entry<UUID, String> e : List.copyOf(data.holders(key).entrySet())) {
			if (e.getKey().equals(player.getUUID())) {
				continue;
			}
			ServerPlayer online = server.getPlayerList().getPlayer(e.getKey());
			if (online != null && !holds(online, key)) {
				data.remove(key, e.getKey());
				continue;
			}
			return online != null ? online.getGameProfile().getName() : e.getValue();
		}
		return null;
	}

	/** "&lt;power&gt; is already held by &lt;player&gt;". */
	public static MutableComponent takenMessage(String key, String holder) {
		return Component.translatable("message.projecthero.unique_powers.taken",
				Component.translatable("projecthero.hero_tier." + key), holder).withStyle(ChatFormatting.RED);
	}

	/**
	 * The gate every acquisition path calls before it consumes anything: true (and the player is told who holds it) if
	 * {@code player} may not gain {@code key} right now.
	 */
	public static boolean refuse(ServerPlayer player, String key) {
		String holder = blockingHolder(player, key);
		if (holder == null) {
			return false;
		}
		long now = player.getServer().getTickCount();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last == null || now - last >= MESSAGE_COOLDOWN || now < last) {
			LAST_MESSAGE.put(player.getUUID(), now);
			player.displayClientMessage(takenMessage(key, holder), false);
		}
		return true;
	}

	/** Runs {@code action} with the rule suspended on this thread (operator {@code force} grants). */
	public static <T> T forced(Supplier<T> action) {
		boolean previous = FORCED.get();
		FORCED.set(true);
		try {
			return action.get();
		} finally {
			FORCED.set(previous);
		}
	}

	// ---------------------------------------------------------------- bookkeeping

	/** A power is being handed to {@code player} right now: record them (and drop anything they just lost). */
	public static void onClaimed(ServerPlayer player, String key) {
		reconcile(player, key);
	}

	/**
	 * Brings the table in line with what {@code player} actually holds. {@code claiming} counts as held even though the
	 * grant routine has not finished setting it yet.
	 */
	public static void reconcile(ServerPlayer player, @Nullable String claiming) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		UniquePowersData data = UniquePowersData.get(server);
		String name = player.getGameProfile().getName();
		for (String key : PowerGrants.HERO_TIER_KEYS) {
			if (key.equals(claiming) || holds(player, key)) {
				data.add(key, player.getUUID(), name);
			} else {
				data.remove(key, player.getUUID());
			}
		}
	}

	private static void onJoin(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		for (String key : UniquePowersData.get(server).takePendingRevokes(player.getUUID())) {
			if (holds(player, key)) {
				PowerGrants.revokeHeroTier(player, key);
				player.sendSystemMessage(Component.translatable("message.projecthero.unique_powers.released",
						Component.translatable("projecthero.hero_tier." + key)).withStyle(ChatFormatting.GOLD));
			}
		}
		reconcile(player, null);
	}

	/**
	 * Frees {@code key}: every online holder loses the power now, every offline holder the next time they join, and the
	 * slot is open at once. Returns how many holders there were.
	 */
	public static int release(MinecraftServer server, String key) {
		UniquePowersData data = UniquePowersData.get(server);
		List<UUID> ids = new ArrayList<>(data.holders(key).keySet());
		for (UUID id : ids) {
			ServerPlayer online = server.getPlayerList().getPlayer(id);
			if (online != null) {
				if (holds(online, key)) {
					PowerGrants.revokeHeroTier(online, key);
					online.sendSystemMessage(Component.translatable("message.projecthero.unique_powers.released",
							Component.translatable("projecthero.hero_tier." + key)).withStyle(ChatFormatting.GOLD));
				}
			} else {
				data.addPendingRevoke(key, id);
			}
		}
		data.clear(key);
		return ids.size();
	}

	// ---------------------------------------------------------------- /projecthero uniquepowers

	public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
		return Commands.literal("uniquepowers")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("list").executes(UniquePowers::list))
				.then(Commands.literal("release")
						.then(Commands.argument("power", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(PowerGrants.HERO_TIER_KEYS, b))
								.executes(UniquePowers::releaseCommand)));
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
			reconcile(p, null);
		}
		boolean on = enabled(server);
		c.getSource().sendSuccess(() -> Component.literal("Unique powers: ")
				.append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY))
				.append(Component.literal("  (/gamerule " + RULE_NAME + ")").withStyle(ChatFormatting.DARK_GRAY)), false);
		int n = 0;
		for (Map.Entry<String, java.util.LinkedHashMap<UUID, String>> e : UniquePowersData.get(server).all().entrySet()) {
			List<String> names = new ArrayList<>();
			for (Map.Entry<UUID, String> h : e.getValue().entrySet()) {
				boolean online = server.getPlayerList().getPlayer(h.getKey()) != null;
				names.add(h.getValue() + (online ? "" : " (offline)"));
			}
			MutableComponent line = Component.literal("  ").append(Component.translatable("projecthero.hero_tier." + e.getKey())
					.withStyle(ChatFormatting.GOLD)).append(Component.literal(": " + String.join(", ", names)).withStyle(ChatFormatting.WHITE));
			c.getSource().sendSuccess(() -> line, false);
			n++;
		}
		if (n == 0) {
			c.getSource().sendSuccess(() -> Component.literal("  Nobody holds a hero power.").withStyle(ChatFormatting.GRAY), false);
		}
		return n;
	}

	private static int releaseCommand(CommandContext<CommandSourceStack> c) {
		String key = StringArgumentType.getString(c, "power");
		if (!isTracked(key)) {
			c.getSource().sendFailure(Component.literal("Unknown hero power (" + String.join(", ", PowerGrants.HERO_TIER_KEYS) + ")"));
			return 0;
		}
		int n = release(c.getSource().getServer(), key);
		c.getSource().sendSuccess(() -> Component.literal(n == 0 ? "Nobody held " + key
				: "Released " + key + " from " + n + " holder(s); offline holders lose it when they next join"), true);
		return n;
	}
}
