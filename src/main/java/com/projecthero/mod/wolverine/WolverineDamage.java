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
				Wolverine.markHurt(p);
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

	/**
	 * v0.12.43: a fall that would kill him leaves him at half a heart instead. The Healing Factor pool takes the damage (at most
	 * {@code FALL_SURVIVE_MAX_ABSORB}; anything beyond that is simply not taken), his legs turn to raw flesh and he is slowed.
	 * Needs some pool left -- with the healing factor spent, a lethal fall still kills.
	 */
	public static boolean survivedLethalFall(ServerPlayer player, float damage) {
		var s = Wolverine.state(player);
		if (damage < player.getHealth() + player.getAbsorptionAmount() || s.healPool <= 0.0f) {
			return false;
		}
		long now = player.level().getGameTime();
		var c = s.copy();
		c.healPool = Math.max(0.0f, s.healPool - Math.min(damage, WolverineConfig.FALL_SURVIVE_MAX_ABSORB));
		c.lastHurtAt = now;
		c.legFleshStartedAt = now;
		Wolverine.save(player, c);
		player.setAbsorptionAmount(0.0f);
		player.setHealth(1.0f);
		player.fallDistance = 0.0f;
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
				WolverineConfig.FALL_SLOW_TICKS, WolverineConfig.FALL_SLOW_AMPLIFIER, true, false, false));
		if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_BIG_FALL,
					net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.6f);
			level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.65f, 0.0f, 0.02f), 1.6f),
					player.getX(), player.getY() + 0.3, player.getZ(), 40, 0.5, 0.3, 0.5, 0.1);
		}
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.wolverine.fall_survived")
				.withStyle(net.minecraft.ChatFormatting.RED), true);
		return true;
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
		if (source.is(DamageTypes.FALL) && survivedLethalFall(player, reduced)) {
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
