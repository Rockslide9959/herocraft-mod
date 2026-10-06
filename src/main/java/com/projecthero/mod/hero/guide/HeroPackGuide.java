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

	public static Chapter symbioteChapter() {
		return chapters().get(CH_SYMBIOTE);
	}

	/**
	 * The "Your Power" (I) screen's entry for a plain Normal Symbiote Host -- bonded, but not also
	 * Spider-Man. Deliberately its own live-built chapter rather than the cached {@link #symbioteChapter()}
	 * (which is the shared book page and covers both host variants): a Normal Host has no web abilities
	 * and no sneak-modified "alt" extras, so their personal menu should show only their own six-ability
	 * kit, not the Black Suit Spider-Man material.
	 */
	public static Chapter symbioteNormalHostChapter() {
		return chapter("projecthero.guide.symbiote", lines -> {
			lines.add(Component.translatable("projecthero.guide.symbiote.tier").withStyle(ChatFormatting.LIGHT_PURPLE));
			para(lines, "projecthero.guide.symbiote.body");
			blank(lines);
			head(lines, "projecthero.guide.symbiote.controls");
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C"}) {
				String ability = switch (slot) {
					case "R" -> "tendril_strike"; case "G" -> "spike_shot"; case "X" -> "leap";
					case "Z" -> "barrage"; case "V" -> "blade"; default -> "spikes";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.symbiote.ability." + ability).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.symbiote.ability." + ability + ".desc");
			}
			lines.add(Component.literal(" Sneak+X  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.symbiote.ability.grapple").withStyle(ChatFormatting.WHITE)));
			para(lines, "projecthero.symbiote.ability.grapple.desc");
			for (String[] extra : new String[][]{{"Sneak+G", "spike_fan"}, {"Sneak+C", "tendril_grab"}}) {
				lines.add(Component.literal(" " + extra[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.symbiote.ability." + extra[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.symbiote.ability." + extra[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.symbiote.passives");
			lines.add(Component.literal(" • ").append(
					Component.translatable("projecthero.guide.symbiote.passive.biomass")).withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal(" • ").append(
					Component.translatable("projecthero.guide.symbiote.passive.recovery")).withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal(" • ").append(
					Component.translatable("projecthero.guide.symbiote.passive.bare_hands")).withStyle(ChatFormatting.GRAY));
			for (String p : new String[]{"protect", "resist", "growth", "cloak", "predator", "resurrect", "squad", "pet", "vial"}) {
				lines.add(Component.literal(" • ").append(
						Component.translatable("projecthero.guide.symbiote.passive." + p)).withStyle(ChatFormatting.GRAY));
			}
			blank(lines);
			head(lines, "projecthero.guide.symbiote.weaknesses");
			lines.add(Component.literal(" • ").append(
					Component.translatable("projecthero.guide.symbiote.weakness.sonic")).withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal(" • ").append(
					Component.translatable("projecthero.guide.symbiote.weakness.fire")).withStyle(ChatFormatting.GRAY));
		});
	}

	/**
	 * The "Your Power" info screen's entry for a player who is BOTH bonded with a Symbiote AND
	 * currently holds Spider-Man -- Black Suit Spider-Man. Deliberately not one of the cached
	 * {@link #chapters()} (the physical HeroPack Guide book has no single reader, so it can't show a
	 * combination that depends on what one specific player currently has); built fresh each time this
	 * is called instead, the same way {@code PowerInfoScreen} already builds its live training-progress
	 * page. Shows Spider-Man's REAL keybinds (Z/X/G/C/V/B, not the Normal host's tendril slots) plus the
	 * three sneak-modified Symbiote extras layered on top of them.
	 */
	public static Chapter symbioteSpiderManChapter() {
		return chapter("projecthero.guide.symbiote_spider_man", lines -> {
			lines.add(Component.translatable("projecthero.guide.symbiote_spider_man.tier").withStyle(ChatFormatting.LIGHT_PURPLE));
			para(lines, "projecthero.guide.symbiote_spider_man.body");
			blank(lines);
			head(lines, "projecthero.guide.spider_man.controls");
			for (String ability : new String[]{
					com.projecthero.mod.spider.SpiderAbilities.WEB_SWING,
					com.projecthero.mod.spider.SpiderAbilities.WEB_ZIP,
					com.projecthero.mod.spider.SpiderAbilities.WEB_YANK,
					com.projecthero.mod.spider.SpiderAbilities.WEB_SHOT,
					com.projecthero.mod.spider.SpiderAbilities.WEB_NET,
					com.projecthero.mod.spider.SpiderAbilities.WALL_CRAWL }) {
				lines.add(Component.literal(" " + com.projecthero.mod.spider.SpiderAbilities.slotKeyOf(ability) + "  ")
						.withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.spider_man.ability." + ability).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.spider_man.ability." + ability + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.symbiote.black_suit");
			String[] extras = {"symbiote_tendril_strike", "symbiote_crush", "symbiote_slam_enhanced"};
			String[] keyedOn = {com.projecthero.mod.spider.SpiderAbilities.WEB_YANK,
					com.projecthero.mod.spider.SpiderAbilities.WEB_SHOT,
					com.projecthero.mod.spider.SpiderAbilities.WALL_CRAWL};
			for (int i = 0; i < extras.length; i++) {
				lines.add(Component.literal(" Sneak+" + com.projecthero.mod.spider.SpiderAbilities.slotKeyOf(keyedOn[i]) + "  ")
						.withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.symbiote.ability." + extras[i]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.symbiote.ability." + extras[i] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.symbiote_spider_man.passives");
			// v0.13.21: the armour line is new; "resist" (the Normal host's -10% suit damage cut) never applied to him
			for (String p : new String[]{"armour", "melee", "speed", "jump", "knockback", "web_capacity", "recovery"}) {
				lines.add(Component.literal(" • ").append(
						Component.translatable("projecthero.guide.symbiote_spider_man.passive." + p)).withStyle(ChatFormatting.GRAY));
			}
			for (String p : new String[]{"protect", "growth", "cloak", "predator", "resurrect", "squad", "pet", "vial"}) {
				lines.add(Component.literal(" • ").append(
						Component.translatable("projecthero.guide.symbiote.passive." + p)).withStyle(ChatFormatting.GRAY));
			}
			blank(lines);
			head(lines, "projecthero.guide.symbiote.weaknesses");
			lines.add(Component.literal(" • ").append(
					Component.translatable("projecthero.guide.symbiote.weakness.sonic")).withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal(" • ").append(
					Component.translatable("projecthero.guide.symbiote.weakness.fire")).withStyle(ChatFormatting.GRAY));
		});
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
	private static final int CHAPTER_POWER_BASE = 30;

	private static List<Chapter> build() {
		List<Chapter> out = new ArrayList<>();

		out.add(chapter("projecthero.guide.overview", lines -> {
			para(lines, "projecthero.guide.overview.body");
			blank(lines);
			head(lines, "projecthero.guide.powerclass");
			para(lines, "projecthero.guide.powerclass.body");
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
			// v0.14.16: every flight in the mod steers the same way now
			blank(lines);
			head(lines, "projecthero.guide.flight");
			para(lines, "projecthero.guide.flight.body");
		}));

		out.add(chapter("projecthero.guide.mutation", lines -> {
			para(lines, "projecthero.guide.mutation.body");
			blank(lines);
			para(lines, "projecthero.guide.mutation.capacity");
			blank(lines);
			para(lines, "projecthero.guide.mutation.research");
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
			for (String perk : new String[]{"melee", "hearts", "speed", "damage_reduction", "regen", "hammer_damage", "hud", "squad"}) {
				lines.add(Component.literal("• ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.guide.thor.perk." + perk).withStyle(ChatFormatting.GRAY)));
			}
			blank(lines);
			head(lines, "projecthero.guide.thor.controls");
			for (String[] slot : new String[][]{
					{"R", "call_mjolnir"}, {"G", "lightning_strike"}, {"X", "lightning_beam"},
					{"Z", "god_of_thunders_wrath"}, {"V", "hammer_volley"}, {"Shift+V", "thunderclap"},
					{"C", "chain_lightning"},
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
			for (String part : new String[]{"intro", "recipe", "forge", "throw", "bifrost", "weapon"}) {
				para(lines, "projecthero.guide.thor.stormbreaker." + part);
			}
			// v0.14.20: the Mjolnir / Stormbreaker 3-hit melee combo
			blank(lines);
			head(lines, "projecthero.guide.thor.combo");
			para(lines, "projecthero.guide.thor.combo.body");
			para(lines, "projecthero.guide.thor.combo.finisher");
		}));

		out.add(chapter("projecthero.guide.iron_man", lines -> {
			lines.add(Component.translatable("projecthero.guide.iron_man.tier").withStyle(ChatFormatting.DARK_AQUA));
			para(lines, "projecthero.guide.iron_man.body");
			blank(lines);
			head(lines, "projecthero.guide.iron_man.progression");
			for (String step : new String[]{"arc_reactor", "tony_stark", "fabricator", "mark_iii", "mark_v",
					"mark_vii"}) {
				lines.add(Component.literal("  ↓  ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.translatable("projecthero.guide.iron_man.step." + step).withStyle(ChatFormatting.WHITE)));
			}
			blank(lines);
			para(lines, "projecthero.guide.iron_man.recipes");
			para(lines, "projecthero.guide.iron_man.controls");
			// v0.14.21: the animated suit-up / platform / pod / suitcase
			head(lines, "projecthero.guide.iron_man.suit_up");
			para(lines, "projecthero.guide.iron_man.suit_up.body");
			head(lines, "projecthero.guide.iron_man.screens"); // v0.14.21 UI redesign
			para(lines, "projecthero.guide.iron_man.screens.body");
			// v0.14.26: scanner, Mark III targeting, I-key spec sheet, auto-feed, the Stark cookers
			for (String section : new String[]{"scanner", "targeting", "spec_sheet", "auto_feed", "furnaces",
					"mark_1", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii", "platform", "combat", "jarvis",
					"remote_pilot", "armour_rules"}) { // v0.15.1: + armour rules; v0.14.29: + Mark 4 / 5 / 6 / 7, combat systems, JARVIS, remote pilot
				head(lines, "projecthero.guide.iron_man." + section);
				para(lines, "projecthero.guide.iron_man." + section + ".body");
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
			head(lines, "projecthero.guide.spider_man.controls");
			for (String ability : new String[]{
					com.projecthero.mod.spider.SpiderAbilities.WEB_SWING,
					com.projecthero.mod.spider.SpiderAbilities.WEB_ZIP,
					com.projecthero.mod.spider.SpiderAbilities.WEB_YANK,
					com.projecthero.mod.spider.SpiderAbilities.WEB_SHOT,
					com.projecthero.mod.spider.SpiderAbilities.WEB_NET,
					com.projecthero.mod.spider.SpiderAbilities.WALL_CRAWL }) {
				lines.add(Component.literal(" " + com.projecthero.mod.spider.SpiderAbilities.slotKeyOf(ability) + "  ")
						.withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.spider_man.ability." + ability).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.spider_man.ability." + ability + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.spider_man.combat");
			para(lines, "projecthero.guide.spider_man.combat.body");
			for (String c : new String[]{"r", "g", "x", "z", "v"}) {
				lines.add(Component.literal(" • ").append(
						Component.translatable("projecthero.guide.spider_man.combat." + c)).withStyle(ChatFormatting.GRAY));
			}
			blank(lines);
			head(lines, "projecthero.guide.spider_man.passives");
			for (String p : new String[]{"sense", "dodge", "arrows", "crawl", "ceiling", "wall_leap",
					"double_jump", "super_jump", "web_blossom", "strength", "agility", "fall", "reserve"}) {
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
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C"}) {
				String key = switch (slot) {
					case "R" -> "arsenal"; case "G" -> "grenade"; case "X" -> "roll";
					case "Z" -> "suppressive"; case "V" -> "adrenaline"; default -> "c4";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.guide.punisher.ability." + key).withStyle(ChatFormatting.WHITE)));
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
			head(lines, "projecthero.guide.green_lantern.controls");
			// v0.14.3: H (Giant Hand) and N (dismiss / take off the ring) joined the kit
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C", "H", "N"}) {
				String key = switch (slot) {
					case "R" -> "ring_bolt"; case "G" -> "construct_fist"; case "X" -> "oath";
					case "Z" -> "shield"; case "V" -> "suit"; case "H" -> "giant_hand"; case "N" -> "dismiss";
					default -> "construct";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.guide.green_lantern.ability." + key).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.green_lantern.ability." + key + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.constructs");
			para(lines, "projecthero.guide.green_lantern.constructs.body");
			blank(lines);
			head(lines, "projecthero.guide.green_lantern.battery");
			para(lines, "projecthero.guide.green_lantern.battery.body");
		}));

		// The Symbiote -- not a Hero Class of its own like the other four: any player can bond with it,
		// and which of the two host variants they get is derived live from whether they also hold
		// Spider-Man. Sits with the other Hero-Tier chapters because that is what pressing I shows.
		out.add(chapter("projecthero.guide.symbiote", lines -> {
			lines.add(Component.translatable("projecthero.guide.symbiote.tier").withStyle(ChatFormatting.LIGHT_PURPLE));
			para(lines, "projecthero.guide.symbiote.body");
			blank(lines);
			head(lines, "projecthero.guide.symbiote.progression");
			para(lines, "projecthero.guide.symbiote.step.find");
			para(lines, "projecthero.guide.symbiote.step.bond");
			blank(lines);
			head(lines, "projecthero.guide.symbiote.hosts");
			para(lines, "projecthero.guide.symbiote.host.normal");
			para(lines, "projecthero.guide.symbiote.host.spider_man");
			para(lines, "projecthero.guide.symbiote.host.agent_venom");
			blank(lines);
			// v0.14.4: infested animals and Symbiote Pets
			head(lines, "projecthero.guide.symbiote.creatures");
			para(lines, "projecthero.guide.symbiote.creatures.wild");
			para(lines, "projecthero.guide.symbiote.creatures.pet");
			para(lines, "projecthero.guide.symbiote.creatures.form"); // v0.14.4 pet hosts
			para(lines, "projecthero.guide.symbiote.creatures.powers");
			para(lines, "projecthero.guide.symbiote.creatures.release");
			blank(lines);
			head(lines, "projecthero.guide.symbiote.controls");
			para(lines, "projecthero.guide.symbiote.controls.body");
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C"}) {
				String ability = switch (slot) {
					case "R" -> "tendril_strike"; case "G" -> "spike_shot"; case "X" -> "leap";
					case "Z" -> "barrage"; case "V" -> "blade"; default -> "spikes";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.symbiote.ability." + ability).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.symbiote.ability." + ability + ".desc");
			}
			lines.add(Component.literal(" Sneak+X  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.symbiote.ability.grapple").withStyle(ChatFormatting.WHITE)));
			para(lines, "projecthero.symbiote.ability.grapple.desc");
			for (String[] extra : new String[][]{{"Sneak+G", "spike_fan"}, {"Sneak+C", "tendril_grab"}}) {
				lines.add(Component.literal(" " + extra[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.symbiote.ability." + extra[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.symbiote.ability." + extra[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.symbiote.black_suit");
			para(lines, "projecthero.guide.symbiote.black_suit.body");
			blank(lines);
			head(lines, "projecthero.guide.symbiote.passives");
			para(lines, "projecthero.guide.symbiote.spider_passives");
			blank(lines);
			head(lines, "projecthero.guide.symbiote.weaknesses");
			lines.add(Component.literal(" • ").append(
					Component.translatable("projecthero.guide.symbiote.weakness.sonic")).withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal(" • ").append(
					Component.translatable("projecthero.guide.symbiote.weakness.fire")).withStyle(ChatFormatting.GRAY));
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
					case "Z" -> "adamantium_execution"; case "V" -> "frenzy"; default -> "berserker_rage";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.wolverine.ability." + key).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.wolverine.ability." + key);
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
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C", "Shift+C"}) {
				String key = switch (slot) {
					case "R" -> "punch"; case "G" -> "heavy_smash"; case "X" -> "leap"; case "Z" -> "stomp";
					case "V" -> "roar"; case "C" -> "regeneration"; default -> "hardening";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.titan_shifter.ability." + key).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.titan_shifter.ability." + key);
			}
			blank(lines);
			head(lines, "projecthero.guide.titan_shifter.stats");
			para(lines, "projecthero.guide.titan_shifter.stats.body");
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
			head(lines, "projecthero.guide.all_might.ofa");
			para(lines, "projecthero.guide.all_might.ofa.body");
			blank(lines);
			head(lines, "projecthero.guide.all_might.forms");
			para(lines, "projecthero.guide.all_might.forms.body");
			head(lines, "projecthero.guide.all_might.costume");
			para(lines, "projecthero.guide.all_might.costume.body");
			blank(lines);
			head(lines, "projecthero.guide.all_might.controls");
			for (String[] row : new String[][] {
					{ "H", "transform" }, { "R", "detroit_smash" }, { "Shift+R", "new_hampshire_smash" }, { "G", "texas_smash" },
					{ "X", "all_might_leap" }, { "Z", "united_states_of_smash" }, { "V", "carolina_smash" }, { "C", "plus_ultra" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.all_might.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.guide.all_might.ability." + row[1]);
			}
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
			for (String section : new String[]{"origin", "rage", "change", "stats", "fists", "death_save"}) {
				head(lines, "projecthero.guide.hulk." + section);
				para(lines, "projecthero.guide.hulk." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.hulk.controls");
			for (String[] row : new String[][] { { "H", "transform" }, { "R", "power_punch" }, { "G", "ground_smash" }, { "Z", "thunderclap" }, { "Shift+Z", "hulk_smash" },
					{ "X", "super_leap" }, { "C", "charge" }, { "V", "grab" }, { "Shift+V", "earth_chunk" }, { "N (hold)", "calm" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.hulk.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.hulk.ability." + row[1] + ".desc");
			}
			blank(lines);
			for (String section : new String[]{"sprint_smash", "control", "riding", "looks", "limits", "commands"}) {
				head(lines, "projecthero.guide.hulk." + section);
				para(lines, "projecthero.guide.hulk." + section + ".body");
				blank(lines);
			}
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
			for (String section : new String[]{"arrival", "moves", "split", "weakness", "rewards", "commands"}) {
				blank(lines);
				head(lines, "projecthero.guide.carnage." + section);
				para(lines, "projecthero.guide.carnage." + section + ".body");
			}
		}));


		// one chapter per ENABLED power (v0.14.8), in registration order (CHAPTER_POWER_BASE + i)
		for (Power power : Powers.enabled()) {
			out.add(powerChapter(power));
		}
		return out;
	}

	private static List<IndexEntry> buildIndex() {
		chapters(); // make sure the flat list exists
		List<IndexEntry> idx = new ArrayList<>();

		section(idx, "projecthero.guide.section.getting_started", false);
		link(idx, "projecthero.guide.overview", CH_OVERVIEW);
		link(idx, "projecthero.guide.mutation", CH_MUTATION);
		link(idx, "projecthero.guide.combos", CH_COMBOS);

		section(idx, "projecthero.guide.section.world", true);
		link(idx, "projecthero.guide.structures", CH_STRUCTURES);
		link(idx, "projecthero.guide.devices", CH_DEVICES);
		link(idx, "projecthero.guide.stark_sorter", CH_STARK_SORTER);

		section(idx, "projecthero.guide.section.heroes", true);
		link(idx, "projecthero.guide.thor", CH_THOR);
		link(idx, "projecthero.guide.iron_man", CH_IRON_MAN);
		link(idx, "projecthero.guide.spider_man", CH_SPIDER_MAN);
		link(idx, "projecthero.guide.max_steel", CH_MAX_STEEL);
		link(idx, "projecthero.guide.punisher", CH_PUNISHER);
		link(idx, "projecthero.guide.green_lantern", CH_GREEN_LANTERN);
		link(idx, "projecthero.guide.symbiote", CH_SYMBIOTE);
		link(idx, "projecthero.guide.wolverine", CH_WOLVERINE);
		link(idx, "projecthero.guide.titan_shifter", CH_TITAN_SHIFTER);
		link(idx, "projecthero.guide.all_might", CH_ALL_MIGHT);
		link(idx, "projecthero.guide.hulk", CH_HULK);
		link(idx, "projecthero.guide.moon_knight", CH_MOON_KNIGHT);
		link(idx, "projecthero.guide.super_soldier", CH_SUPER_SOLDIER);
		link(idx, "projecthero.guide.kryptonian", CH_KRYPTONIAN);
		// v0.14.13: Super Speed is Hero-Tier -- its chapter is still the generated power chapter
		for (int i = 0; i < Powers.enabled().size(); i++) {
			if (Powers.isHeroTier(Powers.enabled().get(i))) {
				idx.add(new IndexEntry(Component.translatable(Powers.enabled().get(i).nameKey()), 1, CHAPTER_POWER_BASE + i));
			}
		}

		section(idx, "projecthero.guide.section.events", true);
		link(idx, "projecthero.guide.zombie_raid", CH_ZOMBIE_RAID);
		link(idx, "projecthero.guide.supervillain_raid", CH_SUPERVILLAIN_RAID);
		link(idx, "projecthero.guide.titan", CH_TITAN);
		link(idx, "projecthero.guide.abyssal_behemoth", CH_ABYSSAL_BEHEMOTH);
		link(idx, "projecthero.guide.oathbreaker", CH_OATHBREAKER);
		link(idx, "projecthero.guide.darkseid_raid", CH_DARKSEID_RAID);
		link(idx, "projecthero.guide.hordes", CH_HORDES);
		link(idx, "projecthero.guide.syndicate", CH_SYNDICATE);
		link(idx, "projecthero.guide.carnage", CH_CARNAGE);

		section(idx, "projecthero.guide.section.squads", true);
		link(idx, "projecthero.guide.squads", CH_SQUADS);

		section(idx, "projecthero.guide.section.powers", true);
		List<Power> powers = new ArrayList<>(Powers.enabled());
		for (PowerCategory category : PowerCategory.values()) {
			boolean started = false;
			for (int i = 0; i < powers.size(); i++) {
				if (powers.get(i).category() != category || Powers.isHeroTier(powers.get(i))) {
					continue;
				}
				if (!started) {
					idx.add(new IndexEntry(Component.translatable(category.translationKey())
							.withStyle(ChatFormatting.YELLOW), 1, -1));
					started = true;
				}
				idx.add(new IndexEntry(Component.translatable(powers.get(i).nameKey()), 2,
						CHAPTER_POWER_BASE + i));
			}
		}
		return idx;
	}

	private static void section(List<IndexEntry> idx, String key, boolean spacerBefore) {
		if (spacerBefore) {
			idx.add(new IndexEntry(Component.empty(), 0, -1));
		}
		idx.add(new IndexEntry(Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 0, -1));
	}

	private static void link(List<IndexEntry> idx, String titleKey, int chapterIndex) {
		idx.add(new IndexEntry(Component.translatable(titleKey), 1, chapterIndex));
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

	private interface Body {
		void fill(List<Component> lines);
	}

	private static Chapter chapter(String titleKey, Body body) {
		List<Component> lines = new ArrayList<>();
		body.fill(lines);
		return new Chapter(Component.translatable(titleKey), lines);
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
