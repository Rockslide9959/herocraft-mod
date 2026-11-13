package com.projecthero.mod.client.saiyan;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanAbilityManager;
import com.projecthero.mod.saiyan.SaiyanConfig;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.data.SaiyanState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * v0.17.3 Saiyan revamp: the Saiyan HUD, bottom-right, drawn from the game's own pieces ({@link VanillaHud}) like the other
 * recent powers':
 * <pre>
 *   Kaioken  Zenkai 0:42  Stronger foe      status tags (wrap onto more lines when they don't fit)
 *   Super Saiyan                   2:41     the form and its time left (Base / "Exhausted" + the lockout in red)
 *   Kaioken  [Meter =======-]       2s      only while Shift+V is held: the 3 s wind-up
 *   Rage     [Meter =======-]       82      red; pulses gold at 100 ("H" to transform)
 *   Ki       [Meter =======-]       64      gold; white pulse while charging
 *   +----+----+----+----+----+----+         R G Z X C V: the kit's cooldowns (the shown move's as the item sweep, the Shift
 *   | R  | G  | Z  | X  | C  | V  |         move's as a durability bar); yellow while Shift shows the Shift moves; the frame on
 *   +----+----+----+----+----+----+         V while charging or under Kaioken
 * </pre>
 * The bars are Meter rows ({@link VanillaHud#meter}, the user's "Meter" style in boss-bar sprites). Hold Left-Alt for the move list.
 */
public final class SaiyanHud {
	private static final int MARGIN = 4;
	private static final int WIDTH = VanillaHud.width(6);
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	private static final int GOLD_TEXT = 0xFF000000 | (SaiyanForm.SSJ.hairColor & 0xFFFFFF);
	private static final int KAIOKEN_RED = 0xFFFF4040;
	private static final int ZENKAI_GOLD = 0xFFFFB020;
	private static final int STRONG_PURPLE = 0xFFD58CFF;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private SaiyanHud() {
	}

	private record Tag(String text, int colour) {
	}

	/** m:ss from ticks (rounded up). */
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
		SaiyanState s = Saiyan.peek(p);
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

		boolean pulse = (now / 4) % 2 == 0;
		// Ki
		int by = y0 - 8;
		float ki = s.ki / SaiyanConfig.KI_MAX;
		int kiColour = s.charging && pulse ? 0xFFFFFF : 0xFFE066;
		VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.saiyan.ki"), ki, kiColour, String.valueOf(Math.round(s.ki)),
				ki < 0.15f ? VanillaHud.RED : VanillaHud.WHITE);
		// Rage (gold pulse when full: H transforms)
		by -= 11;
		boolean full = s.rage >= SaiyanConfig.RAGE_MAX - 1.0e-3f;
		boolean canTransform = full && !s.ssj && Saiyan.lockoutTicksLeft(p) <= 0;
		int rageColour = s.ssj ? 0xFFE066 : canTransform && pulse ? 0xFFE066 : 0xFF3030;
		String rageValue = canTransform ? "H" : String.valueOf(Math.round(s.rage));
		VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.saiyan.rage"), s.ssj ? 0f : s.rage / SaiyanConfig.RAGE_MAX,
				rageColour, rageValue, canTransform ? VanillaHud.YELLOW : VanillaHud.WHITE);
		// the Kaioken wind-up (Shift + hold V)
		float windup = Saiyan.kaiokenWindup(p);
		if (windup > 0f) {
			by -= 11;
			int left = (int) Math.ceil(SaiyanConfig.KAIOKEN_WINDUP * (1f - windup) / 20f);
			VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.saiyan.kaioken"), windup, 0xFF2020, left + "s", KAIOKEN_RED);
		}
		// the form line: name on the left, its clock on the right
		by -= 11;
		int lock = Saiyan.lockoutTicksLeft(p);
		String title;
		int titleColour;
		String clock = null;
		int clockColour = VanillaHud.WHITE;
		int uiLeft = com.projecthero.mod.saiyan.SaiyanUltraInstinct.ticksLeft(p);
		if (uiLeft > 0) {
			// v0.17.3 the secret Ultra Instinct: silver, its own (short) clock
			title = Component.translatable(SaiyanForm.ULTRA.nameKey()).getString();
			titleColour = 0xFFE8F0FF;
			clock = clock(uiLeft);
			clockColour = uiLeft < 5 * 20 && pulse ? VanillaHud.RED : 0xFFE8F0FF;
		} else if (s.ssj) {
			title = Component.translatable(SaiyanForm.SSJ.nameKey()).getString();
			titleColour = GOLD_TEXT;
			int left = Saiyan.ssjTicksLeft(p);
			clock = clock(left);
			clockColour = left < 20 * 20 && pulse ? VanillaHud.RED : GOLD_TEXT;
		} else if (lock > 0) {
			title = Component.translatable("hud.projecthero.saiyan.exhausted").getString();
			titleColour = VanillaHud.RED;
			clock = clock(lock);
			clockColour = VanillaHud.RED;
		} else {
			title = Component.translatable(SaiyanForm.BASE.nameKey()).getString();
			titleColour = VanillaHud.WHITE;
		}
		int clockW = clock == null ? 0 : font.width(clock) + 4;
		g.drawString(font, font.plainSubstrByWidth(title, WIDTH - clockW), x0, by - 2, titleColour, true);
		if (clock != null) {
			g.drawString(font, clock, x0 + WIDTH - font.width(clock), by - 2, clockColour, true);
		}
		// status tags, wrapped to the HUD's width (bottom line first, upward)
		List<Tag> tags = new ArrayList<>();
		if (s.kaioken()) {
			tags.add(new Tag(Component.translatable("hud.projecthero.saiyan.kaioken").getString(), pulse ? KAIOKEN_RED : 0xFFFF8080));
		} else if (Saiyan.kaiokenCooldown(p) > 0) {
			tags.add(new Tag(Component.translatable("hud.projecthero.saiyan.kaioken").getString() + " " + clock(Saiyan.kaiokenCooldown(p)), VanillaHud.GREY));
		}
		if (Saiyan.zenkai(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.saiyan.zenkai").getString() + " " + clock(Saiyan.zenkaiTicksLeft(p)), ZENKAI_GOLD));
		} else if (Saiyan.zenkaiCooldown(p) > 0) {
			tags.add(new Tag(Component.translatable("hud.projecthero.saiyan.zenkai").getString() + " " + clock(Saiyan.zenkaiCooldown(p)), VanillaHud.GREY));
		}
		if (Saiyan.strongFoe(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.saiyan.strong_foe").getString(), STRONG_PURPLE));
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
				String[] ids = SaiyanAbilityManager.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				Component extra = slot == AbilitySlot.SLOT_5 ? Component.translatable("projecthero.saiyan.ability.kaioken.row") : null;
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.saiyan.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.saiyan.ability." + ids[1]) : null, extra));
			}
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_7), Component.translatable("projecthero.saiyan.ability.super_saiyan"), null));
			rows.add(MoveListOverlay.Row.of("2x Jump", Component.translatable("projecthero.saiyan.ability.flight"), null));
			MoveListOverlay.draw(g, x0 + WIDTH, top - 4, rows);
		}
	}

	private static void keys(GuiGraphics g, LocalPlayer p, SaiyanState s, int x0, int y0) {
		boolean shift = forceShift || p.isShiftKeyDown();
		VanillaHud.strip(g, x0, y0, SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = SaiyanAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = SaiyanAbilityManager.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, SaiyanAbilityManager.maxCooldown(shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, SaiyanAbilityManager.cooldownRemaining(p, other) / (float) Math.max(1, SaiyanAbilityManager.maxCooldown(other)));
			boolean active = slot == AbilitySlot.SLOT_5 && (s.charging || s.kaioken());
			VanillaHud.slot(g, x0, y0, i, keyLabel(slot), true, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
