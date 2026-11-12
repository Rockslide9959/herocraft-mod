package com.projecthero.mod.client.doctorfate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.doctorfate.network.FateFxPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3 Doctor Fate: the client-only effects. Sight of Nabu: for 10 s every creature within 64 blocks glows through walls (Chaos
 * red, Order gold, neutral white; {@code FateSightGlowMixin} / {@code FateSightColorMixin}) with its health over its head
 * ({@link FateWorldRender}). Only the casting player's client gets the payload.
 */
public final class FateFx {
	public static final int CHAOS = 0xFF3A3A;
	public static final int ORDER = 0xFFC93A;
	public static final int NEUTRAL = 0xF0F0F0;

	/** entity id -> {kind, until (game time)} */
	private static final Map<Integer, long[]> SIGHT = new ConcurrentHashMap<>();
	static long struggledAt = -100L;

	private FateFx() {
	}

	static void reset() {
		SIGHT.clear();
	}

	static void receive(FateFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || p.kind() != FateFxPayload.SIGHT) {
			return;
		}
		long until = mc.level.getGameTime() + p.ticks();
		SIGHT.clear();
		int[] d = p.data();
		for (int i = 0; i + 1 < d.length; i += 2) {
			SIGHT.put(d[i], new long[] { d[i + 1], until });
		}
	}

	static void tick(Minecraft mc) {
		if (!SIGHT.isEmpty() && mc.level != null) {
			long now = mc.level.getGameTime();
			SIGHT.values().removeIf(v -> v[1] <= now);
		}
	}

	static void struggled(long now) {
		struggledAt = now;
	}

	/** Sight of Nabu shows this entity right now. */
	public static boolean sighted(Entity e) {
		long[] v = SIGHT.get(e.getId());
		return v != null && e.level() != null && v[1] > e.level().getGameTime();
	}

	public static int colour(Entity e) {
		long[] v = SIGHT.get(e.getId());
		if (v == null) {
			return NEUTRAL;
		}
		return v[0] == FateFxPayload.SIGHT_CHAOS ? CHAOS : v[0] == FateFxPayload.SIGHT_ORDER ? ORDER : NEUTRAL;
	}

	static Map<Integer, long[]> sightMap() {
		return SIGHT;
	}
}
