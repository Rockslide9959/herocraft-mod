package com.projecthero.mod.client.raven;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.IronManGui;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenAbilityManager;
import com.projecthero.mod.raven.RavenConfig;
import com.projecthero.mod.raven.data.RavenState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Raven HUD, bottom-right, from the game's own pieces ({@link VanillaHud}) like the other recent powers:
 * <pre>
 *   Meditating  Shield 32  Debris 6         status tags (wrap onto more lines when they don't fit)
 *   Raven                       RAGE        the title; "Leaking" / "RAGE" / "Wrath 0:12" on the right
 *   [ Dark Energy  74          ]            the <b>Slab</b>: violet, the number inside
 *   ---------------------                   the <b>Hairline</b> under it: Composure (white calm, blue, orange leaking, red Rage)
 *   +----+----+----+----+----+----+         R G Z X C V: cooldowns; greyed when she can't pay / Shift+Z without Rage;
 *   | R  | G  | Z  | X  | C  | V  |         yellow while Shift shows the Shift moves
 *   +----+----+----+----+----+----+
 * </pre>
 * Hold Left-Alt for the move list. Every text is cut to the HUD's width (no sprawl).
 */
public final class RavenHud {
	private static final int MARGIN = 4;
	private static final int WIDTH = VanillaHud.width(6);
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	private static final int TITLE = 0xFFB98CF0;
	private static final int ENERGY = 0xFF7A3CC8;
	private static final int TAG = 0xFFC8B0F0;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private RavenHud() {
	}

	private record Tag(String text, int colour) {
	}

	static String clock(int ticks) {
		int s = (ticks + 19) / 20;
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}

	/** The Composure Hairline's colour. */
	public static int composureColour(float composure, long now) {
		if (composure < RavenConfig.RAGE_BELOW) {
			return (now / 4) % 2 == 0 ? 0xFFFF2030 : 0xFFB01020;
		}
		if (composure < RavenConfig.LEAK_BELOW) {
			return 0xFFE07A3A;
		}
		if (composure >= 90f) {
			return 0xFFEDEBFF;
		}
		return 0xFF5A7CE0;
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		RavenState s = Raven.peek(p);
		if (s == null || !s.hasPower) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		Font font = mc.font;
		long now = mc.level.getGameTime();
		int x0 = g.guiWidth() - MARGIN - WIDTH;
		int bottom = g.guiHeight() - MARGIN;
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			bottom = g.guiHeight() - 41; // a narrow window: clear of the hotbar and food bar
		}
		int y0 = bottom - VanillaHud.HEIGHT;
		keys(g, p, s, x0, y0);

		// Composure: the Hairline
		int hy = y0 - 5;
		IronManGui.hairline(g, x0, hy, WIDTH, 2, s.composure / RavenConfig.COMPOSURE_MAX, composureColour(s.composure, now), 0x90000000);
		// Dark Energy: the Slab
		int sy = hy - 12;
		float energy = s.energy / RavenConfig.ENERGY_MAX;
		String label = Component.translatable("hud.projecthero.raven.energy").getString() + "  " + Math.round(s.energy);
		IronManGui.slab(g, font, x0, sy, WIDTH, energy, energy < 0.15f ? 0xFFB03050 : ENERGY, label);
		// the title line
		int ty = sy - 11;
		String title = Component.translatable("hud.projecthero.raven.title").getString();
		String mood = null;
		int moodColour = 0xFFFFFFFF;
		if (Raven.inWrath(p)) {
			mood = Component.translatable("hud.projecthero.raven.wrath").getString() + " " + clock(Raven.wrathTicksLeft(p));
			moodColour = 0xFFFF3040;
		} else if (s.composure < RavenConfig.RAGE_BELOW) {
			mood = Component.translatable("hud.projecthero.raven.rage").getString();
			moodColour = composureColour(s.composure, now);
		} else if (s.composure < RavenConfig.LEAK_BELOW) {
			mood = Component.translatable("hud.projecthero.raven.leaking").getString();
			moodColour = 0xFFE07A3A;
		}
		int moodW = mood == null ? 0 : Math.min(font.width(mood), WIDTH / 2);
		g.drawString(font, font.plainSubstrByWidth(title, WIDTH - moodW - 6), x0, ty, TITLE, true);
		if (mood != null) {
			String m = font.plainSubstrByWidth(mood, WIDTH / 2);
			g.drawString(font, m, x0 + WIDTH - font.width(m), ty, moodColour, true);
		}
		// status tags, wrapped to the HUD's width (bottom line first, upward)
		List<Tag> tags = new ArrayList<>();
		if (s.meditating) {
			tags.add(new Tag(Component.translatable("hud.projecthero.raven.meditating").getString(), 0xFFEDEBFF));
		}
		if (s.flying) {
			tags.add(new Tag(Component.translatable("hud.projecthero.raven.flying").getString(), TAG));
		}
		if (s.shield) {
			tags.add(new Tag(Component.translatable("hud.projecthero.raven.shield").getString() + " " + Math.round(s.shieldHp), TAG));
		}
		if (s.debris > 0) {
			tags.add(new Tag(Component.translatable("hud.projecthero.raven.debris").getString() + " " + s.debris, TAG));
		}
		if (s.tkTarget >= 0) {
			tags.add(new Tag(Component.translatable("hud.projecthero.raven.holding").getString(), TAG));
		}
		int top = ty;
		if (!tags.isEmpty()) {
			List<List<Tag>> lines = new ArrayList<>();
			List<Tag> line = new ArrayList<>();
			int w = 0;
			for (Tag t : tags) {
				String text = font.plainSubstrByWidth(t.text, WIDTH);
				int tw = font.width(text);
				if (!line.isEmpty() && w + 6 + tw > WIDTH) {
					lines.add(line);
					line = new ArrayList<>();
					w = 0;
				}
				line.add(new Tag(text, t.colour));
				w += (line.size() > 1 ? 6 : 0) + tw;
			}
			lines.add(line);
			for (List<Tag> l : lines) {
				top -= 10;
				int x = x0;
				for (Tag t : l) {
					g.drawString(font, t.text, x, top, t.colour, true);
					x += font.width(t.text) + 6;
				}
			}
		}

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = RavenAbilityManager.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.raven.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.raven.ability." + ids[1]) : null));
			}
			MoveListOverlay.draw(g, x0 + WIDTH, top - 4, rows);
		}

		// the meditation's soft chant across the middle of the screen
		if (s.meditating) {
			int line = (int) ((now / 30) % 3);
			String chant = Component.translatable("message.projecthero.raven.chant." + line).getString();
			float k = ((now % 30) + delta.getGameTimeDeltaPartialTick(false)) / 30f;
			int alpha = Mth.clamp((int) (200 * Mth.sin(k * Mth.PI)), 8, 200);
			chant = font.plainSubstrByWidth(chant, Math.min(220, g.guiWidth() - 20));
			g.drawString(font, chant, (g.guiWidth() - font.width(chant)) / 2, g.guiHeight() / 2 - 40, (alpha << 24) | 0xD8C8FF, true);
		}
	}

	private static void keys(GuiGraphics g, LocalPlayer p, RavenState s, int x0, int y0) {
		boolean shift = forceShift || p.isShiftKeyDown();
		VanillaHud.strip(g, x0, y0, SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = RavenAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = RavenAbilityManager.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, RavenAbilityManager.maxCooldown(shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, RavenAbilityManager.cooldownRemaining(p, other) / (float) Math.max(1, RavenAbilityManager.maxCooldown(other)));
			boolean usable = !s.meditating && RavenAbilityManager.allowed(p, shown)
					&& (p.getAbilities().instabuild || s.energy + 1.0e-3f >= RavenAbilityManager.costNow(p, shown));
			if (RavenAbilityManager.MEDITATE.equals(shown)) {
				usable = !Raven.inWrath(p);
			}
			boolean active = (RavenAbilityManager.DARK_SHIELD.equals(shown) && s.shield)
					|| (RavenAbilityManager.MEDITATE.equals(shown) && s.meditating)
					|| (RavenAbilityManager.TELEKINESIS.equals(shown) && s.tkTarget >= 0)
					|| (RavenAbilityManager.DEBRIS_STORM.equals(shown) && s.debris > 0)
					|| (RavenAbilityManager.TRIGONS_WRATH.equals(shown) && Raven.inWrath(p));
			VanillaHud.slot(g, x0, y0, i, keyLabel(slot), usable, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
