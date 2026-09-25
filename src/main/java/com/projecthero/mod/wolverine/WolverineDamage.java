package com.projecthero.mod.wolverine;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * Wolverine's incoming-damage rules: 35% less physical damage (a further 20% during Rage, multiplied
 * -- 0.65 x 0.80, never additive-to-zero), 75% less fall damage, and the emergency-heal triggers.
 * Fabric's {@code ALLOW_DAMAGE} is a boolean veto, so this uses the same cancel-and-re-apply-smaller
 * pattern as {@code PunisherDamage} / {@code SymbioteDamageRules}, behind a re-entrancy guard so the
 * reduced hit is never reduced again.
 *
 * <p>Fire is deliberately not reduced (he is not fire-immune -- the healing factor outpaces it), and
 * neither are magic / poison / wither / hunger (armour-bypassing damage): those are answered by the
 * regeneration and the debuff-shortening mixin instead.
 */
public final class WolverineDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private WolverineDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(WolverineDamage::onAllowDamage);
		// Started by damage as well as the tick, so a single big hit cannot slip past the 5-tick scan.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, dealtAmount, blocked) -> {
			if (Wolverine.deployingClaws()) {
				return; // v0.12.20: deploying the claws is not a fight -- no Rage, no combat timer
			}
			if (entity instanceof ServerPlayer p && Wolverine.hasPower(p)) {
				Wolverine.markCombat(p);
				Wolverine.addRage(p, WolverineConfig.RAGE_PER_HIT);
			}
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && Wolverine.hasPower(attacker)) {
				Wolverine.markCombat(attacker);
				Wolverine.addRage(attacker, WolverineConfig.RAGE_PER_HIT);
			}
		});
		// A lethal hit while the emergency heal is ready is survived -- the healing factor kicks in at 1 HP.
		// Then the 60 s internal cooldown applies, so he is hard to kill but not immortal.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p) || !Wolverine.hasPower(p)) {
				return true;
			}
			if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
				return true; // /kill and the void still kill
			}
			if (WolverinePassives.tryEmergency(p)) {
				p.setHealth(Math.max(1.0f, p.getMaxHealth() * WolverineConfig.EMERGENCY_HEAL_FRACTION));
				return false;
			}
			return true;
		});
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player) || amount <= 0f || !Wolverine.hasPower(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (Wolverine.invulnerable(player)) {
			return false; // the resurrection window: nothing can hurt him (the void and /kill excepted above)
		}
		float factor = 1.0f;
		if (source.is(DamageTypes.FALL)) {
			factor *= 1.0f - WolverineConfig.FALL_REDUCTION;
		} else if (!source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			factor *= 1.0f - WolverineConfig.DAMAGE_REDUCTION;
			if (Wolverine.raging(player)) {
				factor *= 1.0f - WolverineConfig.RAGE_DAMAGE_REDUCTION;
			}
		}
		if (factor >= 0.999f) {
			return true;
		}
		float reduced = amount * factor;
		if (reduced < 0.1f) {
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
}
