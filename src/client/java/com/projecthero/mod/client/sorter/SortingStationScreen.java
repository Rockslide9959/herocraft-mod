package com.projecthero.mod.client.sorter;

import com.projecthero.mod.ironman.sorter.SortingStationMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.14.16: the Stark Sorting Station screen. A procedural flat panel in the Fabricator's style (no background
 * texture) with Stark red/gold trim: the 6x9 store, a status band with the job's progress bar and the
 * <b>Sort</b> and (v0.14.20) <b>Tidy</b> buttons, then the player inventory. v0.14.21 adds a supply column on the
 * right: three sign slots, three chest slots (ghost icons while empty) and what the room still needs ("Add 3
 * signs"), wrapped to the column. Slot geometry matches {@link SortingStationMenu}.
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
	// v0.14.21: the supply column to the right of the store
	private static final int MAIN_W = 176;
	private static final int SIDE_W = 72;
	private static final int SIDE_H = BAND_Y + BAND_H + 2;
	private static final int NEEDS_X = MAIN_W + 6;
	private static final int NEEDS_Y = SortingStationMenu.CHEST_Y + 24;
	private static final int NEEDS_W = SIDE_W - 10;
	private static final int NEEDS_BOTTOM = SIDE_H - 4;

	public SortingStationScreen(SortingStationMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = MAIN_W + SIDE_W;
		this.imageHeight = SortingStationMenu.INV_Y + 82;
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = SortingStationMenu.INV_Y - 11;
	}

	private static final ItemStack GHOST_SIGN = new ItemStack(Items.OAK_SIGN);
	private static final ItemStack GHOST_CHEST = new ItemStack(Items.CHEST);

	/** A faded item in an empty supply slot, so it is obvious what goes there. */
	private void ghost(GuiGraphics g, int slotIndex, int x, int y, ItemStack icon) {
		if (menu.getSlot(slotIndex).hasItem()) {
			return;
		}
		g.renderFakeItem(icon, x, y);
		g.pose().pushPose();
		g.pose().translate(0, 0, 300);
		g.fill(x, y, x + 16, y + 16, 0xB012151C);
		g.pose().popPose();
	}

	/** v0.14.21: the "what the room still needs" lines for the supply column. */
	private java.util.List<Component> needsLines() {
		java.util.List<Component> out = new java.util.ArrayList<>();
		if (menu.signsShort() > 0) {
			out.add(Component.translatable("screen.projecthero.stark_sorting_station.add_signs", menu.signsShort())
					.withStyle(ChatFormatting.GOLD));
		}
		if (menu.chestsShort() > 0) {
			out.add(Component.translatable("screen.projecthero.stark_sorting_station.add_chests", menu.chestsShort())
					.withStyle(ChatFormatting.GOLD));
		}
		if (menu.noSignFace() > 0) {
			out.add(Component.translatable("screen.projecthero.stark_sorting_station.no_sign_face", menu.noSignFace())
					.withStyle(ChatFormatting.GRAY));
		}
		if (out.isEmpty()) {
			out.add(Component.translatable("screen.projecthero.stark_sorting_station.supply_ok").withStyle(ChatFormatting.GREEN));
		}
		return out;
	}

	private boolean overNeeds(double mx, double my) {
		return mx >= leftPos + MAIN_W && mx < leftPos + imageWidth && my >= topPos + NEEDS_Y - 2 && my < topPos + NEEDS_BOTTOM;
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
		g.fill(x, y, x + MAIN_W, y + imageHeight, BG);
		g.fill(x + MAIN_W, y, x + imageWidth, y + SIDE_H, BG);
		// red/gold Stark trim
		g.fill(x, y, x + imageWidth, y + 2, RED);
		g.fill(x, y + 2, x + imageWidth, y + 3, GOLD);
		g.fill(x, y + imageHeight - 2, x + MAIN_W, y + imageHeight, RED);
		g.fill(x + MAIN_W, y + SIDE_H - 2, x + imageWidth, y + SIDE_H, RED);
		g.fill(x + MAIN_W, y + 3, x + MAIN_W + 1, y + SIDE_H - 2, CELL_BORDER);

		// v0.14.21: supply slots, with a faded sign / chest while empty
		for (int i = 0; i < 3; i++) {
			int sx = x + SortingStationMenu.SUPPLY_X + i * 18;
			cell(g, sx, y + SortingStationMenu.SIGN_Y);
			cell(g, sx, y + SortingStationMenu.CHEST_Y);
			ghost(g, SortingStationMenu.SUPPLY_START + i, sx, y + SortingStationMenu.SIGN_Y, GHOST_SIGN);
			ghost(g, SortingStationMenu.SUPPLY_START + 3 + i, sx, y + SortingStationMenu.CHEST_Y, GHOST_CHEST);
		}

		for (int row = 0; row < SortingStationMenu.ROWS; row++) {
			for (int col = 0; col < 9; col++) {
				cell(g, x + 8 + col * 18, y + 18 + row * 18);
			}
		}

		// status band
		g.fill(x + 4, y + BAND_Y, x + MAIN_W - 4, y + BAND_Y + BAND_H, PANEL);
		g.fill(x + 4, y + BAND_Y, x + MAIN_W - 4, y + BAND_Y + 1, GOLD);
		g.fill(x + 4, y + BAND_Y + BAND_H - 1, x + MAIN_W - 4, y + BAND_Y + BAND_H, CELL_BORDER);

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
		// v0.14.21: the supply column -- headings, then what the room still needs, wrapped to the column
		g.drawString(font, Component.translatable("screen.projecthero.stark_sorting_station.supplies"), NEEDS_X, 6, GOLD, false);
		g.drawString(font, Component.translatable("screen.projecthero.stark_sorting_station.signs"), SortingStationMenu.SUPPLY_X,
				SortingStationMenu.SIGN_Y - 10, 0xFF7FA8D8, false);
		g.drawString(font, Component.translatable("screen.projecthero.stark_sorting_station.chests"), SortingStationMenu.SUPPLY_X,
				SortingStationMenu.CHEST_Y - 10, 0xFF7FA8D8, false);
		int ny = NEEDS_Y;
		for (Component need : needsLines()) {
			for (FormattedCharSequence line : font.split(need, NEEDS_W)) {
				if (ny + 9 > NEEDS_BOTTOM) {
					break;
				}
				g.drawString(font, line, NEEDS_X, ny, 0xFFFFFFFF, false);
				ny += 10;
			}
			ny += 2;
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
		if (hint == null && overNeeds(mouseX, mouseY)) {
			java.util.List<FormattedCharSequence> lines = new java.util.ArrayList<>();
			lines.addAll(font.split(Component.translatable("screen.projecthero.stark_sorting_station.needs_detail",
					menu.signsNeeded(), menu.chestsNeeded()), HINT_WIDTH));
			lines.addAll(font.split(Component.translatable("screen.projecthero.stark_sorting_station.supply_hint"), HINT_WIDTH));
			g.renderTooltip(font, lines, mouseX, mouseY);
			return;
		}
		if (hint == null && hoveredSlot instanceof SortingStationMenu.SupplySlot supply && !supply.hasItem()) {
			hint = supply.isSignSlot() ? "screen.projecthero.stark_sorting_station.sign_slot"
					: "screen.projecthero.stark_sorting_station.chest_slot";
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
