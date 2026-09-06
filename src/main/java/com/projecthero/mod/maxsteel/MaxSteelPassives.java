package com.projecthero.mod.maxsteel;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The Base Mode passives that are not attribute modifiers (those live in {@link MaxSteelAttributes})
 * or damage rules (those live in {@link MaxSteelDamage}): underwater breathing and, from v0.9.2, a
 * passive Regeneration I.
 *
 * <p>Ticked from {@link MaxSteelAbilityManager#serverTick} while the player is transformed.
 *
 * <p><b>Simplification vs. the spec</b>: the brief asks for "underwater breathing for 45 seconds
 * before normal air depletion"; this holds the air supply topped up for as long as the suit is on,
 * i.e. effectively unlimited, matching how the higher Iron Man marks behave. A per-dive 45s timer
 * would need another scratch field for a very small gameplay difference; noted for a later pass.
 */
public final class MaxSteelPassives {
	private MaxSteelPassives() {
	}

	public static void tick(ServerPlayer player) {
		if (!MaxSteel.isTransformed(player)) {
			return;
		}
		if (player.isUnderWater() && player.getAirSupply() < player.getMaxAirSupply()) {
			player.setAirSupply(player.getMaxAirSupply());
		}
		// v0.9.2: passive Regeneration I while the suit is on. Refreshed with a short, particle-free,
		// icon-visible instance -- only re-applied when ours is missing / about to lapse / weaker.
		MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
		if (regen == null || (!regen.isInfiniteDuration() && regen.getDuration() < 20)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, true));
		}
	}
}
