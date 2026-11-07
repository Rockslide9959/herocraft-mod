package com.projecthero.mod.client.blackpanther;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.blackpanther.network.PantherSensesPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3: Panther Senses' purple outlines, for that Black Panther only (the payload goes to them alone; the glow / colour
 * mixins only ask this client). Entity id -> game time the outline ends.
 */
public final class PantherSensesClient {
	private static final Map<Integer, Long> SENSED = new ConcurrentHashMap<>();
	public static final int PURPLE = 0xB070FF;

	private PantherSensesClient() {
	}

	static void receive(PantherSensesPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		long until = mc.level.getGameTime() + payload.ticks();
		for (int id : payload.ids()) {
			SENSED.put(id, until);
		}
	}

	public static boolean sensed(Entity e) {
		Long until = SENSED.get(e.getId());
		if (until == null) {
			return false;
		}
		if (e.level().getGameTime() > until || !e.isAlive()) {
			SENSED.remove(e.getId());
			return false;
		}
		return true;
	}

	public static void reset() {
		SENSED.clear();
	}
}
