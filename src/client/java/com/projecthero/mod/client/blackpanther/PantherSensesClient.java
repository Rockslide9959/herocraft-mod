package com.projecthero.mod.client.blackpanther;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.blackpanther.network.PantherSensesPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3: Panther Senses' purple outlines, for that Black Panther only (the payload goes to them alone; the glow / colour
 * mixins only ask this client). v0.17.4: two more kinds -- Hunter's Instinct (red: creatures hunting him) and Kimoyo Scan (gold:
 * the marked creature). Per kind, entity id -> the game time the outline ends.
 */
public final class PantherSensesClient {
	public static final int PURPLE = 0xB070FF;
	public static final int RED = 0xFF5040;
	public static final int GOLD = 0xFFC040;

	@SuppressWarnings("unchecked")
	private static final Map<Integer, Long>[] SENSED = new Map[] { new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>() };

	private PantherSensesClient() {
	}

	static void receive(PantherSensesPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Map<Integer, Long> map = SENSED[Math.floorMod(payload.kind(), SENSED.length)];
		long until = mc.level.getGameTime() + payload.ticks();
		for (int id : payload.ids()) {
			map.put(id, until);
		}
	}

	private static boolean live(Map<Integer, Long> map, Entity e) {
		Long until = map.get(e.getId());
		if (until == null) {
			return false;
		}
		if (e.level().getGameTime() > until || !e.isAlive()) {
			map.remove(e.getId());
			return false;
		}
		return true;
	}

	public static boolean sensed(Entity e) {
		for (Map<Integer, Long> map : SENSED) {
			if (live(map, e)) {
				return true;
			}
		}
		return false;
	}

	/** The outline's colour: a scan mark wins, then a hunter, then the senses. */
	public static int colour(Entity e) {
		if (live(SENSED[PantherSensesPayload.MARK], e)) {
			return GOLD;
		}
		if (live(SENSED[PantherSensesPayload.INSTINCT], e)) {
			return RED;
		}
		return PURPLE;
	}

	/** How many live outlines of a kind there are (the HUD's Hunted tag). */
	public static int count(int kind) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return 0;
		}
		long now = mc.level.getGameTime();
		Map<Integer, Long> map = SENSED[Math.floorMod(kind, SENSED.length)];
		map.values().removeIf(until -> until < now);
		return map.size();
	}

	public static void reset() {
		for (Map<Integer, Long> map : SENSED) {
			map.clear();
		}
	}
}
