package com.projecthero.mod.client.greenlantern;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Client-side store of the entity ids Ring Scan (v0.11.10) currently flags for the local player, split
 * by hostile/passive so {@code EntityGlowMixin} can outline each set a different colour. Fed by
 * {@link com.projecthero.mod.network.GreenLanternRingScanPayload}, one viewer at a time -- mirrors
 * {@code SpiderSenseGlowClient}'s pattern exactly, including its self-expiring set (so a scanned
 * creature stops glowing on its own once {@link #EXPIRY_TICKS} passes with no fresh scan, matching the
 * server side's own {@code SCAN_DURATION_TICKS} window without the two ever needing to agree on a
 * shared timer).
 */
public final class GreenLanternRingScanClient {
	private static final long EXPIRY_TICKS = 120L; // matches GreenLanternConfig#SCAN_DURATION_TICKS

	private static IntSet hostileIds = new IntOpenHashSet();
	private static IntSet passiveIds = new IntOpenHashSet();
	private static long updatedAtTick;

	private GreenLanternRingScanClient() {
	}

	public static void accept(int[] hostile, int[] passive, long nowTick) {
		IntOpenHashSet nextHostile = new IntOpenHashSet(hostile.length);
		for (int id : hostile) {
			nextHostile.add(id);
		}
		IntOpenHashSet nextPassive = new IntOpenHashSet(passive.length);
		for (int id : passive) {
			nextPassive.add(id);
		}
		hostileIds = nextHostile;
		passiveIds = nextPassive;
		updatedAtTick = nowTick;
	}

	private static boolean fresh(long nowTick) {
		long age = nowTick - updatedAtTick;
		return age >= 0L && age <= EXPIRY_TICKS;
	}

	public static boolean isHostile(int entityId, long nowTick) {
		return fresh(nowTick) && hostileIds.contains(entityId);
	}

	public static boolean isPassive(int entityId, long nowTick) {
		return fresh(nowTick) && passiveIds.contains(entityId);
	}

	public static void clear() {
		hostileIds = new IntOpenHashSet();
		passiveIds = new IntOpenHashSet();
		updatedAtTick = 0L;
	}
}
