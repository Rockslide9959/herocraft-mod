package com.projecthero.mod.omnitrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 (user: "pressing I while transformed should also show information about the aliens and their abilities"): the ONE
 * place the Omnitrix's reference text is built -- the "Your Power" screen (I) and the Guidebook's Omnitrix chapter both call it,
 * so neither can drift from the kits. Everything comes from the registry ({@link Aliens}), each kit's {@link AlienKit#stats()}
 * and {@link AlienKit#moves()} (names, descriptions, cooldowns), {@link OmnitrixConfig}'s timings and the lang
 * ({@code projecthero.omnitrix.info.*}: the labels, and per alien {@code .<id>.role}, {@code .<id>.passive.<n>},
 * {@code .<id>.weakness.<n>}).
 *
 * <p>Every line is a {@link Component}; the caller wraps it (TooltipWrap rule). Section headings are bold gold, like the Quirk
 * and Green Lantern pages. {@code keys} turns a slot into its key label: the player's live bindings on the screen,
 * {@link #LETTERS} in the guide. Client-safe: only synced state is read.
 */
public final class OmnitrixInfo {
	private static final String I = "projecthero.omnitrix.info.";
	/** The kit keys in the HUD's order: R, G, X, Z, C (V is the Omnitrix's; H / N are never used). */
	public static final AbilitySlot[] KIT_SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4,
			AbilitySlot.SLOT_6 };
	private static final int BAR_CELLS = 20;

	/** The guide's key labels: the default letters. */
	public static final Function<AbilitySlot, Component> LETTERS = s -> Component.literal(String.valueOf(s.defaultKey()));

	private OmnitrixInfo() {
	}

	// ---------------------------------------------------------------- lang keys (tests read them)

	public static String nameKey(String alien, AlienMove m) {
		return "projecthero.omnitrix." + alien + "." + m.id();
	}

	public static String descKey(String alien, AlienMove m) {
		return nameKey(alien, m) + ".desc";
	}

	public static String roleKey(String alien) {
		return I + alien + ".role";
	}

	// ---------------------------------------------------------------- live status

	/** Live: transformed (with a time bar), recharging, or ready; the dial's selection and whether the watch is up. */
	public static void status(List<Component> out, Player live) {
		OmnitrixState s = Omnitrix.state(live);
		out.add(head("status"));
		if (s.transformed()) {
			Aliens.Alien a = Aliens.byId(s.active);
			int left = Omnitrix.timeLeft(live);
			boolean warn = left <= OmnitrixConfig.WARN_TICKS;
			out.add(Component.translatable(I + "as", a == null ? Component.literal(s.active) : alienName(a))
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			out.add(Component.literal(" ").append(cells(left / (float) OmnitrixConfig.TRANSFORM_TICKS, warn ? ChatFormatting.RED : ChatFormatting.GREEN))
					.append(Component.literal("  ")).append(Component.translatable(I + "time_left", clock(left))
							.withStyle(warn ? ChatFormatting.RED : ChatFormatting.WHITE)));
		} else {
			int recharge = Omnitrix.rechargeLeft(live);
			if (recharge > 0) {
				out.add(Component.translatable(I + "recharging", (recharge + 19) / 20).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
				out.add(Component.literal(" ").append(cells(recharge / (float) OmnitrixConfig.RECHARGE_TICKS, ChatFormatting.RED)));
			} else {
				out.add(Component.translatable(I + "ready").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			}
			Aliens.Alien sel = Aliens.byId(s.selected);
			if (sel != null) {
				out.add(Component.translatable(I + "selected", alienName(sel)).withStyle(ChatFormatting.GRAY));
			}
			if (s.selecting) {
				out.add(Component.translatable(I + "watch_up").withStyle(ChatFormatting.DARK_GREEN));
			}
		}
	}

	// ---------------------------------------------------------------- the watch

	/** The Omnitrix's own keys: V (raise / lower), the scroll wheel, a click, V as an alien. */
	public static void controls(List<Component> out, Function<AbilitySlot, Component> keys) {
		Component v = keys.apply(AbilitySlot.SLOT_5);
		out.add(head("controls"));
		out.add(keyCell(v).append(Component.translatable(I + "key.raise").withStyle(ChatFormatting.WHITE)));
		out.add(keyCell(Component.translatable(I + "key.scroll_label")).append(Component.translatable(I + "key.scroll").withStyle(ChatFormatting.WHITE)));
		out.add(keyCell(Component.translatable(I + "key.click_label")).append(Component.translatable(I + "key.slap").withStyle(ChatFormatting.WHITE)));
		out.add(keyCell(v).append(Component.translatable(I + "key.revert", OmnitrixConfig.EARLY_RECHARGE_TICKS / 20).withStyle(ChatFormatting.WHITE)));
		out.add(Component.translatable(I + "key.kit").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** The timeout, the warning, the recharge and the early revert, from {@link OmnitrixConfig}. */
	public static void rules(List<Component> out, Function<AbilitySlot, Component> keys) {
		out.add(head("rules"));
		out.add(bullet(Component.translatable(I + "rule.timeout", clock(OmnitrixConfig.TRANSFORM_TICKS), OmnitrixConfig.WARN_TICKS / 20)));
		out.add(bullet(Component.translatable(I + "rule.recharge", OmnitrixConfig.RECHARGE_TICKS / 20)));
		out.add(bullet(Component.translatable(I + "rule.early", keys.apply(AbilitySlot.SLOT_5), OmnitrixConfig.EARLY_RECHARGE_TICKS / 20)));
		out.add(bullet(Component.translatable(I + "rule.death")));
		out.add(bullet(Component.translatable(I + "rule.locked")));
	}

	/** The Containment Pod warning, in red. */
	public static void containment(List<Component> out) {
		out.add(Component.translatable(I + "containment").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		out.add(Component.translatable(I + "containment.body", OmnitrixConfig.EXTRACTION_TICKS / 20).withStyle(ChatFormatting.RED));
	}

	// ---------------------------------------------------------------- the roster

	/**
	 * A compact list of every alien on the dial: the name in its colour, its one-line role, and its five tap moves on one line
	 * ("R Fire Blast  G Pyro Bomb ..."); "coming soon" for an alien without a kit. {@code selected} (may be null) is marked.
	 */
	public static void roster(List<Component> out, Function<AbilitySlot, Component> keys, @Nullable String selected) {
		out.add(head("roster"));
		out.add(Component.translatable(I + "roster.how").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		for (Aliens.Alien a : Aliens.all()) {
			AlienKit kit = Aliens.kit(a.id());
			MutableComponent title = Component.literal(" ").append(alienName(a).withStyle(ChatFormatting.BOLD));
			if (a.id().equals(selected)) {
				title.append(Component.literal("  ")).append(Component.translatable(I + "roster.selected").withStyle(ChatFormatting.GREEN));
			}
			if (kit == null) {
				title.append(Component.literal("  ")).append(Component.translatable("projecthero.guide.omnitrix.coming_soon").withStyle(ChatFormatting.DARK_GRAY));
				out.add(title);
				continue;
			}
			out.add(title);
			out.add(Component.literal("   ").append(Component.translatable(roleKey(a.id())).withStyle(ChatFormatting.GRAY)));
			// R G X on one line, Z C on the next: a pair never splits from its key at the edge
			MutableComponent moves = Component.literal("   ");
			int onLine = 0;
			for (AbilitySlot slot : KIT_SLOTS) {
				AlienMove m = kit.moveOn(slot, false, false);
				if (m == null) {
					continue;
				}
				if (onLine == 3) {
					out.add(moves);
					moves = Component.literal("   ");
					onLine = 0;
				}
				if (onLine > 0) {
					moves.append(Component.literal("   "));
				}
				onLine++;
				moves.append(Component.empty().append(keys.apply(slot)).withStyle(ChatFormatting.GOLD)).append(Component.literal(" "))
						.append(Component.translatable(nameKey(a.id(), m)).withStyle(ChatFormatting.WHITE));
			}
			out.add(moves);
			AlienStats st = kit.stats();
			out.add(Component.literal("   ").append(Component.translatable(I + "roster.stats", fmt(st.maxHealth()), fmt(st.speed()), fmt(st.scale()),
					fmt(st.armor())).withStyle(ChatFormatting.DARK_GREEN)));
		}
	}

	// ---------------------------------------------------------------- one alien's page

	/**
	 * One alien in full: the name, the role and the guide's description, the body (every stat that differs from a player),
	 * passives, weaknesses, and every move per key (tap / Shift / hold, then the attack / use moves) with its cooldown and
	 * description. {@code live} (may be null) adds each move's cooldown left. Nothing but the name for a "coming soon" alien.
	 */
	public static void alienPage(List<Component> out, Aliens.Alien a, Function<AbilitySlot, Component> keys, @Nullable Player live) {
		AlienKit kit = Aliens.kit(a.id());
		out.add(alienName(a).withStyle(ChatFormatting.BOLD));
		if (kit == null) {
			out.add(Component.translatable("projecthero.guide.omnitrix.coming_soon").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		out.add(Component.translatable(roleKey(a.id())).withStyle(s -> s.withColor(a.colour()).withItalic(true)));
		out.add(Component.translatable("projecthero.guide.omnitrix.alien." + a.id()).withStyle(ChatFormatting.GRAY));
		out.add(Component.empty());
		body(out, kit.stats());
		out.add(Component.empty());
		listSection(out, "passives", I + a.id() + ".passive.", ChatFormatting.GREEN);
		out.add(Component.empty());
		listSection(out, "weaknesses", I + a.id() + ".weakness.", ChatFormatting.RED);
		out.add(Component.empty());
		moves(out, a.id(), kit, keys, live);
	}

	/** The body: every stat as the alien has it (health, speed, size, armour...), the extras only when they differ. */
	public static void body(List<Component> out, AlienStats st) {
		out.add(head("body"));
		List<Component> cells = new ArrayList<>();
		cells.add(stat("health", fmt(st.maxHealth())));
		cells.add(stat("speed", fmt(st.speed())));
		cells.add(stat("size", fmt(st.scale())));
		cells.add(stat("armor", fmt(st.armor())));
		if (st.toughness() > 0f) {
			cells.add(stat("toughness", fmt(st.toughness())));
		}
		cells.add(stat("kb", Math.round(st.knockbackResist() * 100)));
		cells.add(stat("fist", fmt(1f + st.attack())));
		if (st.reach() != 0f) {
			cells.add(stat("reach", fmt(st.reach())));
		}
		if (st.step() != 0f) {
			cells.add(stat("step", fmt(st.step())));
		}
		if (st.jump() != 0f) {
			cells.add(stat("jump", fmt(st.jump())));
		}
		if (st.safeFall() != 0f) {
			cells.add(stat("safe_fall", fmt(st.safeFall())));
		}
		if (st.gravity() != 1f) {
			cells.add(stat("gravity", fmt(st.gravity())));
		}
		// two to a line, so the column stays short
		for (int i = 0; i < cells.size(); i += 2) {
			MutableComponent row = Component.literal(" ").append(cells.get(i));
			if (i + 1 < cells.size()) {
				row.append(Component.literal("   ")).append(cells.get(i + 1));
			}
			out.add(row);
		}
	}

	/** Every move per key: R, G, X, Z, C (tap, Shift, hold), then the vanilla-key moves; name, cooldown, description. */
	public static void moves(List<Component> out, String alien, AlienKit kit, Function<AbilitySlot, Component> keys, @Nullable Player live) {
		out.add(head("moves"));
		out.add(Component.translatable(I + "moves.how").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		for (AbilitySlot slot : KIT_SLOTS) {
			Component k = keys.apply(slot);
			for (AlienMove m : kit.moves()) {
				if (m.slot() == slot && !m.shift() && !m.hold()) {
					moveRow(out, alien, kit, m, k, live);
				}
			}
			for (AlienMove m : kit.moves()) {
				if (m.slot() == slot && m.shift()) {
					moveRow(out, alien, kit, m, Component.translatable(I + "shift", k), live);
				}
			}
			for (AlienMove m : kit.moves()) {
				if (m.slot() == slot && m.hold()) {
					moveRow(out, alien, kit, m, Component.translatable(I + "hold", k), live);
				}
			}
		}
		boolean vanilla = false;
		for (AlienMove m : kit.moves()) {
			if (!m.onSlot()) {
				if (!vanilla) {
					out.add(Component.translatable(I + "vanilla").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
					vanilla = true;
				}
				moveRow(out, alien, kit, m, Component.literal(m.keyLabel()), live);
			}
		}
	}

	private static void moveRow(List<Component> out, String alien, AlienKit kit, AlienMove m, Component key, @Nullable Player live) {
		MutableComponent row = keyCell(key).append(Component.translatable(nameKey(alien, m)).withStyle(ChatFormatting.WHITE));
		if (m.cooldown() > 0) {
			row.append(Component.literal("  ")).append(Component.translatable(I + "cooldown", seconds(m.cooldown())).withStyle(ChatFormatting.DARK_AQUA));
		}
		if (live != null && m.cooldown() > 0) {
			int left = kit.cooldownLeft(live, m.id());
			if (left > 0) {
				row.append(Component.literal("  ")).append(Component.translatable(I + "cooldown_left", seconds(left)).withStyle(ChatFormatting.RED));
			}
		}
		out.add(row);
		out.add(Component.literal("   ").append(Component.translatable(descKey(alien, m)).withStyle(ChatFormatting.GRAY)));
	}

	// ---------------------------------------------------------------- helpers

	private static void listSection(List<Component> out, String heading, String prefix, ChatFormatting colour) {
		out.add(head(heading));
		int n = 0;
		for (int i = 1; i <= 12 && has(prefix + i); i++) {
			out.add(Component.literal(" • ").withStyle(colour).append(Component.translatable(prefix + i).withStyle(ChatFormatting.GRAY)));
			n++;
		}
		if (n == 0) {
			out.add(Component.literal(" ").append(Component.translatable(I + "none")).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	public static MutableComponent alienName(Aliens.Alien a) {
		return Component.translatable(a.nameKey()).withStyle(s -> s.withColor(a.colour()));
	}

	private static MutableComponent head(String key) {
		return Component.translatable(I + key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
	}

	private static MutableComponent bullet(Component text) {
		return Component.literal(" • ").withStyle(ChatFormatting.DARK_GREEN).append(Component.empty().append(text).withStyle(ChatFormatting.GRAY));
	}

	private static MutableComponent stat(String key, Object value) {
		return Component.translatable(I + "stat." + key).withStyle(ChatFormatting.GRAY).append(Component.literal(" "))
				.append(Component.translatable(I + "stat." + key + ".value", value).withStyle(ChatFormatting.WHITE));
	}

	private static MutableComponent keyCell(Component key) {
		return Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.empty().append(key).withStyle(ChatFormatting.GOLD))
				.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
	}

	private static MutableComponent cells(float frac, ChatFormatting colour) {
		int full = Math.round(BAR_CELLS * Math.max(0f, Math.min(1f, frac)));
		return Component.literal("█".repeat(full)).withStyle(colour)
				.append(Component.literal("█".repeat(BAR_CELLS - full)).withStyle(ChatFormatting.DARK_GRAY));
	}

	private static boolean has(String key) {
		return net.minecraft.locale.Language.getInstance().has(key);
	}

	/** "3:00", "0:42". */
	public static String clock(int ticks) {
		int secs = Math.max(0, ticks) / 20;
		return String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
	}

	private static String seconds(int ticks) {
		return fmt(ticks / 20f);
	}

	/** "1.5", "12", "0.33". */
	public static String fmt(float v) {
		if (Math.abs(v - Math.round(v)) < 0.001f) {
			return String.valueOf(Math.round(v));
		}
		String s = String.format(Locale.ROOT, "%.2f", v);
		return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
	}
}
