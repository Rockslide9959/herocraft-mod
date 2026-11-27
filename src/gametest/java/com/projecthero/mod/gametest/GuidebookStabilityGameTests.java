package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.guide.HeroPackGuide;
import com.projecthero.mod.hero.guide.Rebalance;
import com.projecthero.mod.network.VersionCheck;
import com.projecthero.mod.omnitrix.OmnitrixFeature;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.5: Guidebook crash fixes and the build-id join check.
 *
 * <ul>
 * <li>Every chapter builds (no builder fell back to the "This page couldn't be shown" line), every category link and icon points
 * at something that exists, every chapter's title finds that chapter in search, odd searches don't throw.</li>
 * <li>The crash found: the search cache kept the Master Control page's old lines, so a page that got SHORTER (fewer found codes
 * after a world switch, a reset code) made the next search read past its end (IndexOutOfBoundsException in
 * {@code HeroPackGuide.search}).</li>
 * <li>{@link VersionCheck}: same version + different build id is refused, a dev build matches anything, the message names both
 * builds, the payload round-trips, and the GameTest server (mock players) is never checked.</li>
 * </ul>
 */
public class GuidebookStabilityGameTests implements FabricGameTest {

	private static boolean isPageError(Component line) {
		if (line.getContents() instanceof TranslatableContents t && t.getKey().equals("projecthero.guide.ui.page_error")) {
			return true;
		}
		for (Component sib : line.getSiblings()) {
			if (isPageError(sib)) {
				return true;
			}
		}
		return false;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyChapterBuildsWithoutThePlaceholder(GameTestHelper helper) {
		List<HeroPackGuide.Chapter> all = HeroPackGuide.chapters();
		helper.assertTrue(all.size() > 80, "the book has its chapters (" + all.size() + ")");
		for (int i = 0; i < all.size(); i++) {
			HeroPackGuide.Chapter ch = all.get(i);
			helper.assertTrue(ch != null, "chapter " + i + " exists");
			helper.assertTrue(!ch.title().getString().isBlank(), "chapter " + i + " has a title");
			helper.assertTrue(!ch.lines().isEmpty(), "chapter " + i + " has lines");
			for (Component line : ch.lines()) {
				helper.assertTrue(line != null, "chapter " + i + " has no null line");
				line.getString(); // resolves every translation and its arguments
				helper.assertTrue(!isPageError(line), "chapter " + i + " (" + ch.title().getString() + ") failed to build -- see the log");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyLinkAndIconTargetExists(GameTestHelper helper) {
		int n = HeroPackGuide.chapters().size();
		boolean[] linked = new boolean[n];
		for (HeroPackGuide.Category c : HeroPackGuide.Category.values()) {
			for (HeroPackGuide.IndexEntry e : HeroPackGuide.allCategoryEntries(c)) {
				if (e.isHeading()) {
					continue;
				}
				helper.assertTrue(e.chapterIndex() < n, c + " links chapter " + e.chapterIndex() + " of " + n);
				linked[e.chapterIndex()] = true;
			}
			for (HeroPackGuide.IndexEntry e : HeroPackGuide.categoryEntries(c)) {
				helper.assertTrue(e.isHeading() || HeroPackGuide.validChapter(e.chapterIndex()), c + " shows only real chapters");
			}
			for (String icon : c.icons) {
				helper.assertTrue(ResourceLocation.tryParse(icon) != null, c + " icon id parses: " + icon);
			}
		}
		for (int i = 0; i < n; i++) {
			String icon = HeroPackGuide.iconOf(i);
			if (icon != null) {
				ResourceLocation rl = ResourceLocation.tryParse(icon);
				helper.assertTrue(rl != null, "chapter " + i + " icon id parses: " + icon);
				// an id that isn't an item (kingpin_cane_icon, ultimate_nullifier_icon are item MODELS) falls back to the category's
				// icon on the client -- a lookup must just never throw
				BuiltInRegistries.ITEM.getOptional(rl);
			}
			// prev / next across a category never leave the list
			for (int x : HeroPackGuide.chaptersIn(HeroPackGuide.categoryOf(i))) {
				helper.assertTrue(HeroPackGuide.validChapter(x), "chapter " + i + "'s category lists a real chapter");
			}
		}
		// out-of-range reads give a placeholder page, never an exception
		helper.assertTrue(isPageError(HeroPackGuide.chapterAt(-1).lines().get(0)), "chapter -1 is a placeholder");
		helper.assertTrue(isPageError(HeroPackGuide.chapterAt(n + 5).lines().get(0)), "chapter n+5 is a placeholder");
		helper.assertTrue(HeroPackGuide.categoryOf(n + 5) != null && HeroPackGuide.categoryOf(-3) != null, "categoryOf never null");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void searchFindsEveryChapterTitleAndSurvivesOddInput(GameTestHelper helper) {
		List<HeroPackGuide.Chapter> all = HeroPackGuide.chapters();
		for (int i = 0; i < all.size(); i++) {
			if (HeroPackGuide.hidden(i)) {
				continue;
			}
			String title = all.get(i).title().getString();
			boolean found = false;
			for (HeroPackGuide.SearchHit h : HeroPackGuide.search(title)) {
				helper.assertTrue(HeroPackGuide.validChapter(h.chapter()), "a hit points at a real chapter");
				if (h.chapter() == i) {
					found = true;
					break;
				}
			}
			// a power chapter can share its title with a book chapter (only one of them need come back first) -- but most must
			if (!found) {
				boolean twin = false;
				for (int j = 0; j < all.size(); j++) {
					twin |= j != i && all.get(j).title().getString().equals(title);
				}
				helper.assertTrue(twin || all.get(i) == HeroPackGuide.omnitrixChapter() && !OmnitrixFeature.enabled(), "searching \"" + title + "\" finds chapter " + i);
			}
		}
		String[] odd = { "", " ", "a", "e", "zzqqxx-no-such-text", "[(*+?\\", "%s %d %1$s", "İ", "ß", "😀",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "\n", "\t" };
		for (String q : odd) {
			List<HeroPackGuide.SearchHit> hits = HeroPackGuide.search(q);
			helper.assertTrue(hits.size() <= HeroPackGuide.MAX_HITS, "at most MAX_HITS for \"" + q + "\"");
			for (HeroPackGuide.SearchHit h : hits) {
				helper.assertTrue(HeroPackGuide.validChapter(h.chapter()), "hit chapter in range for \"" + q + "\"");
				helper.assertTrue(h.line() < HeroPackGuide.chapterAt(h.chapter()).lines().size(), "hit line in range for \"" + q + "\"");
			}
		}
		helper.succeed();
	}

	/** The crash: search read the cached (longer) Master Control text against the rebuilt (shorter) page. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void searchSurvivesTheMasterControlPageShrinking(GameTestHelper helper) {
		var revealed = HeroPackGuide.masterControlRevealed;
		var live = HeroPackGuide.masterControlLive;
		boolean wasOn = OmnitrixFeature.enabled();
		OmnitrixFeature.forceForTests(true);
		try {
			List<Component> many = new ArrayList<>();
			for (int i = 0; i < 12; i++) {
				many.add(Component.literal("found code line " + i));
			}
			HeroPackGuide.masterControlRevealed = () -> true;
			HeroPackGuide.masterControlLive = () -> many;
			HeroPackGuide.refreshMasterControl();
			helper.assertTrue(!HeroPackGuide.search("found code line").isEmpty(), "the live lines are searchable");
			HeroPackGuide.masterControlLive = List::of; // fewer lines now (a world switch, a reset code)
			HeroPackGuide.refreshMasterControl();
			try {
				HeroPackGuide.search("e"); // reads every line of every chapter
				helper.assertTrue(HeroPackGuide.search("found code line").isEmpty(), "the old lines are gone from search");
			} catch (RuntimeException e) {
				helper.fail("search after the page shrank threw " + e);
			}
			// and a throwing reveal check keeps the page hidden instead of crashing
			HeroPackGuide.masterControlRevealed = () -> {
				throw new IllegalStateException("test: no player");
			};
			HeroPackGuide.search("master");
			HeroPackGuide.categoryEntries(HeroPackGuide.Category.OTHER);
		} finally {
			HeroPackGuide.masterControlRevealed = revealed;
			HeroPackGuide.masterControlLive = live;
			HeroPackGuide.refreshMasterControl();
			OmnitrixFeature.forceForTests(wasOn);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the build check

	@GameTest(template = EMPTY_STRUCTURE)
	public void buildCheckRefusesADifferentBuildOfTheSameVersion(GameTestHelper helper) {
		helper.assertTrue(VersionCheck.sameBuild("0.17.5", "abc-1", "0.17.5", "abc-1"), "the same build plays");
		helper.assertTrue(!VersionCheck.sameBuild("0.17.5", "abc-1", "0.17.5", "abc-2"), "a different build of 0.17.5 is refused");
		helper.assertTrue(!VersionCheck.sameBuild("0.17.5", "abc-1", "0.17.4", "abc-1"), "a different version is refused");
		helper.assertTrue(VersionCheck.sameBuild("0.17.5", VersionCheck.DEV_BUILD, "0.17.5", "abc-2"), "a dev server takes any build");
		helper.assertTrue(VersionCheck.sameBuild("0.17.5", "abc-1", "0.17.5", VersionCheck.DEV_BUILD), "a dev client joins any build");
		helper.assertTrue(!VersionCheck.sameBuild("0.17.5", VersionCheck.DEV_BUILD, "0.17.4", "x"), "dev never skips the version");

		String msg = VersionCheck.buildMismatchMessage(VersionCheck.describe("host1", "2026-11-19 10:00 UTC"),
				VersionCheck.describe("mine2", "")).getString();
		helper.assertTrue(msg.startsWith("Your Project Hero jar is a different build from the host's"), "the message leads with the cause");
		helper.assertTrue(msg.contains("yours: mine2") && msg.contains("host: host1, built 2026-11-19 10:00 UTC"), "both builds named: " + msg);
		helper.assertTrue(msg.endsWith("Get the latest jar and try again."), "the message says what to do");

		helper.assertTrue(!VersionCheck.buildId().isBlank(), "this jar has a build id");
		VersionCheck.BuildPayload local = VersionCheck.BuildPayload.local();
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		VersionCheck.BuildPayload.CODEC.encode(buf, local);
		helper.assertTrue(VersionCheck.BuildPayload.CODEC.decode(buf).equals(local), "the build payload round-trips");
		buf.release();

		// mock players / the GameTest server are never checked (they have no client to answer)
		helper.assertTrue(!VersionCheck.checksBuild(helper.getLevel().getServer(), helper.makeMockServerPlayerInLevel().getGameProfile()),
				"the GameTest server skips the build check");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.7: the Rebalance chapter (power tiers removed)

	/** "balanced" / "pending" for the Rebalance chapter's two group headings, else null. */
	private static String groupNamed(Component line) {
		if (line.getContents() instanceof TranslatableContents t) {
			if (t.getKey().equals("projecthero.guide.rebalance.balanced")) {
				return "balanced";
			}
			if (t.getKey().equals("projecthero.guide.rebalance.pending")) {
				return "pending";
			}
		}
		for (Component sib : line.getSiblings()) {
			String s = groupNamed(sib);
			if (s != null) {
				return s;
			}
		}
		return null;
	}

	/**
	 * v0.17.7 (user: "remove the power tiers from the mod please, in the guidebook at a rebalance section; for now I'd say black
	 * panther and captain america is balanced"): the Rebalance chapter is the second home tile and its own category, every power
	 * chapter is linked in it exactly once, Black Panther and Super Soldier sit under Balanced and every other power under Not yet
	 * rebalanced, and nothing tier-shaped is left in the book.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyPowerIsListedOnceInTheRebalanceChapter(GameTestHelper helper) {
		List<HeroPackGuide.Chapter> all = HeroPackGuide.chapters();
		int rb = HeroPackGuide.rebalanceChapterIndex();
		helper.assertTrue(HeroPackGuide.validChapter(rb), "the Rebalance chapter exists (" + rb + " of " + all.size() + ")");
		helper.assertTrue(HeroPackGuide.titleKey(rb).equals("projecthero.guide.rebalance"), "chapter " + rb + " is Rebalance");
		helper.assertTrue(HeroPackGuide.categoryOf(rb) == HeroPackGuide.Category.REBALANCE, "Rebalance is filed under its own category");
		helper.assertTrue(HeroPackGuide.HOME_SHELVES[0].length >= 2 && HeroPackGuide.HOME_SHELVES[0][1] == HeroPackGuide.Category.REBALANCE,
				"Rebalance is the second tile on the home page");
		for (HeroPackGuide.Category[] shelf : HeroPackGuide.HOME_SHELVES) {
			for (HeroPackGuide.Category c : shelf) {
				helper.assertTrue(c != null, "no null (old Powers by Tier) tile on the home page");
			}
		}

		// where each link sits
		java.util.Map<Integer, String> listedUnder = new java.util.HashMap<>();
		String current = null;
		for (Component line : all.get(rb).lines()) {
			int target = HeroPackGuide.linkTarget(line);
			if (target < 0) {
				String g = groupNamed(line);
				if (g != null) {
					current = g;
				}
				continue;
			}
			helper.assertTrue(HeroPackGuide.validChapter(target), "a Rebalance link points at a real chapter: " + line.getString());
			helper.assertTrue(HeroPackGuide.isPowerChapter(target), "a Rebalance link points at a power chapter: " + line.getString());
			helper.assertTrue(listedUnder.put(target, current) == null, "each power is listed once: " + line.getString());
		}
		int powers = 0;
		for (int i = 0; i < all.size(); i++) {
			if (!HeroPackGuide.isPowerChapter(i)) {
				continue;
			}
			powers++;
			String want = Rebalance.isBalanced(HeroPackGuide.titleKey(i)) ? "balanced" : "pending";
			helper.assertTrue(want.equals(listedUnder.get(i)), all.get(i).title().getString() + " is listed under " + want + ", got "
					+ listedUnder.get(i));
		}
		helper.assertTrue(powers > 20 && listedUnder.size() == powers, "every power chapter is listed (" + listedUnder.size() + " of " + powers + ")");

		int panther = all.indexOf(HeroPackGuide.blackPantherChapter());
		int cap = all.indexOf(HeroPackGuide.superSoldierChapter());
		helper.assertTrue(panther >= 0 && "balanced".equals(listedUnder.get(panther)), "Black Panther is Balanced");
		helper.assertTrue(cap >= 0 && "balanced".equals(listedUnder.get(cap)), "Super Soldier (Captain America) is Balanced");
		helper.assertTrue(HeroPackGuide.isBalanced(panther) && HeroPackGuide.isBalanced(cap), "isBalanced agrees");
		int thor = all.indexOf(HeroPackGuide.thorChapter());
		helper.assertTrue("pending".equals(listedUnder.get(thor)), "Thor is not yet rebalanced");
		int[] bc = HeroPackGuide.balancedCount();
		helper.assertTrue(bc[0] == Rebalance.BALANCED.size() && bc[1] == powers, "balancedCount " + bc[0] + " / " + bc[1]);
		boolean aka = false;
		for (Component line : all.get(rb).lines()) {
			aka |= HeroPackGuide.linkTarget(line) == cap && line.getString().contains("Captain America");
		}
		helper.assertTrue(aka, "the Super Soldier line mentions Captain America");

		// nothing tier-shaped is left: no chapter line names a projecthero.rank.* key
		for (HeroPackGuide.Chapter ch : all) {
			for (Component line : ch.lines()) {
				helper.assertFalse(mentionsRank(line), ch.title().getString() + " still shows a tier: " + line.getString());
			}
		}
		helper.assertFalse(HeroPackGuide.search("Not yet rebalanced").isEmpty(), "the Rebalance lists are searchable");
		helper.succeed();
	}

	private static boolean mentionsRank(Component line) {
		if (line.getContents() instanceof TranslatableContents t && (t.getKey().startsWith("projecthero.rank.")
				|| t.getKey().startsWith("projecthero.guide.tiers.") || t.getKey().startsWith("projecthero.guide.power_tiers"))) {
			return true;
		}
		for (Component sib : line.getSiblings()) {
			if (mentionsRank(sib)) {
				return true;
			}
		}
		return false;
	}
}
