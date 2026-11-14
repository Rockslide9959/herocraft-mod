package com.projecthero.mod.client.doctorfate;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.doctorfate.DoctorFate;
import com.projecthero.mod.doctorfate.DoctorFateAbilityManager;
import com.projecthero.mod.doctorfate.DoctorFateConfig;
import com.projecthero.mod.doctorfate.data.FateState;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Doctor Fate's HUD, bottom-right, built from the game's own pieces ({@link VanillaHud}) like the other recent powers':
 * <pre>
 *   Helmet  Quest 2/5  Avatar 0:09        status tags (wrap onto more lines when they don't fit)
 *   Doctor Fate                Pull       the title and Nabu's tier
 *   Dominion [Gauge ##########]   62      ten cells: Kent's blue, with Order's gold creeping over it
 *   +----+----+----+----+----+----+       R G Z X C V: cooldowns; a move that needs the helmet is greyed without it
 *   | R  | G  | Z  | X  | C  | V  |
 *   +----+----+----+----+----+----+
 * </pre>
 * Taking hold (75+) adds gold at the screen's edges; a Possession fills the screen with gold, "Nabu holds you", the time left and
 * "mash SPACE to struggle". Hold Left-Alt for the move list. (The Amulet of Anubis is why he sees the bar even with the helmet off.)
 */
public final class DoctorFateHud {
	private static final int MARGIN = 4;
	private static final int WIDTH = VanillaHud.width(6);
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	private static final int GOLD_TEXT = 0xFFFFC93A;
	private static final int BLUE_TEXT = 0xFF7FA4FF;
	private static final int[] TIER_COLOURS = { 0xFFB8C8FF, 0xFFFFE08A, 0xFFFFC93A, 0xFFFF9A30, 0xFFFFFFFF };

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private DoctorFateHud() {
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
		FateState s = DoctorFate.peek(p);
		if (s == null || !s.hasPower) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		boolean possessed = DoctorFate.possessed(p);
		overlays(g, mc, p, s, possessed, partial);

		Font font = mc.font;
		int x0 = g.guiWidth() - MARGIN - WIDTH;
		int bottom = g.guiHeight() - MARGIN;
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			bottom = g.guiHeight() - 41; // a narrow window: clear of the hotbar and food bar
		}
		int y0 = bottom - VanillaHud.HEIGHT;
		keys(g, p, x0, y0);

		// Dominion: a ten-cell Gauge
		int by = y0 - 9;
		gauge(g, font, x0, by, s.dominion, possessed);
		// the title line and the tier
		by -= 11;
		int tier = DoctorFate.tier(p);
		String tierName = Component.translatable("hud.projecthero.doctor_fate.tier." + tier).getString();
		int tw = font.width(tierName);
		String title = font.plainSubstrByWidth(Component.translatable("hud.projecthero.doctor_fate.title").getString(), WIDTH - tw - 6);
		g.drawString(font, title, x0, by - 2, GOLD_TEXT, true);
		g.drawString(font, tierName, x0 + WIDTH - tw, by - 2, TIER_COLOURS[Math.min(4, tier)], true);
		// status tags, wrapped to the HUD's width (bottom line first, upward)
		List<Tag> tags = new ArrayList<>();
		long now = mc.level.getGameTime();
		// v0.17.4: "Helmet" worn; "Summoning..." / "Putting on" / "Taking off" during the sequences; "No helmet: H" otherwise
		String helmetKey = s.helmetChangeAt > 0L
				? (s.helmetChangeAt > now ? "hud.projecthero.doctor_fate.helmet_summoning"
						: s.helmetGoingOn ? "hud.projecthero.doctor_fate.helmet_putting_on" : "hud.projecthero.doctor_fate.helmet_taking_off")
				: s.helmetOn ? "hud.projecthero.doctor_fate.helmet_on" : "hud.projecthero.doctor_fate.helmet_off";
		tags.add(new Tag(Component.translatable(helmetKey).getString(), s.helmetOn || s.helmetChangeAt > 0L ? GOLD_TEXT : BLUE_TEXT));
		int lock = DoctorFate.lockoutTicksLeft(p);
		if (lock > 0) {
			tags.add(new Tag(Component.translatable("hud.projecthero.doctor_fate.lockout").getString() + " " + clock(lock), VanillaHud.RED));
		}
		if (s.bargain.questActive()) {
			tags.add(new Tag(Component.translatable("hud.projecthero.doctor_fate.quest", s.bargain.questKills(), s.bargain.questNeed()).getString(), GOLD_TEXT));
		}
		if (DoctorFate.avatarActive(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.doctor_fate.avatar").getString() + " " + clock(DoctorFate.avatarTicksLeft(p)), GOLD_TEXT));
		}
		if (s.flying) {
			tags.add(new Tag(Component.translatable("hud.projecthero.doctor_fate.flying").getString(), GOLD_TEXT));
		}
		int top = by - 2;
		List<List<Tag>> lines = new ArrayList<>();
		List<Tag> line = new ArrayList<>();
		int w = 0;
		for (Tag t : tags) {
			String text = font.plainSubstrByWidth(t.text, WIDTH);
			int tww = font.width(text);
			if (!line.isEmpty() && w + 6 + tww > WIDTH) {
				lines.add(line);
				line = new ArrayList<>();
				w = 0;
			}
			line.add(new Tag(text, t.colour));
			w += (line.size() > 1 ? 6 : 0) + tww;
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
		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = DoctorFateAbilityManager.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.doctor_fate.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.doctor_fate.ability." + ids[1]) : null));
			}
			MoveListOverlay.draw(g, x0 + WIDTH, top - 4, rows);
		}
	}

	/** The Dominion Gauge: ten cells, Kent's blue with Order's gold creeping over it, the number on the right. */
	static void gauge(GuiGraphics g, Font font, int x0, int by, float dominion, boolean possessed) {
		Component label = Component.translatable("hud.projecthero.doctor_fate.dominion");
		int labelW = font.width(label);
		String value = String.valueOf(Math.round(dominion));
		int valueW = Math.max(font.width("100"), font.width(value));
		g.drawString(font, label, x0, by - 1, VanillaHud.WHITE, true);
		int barX = x0 + labelW + 4;
		int barW = WIDTH - labelW - 4 - valueW - 4;
		int cells = 10;
		int cw = Math.max(2, (barW - (cells - 1)) / cells);
		float frac = Mth.clamp(dominion / DoctorFateConfig.DOMINION_MAX, 0f, 1f);
		long t = System.currentTimeMillis();
		for (int c = 0; c < cells; c++) {
			int cx = barX + c * (cw + 1);
			g.fill(cx - 1, by - 1, cx + cw + 1, by + 6, 0xFF101828);
			g.fill(cx, by, cx + cw, by + 5, 0xFF2A56C8); // your colour
			float fill = Mth.clamp(frac * cells - c, 0f, 1f);
			if (fill > 0f) {
				int fw = Math.max(1, Math.round(cw * fill));
				int gold = c >= 7 ? 0xFFFFB02A : 0xFFFFC93A;
				if (possessed && (t / 150) % 2 == 0) {
					gold = 0xFFFFF2B0;
				}
				g.fill(cx, by, cx + fw, by + 5, gold); // Order's gold creeping over it
				g.fill(cx, by, cx + fw, by + 1, 0xFFFFE9A0);
			}
			// tier marks under the 25 / 50 / 75 cells
			if (c == 2 || c == 5 || c == 7) {
				g.fill(cx + cw, by + 6, cx + cw + 1, by + 8, 0xFFFFE08A);
			}
		}
		g.drawString(font, value, x0 + WIDTH - font.width(value), by - 1, dominion >= DoctorFateConfig.TIER_HOLD ? 0xFFFF9A30 : VanillaHud.WHITE, true);
	}

	private static void keys(GuiGraphics g, LocalPlayer p, int x0, int y0) {
		boolean shift = forceShift || p.isShiftKeyDown();
		VanillaHud.strip(g, x0, y0, SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = DoctorFateAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = DoctorFateAbilityManager.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, DoctorFateAbilityManager.maxCooldown(shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, DoctorFateAbilityManager.cooldownRemaining(p, other) / (float) Math.max(1, DoctorFateAbilityManager.maxCooldown(other)));
			boolean usable = DoctorFateAbilityManager.usable(p, shown);
			boolean active = (slot == AbilitySlot.SLOT_2 && DoctorFate.shieldUp(p)) || (slot == AbilitySlot.SLOT_4 && DoctorFate.avatarActive(p));
			VanillaHud.slot(g, x0, y0, i, keyLabel(slot), usable, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}

	// ---------------------------------------------------------------- Taking hold and Possession

	private static void overlays(GuiGraphics g, Minecraft mc, LocalPlayer p, FateState s, boolean possessed, float partial) {
		int W = g.guiWidth(), H = g.guiHeight();
		if (possessed) {
			int pulse = 40 + (int) (20 * Math.sin((p.tickCount + partial) * 0.25));
			g.fill(0, 0, W, H, (pulse << 24) | 0xFFC030);
			edges(g, W, H, 0xC0FFC93A, 26);
			Font font = mc.font;
			int left = DoctorFate.possessionTicksLeft(p);
			Component head = Component.translatable("hud.projecthero.doctor_fate.possessed");
			g.pose().pushPose();
			g.pose().translate(W / 2f, H / 2f - 46, 0);
			g.pose().scale(1.6f, 1.6f, 1f);
			g.drawString(font, head, -font.width(head) / 2, 0, 0xFFFFE070, true);
			g.pose().popPose();
			int bw = Math.min(160, W - 40), bx = (W - bw) / 2, byy = H / 2 - 26;
			float frac = left / (float) DoctorFateConfig.POSSESSION_TICKS;
			g.fill(bx - 1, byy - 1, bx + bw + 1, byy + 5, 0xFF201000);
			g.fill(bx, byy, bx + Math.round(bw * frac), byy + 4, 0xFFFFC93A);
			boolean flash = mc.level.getGameTime() - FateFx.struggledAt < 4;
			List<FormattedCharSequence> hint = font.split(Component.translatable("hud.projecthero.doctor_fate.struggle", clock(left)), Math.min(220, W - 20));
			int y = byy + 9;
			for (FormattedCharSequence l : hint) {
				g.drawString(font, l, (W - font.width(l)) / 2, y, flash ? 0xFF8FB8FF : 0xFFFFFFFF, true);
				y += 10;
			}
			return;
		}
		if (s.helmetOn && s.dominion >= DoctorFateConfig.TIER_HOLD) {
			float k = (s.dominion - DoctorFateConfig.TIER_HOLD) / (DoctorFateConfig.DOMINION_MAX - DoctorFateConfig.TIER_HOLD);
			int a = 60 + Math.round(90 * k) + (DoctorFateClient.lagging() ? 50 : 0);
			edges(g, W, H, (Math.min(220, a) << 24) | 0xFFB020, 14 + Math.round(16 * k));
		}
	}

	/** A soft gold frame: {@code depth} pixels in from every edge, fading inward. */
	private static void edges(GuiGraphics g, int W, int H, int argb, int depth) {
		int a0 = argb >>> 24, rgb = argb & 0xFFFFFF;
		for (int i = 0; i < depth; i++) {
			int a = Math.round(a0 * (1f - i / (float) depth) * (1f - i / (float) depth));
			if (a <= 0) {
				continue;
			}
			int c = (a << 24) | rgb;
			g.fill(i, i, W - i, i + 1, c);
			g.fill(i, H - i - 1, W - i, H - i, c);
			g.fill(i, i + 1, i + 1, H - i - 1, c);
			g.fill(W - i - 1, i + 1, W - i, H - i - 1, c);
		}
	}
}
