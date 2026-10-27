package com.projecthero.mod.greenlantern;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.GreenLanternCuePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;

/**
 * v0.17.1 (user-approved revamp): the Power Ring talks -- the Green Lantern counterpart of {@code JarvisDialogue}, in the
 * same delivery style: short lines from the server, drawn by the client as a small green subtitle with a soft chime
 * ({@code GreenLanternRingVoiceClient}), mutable from the Shift+N suits screen (client side, default on).
 *
 * <p>Edges, not levels: every {@link #EVAL_INTERVAL} ticks {@link #tick} compares what it sees with what it saw last time
 * (Ring Charge falling to 25% / 10%, rising into the Willpower Surge, several hostiles turning up, a shield or dome
 * failing). Events speak through {@link #say}: putting the ring on, the Oath, a new rank, a refused low-charge move,
 * a hard-light wall about to break, a landed combo. Every line has its own cooldown and there is a per-Lantern gap between
 * two lines ({@link GreenLanternConfig#RING_VOICE_GAP_TICKS}); urgent lines skip the gap, never their own cooldown. The
 * first look at a Lantern only records a baseline. Static per-player state, cleared on server stop
 * ({@link #clearSessionState}) and with the Lantern's transient state ({@link #onCleanup}).
 */
public final class GreenLanternRingVoice {
	public static final int EVAL_INTERVAL = 10;

	/** Every ring line: id (lang {@code message.projecthero.green_lantern.ring_voice.<id>}), cooldown, urgent. Appended only. */
	public enum Line {
		GREETING("greeting", 10 * 20, true),
		OATH("oath", 30 * 20, true),
		CHARGE_LOW("charge_low", 60 * 20, false),
		CHARGE_CRITICAL("charge_critical", 60 * 20, true),
		RECHARGE("recharge", 45 * 20, false),
		HOSTILES("hostiles", 90 * 20, false),
		SURGE("surge", 60 * 20, false),
		INTEGRITY("integrity", 20 * 20, true),
		RANK("rank", 0, true),
		COMBO("combo", 30 * 20, false),
		/** v0.17.1 Ring Auto-Protection: one line per automatic save (GreenLanternAutoProtect). */
		SHIELD_ENGAGED("shield_engaged", 20 * 20, true),
		CUSHION("cushion", 60 * 20, false),
		AIR_BUBBLE("air_bubble", 60 * 20, false),
		HEAT_SHELL("heat_shell", 60 * 20, false),
		BLIND_SIDE("blind_side", 45 * 20, false),
		/** v0.17.1: a Hard-Light Gateway opened ("Gateway open: %s."), the waypoint's name as a literal argument. */
		GATEWAY("gateway", 0, true);

		public final String id;
		public final int cooldownTicks;
		public final boolean urgent;

		Line(String id, int cooldownTicks, boolean urgent) {
			this.id = id;
			this.cooldownTicks = cooldownTicks;
			this.urgent = urgent;
		}
	}

	/** What the previous evaluation saw, plus the cooldown book-keeping. */
	private static final class Seen {
		boolean baseline;
		float charge = 1f;
		boolean surge;
		boolean hostiles;
		boolean barrierFailing;
		final Map<Line, Long> readyAt = new EnumMap<>(Line.class);
		long lastSpokenAt = Long.MIN_VALUE / 2;
		Line last;
		String lastArg = "";
		int spoken;
	}

	private static final Map<UUID, Seen> SEEN = new ConcurrentHashMap<>();

	private GreenLanternRingVoice() {
	}

	public static void clearSessionState() {
		SEEN.clear();
	}

	public static void onCleanup(UUID player) {
		Seen s = SEEN.get(player);
		if (s != null) {
			s.baseline = false; // look again from scratch (keeps the cooldowns, so a relog can't re-trigger everything)
		}
	}

	private static Seen seen(ServerPlayer player) {
		return SEEN.computeIfAbsent(player.getUUID(), k -> new Seen());
	}

	/** Per-Lantern tick, from {@code GreenLanternAbilityManager#serverTick} (only while the ring is on). */
	public static void tick(ServerPlayer player) {
		if (player.tickCount % EVAL_INTERVAL != 0) {
			return;
		}
		evaluate(player, player.tickCount % (EVAL_INTERVAL * 2) == 0);
	}

	/** One look at the Lantern (public for tests): compares with the last look and speaks on the edges. */
	public static void evaluate(ServerPlayer player, boolean scanHostiles) {
		Seen s = seen(player);
		long now = player.level().getGameTime();
		float charge = GreenLanternEnergy.get(player) / Math.max(1f, GreenLanternRanks.maxCharge(player));
		boolean surge = charge > GreenLanternConfig.SURGE_HIGH;
		boolean hostiles = scanHostiles ? hostilesNear(player) : s.hostiles;
		boolean failing = barrierFailing(player);
		if (!s.baseline) {
			s.baseline = true;
			s.charge = charge;
			s.surge = surge;
			s.hostiles = hostiles;
			s.barrierFailing = failing;
			return;
		}
		if (charge <= GreenLanternConfig.RING_VOICE_CRITICAL && s.charge > GreenLanternConfig.RING_VOICE_CRITICAL) {
			attempt(player, s, Line.CHARGE_CRITICAL, "", now);
		} else if (charge <= GreenLanternConfig.RING_VOICE_LOW && s.charge > GreenLanternConfig.RING_VOICE_LOW) {
			attempt(player, s, Line.CHARGE_LOW, "", now);
		}
		if (surge && !s.surge) {
			attempt(player, s, Line.SURGE, "", now);
		}
		if (hostiles && !s.hostiles) {
			attempt(player, s, Line.HOSTILES, "", now);
		}
		if (failing && !s.barrierFailing) {
			attempt(player, s, Line.INTEGRITY, "", now);
		}
		s.charge = charge;
		s.surge = surge;
		s.hostiles = hostiles;
		s.barrierFailing = failing;
	}

	private static boolean hostilesNear(ServerPlayer player) {
		return AbilityHelpers.living(player.serverLevel(), player.position(), GreenLanternConfig.RING_VOICE_HOSTILE_RADIUS,
				e -> e instanceof Enemy && e.isAlive() && !e.isSpectator()).size() >= GreenLanternConfig.RING_VOICE_HOSTILE_COUNT;
	}

	/** The Z bubble or the dome is up and under the flicker threshold of its HP. */
	private static boolean barrierFailing(ServerPlayer player) {
		if (!GreenLanternShield.isActive(player)) {
			return false;
		}
		float frac = GreenLanternShield.isDome(player) ? GreenLanternShield.hp(player) / GreenLanternConfig.DOME_HP
				: GreenLanternShield.bubbleHp(player) / GreenLanternConfig.SHIELD_HP;
		return frac < GreenLanternConfig.DURABILITY_FLICKER_BELOW;
	}

	// ---------------------------------------------------------------- events

	/** An event line (greeting, Oath, recharge, integrity...), subject to its cooldown and the gap. */
	public static boolean say(ServerPlayer player, Line line) {
		return say(player, line, "");
	}

	/** As {@link #say(ServerPlayer, Line)}, with a translation key filled into the line's {@code %s}. */
	public static boolean say(ServerPlayer player, Line line, String argKey) {
		if (player == null || !GreenLantern.hasPower(player)) {
			return false;
		}
		return attempt(player, seen(player), line, argKey == null ? "" : argKey, player.level().getGameTime());
	}

	/** A landed combo: now and then the ring calls it by name. */
	public static void combo(ServerPlayer player, GreenLanternCombos.Combo combo) {
		say(player, Line.COMBO, combo.nameKey());
	}

	private static boolean attempt(ServerPlayer player, Seen s, Line line, String arg, long now) {
		Long ready = s.readyAt.get(line);
		if (ready != null && now < ready) {
			return false;
		}
		if (!line.urgent && now - s.lastSpokenAt < GreenLanternConfig.RING_VOICE_GAP_TICKS) {
			return false;
		}
		s.readyAt.put(line, now + line.cooldownTicks);
		s.lastSpokenAt = now;
		s.last = line;
		s.lastArg = arg;
		s.spoken++;
		send(player, line, arg);
		return true;
	}

	private static void send(ServerPlayer player, Line line, String arg) {
		if (ServerPlayNetworking.canSend(player, GreenLanternCuePayload.TYPE)) {
			ServerPlayNetworking.send(player, new GreenLanternCuePayload(GreenLanternCuePayload.VOICE, line.id, arg));
		} else {
			Component text = arg.isEmpty() ? Component.translatable("message.projecthero.green_lantern.ring_voice." + line.id)
					: Component.translatable("message.projecthero.green_lantern.ring_voice." + line.id, Component.translatable(arg));
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.ring_voice.prefix").append(" ")
					.append(text).withStyle(ChatFormatting.GREEN), true);
		}
	}

	// ---------------------------------------------------------------- tests

	public static Line lastLine(ServerPlayer player) {
		Seen s = SEEN.get(player.getUUID());
		return s == null ? null : s.last;
	}

	public static String lastArg(ServerPlayer player) {
		Seen s = SEEN.get(player.getUUID());
		return s == null ? null : s.lastArg;
	}

	public static int spokenCount(ServerPlayer player) {
		Seen s = SEEN.get(player.getUUID());
		return s == null ? 0 : s.spoken;
	}

	/** Tests: forget the cooldowns / gap so a line can be heard again at once. */
	public static void resetCooldowns(ServerPlayer player) {
		Seen s = SEEN.get(player.getUUID());
		if (s != null) {
			s.readyAt.clear();
			s.lastSpokenAt = Long.MIN_VALUE / 2;
		}
	}
}
