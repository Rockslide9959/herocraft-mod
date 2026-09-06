package com.herocraft.mod.hero.guide;

import java.util.ArrayList;
import java.util.List;

import com.herocraft.mod.hero.Ability;
import com.herocraft.mod.hero.AbilitySlot;
import com.herocraft.mod.hero.Power;
import com.herocraft.mod.hero.PowerCategory;
import com.herocraft.mod.hero.Powers;

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
		return chapter("herocraft.guide.symbiote", lines -> {
			lines.add(Component.translatable("herocraft.guide.symbiote.tier").withStyle(ChatFormatting.LIGHT_PURPLE));
			para(lines, "herocraft.guide.symbiote.body");
			blank(lines);
			head(lines, "herocraft.guide.symbiote.controls");
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C"}) {
				String ability = switch (slot) {
					case "R" -> "tendril_grab"; case "G" -> "tendril_strike"; case "X" -> "leap";
					case "Z" -> "onslaught"; case "V" -> "shield"; default -> "frenzy";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("herocraft.symbiote.ability." + ability).withStyle(ChatFormatting.WHITE)));
				para(lines, "herocraft.symbiote.ability." + ability + ".desc");
			}
			lines.add(Component.literal(" Sneak+X  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("herocraft.symbiote.ability.grapple").withStyle(ChatFormatting.WHITE)));
			para(lines, "herocraft.symbiote.ability.grapple.desc");
			blank(lines);
			head(lines, "herocraft.guide.symbiote.passives");
			lines.add(Component.literal(" • ").append(
					Component.translatable("herocraft.guide.symbiote.passive.recovery")).withStyle(ChatFormatting.GRAY));
			blank(lines);
			head(lines, "herocraft.guide.symbiote.weaknesses");
			lines.add(Component.literal(" • ").append(
					Component.translatable("herocraft.guide.symbiote.weakness.sonic")).withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal(" • ").append(
					Component.translatable("herocraft.guide.symbiote.weakness.fire")).withStyle(ChatFormatting.GRAY));
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
		return chapter("herocraft.guide.symbiote_spider_man", lines -> {
			lines.add(Component.translatable("herocraft.guide.symbiote_spider_man.tier").withStyle(ChatFormatting.LIGHT_PURPLE));
			para(lines, "herocraft.guide.symbiote_spider_man.body");
			blank(lines);
			head(lines, "herocraft.guide.spider_man.controls");
			for (String ability : new String[]{
					com.herocraft.mod.spider.SpiderAbilities.WEB_SWING,
					com.herocraft.mod.spider.SpiderAbilities.WEB_ZIP,
					com.herocraft.mod.spider.SpiderAbilities.WEB_YANK,
					com.herocraft.mod.spider.SpiderAbilities.WEB_SHOT,
					com.herocraft.mod.spider.SpiderAbilities.WEB_NET,
					com.herocraft.mod.spider.SpiderAbilities.WALL_CRAWL }) {
				lines.add(Component.literal(" " + com.herocraft.mod.spider.SpiderAbilities.slotKeyOf(ability) + "  ")
						.withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("herocraft.spider_man.ability." + ability).withStyle(ChatFormatting.WHITE)));
				para(lines, "herocraft.spider_man.ability." + ability + ".desc");
			}
			blank(lines);
			head(lines, "herocraft.guide.symbiote.black_suit");
			String[] extras = {"symbiote_tendril_strike", "symbiote_crush", "symbiote_slam_enhanced"};
			String[] keyedOn = {com.herocraft.mod.spider.SpiderAbilities.WEB_YANK,
					com.herocraft.mod.spider.SpiderAbilities.WEB_SHOT,
					com.herocraft.mod.spider.SpiderAbilities.WALL_CRAWL};
			for (int i = 0; i < extras.length; i++) {
				lines.add(Component.literal(" Sneak+" + com.herocraft.mod.spider.SpiderAbilities.slotKeyOf(keyedOn[i]) + "  ")
						.withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("herocraft.symbiote.ability." + extras[i]).withStyle(ChatFormatting.WHITE)));
				para(lines, "herocraft.symbiote.ability." + extras[i] + ".desc");
			}
			blank(lines);
			head(lines, "herocraft.guide.symbiote_spider_man.passives");
			for (String p : new String[]{"melee", "speed", "jump", "knockback", "web_capacity", "recovery"}) {
				lines.add(Component.literal(" • ").append(
						Component.translatable("herocraft.guide.symbiote_spider_man.passive." + p)).withStyle(ChatFormatting.GRAY));
			}
			blank(lines);
			head(lines, "herocraft.guide.symbiote.weaknesses");
			lines.add(Component.literal(" • ").append(
					Component.translatable("herocraft.guide.symbiote.weakness.sonic")).withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal(" • ").append(
					Component.translatable("herocraft.guide.symbiote.weakness.fire")).withStyle(ChatFormatting.GRAY));
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
	private static final int CH_SYMBIOTE = 10;
	private static final int CH_ZOMBIE_RAID = 11;
	private static final int CH_SUPERVILLAIN_RAID = 12;
	private static final int CHAPTER_POWER_BASE = 13;

	private static List<Chapter> build() {
		List<Chapter> out = new ArrayList<>();

		out.add(chapter("herocraft.guide.overview", lines -> {
			para(lines, "herocraft.guide.overview.body");
			blank(lines);
			head(lines, "herocraft.guide.controls.title");
			para(lines, "herocraft.guide.controls.body");
			for (AbilitySlot slot : AbilitySlot.values()) {
				lines.add(Component.literal("  " + slot.defaultKey() + "  ")
						.withStyle(ChatFormatting.GOLD)
						.append(Component.translatable(slot.keyBindingTranslationKey()).withStyle(ChatFormatting.WHITE))
						.append(Component.literal("  — " + slot.role()).withStyle(ChatFormatting.GRAY)));
			}
			lines.add(Component.translatable("herocraft.guide.controls.select").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("herocraft.guide.controls.info").withStyle(ChatFormatting.GRAY));
		}));

		out.add(chapter("herocraft.guide.mutation", lines -> {
			para(lines, "herocraft.guide.mutation.body");
			blank(lines);
			para(lines, "herocraft.guide.mutation.capacity");
			blank(lines);
			para(lines, "herocraft.guide.mutation.research");
		}));

		out.add(chapter("herocraft.guide.structures", lines -> {
			para(lines, "herocraft.guide.structures.body");
			blank(lines);
			for (String s : new String[]{"research_facility", "meteor_impact", "power_station",
					"geological_site", "government_site", "hydrostatic_facility"}) {
				lines.add(Component.translatable("herocraft.guide.structure." + s).withStyle(ChatFormatting.WHITE));
				para(lines, "herocraft.guide.structure." + s + ".desc");
			}
		}));

		out.add(chapter("herocraft.guide.devices", lines -> {
			para(lines, "herocraft.guide.devices.body");
			blank(lines);
			for (String d : new String[]{"overloaded_redstone_coil", "experimental_light_projector",
					"unstable_gravity_plate", "geological_resonance_chamber", "molecular_compression_chamber",
					"mass_compression_chamber", "pressure_chamber", "hydrostatic_test_tank", "electromagnetic_coil"}) {
				lines.add(Component.translatable("block.herocraft." + d).withStyle(ChatFormatting.WHITE));
			}
		}));

		out.add(chapter("herocraft.guide.combos", lines -> {
			para(lines, "herocraft.guide.combos.body");
			blank(lines);
			for (String k : COMBO_KEYS) {
				lines.add(Component.literal("• ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("herocraft.combo." + k).withStyle(ChatFormatting.GRAY)));
			}
		}));

		out.add(chapter("herocraft.guide.thor", lines -> para(lines, "herocraft.guide.thor.body")));

		out.add(chapter("herocraft.guide.iron_man", lines -> {
			lines.add(Component.translatable("herocraft.guide.iron_man.tier").withStyle(ChatFormatting.DARK_AQUA));
			para(lines, "herocraft.guide.iron_man.body");
			blank(lines);
			head(lines, "herocraft.guide.iron_man.progression");
			for (String step : new String[]{"arc_reactor", "tony_stark", "fabricator", "mark_iii", "mark_v",
					"mark_vii"}) {
				lines.add(Component.literal("  ↓  ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.translatable("herocraft.guide.iron_man.step." + step).withStyle(ChatFormatting.WHITE)));
			}
			blank(lines);
			para(lines, "herocraft.guide.iron_man.recipes");
			para(lines, "herocraft.guide.iron_man.controls");
		}));

		// Spider-Man. Sits with the other Hero Classes rather than with the 27 mutations, because that
		// is what it is -- but its progression starts inside the mutation system, so the entry spells
		// out the whole path from serum to Hero Class in one place.
		out.add(chapter("herocraft.guide.spider_man", lines -> {
			lines.add(Component.translatable("herocraft.guide.spider_man.tier").withStyle(ChatFormatting.DARK_RED));
			para(lines, "herocraft.guide.spider_man.body");
			blank(lines);
			head(lines, "herocraft.guide.spider_man.progression");
			for (String step : new String[]{"adhesion", "mutagen", "evolve"}) {
				lines.add(Component.literal("  ↓  ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.translatable("herocraft.guide.spider_man.step." + step).withStyle(ChatFormatting.WHITE)));
			}
			blank(lines);
			head(lines, "herocraft.guide.spider_man.controls");
			for (String ability : new String[]{
					com.herocraft.mod.spider.SpiderAbilities.WEB_SWING,
					com.herocraft.mod.spider.SpiderAbilities.WEB_ZIP,
					com.herocraft.mod.spider.SpiderAbilities.WEB_YANK,
					com.herocraft.mod.spider.SpiderAbilities.WEB_SHOT,
					com.herocraft.mod.spider.SpiderAbilities.WEB_NET,
					com.herocraft.mod.spider.SpiderAbilities.WALL_CRAWL }) {
				lines.add(Component.literal(" " + com.herocraft.mod.spider.SpiderAbilities.slotKeyOf(ability) + "  ")
						.withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("herocraft.spider_man.ability." + ability).withStyle(ChatFormatting.WHITE)));
				para(lines, "herocraft.spider_man.ability." + ability + ".desc");
			}
			blank(lines);
			head(lines, "herocraft.guide.spider_man.passives");
			for (String p : new String[]{"sense", "dodge", "arrows", "crawl", "ceiling", "wall_leap",
					"double_jump", "super_jump", "web_blossom", "strength", "agility", "fall", "reserve"}) {
				lines.add(Component.literal(" • ").append(
						Component.translatable("herocraft.guide.spider_man.passive." + p)).withStyle(ChatFormatting.GRAY));
			}
			blank(lines);
			para(lines, "herocraft.guide.spider_man.swinging");
			para(lines, "herocraft.guide.spider_man.reserve");
		}));
		// Max Steel -- Hero Tier. Max McGrath bonded with the Ultralink Steel: T.U.R.B.O. Energy, a
		// nanotech suit, and five specialised Turbo Modes.
		out.add(chapter("herocraft.guide.max_steel", lines -> {
			lines.add(Component.translatable("herocraft.guide.max_steel.tier").withStyle(ChatFormatting.DARK_AQUA));
			para(lines, "herocraft.guide.max_steel.body");
			blank(lines);
			head(lines, "herocraft.guide.max_steel.progression");
			para(lines, "herocraft.guide.max_steel.step.find");
			para(lines, "herocraft.guide.max_steel.step.bond");
			blank(lines);
			head(lines, "herocraft.guide.max_steel.energy");
			para(lines, "herocraft.guide.max_steel.energy.body");
			blank(lines);
			head(lines, "herocraft.guide.max_steel.controls");
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C"}) {
				String key = switch (slot) {
					case "R" -> "turbo_blast"; case "G" -> "turbo_strength"; case "X" -> "turbo_speed";
					case "Z" -> "turbo_flight"; case "V" -> "turbo_stealth"; default -> "turbo_cannon";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("herocraft.max_steel.ability." + key).withStyle(ChatFormatting.WHITE)));
				para(lines, "herocraft.guide.max_steel.ability." + key);
			}
			blank(lines);
			head(lines, "herocraft.guide.max_steel.passives");
			para(lines, "herocraft.guide.max_steel.passives.body");
			blank(lines);
			head(lines, "herocraft.guide.max_steel.emergency");
			para(lines, "herocraft.guide.max_steel.emergency.body");
		}));

		// Punisher -- Hero Tier. A trained human: firearms, explosives, tactical gear. Earned through
		// Vigilante Training, not an accident.
		out.add(chapter("herocraft.guide.punisher", lines -> {
			lines.add(Component.translatable("herocraft.guide.punisher.tier").withStyle(ChatFormatting.DARK_GRAY));
			para(lines, "herocraft.guide.punisher.body");
			blank(lines);
			head(lines, "herocraft.guide.punisher.progression");
			para(lines, "herocraft.guide.punisher.step.safehouse");
			para(lines, "herocraft.guide.punisher.step.training");
			blank(lines);
			head(lines, "herocraft.guide.punisher.weapons");
			for (String w : new String[]{"punisher_pistol", "punisher_assault_rifle",
					"punisher_shotgun", "punisher_sniper"}) {
				lines.add(Component.translatable("item.herocraft." + w).withStyle(ChatFormatting.WHITE));
			}
			para(lines, "herocraft.guide.punisher.ammo");
			blank(lines);
			head(lines, "herocraft.guide.punisher.controls");
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C"}) {
				String key = switch (slot) {
					case "R" -> "arsenal"; case "G" -> "grenade"; case "X" -> "roll";
					case "Z" -> "suppressive"; case "V" -> "adrenaline"; default -> "c4";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("herocraft.guide.punisher.ability." + key).withStyle(ChatFormatting.WHITE)));
			}
			blank(lines);
			head(lines, "herocraft.guide.punisher.passives");
			para(lines, "herocraft.guide.punisher.passives.body");
			blank(lines);
			para(lines, "herocraft.guide.punisher.crafting");
		}));

		// The Symbiote -- not a Hero Class of its own like the other four: any player can bond with it,
		// and which of the two host variants they get is derived live from whether they also hold
		// Spider-Man. Sits with the other Hero-Tier chapters because that is what pressing I shows.
		out.add(chapter("herocraft.guide.symbiote", lines -> {
			lines.add(Component.translatable("herocraft.guide.symbiote.tier").withStyle(ChatFormatting.LIGHT_PURPLE));
			para(lines, "herocraft.guide.symbiote.body");
			blank(lines);
			head(lines, "herocraft.guide.symbiote.progression");
			para(lines, "herocraft.guide.symbiote.step.find");
			para(lines, "herocraft.guide.symbiote.step.bond");
			blank(lines);
			head(lines, "herocraft.guide.symbiote.hosts");
			para(lines, "herocraft.guide.symbiote.host.normal");
			para(lines, "herocraft.guide.symbiote.host.spider_man");
			blank(lines);
			head(lines, "herocraft.guide.symbiote.controls");
			para(lines, "herocraft.guide.symbiote.controls.body");
			for (String slot : new String[]{"R", "G", "X", "Z", "V", "C"}) {
				String ability = switch (slot) {
					case "R" -> "tendril_grab"; case "G" -> "tendril_strike"; case "X" -> "leap";
					case "Z" -> "onslaught"; case "V" -> "shield"; default -> "frenzy";
				};
				lines.add(Component.literal(" " + slot + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("herocraft.symbiote.ability." + ability).withStyle(ChatFormatting.WHITE)));
				para(lines, "herocraft.symbiote.ability." + ability + ".desc");
			}
			lines.add(Component.literal(" Sneak+X  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("herocraft.symbiote.ability.grapple").withStyle(ChatFormatting.WHITE)));
			para(lines, "herocraft.symbiote.ability.grapple.desc");
			blank(lines);
			head(lines, "herocraft.guide.symbiote.black_suit");
			para(lines, "herocraft.guide.symbiote.black_suit.body");
			blank(lines);
			head(lines, "herocraft.guide.symbiote.passives");
			lines.add(Component.literal(" • ").append(
					Component.translatable("herocraft.guide.symbiote.passive.recovery")).withStyle(ChatFormatting.GRAY));
			blank(lines);
			head(lines, "herocraft.guide.symbiote.weaknesses");
			lines.add(Component.literal(" • ").append(
					Component.translatable("herocraft.guide.symbiote.weakness.sonic")).withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal(" • ").append(
					Component.translatable("herocraft.guide.symbiote.weakness.fire")).withStyle(ChatFormatting.GRAY));
		}));

		// World events. Written here rather than in a separate book so there is exactly one in-game
		// reference for the whole mod (spec section 38/58: integrate, do not build a second encyclopedia).
		out.add(chapter("herocraft.guide.zombie_raid", lines -> {
			para(lines, "herocraft.guide.zombie_raid.body");
			blank(lines);
			for (String section : new String[]{"curse", "sources", "waves", "bosses", "rewards", "repeat"}) {
				head(lines, "herocraft.guide.zombie_raid." + section);
				para(lines, "herocraft.guide.zombie_raid." + section + ".body");
				blank(lines);
			}
		}));

		out.add(chapter("herocraft.guide.supervillain_raid", lines -> {
			para(lines, "herocraft.guide.supervillain_raid.body");
			blank(lines);
			for (String section : new String[]{"spy", "mark", "prepare", "waves", "villain", "rewards"}) {
				head(lines, "herocraft.guide.supervillain_raid." + section);
				para(lines, "herocraft.guide.supervillain_raid." + section + ".body");
				blank(lines);
			}
		}));

		// one chapter per power, in registration order (CHAPTER_POWER_BASE + i)
		for (Power power : Powers.all()) {
			out.add(powerChapter(power));
		}
		return out;
	}

	private static List<IndexEntry> buildIndex() {
		chapters(); // make sure the flat list exists
		List<IndexEntry> idx = new ArrayList<>();

		section(idx, "herocraft.guide.section.getting_started", false);
		link(idx, "herocraft.guide.overview", CH_OVERVIEW);
		link(idx, "herocraft.guide.mutation", CH_MUTATION);
		link(idx, "herocraft.guide.combos", CH_COMBOS);

		section(idx, "herocraft.guide.section.world", true);
		link(idx, "herocraft.guide.structures", CH_STRUCTURES);
		link(idx, "herocraft.guide.devices", CH_DEVICES);

		section(idx, "herocraft.guide.section.heroes", true);
		link(idx, "herocraft.guide.thor", CH_THOR);
		link(idx, "herocraft.guide.iron_man", CH_IRON_MAN);
		link(idx, "herocraft.guide.spider_man", CH_SPIDER_MAN);
		link(idx, "herocraft.guide.max_steel", CH_MAX_STEEL);
		link(idx, "herocraft.guide.punisher", CH_PUNISHER);
		link(idx, "herocraft.guide.symbiote", CH_SYMBIOTE);

		section(idx, "herocraft.guide.section.events", true);
		link(idx, "herocraft.guide.zombie_raid", CH_ZOMBIE_RAID);
		link(idx, "herocraft.guide.supervillain_raid", CH_SUPERVILLAIN_RAID);

		section(idx, "herocraft.guide.section.powers", true);
		List<Power> powers = new ArrayList<>(Powers.all());
		for (PowerCategory category : PowerCategory.values()) {
			boolean started = false;
			for (int i = 0; i < powers.size(); i++) {
				if (powers.get(i).category() != category) {
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
	 * The same entry, plus the reader's own Experimental Power research from Empowered Zombie kills
	 * (spec section 38). Pass a negative percentage to omit the line -- the shared book has no single
	 * reader, so only the per-player "Your Power" screen supplies one.
	 */
	public static Chapter powerChapter(Power power, int researchPercent) {
		return chapter(power.nameKey(), lines -> {
			if (researchPercent >= 0) {
				lines.add(Component.translatable("herocraft.guide.power.analysis", researchPercent)
						.withStyle(researchPercent >= 100 ? ChatFormatting.GOLD : ChatFormatting.AQUA));
			}
			lines.add(Component.translatable(power.category().translationKey()).withStyle(ChatFormatting.DARK_AQUA));
			para(lines, power.descKey());
			blank(lines);
			head(lines, "herocraft.guide.power.abilities");
			for (AbilitySlot slot : AbilitySlot.values()) {
				Ability a = power.ability(slot);
				lines.add(Component.literal(" " + slot.defaultKey() + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable(a.nameKey()).withStyle(ChatFormatting.WHITE)));
				para(lines, a.descKey());
			}
			blank(lines);
			head(lines, "herocraft.guide.power.passives");
			for (String pk : power.passiveKeys()) {
				lines.add(Component.literal(" • ").append(Component.translatable(pk)).withStyle(ChatFormatting.GRAY));
			}
			blank(lines);
			if (power.serum() != null) {
				lines.add(Component.translatable("herocraft.guide.power.serum",
						Component.translatable(power.serum().resultName())).withStyle(ChatFormatting.LIGHT_PURPLE));
				lines.add(Component.translatable("herocraft.guide.power.base",
						Component.literal(power.serum().basePotion())).withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("herocraft.guide.power.additives",
						Component.literal(String.join(", ", power.serum().additives()))).withStyle(ChatFormatting.GRAY));
				if (power.serum().fuel() != null) {
					lines.add(Component.translatable("herocraft.guide.power.fuel",
							Component.literal(power.serum().fuel())).withStyle(ChatFormatting.GRAY));
				}
			}
			if (power.trigger() != null) {
				lines.add(Component.translatable("herocraft.guide.power.trigger",
						Component.translatable(power.trigger().descKey())).withStyle(ChatFormatting.YELLOW));
				if (power.trigger().labDeviceKey() != null) {
					lines.add(Component.translatable("herocraft.guide.power.device",
							Component.translatable(power.trigger().labDeviceKey())).withStyle(ChatFormatting.GRAY));
				}
			}
		});
	}

	private static final String[] COMBO_KEYS = {
			"strength_flight", "speed_electrokinesis", "water_electrokinesis", "geokinesis_strength",
			"cryokinesis_water", "pyrokinesis_flight", "energy_absorption_laser", "durability_size",
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
