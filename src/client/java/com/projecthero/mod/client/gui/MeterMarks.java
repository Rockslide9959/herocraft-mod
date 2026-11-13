package com.projecthero.mod.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * v0.17.3 (vanilla HUD move): 1-px tick marks on the meter row a {@link VanillaHud.Stack} has just drawn -- the Hulk's rage
 * tiers, Max Steel's Blast stages and overload line, Wolverine's skin-recovery half mark. Works out the boss bar's span the
 * same way {@link VanillaHud#meter} lays it out, so call it right after {@code stack.meter(label, ..., value, ...)} with the
 * same label and value (labels short enough not to be cut).
 */
final class MeterMarks {
	private MeterMarks() {
	}

	/** Marks at each fraction {@code at} (0..1) of the last meter's bar. */
	static void marks(VanillaHud.Stack st, Component label, String value, int colour, float... at) {
		Font font = st.font;
		int by = st.top() + 2; // the bar's top (Stack.meter leaves top at by - 2)
		int labelW = font.width(label);
		int valueW = Math.max(font.width("0:00"), font.width(value));
		int barX = st.x0 + labelW + 4;
		int barW = Math.max(20, st.width - labelW - 4 - valueW - 3);
		for (float f : at) {
			int x = barX + Math.round(barW * Math.max(0f, Math.min(1f, f)));
			st.g.fill(x, by - 2, x + 1, by + 5, colour);
		}
	}
}
