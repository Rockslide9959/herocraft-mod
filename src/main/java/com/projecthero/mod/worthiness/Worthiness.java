package com.projecthero.mod.worthiness;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Whether a player holds the Power of Thor (a worthiness score at or above {@link #THRESHOLD}).
 *
 * <p>v0.11.14: worthiness is earned one way only -- by <b>lifting Mjolnir while you have the Hero of the
 * Village effect</b> ({@link #canLift}/{@link #ascend}). The lift binds the hammer to you on the spot, makes
 * you Thor (replacing whatever Primary power you had) and consumes the effect. Once you are Thor you may
 * pick the hammer up freely; anyone else -- including someone whose Hero of the Village effect has since run
 * out -- cannot budge it. The score itself is still the persisted flag, and the {@code /thor} test commands
 * still set it directly.
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

	/** True while the player has the Hero of the Village effect -- the only key that lets an outsider lift Mjolnir. */
	public static boolean hasAscensionEffect(Player player) {
		return player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
	}

	/** Whether {@code player} may physically lift a hammer right now: creative, already Thor, or Hero of the Village. */
	public static boolean canLift(Player player) {
		return WorthinessEnforcer.bypassesWorthiness(player) || isWorthy(player) || hasAscensionEffect(player);
	}

	/**
	 * True if lifting the hammer right now would be an ascension: an unworthy, non-creative player with
	 * the Hero of the Village effect. Callers check this, make room for the hammer, then call
	 * {@link #ascend}.
	 */
	public static boolean wouldAscend(Player player) {
		return !WorthinessEnforcer.bypassesWorthiness(player) && !isWorthy(player) && hasAscensionEffect(player);
	}

	/**
	 * v0.11.15: as {@link #wouldAscend(Player)}, but for a specific hammer. A hammer that is already bound to
	 * <em>another</em> player never turns its lifter into Thor and never rebinds -- a Hero of the Village may
	 * still lift it (see {@link #canLift}), but the power stays with its owner and nothing about the
	 * owner changes.
	 */
	public static boolean wouldAscend(Player player, ItemStack hammer) {
		return wouldAscend(player) && !boundToSomeoneElse(player, hammer);
	}

	/** True if {@code hammer} is bound to a player other than {@code player}. */
	public static boolean boundToSomeoneElse(Player player, ItemStack hammer) {
		java.util.UUID owner = hammer.get(com.projecthero.mod.item.ModDataComponents.BOUND_OWNER);
		return owner != null && !owner.equals(player.getUUID());
	}

	/**
	 * The moment a Hero of the Village lifts Mjolnir: replaces their Primary power with Thor, binds
	 * {@code hammer} to them (unless someone else already owns it), and consumes the effect.
	 *
	 * @param hammer the stack about to enter the player's inventory -- mutated in place so it arrives bound
	 */
	public static void ascend(ServerPlayer player, ItemStack hammer) {
		com.projecthero.mod.hero.HeroTiers.claimPrimary(player, "thor");
		setScore(player, TEST_WORTHY_SCORE);
		com.projecthero.mod.power.ThorPowers.bindOnAscend(player, hammer);
		player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
		// The "You are worthy / Mjolnir accepts you" announcement comes from ThorFeedback#bound, which the
		// bind above triggers -- one place, two lines, no duplicate.
	}
}
