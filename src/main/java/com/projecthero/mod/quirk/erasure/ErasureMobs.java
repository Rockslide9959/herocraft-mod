package com.projecthero.mod.quirk.erasure;

import com.projecthero.mod.hero.PowerSuppression;

import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * v0.16.7: what Erasure ({@link PowerSuppression}) does to a MOB. Its basic body (walking, melee) keeps working; its
 * powers do not:
 * <ul>
 *   <li>the "power" goals refuse to run ({@code ErasureWrappedGoalMixin}, only while that mob's AI step runs -- see
 *       {@code ErasureMobAiMixin}): bows / crossbows / ranged goals (skeletons, strays, bogged, pillagers, illusioners,
 *       witches, snow golems, the wither), the creeper's swell, every spellcaster-illager spell (evokers, illusioners)
 *       and the guardian beam;</li>
 *   <li>anything it would launch or summon is never added to the world ({@code ErasureServerLevelSpawnMixin}): arrows,
 *       fireballs (blazes, ghasts, the dragon), wither skulls, shulker bullets, potions, wind charges, evoker fangs, vexes,
 *       dragon's breath;</li>
 *   <li>creepers never explode, even lit ({@code ErasureCreeperMixin}); endermen and shulkers never teleport
 *       ({@code ErasureEnderManMixin} / {@code ErasureShulkerMixin});</li>
 *   <li>the mod's bosses hold their special moves: each boss's move picker asks {@link #specialsHeld} (one-line checks in
 *       the Behemoth, Ultron Prime / Omega, Galactus and King Laufey).</li>
 * </ul>
 */
public final class ErasureMobs {
	/** True while an erased mob's server AI step is running (server thread only). */
	private static boolean aiErased;

	private static final ClassValue<Boolean> POWER_GOAL = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			if (RangedBowAttackGoal.class.isAssignableFrom(type) || RangedCrossbowAttackGoal.class.isAssignableFrom(type)
					|| RangedAttackGoal.class.isAssignableFrom(type) || SwellGoal.class.isAssignableFrom(type)) {
				return true;
			}
			for (Class<?> c = type; c != null && c != Goal.class && c != Object.class; c = c.getSuperclass()) {
				Class<?> outer = c.getEnclosingClass();
				if (outer != null && (SpellcasterIllager.class.isAssignableFrom(outer) || Guardian.class.isAssignableFrom(outer))) {
					return true;
				}
			}
			return false;
		}
	};

	private ErasureMobs() {
	}

	/** {@code Mob.serverAiStep} HEAD. */
	public static void enterAi(Mob mob) {
		aiErased = PowerSuppression.isSuppressed(mob);
	}

	/** {@code Mob.serverAiStep} RETURN. */
	public static void exitAi() {
		aiErased = false;
	}

	/** A goal of a power (ranged, swell, spell, beam). */
	public static boolean isPowerGoal(Goal goal) {
		return goal != null && POWER_GOAL.get(goal.getClass());
	}

	/** The running mob is erased and this goal is one of its powers. */
	public static boolean blocksGoal(Goal goal) {
		return aiErased && isPowerGoal(goal);
	}

	/** Server side: this creature's special moves are held right now (bosses' move pickers call this). */
	public static boolean specialsHeld(Entity e) {
		return e != null && !e.level().isClientSide() && PowerSuppression.isSuppressed(e);
	}

	/** A mob with a power the goggles pick out: anything ranged, explosive, teleporting, spellcasting, or a boss. */
	public static boolean isSpecial(net.minecraft.world.entity.LivingEntity e) {
		return e instanceof net.minecraft.world.entity.monster.RangedAttackMob
				|| e instanceof net.minecraft.world.entity.monster.Creeper
				|| e instanceof net.minecraft.world.entity.monster.EnderMan
				|| e instanceof net.minecraft.world.entity.monster.Blaze
				|| e instanceof net.minecraft.world.entity.monster.Ghast
				|| e instanceof net.minecraft.world.entity.monster.Shulker
				|| e instanceof Guardian
				|| e instanceof SpellcasterIllager
				|| e instanceof Vex
				|| e instanceof net.minecraft.world.entity.monster.warden.Warden
				|| e instanceof net.minecraft.world.entity.monster.breeze.Breeze
				|| ErasureMoves.isBoss(e);
	}

	/** Something an erased mob launches / summons: not added to the world. */
	public static boolean blocksSpawn(Entity e) {
		Entity owner = null;
		if (e instanceof Projectile pr) {
			owner = pr.getOwner();
		} else if (e instanceof EvokerFangs f) {
			owner = f.getOwner();
		} else if (e instanceof Vex v) {
			owner = v.getOwner();
		} else if (e instanceof AreaEffectCloud c) {
			owner = c.getOwner();
		}
		return owner instanceof Mob && specialsHeld(owner);
	}
}
