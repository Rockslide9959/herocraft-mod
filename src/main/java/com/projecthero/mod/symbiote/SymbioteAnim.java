package com.projecthero.mod.symbiote;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.13.19: the Symbiote host's move animations. The server stamps {@link SymbioteVitals#animId} /
 * {@link SymbioteVitals#animStart} when a move fires; the client ({@code SymbiotePose}) turns that into a
 * keyframed arm / body pose on the vanilla model, which the GeckoLib suit copies -- so every viewer sees the
 * same thing and the suit follows it for free. Lengths (ticks) live here so the server knows when a pose
 * is over and the client knows how long to play it.
 */
public final class SymbioteAnim {
	public static final int NONE = 0;
	public static final int TENDRIL_STRIKE = 1;
	public static final int TENDRIL_SWEEP = 2;
	public static final int SPIKE_SHOT = 3;
	public static final int SPIKE_FAN = 4;
	public static final int LUNGE = 5;
	public static final int GRAPPLE = 6;
	public static final int BARRAGE = 7;
	public static final int BLADE_SLASH = 8;
	public static final int ONSLAUGHT_CHARGE = 9;
	public static final int ONSLAUGHT_RELEASE = 10;
	public static final int GRAB = 11;
	public static final int THROW = 12;
	public static final int BLADE_FORM = 13;
	public static final int SPIKES_FLEX = 14;
	public static final int RESURRECT = 15;

	private SymbioteAnim() {
	}

	/** Play {@code animId} on this host from now. */
	public static void play(ServerPlayer player, int animId) {
		SymbioteVitals c = player.getAttachedOrCreate(ModAttachments.SYMBIOTE_VITALS).copy();
		c.animId = animId;
		c.animStart = player.level().getGameTime();
		player.setAttached(ModAttachments.SYMBIOTE_VITALS, c);
	}

	/** Stop whatever is playing (only if it is {@code animId}, so a newer move is never cut off). */
	public static void stop(ServerPlayer player, int animId) {
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (v == null || v.animId != animId) {
			return;
		}
		SymbioteVitals c = v.copy();
		c.animId = NONE;
		player.setAttached(ModAttachments.SYMBIOTE_VITALS, c);
	}
}
