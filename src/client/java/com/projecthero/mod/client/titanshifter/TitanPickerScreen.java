package com.projecthero.mod.client.titanshifter;

import java.util.List;

import com.projecthero.mod.network.TitanShiftPayload;
import com.projecthero.mod.titanshifter.TitanType;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.16.7 (user, Titan revamp): the Titan picker that opens when a Titan Serum is drunk -- four cards (Attack, Armoured,
 * Female, Beast), each with its height and a wrapped one-paragraph pitch; click one to become it (or, for a shifter, to
 * re-forge into it). Escape picks nothing and keeps the serum. Laid out for the smallest scaled screen (320 x 240):
 * two columns of cards, every line wrapped to the card ({@link #CARD_TEXT_LINES} at most, checked by the UI gametest).
 */
public class TitanPickerScreen extends Screen {
	/** The most description lines a card has room for (the gametest checks every description wraps into this many). */
	public static final int CARD_TEXT_LINES = com.projecthero.mod.titanshifter.TitanUi.PICKER_TEXT_LINES;
	public static final int MAX_PANEL_W = com.projecthero.mod.titanshifter.TitanUi.PICKER_MAX_PANEL_W;
	private static final int GAP = com.projecthero.mod.titanshifter.TitanUi.PICKER_GAP;
	private static final int PAD = com.projecthero.mod.titanshifter.TitanUi.PICKER_PAD;
	private static final int LINE = 9;

	private static final TitanType[] ORDER = { TitanType.GENERIC_TITAN, TitanType.ARMORED_TITAN, TitanType.FEMALE_TITAN,
			TitanType.BEAST_TITAN };
	private static final TitanShiftPayload.Action[] PICKS = { TitanShiftPayload.Action.PICK_ATTACK, TitanShiftPayload.Action.PICK_ARMORED,
			TitanShiftPayload.Action.PICK_FEMALE, TitanShiftPayload.Action.PICK_BEAST };

	private final int current;
	private int hovered = -1;

	public TitanPickerScreen(int current) {
		super(Component.translatable("screen.projecthero.titan_picker.title"));
		this.current = current;
	}

	/** Card width for a screen this wide (the gametest uses the same sum). */
	public static int cardWidth(int screenW) {
		int panelW = Math.min(screenW - 16, MAX_PANEL_W);
		return (panelW - GAP) / 2;
	}

	public static int cardHeight() {
		return PAD * 2 + 11 + 10 + CARD_TEXT_LINES * LINE;
	}

	private int[] cardRect(int i) {
		int cw = cardWidth(width);
		int ch = cardHeight();
		int panelW = cw * 2 + GAP;
		int x0 = (width - panelW) / 2;
		int totalH = 14 + ch * 2 + GAP + 14;
		int y0 = Math.max(4, (height - totalH) / 2) + 14;
		int col = i % 2;
		int row = i / 2;
		return new int[] { x0 + col * (cw + GAP), y0 + row * (ch + GAP), cw, ch };
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		int[] first = cardRect(0);
		Component title = Component.translatable(current >= 0 ? "screen.projecthero.titan_picker.reforge"
				: "screen.projecthero.titan_picker.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		g.drawCenteredString(font, title, width / 2, first[1] - 12, 0xFFFFAA00);
		hovered = -1;
		for (int i = 0; i < ORDER.length; i++) {
			int[] r = cardRect(i);
			boolean over = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
			if (over) {
				hovered = i;
			}
			TitanType t = ORDER[i];
			boolean mine = t.ordinal() == current;
			g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], over ? 0xE0302418 : 0xD0161210);
			g.renderOutline(r[0], r[1], r[2], r[3], over ? 0xFFFFC040 : mine ? 0xFF6FB0FF : 0xFF4A3A28);
			int tx = r[0] + PAD;
			int ty = r[1] + PAD;
			int tw = r[2] - PAD * 2;
			g.drawString(font, Component.translatable(t.nameKey()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), tx, ty, 0xFFFFAA00, true);
			ty += 11;
			Component size = Component.translatable(mine ? "screen.projecthero.titan_picker.size_current" : "screen.projecthero.titan_picker.size",
					Math.round(t.dimensionHeight()));
			g.drawString(font, size, tx, ty, mine ? 0xFF8FC8FF : 0xFFA89880, false);
			ty += 10;
			List<FormattedCharSequence> lines = font.split(Component.translatable(t.descKey()), tw);
			for (int k = 0; k < Math.min(CARD_TEXT_LINES, lines.size()); k++) {
				g.drawString(font, lines.get(k), tx, ty, 0xFFE6E0D8, false);
				ty += LINE;
			}
		}
		int[] last = cardRect(3);
		List<FormattedCharSequence> hint = font.split(Component.translatable("screen.projecthero.titan_picker.hint")
				.withStyle(ChatFormatting.GRAY), cardWidth(width) * 2 + GAP);
		int hy = last[1] + last[3] + 4;
		for (FormattedCharSequence l : hint) {
			g.drawCenteredString(font, l, width / 2, hy, 0xFFAAAAAA);
			hy += LINE;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (int i = 0; i < ORDER.length; i++) {
				int[] r = cardRect(i);
				if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
					pick(i);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		// 1-4 pick too
		if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_4) {
			pick(key - org.lwjgl.glfw.GLFW.GLFW_KEY_1);
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	private void pick(int i) {
		ClientPlayNetworking.send(new TitanShiftPayload(PICKS[i]));
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
