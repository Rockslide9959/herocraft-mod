package com.projecthero.mod.moonknight.ability;

import java.util.List;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.item.MoonKnightTruncheonItem;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Shared combat plumbing for Moon Knight's darts, cape, grapple, dash and truncheon (Phases 3-4): who counts as an enemy (never a squadmate
 * or your own pet), how an ability hit is dealt (lunar-scaled, boss-capped, PvP-gated through {@link AbilityHelpers}),
 * the moonlight particle colours, and the session reset for every static map those four keys keep.
 *
 * <p>Ability hits are dealt inside {@link #ABILITY_HIT} so the Truncheon combo (which listens to every hit the Moon
 * Knight lands with his own hand) doesn't count a staff spin or a slam as a melee swing.
 */
public final class MoonKnightCombat {
	/** Moonlight white-silver, pale blue, and the shadow used by Shadow Step. */
	public static final DustParticleOptions MOON = new DustParticleOptions(new Vector3f(0.92f, 0.95f, 1.0f), 1.1f);
	public static final DustParticleOptions PALE_BLUE = new DustParticleOptions(new Vector3f(0.62f, 0.8f, 1.0f), 1.0f);
	public static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.07f, 0.07f, 0.11f), 1.6f);

	private static final ThreadLocal<Boolean> ABILITY_HIT = ThreadLocal.withInitial(() -> false);

	private MoonKnightCombat() {
	}

	/**
	 * From {@code MoonKnightEntities.initialize()}: the Truncheon can never lie in the world as an item (dropped,
	 * thrown out of a death pile, spilled from a container) -- it simply vanishes, like the suit pieces.
	 */
	public static void initializeEvents() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity item && item.getItem().getItem() instanceof MoonKnightTruncheonItem) {
				item.discard();
			}
		});
	}

	/** True while one of Moon Knight's ability hits is being dealt (not a hand swing). */
	public static boolean isAbilityHit() {
		return ABILITY_HIT.get();
	}

	/** Never touch yourself, a squadmate, a pet you own, or any player while PvP is off. */
	public static boolean friendly(ServerPlayer player, LivingEntity target) {
		// v0.14.20: the inverse of the shared rule 1 (com.projecthero.mod.combat.HeroTargets#canHarm)
		return target == player || !com.projecthero.mod.combat.HeroTargets.canHarm(player, target);
	}

	/** Everything hostile to {@code player} within {@code radius} of {@code center} (the PvP gate included). */
	public static List<LivingEntity> enemies(ServerPlayer player, Vec3 center, double radius) {
		return AbilityHelpers.enemiesAround(player, center, radius).stream().filter(e -> !friendly(player, e)).toList();
	}

	/** Cap one hit on a boss the way the other heroes do. */
	public static float bossCapped(LivingEntity target, float amount) {
		if (TitanCombat.isBoss(target)) {
			return Math.min(amount, Math.max(2.0f, target.getMaxHealth() * MoonKnightConfig.BOSS_MAX_FRACTION_PER_HIT));
		}
		return amount;
	}

	/** One ability hit with the player's own attack source. True if it landed. */
	public static boolean hit(ServerPlayer player, LivingEntity target, float amount) {
		return hit(player, target, player.damageSources().playerAttack(player), amount);
	}

	/** One ability hit, already lunar-scaled by the caller; boss-capped, squad-safe, PvP-gated. True if it landed. */
	public static boolean hit(ServerPlayer player, LivingEntity target, DamageSource source, float amount) {
		if (friendly(player, target) || !target.isAlive()) {
			return false;
		}
		float dealt = bossCapped(target, amount);
		boolean outer = ABILITY_HIT.get();
		ABILITY_HIT.set(true);
		try {
			return AbilityHelpers.hurtBurst(player, target, source, dealt);
		} finally {
			ABILITY_HIT.set(outer);
		}
	}

	/** Knock {@code target} away from {@code origin} (never a boss). */
	public static void knock(LivingEntity target, Vec3 origin, double strength, double lift) {
		if (TitanCombat.isBoss(target) || strength <= 0.0) {
			return;
		}
		AbilityHelpers.knockbackFrom(target, origin, strength);
		if (lift > 0.0) {
			AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
		}
	}

	/** Server stop: drop every static map the four keys keep. */
	public static void clearSessionState() {
		MoonKnightDarts.clearSessionState();
		MoonKnightCape.clearSessionState();
		MoonKnightGrapple.clearSessionState();
		MoonKnightTruncheon.clearSessionState();
		MoonKnightDash.clearSessionState();
	}
}
