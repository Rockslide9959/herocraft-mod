package com.projecthero.mod.hulk.gladiator;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.5: Gladiator Hulk's melee swings alternate hands -- hammer (right hand), then axe (left hand), then hammer...
 * strictly, never at random (Wolverine's claws pick a random hand). Stateless: the next swing is simply the other hand
 * from the last one ({@link Player#swingingArm}, which vanilla keeps after a swing ends and syncs to every viewer with
 * the swing itself). The hit is vanilla's either way, so the damage is the same from both weapons; only which arm swings
 * (first person, the GeckoLib {@code punch} / {@code punch_left} clips everyone else sees) changes.
 *
 * <p>Client-safe: the local player picks the hand for a left-click ({@code GladiatorAttackMixin}), the server for the
 * unwilling Hulk's rampage swings ({@code HulkControl}).
 */
public final class GladiatorSwing {
	private GladiatorSwing() {
	}

	/** True while his left-click swings alternate: out as Gladiator Hulk. */
	public static boolean alternates(Player player) {
		return GladiatorAbilities.active(player);
	}

	/** The hand after {@code last} (the hammer's -- the main hand -- when he has not swung yet). */
	public static InteractionHand after(InteractionHand last) {
		return last == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	/**
	 * The hand this swing should use: the other one from his last swing as Gladiator Hulk, {@code vanilla} (what the
	 * game would have swung) for anyone else.
	 */
	public static InteractionHand next(Player player, InteractionHand vanilla) {
		return alternates(player) ? after(player.swingingArm) : vanilla;
	}

	/** The arm a hand swings (the main hand is the main arm -- the right one unless he plays left-handed). */
	public static HumanoidArm arm(Player player, InteractionHand hand) {
		return hand == InteractionHand.OFF_HAND ? player.getMainArm().getOpposite() : player.getMainArm();
	}
}
