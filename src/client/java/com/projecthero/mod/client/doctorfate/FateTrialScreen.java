package com.projecthero.mod.client.doctorfate;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.doctorfate.network.FateActionPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.4 round 2: the two panels of the trial in Nabu's library ({@link FateLibrary}), drawn over the library and pausing nothing:
 * <ul>
 *   <li><b>A book</b> read at a lectern: Kent Nelson's journal page (blue-edged parchment) or one of Nabu's tomes (gold on black).
 *   Esc, E or a click closes it.</li>
 *   <li><b>Nabu's challenge</b> at the Eye: his words and three answers (keys 1-3 or a click). The server decides what's right
 *   ({@code DoctorFateNabuTrial}); Esc walks away without answering (he'll ask again).</li>
 * </ul>
 * Every line wraps to the panel (the tooltip-wrap rule).
 */
public final class FateTrialScreen extends Screen {
	private static final int PANEL_W = 260;

	private final boolean isBook;
	private final boolean page;
	private final int id;
	private final int[] answers;
	private int hover = -1;
	private int[] rowY = new int[4];
	private boolean sent;

	private FateTrialScreen(boolean isBook, boolean page, int id, int[] answers) {
		super(Component.translatable("screen.projecthero.doctor_fate.trial.title"));
		this.isBook = isBook;
		this.page = page;
		this.id = id;
		this.answers = answers;
	}

	static FateTrialScreen book(boolean kentsPage, int id) {
		return new FateTrialScreen(true, kentsPage, id, null);
	}

	static FateTrialScreen question(int id, int[] answers) {
		return new FateTrialScreen(false, false, id, answers);
	}

	private String base() {
		return "message.projecthero.doctor_fate.trial.";
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		// no blur: the library stays visible behind
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		int w = Math.min(PANEL_W, width - 20);
		List<FormattedCharSequence> body = new ArrayList<>();
		Component title;
		int bg;
		int edge;
		int text;
		if (isBook) {
			String k = base() + (page ? "page." : "tome.") + id;
			title = Component.translatable(k + ".title");
			body.addAll(font.split(Component.translatable(k + ".text"), w - 24));
			body.add(FormattedCharSequence.EMPTY);
			body.addAll(font.split(Component.translatable(base() + (page ? "page_note" : "tome_note")), w - 24));
			bg = page ? 0xF0E8DCB8 : 0xF0100804;
			edge = page ? 0xFF4A78D0 : 0xFFC08020;
			text = page ? 0xFF302010 : 0xFFE8C060;
		} else {
			title = Component.translatable(base() + "nabu_asks");
			body.addAll(font.split(Component.translatable(base() + "question." + id), w - 24));
			bg = 0xF0100804;
			edge = 0xFFFFC93A;
			text = 0xFFFFE8A0;
		}
		List<List<FormattedCharSequence>> rows = new ArrayList<>();
		if (!isBook) {
			for (int i = 0; i < 3; i++) {
				rows.add(font.split(Component.literal((i + 1) + ". ").append(Component.translatable(base() + "answer." + id + "." + answers[i])), w - 36));
			}
		}
		int h = 34 + body.size() * 10;
		for (List<FormattedCharSequence> r : rows) {
			h += r.size() * 10 + 8;
		}
		h += isBook ? 14 : 16;
		int x0 = (width - w) / 2;
		int y0 = Math.max(6, (height - h) / 2);
		g.fill(x0 - 2, y0 - 2, x0 + w + 2, y0 + h + 2, edge);
		g.fill(x0, y0, x0 + w, y0 + h, bg);
		int y = y0 + 8;
		g.drawCenteredString(font, title, width / 2, y, isBook && page ? 0xFF1A3A80 : 0xFFFFD860);
		y += 16;
		for (FormattedCharSequence line : body) {
			g.drawString(font, line, x0 + 12, y, text, !(isBook && page));
			y += 10;
		}
		hover = -1;
		if (!isBook) {
			y += 6;
			for (int i = 0; i < rows.size(); i++) {
				int rh = rows.get(i).size() * 10 + 4;
				boolean over = mouseX >= x0 + 8 && mouseX <= x0 + w - 8 && mouseY >= y - 2 && mouseY <= y + rh - 2;
				if (over) {
					hover = i;
				}
				g.fill(x0 + 8, y - 2, x0 + w - 8, y + rh - 2, over ? 0x60FFC93A : 0x30FFC93A);
				int ry = y;
				for (FormattedCharSequence line : rows.get(i)) {
					g.drawString(font, line, x0 + 14, ry, over ? 0xFFFFFFFF : 0xFFE8DCC0, true);
					ry += 10;
				}
				rowY[i] = y;
				y += rh + 4;
			}
		}
		Component hint = Component.translatable(base() + (isBook ? "close_hint" : "answer_hint"));
		g.drawCenteredString(font, hint, width / 2, y0 + h - 12, isBook && page ? 0xFF6A5A40 : 0xFF9A8A60);
		super.render(g, mouseX, mouseY, Mth.clamp(partial, 0f, 1f));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (isBook) {
			onClose();
			return true;
		}
		if (hover >= 0) {
			pick(hover);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (!isBook && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_3) {
			pick(key - GLFW.GLFW_KEY_1);
			return true;
		}
		if (isBook && (key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER)) {
			onClose();
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	private void pick(int row) {
		if (!sent) {
			sent = true;
			FateLibrary.send(FateActionPayload.Action.TRIAL_ANSWER, row);
		}
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
