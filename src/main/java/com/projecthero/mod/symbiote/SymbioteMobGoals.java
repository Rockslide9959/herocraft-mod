package com.projecthero.mod.symbiote;

import com.projecthero.mod.mixin.MobGoalsAccessor;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.4: the AI a Symbiote bolts onto an animal it takes over. Goals are not saved with an entity (vanilla
 * rebuilds them in the constructor), so everything here is re-installed from {@code ServerEntityEvents.ENTITY_LOAD}
 * ({@link #onLoad}) as well as at the moment of the takeover -- idempotently, recognised by the {@link SymbioteGoal}
 * marker.
 *
 * <ul>
 *   <li><b>Infested animal</b> (an untamed cow, pig, sheep, chicken, rabbit, fox, ocelot, wild wolf or stray cat):
 *       loses its panic / tempt / breed / flee / follow-parent goals and gains a melee attack plus
 *       retaliate-and-hunt-players targeting. Animals with no attack-damage attribute (cow, pig...) bite for a
 *       flat {@link #PASSIVE_BITE_DAMAGE} instead of vanilla's attribute read, which would throw.</li>
 *   <li><b>Symbiote Pet cat</b>: vanilla cats never fight, so it gets the wolf's owner-defence targeting and a
 *       melee attack. A Symbiote Pet wolf needs nothing -- vanilla wolves already do all of that.</li>
 * </ul>
 */
public final class SymbioteMobGoals {
	/** What an infested animal with no attack attribute of its own bites for. */
	public static final float PASSIVE_BITE_DAMAGE = 5.0f;

	private SymbioteMobGoals() {
	}

	/** Marker: every goal this class adds, so a re-install is a no-op and a release removes exactly these. */
	interface SymbioteGoal {
	}

	/** Is this host one whose AI we rewrite (an animal that would otherwise flee rather than fight)? */
	public static boolean isInfestedAnimal(Mob mob) {
		return mob instanceof Animal && !(mob instanceof Enemy);
	}

	public static void onLoad(Mob mob) {
		if (SymbiotePet.is(mob) && mob instanceof TamableAnimal pet) {
			installPet(pet);
		} else if (SymbioteHost.is(mob) && isInfestedAnimal(mob)) {
			installHostile(mob);
		}
	}

	public static void installHostile(Mob mob) {
		if (!(mob instanceof PathfinderMob pm) || installed(mob)) {
			return;
		}
		GoalSelector goals = goals(mob);
		GoalSelector targets = targets(mob);
		goals.removeAllGoals(g -> g instanceof PanicGoal || g instanceof TemptGoal || g instanceof BreedGoal
				|| g instanceof AvoidEntityGoal || g instanceof FollowParentGoal);
		goals.addGoal(1, new MaulGoal(pm, 1.35));
		targets.addGoal(1, new RetaliateGoal(pm));
		targets.addGoal(2, new HuntPlayersGoal(mob));
	}

	public static void installPet(TamableAnimal pet) {
		if (installed(pet) || !(pet instanceof Cat)) {
			return;
		}
		GoalSelector goals = goals(pet);
		GoalSelector targets = targets(pet);
		goals.addGoal(2, new MaulGoal(pet, 1.3));
		targets.addGoal(1, new DefendOwnerGoal(pet));
		targets.addGoal(2, new AssistOwnerGoal(pet));
		targets.addGoal(3, new RetaliateGoal(pet));
	}

	/** Strip every goal this class added (the Symbiote left the mob). */
	public static void removeAll(Mob mob) {
		goals(mob).removeAllGoals(g -> g instanceof SymbioteGoal);
		targets(mob).removeAllGoals(g -> g instanceof SymbioteGoal);
	}

	public static boolean installed(Mob mob) {
		return goals(mob).getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof SymbioteGoal)
				|| targets(mob).getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof SymbioteGoal);
	}

	private static GoalSelector goals(Mob mob) {
		return ((MobGoalsAccessor) mob).projecthero$goalSelector();
	}

	private static GoalSelector targets(Mob mob) {
		return ((MobGoalsAccessor) mob).projecthero$targetSelector();
	}

	/** One melee hit from a Symbiote-driven animal. */
	static void strike(Mob mob, LivingEntity target) {
		if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
			mob.doHurtTarget(target);
			return;
		}
		if (target.hurt(mob.damageSources().mobAttack(mob), PASSIVE_BITE_DAMAGE)) {
			target.knockback(0.4, mob.getX() - target.getX(), mob.getZ() - target.getZ());
			mob.setLastHurtMob(target);
		}
	}

	private static boolean sitting(Mob mob) {
		return mob instanceof TamableAnimal t && (t.isOrderedToSit() || t.isInSittingPose());
	}

	// ---------------- goals ----------------

	static final class MaulGoal extends MeleeAttackGoal implements SymbioteGoal {
		MaulGoal(PathfinderMob mob, double speed) {
			super(mob, speed, true);
		}

		@Override
		public boolean canUse() {
			return !sitting(mob) && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return !sitting(mob) && super.canContinueToUse();
		}

		@Override
		protected void checkAndPerformAttack(LivingEntity target) {
			if (canPerformAttack(target)) {
				resetAttackCooldown();
				mob.swing(InteractionHand.MAIN_HAND);
				strike(mob, target);
			}
		}
	}

	static final class RetaliateGoal extends HurtByTargetGoal implements SymbioteGoal {
		RetaliateGoal(PathfinderMob mob) {
			super(mob);
		}
	}

	static final class HuntPlayersGoal extends NearestAttackableTargetGoal<Player> implements SymbioteGoal {
		HuntPlayersGoal(Mob mob) {
			super(mob, Player.class, 10, true, false, null);
		}
	}

	static final class DefendOwnerGoal extends OwnerHurtByTargetGoal implements SymbioteGoal {
		DefendOwnerGoal(TamableAnimal pet) {
			super(pet);
		}
	}

	static final class AssistOwnerGoal extends OwnerHurtTargetGoal implements SymbioteGoal {
		AssistOwnerGoal(TamableAnimal pet) {
			super(pet);
		}
	}
}
