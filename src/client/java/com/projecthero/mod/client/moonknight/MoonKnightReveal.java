package com.projecthero.mod.client.moonknight;

import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAlter;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.data.MoonKnightState;

import net.minecraft.world.entity.player.Player;

/**
 * v0.13.20: how much of Moon Knight's suit is visible right now, for the pixel-by-pixel materialise. Read off the
 * synced transformation clock ({@link MoonKnightState#transformStart}) and flags, so every viewer sees the same
 * pixels appear: 0 -> 1 over the H transformation, 1 -> 0 over the dissolve when H takes it off, 1 otherwise.
 * v0.13.21: both take exactly 1.5 s ({@code TRANSFORM_TICKS} / {@code UNTRANSFORM_TICKS} = 30), and an alter change
 * rematerialises the new alter's suit over the old one the same way ({@link #swapProgress}).
 */
public final class MoonKnightReveal {
	private MoonKnightReveal() {
	}

	public static float progress(Player player, float partialTick) {
		MoonKnightState s = MoonKnight.peek(player);
		if (s == null) {
			return 1.0f;
		}
		MoonKnightAction a = MoonKnightAnim.action(player);
		float elapsed = player.level().getGameTime() - s.transformStart + partialTick;
		if (a.has(MoonKnightAction.FLAG_TRANSFORMING)) {
			return clamp(elapsed / (float) MoonKnightConfig.TRANSFORM_TICKS);
		}
		if (a.has(MoonKnightAction.FLAG_UNTRANSFORMING)) {
			return clamp(1.0f - elapsed / (float) MoonKnightConfig.UNTRANSFORM_TICKS);
		}
		return 1.0f;
	}

	/**
	 * The alter whose suit is being replaced right now, or null if no swap is running (none started, it finished,
	 * or it would swap a suit for the same one).
	 */
	public static MoonKnightAlter swapFrom(Player player) {
		MoonKnightAction a = MoonKnightAnim.action(player);
		if (a.swapFrom < 0 || !MoonKnight.isTransformed(player)) {
			return null;
		}
		long age = player.level().getGameTime() - a.swapStart;
		if (age < 0 || age >= MoonKnightConfig.ALTER_SWAP_TICKS) {
			return null;
		}
		MoonKnightAlter from = MoonKnightAlter.byOrdinal(a.swapFrom);
		return from == MoonKnight.alter(player) ? null : from;
	}

	/** 0 -> 1 as the new alter's suit covers the old one (1 when no swap is running). */
	public static float swapProgress(Player player, float partialTick) {
		if (swapFrom(player) == null) {
			return 1.0f;
		}
		MoonKnightAction a = MoonKnightAnim.action(player);
		return clamp((player.level().getGameTime() - a.swapStart + partialTick) / (float) MoonKnightConfig.ALTER_SWAP_TICKS);
	}

	private static float clamp(float v) {
		return Math.max(0.0f, Math.min(1.0f, v));
	}
}
