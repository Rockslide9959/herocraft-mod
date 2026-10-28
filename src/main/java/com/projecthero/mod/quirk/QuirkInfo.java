package com.projecthero.mod.quirk;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.item.QuirkSuits;
import com.projecthero.mod.quirk.meter.QuirkBand;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;
import com.projecthero.mod.quirk.meter.QuirkMeters;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;

/**
 * v0.17.1 (user: "update the menus for the quirks ... more information ... include the quirk combos"): the ONE place a kit
 * Quirk's reference text is built from its live data -- the registered {@link QuirkMeterSpec} (bands, damage, push past 100,
 * pacing), {@link PlusUltra}'s numbers and the kit's ultimate, the kit's keys ({@link QuirkKit#idsOf} + {@link
 * QuirkKit#holdIdOf}, cooldowns from {@link QuirkKit#maxCooldown}), its {@link QuirkCombos} routes, and the lang for the
 * rest. The Guidebook chapter ({@code HeroPackGuide.quirk}) calls it with {@code live == null}; the "Your Power" screen calls
 * it with the local player, which adds the current meter, band, Plus Ultra gauge, strain and suit status. Client-safe: it
 * only reads synced state.
 *
 * <p>Every line is a {@link Component}; the caller wraps it ({@code TooltipWrap} rule). Section headings are bold gold (the
 * guide's search treats them as headings). Lang: {@code projecthero.quirk.info.*} for the shared labels, and per Quirk
 * {@code projecthero.quirk.<q>.fills}, {@code .passive.<n>}, {@code .survival.<n>}, {@code .suit_perk} and
 * {@code projecthero.quirk.band.<q>.<band>.desc}.
 */
public final class QuirkInfo {
	private static final String I = "projecthero.quirk.info.";
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4,
			AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 };
	private static final int BAR_CELLS = 20;

	/** The guide's key labels: the default letters. */
	public static final Function<AbilitySlot, Component> LETTERS = s -> Component.literal(String.valueOf(s.defaultKey()));

	private QuirkInfo() {
	}

	// ---------------------------------------------------------------- header

	/** The tier line ("Quirk (Primary power): ...") and the one-line identity. */
	public static void header(List<Component> out, Quirk q) {
		out.add(Component.translatable("projecthero.guide.quirk." + q.id + ".tier").withStyle(s -> s.withColor(q.meterColour & 0xFFFFFF)));
		out.add(Component.translatable("projecthero.quirk." + q.id + ".intro").withStyle(ChatFormatting.GRAY));
	}

	/** Live only: Erased / overloaded / pushing past 100, in red at the top. Nothing when all is well. */
	public static void alerts(List<Component> out, Quirk q, Player live) {
		if (com.projecthero.mod.hero.PowerSuppression.isSuppressed(live)) {
			out.add(Component.translatable(I + "erased").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}
		if (QuirkMeters.beyond(live)) {
			QuirkMeterSpec spec = QuirkMeters.spec(q);
			float left = QuirkMeters.beyondLeft(live) * (spec == null ? 0 : spec.pushTicks) / 20f;
			out.add(Component.translatable(I + "pushing", fmt(left)).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		} else if (Quirks.overloaded(live)) {
			out.add(Component.translatable(I + "overloaded", Component.translatable("projecthero.guide.quirk." + q.id + ".overload"),
					(Quirks.overloadLeft(live) + 19) / 20).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}
	}

	// ---------------------------------------------------------------- the meter

	/** The meter: live value and band (when {@code live}), every band with its range, damage and effect, push / crash, pacing. */
	public static void meter(List<Component> out, Quirk q, @Nullable Player live) {
		Component meterName = Component.translatable("projecthero.quirk.meter." + q.meterId);
		out.add(Component.translatable(I + "meter", meterName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		QuirkMeterSpec spec = QuirkMeters.spec(q);
		if (spec == null) {
			out.add(Component.translatable("projecthero.quirk." + q.id + ".fills").withStyle(ChatFormatting.GRAY));
			return;
		}
		String b = "projecthero.quirk.band." + q.id + ".";
		boolean spends = spec.direction == QuirkMeterSpec.Direction.SPENDS;
		boolean allOne = true;
		for (float d : spec.damage) {
			allOne &= Math.abs(d - 1f) < 0.001f;
		}
		if (live != null) {
			float v = Math.max(0f, Math.min(100f, Quirks.clientMeter(live)));
			QuirkBand band = QuirkMeters.beyond(live) ? QuirkBand.BEYOND : QuirkMeters.clientBand(live);
			out.add(Component.empty().append(Component.translatable(I + "now").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(" " + Math.round(v) + (band == QuirkBand.BEYOND ? "+" : "") + " / 100  ").withStyle(ChatFormatting.WHITE))
					.append(bar(spec, v))
					.append(Component.literal("  "))
					.append(Component.translatable(b + spec.names[band.ordinal()]).withStyle(bandColour(spec, band), ChatFormatting.BOLD)));
			MutableComponent effect = Component.literal("   ");
			if (!allOne) {
				effect.append(Component.translatable(I + "damage", fmt(spec.damageFor(band))).withStyle(ChatFormatting.AQUA)).append(Component.literal("  "));
			}
			effect.append(desc(b + spec.names[band.ordinal()] + ".desc"));
			out.add(effect);
		}
		out.add(Component.translatable(spends ? I + "spends" : !spec.channelIsMeter ? I + "fuel" : I + "builds").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		int sweet = Math.round(spec.sweetFrom);
		int red = Math.round(spec.redFrom);
		out.add(bandRow(spec, QuirkBand.LOW, b, "0-" + (sweet - 1), allOne));
		out.add(Component.literal("   ").append(desc(b + spec.names[0] + ".desc")));
		out.add(bandRow(spec, QuirkBand.SWEET, b, sweet + "-" + (red - 1), allOne));
		out.add(Component.literal("   ").append(desc(b + spec.names[1] + ".desc")));
		out.add(bandRow(spec, QuirkBand.RED, b, red + "-100", allOne));
		out.add(Component.literal("   ").append(desc(b + spec.names[2] + ".desc")));
		String fourth = b + spec.names[3] + ".desc";
		if (spec.pushPast) {
			out.add(bandRow(spec, QuirkBand.BEYOND, b, Component.translatable(I + "past_100").getString(), allOne));
			out.add(Component.literal("   ").append(Component.translatable(I + "push", fmt(spec.pushTicks / 20f), fmt(spec.pushHpPerSecond),
					fmt(spec.crashTicks / 20f)).withStyle(ChatFormatting.LIGHT_PURPLE)));
			out.add(Component.literal("   ").append(desc(fourth)));
		} else if (has(fourth)) {
			out.add(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.translatable(b + spec.names[3]).withStyle(ChatFormatting.RED))
					.append(Component.literal("  ").append(Component.translatable(I + "at_100")).withStyle(ChatFormatting.DARK_GRAY)));
			out.add(Component.literal("   ").append(desc(fourth)));
		}
		// a drawback channel that is not the HUD meter (Explosion's Arm Strain)
		if (!spec.channelIsMeter) {
			Component ch = Component.translatable("projecthero.quirk." + q.id + ".channel");
			out.add(Component.translatable(I + "channel", ch, fmt(spec.danger), fmt(spec.safe)).withStyle(ChatFormatting.RED));
			if (live != null && q == Quirk.EXPLOSION) {
				float s = Quirks.overloaded(live) ? 100f : Math.max(0f, Math.min(100f, com.projecthero.mod.quirk.explosion.Explosion.strain(live)));
				out.add(Component.empty().append(Component.literal("   " + Math.round(s) + " / 100  ").withStyle(ChatFormatting.WHITE))
						.append(cells(s / 100f, s >= spec.danger ? ChatFormatting.RED : ChatFormatting.GOLD)));
			}
		}
		out.add(Component.translatable(I + "fills_head").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(" ").append(Component.translatable("projecthero.quirk." + q.id + ".fills")).withStyle(ChatFormatting.GRAY)));
		MutableComponent pacing = Component.translatable(I + "pacing", fmt(spec.buildMult), fmt(spec.recoverMult));
		if (spec.suitBuildMult != 1f) {
			pacing.append(Component.literal(" ")).append(Component.translatable(I + "pacing_suit", fmt(spec.suitBuildMult)));
		}
		out.add(pacing.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		// the danger / overload rules, from the chapter's own text
		out.add(Component.translatable("projecthero.guide.quirk." + q.id + ".overload").withStyle(ChatFormatting.RED)
				.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable("projecthero.guide.quirk." + q.id + ".overload.body").withStyle(ChatFormatting.GRAY)));
	}

	private static Component bandRow(QuirkMeterSpec spec, QuirkBand band, String b, String range, boolean allOne) {
		MutableComponent row = Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.translatable(b + spec.names[band.ordinal()]).withStyle(bandColour(spec, band)))
				.append(Component.literal("  " + range).withStyle(ChatFormatting.DARK_GRAY));
		if (!allOne) {
			row.append(Component.literal("  ")).append(Component.translatable(I + "damage", fmt(spec.damageFor(band))).withStyle(ChatFormatting.AQUA));
		}
		return row;
	}

	/** A band's colour: the safe band grey, the sweet spot green, the danger red (a SPENDS meter's top band is its gold bonus). */
	public static ChatFormatting bandColour(QuirkMeterSpec spec, QuirkBand band) {
		boolean spends = spec.direction == QuirkMeterSpec.Direction.SPENDS;
		boolean topIsDanger = !spends && spec.channelIsMeter;
		return switch (band) {
			case LOW -> spends ? ChatFormatting.RED : ChatFormatting.GRAY;
			case SWEET -> ChatFormatting.GREEN;
			case RED -> topIsDanger ? ChatFormatting.RED : ChatFormatting.GOLD;
			case BEYOND -> ChatFormatting.LIGHT_PURPLE;
		};
	}

	/** A 20-cell text bar, each filled cell in the colour of the band it sits in. */
	private static Component bar(QuirkMeterSpec spec, float v) {
		MutableComponent out = Component.empty();
		int full = Math.round(BAR_CELLS * v / 100f);
		for (int i = 0; i < BAR_CELLS; i++) {
			float at = (i + 0.5f) * 100f / BAR_CELLS;
			out.append(Component.literal("|").withStyle(i < full ? bandColour(spec, spec.bandOf(at)) : ChatFormatting.DARK_GRAY));
		}
		return out;
	}

	private static Component cells(float frac, ChatFormatting colour) {
		int full = Math.round(BAR_CELLS * Math.max(0f, Math.min(1f, frac)));
		return Component.literal("|".repeat(full)).withStyle(colour)
				.append(Component.literal("|".repeat(BAR_CELLS - full)).withStyle(ChatFormatting.DARK_GRAY));
	}

	// ---------------------------------------------------------------- Plus Ultra

	public static void plusUltra(List<Component> out, Quirk q, @Nullable Player live, Function<AbilitySlot, Component> keys) {
		QuirkKit kit = QuirkKits.of(q);
		out.add(Component.translatable(I + "plus_ultra").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (!kit.hasUltimate()) {
			out.add(Component.translatable(I + "plus_ultra.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		Component c = keys.apply(AbilitySlot.SLOT_6);
		if (live != null) {
			float g = Math.max(0f, Math.min(PlusUltra.MAX, PlusUltra.clientGauge(live)));
			boolean full = g >= PlusUltra.MAX;
			out.add(Component.empty().append(Component.translatable(I + "gauge").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(" " + Math.round(g) + " / 100  ").withStyle(ChatFormatting.WHITE))
					.append(cells(g / PlusUltra.MAX, full ? ChatFormatting.GOLD : ChatFormatting.YELLOW))
					.append(Component.literal("  "))
					.append(full ? Component.translatable(I + "gauge.full", c).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
							: Component.translatable(I + "gauge.filling").withStyle(ChatFormatting.DARK_GRAY)));
		}
		out.add(Component.translatable(I + "ultimate", c, fmt(kit.ultimateHoldTicks() / 20f)).withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" "))
				.append(Component.translatable(kit.ultimateLangKey(q)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
		out.add(Component.literal("   ").append(desc(kit.ultimateLangKey(q) + ".desc")));
		out.add(Component.translatable(I + "pu_fills", fmt(PlusUltra.HIT_BASE), fmt(PlusUltra.HIT_MAX), fmt(PlusUltra.HIT_INTERVAL / 20f),
				fmt(PlusUltra.TAKEN_PER_HP), fmt(PlusUltra.COMBO), fmt(PlusUltra.ALLY_SAVE)).withStyle(ChatFormatting.GRAY));
		out.add(Component.translatable(I + "pu_decay", PlusUltra.DECAY_DELAY / 20, fmt(PlusUltra.DECAY_PER_SECOND), c)
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	// ---------------------------------------------------------------- controls

	/** Every key: tap, Shift and hold move with cooldowns and descriptions; hold C at 100; Dark Shadow's N. Never H. */
	public static void controls(List<Component> out, Quirk q, Function<AbilitySlot, Component> keys, Component nKey) {
		QuirkKit kit = QuirkKits.of(q);
		out.add(Component.translatable(I + "controls").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		out.add(Component.translatable(I + "controls.how").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		int rows = 0;
		for (AbilitySlot slot : SLOTS) {
			String[] ids = kit.idsOf(slot);
			Component k = keys.apply(slot);
			for (int j = 0; j < Math.min(2, ids.length); j++) {
				moveRow(out, kit, j == 0 ? k : Component.translatable(I + "shift", k), ids[j]);
				rows++;
			}
			String hold = holdOf(kit, slot);
			if (hold != null) {
				moveRow(out, kit, Component.translatable(I + "hold", k), hold);
				rows++;
			}
		}
		if (rows == 0) {
			out.add(Component.translatable("projecthero.guide.quirks.moves.none").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (kit.hasUltimate()) {
			out.add(keyCell(Component.translatable(I + "hold_c_100", keys.apply(AbilitySlot.SLOT_6)))
					.append(Component.translatable(kit.ultimateLangKey(q)).withStyle(ChatFormatting.WHITE)));
		}
		if (q == Quirk.DARK_SHADOW) {
			// v0.16.7 (user): N hides Dark Shadow -- the one N move
			out.add(keyCell(nKey).append(Component.translatable(I + "hide_dark_shadow").withStyle(ChatFormatting.WHITE)));
			out.add(Component.literal("   ").append(desc("projecthero.guide.quirk.dark_shadow.hide")));
		}
	}

	private static void moveRow(List<Component> out, QuirkKit kit, Component key, String id) {
		MutableComponent row = keyCell(key).append(Component.translatable(kit.abilityLangKey(id)).withStyle(ChatFormatting.WHITE));
		int cd = kit.maxCooldown(id);
		if (cd > 0) {
			row.append(Component.literal("  ")).append(Component.translatable(I + "cooldown", seconds(cd)).withStyle(ChatFormatting.DARK_AQUA));
		}
		out.add(row);
		out.add(Component.literal("   ").append(desc(kit.abilityLangKey(id) + ".desc")));
	}

	private static MutableComponent keyCell(Component key) {
		return Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.empty().append(key).withStyle(ChatFormatting.GOLD))
				.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** A key's hold move: the kit's third {@link QuirkKit#idsOf} id or its {@link QuirkKit#holdIdOf}. */
	public static @Nullable String holdOf(QuirkKit kit, AbilitySlot slot) {
		String[] ids = kit.idsOf(slot);
		return ids.length > 2 ? ids[2] : kit.holdIdOf(slot);
	}

	/** "Shift+G" / "Hold X" / "R": the key a move of this kit is on, or null. */
	public static @Nullable Component keyOf(QuirkKit kit, String id, Function<AbilitySlot, Component> keys) {
		for (AbilitySlot slot : SLOTS) {
			String[] ids = kit.idsOf(slot);
			Component k = keys.apply(slot);
			if (ids.length > 0 && ids[0].equals(id)) {
				return k;
			}
			if (ids.length > 1 && ids[1].equals(id)) {
				return Component.translatable(I + "shift", k);
			}
			if (id.equals(holdOf(kit, slot))) {
				return Component.translatable(I + "hold", k);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- combos

	/** Every combo route: its name, opener -> finisher (with keys), window, bonus, same-target rule and effect. */
	public static void combos(List<Component> out, Quirk q, Function<AbilitySlot, Component> keys) {
		QuirkKit kit = QuirkKits.of(q);
		out.add(Component.translatable(I + "combos").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		List<QuirkCombos.Route> routes = QuirkCombos.routes(q);
		if (routes.isEmpty()) {
			out.add(Component.translatable(I + "combos.none").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		out.add(Component.translatable(I + "combos.how", fmt(PlusUltra.COMBO)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		for (QuirkCombos.Route r : routes) {
			out.add(Component.literal(" ◆ ").withStyle(s -> s.withColor(q.meterColour & 0xFFFFFF))
					.append(Component.translatable(r.nameKey()).withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE)));
			out.add(Component.literal("   ").append(move(kit, r.opener, keys))
					.append(Component.literal("  ->  ").withStyle(ChatFormatting.GOLD))
					.append(move(kit, r.finisher, keys)));
			out.add(Component.literal("   ")
					.append(Component.translatable(I + "combo.window", seconds(r.window)).withStyle(ChatFormatting.GRAY))
					.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.translatable(I + "combo.bonus", fmt(r.bonus)).withStyle(ChatFormatting.AQUA))
					.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.translatable(r.sameTarget ? I + "combo.same" : I + "combo.any")
							.withStyle(r.sameTarget ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY)));
			out.add(Component.literal("   ").append(desc(r.descKey())));
		}
	}

	private static Component move(QuirkKit kit, String id, Function<AbilitySlot, Component> keys) {
		MutableComponent m = Component.translatable(kit.abilityLangKey(id)).withStyle(ChatFormatting.WHITE);
		Component k = keyOf(kit, id, keys);
		if (k != null) {
			m.append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY)).append(Component.empty().append(k).withStyle(ChatFormatting.GOLD))
					.append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
		}
		return m;
	}

	// ---------------------------------------------------------------- passives, survival, suit

	public static void passives(List<Component> out, Quirk q) {
		out.add(Component.translatable(I + "passives").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		bullets(out, "projecthero.quirk." + q.id + ".passive.");
	}

	public static void survival(List<Component> out, Quirk q) {
		out.add(Component.translatable(I + "survival").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		bullets(out, "projecthero.quirk." + q.id + ".survival.");
	}

	/** The costume set perk and (live) whether the full set is on. */
	public static void suitPerk(List<Component> out, Quirk q, @Nullable Player live) {
		out.add(Component.translatable(I + "suit").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		out.add(Component.translatable("projecthero.quirk." + q.id + ".suit_perk").withStyle(ChatFormatting.AQUA));
		if (live != null) {
			QuirkSuits.Suit suit = QuirkSuits.Suit.of(q);
			int worn = 0;
			int all = 0;
			for (ArmorItem.Type t : suit.pieces()) {
				all++;
				if (QuirkSuits.wornIn(live, t.getSlot()) == q) {
					worn++;
				}
			}
			out.add(worn >= all && QuirkPerks.fullSet(live)
					? Component.translatable(I + "suit.on").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
					: Component.translatable(I + "suit.off", worn, all).withStyle(ChatFormatting.GRAY));
		}
	}

	/** {@code prefix1}, {@code prefix2}, ... while the lang has them, as bullets. */
	public static int bullets(List<Component> out, String prefix) {
		int n = 0;
		while (has(prefix + (n + 1))) {
			n++;
			out.add(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.translatable(prefix + n).withStyle(ChatFormatting.GRAY)));
		}
		return n;
	}

	// ---------------------------------------------------------------- helpers

	private static Component desc(String key) {
		return Component.translatable(key).withStyle(ChatFormatting.GRAY);
	}

	private static boolean has(String key) {
		return net.minecraft.locale.Language.getInstance().has(key);
	}

	/** "1.5", "12", "0.12". */
	public static String fmt(float v) {
		if (Math.abs(v - Math.round(v)) < 0.001f) {
			return String.valueOf(Math.round(v));
		}
		String s = String.format(Locale.ROOT, "%.2f", v);
		return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
	}

	/** Ticks as seconds, "12" or "1.5". */
	public static String seconds(int ticks) {
		return fmt(ticks / 20f);
	}
}
