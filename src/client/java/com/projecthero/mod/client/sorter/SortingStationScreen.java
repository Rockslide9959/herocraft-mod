package com.projecthero.mod.client.sorter;

import com.projecthero.mod.ironman.sorter.SortingStationMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * v0.14.16: the Stark Sorting Station screen. A procedural flat panel in the Fabricator's style (no background
 * texture) with Stark red/gold trim: the 6x9 store, a status band with the job's progress bar and the
 * <b>Sort</b> and (v0.14.20) <b>Tidy</b> buttons, then the player inventory. Slot geometry matches {@link SortingStationMenu}.
 */
public class SortingStationScreen extends AbstractContainerScreen<SortingStationMenu> {
	private static final int BG = 0xFF181C24;
	private static final int PANEL = 0xFF20242E;
	private static final int GOLD = 0xFFD9A43A;
	private static final int RED = 0xFF9E2026;
	private static final int CELL_BG = 0xFF12151C;
	private static final int CELL_BORDER = 0xFF3C4658;
	private static final int ARC = 0xFF5FD8FF;
	private static final int TEXT = 0xFFB8C0E0;

	// v0.14.20: the band grew to fit two stacked buttons (Sort above Tidy) and a two-line status
	private static final int BAND_Y = 128;
	private static final int BAND_H = 38;
	private static final int BTN_X = 122;
	private static final int SORT_Y = 131;
	private static final int TIDY_Y = 148;
	private static final int BTN_W = 46;
	private static final int BTN_H = 15;
	private static final int BAR_X = 8;
	private static final int BAR_Y = BAND_Y + 29;
	private static final int BAR_W = 108;
	private static final int HINT_WIDTH = 180;

	public SortingStationScreen(SortingStationMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = 176;
		this.imageHeight = SortingStationMenu.INV_Y + 82;
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = SortingStationMenu.INV_Y - 11;
	}

	private void cell(GuiGraphics g, int x, int y) {
		g.fill(x - 1, y - 1, x + 17, y + 17, CELL_BORDER);
		g.fill(x, y, x + 16, y + 16, CELL_BG);
	}

	private boolean overButton(double mx, double my, int btnY) {
		int x = leftPos + BTN_X;
		int y = topPos + btnY;
		return mx >= x && mx < x + BTN_W && my >= y && my < y + BTN_H;
	}

	/** One of the two band buttons; {@code active} = this button's job is the one running. */
	private void button(GuiGraphics g, int btnY, String labelKey, boolean active, int mouseX, int mouseY) {
		boolean busy = menu.running();
		boolean hover = !busy && overButton(mouseX, mouseY, btnY);
		int bxx = leftPos + BTN_X;
		int byy = topPos + btnY;
		g.fill(bxx - 1, byy - 1, bxx + BTN_W + 1, byy + BTN_H + 1, busy ? CELL_BORDER : GOLD);
		g.fill(bxx, byy, bxx + BTN_W, byy + BTN_H, busy ? 0xFF2B3242 : (hover ? 0xFFB8282F : RED));
		Component label = Component.translatable(busy && active ? "screen.projecthero.stark_sorting_station.busy" : labelKey);
		g.drawString(font, label, bxx + (BTN_W - font.width(label)) / 2, byy + (BTN_H - 8) / 2 + 1,
				busy ? 0xFF8A93A8 : 0xFFFFE9B0, true);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		g.fill(x, y, x + imageWidth, y + imageHeight, BG);
		// red/gold Stark trim
		g.fill(x, y, x + imageWidth, y + 2, RED);
		g.fill(x, y + 2, x + imageWidth, y + 3, GOLD);
		g.fill(x, y + imageHeight - 2, x + imageWidth, y + imageHeight, RED);

		for (int row = 0; row < SortingStationMenu.ROWS; row++) {
			for (int col = 0; col < 9; col++) {
				cell(g, x + 8 + col * 18, y + 18 + row * 18);
			}
		}

		// status band
		g.fill(x + 4, y + BAND_Y, x + imageWidth - 4, y + BAND_Y + BAND_H, PANEL);
		g.fill(x + 4, y + BAND_Y, x + imageWidth - 4, y + BAND_Y + 1, GOLD);
		g.fill(x + 4, y + BAND_Y + BAND_H - 1, x + imageWidth - 4, y + BAND_Y + BAND_H, CELL_BORDER);

		int bx = x + BAR_X;
		int by = y + BAR_Y;
		g.fill(bx - 1, by - 1, bx + BAR_W + 1, by + 5, CELL_BORDER);
		g.fill(bx, by, bx + BAR_W, by + 4, 0xFF10141C);
		int total = menu.total();
		if (total > 0) {
			float f = Math.min(1f, menu.done() / (float) total);
			g.fill(bx, by, bx + Math.round(BAR_W * f), by + 4, menu.running() ? ARC : 0xFF66E0A0);
		}

		// the Sort and Tidy buttons
		button(g, SORT_Y, "screen.projecthero.stark_sorting_station.sort", !menu.tidyMode(), mouseX, mouseY);
		button(g, TIDY_Y, "screen.projecthero.stark_sorting_station.tidy", menu.tidyMode(), mouseX, mouseY);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				cell(g, x + 8 + col * 18, y + SortingStationMenu.INV_Y + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			cell(g, x + 8 + col * 18, y + SortingStationMenu.INV_Y + 58);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
		g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF7FA8D8, false);

		Component status;
		boolean tidy = menu.tidyMode();
		if (menu.running()) {
			status = Component.translatable(tidy ? "screen.projecthero.stark_sorting_station.tidying"
					: "screen.projecthero.stark_sorting_station.sorting", menu.done(), menu.total());
		} else if (menu.total() > 0) {
			status = Component.translatable(tidy ? "screen.projecthero.stark_sorting_station.tidied"
					: "screen.projecthero.stark_sorting_station.finished", menu.done(), menu.total());
		} else {
			status = Component.translatable("screen.projecthero.stark_sorting_station.idle");
		}
		// stays inside the band: wrap to the space left of the buttons, at most two lines
		var lines = font.split(status, BTN_X - BAR_X - 6);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			FormattedCharSequence line = lines.get(i);
			g.drawString(font, line, BAR_X, BAND_Y + 5 + i * 10, menu.running() ? ARC : TEXT, false);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		String hint = null;
		if (!menu.running() && overButton(mouseX, mouseY, SORT_Y)) {
			hint = "screen.projecthero.stark_sorting_station.sort_hint";
		} else if (!menu.running() && overButton(mouseX, mouseY, TIDY_Y)) {
			hint = "screen.projecthero.stark_sorting_station.tidy_hint";
		}
		if (hint != null) {
			g.renderTooltip(font, font.split(Component.translatable(hint), HINT_WIDTH), mouseX, mouseY);
		} else {
			renderTooltip(g, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		int id = overButton(mx, my, SORT_Y) ? SortingStationMenu.BUTTON_SORT
				: overButton(mx, my, TIDY_Y) ? SortingStationMenu.BUTTON_TIDY : -1;
		if (button == 0 && id >= 0 && !menu.running() && minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
			minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}
}
