package com.projecthero.mod.event;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.mixin.BossEventAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

/**
 * A vanilla-style raid bar for a world event: the same top-of-screen {@link ServerBossEvent} the
 * game's own Pillager raid uses, driven by the event's tick.
 *
 * <p>Membership is recomputed every refresh from {@link ServerLevel#players()} -- everyone within a
 * radius of the event centre is shown the bar, everyone else has it removed -- so it needs no packets
 * of its own and no per-player bookkeeping that could leak. It is transient: an event holds one of
 * these as a plain field, never persists it, and just calls {@link #update} again after a reload.
 *
 * <p>The bar's id is pinned to the event's own (persistent) id rather than the random one
 * {@link ServerBossEvent} would generate. Without that, a bar torn down and rebuilt -- on pause/resume
 * or a world reload -- gets a fresh id each time, and any client that still shows the old one (because
 * it was out of range when the bar was cleared and so never received a REMOVE) keeps a frozen ghost
 * copy. A stable id means the client updates the same bar instead of stacking a second.
 */
public final class EventBossBar {
	private final UUID stableId;
	private final BossEvent.BossBarColor color;
	private final BossEvent.BossBarOverlay overlay;
	private final boolean darkenScreen;

	private ServerBossEvent bar;

	public EventBossBar(UUID stableId, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay,
			boolean darkenScreen) {
		this.stableId = stableId;
		this.color = color;
		this.overlay = overlay;
		this.darkenScreen = darkenScreen;
	}

	/**
	 * Show (creating it on first call) and refresh the bar: its label, its fill, and its audience --
	 * every player currently within {@code radius} of {@code center} in {@code level}.
	 */
	public void update(ServerLevel level, BlockPos center, double radius, Component name, float progress) {
		if (bar == null) {
			bar = new ServerBossEvent(name, color, overlay);
			((BossEventAccessor) bar).projecthero$setId(stableId);
			bar.setDarkenScreen(darkenScreen);
			bar.setCreateWorldFog(false);
		}
		bar.setVisible(true);
		bar.setName(name);
		bar.setProgress(Mth.clamp(progress, 0.0f, 1.0f));

		double radiusSq = radius * radius;
		List<ServerPlayer> online = level.players();
		for (ServerPlayer player : online) {
			boolean near = player.isAlive()
					&& player.distanceToSqr(center.getX() + 0.5, player.getY(), center.getZ() + 0.5) <= radiusSq;
			if (near) {
				bar.addPlayer(player);
			} else {
				bar.removePlayer(player);
			}
		}
		// Drop anyone who has since disconnected or changed dimension -- they are not in this level's
		// player list so the loop above never touched them.
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (!online.contains(player)) {
				bar.removePlayer(player);
			}
		}
	}

	/** Whether the bar exists and is being shown. */
	public boolean isShowing() {
		return bar != null && bar.isVisible();
	}

	/** Hide the bar and drop it off every client. Safe to call when it was never created. */
	public void clear() {
		if (bar != null) {
			bar.removeAllPlayers();
			bar.setVisible(false);
			bar = null;
		}
	}

	/**
	 * Like {@link #clear()}, but also sends an explicit REMOVE for this bar to <em>every</em> player in
	 * {@code level} -- not just the ones still in its member set. A player who was out of range when the
	 * bar was last refreshed was already dropped from the set, so {@code removeAllPlayers()} alone would
	 * leave them with a frozen ghost bar.
	 */
	public void clear(ServerLevel level) {
		clear();
		ClientboundBossEventPacket packet = ClientboundBossEventPacket.createRemovePacket(stableId);
		for (ServerPlayer player : level.players()) {
			player.connection.send(packet);
		}
	}
}
