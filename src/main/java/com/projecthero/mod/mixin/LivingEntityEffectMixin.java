package com.projecthero.mod.mixin;

import java.util.Set;

import com.projecthero.mod.hero.ExperimentalPowers;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Super Regeneration (power 12) and its Wolverine ascension: the listed negative effects affect the mutant 50% less. Implemented
 * by halving the duration of a fresh application before vanilla stores it, so Poison, Wither,
 * Weakness, Slowness, Mining Fatigue, Nausea and Hunger all wear off in half the time. Amplifier is
 * left alone. Inert for every other entity / power.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin {
	private static final Set<Holder<MobEffect>> PROJECTHERO$HALVED = Set.of(
			MobEffects.POISON,
			MobEffects.WITHER,
			MobEffects.WEAKNESS,
			MobEffects.MOVEMENT_SLOWDOWN,
			MobEffects.DIG_SLOWDOWN,
			MobEffects.CONFUSION,
			MobEffects.HUNGER);

	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance projecthero$superRegenerationResistsDebuffs(MobEffectInstance instance,
			MobEffectInstance instanceArg, Entity source) {
		if (instance == null || instance.isInfiniteDuration() || instance.getDuration() <= 1) {
			return instance;
		}
		if (!((Object) this instanceof ServerPlayer player)) {
			return instance;
		}
		boolean wolverine = com.projecthero.mod.wolverine.Wolverine.hasPower(player);
		if (!PROJECTHERO$HALVED.contains(instance.getEffect())
				|| !(wolverine || ExperimentalPowers.owns(player, "power_12_super_regeneration"))) {
			return instance;
		}
		// Wolverine ascends Super Regeneration: it keeps the 50% debuff cut, and Poison / Wither are cut harder.
		if (wolverine && (instance.getEffect() == MobEffects.POISON || instance.getEffect() == MobEffects.WITHER)) {
			return new MobEffectInstance(instance.getEffect(),
					Math.max(1, (int) (instance.getDuration() * com.projecthero.mod.wolverine.WolverineConfig.POISON_WITHER_DURATION_FACTOR)),
					instance.getAmplifier(), instance.isAmbient(), instance.isVisible(), instance.showIcon());
		}
		return new MobEffectInstance(instance.getEffect(), Math.max(1, instance.getDuration() / 2),
				instance.getAmplifier(), instance.isAmbient(), instance.isVisible(), instance.showIcon());
	}
}
