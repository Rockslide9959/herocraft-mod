package com.projecthero.mod.client.ghostrider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ghostrider.network.GhostRiderMarkPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.5: the Judgement Mark's red outlines, for the Ghost Rider who cast it only ({@link GhostRiderMarkPayload} goes to
 * him alone; the glow and colour mixins only ask this client). Entity id -> game time the outline ends.
 */
public final class GhostRiderMarkClient {
	private static final Map<Integer, Long> MARKED = new ConcurrentHashMap<>();
	public static final int RED = 0xFF2A10;

	private GhostRiderMarkClient() {
	}

	static void receive(GhostRiderMarkPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		long until = mc.level.getGameTime() + payload.ticks();
		for (int id : payload.ids()) {
			MARKED.put(id, until);
		}
	}

	public static boolean marked(Entity e) {
		Long until = MARKED.get(e.getId());
		if (until == null) {
			return false;
		}
		if (e.level().getGameTime() > until || !e.isAlive()) {
			MARKED.remove(e.getId());
			return false;
		}
		return true;
	}

	public static void reset() {
		MARKED.clear();
	}
}
