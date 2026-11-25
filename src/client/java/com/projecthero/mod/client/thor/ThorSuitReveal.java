package com.projecthero.mod.client.thor;

import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorVisuals;
import com.projecthero.mod.thorarmor.ThorArmor;

import net.minecraft.world.entity.player.Player;

/**
 * v0.14.4: Thor's Armour materialises piece by piece on the suit clock and comes off the same way in reverse. v0.17.7 (user: "no i
 * still want the suit and armour i was just saying remove thors actual skin form the suit and keep the helmet"): the whole suit is
 * back -- boots, greaves, the chestplate with its arm plates and bracers, then the winged helmet -- and each piece now appears whole
 * as its bolt lands and settles onto the body in a few ticks ({@link #pieceScale}, applied to the GeckoLib bones by
 * {@code SuperheroArmorRenderer#applyBoneVisibilityBySlot}) instead of sweeping on pixel row by pixel row; coming off, each piece
 * swells away and vanishes. The cape unrolls down from the collar last ({@link ThorCapeLayer}). Read off the synced suit clock
 * ({@link ThorFx#suitDir()} / {@link ThorFx#suitStart()}) with {@link ThorArmor#partProgress}, so every viewer sees the same thing.
 */
public final class ThorSuitReveal {
	/** How much bigger than its fitted size a piece is when it first appears (it settles down to 1). */
	private static final float POP = 0.35f;

	private ThorSuitReveal() {
	}

	/** How much of {@code part} ({@code ThorArmor.PART_*}) is showing right now (0..1). */
	public static float progress(Player player, int part, float partialTick) {
		ThorFx fx = ThorVisuals.fx(player);
		if (fx.suitDir() == ThorFx.SUIT_NONE) {
			return 1.0f;
		}
		float elapsed = player.level().getGameTime() - fx.suitStart() + partialTick;
		return ThorArmor.partProgress(fx.suitDir(), part, elapsed);
	}

	/**
	 * The scale to draw {@code part}'s bones at right now: 0 = not there yet (or gone), otherwise 1 + a little, easing out to exactly 1
	 * as the piece locks on (and, coming off, swelling back out before it vanishes).
	 */
	public static float pieceScale(Player player, int part, float partialTick) {
		float p = progress(player, part, partialTick);
		if (p <= 0.0f) {
			return 0.0f;
		}
		if (p >= 1.0f) {
			return 1.0f;
		}
		float rest = 1.0f - p;
		return 1.0f + POP * rest * rest * rest;
	}
}
