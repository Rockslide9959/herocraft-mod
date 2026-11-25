package com.projecthero.mod.worthiness;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Whether a player holds the Power of Thor (a worthiness score at or above {@link #THRESHOLD}).
 *
 * <p>v0.17.7: worthiness is earned by <b>lifting a free Mjolnir</b> -- with full Valor, during a thunderstorm, after winning
 * the Trial of Asgard ({@code thoracquire.MjolnirLift} / {@code TrialOfAsgard}). Hero of the Village no longer opens it (it
 * gives Valor instead). The lift binds the hammer, makes you Thor ({@link #ascend}). Once you are Thor you may pick the hammer
 * up freely; nobody else can budge it. The score is still the persisted flag, and the {@code /thor} test commands set it.
 * (v0.11.14-v0.17.6: lifting it with Hero of the Village was the way in.)
 */
public final class Worthiness {
	/** Score needed to lift/use Mjolnir. Matches THOR_DESIGN.md's suggested threshold. */
	public static final int THRESHOLD = 50;

	/** Score granted by the {@code /thor worthy} test command. */
	public static final int TEST_WORTHY_SCORE = 100;

	private Worthiness() {
	}

	public static int getScore(Player player) {
		return player.getAttachedOrElse(ModAttachments.WORTHINESS, 0);
	}

	public static void setScore(Player player, int score) {
		player.setAttached(ModAttachments.WORTHINESS, score);
	}

	public static boolean isWorthy(Player player) {
		return getScore(player) >= THRESHOLD;
	}

	/** True while the player has the Hero of the Village effect (v0.17.7: no longer opens Mjolnir; Shazam still reads it). */
	public static boolean hasAscensionEffect(Player player) {
		return player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
	}

	/** Whether {@code player} may physically pick a hammer up right now: creative, or already Thor (and not the Hulk). */
	public static boolean canLift(Player player) {
		// v0.13.12: the Hulk cannot lift Mjolnir, worthy or not
		return WorthinessEnforcer.bypassesWorthiness(player)
				|| (!com.projecthero.mod.hulk.Hulk.isHulk(player) && isWorthy(player));
	}

	/**
	 * v0.17.7: true if this player is an outsider who could become Thor by lifting a free hammer: not creative, not already
	 * Thor, not the Hulk, and with full Valor. (The storm and the Trial of Asgard are checked at the hammer by
	 * {@code thoracquire.MjolnirLift}.)
	 */
	public static boolean wouldAscend(Player player) {
		return !WorthinessEnforcer.bypassesWorthiness(player) && !isWorthy(player) && com.projecthero.mod.thoracquire.Valor.isFull(player)
				&& !com.projecthero.mod.hulk.Hulk.isHulk(player);
	}

	/** As {@link #wouldAscend(Player)}, for a specific hammer: one bound to another player never turns its lifter into Thor. */
	public static boolean wouldAscend(Player player, ItemStack hammer) {
		return wouldAscend(player) && !boundToSomeoneElse(player, hammer);
	}

	/** True if {@code hammer} is bound to a player other than {@code player}. */
	public static boolean boundToSomeoneElse(Player player, ItemStack hammer) {
		java.util.UUID owner = hammer.get(com.projecthero.mod.item.ModDataComponents.BOUND_OWNER);
		return owner != null && !owner.equals(player.getUUID());
	}

	/**
	 * The moment of the lift (v0.17.7: the end of {@code MjolnirLift}'s cutscene): replaces their Primary power with Thor and
	 * binds {@code hammer} to them (unless someone else already owns it). Hero of the Village is no longer consumed.
	 *
	 * @param hammer the stack about to enter the player's inventory -- mutated in place so it arrives bound
	 */
	public static void ascend(ServerPlayer player, ItemStack hammer) {
		com.projecthero.mod.hero.HeroTiers.claimPrimary(player, "thor");
		setScore(player, TEST_WORTHY_SCORE);
		com.projecthero.mod.power.ThorPowers.bindOnAscend(player, hammer);
		// The "You are worthy / Mjolnir accepts you" announcement comes from ThorFeedback#bound, which the
		// bind above triggers -- one place, two lines, no duplicate.
	}
}
