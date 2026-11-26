package com.projecthero.mod.shazam;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.EvokerFangs;

/**
 * v0.17.3 Shazam: hits on the champion (and the family), all while transformed.
 * <ul>
 *   <li><b>Magic</b> ({@link #isMagic}: potions, evokers, witches, Doctor Fate, Raven) hurts 50% more.</li>
 *   <li>No fall damage while transformed (v0.17.7; was only while flying, during the ultimate or after a dash, leap or slam).</li>
 *   <li>v0.17.7 Planet: <b>Stamina of Atlas</b> is netherite-level armour ({@link Shazam#reconcile}; was a flat 35% here), and
 *   <b>Courage of Achilles</b> is Resistance II (was Achilles Stand's total immunity and squad shield).</li>
 * </ul>
 * A changed hit is cancelled and re-issued behind a re-entrancy guard, vanilla's difficulty scaling undone first (as the Spin's).
 */
public final class ShazamDamage {
	/** Damage types that count as magic for the champion's weakness ({@code data/projecthero/tags/damage_type/shazam_weakness.json}). */
	public static final TagKey<DamageType> WEAKNESS = TagKey.create(Registries.DAMAGE_TYPE, ProjectHeroMod.id("shazam_weakness"));
	/** Hero powers whose every hit counts as magic. */
	public static final java.util.List<String> MAGIC_HEROES = java.util.List.of("doctor_fate", "raven");

	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private ShazamDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ShazamDamage::allowDamage);
		// the Wizard remembers who killed villagers and iron golems
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer
					&& (entity instanceof net.minecraft.world.entity.npc.AbstractVillager || entity instanceof net.minecraft.world.entity.animal.IronGolem)) {
				ShazamWizards.rememberInnocentKill(killer);
			}
		});
	}

	/** Magic for the champion's weakness: the tag, a witch / evoker / illusioner (or its fangs), or a Doctor Fate / Raven player. */
	public static boolean isMagic(DamageSource source) {
		if (source.is(WEAKNESS)) {
			return true;
		}
		Entity direct = source.getDirectEntity();
		Entity cause = source.getEntity();
		if (direct instanceof EvokerFangs || cause instanceof Witch || cause instanceof Evoker || cause instanceof Illusioner) {
			return true;
		}
		if (cause instanceof ServerPlayer p) {
			for (String key : MAGIC_HEROES) {
				if (HeroTiers.holdsHero(p, key)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The multiplier on a hit of {@code amount} the victim would take ({@code 0} = cancelled). For the tests too. */
	public static float factor(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer victim)) {
			return 1f;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 1f;
		}
		float f = 1f;
		if (Shazam.transformed(victim)) {
			// v0.17.7 (user: "Make shazam take no fall damage"): never, while transformed (was only while flying / after a move)
			if (source.is(DamageTypeTags.IS_FALL)) {
				return 0f;
			}
			if (isMagic(source)) {
				f *= ShazamConfig.MAGIC_WEAKNESS;
			}
			// v0.17.7 Planet: the old flat 35% is gone -- his toughness is netherite-level armour now (Shazam.reconcile), and
			// Courage of Achilles is Resistance II, no longer total immunity
		}
		return f;
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer victim)) {
			return true;
		}
		float f = factor(victim, source);
		if (f == 1f) {
			return true;
		}
		if (f <= 0f) {
			if (source.is(DamageTypeTags.IS_FALL)) {
				victim.resetFallDistance();
			}
			return false;
		}
		if (victim.invulnerableTime > 10) {
			return true; // vanilla will ignore this hit anyway
		}
		reissue(victim, source, amount * f);
		return false;
	}

	private static void reissue(LivingEntity target, DamageSource source, float amount) {
		if (source.scalesWithDifficulty()) {
			amount = switch (target.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		REENTRANT.set(true);
		try {
			target.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}
