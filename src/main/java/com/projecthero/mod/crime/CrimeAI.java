package com.projecthero.mod.crime;

import com.projecthero.mod.mixin.MobGoalsAccessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.2: how a Syndicate crook behaves on a street crime. The crooks are the Syndicate's own thugs and gunmen (same
 * entities, skins and guns); a crime only rewrites their goals in memory, through {@link MobGoalsAccessor}. Nothing is
 * saved on the mob: when a crook's chunk reloads, {@code Crimes}' entity-load hook asks the owning crime to apply its
 * role again (or removes the crook if its crime is gone).
 */
public final class CrimeAI {
	/** Every crime crook carries this tag (on top of the event framework's own). */
	public static final String CROOK_TAG = "projecthero_crime_crook";
	/** The villager a mugging is aimed at. */
	public static final String VICTIM_TAG = "projecthero_crime_victim";

	public enum Role {
		/** Picks on the victim; turns on a player who hits him or gets in his face (6 blocks). */
		MUGGER,
		/** Runs where the crime sends him and never fights back: burglars, the getaway runner, the firebug. */
		RUNNER,
		/** A gunman covering the getaway: his normal gun AI, kept near the runner. */
		GUARD
	}

	private CrimeAI() {
	}

	public static void apply(Mob mob, Role role) {
		GoalSelector goals = ((MobGoalsAccessor) mob).projecthero$goalSelector();
		GoalSelector targets = ((MobGoalsAccessor) mob).projecthero$targetSelector();
		switch (role) {
			case MUGGER -> {
				remove(targets, g -> g instanceof NearestAttackableTargetGoal<?> || g instanceof CloseThreatGoal);
				targets.addGoal(2, new CloseThreatGoal(mob));
			}
			case RUNNER -> {
				remove(goals, g -> !(g instanceof FloatGoal) && !(g instanceof OpenDoorGoal));
				remove(targets, g -> true);
				mob.setTarget(null);
			}
			case GUARD -> {
				// his own gun AI, but players only (no picking fights with the village's iron golems)
				remove(targets, g -> g instanceof NearestAttackableTargetGoal<?>);
				targets.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
			}
		}
	}

	/** Removes matching goals, stopping any that are running first (removeAllGoals alone leaves their flags locked). */
	private static void remove(GoalSelector selector, java.util.function.Predicate<Goal> which) {
		for (var wrapped : java.util.List.copyOf(selector.getAvailableGoals())) {
			if (wrapped.isRunning() && which.test(wrapped.getGoal())) {
				wrapped.stop();
			}
		}
		selector.removeAllGoals(which);
	}

	/** A mugger only notices a player who comes within 6 blocks (being hit is the vanilla hurt-by goal's job). */
	static final class CloseThreatGoal extends NearestAttackableTargetGoal<Player> {
		CloseThreatGoal(Mob mob) {
			super(mob, Player.class, 5, true, false, p -> p.distanceToSqr(mob) < 36.0);
		}
	}

	/** True if {@code goal} is one of ours -- for tests. */
	static boolean isCrimeGoal(Goal goal) {
		return goal instanceof CloseThreatGoal;
	}
}
