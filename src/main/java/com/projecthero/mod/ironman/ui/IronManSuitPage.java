package com.projecthero.mod.ironman.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.ironman.IronManPassives;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.ability.IronManComicMoves;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * v0.17.7 (user: "when player presses I in an armour the armour information is very limited, please make it more fleshed out
 * and give more explanations and information as if its a regular power"): the full page for one Iron Man suit, built from the
 * suit's own data ({@link IronManSuit}, {@link IronManComicKit}) plus a few per-suit lang lines. One source for the I-key page
 * ({@code IronManSuitInfoScreen}, which adds the live status on top) and the suit sections of the Guidebook's Iron Man chapter.
 *
 * <p>Every line is one {@link Component}; the callers word-wrap (TooltipWrap rule). A line that starts with spaces keeps its
 * wrapped rows under the same indent (both callers do a hanging indent), which is how a move's description sits under its key.
 *
 * <p>Page order: era + intro, Suit Stats, Keys &amp; Moves, Passives, Getting the Suit, Putting It On &amp; Taking It Off, HUD,
 * Tips. Per-suit lang: {@code projecthero.ironman_page.<suit>.lore / .passive / .obtain / .wear / .tips} (and {@code .hud} for
 * the suits in {@link #HUD_EXTRA}).
 */
public final class IronManSuitPage {
	/** The default key labels (the Guidebook; the I page passes the player's real binds). */
	public static final String[] DEFAULT_KEYS = { "R", "G", "X", "Z", "V", "C" };
	private static final String P = "projecthero.ironman_page.";
	/** Moves whose kit input is HOLD but that are used as a tap (the hold is a separate move, listed on its own). */
	private static final Set<String> TAP_LIKE_HOLDS = Set.of("mk2_repulsor", "mk6_energy_shield", "mk8_hydraulic_brace");
	/** Suits with an extra HUD meter of their own ({@code projecthero.ironman_page.<suit>.hud}). */
	private static final Set<String> HUD_EXTRA = Set.of("mark_9", "mark_6", "mark_vii", "mark_8", "mark_v", "mark_1_v2");

	private IronManSuitPage() {
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
	}

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> String.valueOf(n);
		};
	}

	/** "Mk I Cave-Forged". */
	public static MutableComponent fullName(IronManSuit suit) {
		return Component.translatable(suit.nameKey()).append(" ").append(Component.translatable(suit.displayKey()));
	}

	/** The suits in progression order: Era I first (Mk I to Mk V), then Era II (Mk VI to the Hulkbuster I). */
	public static List<IronManSuit> progression() {
		List<IronManSuit> out = new ArrayList<>();
		for (IronManSuit s : IronManSuits.all()) {
			if (s.eraOne()) {
				out.add(s);
			}
		}
		for (String id : new String[] { "mark_2", "mark_6", "mark_vii", "mark_9", "mark_8" }) {
			IronManSuit s = IronManSuits.byId(id);
			if (s != null) {
				out.add(s);
			}
		}
		for (IronManSuit s : IronManSuits.all()) {
			if (!out.contains(s)) {
				out.add(s);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ the page

	/** The era line and the short intro. */
	public static List<Component> intro(IronManSuit suit) {
		List<Component> out = new ArrayList<>();
		out.add(Component.translatable(P + "era." + (suit.eraOne() ? "era1" : "era2")).withStyle(ChatFormatting.DARK_AQUA));
		out.add(Component.translatable(P + suit.id() + ".lore").withStyle(ChatFormatting.GRAY));
		return out;
	}

	/** Everything after the intro (and after the live status the I page puts in between). */
	public static List<Component> details(IronManSuit suit, String[] keys) {
		String[] k = keys == null || keys.length < 6 ? DEFAULT_KEYS : keys;
		List<Component> out = new ArrayList<>();
		head(out, "stats");
		out.addAll(stats(suit));
		head(out, "keys");
		para(out, P + "keys.intro." + (suit.eraOne() ? "era1" : "era2"));
		out.addAll(moves(suit, k));
		head(out, "passives");
		out.addAll(passives(suit));
		head(out, "obtain");
		para(out, P + suit.id() + ".obtain");
		head(out, "wear");
		para(out, P + "wear." + (suit.eraOne() ? "era1" : "era2"));
		para(out, P + suit.id() + ".wear");
		head(out, "hud");
		out.addAll(hud(suit));
		head(out, "tips");
		para(out, P + suit.id() + ".tips");
		return out;
	}

	/** The Guidebook's section for one suit: the same page, without the live status. */
	public static List<Component> guide(IronManSuit suit) {
		List<Component> out = new ArrayList<>(intro(suit));
		out.addAll(details(suit, DEFAULT_KEYS));
		return out;
	}

	private static void head(List<Component> out, String section) {
		out.add(Component.empty());
		out.add(Component.translatable(P + "section." + section).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
	}

	private static void para(List<Component> out, String key) {
		out.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
	}

	private static Component row(Component label, String value) {
		return Component.literal(" ").append(label.copy().withStyle(ChatFormatting.GRAY))
				.append(Component.literal("  " + value).withStyle(ChatFormatting.WHITE));
	}

	private static Component bullet(Component text) {
		return Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY).append(text.copy().withStyle(ChatFormatting.GRAY));
	}

	// ------------------------------------------------------------------ stats

	/** The stat rows: the shared comic stats plus the helmet systems and the full-suit bonuses. */
	public static List<Component> stats(IronManSuit suit) {
		List<Component> out = new ArrayList<>();
		for (IronManComicText.Row r : IronManComicText.stats(suit)) {
			out.add(row(r.label(), r.value()));
		}
		out.add(row(Component.translatable(P + "stat.haste"), "Haste " + roman(IronManPassives.hasteAmplifier(suit) + 1)));
		if (suit.walkSpeedBonus() != 0.0) {
			out.add(row(Component.translatable(P + "stat.walk"), (suit.walkSpeedBonus() > 0 ? "+" : "")
					+ Math.round(suit.walkSpeedBonus() * 100.0) + "%"));
		}
		if (suit.scale() != 1.0f) {
			out.add(row(Component.translatable(P + "stat.scale"), "x" + fmt(suit.scale())));
		}
		if (suit.resistanceAmplifier() >= 0) {
			out.add(row(Component.translatable("screen.projecthero.ironman_info.resistance"), roman(suit.resistanceAmplifier() + 1)));
		}
		if (suit.altitudeCeiling() > 0 && suit.altitudeCeiling() < 10000) {
			out.add(row(Component.translatable("screen.projecthero.ironman_info.ceiling"), "Y " + fmt(suit.altitudeCeiling())
					+ (suit.ceilingFreeze() ? "  " + Component.translatable(P + "stat.ceiling_freeze").getString() : "")));
		} else {
			out.add(row(Component.translatable("screen.projecthero.ironman_info.ceiling"), Component.translatable(P + "stat.no_ceiling").getString()));
		}
		out.add(row(Component.translatable("screen.projecthero.ironman_info.scanner"), "mark_1".equals(suit.id()) ? "-"
				: ("mark_2".equals(suit.id()) ? "25" : "100") + " m"));
		out.add(row(Component.translatable(P + "stat.highlight"), fmt(suit.targetScanRange()) + " m"));
		out.add(row(Component.translatable("screen.projecthero.ironman_info.targeting"), yesNo(IronManTargeting.hasTargeting(suit))));
		out.add(row(Component.translatable("screen.projecthero.ironman_info.night_vision"), suit.helmetNightVision()
				? Component.translatable("screen.projecthero.ironman_info.night_vision_auto").getString() : yesNo(false)));
		String air = suit.waterBreathing() ? Component.translatable("screen.projecthero.ironman_info.air_unlimited").getString()
				: suit.airTankSeconds() > 0 ? suit.airTankSeconds() + " s" : yesNo(false);
		out.add(row(Component.translatable("screen.projecthero.ironman_info.air"), air));
		out.add(row(Component.translatable("screen.projecthero.ironman_info.auto_feed"), yesNo(suit.autoFeed())));
		return out;
	}

	private static String yesNo(boolean b) {
		return Component.translatable(b ? "screen.projecthero.ironman_info.yes" : "screen.projecthero.ironman_info.no").getString();
	}

	// ------------------------------------------------------------------ moves

	/**
	 * Every key: "[R] Flamethrower  hold, 8 damage, 5 energy/s" and the description indented under it, then the Shift move the
	 * same way; C's tap move, and what Shift+C (hold) does -- the Era I eject or the Era II fold. {@code keys} = the six key
	 * labels, R G X Z V C.
	 */
	public static List<Component> moves(IronManSuit suit, String[] keys) {
		String[] k = keys == null || keys.length < 6 ? DEFAULT_KEYS : keys;
		List<Component> out = new ArrayList<>();
		IronManComicKit.Kit kit = IronManComicKit.kit(suit.id());
		if (kit == null) {
			return out;
		}
		for (int slot = 1; slot <= kit.keys().size() && slot <= 6; slot++) {
			IronManComicKit.Key key = kit.key(slot);
			if (key == null) {
				continue;
			}
			String label = k[slot - 1];
			if (key.tap() != null) {
				move(out, suit, "[" + label + "]", key.tap());
			}
			if (key.shift() != null) {
				move(out, suit, "[Shift+" + label + "]", key.shift());
			}
			if (slot == 6) {
				// the C key's extras: the hold moves, then Shift+C
				if ("mark_6".equals(suit.id())) {
					line(out, Component.translatable(P + "move.hold", label).getString(), Component.translatable("hud.projecthero.ironman.comic.mk6_overload_burst"),
							stats(IronManComicMoves.BURST_DAMAGE, IronManComicMoves.BURST_COOLDOWN_TICKS, IronManComicMoves.BURST_ENERGY, 0f, false),
							P + "move.mk6_overload_burst");
				} else if ("mark_8".equals(suit.id())) {
					line(out, Component.translatable(P + "move.hold", label).getString(), Component.translatable(P + "move.sentry_mode"),
							Component.translatable(P + "move.free").getString(), P + "move.sentry_mode.desc");
				}
				line(out, "[Shift+" + label + "]", Component.translatable(P + "move." + (suit.eraOne() ? "eject" : "fold")),
						Component.translatable(P + "move.hold_s", suit.eraOne() ? 3 : 1).getString(),
						P + "move." + (suit.eraOne() ? "eject" : "fold") + ".desc");
			}
			out.add(Component.empty());
		}
		if (!out.isEmpty() && out.get(out.size() - 1).getString().isEmpty()) {
			out.remove(out.size() - 1);
		}
		return out;
	}

	private static void move(List<Component> out, IronManSuit suit, String key, IronManComicKit.Move m) {
		line(out, key, Component.translatable(m.nameKey()), moveStats(suit, m), m.descKey());
	}

	private static void line(List<Component> out, String key, Component name, String stats, String descKey) {
		MutableComponent head = Component.literal(" " + key + " ").withStyle(ChatFormatting.AQUA)
				.append(name.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
		if (!stats.isEmpty()) {
			head.append(Component.literal("  " + stats).withStyle(ChatFormatting.YELLOW));
		}
		out.add(head);
		out.add(Component.literal("      ").append(Component.translatable(descKey).withStyle(ChatFormatting.GRAY)));
	}

	/** "hold, 36 damage, 8s cooldown, 20 energy" -- straight from the kit (and the few numbers the move code keeps itself). */
	public static String moveStats(IronManSuit suit, IronManComicKit.Move m) {
		int cd = m.cooldownTicks();
		float cost = IronManComicKit.energyCost(m.id());
		switch (m.id()) {
			case "mk2_repulsor" -> cd = suit.repulsorTapCooldownTicks();
			case "mk6_energy_shield" -> {
				cd = IronManComicMoves.SHIELD_COOLDOWN_TICKS;
				cost = IronManComicMoves.SHIELD_ENERGY;
			}
			case "mk8_hydraulic_brace" -> {
				cd = IronManComicMoves.BRACE_COOLDOWN_TICKS;
				cost = IronManComicMoves.BRACE_ENERGY;
			}
			default -> {
			}
		}
		String s = stats(m.damage(), cd, cost, IronManComicKit.energyPerSecond(m.id()),
				m.input() == IronManComicKit.Input.HOLD && !TAP_LIKE_HOLDS.contains(m.id()));
		if ("mk2_repulsor".equals(m.id())) {
			s += ", " + Component.translatable(P + "move.charged", fmt(suit.chargedRepulsorDamage())).getString();
		}
		return s;
	}

	private static String stats(float damage, int cooldownTicks, float energy, float energyPerSecond, boolean hold) {
		List<String> parts = new ArrayList<>();
		if (hold) {
			parts.add(Component.translatable(P + "move.hold_tag").getString());
		}
		if (damage > 0f) {
			parts.add(Component.translatable(P + "move.damage", fmt(damage)).getString());
		}
		parts.add(cooldownTicks > 0 ? Component.translatable(P + "move.cooldown", fmt(cooldownTicks / 20.0)).getString()
				: Component.translatable(P + "move.no_cooldown").getString());
		if (energy > 0f) {
			parts.add(Component.translatable(P + "move.energy", fmt(energy)).getString());
		}
		if (energyPerSecond > 0f) {
			parts.add(Component.translatable(P + "move.energy_per_second", fmt(energyPerSecond)).getString());
		}
		if (energy <= 0f && energyPerSecond <= 0f) {
			parts.add(Component.translatable(P + "move.no_energy").getString());
		}
		return String.join(", ", parts);
	}

	// ------------------------------------------------------------------ passives

	/** One bullet per passive: the shared rules with this suit's numbers, then its own specials. */
	public static List<Component> passives(IronManSuit suit) {
		List<Component> out = new ArrayList<>();
		out.add(bullet(Component.translatable(P + "passive.absorb", Math.round(suit.absorbShare() * 100f), fmt(suit.maxIntegrity()))));
		if (suit.hasReactor()) {
			out.add(bullet(Component.translatable(P + "passive.reactor", fmt(suit.energyCapacity()), fmt(suit.energyRegenPerSecond()))));
		}
		if (suit.manualFlight()) {
			out.add(bullet(suit.sprintFlight() && suit.flightSprintBps() > suit.flightNormalBps()
					? Component.translatable(P + "passive.flight", fmt(suit.flightNormalBps()), fmt(suit.flightSprintBps()))
					: Component.translatable(P + "passive.flight_hover", fmt(suit.flightNormalBps()))));
		} else {
			out.add(bullet(Component.translatable(P + "passive.no_flight")));
		}
		out.add(bullet(Component.translatable(P + "passive.armour")));
		out.add(bullet(Component.translatable(P + "passive.strength", fmt(suit.strengthBonus()),
				roman(IronManPassives.hasteAmplifier(suit) + 1))));
		if (suit.eraOne()) {
			out.add(bullet(Component.translatable(P + "passive.repair_gantry")));
		} else if (suit.selfRepairPerSecond() > 0f) {
			out.add(bullet(Component.translatable(P + "passive.repair_self", fmt(suit.selfRepairPerSecond()))));
		} else {
			out.add(bullet(Component.translatable(P + "passive.repair_platform")));
		}
		if (suit.resistanceAmplifier() >= 0) {
			out.add(bullet(Component.translatable(P + "passive.resistance", roman(suit.resistanceAmplifier() + 1))));
		}
		if (suit.waterBreathing()) {
			out.add(bullet(Component.translatable(P + "passive.water_breathing")));
		} else if (suit.airTankSeconds() > 0) {
			out.add(bullet(Component.translatable(P + "passive.air_tank", suit.airTankSeconds())));
		} else {
			out.add(bullet(Component.translatable(P + "passive.no_air")));
		}
		if (suit.helmetNightVision()) {
			out.add(bullet(Component.translatable(P + "passive.night_vision")));
		}
		if (suit.autoFeed()) {
			out.add(bullet(Component.translatable(P + "passive.auto_feed")));
		}
		if (IronManTargeting.hasTargeting(suit)) {
			out.add(bullet(Component.translatable(P + "passive.targeting")));
		}
		out.add(bullet(Component.translatable(P + ("mark_1".equals(suit.id()) ? "passive.system" : "passive.jarvis"))));
		out.add(bullet(Component.translatable(P + suit.id() + ".passive")));
		return out;
	}

	// ------------------------------------------------------------------ HUD

	/** What the helmet HUD shows in this suit. */
	public static List<Component> hud(IronManSuit suit) {
		List<Component> out = new ArrayList<>();
		out.add(bullet(Component.translatable(P + "hud.integrity")));
		if (suit.hasReactor()) {
			out.add(bullet(Component.translatable(P + "hud.reactor")));
		}
		out.add(bullet(Component.translatable(P + "hud.keys")));
		if (!"mark_1".equals(suit.id())) {
			out.add(bullet(Component.translatable(P + "hud.scanner")));
		}
		out.add(bullet(Component.translatable(P + "hud." + (suit.eraOne() ? "eject" : "fold"))));
		if (HUD_EXTRA.contains(suit.id())) {
			out.add(bullet(Component.translatable(P + suit.id() + ".hud")));
		}
		out.add(bullet(Component.translatable(P + "hud.faceplate")));
		return out;
	}

	// ------------------------------------------------------------------ no suit on

	/**
	 * Tony Stark with no suit on (the I page, before the Iron Man chapter): how to get and wear a suit, then every suit in order
	 * with its era and how you get it.
	 */
	public static List<Component> unsuited() {
		List<Component> out = new ArrayList<>();
		out.add(Component.translatable(P + "unsuited.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		para(out, P + "unsuited.body");
		para(out, P + "unsuited.wear");
		out.add(Component.empty());
		out.add(Component.translatable(P + "unsuited.suits").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		boolean era2 = false;
		out.add(Component.translatable(P + "era.era1").withStyle(ChatFormatting.DARK_AQUA));
		for (IronManSuit s : progression()) {
			if (!s.eraOne() && !era2) {
				era2 = true;
				out.add(Component.translatable(P + "era.era2").withStyle(ChatFormatting.DARK_AQUA));
			}
			out.add(Component.literal("  ").append(fullName(s).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
					.append(Component.literal(" -- ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.translatable(P + s.id() + ".obtain").withStyle(ChatFormatting.GRAY)));
		}
		out.add(Component.empty());
		para(out, P + "unsuited.more");
		return out;
	}
}
