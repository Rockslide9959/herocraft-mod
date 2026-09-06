package com.projecthero.mod.worthiness;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.world.entity.player.Player;

/**
 * Hidden moral score gating Mjolnir. See THOR_DESIGN.md section 2. Only the score, threshold,
 * and a manual test command are wired up so far -- the full scoring table (villager kills, etc.)
 * comes later.
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
}
