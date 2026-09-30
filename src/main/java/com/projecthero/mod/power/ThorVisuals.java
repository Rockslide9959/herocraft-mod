package com.projecthero.mod.power;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4: the one place the server writes {@link ModAttachments#THOR_FX} -- which move animation just started (and
 * where it landed), which moves are being channelled, and the Thor's Armour suit clock. Only writes when something
 * changed, so a per-tick "still channelling" call costs nothing on the wire.
 */
public final class ThorVisuals {
	private ThorVisuals() {
	}

	public static ThorFx fx(Player player) {
		return player.getAttachedOrElse(ModAttachments.THOR_FX, ThorFx.EMPTY);
	}

	/** Starts one of the {@code ThorFx.ANIM_*} move animations now, landing at the caster's own feet. */
	public static void anim(ServerPlayer player, int id) {
		anim(player, id, player.position());
	}

	/** Starts one of the {@code ThorFx.ANIM_*} move animations now, landing at {@code at}. */
	public static void anim(ServerPlayer player, int id, Vec3 at) {
		player.setAttached(ModAttachments.THOR_FX, fx(player).withAnim(id, player.level().getGameTime(), at.x, at.y, at.z));
	}

	public static void channel(ServerPlayer player, int channel, boolean on) {
		ThorFx fx = fx(player);
		if (fx.has(channel) != on) {
			player.setAttached(ModAttachments.THOR_FX, fx.withChannel(channel, on, player.level().getGameTime()));
		}
	}

	/** Starts ({@code ThorFx.SUIT_UP} / {@code SUIT_DOWN}) or clears ({@code SUIT_NONE}) the suit clock. */
	public static void suit(ServerPlayer player, int dir) {
		ThorFx fx = fx(player);
		if (dir == ThorFx.SUIT_NONE && fx.suitDir() == ThorFx.SUIT_NONE) {
			return;
		}
		player.setAttached(ModAttachments.THOR_FX, fx.withSuit(dir, player.level().getGameTime()));
	}
}
