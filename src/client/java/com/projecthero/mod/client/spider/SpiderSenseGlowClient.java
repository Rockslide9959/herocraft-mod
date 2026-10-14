package com.projecthero.mod.client.spider;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Client-side store of the entity ids the local player's Spider-Sense currently flags as a threat
 * (v0.9.3). Fed by {@link com.projecthero.mod.network.SpiderSenseGlowPayload}; read by
 * {@code EntityGlowMixin} to outline exactly those entities red, for this viewer only.
 *
 * <p>The set is replaced wholesale on every packet and expires on its own a second after the last
 * update, so a mob stops glowing the moment the server stops sending it (or the player leaves the
 * server / the sense scan stops).
 */
public final class SpiderSenseGlowClient {
	private static final long EXPIRY_TICKS = 25L;

	private static IntSet ids = new IntOpenHashSet();
	private static long updatedAtTick;

	private SpiderSenseGlowClient() {
	}

	public static void accept(int[] incoming, long nowTick) {
		IntOpenHashSet next = new IntOpenHashSet(incoming.length);
		for (int id : incoming) {
			next.add(id);
		}
		ids = next;
		updatedAtTick = nowTick;
	}

	/** Whether {@code entityId} is a current Spider-Sense threat for the local player. */
	public static boolean isThreat(int entityId, long nowTick) {
		long age = nowTick - updatedAtTick;
		if (ids.isEmpty() || age < 0L || age > EXPIRY_TICKS) {
			return false;
		}
		return ids.contains(entityId);
	}

	/**
	 * v0.16.1 Spider-Sense Pulse (V): while the viewer's pulse runs, every hostile ({@code Enemy}) within the pulse radius
	 * glows for him alone -- decided here, client side, never a server glowing flag.
	 */
	public static boolean pulseHighlights(net.minecraft.world.entity.Entity self, net.minecraft.world.entity.player.Player viewer, long nowTick) {
		com.projecthero.mod.spider.data.SpiderMoveState m = viewer.getAttachedOrElse(
				com.projecthero.mod.attachment.ModAttachments.SPIDER_MOVE_STATE, null);
		if (m == null || nowTick >= m.pulseUntil || !(self instanceof net.minecraft.world.entity.monster.Enemy)) {
			return false;
		}
		double r = com.projecthero.mod.spider.SpiderManConfig.moves().pulseRadius;
		return self.distanceToSqr(viewer) <= r * r;
	}

	/** v0.16.2 Web Pin: the target the viewer has marked with Shift+V is outlined for him alone. */
	public static boolean pinMarked(net.minecraft.world.entity.Entity self, net.minecraft.world.entity.player.Player viewer, long nowTick) {
		com.projecthero.mod.spider.data.SpiderMoveState m = viewer.getAttachedOrElse(
				com.projecthero.mod.attachment.ModAttachments.SPIDER_MOVE_STATE, null);
		return m != null && nowTick < m.pinUntil && m.pinTarget == self.getId();
	}

	/** The Web Pin mark's outline colour (a pale web white). */
	public static final int PIN_COLOR = 0xEAF2FF;

	public static void clear() {
		ids = new IntOpenHashSet();
		updatedAtTick = 0L;
	}
}
