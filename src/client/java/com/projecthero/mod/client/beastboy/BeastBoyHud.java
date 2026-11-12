package com.projecthero.mod.client.beastboy;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.beastboy.BeastBoy;
import com.projecthero.mod.beastboy.BeastBoyAbilityManager;
import com.projecthero.mod.beastboy.BeastBoyConfig;
import com.projecthero.mod.beastboy.BeastForm;
import com.projecthero.mod.beastboy.data.BeastBoyState;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * v0.17.3 Beast Boy HUD, bottom-right, built from the game's own pieces ({@link VanillaHud}) like the other recent powers':
 * <pre>
 *   The Beast 0:21  Senses 0:08  Dazed            status tags (wrap onto more lines when they don't fit)
 *   Beast Boy . Wolf          Chain [#][#][#][ ][ ]   the form he's in; the Morph Chain's five links
 *   Stamina  [Meter =======-]          64         Morph Stamina (the user's "Meter" style)
 *   +----+----+----+----+----+----+               R G Z X C V for the form he's in: cooldowns; greyed when he can't pay or
 *   | R  | G  | Z  | X  | C  | V  |               the key does nothing in this form; yellow while Shift shows the Shift moves
 *   +----+----+----+----+----+----+
 * </pre>
 * In an animal form the form's own health is a <b>Slab</b> bar over his hearts (with the form's name and HP). Before the power,
 * the Sakutia fever counts down there instead. Hold Left-Alt for the move list.
 */
public final class BeastBoyHud {
	private static final int MARGIN = 4;
	private static final int WIDTH = VanillaHud.width(6);
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	private static final int GREEN_TEXT = 0xFF7CE05A;
	private static final int DARK_GREEN_TEXT = 0xFF3FA028;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private BeastBoyHud() {
	}

	private record Tag(String text, int colour) {
	}

	static String clock(int ticks) {
		int s = (ticks + 19) / 20;
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		BeastBoyState s = BeastBoy.peek(p);
		if (s == null) {
			return;
		}
		if (!s.hasPower) {
			fever(g, mc, p);
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		Font font = mc.font;
		BeastForm form = BeastBoy.form(p);
		formSlab(g, mc, p, s, form);

		int x0 = g.guiWidth() - MARGIN - WIDTH;
		int bottom = g.guiHeight() - MARGIN;
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			bottom = g.guiHeight() - 41; // a narrow window: clear of the hotbar and food bar
		}
		int y0 = bottom - VanillaHud.HEIGHT;
		keys(g, p, s, form, x0, y0);

		// Morph Stamina
		int by = y0 - 8;
		float st = s.stamina / BeastBoyConfig.STAMINA_MAX;
		VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.beast_boy.stamina"), st, 0x58C83C,
				String.valueOf(Math.round(s.stamina)), st < 0.15f ? VanillaHud.RED : VanillaHud.WHITE);
		// the title (and the form) and the chain's links
		by -= 11;
		int chain = BeastBoy.chain(p);
		int linksW = 5 * 6;
		String chainLabel = Component.translatable("hud.projecthero.beast_boy.chain").getString();
		int chainW = font.width(chainLabel) + 3 + linksW;
		String title = Component.translatable("hud.projecthero.beast_boy.title").getString();
		if (form != BeastForm.HUMAN) {
			title += " · " + Component.translatable(BeastWheelScreen.formKey(form)).getString();
		}
		g.drawString(font, font.plainSubstrByWidth(title, WIDTH - chainW - 4), x0, by - 2, GREEN_TEXT, true);
		int cx = x0 + WIDTH - linksW;
		g.drawString(font, chainLabel, cx - 3 - font.width(chainLabel), by - 2, chain > 0 ? 0xFFFFE070 : VanillaHud.GREY, true);
		for (int i = 0; i < 5; i++) {
			int lx = cx + i * 6;
			g.fill(lx, by - 1, lx + 5, by + 5, 0xFF1A1A1A);
			g.fill(lx + 1, by, lx + 4, by + 4, i < chain ? (BeastBoy.isBeast(p) ? 0xFFFF6A3A : 0xFFFFD84A) : 0xFF3A3A3A);
		}
		// status tags, wrapped to the HUD's width (bottom line first, upward)
		List<Tag> tags = new ArrayList<>();
		if (BeastBoy.isBeast(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.beast_boy.beast").getString() + " " + clock(BeastBoy.beastTicksLeft(p)), 0xFFFF8A4A));
		}
		if (BeastBoy.sensing(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.beast_boy.senses").getString() + " " + clock(BeastBoy.sensesTicksLeft(p)), GREEN_TEXT));
		}
		if (BeastBoy.dazed(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.beast_boy.dazed").getString(), VanillaHud.RED));
		}
		if (!p.getPassengers().isEmpty()) {
			tags.add(new Tag(Component.translatable("hud.projecthero.beast_boy.carrying", p.getPassengers().size()).getString(), GREEN_TEXT));
		}
		int top = by - 2;
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
				String[] ids = BeastBoyAbilityManager.idsOf(p, slot);
				if (ids[0] == null && ids[1] == null) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), name(ids[0]), ids[1] != null && !ids[1].equals(ids[0]) ? name(ids[1]) : null));
			}
			MoveListOverlay.draw(g, x0 + WIDTH, top - 4, rows);
		}
	}

	private static Component name(String id) {
		return id == null ? Component.literal("-") : Component.translatable("projecthero.beast_boy.ability." + id);
	}

	private static void keys(GuiGraphics g, LocalPlayer p, BeastBoyState s, BeastForm form, int x0, int y0) {
		boolean shift = forceShift || p.isShiftKeyDown();
		VanillaHud.strip(g, x0, y0, SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String shown = BeastBoyAbilityManager.moveFor(form, slot, shift);
			String other = BeastBoyAbilityManager.moveFor(form, slot, !shift);
			int cd = shown == null ? 0 : BeastBoy.cooldownRemaining(p, shown);
			float frac = shown == null ? 0f : Math.min(1f, cd / (float) Math.max(1, BeastBoyAbilityManager.maxCooldown(shown)));
			float otherFrac = other == null || other.equals(shown) ? 0f
					: Math.min(1f, BeastBoy.cooldownRemaining(p, other) / (float) Math.max(1, BeastBoyAbilityManager.maxCooldown(other)));
			boolean usable = shown != null && !BeastBoy.dazed(p)
					&& (p.getAbilities().instabuild || s.stamina + 1.0e-3f >= BeastBoyAbilityManager.cost(shown));
			boolean active = (BeastBoyAbilityManager.ANIMAL_SENSES.equals(shown) && BeastBoy.sensing(p))
					|| (BeastBoyAbilityManager.THE_BEAST.equals(shown) && BeastBoy.isBeast(p))
					|| (BeastBoyAbilityManager.FLY_MORPH.equals(shown) && form == BeastForm.FLY)
					|| (BeastBoyAbilityManager.CONTEXT_MORPH.equals(shown) && (form == BeastForm.CHEETAH || form == BeastForm.HAWK || form == BeastForm.DOLPHIN))
					|| (BeastBoyAbilityManager.ANIMAL_WHEEL.equals(shown) && mcScreenIsWheel());
			VanillaHud.slot(g, x0, y0, i, keyLabel(slot), usable, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
	}

	private static boolean mcScreenIsWheel() {
		return Minecraft.getInstance().screen instanceof BeastWheelScreen;
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}

	/** The form's health: a Slab bar (7 px, bordered) just above the hearts and armour, with the form's name and HP over it. */
	private static void formSlab(GuiGraphics g, Minecraft mc, LocalPlayer p, BeastBoyState s, BeastForm form) {
		if (form == BeastForm.HUMAN || form.formHealth <= 0f || p.isCreative() || p.isSpectator()) {
			return;
		}
		int x = g.guiWidth() / 2 - 91;
		int w = 81;
		int y = g.guiHeight() - 39 - 10 - (p.getArmorValue() > 0 ? 10 : 0) - 29; // clear of the action-bar message
		float frac = Math.max(0f, Math.min(1f, s.formHp / form.formHealth));
		int colour = frac > 0.5f ? 0xFF58C83C : frac > 0.25f ? 0xFFE0C040 : 0xFFE04A3A;
		g.fill(x - 1, y - 1, x + w + 1, y + 8, 0xFF0A0A0A);
		g.fill(x, y, x + w, y + 7, 0xFF2A2A2A);
		g.fill(x, y, x + Math.round(w * frac), y + 7, colour);
		g.fill(x, y, x + Math.round(w * frac), y + 1, 0x40FFFFFF);
		Font font = mc.font;
		String label = Component.translatable(BeastWheelScreen.formKey(form)).getString();
		String hp = Math.round(s.formHp) + "/" + Math.round(form.formHealth);
		g.drawString(font, font.plainSubstrByWidth(label, w - font.width(hp) - 4), x, y - 10, GREEN_TEXT, true);
		g.drawString(font, hp, x + w - font.width(hp), y - 10, VanillaHud.WHITE, true);
	}

	/** Before the power: the Sakutia fever's countdown over the hearts. */
	private static void fever(GuiGraphics g, Minecraft mc, LocalPlayer p) {
		int left = BeastBoy.feverTicksLeft(p);
		if (left <= 0) {
			return;
		}
		Font font = mc.font;
		int x = g.guiWidth() / 2 - 91;
		int y = g.guiHeight() - 39 - 10 - (p.getArmorValue() > 0 ? 10 : 0) - 29; // clear of the action-bar message
		int w = 81;
		float frac = 1f - left / (float) BeastBoyConfig.FEVER_TICKS;
		g.fill(x - 1, y - 1, x + w + 1, y + 8, 0xFF0A0A0A);
		g.fill(x, y, x + w, y + 7, 0xFF2A2A2A);
		g.fill(x, y, x + Math.round(w * frac), y + 7, DARK_GREEN_TEXT);
		String label = font.plainSubstrByWidth(Component.translatable("hud.projecthero.beast_boy.fever").getString() + " " + clock(left), 160);
		g.drawString(font, label, x, y - 10, GREEN_TEXT, true);
	}
}
