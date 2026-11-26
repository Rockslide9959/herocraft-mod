package com.projecthero.mod.hero.power;

import java.util.Set;

import com.projecthero.mod.hero.HeroConfig;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * v0.17.7 City tier: the one damage path every mutation hit goes through, so stacked mutations (several owned at once,
 * all passives running) and buff modes never lift a single hit past {@link HeroConfig#mutationMaxHit} (30, the top of
 * City's ultimate range). Boss per-hit caps still apply on top (they live in the bosses' own hurt code).
 *
 * <ul>
 *   <li>Ability hits: {@link #hurt}, {@link #hurtBurst}, {@link #hurtLands} cap the amount, then hand it to
 *       {@link AbilityHelpers} (the shared floor: squads, pets, PvP config).</li>
 *   <li>Plain melee: the passive / mode attack-damage modifiers mutations add ({@link #MELEE_MODIFIERS}) never push a
 *       swing past the cap. {@code PlayerAttackComboMixin} brackets the swing, {@code LivingEntityMutationHitMixin}
 *       trims the hit. A swing that is already over the cap WITHOUT the mutation modifiers (another power's weapon)
 *       is left alone -- the cap only removes what mutations added.</li>
 * </ul>
 */
public final class MutationDamage {
	/** The attack-damage modifier ids mutations add (passives and modes, all 27 powers). */
	private static final Set<ResourceLocation> MELEE_MODIFIERS = Set.of(
			id("strength_passive_attack"), id("strength_effort_attack"), id("overdrive_attack_damage"),
			id("crystal_armor_atk"), id("electro_charged_melee"), id("cryo_ice_blade"), id("elastic_gear2_damage"),
			id("density_atk"), id("shadow_cloak_atk"), id("energy_supercharged_atk"), id("gravity_nexus_atk"),
			id("water_aquatic_melee"), id("size_atk"));

	/** The player whose melee swing is being resolved right now (server thread), or null. */
	private static final ThreadLocal<ServerPlayer> SWING = new ThreadLocal<>();

	private MutationDamage() {
	}

	private static ResourceLocation id(String path) {
		return com.projecthero.mod.ProjectHeroMod.id(path);
	}

	/** The per-hit ceiling for mutation damage (default 30). */
	public static float maxHit() {
		return (float) HeroConfig.get().mutationMaxHit;
	}

	/** {@code amount}, never above {@link #maxHit()}. Apply after every multiplier. */
	public static float cap(float amount) {
		return Math.min(amount, maxHit());
	}

	public static boolean hurt(ServerPlayer source, LivingEntity target, float amount) {
		return AbilityHelpers.hurt(source, target, cap(amount));
	}

	public static boolean hurt(ServerPlayer source, LivingEntity target, DamageSource damageSource, float amount) {
		return AbilityHelpers.hurt(source, target, damageSource, cap(amount));
	}

	public static boolean hurtBurst(ServerPlayer source, LivingEntity target, float amount) {
		return AbilityHelpers.hurtBurst(source, target, cap(amount));
	}

	public static boolean hurtBurst(ServerPlayer source, LivingEntity target, DamageSource damageSource, float amount) {
		return AbilityHelpers.hurtBurst(source, target, damageSource, cap(amount));
	}

	public static boolean hurtLands(ServerPlayer source, LivingEntity target, float amount) {
		return AbilityHelpers.hurtLands(source, target, cap(amount));
	}

	// ---------------- plain melee ----------------

	public static void beforeMelee(ServerPlayer player) {
		SWING.set(player);
	}

	public static void afterMelee() {
		SWING.remove();
	}

	/** Called at the top of {@code LivingEntity#hurt} (server side): trims a melee swing's mutation bonus to the cap. */
	public static float onHurt(DamageSource source, float amount) {
		ServerPlayer swinger = SWING.get();
		if (swinger == null || source.getEntity() != swinger || source.getDirectEntity() != swinger) {
			return amount;
		}
		float max = maxHit();
		if (amount <= max) {
			return amount;
		}
		AttributeInstance inst = swinger.getAttribute(Attributes.ATTACK_DAMAGE);
		if (inst == null) {
			return amount;
		}
		double with = inst.getValue();
		double without = valueWithout(inst);
		if (with <= 0.0 || without >= with - 1.0e-4) {
			return amount; // no mutation melee bonus on this swing
		}
		float unbuffed = (float) (amount * (without / with));
		return Math.min(amount, Math.max(max, unbuffed));
	}

	/** The attribute's value with every mutation modifier left out (vanilla's add -> base-multiply -> total-multiply). */
	private static double valueWithout(AttributeInstance inst) {
		double base = inst.getBaseValue();
		double add = 0.0;
		double multBase = 0.0;
		double multTotal = 1.0;
		for (AttributeModifier m : inst.getModifiers()) {
			if (MELEE_MODIFIERS.contains(m.id())) {
				continue;
			}
			switch (m.operation()) {
				case ADD_VALUE -> add += m.amount();
				case ADD_MULTIPLIED_BASE -> multBase += m.amount();
				case ADD_MULTIPLIED_TOTAL -> multTotal *= 1.0 + m.amount();
			}
		}
		double v = (base + add) * (1.0 + multBase) * multTotal;
		return inst.getAttribute().value().sanitizeValue(v);
	}
}
