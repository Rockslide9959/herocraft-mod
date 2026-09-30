package com.projecthero.mod.client.gui;

import java.util.Locale;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAlter;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.data.MoonKnightState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Moon Knight's HUD (v0.13.19, Phase 1), in the same bottom-right corner and boxed-key style as every other hero,
 * shown only while transformed:
 * <pre>
 *   (moon) x1.50   Marc Spector       (crescent)
 *   [R][G][Z][X][C][V]
 *   Vengeance 62%                    GLIDE / BLOCK
 *   ------------------                          Hairline Vengeance bar
 * </pre>
 * v0.14.4: the icon is the lunar state -- the sun by day (and always in the Nether / End), tonight's moon on an
 * ordinary night, a haloed full moon under the full moon -- with its multiplier (x0.7 / x1.0 / x1.5; hold Left Alt
 * for the state's name). Under the crosshair it names the Grapple Kick's current lock-on target. The rest:
 * the crescent is Khonshu's Resurrection (bright = charged, dark = spent), and every box is labelled with the key
 * actually bound to it -- rebind a key and the HUD follows. Hold Left Alt for the ability names.
 */
public final class MoonKnightHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int HAIRLINE = 3;

	private static final int COLOR_BOX_BG = 0xC0101014;
	private static final int COLOR_BORDER = 0xFF3A3A44;
	private static final int COLOR_KEY = 0xFFE8E4D8;
	private static final int COLOR_LABEL = 0xFFE8E4D8;
	private static final int COLOR_VENGEANCE = 0xFFF2EEE0;
	private static final int COLOR_VENGEANCE_LOW = 0xFFB04040;
	private static final ResourceLocation MOON_PHASES = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
	private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");

	/** The six keys in the order the user listed them, with the ability-id prefix each box's cooldowns use. */
	private static final AbilitySlot[] ORDER = {
			AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3, AbilitySlot.SLOT_6, AbilitySlot.SLOT_5
	};
	// v0.13.21 keys: R Darts, G Grapple Kick, Z Khonshu, X Dash, C Truncheon, V Alters
	private static final String[] IDS = { "darts", "kick", "khonshu", "dash", "truncheon", "alter" };

	private MoonKnightHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		MoonKnightState s = MoonKnight.peek(player);
		if (s == null || !s.hasPact || !s.transformed) {
			return;
		}
		com.projecthero.mod.moonknight.data.MoonKnightAction action = com.projecthero.mod.moonknight.MoonKnightAnim.action(player);
		long now = mc.level.getGameTime();
		int totalW = 6 * BOX + 5 * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int barY = g.guiHeight() - MARGIN - HAIRLINE;
		int labelY = barY - 11;
		int y0 = labelY - 3 - BOX;
		int topY = y0 - 15;
		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		// ---- top line: lunar state (v0.14.4: DAY sun / NIGHT tonight's moon / FULL MOON) + multiplier, alter, resurrection
		MoonKnightLunar.State lunar = MoonKnightLunar.state(mc.level);
		switch (lunar) {
			case DAY -> {
				g.setColor(0.8f, 0.78f, 0.62f, 1.0f);
				g.blit(SUN, x0 - 2, topY - 2, 16, 16, 0, 0, 32, 32, 32, 32);
			}
			case NIGHT -> {
				int phase = mc.level.getMoonPhase();
				g.setColor(0.78f, 0.8f, 0.86f, 1.0f);
				g.blit(MOON_PHASES, x0, topY, 12, 12, (phase % 4) * 32, (phase / 4) * 32, 32, 32, 128, 64);
			}
			case FULL_MOON -> {
				// the full moon, with a faint halo so it reads as the strongest state at a glance
				g.fill(x0 - 1, topY - 1, x0 + 13, topY + 13, 0x30E8F0FF);
				g.blit(MOON_PHASES, x0, topY, 12, 12, 0, 0, 32, 32, 128, 64);
			}
		}
		g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		float power = lunar.power();
		int powerColor = switch (lunar) {
			case FULL_MOON -> 0xFFFFFFFF;
			case NIGHT -> 0xFFD8D4C8;
			case DAY -> 0xFF9A968C;
		};
		String mult = String.format(Locale.ROOT, "x%.1f", power);
		g.drawString(mc.font, mult, x0 + 15, topY + 2, powerColor, false);
		if (expanded) {
			g.drawString(mc.font, Component.translatable(lunar.nameKey()), x0, topY - 11, powerColor, false);
		}
		drawKickTarget(g, mc, player, now);

		MoonKnightAlter alter = MoonKnightAlter.byOrdinal(s.alter);
		Component alterName = Component.translatable(alter.nameKey()).withStyle(alter.colour());
		boolean fractured = s.fractureUntil > now;
		if (fractured) {
			alterName = Component.translatable("hud.projecthero.moon_knight.fractured", alterName,
					(int) Math.ceil((s.fractureUntil - now) / 20.0)).withStyle(ChatFormatting.RED);
		}
		int alterX = x0 + 15 + mc.font.width(mult) + 6;
		g.drawString(mc.font, alterName, alterX, topY + 2, COLOR_LABEL, false);
		drawCrescent(g, x0 + totalW - 9, topY + 1, s.resurrectionCharged);

		// ---- the six keys
		for (int i = 0; i < 6; i++) {
			int x = x0 + i * (BOX + GAP);
			g.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, COLOR_BORDER);
			g.drawString(mc.font, keyLabel(ORDER[i]), x + 2, y0 + 2, COLOR_KEY, false);
			// a charging HOLD (the dart fan, the Eye of Khonshu) fills its box from the bottom
			if (action.has(com.projecthero.mod.moonknight.data.MoonKnightAction.FLAG_CHARGING) && action.chargeKey == ORDER[i].number()) {
				int max = ORDER[i] == AbilitySlot.SLOT_4 ? com.projecthero.mod.moonknight.MoonKnightConfig.EYE_HOLD_TICKS
						: com.projecthero.mod.moonknight.MoonKnightConfig.DART_FAN_MAX_CHARGE;
				float c = Math.min(1.0f, (now - action.chargeStart) / (float) Math.max(1, max));
				int fillH = Math.round((BOX - 2) * c);
				g.fill(x + 1, y0 + BOX - 1 - fillH, x + BOX - 1, y0 + BOX - 1, 0x99E8F0FF);
			}
			int cd = cooldown(player, IDS[i]);
			if (cd > 0) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0xB0000000);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (expanded) {
				Component name = Component.translatable("projecthero.moon_knight.ability." + IDS[i]);
				g.drawString(mc.font, name, x0 - 8 - mc.font.width(name), y0 + i * 10 - 40, 0xFFE0DCD0);
			}
		}

		// ---- vengeance (+ glide indicator)
		float frac = Math.max(0.0f, Math.min(1.0f, s.vengeance / MoonKnightConfig.VENGEANCE_MAX));
		int pct = Math.round(frac * 100.0f);
		g.drawString(mc.font, Component.translatable("hud.projecthero.moon_knight.vengeance", pct), x0, labelY,
				frac < 0.15f ? COLOR_VENGEANCE_LOW : COLOR_LABEL, false);
		if (action.has(com.projecthero.mod.moonknight.data.MoonKnightAction.FLAG_GLIDING)) {
			Component glide = Component.translatable("hud.projecthero.moon_knight.glide").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
			g.drawString(mc.font, glide, x0 + totalW - mc.font.width(glide), labelY, 0xFF8FE0FF, false);
		} else if (action.has(com.projecthero.mod.moonknight.data.MoonKnightAction.FLAG_CAPE_BLOCK)) {
			// v0.13.21: the Cape Block (hold right click)
			Component block = Component.translatable("hud.projecthero.moon_knight.cape_block").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
			g.drawString(mc.font, block, x0 + totalW - mc.font.width(block), labelY, 0xFFE8E4D8, false);
		}
		g.fill(x0, barY, x0 + totalW, barY + HAIRLINE, 0x80000000);
		g.fill(x0, barY, x0 + Math.round(totalW * frac), barY + HAIRLINE, frac < 0.15f ? COLOR_VENGEANCE_LOW : COLOR_VENGEANCE);
	}

	/**
	 * v0.14.4: under the crosshair, who the Grapple Kick would hit right now ({@code MoonKnightKickPreviewClient}) --
	 * "[G] Zombie 23m", white while it is ready, grey while it recharges.
	 */
	private static void drawKickTarget(GuiGraphics g, Minecraft mc, Player player, long now) {
		net.minecraft.world.entity.LivingEntity t = com.projecthero.mod.client.moonknight.MoonKnightKickPreviewClient.target();
		if (t == null || mc.screen != null) {
			return;
		}
		boolean ready = MoonKnight.cooldownRemaining(player, "kick") <= 0;
		Component text = Component.translatable("hud.projecthero.moon_knight.kick_target", keyLabel(AbilitySlot.SLOT_2),
				t.getDisplayName(), Math.round(player.distanceTo(t)));
		int w = mc.font.width(text);
		g.drawString(mc.font, text, g.guiWidth() / 2 - w / 2, g.guiHeight() / 2 + 10, ready ? 0xE0F2F0E8 : 0xA0909090, true);
	}

	/** The longest cooldown among a key's tap / hold / sneak variants ({@code <id>}, {@code <id>_hold}, {@code <id>_sneak}). */
	private static int cooldown(Player player, String id) {
		return Math.max(MoonKnight.cooldownRemaining(player, id),
				Math.max(MoonKnight.cooldownRemaining(player, id + "_hold"), MoonKnight.cooldownRemaining(player, id + "_sneak")));
	}

	private static String keyLabel(AbilitySlot slot) {
		String k = ModKeyBindings.ABILITY_SLOTS[slot.index()].getTranslatedKeyMessage().getString();
		k = k.toUpperCase(Locale.ROOT);
		return k.length() > 2 ? k.substring(0, 2) : k;
	}

	/** Khonshu's Resurrection: a small crescent, bright white when charged, dark when spent. */
	private static void drawCrescent(GuiGraphics g, int x, int y, boolean charged) {
		int c = charged ? 0xFFF8F6EE : 0xFF3C3A36;
		// a 9x9 disc with a 5 px bite taken out of its right side
		for (int dy = 0; dy < 9; dy++) {
			for (int dx = 0; dx < 9; dx++) {
				double ox = dx - 4.0;
				double oy = dy - 4.0;
				boolean inDisc = ox * ox + oy * oy <= 16.5;
				double bx = dx - 6.2;
				boolean inBite = bx * bx + oy * oy <= 11.0;
				if (inDisc && !inBite) {
					g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, c);
				}
			}
		}
	}
}
