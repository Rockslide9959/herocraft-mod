package com.projecthero.mod.maxsteel;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Max Steel suit's incoming-damage mitigation. Same cancel-and-re-apply-smaller pattern as
 * {@code IronManDamage} / {@code HeroDamageRules}, because Fabric's {@code ALLOW_DAMAGE} is a boolean
 * veto with no "reduce amount".
 *
 * <ul>
 *   <li><b>Base Mode</b> (any transformed state): no flat reduction any more (v0.9.2) -- only the
 *       suit's raw diamond-tier armour protects the player.</li>
 *   <li><b>Turbo Strength Mode</b>: a real Resistance I effect (applied in {@link MaxSteelStrength}),
 *       plus -- while the player is crouching -- a {@link MaxSteelConfig#STRENGTH_SHIELD_BLOCK} shield
 *       block applied here.</li>
 *   <li><b>Turbo Stealth Mode</b>: a single hit above {@link MaxSteelConfig#STEALTH_BREAK_DAMAGE} raw
 *       breaks stealth (handled in Phase 6; the hook is here).</li>
 * </ul>
 *
 * <p>Also the one place "in combat" is marked for the T.U.R.B.O. regen rate. Falls are left to the
 * {@code SAFE_FALL_DISTANCE} / {@code FALL_DAMAGE_MULTIPLIER} attribute modifiers in
 * {@link MaxSteelAttributes}, not reduced here.
 */
public final class MaxSteelDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private MaxSteelDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(MaxSteelDamage::onAllowDamage);
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player)) {
			return true;
		}
		if (!MaxSteel.isTransformed(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}

		// self-inflicted Turbo Cannon impact never hurts the pilot (spec)
		if (MaxSteelCannon.isOwnCannonDamage(player, source)) {
			return false;
		}

		MaxSteelEnergy.markCombat(player);

		// Stealth breaks on a real hit.
		if (amount > MaxSteelConfig.STEALTH_BREAK_DAMAGE) {
			MaxSteelStealth.onHardHit(player);
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			// Flight-landing grace: no fall damage at all for a couple of seconds after Turbo Flight ends.
			if (player.level().getGameTime() < MaxSteel.state(player).fallGraceUntil) {
				player.resetFallDistance();
				return false;
			}
			return true; // otherwise the SAFE_FALL_DISTANCE / FALL_DAMAGE_MULTIPLIER attributes handle it
		}

		// v0.9.2: no flat Base-Mode reduction. Strength Mode's Resistance I is a real effect; the only
		// mitigation applied here is the Strength-Mode shield block while the player is bracing (crouch).
		float factor = 1f;
		if (MaxSteel.mode(player) == MaxSteelMode.STRENGTH && player.isCrouching()) {
			factor = 1f - MaxSteelConfig.STRENGTH_SHIELD_BLOCK;
		}

		if (factor >= 1f) {
			return true; // nothing to do -- let the hit through untouched
		}

		float reduced = amount * factor;
		if (reduced < 0.5f) {
			return false;
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, reduced);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}

	/** Call when the player deals damage, so the regen rate drops to the combat figure too. */
	public static void onDealtDamage(ServerPlayer player) {
		if (MaxSteel.isTransformed(player)) {
			MaxSteelEnergy.markCombat(player);
		}
	}

	static void hitFeedback(ServerPlayer player) {
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.2f, 1.8f);
	}
}
