package com.projecthero.mod.hero.guide;

import java.util.Map;
import java.util.Set;

/**
 * v0.17.7 (user: "remove the power tiers from the mod please, in the guidebook at a rebalance section; for now I'd say black
 * panther and captain america is balanced"): which powers the user has signed off as balanced. The Guidebook's Rebalance chapter
 * ({@link HeroPackGuide#rebalanceChapterIndex()}) lists every power chapter under "Balanced" or "Not yet rebalanced" from this.
 */
public final class Rebalance {
	private Rebalance() {
	}

	/** Add a chapter key here when the user signs a power off as balanced. */
	public static final Set<String> BALANCED = Set.of(
			"projecthero.guide.black_panther",
			"projecthero.guide.super_soldier");

	/** A second name shown after a chapter's title in the Rebalance lists (lang keys), e.g. Super Soldier is Captain America. */
	public static final Map<String, String> ALSO_KNOWN_AS = Map.of(
			"projecthero.guide.super_soldier", "projecthero.guide.rebalance.aka.captain_america");

	/** Whether the power whose Guidebook chapter has this title key is balanced. */
	public static boolean isBalanced(String chapterKey) {
		return chapterKey != null && BALANCED.contains(chapterKey);
	}
}
