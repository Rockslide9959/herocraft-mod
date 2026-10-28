package com.projecthero.mod.client.omnitrix;

import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixAttachments;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the Omnitrix HUD (bottom left): a small green-and-black Omnitrix dial -- the time left as a green arc (flashing red
 * in the last 15 s) while transformed, the recharge as a red arc, the selected alien's silhouette in the middle -- then, while
 * transformed, the alien's key boxes (R G Z X C, Shift moves as the lower strip; V = the Omnitrix). No H / N boxes. While the
 * watch is up a short hint wraps above it. During a Containment Pod extraction a red SELF-DESTRUCT warning flashes at the top
 * of the screen; a transformation flashes the screen green (red for a timeout).
 */
public final class OmnitrixHud {
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3, AbilitySlot.SLOT_6 };
	private static final int GREEN = 0xFF4CE04A;
	private static final int GREEN_DIM = 0xFF1F6A22;
	private static final int RED = 0xFFFF3A2E;
	private static final int BLACK = 0xF0101413;

	private OmnitrixHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui || !Omnitrix.hasPower(p)) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		flash(g, p, s, now, partial);
		extraction(g, mc, p, now);
		int cx = 28;
		int cy = g.guiHeight() - 30;
		dial(g, mc, p, s, now, cx, cy);
		if (s.transformed()) {
			keys(g, mc, p, s, now, cx + 26, cy - 9);
		} else if (s.selecting) {
			Aliens.Alien a = Aliens.byId(s.selected);
			Component name = a == null ? Component.empty() : Component.translatable(a.nameKey());
			List<FormattedCharSequence> hint = mc.font.split(Component.translatable("hud.projecthero.omnitrix.hint"), 112);
			int y = cy - 24 - hint.size() * 9;
			g.drawString(mc.font, name, 6, y - 11, GREEN, true);
			for (FormattedCharSequence line : hint) {
				g.drawString(mc.font, line, 6, y, 0xFFB8C8B8, true);
				y += 9;
			}
		}
	}

	private static void disc(GuiGraphics g, int cx, int cy, int r, int colour) {
		for (int dy = -r; dy <= r; dy++) {
			int w = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
			g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, colour);
		}
	}

	/** An arc of {@code frac} (0..1) clockwise from the top, {@code thick} px wide. */
	private static void arc(GuiGraphics g, int cx, int cy, int r, int thick, float frac, int colour) {
		int steps = Math.max(1, (int) (frac * 96));
		for (int i = 0; i <= steps; i++) {
			double a = -Math.PI / 2 + (i / 96.0) * Math.PI * 2;
			for (int k = 0; k < thick; k++) {
				int x = cx + (int) Math.round(Math.cos(a) * (r - k));
				int y = cy + (int) Math.round(Math.sin(a) * (r - k));
				g.fill(x, y, x + 1, y + 1, colour);
			}
		}
	}

	private static void dial(GuiGraphics g, Minecraft mc, LocalPlayer p, OmnitrixState s, long now, int cx, int cy) {
		disc(g, cx, cy, 20, 0xFF0A0C0B);
		disc(g, cx, cy, 18, 0xFF2A3030);
		disc(g, cx, cy, 14, BLACK);
		boolean warn = false;
		String label;
		if (s.transformed()) {
			int left = (int) Math.max(0, s.endsAt - now);
			warn = left <= OmnitrixConfig.WARN_TICKS;
			int col = warn && (now / 5) % 2 == 0 ? RED : GREEN;
			arc(g, cx, cy, 18, 3, left / (float) OmnitrixConfig.TRANSFORM_TICKS, col);
			label = String.format(java.util.Locale.ROOT, "%d:%02d", left / 1200, (left / 20) % 60);
		} else if (now < s.rechargeUntil) {
			int left = (int) (s.rechargeUntil - now);
			int total = Math.max(left, s.rechargeUntil - s.endsAt > 0 ? OmnitrixConfig.RECHARGE_TICKS : OmnitrixConfig.RECHARGE_TICKS);
			arc(g, cx, cy, 18, 3, left / (float) total, RED);
			label = (left + 19) / 20 + "s";
			warn = true;
		} else {
			arc(g, cx, cy, 18, 2, 1f, s.selecting ? GREEN : GREEN_DIM);
			label = null;
		}
		// the hourglass face, or the alien's silhouette
		String shown = s.transformed() ? s.active : s.selected;
		if (s.selecting || s.transformed()) {
			disc(g, cx, cy, 12, warn ? 0xFF7A1A14 : 0xFF2E9A30);
			g.setColor(0.05f, 0.1f, 0.05f, 1f);
			g.blit(OmnitrixWatch.silhouette(shown), cx - 10, cy - 10, 0, 0, 20, 20, 20, 20);
			g.setColor(1f, 1f, 1f, 1f);
		} else {
			hourglass(g, cx, cy, 12, warn ? RED : GREEN);
		}
		if (label != null) {
			Font f = mc.font;
			int w = f.width(label);
			g.fill(cx - w / 2 - 2, cy + 14, cx + w / 2 + 2, cy + 24, 0xC0000000);
			g.drawString(f, label, cx - w / 2, cy + 15, warn ? RED : 0xFFFFFFFF, false);
		}
	}

	private static void hourglass(GuiGraphics g, int cx, int cy, int r, int colour) {
		for (int dy = -r; dy <= r; dy++) {
			int w = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
			// the green hourglass: wide at the top and bottom, pinched in the middle
			int hw = (int) (Math.abs(dy) * 0.85f) + 1;
			g.fill(cx - Math.min(w, hw), cy + dy, cx + Math.min(w, hw) + 1, cy + dy + 1, colour);
		}
	}

	private static void keys(GuiGraphics g, Minecraft mc, LocalPlayer p, OmnitrixState s, long now, int x0, int y0) {
		AlienKit kit = Aliens.kit(s.active);
		if (kit == null) {
			return;
		}
		Aliens.Alien a = Aliens.byId(s.active);
		g.drawString(mc.font, Component.translatable(a.nameKey()), x0, y0 - 11, 0xFF000000 | a.colour(), true);
		int x = x0;
		for (AbilitySlot slot : SLOTS) {
			AlienMove tap = kit.moveOn(slot, false, false);
			AlienMove shift = kit.moveOn(slot, true, false);
			box(g, mc, x, y0, String.valueOf(slot.defaultKey()), cooldownFrac(kit, p, tap), cooldownFrac(kit, p, shift), a.colour());
			x += 20;
		}
		box(g, mc, x + 4, y0, "V", 0f, 0f, 0x4CE04A);
	}

	private static float cooldownFrac(AlienKit kit, LocalPlayer p, AlienMove m) {
		if (m == null || m.cooldown() <= 0) {
			return m == null ? -1f : 0f;
		}
		return Mth.clamp(kit.cooldownLeft(p, m.id()) / (float) m.cooldown(), 0f, 1f);
	}

	private static void box(GuiGraphics g, Minecraft mc, int x, int y, String key, float tap, float shift, int colour) {
		g.fill(x, y, x + 18, y + 18, 0xD0080808);
		g.renderOutline(x, y, 18, 18, 0xFF000000 | colour);
		if (tap > 0f) {
			g.fill(x + 1, y + 1 + (int) (16 * (1f - tap)), x + 17, y + 17, 0xB0000000);
		}
		g.drawCenteredString(mc.font, key, x + 9, y + 5, tap > 0f ? 0xFF808080 : 0xFFFFFFFF);
		if (shift >= 0f) {
			g.fill(x + 1, y + 19, x + 17, y + 21, 0xFF202020);
			g.fill(x + 1, y + 19, x + 1 + (int) (16 * (1f - shift)), y + 21, 0xFF000000 | colour);
		}
	}

	private static void flash(GuiGraphics g, LocalPlayer p, OmnitrixState s, long now, float partial) {
		boolean anim = s.animId == Omnitrix.ANIM_TRANSFORM || s.animId == Omnitrix.ANIM_TIMEOUT || s.animId == Omnitrix.ANIM_REVERT;
		float t = now + partial - s.animStart;
		if (!anim || t > 12f) {
			return;
		}
		float a = (1f - t / 12f) * (s.animId == Omnitrix.ANIM_REVERT ? 0.35f : 0.6f);
		int rgb = s.animId == Omnitrix.ANIM_TIMEOUT ? 0xFF3020 : 0x7CFF6A;
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (a * 255) << 24) | rgb);
	}

	private static void extraction(GuiGraphics g, Minecraft mc, LocalPlayer p, long now) {
		Integer t = p.getAttached(OmnitrixAttachments.EXTRACTION);
		if (t == null) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		boolean on = (now / 6) % 2 == 0;
		// the red edges
		int edge = on ? 0x70FF1A10 : 0x40FF1A10;
		g.fill(0, 0, w, 6, edge);
		g.fill(0, h - 6, w, h, edge);
		g.fill(0, 0, 6, h, edge);
		g.fill(w - 6, 0, w, h, edge);
		int bw = 236;
		int x = w / 2 - bw / 2;
		int y = 22;
		g.fill(x, y, x + bw, y + 34, 0xE0180404);
		g.renderOutline(x, y, bw, 34, on ? RED : 0xFF801010);
		Component title = Component.translatable("hud.projecthero.omnitrix.self_destruct");
		g.drawCenteredString(mc.font, title, w / 2, y + 4, on ? RED : 0xFFFFB0A0);
		int left = Math.max(0, OmnitrixConfig.EXTRACTION_TICKS - t);
		g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.omnitrix.extracting", (left + 19) / 20), w / 2, y + 14, 0xFFFFFFFF);
		float frac = Mth.clamp(t / (float) OmnitrixConfig.EXTRACTION_TICKS, 0f, 1f);
		g.fill(x + 6, y + 26, x + bw - 6, y + 30, 0xFF300808);
		g.fill(x + 6, y + 26, x + 6 + (int) ((bw - 12) * frac), y + 30, on ? RED : 0xFFC02018);
	}
}
