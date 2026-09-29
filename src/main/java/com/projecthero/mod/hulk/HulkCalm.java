package com.projecthero.mod.hulk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * v0.13.14: calming down -- hold N for 2 seconds, out of combat for 3, and a breathing exercise opens
 * ({@code HulkCalmScreen}): a guide ring swells and shrinks; hold Space to breathe in while it swells and let go while it
 * shrinks. Every second the client reports how many ticks it was in rhythm and how many out; in-rhythm time drains rage
 * fast ({@code calm.hitDrain} a second), out-of-rhythm time feeds it ({@code calm.missRage}). The server never trusts
 * more ticks than have passed. Getting hurt breaks the focus and ends it. A Hulk calmed to 0 changes back WITHOUT the
 * exhaustion (he let go of the anger, he did not burn it out).
 */
public final class HulkCalm {
	private static final int MAX_TICKS = 20 * 60;

	private record Session(long startedAt, long lastReportAt) {
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private HulkCalm() {
	}

	public static void clearSessionState() {
		SESSIONS.clear();
	}

	/** N held for 2 s. */
	public static void start(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (!s.hasPower || s.combat.calming || s.rampaging(now) || Hulk.changing(s, now) || !player.isAlive()) {
			return;
		}
		if (s.rage <= 0.0f) {
			Hulk.say(player, "message.projecthero.hulk.calm_already", ChatFormatting.GRAY);
			return;
		}
		if (now - s.lastCombatAt < HulkConfig.calm().outOfCombatTicks) {
			Hulk.say(player, "message.projecthero.hulk.calm_in_combat", ChatFormatting.RED);
			return;
		}
		HulkState n = s.copy();
		n.combat.calming = true;
		n.combat.smashChargeStart = 0L;
		n.leapChargeStart = 0L;
		Hulk.save(player, n);
		SESSIONS.put(player.getUUID(), new Session(now, now));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	/** The client's once-a-second tally. */
	public static void report(ServerPlayer player, int inRhythm, int outOfRhythm) {
		Session session = SESSIONS.get(player.getUUID());
		HulkState s = Hulk.state(player);
		if (session == null || !s.combat.calming) {
			return;
		}
		long now = player.level().getGameTime();
		long elapsed = now - session.lastReportAt();
		if (elapsed < 10) {
			return; // flooding
		}
		// never credit more ticks than actually went by (plus a little slack for packet timing)
		long budget = Math.min(40, elapsed + 4);
		int in = (int) Math.max(0, Math.min(inRhythm, budget));
		int out = (int) Math.max(0, Math.min(outOfRhythm, budget - in));
		SESSIONS.put(player.getUUID(), new Session(session.startedAt(), now));
		HulkConfig.Calm cfg = HulkConfig.calm();
		float delta = -cfg.hitDrain * (in / 20.0f) + cfg.missRage * (out / 20.0f);
		HulkState n = s.copy();
		n.rage = Math.max(0.0f, Math.min(HulkConfig.RAGE_MAX, s.rage + delta));
		Hulk.save(player, n);
		if (in > out && player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + player.getBbHeight() * 0.7, player.getZ(),
					2, 0.3, 0.3, 0.3, 0.0);
		}
		if (n.rage <= 0.0f) {
			finish(player, true);
		}
	}

	/** Esc in the screen, or it timed out. */
	public static void stop(ServerPlayer player) {
		finish(player, false);
	}

	/** Getting hurt breaks the focus. */
	static void interrupt(ServerPlayer player) {
		if (SESSIONS.containsKey(player.getUUID()) || Hulk.state(player).combat.calming) {
			Hulk.say(player, "message.projecthero.hulk.calm_broken", ChatFormatting.RED);
			finish(player, false);
		}
	}

	private static void finish(ServerPlayer player, boolean calmed) {
		SESSIONS.remove(player.getUUID());
		HulkState s = Hulk.state(player);
		if (s.combat.calming) {
			HulkState n = s.copy();
			n.combat.calming = false;
			Hulk.save(player, n);
		}
		if (!calmed) {
			return;
		}
		if (Hulk.isHulk(player)) {
			Hulk.revert(player, false);
		}
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.hulk.calmed")
				.withStyle(ChatFormatting.GREEN), true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	/** Every tick for a Gamma player: time out a forgotten session. */
	static void tick(ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null) {
			if (Hulk.state(player).combat.calming) {
				finish(player, false); // a stale flag from before a relog
			}
			return;
		}
		long now = player.level().getGameTime();
		if (now - session.startedAt() > MAX_TICKS || now - session.lastReportAt() > 60 || !player.isAlive()) {
			finish(player, false);
		}
	}

	public static void clear(UUID id) {
		SESSIONS.remove(id);
	}
}
