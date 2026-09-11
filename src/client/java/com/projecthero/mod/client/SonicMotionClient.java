package com.projecthero.mod.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sonic Scream's "enhanced hearing", client-only: every tick, for anyone who owns the power, this scans
 * living entities within {@link #RADIUS} blocks and remembers which ones just moved. {@link EntityGlowMixin}
 * (via {@code isFlashing}) turns that into a brief per-viewer glow -- purely this client's own render,
 * nothing sent to the server, and the wielder never flashes themselves.
 */
public final class SonicMotionClient {
	private static final String KEY = "power_14_sonic_scream";
	private static final double RADIUS = 40.0;
	/** How long a "just moved" flash lingers, in ticks. */
	private static final int FLASH_TICKS = 6;
	/** Ignore sub-pixel jitter -- only a real step counts as "moved". */
	private static final double MOVE_THRESHOLD_SQ = 0.0009;

	private static final Map<Integer, Vec3> LAST_POS = new HashMap<>();
	private static final Map<Integer, Long> FLASH_UNTIL = new HashMap<>();

	private SonicMotionClient() {
	}

	public static void clientTick(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			LAST_POS.clear();
			FLASH_UNTIL.clear();
			return;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains(KEY)) {
			return;
		}
		long now = level.getGameTime();
		Set<Integer> seen = new HashSet<>();
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity) || e == player) {
				continue;
			}
			if (e.distanceToSqr(player) > RADIUS * RADIUS) {
				continue;
			}
			seen.add(e.getId());
			Vec3 cur = e.position();
			Vec3 prev = LAST_POS.put(e.getId(), cur);
			if (prev != null && prev.distanceToSqr(cur) > MOVE_THRESHOLD_SQ) {
				FLASH_UNTIL.put(e.getId(), now + FLASH_TICKS);
			}
		}
		LAST_POS.keySet().retainAll(seen);
		if (!FLASH_UNTIL.isEmpty()) {
			FLASH_UNTIL.values().removeIf(until -> until < now);
		}
	}

	/** True while entity {@code id} recently moved and should flash for the local viewer. */
	public static boolean isFlashing(int id, long now) {
		Long until = FLASH_UNTIL.get(id);
		return until != null && until >= now;
	}
}
