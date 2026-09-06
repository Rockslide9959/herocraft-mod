package com.projecthero.mod.client.gui;

import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Iron Man Suit Platform screen. The layout is drawn entirely by this class on a plain panel (the
 * shared Fabricator background is not used) so every element has a fixed, non-overlapping band:
 * <pre>
 *   y 6    title
 *   y 22   slot labels H C L B
 *   y 32   the four armour slots
 *   y 54   SUIT CHARGE bar   (or "No suit stored")
 *   y 66   INTEGRITY bar
 *   y 78   RESERVE line
 *   y 92   Deploy / Retrieve buttons
 *   y 114  "Inventory"
 *   y 124  player inventory (3 rows)
 *   y 182  hotbar
 * </pre>
 */
public class IronManSuitPlatformScreen extends AbstractContainerScreen<IronManSuitPlatformMenu> {
	private static final int BG = 0xFF181C24;
	private static final int PANEL = 0xFF20242E;
	private static final int PANEL_BORDER = 0xFF3C4658;
	private static final int CELL_BG = 0xFF12151C;
	private static final int CELL_BORDER = 0xFF3C4658;
	private static final String[] SLOT_LABELS = { "H", "C", "L", "B" };

	public IronManSuitPlatformScreen(IronManSuitPlatformMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = 176;
		this.imageHeight = 202;
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = 114;
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.suit_platform.deploy"),
				b -> click(0)).bounds(leftPos + 8, topPos + 92, 78, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.suit_platform.retrieve"),
				b -> click(1)).bounds(leftPos + 90, topPos + 92, 78, 16).build());
	}

	private void click(int id) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	private void cell(GuiGraphics g, int x, int y, String label) {
		g.fill(x - 1, y - 1, x + 17, y + 17, CELL_BORDER);
		g.fill(x, y, x + 16, y + 16, CELL_BG);
		if (label != null) {
			g.drawString(font, label, x + 5, y - 10, 0xFF7FA8D8, false);
		}
	}

	private void bar(GuiGraphics g, int x, int y, String label, float frac, String value, int color) {
		frac = Math.max(0f, Math.min(1f, frac));
		g.drawString(font, label, x, y, 0xFF9AA6D0, false);
		int barX = x + 58;
		int w = 44;
		g.fill(barX - 1, y - 1, barX + w + 1, y + 7, PANEL_BORDER);
		g.fill(barX, y, barX + w, y + 6, 0xAA0A0A10);
		g.fill(barX, y, barX + Math.round(w * frac), y + 6, color);
		g.drawString(font, value, barX + w + 4, y, 0xFFB8C0E0, false);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = leftPos, y = topPos;
		g.fill(x, y, x + imageWidth, y + imageHeight, BG);
		g.fill(x + 5, y + 18, x + imageWidth - 5, y + 88, PANEL);       // upper section
		g.fill(x + 5, y + 18, x + imageWidth - 5, y + 19, PANEL_BORDER);
		g.fill(x + 5, y + 87, x + imageWidth - 5, y + 88, PANEL_BORDER);

		for (int i = 0; i < 4; i++) {
			cell(g, x + 53 + i * 20, y + 32, SLOT_LABELS[i]);
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				cell(g, x + 8 + col * 18, y + 124 + row * 18, null);
			}
		}
		for (int col = 0; col < 9; col++) {
			cell(g, x + 8 + col * 18, y + 182, null);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		super.renderLabels(g, mouseX, mouseY);
		java.util.Locale L = java.util.Locale.ROOT;

		if (menu.hasStoredSuit()) {
			float cap = Math.max(1f, menu.suitCapacityExact());
			float energy = Math.max(0f, Math.min(cap, menu.suitEnergyExact()));
			float chargePct = 100f * energy / cap;
			float integPct = Math.max(0f, Math.min(100f, menu.suitIntegrityPercentExact()));

			bar(g, 10, 54, "SUIT CHARGE", energy / cap, String.format(L, "%.2f%%", chargePct),
					chargePct < 15f ? 0xFFFF7A3C : 0xFF6FA8FF);
			bar(g, 10, 66, "INTEGRITY", integPct / 100f, String.format(L, "%.2f%%", integPct),
					integPct < 30f ? 0xFFFF5555 : 0xFF66E0A0);
			// exact energy / capacity, so the readout never disagrees with the bar above it
			g.drawString(font, Component.literal(
					String.format(L, "ENERGY  %,d / %,d", Math.round(energy), Math.round(cap)))
					.withStyle(ChatFormatting.DARK_AQUA), 10, 78, 0xFF4C8FB0, false);
		} else {
			g.drawString(font, Component.translatable("screen.projecthero.suit_platform.empty")
					.withStyle(ChatFormatting.DARK_GRAY), 10, 58, 0xFF6A7286, false);
			g.drawString(font, Component.literal(String.format(L, "RESERVE  %,d", Math.round(menu.reserveEnergyExact())))
					.withStyle(ChatFormatting.DARK_AQUA), 10, 78, 0xFF4C8FB0, false);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		this.renderTooltip(g, mouseX, mouseY);
	}
}
