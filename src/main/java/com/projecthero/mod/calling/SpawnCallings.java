package com.projecthero.mod.calling;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkConfig;
import com.projecthero.mod.quirk.Quirks;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.6 (user): the first time a player spawns into a world, a power may choose them -- 5% the Green Lantern Power
 * Ring, otherwise 5% a random Miraculous (Tikki, Plagg, Trixx or Nooroo). Either is an OFFER: a light descends, chat says
 * who chose them, and clickable [Accept] / [Refuse] run {@code /herocalling accept|refuse}. Accepting grants it through
 * the power's own path ({@code GreenLantern.bond} / {@code Miraculous.grant}, which replace the Primary power and
 * respect {@code projectheroUniquePowers}); refusing, ignoring it for {@link #OFFER_TICKS} or logging out lets it go.
 * v0.16.7 (user): when both miss, {@code QuirkConfig.SPAWN_CHANCE} (5%) a random Quirk awakens the same way
 * ({@code Quirks.grant} on Accept).
 *
 * <p>"First spawn" = the {@link ModAttachments#SPAWN_CALLING_DONE} flag is not set yet AND the player has under
 * {@link #NEW_PLAYER_PLAY_TICKS} of play time in this world, so worlds that existed before this update don't roll for
 * players who have already been playing. The flag is set either way, so it is one roll per player per world. The
 * {@code projectheroSpawnCallings} gamerule (default on) turns the whole thing off.
 */
public final class SpawnCallings {
	public static final String RULE_NAME = "projectheroSpawnCallings";
	public static GameRules.Key<GameRules.BooleanValue> RULE;

	public static final float RING_CHANCE = 0.05f;
	public static final float MIRACULOUS_CHANCE = 0.05f;
	/** The offer appears this long after joining (the world has loaded, the player can read chat). */
	public static final int OFFER_DELAY_TICKS = 100;
	/** How long an offer stays open. */
	public static final int OFFER_TICKS = 5 * 60 * 20;
	/** Under a minute of play time in this world counts as a first spawn. */
	public static final int NEW_PLAYER_PLAY_TICKS = 60 * 20;

	/** Test hook: forces the next roll (null = roll the dice). */
	public static @Nullable Offer forceNext;

	public enum Kind {
		GREEN_LANTERN,
		MIRACULOUS,
		/** v0.16.7: a Quirk ({@link Offer#quirk}). */
		QUIRK
	}

	/**
	 * {@code kwami} only for MIRACULOUS, {@code quirk} only for QUIRK. {@code showAt}: game time it is announced;
	 * {@code expiresAt}: when it lapses.
	 */
	public record Offer(Kind kind, @Nullable Kwami kwami, long showAt, long expiresAt, boolean shown, @Nullable Quirk quirk) {
		public Offer(Kind kind, @Nullable Kwami kwami, long showAt, long expiresAt, boolean shown) {
			this(kind, kwami, showAt, expiresAt, shown, null);
		}

		Offer announced() {
			return new Offer(kind, kwami, showAt, expiresAt, true, quirk);
		}
	}

	@FunctionalInterface
	private interface ParticleSink {
		void send(net.minecraft.core.particles.ParticleOptions opt, double x, double y, double z, int n, double dx, double dy, double dz, double speed);
	}

	private static final Map<UUID, Offer> PENDING = new ConcurrentHashMap<>();

	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.25f, 1.0f, 0.35f), 0.8f);

	private SpawnCallings() {
	}

	public static void initialize() {
		RULE = GameRuleRegistry.register(RULE_NAME, GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
		ServerPlayerEvents.JOIN.register(SpawnCallings::onJoin);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.getPlayer().getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(SpawnCallings::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	public static void clearSessionState() {
		PENDING.clear();
		forceNext = null;
	}

	// ---------------------------------------------------------------- the roll

	static void onJoin(ServerPlayer player) {
		// fake players (other mods' machines, test harness dummies) are never chosen
		if (player instanceof net.fabricmc.fabric.api.entity.FakePlayer) {
			return;
		}
		if (Boolean.TRUE.equals(player.getAttachedOrElse(ModAttachments.SPAWN_CALLING_DONE, false))) {
			return;
		}
		player.setAttached(ModAttachments.SPAWN_CALLING_DONE, true);
		boolean fresh = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) < NEW_PLAYER_PLAY_TICKS;
		if (!fresh && forceNext == null) {
			return;
		}
		if (!player.serverLevel().getGameRules().getBoolean(RULE) && forceNext == null) {
			return;
		}
		Offer o = roll(player);
		if (o != null) {
			PENDING.put(player.getUUID(), o);
		}
	}

	/** One roll: the ring first, then a Miraculous, then (v0.16.7) a Quirk. Null = nothing chose them. */
	public static @Nullable Offer roll(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (forceNext != null) {
			Offer f = forceNext;
			forceNext = null;
			Quirk fq = f.kind() == Kind.QUIRK && f.quirk() == null ? randomQuirk(player.getRandom()) : f.quirk();
			return new Offer(f.kind(), f.kwami(), now + OFFER_DELAY_TICKS, now + OFFER_DELAY_TICKS + OFFER_TICKS, false, fq);
		}
		var random = player.getRandom();
		if (random.nextFloat() < RING_CHANCE) {
			return new Offer(Kind.GREEN_LANTERN, null, now + OFFER_DELAY_TICKS, now + OFFER_DELAY_TICKS + OFFER_TICKS, false);
		}
		if (random.nextFloat() < MIRACULOUS_CHANCE) {
			Kwami[] all = Kwami.values();
			Kwami k = all[random.nextInt(all.length)];
			return new Offer(Kind.MIRACULOUS, k, now + OFFER_DELAY_TICKS, now + OFFER_DELAY_TICKS + OFFER_TICKS, false);
		}
		// v0.16.7: the ring and the Miraculous both missed -- 5% a random Quirk
		if (random.nextDouble() < QuirkConfig.SPAWN_CHANCE) {
			return new Offer(Kind.QUIRK, null, now + OFFER_DELAY_TICKS, now + OFFER_DELAY_TICKS + OFFER_TICKS, false, randomQuirk(random));
		}
		return null;
	}

	/** v0.16.7: one of the seven Quirks, evenly. */
	public static Quirk randomQuirk(net.minecraft.util.RandomSource random) {
		Quirk[] all = Quirk.values();
		return all[random.nextInt(all.length)];
	}

	public static @Nullable Offer pending(ServerPlayer player) {
		return PENDING.get(player.getUUID());
	}

	/** Test / command hook: put an offer up now. */
	public static void offerNow(ServerPlayer player, Kind kind, @Nullable Kwami kwami) {
		long now = player.level().getGameTime();
		Offer o = new Offer(kind, kwami, now, now + OFFER_TICKS, false);
		PENDING.put(player.getUUID(), o);
		announce(player, o);
		PENDING.put(player.getUUID(), o.announced());
	}

	/** v0.16.7 test / command hook: a Quirk offer now. */
	public static void offerNow(ServerPlayer player, Quirk quirk) {
		long now = player.level().getGameTime();
		Offer o = new Offer(Kind.QUIRK, null, now, now + OFFER_TICKS, false, quirk);
		PENDING.put(player.getUUID(), o);
		announce(player, o);
		PENDING.put(player.getUUID(), o.announced());
	}

	// ---------------------------------------------------------------- the offer

	static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Offer> e : PENDING.entrySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				PENDING.remove(e.getKey());
				continue;
			}
			Offer o = e.getValue();
			long now = p.level().getGameTime();
			if (!o.shown() && now >= o.showAt()) {
				announce(p, o);
				PENDING.put(e.getKey(), o.announced());
			} else if (o.shown() && now >= o.expiresAt()) {
				PENDING.remove(e.getKey());
				p.displayClientMessage(Component.translatable(o.kind() == Kind.GREEN_LANTERN ? "message.projecthero.calling.ring_left"
						: o.kind() == Kind.QUIRK ? "message.projecthero.calling.quirk_left"
						: "message.projecthero.calling.miraculous_left").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
			} else if (o.shown() && now % 10 == 0) {
				glow(p, o, 6);
			}
		}
	}

	private static void announce(ServerPlayer player, Offer o) {
		ServerLevel level = player.serverLevel();
		Vec3 c = player.position().add(0, 1.0, 0);
		glow(player, o, 60);
		if (o.kind() == Kind.GREEN_LANTERN) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f);
			player.displayClientMessage(Component.translatable("message.projecthero.calling.ring_title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
			player.displayClientMessage(Component.translatable("message.projecthero.calling.ring_body").withStyle(ChatFormatting.GREEN), false);
		} else if (o.kind() == Kind.QUIRK) {
			// v0.16.7: a Quirk stirs
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.3f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.5f);
			player.displayClientMessage(Component.translatable("message.projecthero.calling.quirk_title", Quirks.quirkName(o.quirk()))
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
			player.displayClientMessage(Component.translatable("message.projecthero.calling.quirk_body", Quirks.quirkName(o.quirk()), Quirks.heroName(o.quirk()))
					.withStyle(ChatFormatting.GOLD), false);
		} else {
			Component kwami = Component.translatable("projecthero.calling.kwami." + o.kwami().id);
			Component hero = Component.translatable("projecthero.calling.hero." + o.kwami().heroKey);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 1.6f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.8f);
			player.displayClientMessage(Component.translatable("message.projecthero.calling.miraculous_title", hero)
					.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
			player.displayClientMessage(Component.translatable("message.projecthero.calling.miraculous_body", kwami, hero)
					.withStyle(ChatFormatting.LIGHT_PURPLE), false);
		}
		MutableComponent accept = Component.translatable("message.projecthero.calling.accept").withStyle(s -> s
				.withColor(ChatFormatting.GREEN).withBold(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/herocalling accept"))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.projecthero.calling.accept_hover"))));
		MutableComponent refuse = Component.translatable("message.projecthero.calling.refuse").withStyle(s -> s
				.withColor(ChatFormatting.RED).withBold(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/herocalling refuse"))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.projecthero.calling.refuse_hover"))));
		player.displayClientMessage(Component.literal("   ").append(accept).append("   ").append(refuse), false);
	}

	/** The light of whatever chose them: a green ring of light, or the Kwami's colour. Only the chosen player sees it. */
	private static void glow(ServerPlayer player, Offer o, int count) {
		ServerLevel level = player.serverLevel();
		ParticleSink out = (opt, x, y, z, n, dx, dy, dz, s) -> level.sendParticles(player, opt, true, x, y, z, n, dx, dy, dz, s);
		Vec3 c = player.position().add(0, 1.0, 0);
		if (o.kind() == Kind.GREEN_LANTERN) {
			for (int i = 0; i < count; i++) {
				double a = i * Math.PI * 2 / Math.max(1, count);
				out.send(GREEN, c.x + Math.cos(a) * 0.9, c.y + 0.6, c.z + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
			}
			out.send(ParticleTypes.GLOW, c.x, c.y + 1.4, c.z, Math.max(1, count / 6), 0.2, 0.2, 0.2, 0.01);
		} else {
			int rgb = o.kind() == Kind.QUIRK ? o.quirk().meterColour & 0xFFFFFF : o.kwami().color;
			DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), 1.2f);
			out.send(dust, c.x, c.y, c.z, count, 0.5, 0.7, 0.5, 0.01);
			out.send(ParticleTypes.END_ROD, c.x, c.y + 0.4, c.z, Math.max(1, count / 8), 0.3, 0.4, 0.3, 0.01);
		}
	}

	// ---------------------------------------------------------------- the answer

	/** Accept the open offer. True if a power was granted. */
	public static boolean accept(ServerPlayer player) {
		Offer o = PENDING.remove(player.getUUID());
		if (o == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.calling.none").withStyle(ChatFormatting.GRAY), false);
			return false;
		}
		boolean ok = o.kind() == Kind.GREEN_LANTERN ? com.projecthero.mod.greenlantern.GreenLantern.bond(player)
				: o.kind() == Kind.QUIRK ? Quirks.grant(player, o.quirk()) : Miraculous.grant(player, o.kwami());
		if (ok && o.kind() == Kind.GREEN_LANTERN) {
			player.displayClientMessage(Component.translatable("message.projecthero.calling.ring_accepted").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		} else if (!ok) {
			// refused by its own rules (UniquePowers, already held) -- they say why themselves; say it went
			player.displayClientMessage(Component.translatable("message.projecthero.calling.could_not").withStyle(ChatFormatting.GRAY), false);
		}
		return ok;
	}

	public static void refuse(ServerPlayer player) {
		Offer o = PENDING.remove(player.getUUID());
		if (o == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.calling.none").withStyle(ChatFormatting.GRAY), false);
			return;
		}
		player.displayClientMessage(Component.translatable(o.kind() == Kind.GREEN_LANTERN ? "message.projecthero.calling.ring_refused"
				: o.kind() == Kind.QUIRK ? "message.projecthero.calling.quirk_refused"
				: "message.projecthero.calling.miraculous_refused").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// any player (no permission level): it only answers that player's own offer
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("herocalling")
				.then(Commands.literal("accept").executes(ctx -> accept(ctx.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("refuse").executes(ctx -> {
					refuse(ctx.getSource().getPlayerOrException());
					return 1;
				}));
		dispatcher.register(root);
	}
}
