package com.projecthero.mod.ironman.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;

/**
 * v0.14.29 (agent D): the Call Armour picker's side-by-side compare panel -- pure layout + numbers (no client classes)
 * so the gametests can check it fits 320 x 240 and 426 x 240 (GUI scale 4 / 3 at 720p) exactly like
 * {@link IronManUiLayout}.
 *
 * <p>The picker's card grid now uses the screen minus the panel ({@link #gridAreaWidth}); the panel sits to the right
 * of it over the same viewport rows. It compares the hovered / focused card (column A) against the worn suit, else a
 * right-click-pinned card, else the last active suit (column B): energy, integrity, current charge / condition, melee
 * bonus, energy regen, self-repair and flight speed, then the hovered suit's key abilities, word-wrapped.
 */
public final class IronManSuitCompare {
	public static final int PANEL_W = 150;
	public static final int PAD = 4;
	public static final int ROW_H = 10;
	public static final int LABEL_W = 52;
	/** Title line + the two suit-name headers. */
	public static final int HEADER_H = 2 * ROW_H + 2;

	private IronManSuitCompare() {
	}

	/** One compared figure. Every one of them reads "higher is better". */
	public enum Stat {
		ENERGY("energy"),
		INTEGRITY("integrity"),
		CHARGE("charge"),
		CONDITION("condition"),
		MELEE("melee"),
		REGEN("regen"),
		REPAIR("repair"),
		FLIGHT("flight");

		public final String key;

		Stat(String key) {
			this.key = "screen.projecthero.suit_call.cmp." + key;
		}
	}

	// ------------------------------------------------------------------ layout

	public static int innerWidth() {
		return PANEL_W - 2 * PAD;
	}

	/** Width of each of the two value columns. */
	public static int valueWidth() {
		return (innerWidth() - LABEL_W) / 2;
	}

	/** Width the card grid gets: the screen minus the panel, its margin and a gap. */
	public static int gridAreaWidth(int screenW) {
		return Math.max(IronManUiLayout.CARD_W + 2 * IronManUiLayout.GRID_MARGIN,
				screenW - PANEL_W - IronManUiLayout.GRID_MARGIN - IronManUiLayout.CARD_GAP);
	}

	public static Rect panel(int screenW, int screenH) {
		return new Rect(screenW - IronManUiLayout.GRID_MARGIN - PANEL_W, IronManUiLayout.GRID_TOP, PANEL_W,
				IronManUiLayout.gridViewportHeight(screenH));
	}

	public static int columns(int screenW) {
		return IronManUiLayout.gridColumns(gridAreaWidth(screenW));
	}

	public static Rect card(int index, int screenW, int scroll) {
		return IronManUiLayout.card(index, gridAreaWidth(screenW), scroll);
	}

	public static int maxScroll(int count, int screenW, int screenH) {
		return IronManUiLayout.gridMaxScroll(count, gridAreaWidth(screenW), screenH);
	}

	public static int scrollToShow(int index, int screenW, int screenH, int scroll, int count) {
		return IronManUiLayout.scrollToShow(index, gridAreaWidth(screenW), screenH, scroll, count);
	}

	/** Top of the "key abilities" block inside the panel, relative to the panel's top edge. */
	public static int abilitiesTop() {
		return PAD + HEADER_H + Stat.values().length * ROW_H + 4;
	}

	// ------------------------------------------------------------------ numbers

	/**
	 * The figure for {@code stat}; {@code energyFrac} / {@code integrityFrac} are the suit's live fractions (negative =
	 * unknown, giving -1).
	 */
	public static float value(IronManSuit suit, Stat stat, float energyFrac, float integrityFrac) {
		return switch (stat) {
			case ENERGY -> suit.energyCapacity();
			case INTEGRITY -> IronManEnergy.maxIntegrity(suit.id());
			case CHARGE -> energyFrac < 0 ? -1f : IronManUiLayout.clamp01(energyFrac) * 100f;
			case CONDITION -> integrityFrac < 0 ? -1f : IronManUiLayout.clamp01(integrityFrac) * 100f;
			case MELEE -> suit.strengthBonus();
			case REGEN -> suit.energyRegenPerSecond();
			case REPAIR -> suit.armorRegenPerSecond() * IronManEnergy.WORN_REGEN_SCALE;
			case FLIGHT -> (float) flightSpeed(suit);
		};
	}

	public static String format(Stat stat, float v) {
		if (v < 0) {
			return "--";
		}
		return switch (stat) {
			case ENERGY, INTEGRITY -> String.valueOf(Math.round(v));
			case CHARGE, CONDITION -> Math.round(v) + "%";
			case MELEE -> "+" + trim(v);
			case REGEN -> trim(v) + "/s";
			case REPAIR -> String.format(Locale.ROOT, "%.1f", v) + "/s";
			case FLIGHT -> Math.round(v) + " b/s";
		};
	}

	private static String trim(float v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
	}

	/**
	 * Top flight speed in blocks/second: the same {@link DirectionalFlightModel} tune the client flies with (sprint
	 * flight where the mark allows it), clamped to its ceiling.
	 */
	public static double flightSpeed(IronManSuit suit) {
		DirectionalFlightModel.Tune t = DirectionalFlightModel.ironManSuit(suit.flightSpeed(), suit.flightAcceleration(),
				suit.maxFlightSpeedMps(), suit.flightCruiseMps(), suit.sprintFlight(), false, 1.0);
		double speed = t.speed();
		if (t.maxHorizontal() > 0.0) {
			speed = Math.min(speed, t.maxHorizontal());
		}
		return speed * 20.0;
	}

	/** -1 / 0 / +1: is A worse / the same / better than B (unknown on either side = 0). */
	public static int better(float a, float b) {
		if (a < 0 || b < 0 || Math.abs(a - b) < 0.05f) {
			return 0;
		}
		return a > b ? 1 : -1;
	}

	/** The suit's key abilities (lang keys), in key order, skipping "store suit". */
	public static List<String> abilityKeys(IronManSuit suit) {
		List<String> out = new ArrayList<>();
		for (int slot = 1; slot <= 6; slot++) {
			String id = suit.abilityInSlot(slot);
			if (id == null || IronManAbilities.SUIT_TOGGLE.equals(id)) {
				continue;
			}
			// the Mark 7's wheel-bound slot has no name of its own (the HUD shows whichever weapon is picked)
			out.add(IronManAbilities.WEAPON_WHEEL_SLOT.equals(id) ? "screen.projecthero.suit_call.cmp.wheel_weapon"
					: "hud.projecthero.ironman.ability." + id);
		}
		return out;
	}
}
