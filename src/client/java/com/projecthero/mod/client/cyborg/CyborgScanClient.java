package com.projecthero.mod.client.cyborg;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.cyborg.network.CyborgScanPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.5: the Scan's cyan creature outlines, for the scanning Cyborg only ({@link CyborgScanPayload} goes to him alone;
 * the glow / colour mixins only ask this client). Entity id -> game time the outline ends.
 */
public final class CyborgScanClient {
	private static final Map<Integer, Long> SCANNED = new ConcurrentHashMap<>();
	public static final int CYAN = 0x5CC8FF;

	private CyborgScanClient() {
	}

	static void receive(CyborgScanPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		long until = mc.level.getGameTime() + payload.ticks();
		for (int id : payload.ids()) {
			SCANNED.put(id, until);
		}
	}

	public static boolean scanned(Entity e) {
		Long until = SCANNED.get(e.getId());
		if (until == null) {
			return false;
		}
		if (e.level().getGameTime() > until || !e.isAlive()) {
			SCANNED.remove(e.getId());
			return false;
		}
		return true;
	}

	public static void reset() {
		SCANNED.clear();
	}
}
