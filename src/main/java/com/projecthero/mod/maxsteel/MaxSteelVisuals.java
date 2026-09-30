package com.projecthero.mod.maxsteel;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.maxsteel.data.MaxSteelFx;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.2: the server's writes to {@link MaxSteelFx} (poses, charge clocks, lock-on, mode swap). Every write is a
 * whole-record replace so the attachment re-syncs to everyone tracking the player.
 */
public final class MaxSteelVisuals {
	private MaxSteelVisuals() {
	}

	public static MaxSteelFx get(Player player) {
		MaxSteelFx fx = player.getAttachedOrElse(ModAttachments.MAX_STEEL_FX, null);
		return fx == null ? MaxSteelFx.EMPTY : fx;
	}

	private static void set(ServerPlayer player, MaxSteelFx fx) {
		if (!fx.equals(get(player))) {
			player.setAttached(ModAttachments.MAX_STEEL_FX, fx);
		}
	}

	/** Starts a one-shot pose now. */
	public static void play(ServerPlayer player, int anim) {
		set(player, get(player).withAnim(anim, player.level().getGameTime()));
	}

	/** Starts (start = game time) or ends (0) the visible Turbo Blast charge. */
	public static void blastCharge(ServerPlayer player, long start) {
		set(player, get(player).withBlastCharge(start));
	}

	/** Starts / ends the visible Turbo Cannon charge and sets the lock-on target (-1 = none). */
	public static void cannonCharge(ServerPlayer player, long start, int target) {
		set(player, get(player).withCannonCharge(start, target));
	}

	/** The suit reconfigures from {@code from} to whatever mode it is in now: the swap animation + pose. */
	public static void swap(ServerPlayer player, MaxSteelMode from) {
		long now = player.level().getGameTime();
		set(player, get(player).withSwap(from.ordinal(), now).withAnim(MaxSteelFx.ANIM_SWAP, now));
	}

	/**
	 * v0.14.4: cancel a mode-swap animation (and its flex pose) -- used when the suit formed straight into a Turbo Mode,
	 * so entering the mode at the end of the armour-up does not rebuild the same form a second time.
	 */
	public static void clearSwap(ServerPlayer player) {
		MaxSteelFx fx = get(player).withSwap(-1, 0L);
		if (fx.anim() == MaxSteelFx.ANIM_SWAP) {
			fx = fx.withAnim(MaxSteelFx.ANIM_NONE, 0L);
		}
		set(player, fx);
	}

	/** Drop everything (suit-down, death, logout, power loss). */
	public static void clear(ServerPlayer player) {
		set(player, MaxSteelFx.EMPTY);
	}
}
