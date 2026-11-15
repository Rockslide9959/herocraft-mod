package com.projecthero.mod.loot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

/**
 * v0.17.5: the Med Kit's heal -- {@link #TOTAL_HEAL} health spread evenly over {@link #DURATION_TICKS}, driven by our
 * own server tick (no vanilla Regeneration effect, so no effect icon). Using a second kit while one is running restarts
 * the timer (it does not stack). Poison and Wither are cleared when the kit is used.
 */
public final class MedKitHealing {
	/** 8 hearts. */
	public static final float TOTAL_HEAL = 16f;
	/** 4 seconds. */
	public static final int DURATION_TICKS = 80;
	public static final float PER_TICK = TOTAL_HEAL / DURATION_TICKS;

	/** Player UUID -> ticks of healing left. Holds no world or entity references; cleared on server stop anyway. */
	private static final Map<UUID, Integer> HEALING = new HashMap<>();

	private MedKitHealing() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(MedKitHealing::serverTick);
	}

	/** Starts (or restarts) the heal and clears Poison / Wither. */
	public static void start(ServerPlayer player) {
		player.removeEffect(MobEffects.POISON);
		player.removeEffect(MobEffects.WITHER);
		HEALING.put(player.getUUID(), DURATION_TICKS);
	}

	public static boolean isHealing(ServerPlayer player) {
		return HEALING.containsKey(player.getUUID());
	}

	/** Ticks left on this player's heal, 0 when none. */
	public static int ticksLeft(ServerPlayer player) {
		return HEALING.getOrDefault(player.getUUID(), 0);
	}

	private static void serverTick(MinecraftServer server) {
		if (HEALING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Integer>> it = HEALING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Integer> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || !p.isAlive()) {
				it.remove();
				continue;
			}
			int left = step(p, e.getValue());
			if (left <= 0) {
				it.remove();
			} else {
				e.setValue(left);
			}
		}
	}

	/** One tick of healing for this player (tests drive it directly; mock players aren't in the server's tick loop). */
	public static void tick(ServerPlayer player) {
		Integer left = HEALING.get(player.getUUID());
		if (left == null) {
			return;
		}
		int next = player.isAlive() ? step(player, left) : 0;
		if (next <= 0) {
			HEALING.remove(player.getUUID());
		} else {
			HEALING.put(player.getUUID(), next);
		}
	}

	private static int step(ServerPlayer p, int left) {
		p.heal(PER_TICK);
		if (left % 10 == 0 && p.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.9, p.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
		}
		return left - 1;
	}

	public static void clearSessionState() {
		HEALING.clear();
	}
}
