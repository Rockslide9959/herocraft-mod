package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.hero.guide.HeroPackGuide;
import com.projecthero.mod.omnitrix.OmnitrixInfo;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * v0.17.1 (user: "pressing I while transformed should also show information about the aliens and their abilities"): the
 * Omnitrix's info builder ({@link OmnitrixInfo}) -- shared by the I screen and the Guidebook -- covers every alien's every
 * declared move with a name and a description, and the lang for each alien's role, passives and weaknesses exists.
 */
public class OmnitrixInfoGameTests implements FabricGameTest {
	/** Every translation key in {@code c}, its arguments and its siblings. */
	private static void keys(Component c, Set<String> out) {
		if (c.getContents() instanceof TranslatableContents t) {
			out.add(t.getKey());
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component a) {
					keys(a, out);
				}
			}
		}
		for (Component s : c.getSiblings()) {
			keys(s, out);
		}
	}

	private static Set<String> keys(List<Component> lines) {
		Set<String> out = new HashSet<>();
		for (Component c : lines) {
			keys(c, out);
		}
		return out;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyAlienMoveHasLangAndIsOnTheInfoPagesAndInTheGuide(GameTestHelper helper) {
		Language lang = Language.getInstance();
		Set<String> guide = keys(HeroPackGuide.omnitrixChapter().lines());
		List<Component> rosterLines = new ArrayList<>();
		OmnitrixInfo.roster(rosterLines, OmnitrixInfo.LETTERS, null);
		Set<String> roster = keys(rosterLines);
		List<String> problems = new ArrayList<>();
		if (Aliens.playable().size() != 10) {
			problems.add("expected all 10 aliens playable, got " + Aliens.playable().size());
		}
		for (Aliens.Alien a : Aliens.all()) {
			AlienKit kit = Aliens.kit(a.id());
			if (kit == null) {
				continue;
			}
			List<Component> page = new ArrayList<>();
			OmnitrixInfo.alienPage(page, a, OmnitrixInfo.LETTERS, null);
			Set<String> pageKeys = keys(page);
			for (String k : new String[] { OmnitrixInfo.roleKey(a.id()), "projecthero.omnitrix.info." + a.id() + ".passive.1",
					"projecthero.omnitrix.info." + a.id() + ".weakness.1", "projecthero.guide.omnitrix.alien." + a.id() }) {
				if (!lang.has(k)) {
					problems.add("no lang " + k);
				}
			}
			if (!roster.contains(a.nameKey()) || !roster.contains(OmnitrixInfo.roleKey(a.id()))) {
				problems.add(a.id() + " missing from the roster");
			}
			for (AlienMove m : kit.moves()) {
				String name = OmnitrixInfo.nameKey(a.id(), m);
				String desc = OmnitrixInfo.descKey(a.id(), m);
				if (!lang.has(name)) {
					problems.add("no lang " + name);
				}
				if (!lang.has(desc)) {
					problems.add("no lang " + desc);
				}
				if (!pageKeys.contains(name) || !pageKeys.contains(desc)) {
					problems.add(a.id() + "." + m.id() + " not on its info page");
				}
				if (!guide.contains(name) || !guide.contains(desc)) {
					problems.add(a.id() + "." + m.id() + " not in the guide chapter");
				}
				if (m.onSlot() && (m.slot() == com.projecthero.mod.hero.AbilitySlot.SLOT_7 || m.slot() == com.projecthero.mod.hero.AbilitySlot.SLOT_8)) {
					problems.add(a.id() + "." + m.id() + " is on H / N");
				}
			}
			// v0.17.1 (user: "add abilities to the V key for every alien")
			if (kit.moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_5, false, false) == null) {
				problems.add(a.id() + " has no V move");
			}
		}
		if (!problems.isEmpty()) {
			helper.fail(String.join("; ", problems.subList(0, Math.min(12, problems.size()))));
		}
		helper.succeed();
	}
}
