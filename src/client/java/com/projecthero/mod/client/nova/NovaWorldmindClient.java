package com.projecthero.mod.client.nova;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.nova.NovaConfig;
import com.projecthero.mod.nova.network.NovaScanPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.13: the Worldmind, as the Nova himself sees it -- and nobody else. While suited every mob within
 * {@link NovaConfig#WORLDMIND_RANGE} blocks (a full sphere, through rock) is outlined gold; a Worldmind Scan outlines
 * everything it found cyan for 10 s and the strongest, marked creature red. Decided only from the local player's own
 * synced state and the scan result sent to him alone; nothing is ever set on the entities (the v0.15.4 privacy rule --
 * the decision is only asked for client-side entities, see {@code NovaWorldmindGlowMixin}).
 */
public final class NovaWorldmindClient {
	public static final int GOLD = 0xFFC83C;
	public static final int CYAN = 0x8BF8FF;
	public static final int RED = 0xFF3344;

	private static final Set<Integer> SCANNED = new HashSet<>();
	private static int marked = -1;
	private static long until;

	private NovaWorldmindClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(NovaScanPayload.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			SCANNED.clear();
			SCANNED.addAll(payload.ids());
			marked = payload.marked();
			until = mc.level == null ? 0L : mc.level.getGameTime() + payload.ticks();
		});
	}

	private static boolean scanLive(Player viewer) {
		return viewer.level() != null && viewer.level().getGameTime() < until;
	}

	/** Whether {@code viewer}'s Worldmind outlines {@code e} right now. */
	public static boolean outlines(Player viewer, Entity e) {
		if (e == viewer || !NovaSuitRender.hidesSkinOverlay(viewer) || !com.projecthero.mod.nova.Nova.suited(viewer)) {
			return false;
		}
		if (scanLive(viewer) && (SCANNED.contains(e.getId()) || e.getId() == marked)) {
			return true;
		}
		return e instanceof Mob && e.isAlive() && e.distanceToSqr(viewer) <= NovaConfig.WORLDMIND_RANGE * NovaConfig.WORLDMIND_RANGE;
	}

	/** The outline colour for {@code e} (red for the marked one, cyan for scanned, gold otherwise). */
	public static int color(Player viewer, Entity e) {
		if (scanLive(viewer)) {
			if (e.getId() == marked) {
				return RED;
			}
			if (SCANNED.contains(e.getId())) {
				return CYAN;
			}
		}
		return GOLD;
	}

	/** World / session change. */
	public static void reset() {
		SCANNED.clear();
		marked = -1;
		until = 0L;
	}
}
