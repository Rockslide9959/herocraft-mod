package com.projecthero.mod.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.3: the one place the server writes {@link ModAttachments#GREEN_LANTERN_FX} -- which move animation just
 * started, which moves are being channelled, and when a ring removal began. Only writes when something changed, so a
 * per-tick "still channelling" call costs nothing on the wire.
 */
public final class GreenLanternVisuals {
	private GreenLanternVisuals() {
	}

	public static GreenLanternFx fx(Player player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);
	}

	/** Starts one of the {@code GreenLanternFx.ANIM_*} move animations now. */
	public static void anim(ServerPlayer player, int id) {
		player.setAttached(ModAttachments.GREEN_LANTERN_FX, fx(player).withAnim(id, player.level().getGameTime()));
	}

	public static void channel(ServerPlayer player, int channel, boolean on) {
		GreenLanternFx fx = fx(player);
		if (fx.has(channel) != on) {
			player.setAttached(ModAttachments.GREEN_LANTERN_FX, fx.withChannel(channel, on));
		}
	}

	public static void ringRemove(ServerPlayer player, long start) {
		GreenLanternFx fx = fx(player);
		if (fx.ringRemoveStart() != start) {
			player.setAttached(ModAttachments.GREEN_LANTERN_FX, fx.withRingRemove(start));
		}
	}

	public static void clear(ServerPlayer player) {
		if (!GreenLanternFx.EMPTY.equals(fx(player))) {
			player.setAttached(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);
		}
	}
}
