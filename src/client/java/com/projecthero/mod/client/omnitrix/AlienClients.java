package com.projecthero.mod.client.omnitrix;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.world.entity.player.Player;

/** v0.17.1: the alien client kits, by {@code Aliens} id. One line per kit in {@code OmnitrixClient.initialize}. */
public final class AlienClients {
	private static final Map<String, AlienClientKit> KITS = new HashMap<>();

	private AlienClients() {
	}

	public static void register(String alienId, AlienClientKit kit) {
		KITS.put(alienId, kit);
	}

	/** v0.17.1: every kit with a custom body rebuilds its renderer (from {@code OmnitrixPlayerRendererMixin}, per resource reload). */
	public static void buildBodies(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
		for (AlienClientKit k : KITS.values()) {
			if (k.customBody()) {
				k.buildBody(context);
			}
		}
	}

	@Nullable
	public static AlienClientKit of(String alienId) {
		return alienId == null ? null : KITS.get(alienId);
	}

	/**
	 * The alien {@code p} LOOKS like right now: the transformed alien, or during a Containment Pod extraction the forced
	 * transform it flickers into; null for a human (or an alien with no client kit).
	 */
	@Nullable
	public static String shown(Player p) {
		if (!Omnitrix.hasPower(p)) {
			return null;
		}
		OmnitrixState s = Omnitrix.state(p);
		if (s.transformed()) {
			return KITS.containsKey(s.active) ? s.active : null;
		}
		if (!s.flicker.isEmpty() && s.flickerUntil > p.level().getGameTime() && KITS.containsKey(s.flicker)) {
			return s.flicker;
		}
		return null;
	}

	@Nullable
	public static AlienClientKit shownKit(Player p) {
		return of(shown(p));
	}
}
