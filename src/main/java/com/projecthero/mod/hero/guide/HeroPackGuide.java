package com.projecthero.mod.hero.guide;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerCategory;
import com.projecthero.mod.hero.Powers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The data-driven content of the {@code HeroPack Guide}. Chapters are built once from {@link Powers}
 * and the shared translation keys, so the in-game guide and {@code docs/HEROPACK_CONTENT_REFERENCE.md}
 * are drawn from the same source and cannot drift far apart. Pure data -- the rendering screen lives
 * in the client source set.
 *
 * <p>{@link #chapters()} is the flat list of pages (stable order). {@link #index()} is a separate
 * table-of-contents view over that list: section headings, category sub-headings and indented,
 * clickable chapter links, so the navigation reads like a document outline rather than one long list.
 */
public final class HeroPackGuide {
	public record Chapter(Component title, List<Component> lines) {
	}

	/**
	 * One row of the navigation outline.
	 *
	 * @param label        display text (headings carry their own bold/colour style)
	 * @param depth        indentation level: 0 = section, 1 = category / top-level link, 2 = power link
	 * @param chapterIndex index into {@link #chapters()} this row opens, or {@code -1} for a
	 *                     non-clickable heading / spacer
	 */
	public record IndexEntry(Component label, int depth, int chapterIndex) {
		public boolean isHeading() {
			return chapterIndex < 0;
		}
	}

	private static List<Chapter> chapters;
	private static List<IndexEntry> index;

	private HeroPackGuide() {
	}

	public static List<Chapter> chapters() {
		if (chapters == null) {
			chapters = build();
		}
		return chapters;
	}

	public static List<IndexEntry> index() {
		if (index == null) {
			buildCategories();
			index = buildIndex();
		}
		return index;
	}

	// ---- Hero-Tier chapter accessors ----
	// The "Your Power" info screen (I key) shows these for a player who holds the matching Hero-Tier
	// power, exactly as they read in the book, so the two never drift.

	public static Chapter thorChapter() {
		return chapters().get(CH_THOR);
	}

	public static Chapter ironManChapter() {
		return chapters().get(CH_IRON_MAN);
	}

	public static Chapter spiderManChapter() {
		return chapters().get(CH_SPIDER_MAN);
	}

	public static Chapter maxSteelChapter() {
		return chapters().get(CH_MAX_STEEL);
	}

	public static Chapter punisherChapter() {
		return chapters().get(CH_PUNISHER);
	}

	public static Chapter greenLanternChapter() {
		return chapters().get(CH_GREEN_LANTERN);
	}

	public static Chapter wolverineChapter() {
		return chapters().get(CH_WOLVERINE);
	}

	public static Chapter titanShifterChapter() {
		return chapters().get(CH_TITAN_SHIFTER);
	}

	public static Chapter allMightChapter() {
		return chapters().get(CH_ALL_MIGHT);
	}

	/** Moon Knight (v0.13.20). */
	public static Chapter moonKnightChapter() {
		return chapters().get(CH_MOON_KNIGHT);
	}

	/** v0.14.8: the Super Soldier chapter (the P power-info screen). */
	public static Chapter superSoldierChapter() {
		return chapters().get(CH_SUPER_SOLDIER);
	}

	public static Chapter hulkChapter() {
		return chapters().get(CH_HULK);
	}

	/** The Kryptonian (v0.14.8). */
	public static Chapter kryptonianChapter() {
		return chapters().get(CH_KRYPTONIAN);
	}

	/** Nova (v0.15.13). */
	public static Chapter novaChapter() {
		return chapters().get(CH_NOVA);
	}

	/** Blue Beetle (v0.16.5). */
	public static Chapter blueBeetleChapter() {
		return chapters().get(CH_BLUE_BEETLE);
	}

	/** Cyborg (v0.16.5). */
	public static Chapter cyborgChapter() {
		return chapters().get(CH_CYBORG);
	}

	/** The Falcon (v0.17.1). */
	public static Chapter falconChapter() {
		return chapters().get(CH_FALCON);
	}

	/** v0.17.1: the Falcon -- the gear, Thrust, the wingsuit flight and the twelve moves. */
	private static Chapter falcon() {
		return chapter("projecthero.guide.falcon", lines -> {
			lines.add(Component.translatable("projecthero.guide.falcon.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.falcon.body");
			blank(lines);
			for (String section : new String[]{"gear", "thrust", "flight", "passives"}) {
				head(lines, "projecthero.guide.falcon." + section);
				para(lines, "projecthero.guide.falcon." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.falcon.controls");
			for (String[] row : new String[][] { { "Space x2", "flight" }, { "Hold X", "jet_burn" }, { "R", "wrist_guns" },
					{ "Shift+R", "micro_missiles" }, { "G", "wing_slash" }, { "Shift+G", "wing_shield" }, { "Tap X", "afterburner" },
					{ "Shift+X", "barrel_roll" }, { "Z", "redwing_strike" }, { "Shift+Z", "redwing_bombing_run" }, { "V", "tactical_goggles" },
					{ "Shift+V", "lift_and_carry" }, { "C", "dive_bomb" }, { "Shift+C", "talon_drop" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.falcon.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.falcon.ability." + row[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.falcon.commands");
			para(lines, "projecthero.guide.falcon.commands.body");
		});
	}

	/** v0.17.1: the Silver Surfer. */
	public static Chapter silverSurferChapter() {
		return chapters().get(CH_SILVER_SURFER);
	}

	/** v0.17.1: the Speed Force Speedster. */
	public static Chapter speedForceChapter() {
		return chapters().get(CH_SPEED_FORCE);
	}

	/** v0.16.6: Ladybug / Cat Noir / Rena Rouge / Hawk Moth / Carapace / Pegasus, by the Kwami. */
	public static Chapter miraculousChapter(com.projecthero.mod.miraculous.Kwami kwami) {
		return chapters().get(switch (kwami) {
			case TIKKI -> CH_LADYBUG;
			case PLAGG -> CH_CAT_NOIR;
			case TRIXX -> CH_RENA_ROUGE;
			case NOOROO -> CH_HAWK_MOTH;
			case WAYZZ -> CH_CARAPACE;
			case KAALKI -> CH_PEGASUS;
			case SASS -> CH_VIPERION;
			case MULLO -> CH_MULTIMOUSE;
		});
	}

	/** The key labels of the six slots, [plain, shift] (the Miraculous controls tables). */
	private static final String[][] SLOT_LABELS = { { "R", "Shift+R" }, { "G", "Shift+G" }, { "X", "Shift+X" }, { "Z", "Shift+Z" },
			{ "V", "Shift+V" }, { "C", "Shift+C" } };

	/**
	 * v0.16.6: one Miraculous hero's chapter. The origin sections are shared text where the three are the same (the box,
	 * the countdown, the passives) and the hero's own otherwise; the controls table is built from the kit
	 * ({@code KwamiKit.idsOf} SLOT_1..SLOT_6, names and descriptions from {@code projecthero.miraculous.ability.<id>}).
	 */
	private static Chapter miraculous(com.projecthero.mod.miraculous.Kwami kwami) {
		String hero = kwami.heroKey;
		return chapter("projecthero.guide." + hero, lines -> {
			lines.add(Component.translatable("projecthero.guide." + hero + ".tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide." + hero + ".body");
			blank(lines);
			// v0.16.7: Viperion adds his special's own section (Second Chance) after the passives
			String[] sections = kwami == com.projecthero.mod.miraculous.Kwami.SASS
					? new String[]{"box", "jewel", "kwami", "transform", "countdown", "food", "passives", "second_chance"}
					: kwami == com.projecthero.mod.miraculous.Kwami.MULLO // v0.17.1: Multimouse adds her own passives + Multitude
							? new String[]{"box", "jewel", "kwami", "transform", "countdown", "food", "passives", "mouse", "multitude"}
					: new String[]{"box", "jewel", "kwami", "transform", "countdown", "food", "passives"};
			// v0.16.7: Rena Rouge adds the Mirage wheel and one section per illusion
			if (kwami == com.projecthero.mod.miraculous.Kwami.TRIXX) {
				sections = new String[]{"box", "jewel", "kwami", "transform", "countdown", "food", "passives", "mirage", "mirage_foxes",
						"mirage_monster", "mirage_vanish", "mirage_zone", "mirage_lure", "mirage_pitfall", "mirage_eclipse", "mirage_mirrors"};
			}
			for (String section : sections) {
				boolean shared = section.equals("box") || section.equals("countdown") || section.equals("passives");
				String key = "projecthero.guide." + (shared ? "miraculous" : hero) + "." + section;
				head(lines, key);
				para(lines, key + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.miraculous.controls");
			com.projecthero.mod.miraculous.KwamiKit kit = com.projecthero.mod.miraculous.MiraculousKits.of(kwami);
			com.projecthero.mod.hero.AbilitySlot[] slots = { com.projecthero.mod.hero.AbilitySlot.SLOT_1, com.projecthero.mod.hero.AbilitySlot.SLOT_2,
					com.projecthero.mod.hero.AbilitySlot.SLOT_3, com.projecthero.mod.hero.AbilitySlot.SLOT_4,
					com.projecthero.mod.hero.AbilitySlot.SLOT_5, com.projecthero.mod.hero.AbilitySlot.SLOT_6 };
			for (int i = 0; i < slots.length; i++) {
				String[] ids = kit.idsOf(slots[i]);
				for (int j = 0; j < Math.min(2, ids.length); j++) {
					lines.add(Component.literal(" " + SLOT_LABELS[i][j] + "  ").withStyle(ChatFormatting.GOLD)
							.append(Component.translatable("projecthero.miraculous.ability." + ids[j]).withStyle(ChatFormatting.WHITE)));
					para(lines, "projecthero.miraculous.ability." + ids[j] + ".desc");
				}
			}
			blank(lines);
			head(lines, "projecthero.guide.miraculous.commands");
			para(lines, "projecthero.guide.miraculous.commands.body");
		});
	}

	/** Ghost Rider (v0.16.5). */
	public static Chapter ghostRiderChapter() {
		return chapters().get(CH_GHOST_RIDER);
	}

	public static Chapter symbioteChapter() {
		return chapters().get(CH_SYMBIOTE);
	}

	/**
	 * The "Your Power" (I) screen's entry for a plain Normal Symbiote Host -- bonded, but not also Spider-Man. Its own
	 * live-built chapter rather than the cached {@link #symbioteChapter()} (the shared book page, which covers every host
	 * variant): a Normal Host's menu shows only their own kit.
	 *
	 * <p>v0.15.15 restructure (user request: "hard to see all the information"): laid out like the other powers' pages --
	 * short sections (how to get it, the suit, Biomass, moves, sneak combos, always-on, weaknesses, tips), one row per key
	 * with its cooldown beside the name and a one- or two-line description under it.
	 */
	public static Chapter symbioteNormalHostChapter() {
		return chapter("projecthero.guide.symbiote", lines -> {
			lines.add(Component.translatable("projecthero.guide.symbiote.tier").withStyle(ChatFormatting.LIGHT_PURPLE));
			para(lines, "projecthero.guide.symbiote.overview");
			blank(lines);
			symbioteHowToGet(lines);
			symbioteSuit(lines);
			symbioteBiomass(lines);
			symbioteMoves(lines);
			symbiotePassives(lines, true);
			symbioteWeaknesses(lines);
			symbioteTips(lines);
		});
	}

	/**
	 * The "Your Power" info screen's entry for a player who is BOTH bonded with a Symbiote AND currently holds Spider-Man
	 * -- Black Suit Spider-Man. Built fresh each call (the physical book cannot show a per-player combination). Shows
	 * Spider-Man's REAL keybinds plus the three sneak-modified Symbiote extras layered on top of them. v0.15.15: same
	 * sectioned layout as the Normal host's page.
	 */
	public static Chapter symbioteSpiderManChapter() {
		return chapter("projecthero.guide.symbiote_spider_man", lines -> {
			lines.add(Component.translatable("projecthero.guide.symbiote_spider_man.tier").withStyle(ChatFormatting.LIGHT_PURPLE));
			para(lines, "projecthero.guide.symbiote_spider_man.body");
			blank(lines);
			head(lines, "projecthero.guide.symbiote_spider_man.suit");
			para(lines, "projecthero.guide.symbiote_spider_man.suit.body");
			blank(lines);
			spiderControls(lines); // v0.16.1
			blank(lines);
			symbioteBlackSuitExtras(lines);
			head(lines, "projecthero.guide.symbiote_spider_man.passives");
			for (String p : new String[]{"armour", "web_capacity"}) {
				bullet(lines, "projecthero.guide.symbiote_spider_man.passive." + p);
			}
			for (String p : new String[]{"protect", "resurrect", "predator", "squad", "pet", "vial"}) {
				bullet(lines, "projecthero.guide.symbiote.s.passive." + p);
			}
			blank(lines);
			symbioteWeaknesses(lines);
			symbioteTips(lines);
		});
	}

	// ---- v0.15.15: the Symbiote pages' shared sections ----

	/** One key row: {@code key} in gold, the move's name in white, its cooldown (or "toggle") in dark grey, then its line. */
	private static void symbioteRow(List<Component> lines, String key, String move, String cooldown) {
		net.minecraft.network.chat.MutableComponent row = Component.literal(" " + key + "  ").withStyle(ChatFormatting.GOLD)
				.append(Component.translatable("projecthero.symbiote.ability." + move).withStyle(ChatFormatting.WHITE));
		if (cooldown != null) {
			row.append(Component.literal("  "))
					.append((cooldown.equals("toggle") ? Component.translatable("projecthero.guide.symbiote.s.toggle")
							: Component.translatable("projecthero.guide.symbiote.s.cd", cooldown)).withStyle(ChatFormatting.DARK_GRAY));
		}
		lines.add(row);
		para(lines, "projecthero.guide.symbiote.s.move." + move);
	}

	private static void bullet(List<Component> lines, String key) {
		lines.add(Component.literal(" • ").append(Component.translatable(key)).withStyle(ChatFormatting.GRAY));
	}

	private static void symbioteHowToGet(List<Component> lines) {
		head(lines, "projecthero.guide.symbiote.progression");
		for (String step : new String[]{"find", "bond", "settle"}) {
			para(lines, "projecthero.guide.symbiote.s.get." + step);
		}
		lines.add(Component.translatable("projecthero.guide.symbiote.s.get.purge").withStyle(ChatFormatting.DARK_GRAY));
		blank(lines);
	}

	private static void symbioteSuit(List<Component> lines) {
		head(lines, "projecthero.guide.symbiote.s.suit");
		para(lines, "projecthero.guide.symbiote.s.suit.body");
		for (String b : new String[]{"armour", "resistance", "stats", "bonded"}) {
			bullet(lines, "projecthero.guide.symbiote.s.suit." + b);
		}
		blank(lines);
	}

	private static void symbioteBiomass(List<Component> lines) {
		head(lines, "projecthero.guide.symbiote.s.biomass");
		para(lines, "projecthero.guide.symbiote.s.biomass.body");
		for (String b : new String[]{"regen", "costs", "empty", "death"}) {
			bullet(lines, "projecthero.guide.symbiote.s.biomass." + b);
		}
		blank(lines);
	}

	private static void symbioteMoves(List<Component> lines) {
		head(lines, "projecthero.guide.symbiote.s.moves");
		symbioteRow(lines, "R", "tendril_strike", "1.5");
		symbioteRow(lines, "G", "spike_shot", "4");
		symbioteRow(lines, "X", "leap", "2");
		symbioteRow(lines, "Z", "barrage", "15");
		symbioteRow(lines, "V", "blade", "toggle");
		symbioteRow(lines, "C", "spikes", "toggle");
		blank(lines);
		head(lines, "projecthero.guide.symbiote.s.combos");
		symbioteRow(lines, "Sneak+R", "tendril_sweep", "10");
		symbioteRow(lines, "Sneak+G", "spike_fan", "10");
		symbioteRow(lines, "Sneak+X", "grapple", "3");
		symbioteRow(lines, "Sneak+hold Z", "onslaught", "90");
		symbioteRow(lines, "Sneak+V", "shield", "toggle");
		symbioteRow(lines, "Sneak+C", "tendril_grab", "5");
		blank(lines);
	}

	/**
	 * v0.16.1: Spider-Man's controls -- six keys, each with a tap / Shift / hold move (and what each does), then the key-free
	 * traversal and the web structures. Shared by the Spider-Man and Black Suit Spider-Man chapters.
	 */
	private static void spiderControls(List<Component> lines) {
		head(lines, "projecthero.guide.spider_man.controls");
		para(lines, "projecthero.guide.spider_man.controls.body");
		for (int i = 0; i < 6; i++) {
			char key = com.projecthero.mod.hero.AbilitySlot.byNumber(i + 1).defaultKey();
			String[] ids = {com.projecthero.mod.spider.SpiderMoves.TAP[i], com.projecthero.mod.spider.SpiderMoves.SHIFT[i],
					com.projecthero.mod.spider.SpiderMoves.HOLD[i]};
			String[] how = {"projecthero.guide.spider_man.how.tap", "projecthero.guide.spider_man.how.shift",
					"projecthero.guide.spider_man.how.hold"};
			for (int k = 0; k < 3; k++) {
				lines.add(Component.literal(" " + key + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable(how[k]).withStyle(ChatFormatting.DARK_GRAY))
						.append(Component.literal(" "))
						.append(Component.translatable("projecthero.spider_man.ability." + ids[k]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.spider_man.ability." + ids[k] + ".desc");
			}
		}
		blank(lines);
		head(lines, "projecthero.guide.spider_man.traversal");
		for (String t : new String[]{"swing", "tricks", "dive", "zip_boost", "crawl", "wall_run", "perch", "stealth", "rope", "hang", "takedown", "perfect_dodge", "sounds", "cut"}) {
			// v0.16.2 (user): it was all grey and hard to read -- a gold title per entry, white text, a gap between
			lines.add(Component.literal("▸ ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.guide.spider_man.traversal." + t + ".title")
							.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
			lines.add(Component.translatable("projecthero.guide.spider_man.traversal." + t).withStyle(ChatFormatting.WHITE));
			blank(lines);
		}
		blank(lines);
		head(lines, "projecthero.guide.spider_man.webs");
		para(lines, "projecthero.guide.spider_man.webs.body");
		para(lines, "projecthero.guide.spider_man.webs.struggle"); // v0.16.4: players struggle out of a cocoon
		blank(lines);
	}

	/** v0.16.2: the Spider-Man Suit v2 -- what it is, the keep-your-old-suit recipe, Web Wings and the Trip Mine. */
	private static void spiderSuitV2(List<Component> lines) {
		head(lines, "projecthero.guide.spider_man.suit_v2");
		para(lines, "projecthero.guide.spider_man.suit_v2.body");
		for (String t : new String[]{"recipe", "wings", "trip_mine"}) {
			bullet(lines, "projecthero.guide.spider_man.suit_v2." + t);
		}
		blank(lines);
		// v0.16.5 (user): the six film / comic suits and where to show them off
		head(lines, "projecthero.guide.spider_man.suits");
		para(lines, "projecthero.guide.spider_man.suits.body");
		for (String t : new String[]{"recipe", "display"}) {
			bullet(lines, "projecthero.guide.spider_man.suits." + t);
		}
		blank(lines);
	}

	private static void symbioteBlackSuitExtras(List<Component> lines) {
		// v0.16.2: the Hunger meter, then the six symbiote moves that replace Spider-Man's while suited
		head(lines, "projecthero.guide.symbiote.hunger");
		para(lines, "projecthero.guide.symbiote.hunger.body");
		for (String t : new String[]{"restrained", "bonded", "overdrive", "takeover"}) {
			bullet(lines, "projecthero.guide.symbiote.hunger.tier." + t);
		}
		blank(lines);
		head(lines, "projecthero.guide.symbiote.black_suit");
		para(lines, "projecthero.guide.symbiote.black_suit.intro");
		String[] moves = {com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.TENDRIL_LASH, com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.SPIKE_BURST, com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.CONSUME,
				com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.INK_SHROUD, com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.TAKE_OVER, com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.SLAM};
		char x = com.projecthero.mod.hero.AbilitySlot.byNumber(3).defaultKey();
		char z = com.projecthero.mod.hero.AbilitySlot.byNumber(4).defaultKey();
		char v = com.projecthero.mod.hero.AbilitySlot.byNumber(5).defaultKey();
		char c = com.projecthero.mod.hero.AbilitySlot.byNumber(6).defaultKey();
		String[] keys = {String.valueOf(x), "Sneak+" + x, "Sneak+hold " + z, "Sneak+" + v, "Hold " + v, "Sneak+" + c};
		String[] cooldowns = {"1.5", "6", "14", "12", null, "14"};
		for (int i = 0; i < moves.length; i++) {
			symbioteRow(lines, keys[i], moves[i], cooldowns[i]);
		}
		blank(lines);
	}

	private static void symbiotePassives(List<Component> lines, boolean normalHost) {
		head(lines, "projecthero.guide.symbiote.passives");
		for (String p : normalHost
				? new String[]{"recovery", "protect", "resurrect", "falls", "arrows", "cloak", "predator", "claws", "diet", "squad", "pet", "vial"}
				: new String[]{"protect", "resurrect", "predator", "squad", "pet", "vial"}) {
			bullet(lines, "projecthero.guide.symbiote.s.passive." + p);
		}
		blank(lines);
	}

	private static void symbioteWeaknesses(List<Component> lines) {
		head(lines, "projecthero.guide.symbiote.weaknesses");
		bullet(lines, "projecthero.guide.symbiote.s.weakness.sonic");
		bullet(lines, "projecthero.guide.symbiote.s.weakness.fire");
		blank(lines);
	}

	private static void symbioteTips(List<Component> lines) {
		head(lines, "projecthero.guide.symbiote.s.tips");
		for (String t : new String[]{"carnage", "burn", "biomass"}) {
			bullet(lines, "projecthero.guide.symbiote.s.tip." + t);
		}
	}

	// The framing chapters are always added in this order by build(); the outline references them by
	// these indices. Powers follow, one per Powers.all() entry, at CHAPTER_POWER_BASE + registration
	// index.
	private static final int CH_OVERVIEW = 0;
	private static final int CH_MUTATION = 1;
	private static final int CH_STRUCTURES = 2;
	private static final int CH_DEVICES = 3;
	private static final int CH_COMBOS = 4;
	private static final int CH_THOR = 5;
	private static final int CH_IRON_MAN = 6;
	private static final int CH_SPIDER_MAN = 7;
	private static final int CH_MAX_STEEL = 8;
	private static final int CH_PUNISHER = 9;
	private static final int CH_GREEN_LANTERN = 10;
	private static final int CH_SYMBIOTE = 11;
	private static final int CH_ZOMBIE_RAID = 12;
	private static final int CH_SUPERVILLAIN_RAID = 13;
	private static final int CH_TITAN = 14;
	private static final int CH_SQUADS = 15;
	private static final int CH_WOLVERINE = 16;
	private static final int CH_TITAN_SHIFTER = 17;
	private static final int CH_ALL_MIGHT = 18;
	private static final int CH_ABYSSAL_BEHEMOTH = 19;
	private static final int CH_OATHBREAKER = 20;
	private static final int CH_HULK = 21;
	private static final int CH_DARKSEID_RAID = 22;
	private static final int CH_MOON_KNIGHT = 23;
	private static final int CH_SUPER_SOLDIER = 24;
	private static final int CH_KRYPTONIAN = 25;
	private static final int CH_HORDES = 26;
	private static final int CH_STARK_SORTER = 27;
	private static final int CH_SYNDICATE = 28; // v0.14.25
	private static final int CH_CARNAGE = 29; // v0.14.25
	private static final int CH_SENTINEL_PURGE = 30; // v0.15.1
	private static final int CH_ULTRON = 31; // v0.15.12
	private static final int CH_NOVA = 32; // v0.15.13
	private static final int CH_FROST_GIANTS = 33; // v0.16.1
	private static final int CH_ULTRON_CONTAGION = 34; // v0.16.1
	private static final int CH_GALACTUS = 35; // v0.16.1
	private static final int CH_CRIME = 36; // v0.16.2
	private static final int CH_CITY = 37; // v0.16.2 City part A: Cities
	private static final int CH_CITY_SERVICES = 38; // v0.16.2 City part B: City Services
	private static final int CH_GHOST_RIDER = 39; // v0.16.5
	private static final int CH_CYBORG = 40; // v0.16.5
	private static final int CH_BLUE_BEETLE = 41; // v0.16.5
	private static final int CH_LADYBUG = 42; // v0.16.6
	private static final int CH_CAT_NOIR = 43; // v0.16.6
	private static final int CH_RENA_ROUGE = 44; // v0.16.6
	private static final int CH_HAWK_MOTH = 45; // v0.16.6
	private static final int CH_CARAPACE = 46; // v0.16.6
	private static final int CH_PEGASUS = 47; // v0.16.6
	private static final int CH_QUIRKS = 48; // v0.16.7: the Quirks overview
	/**
	 * v0.16.7: one chapter per kit Quirk, in {@link com.projecthero.mod.quirk.Quirk#kitQuirks()} order (One For All, the eighth
	 * Quirk, is standalone: its chapter is the All Might one, {@link #CH_ALL_MIGHT}, so these indices stay put).
	 */
	private static final int CH_QUIRK_BASE = 49;
	/** v0.16.7: Viperion (after the Quirks, so their indices stay put). */
	private static final int CH_VIPERION = CH_QUIRK_BASE + com.projecthero.mod.quirk.Quirk.kitQuirks().length; // 56
	/** v0.17.1: the Falcon and the Silver Surfer (after Viperion, so every earlier index stays put). */
	private static final int CH_FALCON = CH_VIPERION + 1; // 57
	private static final int CH_SILVER_SURFER = CH_FALCON + 1; // 58
	/** v0.17.1: the Speed Force Speedster (after the Silver Surfer). */
	private static final int CH_SPEED_FORCE = CH_SILVER_SURFER + 1; // 59
	/** v0.17.1: Multimouse (after the Speed Force Speedster, so every earlier index stays put). */
	private static final int CH_MULTIMOUSE = CH_SPEED_FORCE + 1; // 60
	private static final int CHAPTER_POWER_BASE = CH_MULTIMOUSE + 1; // 61

	private static List<Chapter> build() {
		List<Chapter> out = new ArrayList<>();

		out.add(chapter("projecthero.guide.overview", lines -> {
			para(lines, "projecthero.guide.overview.body");
			blank(lines);
			head(lines, "projecthero.guide.powerclass");
			para(lines, "projecthero.guide.powerclass.body");
			blank(lines);
			// v0.16.1: the projectheroUniquePowers gamerule
			head(lines, "projecthero.guide.unique_powers");
			para(lines, "projecthero.guide.unique_powers.body");
			// v0.16.6: the spawn-in callings
			blank(lines);
			head(lines, "projecthero.guide.spawn_callings");
			para(lines, "projecthero.guide.spawn_callings.body");
			blank(lines);
			head(lines, "projecthero.guide.controls.title");
			para(lines, "projecthero.guide.controls.body");
			for (AbilitySlot slot : AbilitySlot.values()) {
				lines.add(Component.literal("  " + slot.defaultKey() + "  ")
						.withStyle(ChatFormatting.GOLD)
						.append(Component.translatable(slot.keyBindingTranslationKey()).withStyle(ChatFormatting.WHITE))
						.append(Component.literal("  — " + slot.role()).withStyle(ChatFormatting.GRAY)));
			}
			lines.add(Component.translatable("projecthero.guide.controls.select").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("projecthero.guide.controls.utility2").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("projecthero.guide.controls.info").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("projecthero.guide.controls.squad").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("projecthero.guide.controls.flight_mode").withStyle(ChatFormatting.GRAY)); // v0.16.2
			// v0.14.16: every flight in the mod steers the same way now
			blank(lines);
			head(lines, "projecthero.guide.flight");
			para(lines, "projecthero.guide.flight.body");
			blank(lines);
			para(lines, "projecthero.guide.flight.physics"); // v0.16.2: the , Flight Mode menu
		}));

		out.add(chapter("projecthero.guide.mutation", lines -> {
			para(lines, "projecthero.guide.mutation.body");
			blank(lines);
			para(lines, "projecthero.guide.mutation.capacity");
			blank(lines);
			para(lines, "projecthero.guide.mutation.research");
			blank(lines);
			para(lines, "projecthero.guide.mutation.notes"); // v0.15.11: 4 notes + a bottle = a serum
			blank(lines);
			para(lines, "projecthero.guide.mutation.random_serums");
		}));

		out.add(chapter("projecthero.guide.structures", lines -> {
			para(lines, "projecthero.guide.structures.body");
			blank(lines);
			for (String s : new String[]{"research_facility", "meteor_impact", "power_station",
					"geological_site", "government_site", "hydrostatic_facility", "gamma_lab", "temple_of_khonshu"}) {
				lines.add(Component.translatable("projecthero.guide.structure." + s).withStyle(ChatFormatting.WHITE));
				para(lines, "projecthero.guide.structure." + s + ".desc");
			}
		}));

		out.add(chapter("projecthero.guide.devices", lines -> {
			para(lines, "projecthero.guide.devices.body");
			blank(lines);
			for (String d : new String[]{"overloaded_redstone_coil", "experimental_light_projector",
					"unstable_gravity_plate", "geological_resonance_chamber", "molecular_compression_chamber",
					"mass_compression_chamber", "pressure_chamber", "hydrostatic_test_tank", "electromagnetic_coil"}) {
				lines.add(Component.translatable("block.projecthero." + d).withStyle(ChatFormatting.WHITE));
			}
			// v0.16.5 (user): the Mannequin and the ten-suit Wardrobe
			blank(lines);
			head(lines, "projecthero.guide.devices.wardrobe");
			para(lines, "projecthero.guide.devices.wardrobe.mannequin");
			para(lines, "projecthero.guide.devices.wardrobe.wardrobe");
		}));

		out.add(chapter("projecthero.guide.combos", lines -> {
			para(lines, "projecthero.guide.combos.body");
			blank(lines);
			for (String k : COMBO_KEYS) {
				lines.add(Component.literal("• ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.combo." + k).withStyle(ChatFormatting.GRAY)));
			}
		}));

		// Thor (v0.13.3 rewrite) -- brought up to the same tier-line / progression / controls / bulleted
		// passives structure every other Hero-Tier page already uses (see Wolverine above for the
		// clearest example of the pattern).
		out.add(chapter("projecthero.guide.thor", lines -> {
			lines.add(Component.translatable("projecthero.guide.thor.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.thor.body");
			blank(lines);
			head(lines, "projecthero.guide.thor.progression");
			for (String step : new String[]{"find_hammer", "worthy", "lift"}) {
				lines.add(Component.literal("  ↓  ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.translatable("projecthero.guide.thor.step." + step).withStyle(ChatFormatting.WHITE)));
			}
			blank(lines);
			para(lines, "projecthero.guide.thor.bind");
			blank(lines);
			head(lines, "projecthero.guide.thor.perks");
			for (String perk : new String[]{"melee", "hearts", "speed", "damage_reduction", "regen", "hammer_damage", "hud", "squad",
					"storm"}) { // v0.16.1: the thunderstorm buff
				lines.add(Component.literal("• ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.guide.thor.perk." + perk).withStyle(ChatFormatting.GRAY)));
			}
			blank(lines);
			head(lines, "projecthero.guide.thor.controls");
			for (String[] slot : new String[][]{
					{"R", "call_mjolnir"}, {"G", "lightning_strike"}, {"Shift+G", "thunder_field"}, // v0.16.1: Thunder Field
					{"X", "lightning_beam"},
					{"Z", "god_of_thunders_wrath"}, {"V", "hammer_volley"}, {"Shift+V", "thunderclap"},
					{"C", "chain_lightning"}, {"N", "weapon_select"}, // v0.15.3: N = the weapon selector
			}) {
				lines.add(Component.literal(" " + slot[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.thor.ability." + slot[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.thor.ability." + slot[1]);
			}
			blank(lines);
			head(lines, "projecthero.guide.thor.flight");
			para(lines, "projecthero.guide.thor.flight.body");
			blank(lines);
			head(lines, "projecthero.guide.thor.armour");
			para(lines, "projecthero.guide.thor.armour.body");
			// v0.14.19: Stormbreaker -- recipe, the Nether forging, its two right-click powers, and that it is Thor's weapon
			blank(lines);
			head(lines, "projecthero.guide.thor.stormbreaker");
			// v0.15.1: "recall" -- Stormbreaker binds on first worthy carry and R calls it interchangeably with Mjolnir
			// v0.15.3: "dropped" -- Stormbreaker on the ground is a resting entity like Mjolnir
			for (String part : new String[]{"intro", "recipe", "forge", "throw", "bifrost", "recall", "dropped", "weapon"}) {
				para(lines, "projecthero.guide.thor.stormbreaker." + part);
			}
			// v0.14.20: the Mjolnir / Stormbreaker 3-hit melee combo
			blank(lines);
			head(lines, "projecthero.guide.thor.combo");
			para(lines, "projecthero.guide.thor.combo.body");
			para(lines, "projecthero.guide.thor.combo.finisher");
			// v0.16.1: Mjolnir + Stormbreaker together -- the 4-hit dual combo and its Twin Thunder Strike finisher
			blank(lines);
			head(lines, "projecthero.guide.thor.dual");
			para(lines, "projecthero.guide.thor.dual.body");
			para(lines, "projecthero.guide.thor.dual.twin_thunder");
			// v0.16.1: the thunderstorm buff
			blank(lines);
			head(lines, "projecthero.guide.thor.storm");
			para(lines, "projecthero.guide.thor.storm.body");
		}));

		out.add(chapter("projecthero.guide.iron_man", lines -> {
			lines.add(Component.translatable("projecthero.guide.iron_man.tier").withStyle(ChatFormatting.DARK_AQUA));
			para(lines, "projecthero.guide.iron_man.body");
			blank(lines);
			head(lines, "projecthero.guide.iron_man.progression");
			// v0.16.1 comic rework: the nine-Mark linear progression
			// v0.16.7 (user): Era I (the Gantry Age) first, then the later suits
			for (String step : new String[]{"arc_reactor", "tony_stark", "mark_1", "fabricator", "mark_1_v2", "mark_iii",
					"mark_4", "mark_v", "mark_2", "mark_6", "mark_vii", "mark_9", "mark_8"}) {
				lines.add(Component.literal("  ↓  ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.translatable("projecthero.guide.iron_man.step." + step).withStyle(ChatFormatting.WHITE)));
			}
			blank(lines);
			para(lines, "projecthero.guide.iron_man.recipes");
			para(lines, "projecthero.guide.iron_man.controls");
			// v0.16.7 (user): Era I -- the Gantry Age, reactor energy, the emergency eject, the Stark Workbench
			for (String section : new String[]{"era1", "era1_energy", "era1_eject", "workbench"}) {
				head(lines, "projecthero.guide.iron_man." + section);
				para(lines, "projecthero.guide.iron_man." + section + ".body");
			}
			// v0.16.1 comic rework: integrity is the resource, every key is tap / Shift
			head(lines, "projecthero.guide.iron_man.comic");
			para(lines, "projecthero.guide.iron_man.comic.body");
			para(lines, "projecthero.guide.iron_man.comic.integrity");
			// v0.14.21: the animated suit-up / platform / pod / suitcase
			head(lines, "projecthero.guide.iron_man.suit_up");
			para(lines, "projecthero.guide.iron_man.suit_up.body");
			// v0.15.11: C with a suit in the pack = putting it on by hand (Mark 1 cave build, Marks 2-7 workshop build)
			head(lines, "projecthero.guide.iron_man.hand_build");
			para(lines, "projecthero.guide.iron_man.hand_build.body");
			// v0.15.15: C to take a Mark 1-7 off (Mark 1 by hand; v0.15.19: Marks 2-7 by hand too, with the wrench)
			head(lines, "projecthero.guide.iron_man.suit_down");
			para(lines, "projecthero.guide.iron_man.suit_down.body");
			head(lines, "projecthero.guide.iron_man.screens"); // v0.14.21 UI redesign
			para(lines, "projecthero.guide.iron_man.screens.body");
			// v0.14.26: scanner, Mark III targeting, I-key spec sheet, auto-feed, the Stark cookers
			for (String section : new String[]{"scanner", "targeting", "spec_sheet", "auto_feed", "furnaces",
					"mark_1", "mark_1_v2", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii", "mark_9", "mark_8", "platform", "gantry", "combat", "jarvis",
					"remote_pilot", "sentry", "armour_rules", "glasses", "bracelets", "phoenix"}) { // v0.15.9: + Mark 8, Sentry Mode; // v0.14.29: + Mark 4 / 5 / 6 / 7, combat, JARVIS, remote pilot; v0.15.1: + armour rules; v0.15.2: + Stark Glasses, Protocol Phoenix; v0.15.4: + Stark Gantry, Colantotte Bracelets
				head(lines, "projecthero.guide.iron_man." + section);
				para(lines, "projecthero.guide.iron_man." + section + ".body");
				// v0.16.7 (user): an Era I suit's page lists its twelve moves straight from the kit (always in sync)
				if (com.projecthero.mod.ironman.ability.era1.IronManEraOne.isEraOne(section)) {
					eraOneMoves(lines, section);
				}
			}
		}));

		// Spider-Man. Sits with the other Hero Classes rather than with the 27 mutations, because that
		// is what it is -- but its progression starts inside the mutation system, so the entry spells
		// out the whole path from serum to Hero Class in one place.
		out.add(chapter("projecthero.guide.spider_man", lines -> {
			lines.add(Component.translatable("projecthero.guide.spider_man.tier").withStyle(ChatFormatting.DARK_RED));
			para(lines, "projecthero.guide.spider_man.body");
			blank(lines);
			head(lines, "projecthero.guide.spider_man.progression");
			for (String step : new String[]{"adhesion", "mutagen", "evolve"}) {
				lines.add(Component.literal("  ↓  ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.translatable("projecthero.guide.spider_man.step." + step).withStyle(ChatFormatting.WHITE)));
			}
			blank(lines);
			spiderControls(lines); // v0.16.1: the 18-move kit, traversal and the web structures
			spiderSuitV2(lines); // v0.16.2: the Suit v2 -- recipe, Web Wings, Trip Mine
			head(lines, "projecthero.guide.spider_man.passives");
			for (String p : new String[]{"sense", "dodge", "arrows", "crawl", "ceiling", "wall_leap",
					"double_jump", "super_jump", "strength", "agility", "fall", "reserve"}) {
				lines.add(Component.literal(" • ").append(
						Component.translatable("projecthero.guide.spider_man.passive." + p)).withStyle(ChatFormatting.GRAY));
			}
			blank(lines);
			para(lines, "projecthero.guide.spider_man.swinging");
			para(lines, "projecthero.guide.spider_man.reserve");
		}));
		// Max Steel -- Hero Tier. Max McGrath bonded with the Ultralink Steel: T.U.R.B.O. Energy, a
		// nanotech suit, and five specialised Turbo Modes.
		out.add(chapter("projecthero.guide.max_steel", lines -> {
			lines.add(Component.translatable("projecthero.guide.max_steel.tier").withStyle(ChatFormatting.DARK_AQUA));
			para(lines, "projecthero.guide.max_steel.body");
			blank(lines);
			head(lines, "projecthero.guide.max_steel.progression");
			para(lines, "projecthero.guide.max_steel.step.find");
			para(lines, "projecthero.guide.max_steel.step.bond");
			blank(lines);
			head(lines, "projecthero.guide.max_steel.energy");
			para(lines, "projecthero.guide.max_steel.energy.body");
			blank(lines);
			head(lines, "projecthero.guide.max_steel.suit");
			para(lines, "projecthero.guide.max_steel.suit.body");
			blank(lines);
			head(lines, "projecthero.guide.max_steel.controls");
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C"}) {
				String key = switch (slot) {
					case "R" -> "turbo_blast"; case "G" -> "turbo_strength"; case "X" -> "turbo_speed";
					case "Z" -> "turbo_flight"; case "V" -> "turbo_stealth"; default -> "turbo_cannon";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.max_steel.ability." + key).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.max_steel.ability." + key);
			}
			blank(lines);
			head(lines, "projecthero.guide.max_steel.hud");
			para(lines, "projecthero.guide.max_steel.hud.body");
			blank(lines);
			head(lines, "projecthero.guide.max_steel.passives");
			para(lines, "projecthero.guide.max_steel.passives.body");
			blank(lines);
			head(lines, "projecthero.guide.max_steel.emergency");
			para(lines, "projecthero.guide.max_steel.emergency.body");
		}));

		// Punisher -- Hero Tier. A trained human: firearms, explosives, tactical gear. Earned through
		// Vigilante Training, not an accident.
		out.add(chapter("projecthero.guide.punisher", lines -> {
			lines.add(Component.translatable("projecthero.guide.punisher.tier").withStyle(ChatFormatting.DARK_GRAY));
			para(lines, "projecthero.guide.punisher.body");
			blank(lines);
			head(lines, "projecthero.guide.punisher.progression");
			para(lines, "projecthero.guide.punisher.step.safehouse");
			para(lines, "projecthero.guide.punisher.step.training");
			blank(lines);
			head(lines, "projecthero.guide.punisher.weapons");
			for (String w : new String[]{"punisher_pistol", "punisher_assault_rifle",
					"punisher_shotgun", "punisher_sniper"}) {
				lines.add(Component.translatable("item.projecthero." + w).withStyle(ChatFormatting.WHITE));
			}
			para(lines, "projecthero.guide.punisher.ammo");
			blank(lines);
			head(lines, "projecthero.guide.punisher.controls");
			// v0.15.18: the new kit -- every key has a Shift move with its own cooldown; N opens the satchel
			para(lines, "projecthero.guide.punisher.controls.note");
			for (String[] row : new String[][] {
					{ "R", "mark" }, { "Shift+R", "threat" }, { "G", "strike" }, { "Shift+G", "kick" },
					{ "Z", "grenade" }, { "Shift+Z", "warzone" }, { "X", "roll" }, { "Shift+X", "advance" },
					{ "C", "smoke" }, { "Shift+C", "flashbang" }, { "V", "weapon" }, { "N", "satchel" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.guide.punisher.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
			}
			blank(lines);
			// v0.15.18: V = Weapon Ability, one move (and a Shift move) per gun
			head(lines, "projecthero.guide.punisher.weapon_ability");
			para(lines, "projecthero.guide.punisher.weapon_ability.body");
			for (String w : new String[]{"pistol", "shotgun", "rifle", "sniper"}) {
				para(lines, "projecthero.guide.punisher.weapon_ability." + w);
			}
			blank(lines);
			head(lines, "projecthero.guide.punisher.passives");
			para(lines, "projecthero.guide.punisher.passives.body");
			blank(lines);
			// v0.13.11: Punisher + Symbiote = Agent Venom
			head(lines, "projecthero.guide.agent_venom");
			para(lines, "projecthero.guide.agent_venom.body");
			for (String[] row : new String[][] { { "H", "suit" }, { "Sneak+X", "agent_venom_tendril_swing" },
					{ "Sneak+Z", "agent_venom_tendril_snatch" }, { "Sneak+V", "agent_venom_unleashed" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.agent_venom.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.agent_venom.ability." + row[1] + ".desc");
			}
			head(lines, "projecthero.guide.agent_venom.passives");
			para(lines, "projecthero.guide.agent_venom.passives.body");
			blank(lines);
			para(lines, "projecthero.guide.punisher.crafting");
		}));

		// Green Lantern -- Hero Tier. The "toolbox" hero: a bonded Power Ring, a 10,000-point Ring
		// Charge resource, controlled flight, hard-light constructs, ranged attacks and shielding.
		out.add(chapter("projecthero.guide.green_lantern", lines -> {
			lines.add(Component.translatable("projecthero.guide.green_lantern.tier").withStyle(ChatFormatting.GREEN));
			para(lines, "projecthero.guide.green_lantern.body");
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.progression");
			para(lines, "projecthero.guide.green_lantern.step.find");
			para(lines, "projecthero.guide.green_lantern.step.trial");
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.charge");
			para(lines, "projecthero.guide.green_lantern.charge.body");
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.flight");
			para(lines, "projecthero.guide.green_lantern.flight.body");
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.air_tank");
			para(lines, "projecthero.guide.green_lantern.air_tank.body");
			head(lines, "projecthero.guide.green_lantern.dome_model"); // v0.15.1
			para(lines, "projecthero.guide.green_lantern.dome_model.body");
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.suits"); // v0.15.15: N suit screen
			para(lines, "projecthero.guide.green_lantern.suits.body");
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.controls");
			// v0.14.3: H (Giant Hand) and N (dismiss / take off the ring) joined the kit
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C", "H", "N"}) {
				String key = switch (slot) {
					case "R" -> "ring_bolt"; case "G" -> "construct_fist"; case "X" -> "oath";
					case "Z" -> "shield"; case "V" -> "giant_hand"; case "H" -> "suit"; // v0.15.15 swap
					case "N" -> "dismiss"; // v0.15.15 gl2: was swallowed by the comment above (N showed the construct text)
					default -> "construct";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.guide.green_lantern.ability." + key).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.green_lantern.ability." + key + ".desc");
			}
			blank(lines);
			// v0.17.1 revamp: the Construct Menu, Lantern Ranks and every construct the ring can make
			head(lines, "projecthero.guide.green_lantern.menu");
			para(lines, "projecthero.guide.green_lantern.menu.body");
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.ranks");
			para(lines, "projecthero.guide.green_lantern.ranks.body");
			for (int r = 1; r <= com.projecthero.mod.greenlantern.GreenLanternRanks.MAX_RANK; r++) {
				lines.add(Component.literal(" " + r + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable(com.projecthero.mod.greenlantern.GreenLanternRanks.nameKey(r)).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.green_lantern.ranks." + r);
			}
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.constructs");
			para(lines, "projecthero.guide.green_lantern.constructs.body");
			for (com.projecthero.mod.greenlantern.bind.GreenLanternBinding.Section sec
					: com.projecthero.mod.greenlantern.bind.GreenLanternBinding.Section.values()) {
				lines.add(Component.translatable(sec.translationKey()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
				for (com.projecthero.mod.greenlantern.bind.GreenLanternBinding.Entry e
						: com.projecthero.mod.greenlantern.bind.GreenLanternBinding.all()) {
					if (e.section() != sec) {
						continue;
					}
					lines.add(Component.literal(" ").append(Component.translatable(e.nameKey()).withStyle(ChatFormatting.WHITE))
							.append(Component.translatable(e.hold() ? "projecthero.guide.green_lantern.entry_hold" : "projecthero.guide.green_lantern.entry_tap")
									.withStyle(ChatFormatting.DARK_GRAY)));
					para(lines, e.descKey());
				}
			}
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.sniper"); // v0.16.1
			para(lines, "projecthero.guide.green_lantern.sniper.body");
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.battery");
			para(lines, "projecthero.guide.green_lantern.battery.body");
			// v0.17.1: Construct Combos, construct durability, the ring's voice
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.combos");
			para(lines, "projecthero.guide.green_lantern.combos.body");
			for (com.projecthero.mod.greenlantern.GreenLanternCombos.Combo c : com.projecthero.mod.greenlantern.GreenLanternCombos.Combo.values()) {
				Component opener = Component.translatable("projecthero.guide.green_lantern.combos.move." + c.opener.name().toLowerCase(java.util.Locale.ROOT));
				Component finisher = c.finisher == null ? Component.translatable("projecthero.guide.green_lantern.combos.pin_wall")
						: Component.translatable("projecthero.guide.green_lantern.combos.move." + c.finisher.name().toLowerCase(java.util.Locale.ROOT));
				lines.add(Component.literal(" ").append(Component.translatable(c.nameKey()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
						.append(Component.literal("  "))
						.append(Component.translatable("projecthero.guide.green_lantern.combos.entry", opener, finisher)
								.withStyle(ChatFormatting.WHITE)));
				para(lines, c.descKey());
			}
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.durability");
			para(lines, "projecthero.guide.green_lantern.durability.body");
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.ring_voice");
			para(lines, "projecthero.guide.green_lantern.ring_voice.body");
		}));

		// The Symbiote -- not a Hero Class of its own like the other four: any player can bond with it,
		// and which host variant they get is derived live from whether they also hold Spider-Man or the
		// Punisher. Sits with the other Hero-Tier chapters because that is what pressing I shows.
		// v0.15.15: restructured into the same short sections as the I-key pages (see symbioteNormalHostChapter).
		out.add(chapter("projecthero.guide.symbiote", lines -> {
			lines.add(Component.translatable("projecthero.guide.symbiote.tier").withStyle(ChatFormatting.LIGHT_PURPLE));
			para(lines, "projecthero.guide.symbiote.overview");
			blank(lines);
			symbioteHowToGet(lines);
			head(lines, "projecthero.guide.symbiote.hosts");
			para(lines, "projecthero.guide.symbiote.host.normal");
			para(lines, "projecthero.guide.symbiote.host.spider_man");
			para(lines, "projecthero.guide.symbiote.host.agent_venom");
			blank(lines);
			symbioteSuit(lines);
			symbioteBiomass(lines);
			para(lines, "projecthero.guide.symbiote.controls.body");
			symbioteMoves(lines);
			symbioteBlackSuitExtras(lines);
			symbiotePassives(lines, true);
			symbioteWeaknesses(lines);
			// v0.14.4: infested animals and Symbiote Pets
			head(lines, "projecthero.guide.symbiote.creatures");
			para(lines, "projecthero.guide.symbiote.creatures.wild");
			para(lines, "projecthero.guide.symbiote.creatures.pet");
			para(lines, "projecthero.guide.symbiote.creatures.form"); // v0.14.4 pet hosts
			para(lines, "projecthero.guide.symbiote.creatures.powers");
			para(lines, "projecthero.guide.symbiote.creatures.release");
			blank(lines);
			symbioteTips(lines);
		}));

		// World events. Written here rather than in a separate book so there is exactly one in-game
		// reference for the whole mod (spec section 38/58: integrate, do not build a second encyclopedia).
		out.add(chapter("projecthero.guide.zombie_raid", lines -> {
			para(lines, "projecthero.guide.zombie_raid.body");
			blank(lines);
			for (String section : new String[]{"curse", "sources", "waves", "bosses", "rewards", "trophies", "crafting", "repeat"}) { // v0.14.4: + trophies
				head(lines, "projecthero.guide.zombie_raid." + section);
				para(lines, "projecthero.guide.zombie_raid." + section + ".body");
				blank(lines);
			}
		}));

		out.add(chapter("projecthero.guide.supervillain_raid", lines -> {
			para(lines, "projecthero.guide.supervillain_raid.body");
			blank(lines);
			for (String section : new String[]{"spy", "mark", "prepare", "waves", "villain", "rewards"}) {
				head(lines, "projecthero.guide.supervillain_raid." + section);
				para(lines, "projecthero.guide.supervillain_raid." + section + ".body");
				blank(lines);
			}
		}));

		// The Titan (v0.10.18) -- a rare wilderness encounter, not part of the scripted raid events
		// above, so it gets its own short entry rather than a subsection of Zombie Raid.
		out.add(chapter("projecthero.guide.titan", lines -> {
			para(lines, "projecthero.guide.titan.body");
			blank(lines);
			// v0.14.4: "moves" (every attack and its tell, incl. Leaping Slam / Grave Roar) and "threat" (aggro).
			for (String section : new String[]{"tell", "fight", "moves", "threat", "rewards"}) {
				head(lines, "projecthero.guide.titan." + section);
				para(lines, "projecthero.guide.titan." + section + ".body");
				blank(lines);
			}
		}));

		// Squads (v0.10.10). Sits between the world events and the powers because it is the thing that
		// makes the rest of the mod playable together: almost every ability in here is an area attack.
		out.add(chapter("projecthero.guide.squads", lines -> {
			para(lines, "projecthero.guide.squads.body");
			blank(lines);
			para(lines, "projecthero.guide.squads.pets"); // v0.15.11: squadmates' pets are safe too
			blank(lines);
			head(lines, "projecthero.guide.squads.commands");
			para(lines, "projecthero.guide.squads.commands.body");
			blank(lines);
			head(lines, "projecthero.guide.squads.menu");
			para(lines, "projecthero.guide.squads.menu.body");
		}));

		// Wolverine (v0.12.1) -- Hero Tier, an ascension of Super Regeneration. Appended after Squads so the
		// fixed framing-chapter indices above stay put; the index links it under Heroes.
		out.add(chapter("projecthero.guide.wolverine", lines -> {
			lines.add(Component.translatable("projecthero.guide.wolverine.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.wolverine.body");
			blank(lines);
			head(lines, "projecthero.guide.wolverine.progression");
			para(lines, "projecthero.guide.wolverine.step.prereq");
			para(lines, "projecthero.guide.wolverine.step.serum");
			blank(lines);
			head(lines, "projecthero.guide.wolverine.claws");
			para(lines, "projecthero.guide.wolverine.claws.body");
			blank(lines);
			head(lines, "projecthero.guide.wolverine.suit");
			para(lines, "projecthero.guide.wolverine.suit.body");
			blank(lines);
			head(lines, "projecthero.guide.wolverine.death");
			para(lines, "projecthero.guide.wolverine.death.body");
			blank(lines);
			head(lines, "projecthero.guide.wolverine.controls");
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C"}) {
				String key = switch (slot) {
					case "R" -> "claw_slash"; case "G" -> "cross_slash"; case "X" -> "claw_dash";
					case "Z" -> "berserker_plunge"; case "V" -> "frenzy"; default -> "berserker_rage";
				};
				// v0.16.1: every key has a Shift move (Sneak + the key), with its own cooldown
				String shift = switch (slot) {
					case "R" -> "rising_uppercut"; case "G" -> "whirlwind"; case "X" -> "pounce";
					case "Z" -> "adamantium_execution"; case "V" -> "bleed_flurry"; default -> "feral_roar";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.wolverine.ability." + key).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.wolverine.ability." + key);
				lines.add(Component.literal(" Shift+" + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.wolverine.ability." + shift).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.wolverine.ability." + shift);
			}
			lines.add(Component.literal(" H  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.guide.wolverine.toggle").withStyle(ChatFormatting.WHITE)));
			lines.add(Component.literal(" N  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.guide.wolverine.sniff").withStyle(ChatFormatting.WHITE)));
			// v0.13.9: the mouse buttons with the claws out
			lines.add(Component.literal(" LMB  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.guide.wolverine.lmb").withStyle(ChatFormatting.WHITE)));
			lines.add(Component.literal(" RMB  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.guide.wolverine.rmb").withStyle(ChatFormatting.WHITE)));
			blank(lines);
			head(lines, "projecthero.guide.wolverine.passives");
			para(lines, "projecthero.guide.wolverine.passives.body");
			blank(lines);
			head(lines, "projecthero.guide.wolverine.emergency");
			para(lines, "projecthero.guide.wolverine.emergency.body");
		}));

		// Titan Shifter (v0.12.31) -- Hero Tier. Appended after Wolverine so every earlier index stays put.
		out.add(chapter("projecthero.guide.titan_shifter", lines -> {
			lines.add(Component.translatable("projecthero.guide.titan_shifter.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.titan_shifter.body");
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.progression");
			para(lines, "projecthero.guide.titan_shifter.step.serum");
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.transform");
			para(lines, "projecthero.guide.titan_shifter.transform.body");
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.energy");
			para(lines, "projecthero.guide.titan_shifter.energy.body");
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.emergency");
			para(lines, "projecthero.guide.titan_shifter.emergency.body");
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.controls");
			lines.add(Component.literal(" H  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.guide.titan_shifter.shift_key").withStyle(ChatFormatting.WHITE)));
			// v0.16.7 (user, Titan revamp): the keys every Titan shares; each Titan's own moves follow
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "Shift+V", "C", "Shift+C"}) {
				String key = switch (slot) {
					case "R" -> "punch"; case "G" -> "heavy_smash"; case "X" -> "leap"; case "Z" -> "stomp";
					case "V" -> "roar"; case "Shift+V" -> "nape_guard"; case "C" -> "regeneration"; default -> "hardening";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.titan_shifter.ability." + key).withStyle(ChatFormatting.WHITE)));
				if (!"nape_guard".equals(key)) {
					para(lines, "projecthero.guide.titan_shifter.ability." + key);
				}
			}
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.stats");
			para(lines, "projecthero.guide.titan_shifter.stats.body");
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.kits");
			for (com.projecthero.mod.titanshifter.TitanType t : com.projecthero.mod.titanshifter.TitanType.values()) {
				para(lines, "projecthero.guide.titan_shifter.kit." + t.id);
			}
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.nape");
			para(lines, "projecthero.guide.titan_shifter.nape.body");
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.limbs");
			para(lines, "projecthero.guide.titan_shifter.limbs.body");
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.human");
			para(lines, "projecthero.guide.titan_shifter.human.body");
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.defeat");
			para(lines, "projecthero.guide.titan_shifter.defeat.body");
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.commands");
			para(lines, "projecthero.guide.titan_shifter.commands.body");
		}));

		// All Might (v0.12.33) -- Hero Tier. Appended after the Titan Shifter so every earlier index stays put.
		out.add(chapter("projecthero.guide.all_might", lines -> {
			lines.add(Component.translatable("projecthero.guide.all_might.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.all_might.body");
			blank(lines);
			head(lines, "projecthero.guide.all_might.progression");
			para(lines, "projecthero.guide.all_might.step.vestige");
			blank(lines);
			head(lines, "projecthero.guide.all_might.timer"); // v0.16.7: the timer is back (20 minutes), and the only limit
			para(lines, "projecthero.guide.all_might.timer.body");
			blank(lines);
			head(lines, "projecthero.guide.all_might.forms");
			para(lines, "projecthero.guide.all_might.forms.body");
			head(lines, "projecthero.guide.all_might.costume");
			para(lines, "projecthero.guide.all_might.costume.body");
			blank(lines);
			head(lines, "projecthero.guide.all_might.controls");
			for (String[] row : new String[][] {
					{ "H", "transform" }, { "R", "detroit_smash" }, { "Shift+R", "new_hampshire_smash" }, { "G", "texas_smash" },
					{ "Shift+G", "delaware_smash" }, { "X", "all_might_leap" }, { "Shift+X", "manchester_smash" },
					{ "Z", "united_states_of_smash" }, { "Shift+Z", "oklahoma_smash" }, { "V", "carolina_smash" },
					{ "Shift+V", "missouri_smash" }, { "C", "plus_ultra" }, { "Shift+C", "st_louis_smash" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.all_might.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.all_might.ability." + row[1]);
			}
			blank(lines);
			// v0.16.6: passing on / lending One For All
			head(lines, "projecthero.guide.all_might.pass_on");
			para(lines, "projecthero.guide.all_might.pass_on.body");
			blank(lines);
			head(lines, "projecthero.guide.all_might.passives");
			para(lines, "projecthero.guide.all_might.passives.body");
			blank(lines);
			head(lines, "projecthero.guide.all_might.commands");
			para(lines, "projecthero.guide.all_might.commands.body");
		}));

		// The Abyssal Behemoth (v0.13.1) -- a very rare, endgame Nether world boss, on the same footing
		// as the Titan: not part of the scripted raid events, so it gets its own short entry.
		out.add(chapter("projecthero.guide.abyssal_behemoth", lines -> {
			para(lines, "projecthero.guide.abyssal_behemoth.body");
			blank(lines);
			for (String section : new String[]{"tell", "fight", "rewards"}) {
				head(lines, "projecthero.guide.abyssal_behemoth." + section);
				para(lines, "projecthero.guide.abyssal_behemoth." + section + ".body");
				blank(lines);
			}
		}));

		// The Oathbreaker (v0.14.0 rework) -- summoned, three-phase duel boss.
		out.add(chapter("projecthero.guide.oathbreaker", lines -> {
			para(lines, "projecthero.guide.oathbreaker.body");
			blank(lines);
			for (String section : new String[]{"summon", "poise", "phase1", "phase2", "phase3", "tells", "rewards"}) {
				head(lines, "projecthero.guide.oathbreaker." + section);
				para(lines, "projecthero.guide.oathbreaker." + section + ".body");
				blank(lines);
			}
		}));

		// The Hulk (v0.13.11) -- Hero Tier, built in phases. Appended last so every earlier index stays put.
		out.add(chapter("projecthero.guide.hulk", lines -> {
			lines.add(Component.translatable("projecthero.guide.hulk.tier").withStyle(ChatFormatting.GREEN));
			para(lines, "projecthero.guide.hulk.body");
			blank(lines);
			// v0.16.4: the new rules (hold H / death changes, the 3-minute lock, rage -> size and damage, the Hulk's death)
			for (String section : new String[]{"origin_v0164", "change_v0164", "rage_v0164", "world_breaker_v0164", "stats_v0164", "fists",
					"death_save_v0164"}) { // v0.16.4 (user): World Breaker
				head(lines, "projecthero.guide.hulk." + section);
				para(lines, "projecthero.guide.hulk." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.hulk.controls");
			for (String[] row : new String[][] { { "H", "transform_v0164" },
					// v0.16.4 (user): the rebuilt kit + the N roar (calming is gone)
					{ "R", "super_punch" }, { "Shift+R", "ground_smash" }, { "G", "thunderclap" }, { "Shift+G", "fault_stomp" },
					{ "Z", "bulldoze" }, { "Shift+Z", "uppercut" }, { "X", "super_leap" }, { "C", "gamma_burst" }, { "Shift+C", "earth_wall" },
					{ "V", "grab" }, { "Shift+V", "loki_slam" }, { "Shift+V", "debris_throw" }, { "N", "roar" },
					{ "H (100% rage)", "world_breaker" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.hulk.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.hulk.ability." + row[1] + ".desc");
			}
			blank(lines);
			// v0.15.3: the Gladiator Hulk's twelve weapon moves (full gladiator gear on)
			head(lines, "projecthero.guide.hulk.gladiator_moves");
			para(lines, "projecthero.guide.hulk.gladiator_moves_v0164.body");
			for (String[] row : new String[][] { { "R", "axe_cleave" }, { "Shift+R", "hammer_uppercut" }, { "G", "hammer_quake" },
					{ "Shift+G", "earthsplitter" }, { "Z", "champions_roar" }, { "Shift+Z", "weapon_clash" }, { "X", "arena_leap" },
					{ "Shift+X", "meteor_dive" }, { "C", "axe_throw" }, { "Shift+C", "hammer_hurl" }, { "V", "whirlwind" },
					{ "Shift+V", "arena_grapple" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.hulk.gladiator.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.hulk.gladiator.ability." + row[1] + ".desc");
			}
			blank(lines);
			for (String section : new String[]{"sprint_smash", "rampage_v0164", "riding", "looks_v0164", "limits_v0164", "commands"}) {
				head(lines, "projecthero.guide.hulk." + section);
				para(lines, "projecthero.guide.hulk." + section + ".body");
				blank(lines);
			}
			// v0.15.3: Gladiator Hulk -- the gear, the N screen, the full-kit rule
			head(lines, "projecthero.guide.hulk.gladiator");
			para(lines, "projecthero.guide.hulk.gladiator.body");
			blank(lines);
		}));

		// The Apokolips Invasion (v0.13.18) -- the Darkseid Raid. Appended after Hulk so every earlier index stays put.
		out.add(chapter("projecthero.guide.darkseid_raid", lines -> {
			para(lines, "projecthero.guide.darkseid_raid.body");
			blank(lines);
			for (String section : new String[]{"start", "waves", "mother_boxes", "phase1", "phase2", "phase3", "death", "rewards"}) {
				head(lines, "projecthero.guide.darkseid_raid." + section);
				para(lines, "projecthero.guide.darkseid_raid." + section + ".body");
				blank(lines);
			}
		}));

		// Moon Knight (v0.13.20). Appended after the Darkseid Raid so every earlier index stays put.
		out.add(chapter("projecthero.guide.moon_knight", lines -> {
			lines.add(Component.translatable("projecthero.guide.moon_knight.tier").withStyle(ChatFormatting.WHITE));
			para(lines, "projecthero.guide.moon_knight.body");
			blank(lines);
			for (String section : new String[]{"origin", "suit", "lunar", "vengeance", "alters", "resurrection"}) {
				head(lines, "projecthero.guide.moon_knight." + section);
				para(lines, "projecthero.guide.moon_knight." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.moon_knight.controls");
			para(lines, "projecthero.guide.moon_knight.controls.body");
			// v0.13.21 key layout
			for (String[] row : new String[][] {
					{ "R", "darts" }, { "Hold R", "dart_fan" }, { "Sneak+R", "moon_mark" },
					{ "G", "dive_kick" }, { "Sneak+G", "shadow_step" },
					{ "X", "dash" }, { "Sneak+X", "grapple" },
					{ "Z", "moonbeam" }, { "Hold Z", "eye" }, { "Sneak+Z", "judgement" },
					{ "C", "truncheon" }, { "Truncheon hits", "truncheon_combo" }, { "Hold C", "staff_spin" }, { "Sneak+C", "slam" },
					{ "V", "alter" }, { "Hold V", "alter_pick" }, { "Sneak+V", "alter_special" },
					{ "Jump, hold Sneak", "glide" }, { "Hold right click", "shroud" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.moon_knight.move." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.moon_knight.move." + row[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.moon_knight.commands");
			para(lines, "projecthero.guide.moon_knight.commands.body");
		}));

		// Super Soldier (v0.14.8). Appended after Moon Knight so every earlier index stays put.
		out.add(chapter("projecthero.guide.super_soldier", lines -> {
			lines.add(Component.translatable("projecthero.guide.super_soldier.tier").withStyle(ChatFormatting.BLUE));
			para(lines, "projecthero.guide.super_soldier.body");
			blank(lines);
			for (String section : new String[]{"serum", "refine", "passives", "shield", "suit"}) { // v0.14.9: + shield, suit
				head(lines, "projecthero.guide.super_soldier." + section);
				para(lines, "projecthero.guide.super_soldier." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.super_soldier.controls");
			for (String[] row : com.projecthero.mod.supersoldier.SuperSoldierAbilities.GUIDE_ROWS) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.super_soldier.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.super_soldier.ability." + row[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.super_soldier.commands");
			para(lines, "projecthero.guide.super_soldier.commands.body");
		}));

		// The Kryptonian (v0.14.8). Appended after Moon Knight so every earlier index stays put.
		out.add(chapter("projecthero.guide.kryptonian", lines -> {
			lines.add(Component.translatable("projecthero.guide.kryptonian.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.kryptonian.body");
			blank(lines);
			for (String section : new String[]{"origin", "body_stats", "solar", "flight", "kryptonite", "suit"}) {
				head(lines, "projecthero.guide.kryptonian." + section);
				para(lines, "projecthero.guide.kryptonian." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.kryptonian.controls");
			// v0.14.16: the new layout, plus C / Shift+C
			for (String[] row : new String[][] { { "R", "punch" }, { "Shift+R", "thunderclap" }, { "G", "heat_vision" },
					{ "Shift+G", "ground_slam" }, { "Z", "freeze_breath" }, { "Shift+Z", "solar_flare" }, { "X", "super_dash" },
					{ "Shift+X", "sky_launch" }, { "C", "barrage" }, { "Shift+C", "meteor_strike" }, { "V", "xray_vision" },
					{ "Shift+V", "super_grab" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.kryptonian.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.kryptonian.ability." + row[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.kryptonian.meteor");
			para(lines, "projecthero.guide.kryptonian.meteor.body");
			blank(lines);
			head(lines, "projecthero.guide.kryptonian.commands");
			para(lines, "projecthero.guide.kryptonian.commands.body");
		}));
		// v0.14.12: the Horde blocks
		out.add(chapter("projecthero.guide.hordes", lines -> {
			para(lines, "projecthero.guide.hordes.body");
			for (String section : new String[]{"rules", "zombie", "skeleton", "spider", "rewards", "commands"}) {
				blank(lines);
				head(lines, "projecthero.guide.hordes." + section);
				para(lines, "projecthero.guide.hordes." + section + ".body");
			}
		}));
		// v0.14.16: the Stark Sorting Station (v0.14.21: + supplies)
		out.add(chapter("projecthero.guide.stark_sorter", lines -> {
			para(lines, "projecthero.guide.stark_sorter.body");
			for (String section : new String[]{"recipe", "use", "plan", "tidy", "supply", "safety"}) {
				blank(lines);
				head(lines, "projecthero.guide.stark_sorter." + section);
				para(lines, "projecthero.guide.stark_sorter." + section + ".body");
			}
		}));
		// v0.14.25: the Syndicate Bust
		out.add(chapter("projecthero.guide.syndicate", lines -> {
			para(lines, "projecthero.guide.syndicate.body");
			for (String section : new String[]{"start", "waves", "crew", "kingpin", "rewards", "commands"}) {
				blank(lines);
				head(lines, "projecthero.guide.syndicate." + section);
				para(lines, "projecthero.guide.syndicate." + section + ".body");
			}
		}));
		// v0.14.25: Carnage
		out.add(chapter("projecthero.guide.carnage", lines -> {
			para(lines, "projecthero.guide.carnage.body");
			for (String section : new String[]{"arrival", "moves", "new_moves", "split", "weakness", "rewards", "commands"}) { // v0.15.15: new_moves
				blank(lines);
				head(lines, "projecthero.guide.carnage." + section);
				para(lines, "projecthero.guide.carnage." + section + ".body");
			}
		}));
		// v0.15.1: the Sentinel Purge
		out.add(chapter("projecthero.guide.sentinel_purge", lines -> {
			para(lines, "projecthero.guide.sentinel_purge.body");
			for (String section : new String[]{"trigger", "waves", "drone", "sentinel", "master_mold", "rewards", "commands"}) {
				blank(lines);
				head(lines, "projecthero.guide.sentinel_purge." + section);
				para(lines, "projecthero.guide.sentinel_purge." + section + ".body");
			}
		}));
		// v0.15.12: the Ultron Uprising
		out.add(chapter("projecthero.guide.ultron", lines -> {
			para(lines, "projecthero.guide.ultron.body");
			for (String section : new String[]{"trigger", "arena", "pylons", "waves", "lancer", "hijacker", "prime", "sentry", "weakness",
					"rewards", "commands"}) { // v0.16.1: + the Lancer and the Hijacker
				blank(lines);
				head(lines, "projecthero.guide.ultron." + section);
				para(lines, "projecthero.guide.ultron." + section + ".body");
			}
		}));
		// v0.15.13: Nova (Richard Rider)
		out.add(chapter("projecthero.guide.nova", lines -> {
			lines.add(Component.translatable("projecthero.guide.nova.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.nova.body");
			blank(lines);
			for (String section : new String[]{"origin", "suit", "force", "flight", "passives"}) {
				head(lines, "projecthero.guide.nova." + section);
				para(lines, "projecthero.guide.nova." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.nova.controls");
			for (String[] row : new String[][] { { "R", "nova_blast" }, { "Shift+R", "bolt_volley" }, { "G", "gravimetric_pulse" },
					{ "Shift+G", "gravity_slam" }, { "Z", "force_shield" }, { "Shift+Z", "nova_overload" }, { "X", "comet_dash" },
					{ "Shift+X", "orbital_launch" }, { "C", "gravity_well" }, { "Shift+C", "gravity_lock" }, { "V", "worldmind_scan" },
					{ "Shift+V", "force_transfer" }, { "Shift+hold N", "remove_helmet" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.nova.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.nova.ability." + row[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.nova.commands");
			para(lines, "projecthero.guide.nova.commands.body");
		}));
		// v0.16.1: the Frost Giant Assault
		out.add(frostGiantsChapter());
		// v0.16.1: the Ultron Contagion
		out.add(chapter("projecthero.guide.ultron_contagion", lines -> {
			para(lines, "projecthero.guide.ultron_contagion.body");
			for (String section : new String[]{"start", "saturation", "nexus", "corruption", "hijack", "capture", "items", "finale", "omega",
					"vision", "rewards", "resurgence", "commands"}) {
				blank(lines);
				head(lines, "projecthero.guide.ultron_contagion." + section);
				para(lines, "projecthero.guide.ultron_contagion." + section + ".body");
			}
		}));
		// v0.16.1: Galactus: The Devourer (CH_GALACTUS)
		out.add(chapter("projecthero.guide.galactus", lines -> {
			para(lines, "projecthero.guide.galactus.body");
			for (String section : new String[]{"ladder", "start", "arena", "herald", "worldship", "consumption", "galactus", "moves", "punishers",
					"nullifier", "rewards", "commands"}) {
				blank(lines);
				head(lines, "projecthero.guide.galactus." + section);
				para(lines, "projecthero.guide.galactus." + section + ".body");
			}
		}));
		// v0.16.2: street crime (CH_CRIME)
		out.add(chapter("projecthero.guide.crime", lines -> {
			para(lines, "projecthero.guide.crime.body");
			for (String section : new String[]{"when", "alerts", "mugging", "break_in", "getaway", "arson", "rewards", "commands"}) {
				blank(lines);
				head(lines, "projecthero.guide.crime." + section);
				para(lines, "projecthero.guide.crime." + section + ".body");
			}
		}));
		// v0.16.2: Cities (CH_CITY) -- City part A: the structure, its rings and buildings, how to find one
		out.add(chapter("projecthero.guide.city", lines -> {
			para(lines, "projecthero.guide.city.body");
			for (String section : new String[]{"finding", "rings", "downtown", "town", "village", "villagers"}) {
				blank(lines);
				head(lines, "projecthero.guide.city." + section);
				para(lines, "projecthero.guide.city." + section + ".body");
			}
		}));
		// v0.16.2 City part B: the City's services (CH_CITY_SERVICES)
		out.add(chapter("projecthero.guide.city_services", lines -> {
			para(lines, "projecthero.guide.city_services.body");
			for (String section : new String[]{"repair", "repair_rules", "food_bank", "cops", "doctor", "firefighter", "bank", "bulletin", "commands"}) {
				blank(lines);
				head(lines, "projecthero.guide.city_services." + section);
				para(lines, "projecthero.guide.city_services." + section + ".body");
			}
		}));
		// v0.16.5: Ghost Rider (CH_GHOST_RIDER)
		out.add(chapter("projecthero.guide.ghost_rider", lines -> {
			lines.add(Component.translatable("projecthero.guide.ghost_rider.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.ghost_rider.body");
			blank(lines);
			for (String section : new String[]{"origin", "change", "hellfire", "vengeance", "cycle", "passives", "rules"}) {
				head(lines, "projecthero.guide.ghost_rider." + section);
				para(lines, "projecthero.guide.ghost_rider." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.ghost_rider.controls");
			for (String[] row : new String[][] { { "R", "hellfire_blast" }, { "Shift+R", "hellfire_wave" }, { "G", "chain_whip" },
					{ "Shift+G", "chain_spin" }, { "X", "hell_cycle" }, { "X (riding)", "cycle_jump" }, { "Z", "judgement_mark" },
					{ "Shift+Z", "penance_stare" }, { "V", "hellfire_rush" }, { "Shift+V", "hellfire_eruption" }, { "C", "soul_shield" },
					{ "Shift+C", "riders_wrath" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.ghost_rider.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.ghost_rider.ability." + row[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.ghost_rider.commands");
			para(lines, "projecthero.guide.ghost_rider.commands.body");
		}));
		// v0.16.5: Cyborg (CH_CYBORG)
		out.add(chapter("projecthero.guide.cyborg", lines -> {
			lines.add(Component.translatable("projecthero.guide.cyborg.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.cyborg.body");
			blank(lines);
			for (String section : new String[]{"origin", "forms", "core", "passives"}) {
				head(lines, "projecthero.guide.cyborg." + section);
				para(lines, "projecthero.guide.cyborg." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.cyborg.controls");
			for (String[] row : new String[][] { { "R", "sonic_cannon" }, { "Shift+R", "sonic_burst" }, { "G", "hydraulic_punch" },
					{ "Shift+G", "arm_saw" }, { "X", "jet_dash" }, { "Shift+X", "grapple_claw" }, { "Z", "missile_barrage" },
					{ "Shift+Z", "scan" }, { "V", "circuit_link" }, { "Shift+V", "boom_tube" }, { "Space x2", "hover" }, { "C", "defense_matrix" },
					{ "Shift+C", "self_repair" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.cyborg.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.cyborg.ability." + row[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.cyborg.commands");
			para(lines, "projecthero.guide.cyborg.commands.body");
		}));
		// v0.16.5: Blue Beetle (CH_BLUE_BEETLE)
		out.add(chapter("projecthero.guide.blue_beetle", lines -> {
			lines.add(Component.translatable("projecthero.guide.blue_beetle.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.blue_beetle.body");
			blank(lines);
			for (String section : new String[]{"origin", "suit", "energy", "adaptation", "khaji_da", "passives"}) {
				head(lines, "projecthero.guide.blue_beetle." + section);
				para(lines, "projecthero.guide.blue_beetle." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.blue_beetle.controls");
			for (String[] row : new String[][] { { "R", "plasma_blaster" }, { "Shift+R", "mega_cannon" }, { "G", "scarab_hammer" },
					{ "Shift+G", "scarab_grab" }, { "X", "scarab_swarm" }, { "Shift+X", "bubble_prison" }, { "Z", "plasma_spikes" },
					{ "Shift+Z", "shield_pulse" }, { "V", "energy_shield" }, { "Shift+V", "camouflage" }, { "C", "energy_blade" },
					{ "Shift+C", "khaji_da_unleashed" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.blue_beetle.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.blue_beetle.ability." + row[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.blue_beetle.commands");
			para(lines, "projecthero.guide.blue_beetle.commands.body");
		}));
		// v0.16.6: the Miraculous -- Ladybug, Cat Noir, Rena Rouge (CH_LADYBUG, CH_CAT_NOIR, CH_RENA_ROUGE)
		out.add(miraculous(com.projecthero.mod.miraculous.Kwami.TIKKI));
		out.add(miraculous(com.projecthero.mod.miraculous.Kwami.PLAGG));
		out.add(miraculous(com.projecthero.mod.miraculous.Kwami.TRIXX));
		out.add(miraculous(com.projecthero.mod.miraculous.Kwami.NOOROO)); // CH_HAWK_MOTH
		out.add(miraculous(com.projecthero.mod.miraculous.Kwami.WAYZZ)); // CH_CARAPACE
		out.add(miraculous(com.projecthero.mod.miraculous.Kwami.KAALKI)); // CH_PEGASUS
		// v0.16.7: the Quirks -- the overview (CH_QUIRKS), then one chapter per Quirk (CH_QUIRK_BASE + ordinal)
		out.add(quirksOverview());
		for (com.projecthero.mod.quirk.Quirk q : com.projecthero.mod.quirk.Quirk.kitQuirks()) {
			out.add(quirk(q));
		}
		out.add(miraculous(com.projecthero.mod.miraculous.Kwami.SASS)); // CH_VIPERION (v0.16.7)
		out.add(falcon()); // CH_FALCON (v0.17.1)
		// v0.17.1: the Silver Surfer (CH_SILVER_SURFER)
		out.add(chapter("projecthero.guide.silver_surfer", lines -> {
			lines.add(Component.translatable("projecthero.guide.silver_surfer.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.silver_surfer.body");
			blank(lines);
			for (String section : new String[]{"origin", "cosmic", "board", "passives"}) {
				head(lines, "projecthero.guide.silver_surfer." + section);
				para(lines, "projecthero.guide.silver_surfer." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.silver_surfer.controls");
			for (String[] row : new String[][] { { "Space x2", "surf" }, { "R", "cosmic_blast" }, { "Shift+R", "cosmic_barrage" },
					{ "G", "board_strike" }, { "Shift+G", "transmutation" }, { "X", "cosmic_dash" }, { "Shift+X", "light_speed" },
					{ "Z", "cosmic_barrier" }, { "Shift+Z", "molecular_phase" }, { "V", "cosmic_awareness" }, { "Shift+V", "healing_touch" },
					{ "C", "cosmic_nova" }, { "Shift+C", "meteor_storm" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.silver_surfer.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.silver_surfer.ability." + row[1] + ".desc");
			}
		}));
		// v0.17.1: the Speed Force Speedster (CH_SPEED_FORCE)
		out.add(chapter("projecthero.guide.speed_force_speedster", lines -> {
			lines.add(Component.translatable("projecthero.guide.speed_force_speedster.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.speed_force_speedster.body");
			blank(lines);
			// v0.17.1: + the Speed Force (the dimension) and the drain after it
			for (String section : new String[]{"awakening", "tachyon", "momentum", "suit", "meter", "passives", "realm", "drain", "blackflash"}) { // v0.17.1: + the Black Flash
				head(lines, "projecthero.guide.speed_force_speedster." + section);
				para(lines, "projecthero.guide.speed_force_speedster." + section + ".body");
				blank(lines);
			}
			// v0.17.1 (user): every key is tap / Shift / HOLD -- the plain Speedster's moves on tap and Shift, the Speed Force's held
			head(lines, "projecthero.guide.speed_force_speedster.controls");
			para(lines, "projecthero.guide.speed_force_speedster.controls.body");
			for (String[] row : new String[][] { { "R", "rapid_assault" }, { "Shift+R", "mach_punch" }, { "Hold R", "infinite_mass_punch" },
					{ "G", "blitz" }, { "Shift+G", "speed_tornado" }, { "Hold G", "lightning_throw" },
					{ "X", "momentum_dash" }, { "Shift+X", "speed_sweep" }, { "Hold X", "tornado_arms" },
					{ "Hold Z", "flash_time" }, { "Hold Z at max", "speed_force_portal" }, { "Shift+Z", "time_remnant" },
					{ "V", "afterimage_decoys" }, { "Shift+V", "speed_heal" }, { "Hold V", "rewind" },
					{ "C", "speed_mode" }, { "Hold Shift+C", "phase" }, { "Hold C", "speed_launch" },
					{ "N", "rescue_run" }, { "Shift+N", "speed_force_menu" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.speed_force_speedster.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.speed_force_speedster.ability." + row[1] + ".desc");
			}
		}));

		out.add(miraculous(com.projecthero.mod.miraculous.Kwami.MULLO)); // CH_MULTIMOUSE (v0.17.1)

		// one chapter per ENABLED power (v0.14.8), in registration order (CHAPTER_POWER_BASE + i)
		for (Power power : Powers.enabled()) {
			out.add(powerChapter(power));
		}
		return out;
	}

	// ---- v0.16.7: the remade guidebook -- categories, icons and search ----

	/**
	 * v0.16.7 (user: "more sub menus like marvel, dc, quirks, miraculous"): the home page's tiles. Every chapter sits in
	 * exactly one category ({@link #categoryEntries}); {@link #index()} is all of them in this order.
	 *
	 * @param colour the tile's accent colour (ARGB)
	 * @param icons  item ids for the tile's icon, the first one that exists is used (client side)
	 */
	public enum Category {
		GETTING_STARTED("getting_started", 0xFFE8C66A, "projecthero:guidebook"),
		MARVEL("marvel", 0xFFE23A3A, "projecthero:iron_man_mark_3_helmet", "projecthero:vibranium_shield"),
		DC("dc", 0xFF3A8CFF, "projecthero:power_ring", "projecthero:kryptonian_crystal"),
		QUIRKS("quirks", 0xFF4FD08A, "projecthero:quirk_serum_random", "projecthero:one_for_all_vestige"),
		MIRACULOUS("miraculous", 0xFFFF5A8C, "projecthero:miraculous_box", "projecthero:ladybug_earrings"),
		OTHER("other", 0xFFB07AFF, "projecthero:mutagenic_serum", "projecthero:prismatic_serum"),
		EVENTS("events", 0xFFFF9A3A, "projecthero:boss_trophy", "projecthero:supervillain_token"),
		WORLD("world", 0xFF8FD3C7, "projecthero:stark_fabricator", "minecraft:crafting_table");

		public final String id;
		public final int colour;
		public final String[] icons;

		Category(String id, int colour, String... icons) {
			this.id = id;
			this.colour = colour;
			this.icons = icons;
		}

		public String nameKey() {
			return "projecthero.guide.category." + id;
		}

		public String descKey() {
			return "projecthero.guide.category." + id + ".desc";
		}
	}

	private static java.util.Map<Category, List<IndexEntry>> categoryEntries;
	private static int[] categoryOfChapter;

	/** One category's page: sub-headings (depth 1, not clickable) and chapter links (depth 1, or 2 under a sub-heading). */
	public static List<IndexEntry> categoryEntries(Category category) {
		buildCategories();
		return categoryEntries.get(category);
	}

	/** How many chapters a category links. */
	public static int chapterCount(Category category) {
		int n = 0;
		for (IndexEntry e : categoryEntries(category)) {
			if (!e.isHeading()) {
				n++;
			}
		}
		return n;
	}

	/** The category a chapter is filed under (breadcrumbs, colours, the search results' dot). */
	public static Category categoryOf(int chapterIndex) {
		buildCategories();
		if (chapterIndex < 0 || chapterIndex >= categoryOfChapter.length || categoryOfChapter[chapterIndex] < 0) {
			return Category.GETTING_STARTED;
		}
		return Category.values()[categoryOfChapter[chapterIndex]];
	}

	/** The chapters of the category {@code chapterIndex} is in, in page order (the chapter page's "Next" link). */
	public static List<Integer> chaptersIn(Category category) {
		List<Integer> out = new ArrayList<>();
		for (IndexEntry e : categoryEntries(category)) {
			if (!e.isHeading()) {
				out.add(e.chapterIndex());
			}
		}
		return out;
	}

	/** v0.16.7: the chapter's list / header icon (an item id; the client falls back to the category's icon). */
	public static String iconOf(int chapterIndex) {
		buildCategories();
		String id = CHAPTER_ICONS.get(chapterIndex);
		if (id != null) {
			return id;
		}
		if (chapterIndex >= CH_QUIRK_BASE && chapterIndex < CHAPTER_POWER_BASE) {
			return "projecthero:quirk_serum_" + com.projecthero.mod.quirk.Quirk.values()[chapterIndex - CH_QUIRK_BASE].id;
		}
		return null;
	}

	/** The Quirk's own chapter (v0.16.7; the "Your Power" screen can show it). */
	public static Chapter quirkChapter(com.projecthero.mod.quirk.Quirk quirk) {
		if (quirk.standalone) {
			return chapters().get(CH_ALL_MIGHT); // v0.16.7: One For All's chapter is the All Might one
		}
		return chapters().get(CH_QUIRK_BASE + quirk.ordinal());
	}

	/** The Quirks overview chapter (v0.16.7). */
	public static Chapter quirksOverviewChapter() {
		return chapters().get(CH_QUIRKS);
	}

	private static final java.util.Map<Integer, String> CHAPTER_ICONS = new java.util.HashMap<>();

	static {
		icon(CH_OVERVIEW, "guidebook");
		icon(CH_SQUADS, "minecraft:shield");
		icon(CH_MUTATION, "mutagenic_serum");
		icon(CH_COMBOS, "prismatic_serum");
		icon(CH_STRUCTURES, "research_note");
		icon(CH_DEVICES, "overloaded_redstone_coil");
		icon(CH_STARK_SORTER, "stark_sorting_station");
		icon(CH_CITY, "daily_bulletin");
		icon(CH_CITY_SERVICES, "food_bank");
		icon(CH_THOR, "thor_armor_chestplate"); // the Mjolnir item renders tiny in a GUI slot
		icon(CH_IRON_MAN, "iron_man_mark_3_helmet");
		icon(CH_SPIDER_MAN, "spider_man_suit_helmet");
		icon(CH_PUNISHER, "punisher_pistol");
		icon(CH_SYMBIOTE, "symbiote_vial_filled");
		icon(CH_WOLVERINE, "adamantium_serum");
		icon(CH_HULK, "gamma_serum");
		icon(CH_MOON_KNIGHT, "moon_knight_helmet");
		icon(CH_SUPER_SOLDIER, "vibranium_shield");
		icon(CH_NOVA, "nova_corps_helmet");
		icon(CH_GHOST_RIDER, "mephisto_contract");
		icon(CH_GREEN_LANTERN, "power_ring");
		icon(CH_KRYPTONIAN, "kryptonian_crystal");
		icon(CH_CYBORG, "basic_circuit");
		icon(CH_FALCON, "falcon_wing_pack"); // v0.17.1
		icon(CH_BLUE_BEETLE, "scarab");
		icon(CH_ALL_MIGHT, "one_for_all_vestige");
		icon(CH_QUIRKS, "quirk_serum_random");
		icon(CH_LADYBUG, "ladybug_earrings");
		icon(CH_CAT_NOIR, "cat_ring");
		icon(CH_RENA_ROUGE, "fox_necklace");
		icon(CH_HAWK_MOTH, "butterfly_brooch");
		icon(CH_CARAPACE, "turtle_bracelet");
		icon(CH_PEGASUS, "horseshoe_glasses");
		icon(CH_VIPERION, "snake_bracelet");
		icon(CH_MULTIMOUSE, "mouse_pendant"); // v0.17.1
		icon(CH_SILVER_SURFER, "silver_surfer_board"); // v0.17.1
		icon(CH_SPEED_FORCE, "tachyon_device"); // v0.17.1
		icon(CH_MAX_STEEL, "max_steel_suit_helmet");
		icon(CH_TITAN_SHIFTER, "titan_serum");
		icon(CH_ZOMBIE_RAID, "zombie_horde");
		icon(CH_SUPERVILLAIN_RAID, "supervillain_token");
		icon(CH_TITAN, "boss_trophy");
		icon(CH_ABYSSAL_BEHEMOTH, "abyssal_core");
		icon(CH_OATHBREAKER, "broken_oath");
		icon(CH_DARKSEID_RAID, "mother_box");
		icon(CH_HORDES, "skeleton_horde");
		icon(CH_SYNDICATE, "kingpin_cane_icon");
		icon(CH_CARNAGE, "crimson_biomass");
		icon(CH_SENTINEL_PURGE, "trask_signal");
		icon(CH_ULTRON, "ultron_core");
		icon(CH_FROST_GIANTS, "casket_of_ancient_winters");
		icon(CH_ULTRON_CONTAGION, "override_spike");
		icon(CH_GALACTUS, "ultimate_nullifier_icon");
		icon(CH_CRIME, "police_scanner");
	}

	private static void icon(int chapter, String item) {
		CHAPTER_ICONS.put(chapter, item.contains(":") ? item : "projecthero:" + item);
	}

	private static void buildCategories() {
		if (categoryEntries != null) {
			return;
		}
		chapters(); // make sure the flat list exists
		java.util.Map<Category, List<IndexEntry>> map = new java.util.EnumMap<>(Category.class);
		for (Category c : Category.values()) {
			map.put(c, new ArrayList<>());
		}

		List<IndexEntry> start = map.get(Category.GETTING_STARTED);
		link(start, "projecthero.guide.overview", CH_OVERVIEW);
		link(start, "projecthero.guide.squads", CH_SQUADS);

		List<IndexEntry> marvel = map.get(Category.MARVEL);
		link(marvel, "projecthero.guide.thor", CH_THOR);
		link(marvel, "projecthero.guide.iron_man", CH_IRON_MAN);
		link(marvel, "projecthero.guide.spider_man", CH_SPIDER_MAN);
		link(marvel, "projecthero.guide.hulk", CH_HULK);
		link(marvel, "projecthero.guide.super_soldier", CH_SUPER_SOLDIER);
		link(marvel, "projecthero.guide.wolverine", CH_WOLVERINE);
		link(marvel, "projecthero.guide.punisher", CH_PUNISHER);
		link(marvel, "projecthero.guide.symbiote", CH_SYMBIOTE);
		link(marvel, "projecthero.guide.moon_knight", CH_MOON_KNIGHT);
		link(marvel, "projecthero.guide.ghost_rider", CH_GHOST_RIDER);
		link(marvel, "projecthero.guide.nova", CH_NOVA);
		link(marvel, "projecthero.guide.falcon", CH_FALCON); // v0.17.1
		link(marvel, "projecthero.guide.silver_surfer", CH_SILVER_SURFER); // v0.17.1

		List<IndexEntry> dc = map.get(Category.DC);
		link(dc, "projecthero.guide.green_lantern", CH_GREEN_LANTERN);
		link(dc, "projecthero.guide.kryptonian", CH_KRYPTONIAN);
		link(dc, "projecthero.guide.cyborg", CH_CYBORG);
		link(dc, "projecthero.guide.blue_beetle", CH_BLUE_BEETLE);
		link(dc, "projecthero.guide.speed_force_speedster", CH_SPEED_FORCE); // v0.17.1
		// Hero-Tier powers that are still generated power chapters (Super Speed / the Flash, v0.14.13)
		for (int i = 0; i < Powers.enabled().size(); i++) {
			if (Powers.isHeroTier(Powers.enabled().get(i))) {
				dc.add(new IndexEntry(Component.translatable(Powers.enabled().get(i).nameKey()), 1, CHAPTER_POWER_BASE + i));
			}
		}

		List<IndexEntry> quirks = map.get(Category.QUIRKS);
		link(quirks, "projecthero.guide.quirks", CH_QUIRKS);
		sub(quirks, "projecthero.guide.sub.quirks");
		// v0.16.7: One For All is listed with the other Quirks (its chapter is the All Might one)
		for (com.projecthero.mod.quirk.Quirk q : com.projecthero.mod.quirk.Quirk.values()) {
			quirks.add(new IndexEntry(Component.translatable("projecthero.guide.quirk." + q.id), 2,
					q.standalone ? CH_ALL_MIGHT : CH_QUIRK_BASE + q.ordinal()));
		}

		List<IndexEntry> miraculous = map.get(Category.MIRACULOUS);
		link(miraculous, "projecthero.guide.ladybug", CH_LADYBUG);
		link(miraculous, "projecthero.guide.cat_noir", CH_CAT_NOIR);
		link(miraculous, "projecthero.guide.rena_rouge", CH_RENA_ROUGE);
		link(miraculous, "projecthero.guide.hawk_moth", CH_HAWK_MOTH);
		link(miraculous, "projecthero.guide.carapace", CH_CARAPACE);
		link(miraculous, "projecthero.guide.pegasus", CH_PEGASUS);
		link(miraculous, "projecthero.guide.viperion", CH_VIPERION);
		link(miraculous, "projecthero.guide.multimouse", CH_MULTIMOUSE); // v0.17.1

		List<IndexEntry> other = map.get(Category.OTHER);
		sub(other, "projecthero.guide.sub.other_heroes");
		other.add(new IndexEntry(Component.translatable("projecthero.guide.max_steel"), 2, CH_MAX_STEEL));
		other.add(new IndexEntry(Component.translatable("projecthero.guide.titan_shifter"), 2, CH_TITAN_SHIFTER));
		sub(other, "projecthero.guide.sub.mutations");
		other.add(new IndexEntry(Component.translatable("projecthero.guide.mutation"), 2, CH_MUTATION));
		other.add(new IndexEntry(Component.translatable("projecthero.guide.combos"), 2, CH_COMBOS));
		List<Power> powers = new ArrayList<>(Powers.enabled());
		for (PowerCategory category : PowerCategory.values()) {
			boolean started = false;
			for (int i = 0; i < powers.size(); i++) {
				if (powers.get(i).category() != category || Powers.isHeroTier(powers.get(i))) {
					continue;
				}
				if (!started) {
					other.add(new IndexEntry(Component.translatable(category.translationKey()), 1, -1));
					started = true;
				}
				other.add(new IndexEntry(Component.translatable(powers.get(i).nameKey()), 2, CHAPTER_POWER_BASE + i));
			}
		}

		List<IndexEntry> events = map.get(Category.EVENTS);
		sub(events, "projecthero.guide.sub.raids");
		for (int[] row : new int[][] { { CH_ZOMBIE_RAID }, { CH_SUPERVILLAIN_RAID }, { CH_DARKSEID_RAID }, { CH_HORDES }, { CH_SYNDICATE },
				{ CH_SENTINEL_PURGE }, { CH_ULTRON }, { CH_ULTRON_CONTAGION }, { CH_FROST_GIANTS }, { CH_GALACTUS }, { CH_CRIME } }) {
			events.add(new IndexEntry(chapters().get(row[0]).title(), 2, row[0]));
		}
		sub(events, "projecthero.guide.sub.bosses");
		for (int ch : new int[] { CH_TITAN, CH_ABYSSAL_BEHEMOTH, CH_OATHBREAKER, CH_CARNAGE }) {
			events.add(new IndexEntry(chapters().get(ch).title(), 2, ch));
		}

		List<IndexEntry> world = map.get(Category.WORLD);
		link(world, "projecthero.guide.structures", CH_STRUCTURES);
		link(world, "projecthero.guide.devices", CH_DEVICES);
		link(world, "projecthero.guide.stark_sorter", CH_STARK_SORTER);
		link(world, "projecthero.guide.city", CH_CITY);
		link(world, "projecthero.guide.city_services", CH_CITY_SERVICES);

		int[] of = new int[chapters().size()];
		java.util.Arrays.fill(of, -1);
		for (Category c : Category.values()) {
			for (IndexEntry e : map.get(c)) {
				if (!e.isHeading() && e.chapterIndex() < of.length) {
					of[e.chapterIndex()] = c.ordinal();
				}
			}
		}
		categoryOfChapter = of;
		categoryEntries = map;
	}

	private static List<IndexEntry> buildIndex() {
		List<IndexEntry> idx = new ArrayList<>();
		boolean first = true;
		for (Category c : Category.values()) {
			section(idx, c.nameKey(), !first);
			first = false;
			idx.addAll(categoryEntries(c));
		}
		return idx;
	}

	private static void section(List<IndexEntry> idx, String key, boolean spacerBefore) {
		if (spacerBefore) {
			idx.add(new IndexEntry(Component.empty(), 0, -1));
		}
		idx.add(new IndexEntry(Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 0, -1));
	}

	private static void sub(List<IndexEntry> idx, String key) {
		idx.add(new IndexEntry(Component.translatable(key), 1, -1));
	}

	private static void link(List<IndexEntry> idx, String titleKey, int chapterIndex) {
		idx.add(new IndexEntry(Component.translatable(titleKey), 1, chapterIndex));
	}

	// ---- v0.16.7: search ----

	/**
	 * One search result.
	 *
	 * @param chapter    the chapter it is in
	 * @param line       the line of {@link Chapter#lines()} that matched, or -1 for a match in the chapter's title
	 * @param section    the section heading the line sits under (or the chapter title), for the result's second label
	 * @param text       the matched text: the whole line (the screen cuts a window around the match)
	 * @param matchStart where the match starts in {@code text}
	 * @param matchLength the query's length
	 */
	public record SearchHit(int chapter, int line, String section, String text, int matchStart, int matchLength) {
	}

	/** At most this many results (the list is sorted best first). */
	public static final int MAX_HITS = 200;
	/** At most this many body-text hits from one chapter, so one huge chapter doesn't bury the rest. */
	private static final int MAX_HITS_PER_CHAPTER = 6;

	private static Object searchLanguage;
	private static List<String[]> searchText;

	/**
	 * v0.16.7 (user: "add a search bar"): case-insensitive search across every chapter's title, section headings and
	 * body text. Title matches come first, then section headings, then body text, each in book order.
	 */
	public static List<SearchHit> search(String query) {
		List<SearchHit> titles = new ArrayList<>();
		List<SearchHit> heads = new ArrayList<>();
		List<SearchHit> body = new ArrayList<>();
		String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
		if (q.isEmpty()) {
			return titles;
		}
		List<Chapter> all = chapters();
		List<String[]> text = searchText();
		for (int c = 0; c < all.size(); c++) {
			Chapter ch = all.get(c);
			String title = ch.title().getString();
			int t = title.toLowerCase(java.util.Locale.ROOT).indexOf(q);
			if (t >= 0) {
				titles.add(new SearchHit(c, -1, title, title, t, q.length()));
			}
			String[] lines = text.get(c);
			String section = title;
			int bodyHits = 0;
			for (int i = 0; i < lines.length; i++) {
				boolean isHead = isSectionLine(ch.lines().get(i));
				String plain = ch.lines().get(i).getString();
				if (isHead && !plain.isBlank()) {
					section = plain;
				}
				int at = lines[i].indexOf(q);
				if (at < 0) {
					continue;
				}
				if (isHead) {
					heads.add(new SearchHit(c, i, section, plain, at, q.length()));
				} else if (bodyHits < MAX_HITS_PER_CHAPTER) {
					body.add(new SearchHit(c, i, section, plain, at, q.length()));
					bodyHits++;
				}
			}
		}
		titles.addAll(heads);
		titles.addAll(body);
		return titles.size() > MAX_HITS ? new ArrayList<>(titles.subList(0, MAX_HITS)) : titles;
	}

	/** A section heading line: drawn bold by {@link #head} (and the bold unit / traversal titles). */
	public static boolean isSectionLine(Component line) {
		if (line.getStyle().isBold()) {
			return true;
		}
		for (Component sib : line.getSiblings()) {
			if (sib.getStyle().isBold()) {
				return true;
			}
		}
		return false;
	}

	/** Every chapter's lines, lower-cased, cached until the language changes. */
	private static List<String[]> searchText() {
		Object lang = net.minecraft.locale.Language.getInstance();
		if (searchText == null || searchLanguage != lang) {
			List<String[]> out = new ArrayList<>();
			for (Chapter ch : chapters()) {
				String[] lines = new String[ch.lines().size()];
				for (int i = 0; i < lines.length; i++) {
					lines[i] = ch.lines().get(i).getString().toLowerCase(java.util.Locale.ROOT);
				}
				out.add(lines);
			}
			searchText = out;
			searchLanguage = lang;
		}
		return searchText;
	}

	// ---- v0.16.7: the Quirk chapters ----

	/** The Quirks overview: how a Quirk is gained, the serums, the meter, the moves, the craftable suit and the Erasure rule. */
	private static Chapter quirksOverview() {
		return chapter("projecthero.guide.quirks", lines -> {
			lines.add(Component.translatable("projecthero.guide.quirks.tier").withStyle(ChatFormatting.GREEN));
			para(lines, "projecthero.guide.quirks.body");
			blank(lines);
			for (String s : new String[] { "get", "serums", "meter", "controls", "suit", "erasure" }) {
				head(lines, "projecthero.guide.quirks." + s);
				para(lines, "projecthero.guide.quirks." + s + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.quirks.list");
			for (com.projecthero.mod.quirk.Quirk q : com.projecthero.mod.quirk.Quirk.values()) {
				lines.add(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.translatable("projecthero.quirk." + q.id).withStyle(s -> s.withColor(q.meterColour & 0xFFFFFF)))
						.append(Component.literal("  "))
						.append(Component.translatable("projecthero.quirk." + q.id + ".hero").withStyle(ChatFormatting.GRAY)));
			}
			blank(lines);
			para(lines, "projecthero.guide.quirks.one_for_all");
		});
	}

	/**
	 * v0.16.7: one Quirk's chapter, built like the Miraculous ones: overview, how to get it (with its serum's name),
	 * its meter and overload, its quirk-specific rules, the 12-move table from the registered kit ({@code
	 * QuirkKits.of(q).idsOf(SLOT_1..SLOT_6)}, names and descriptions from {@code projecthero.quirk.ability.<id>}), the
	 * craftable suit (its four pieces and recipe) and the Erased rule.
	 */
	private static Chapter quirk(com.projecthero.mod.quirk.Quirk q) {
		String k = "projecthero.guide.quirk." + q.id;
		return chapter(k, lines -> {
			lines.add(Component.translatable(k + ".tier").withStyle(s -> s.withColor(q.meterColour & 0xFFFFFF)));
			para(lines, k + ".body");
			blank(lines);
			head(lines, "projecthero.guide.quirks.get");
			para(lines, "projecthero.guide.quirks.get.short");
			lines.add(Component.translatable("projecthero.guide.quirks.serum",
					Component.translatableWithFallback("item.projecthero.quirk_serum_" + q.id, "Quirk Serum (" + q.quirkName + ")"))
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			blank(lines);
			lines.add(Component.translatable("projecthero.guide.quirks.meter_of", Component.translatable("projecthero.quirk.meter." + q.meterId))
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			para(lines, k + ".meter.body");
			blank(lines);
			head(lines, k + ".overload");
			para(lines, k + ".overload.body");
			blank(lines);
			head(lines, k + ".passive");
			para(lines, k + ".passive.body");
			blank(lines);
			head(lines, "projecthero.guide.quirks.moves");
			com.projecthero.mod.quirk.QuirkKit kit = com.projecthero.mod.quirk.QuirkKits.of(q);
			AbilitySlot[] slots = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
					AbilitySlot.SLOT_6 };
			int rows = 0;
			for (int i = 0; i < slots.length; i++) {
				String[] ids = kit.idsOf(slots[i]);
				for (int j = 0; j < Math.min(2, ids.length); j++) {
					net.minecraft.network.chat.MutableComponent row = Component.literal(" " + SLOT_LABELS[i][j] + "  ").withStyle(ChatFormatting.GOLD)
							.append(Component.translatable(kit.abilityLangKey(ids[j])).withStyle(ChatFormatting.WHITE));
					int cd = kit.maxCooldown(ids[j]);
					if (cd > 0) {
						row.append(Component.literal("  "))
								.append(Component.translatable("projecthero.guide.quirks.cooldown", seconds(cd)).withStyle(ChatFormatting.DARK_GRAY));
					}
					lines.add(row);
					para(lines, kit.abilityLangKey(ids[j]) + ".desc");
					rows++;
				}
			}
			if (rows == 0) {
				lines.add(Component.translatable("projecthero.guide.quirks.moves.none").withStyle(ChatFormatting.DARK_GRAY));
			}
			if (q == com.projecthero.mod.quirk.Quirk.DARK_SHADOW) {
				// v0.16.7 (user): N hides Dark Shadow
				lines.add(Component.literal(" N  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.quirk.ability.quirk_dark_shadow_recall").withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.quirk.dark_shadow.hide");
			}
			blank(lines);
			// v0.16.7 (user): the hero suit is craftable armour now (a Quirk never changes your look)
			head(lines, "projecthero.guide.quirks.suit");
			para(lines, "projecthero.guide.quirks.suit.short");
			com.projecthero.mod.quirk.item.QuirkSuits.Suit suit = com.projecthero.mod.quirk.item.QuirkSuits.Suit.of(q);
			for (net.minecraft.world.item.ArmorItem.Type t : suit.pieces()) { // v0.16.7: Ingenium has no helmet
				lines.add(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
						.append(com.projecthero.mod.quirk.item.QuirkSuits.piece(q, t).getDescription().copy().withStyle(ChatFormatting.WHITE)));
			}
			lines.add(Component.translatable("projecthero.guide.quirks.suit.recipe", net.minecraft.world.item.Items.IRON_INGOT.getDescription(),
					suit.main.getDescription(), suit.accent.getDescription()).withStyle(ChatFormatting.LIGHT_PURPLE));
			if (q == com.projecthero.mod.quirk.Quirk.DARK_SHADOW) {
				para(lines, "projecthero.guide.quirk.dark_shadow.bird");
			}
			if (q == com.projecthero.mod.quirk.Quirk.ENGINE) {
				para(lines, "projecthero.guide.quirk.engine.suit");
			}
			blank(lines);
			head(lines, "projecthero.guide.quirks.erased");
			para(lines, q == com.projecthero.mod.quirk.Quirk.ERASURE ? "projecthero.guide.quirks.erased.self" : "projecthero.guide.quirks.erased.body");
		});
	}

	/** Ticks as seconds, "12" or "1.5". */
	private static String seconds(int ticks) {
		return ticks % 20 == 0 ? String.valueOf(ticks / 20) : String.valueOf(Math.round(ticks / 2.0) / 10.0);
	}

	/**
	 * The single power's guide entry: category, flavour, all six abilities with descriptions, passive
	 * traits and (if any) the serum recipe / mutation trigger. Reused verbatim by the in-game
	 * "current power" info screen (opened with I) so the two never drift.
	 */
	public static Chapter powerChapter(Power power) {
		return powerChapter(power, -1);
	}

	/**
	 * Same entry. The second argument is vestigial (it used to carry Empowered-Zombie research
	 * progress, removed in v0.10.3); any value is ignored.
	 */
	public static Chapter powerChapter(Power power, int ignored) {
		return chapter(power.nameKey(), lines -> {
			lines.add(Component.translatable(power.category().translationKey()).withStyle(ChatFormatting.DARK_AQUA));
			para(lines, power.descKey());
			blank(lines);
			head(lines, "projecthero.guide.power.abilities");
			if (power.abilities().isEmpty()) {
				// v0.14.5: a passive-only power (Super Regeneration) has no keys at all
				lines.add(Component.translatable("projecthero.guide.power.no_abilities",
						Component.translatable(power.nameKey())).withStyle(ChatFormatting.GRAY));
			}
			for (AbilitySlot slot : AbilitySlot.values()) {
				Ability a = power.ability(slot);
				if (a == null) {
					continue;
				}
				lines.add(Component.literal(" " + slot.defaultKey() + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable(a.nameKey()).withStyle(ChatFormatting.WHITE)));
				para(lines, a.descKey());
			}
			blank(lines);
			head(lines, "projecthero.guide.power.passives");
			for (String pk : power.passiveKeys()) {
				lines.add(Component.literal(" • ").append(Component.translatable(pk)).withStyle(ChatFormatting.GRAY));
			}
			blank(lines);
			boolean heroTier = Powers.isHeroTier(power);
			if (heroTier) {
				// v0.14.13: no serum -- the Speed Force
				head(lines, "projecthero.guide.super_speed.origin");
				para(lines, "projecthero.guide.super_speed.origin.body");
			}
			if (power.serum() != null && !heroTier) {
				lines.add(Component.translatable("projecthero.guide.power.serum",
						Component.translatable(power.serum().resultName())).withStyle(ChatFormatting.LIGHT_PURPLE));
				lines.add(Component.translatable("projecthero.guide.power.base",
						Component.literal(power.serum().basePotion())).withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("projecthero.guide.power.additives",
						Component.literal(String.join(", ", power.serum().additives()))).withStyle(ChatFormatting.GRAY));
				if (power.serum().fuel() != null) {
					lines.add(Component.translatable("projecthero.guide.power.fuel",
							Component.literal(power.serum().fuel())).withStyle(ChatFormatting.GRAY));
				}
			}
			if (com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.KEY.equals(power.key())) {
				// v0.14.11: the Flash Suit and its ring
				blank(lines);
				head(lines, "projecthero.guide.flash_suit");
				para(lines, "projecthero.guide.flash_suit.body");
			}
			if (power.trigger() != null && !heroTier) {
				lines.add(Component.translatable("projecthero.guide.power.trigger",
						Component.translatable(power.trigger().descKey())).withStyle(ChatFormatting.YELLOW));
				if (power.trigger().labDeviceKey() != null) {
					lines.add(Component.translatable("projecthero.guide.power.device",
							Component.translatable(power.trigger().labDeviceKey())).withStyle(ChatFormatting.GRAY));
				}
			}
			if (!heroTier && com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.KEY.equals(power.key())) {
				// v0.14.21: Super Speed is a mutation again; the Speed Force stays as a second, serum-free way in
				blank(lines);
				head(lines, "projecthero.guide.super_speed.origin");
				para(lines, "projecthero.guide.super_speed.origin.body");
			}
		});
	}

	private static final String[] COMBO_KEYS = {
			"strength_flight", "speed_electrokinesis", "water_electrokinesis", "geokinesis_strength",
			"cryokinesis_water", "pyrokinesis_flight", "energy_absorption_laser",
			"wind_fire", "shadow_teleportation",
	};

	/** v0.16.1: the Frost Giant Assault chapter, every enemy described up front. */
	public static Chapter frostGiantsChapter() {
		return chapter("projecthero.guide.frost_giants", lines -> {
			para(lines, "projecthero.guide.frost_giants.body");
			for (String section : new String[] { "ladder", "trigger", "arena", "cold", "braziers", "waves", "laufey", "ymir", "casket",
					"weakness", "rewards", "gear", "thor", "commands" }) {
				blank(lines);
				head(lines, "projecthero.guide.frost_giants." + section);
				para(lines, "projecthero.guide.frost_giants." + section + ".body");
			}
			blank(lines);
			head(lines, "projecthero.guide.frost_giants.enemies");
			for (String id : new String[] { "rime_hound", "jotun_raider", "frost_shaman", "ice_brute", "frost_giant_warlord", "king_laufey" }) {
				blank(lines);
				lines.add(Component.translatable("entity.projecthero." + id).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
				para(lines, "projecthero.guide.frost_giants.unit." + id);
			}
		});
	}

	private interface Body {
		void fill(List<Component> lines);
	}

	private static Chapter chapter(String titleKey, Body body) {
		List<Component> lines = new ArrayList<>();
		body.fill(lines);
		return new Chapter(Component.translatable(titleKey), lines);
	}

	/** v0.16.7 (user): one line per Era I move -- key, name, description -- and the Shift+C eject. */
	private static void eraOneMoves(List<Component> lines, String suitId) {
		var kit = com.projecthero.mod.ironman.ability.IronManComicKit.kit(suitId);
		if (kit == null) {
			return;
		}
		String[] keys = { "R", "G", "X", "Z", "V", "C" };
		for (int i = 0; i < kit.keys().size() && i < keys.length; i++) {
			var key = kit.keys().get(i);
			eraOneMove(lines, keys[i], key.tap());
			if (key.shift() != null) {
				eraOneMove(lines, "Shift+" + keys[i], key.shift());
			}
		}
		lines.add(Component.literal("  Shift+C  ").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable("projecthero.guide.iron_man.era1_eject.line").withStyle(ChatFormatting.GRAY)));
	}

	private static void eraOneMove(List<Component> lines, String key, com.projecthero.mod.ironman.ability.IronManComicKit.Move m) {
		lines.add(Component.literal("  " + key + "  ").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable(m.nameKey()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" -- ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable(m.descKey()).withStyle(ChatFormatting.GRAY)));
	}

	private static void para(List<Component> lines, String key) {
		lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
	}

	private static void head(List<Component> lines, String key) {
		lines.add(Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
	}

	private static void blank(List<Component> lines) {
		lines.add(Component.empty());
	}
}
