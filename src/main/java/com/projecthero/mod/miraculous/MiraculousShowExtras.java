package com.projecthero.mod.miraculous;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (user): two touches from the show for every Miraculous holder.
 * <ul>
 *   <li><b>Secret identity</b>: transformed, nobody sees who you are -- your display name (name tag, chat, death
 *       messages; {@code MiraculousIdentityMixin}) and your tab-list entry become your hero name in your Kwami's colour.
 *       The tab list is re-sent the moment you transform or change back.</li>
 *   <li><b>Kwami chatter</b>: your Kwami speaks up in chat now and then, in its own voice -- the countdown nearly out, a
 *       danger near while you are not transformed, badly hurt in a fight, a big win, and the odd idle remark. Never more
 *       than one line every {@link #MIN_GAP} ticks.</li>
 * </ul>
 */
public final class MiraculousShowExtras {
	/** Never two chatter lines within this many ticks of each other. */
	static final int MIN_GAP = 600;
	static final int DANGER_COOLDOWN = 3600;
	static final int HURT_COOLDOWN = 1800;
	static final int VICTORY_COOLDOWN = 1200;
	static final int IDLE_MIN = 12000;
	static final int IDLE_SPREAD = 6000;
	/** How many lines each event has in the lang file (keys {@code message.projecthero.kwami_chat.<kwami>.<event>.<i>}). */
	static final Map<String, Integer> LINES = Map.of("countdown", 2, "danger", 2, "hurt", 2, "victory", 2, "idle", 3);

	private static final Map<UUID, Boolean> WAS_TRANSFORMED = new ConcurrentHashMap<>();
	/** player -> [last line, last danger, last hurt, last victory, next idle, countdown end already warned] */
	private static final Map<UUID, long[]> CHAT = new ConcurrentHashMap<>();

	private MiraculousShowExtras() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(MiraculousShowExtras::serverTick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer p && Miraculous.transformed(p) && entity != p
					&& (entity.getMaxHealth() >= 100f || com.projecthero.mod.titanshifter.TitanCombat.isBoss(entity))) {
				long[] c = chat(p);
				long now = p.level().getGameTime();
				if (now - c[3] >= VICTORY_COOLDOWN) {
					c[3] = now;
					speak(p, "victory", now);
				}
			}
		});
	}

	public static void clearSessionState() {
		WAS_TRANSFORMED.clear();
		CHAT.clear();
	}

	// ---------------------------------------------------------------- secret identity

	/** Client-safe: the name shown for a transformed holder (null when not transformed). */
	public static MutableComponent heroName(Player player) {
		Kwami k = Miraculous.kwami(player);
		if (k == null || !Miraculous.transformed(player)) {
			return null;
		}
		return Component.translatable("projecthero.hero_tier." + k.heroKey)
				.withStyle(st -> st.withColor(TextColor.fromRgb(k.color & 0xFFFFFF)));
	}

	private static void refreshTabList(MinecraftServer server, ServerPlayer p) {
		server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
				EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(p)));
	}

	// ---------------------------------------------------------------- the tick

	private static void serverTick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			boolean has = Miraculous.hasPower(p);
			boolean now = has && Miraculous.transformed(p);
			Boolean was = WAS_TRANSFORMED.put(p.getUUID(), now);
			if (was != null && was != now) {
				refreshTabList(server, p);
			}
			if (has && p.tickCount % 20 == 0) {
				chatterTick(p);
			}
		}
	}

	private static long[] chat(ServerPlayer p) {
		return CHAT.computeIfAbsent(p.getUUID(), k -> {
			long t = p.level().getGameTime();
			return new long[] { t, -100000L, -100000L, -100000L, t + IDLE_MIN + p.getRandom().nextInt(IDLE_SPREAD), 0L };
		});
	}

	private static void chatterTick(ServerPlayer p) {
		if (!p.isAlive() || p.isSpectator()) {
			return;
		}
		long now = p.level().getGameTime();
		long[] c = chat(p);
		boolean transformed = Miraculous.transformed(p);
		// the countdown nearly out (the last jewel spot), once per countdown
		int left = Miraculous.countdownLeft(p);
		var s = Miraculous.peek(p);
		long end = s == null ? 0L : s.countdownEnd;
		if (transformed && left > 0 && left <= MiraculousConfig.COUNTDOWN_SPOT_TICKS && c[5] != end) {
			c[5] = end;
			speak(p, "countdown", now);
			return;
		}
		if (transformed && p.getHealth() < p.getMaxHealth() * 0.3f && now - c[2] >= HURT_COOLDOWN) {
			if (speak(p, "hurt", now)) {
				c[2] = now;
			}
			return;
		}
		if (!transformed && now - c[1] >= DANGER_COOLDOWN && dangerNear(p)) {
			if (speak(p, "danger", now)) {
				c[1] = now;
			}
			return;
		}
		if (!transformed && now >= c[4]) {
			c[4] = now + IDLE_MIN + p.getRandom().nextInt(IDLE_SPREAD);
			speak(p, "idle", now);
		}
	}

	/** A boss, or five or more monsters, within 24 blocks. */
	private static boolean dangerNear(ServerPlayer p) {
		List<LivingEntity> near = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24.0),
				e -> e != p && e.isAlive() && (e instanceof Enemy || com.projecthero.mod.titanshifter.TitanCombat.isBoss(e)));
		for (LivingEntity e : near) {
			if (com.projecthero.mod.titanshifter.TitanCombat.isBoss(e)) {
				return true;
			}
		}
		return near.size() >= 5;
	}

	/** One line from the Kwami (false: too soon after the last, or a hidden Kwami does not talk). */
	private static boolean speak(ServerPlayer p, String event, long now) {
		long[] c = chat(p);
		if (now - c[0] < MIN_GAP && !event.equals("countdown")) {
			return false;
		}
		var s = Miraculous.peek(p);
		if (s != null && s.kwamiHidden && !s.transformed) {
			return false;
		}
		Kwami k = Miraculous.kwami(p);
		if (k == null) {
			return false;
		}
		c[0] = now;
		int i = 1 + p.getRandom().nextInt(LINES.getOrDefault(event, 1));
		KwamiCompanion.say(p, "message.projecthero.kwami_chat." + k.id + "." + event + "." + i);
		return true;
	}

	/** Tests: the tick, driven by hand. */
	public static void tickForTest(ServerPlayer p) {
		chatterTick(p);
	}
}
