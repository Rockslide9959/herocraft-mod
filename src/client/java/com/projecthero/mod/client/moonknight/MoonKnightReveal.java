package com.projecthero.mod.client.moonknight;

import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.data.MoonKnightState;

import net.minecraft.world.entity.player.Player;

/**
 * v0.13.20: how much of Moon Knight's suit is visible right now, for the pixel-by-pixel materialise. Read off the
 * synced transformation clock ({@link MoonKnightState#transformStart}) and flags, so every viewer sees the same
 * pixels appear: 0 -> 1 over the H transformation, 1 -> 0 over the dissolve when H takes it off, 1 otherwise.
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
			// fully formed a few ticks before the clock ends, so the last pixels land as the bandages finish
			return clamp(elapsed / (MoonKnightConfig.TRANSFORM_TICKS - 4.0f));
		}
		if (a.has(MoonKnightAction.FLAG_UNTRANSFORMING)) {
			return clamp(1.0f - elapsed / (float) MoonKnightConfig.UNTRANSFORM_TICKS);
		}
		return 1.0f;
	}

	private static float clamp(float v) {
		return Math.max(0.0f, Math.min(1.0f, v));
	}
}
